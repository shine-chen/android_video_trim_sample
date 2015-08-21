package csgi.com.videoedittest;

import android.annotation.TargetApi;
import android.content.Context;
import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.net.Uri;
import android.util.Log;
import android.view.Surface;

import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * Created by Shine on 8/13/15.
 */
@TargetApi(18)
public class Player {
    private static final String TAG = "Player";

    private static final long FRAME_DURATION_US = 200000;
    private static final long JUMP_SEEK_DURATION_US = 1000000;  // 1 second

    enum PlaybackState {
        Playing,
        Pause,
        Stop,
        SEEKING,
    }

    public interface Listener {
        void onPrepared();
        void updatePosition(long position, long duration);
    }

    private MediaExtractor extractor;
    private MediaCodec decoder;
    private Surface surface;
    private long duration;

    private Listener listener;
    public void setListener(Listener listener) {
        this.listener = listener;
    }

    public long getDuration() {
        return duration;
    }

    private Thread playbackThread;

    ByteBuffer[] videoDecoderInputBuffers = null;
    ByteBuffer[] videoDecoderOutputBuffers = null;

    private PlaybackState mPlaybackState = PlaybackState.Pause;

    public Player(Surface surface) {
        this.surface = surface;

        this.extractor = new MediaExtractor();
    }

    public void setDataSource(String path) throws IOException {
        extractor.setDataSource(path);
    }

    public void setDataSource(Context context, Uri uri) throws IOException {
        extractor.setDataSource(context, uri, null);
    }

    synchronized public PlaybackState getState() {
        return mPlaybackState;
    }

    synchronized private void setState(PlaybackState playbackState) {
        mPlaybackState = playbackState;
    }

    private void prepare() {
        try {
            for (int i = 0; i < extractor.getTrackCount(); i++) {
                MediaFormat format = extractor.getTrackFormat(i);
                String mime = format.getString(MediaFormat.KEY_MIME);
                if (mime.startsWith("video/")) {
                    extractor.selectTrack(i);
                    decoder = MediaCodec.createDecoderByType(mime);
                    decoder.configure(format, surface, null, 0);

                    duration = format.getLong(MediaFormat.KEY_DURATION);
                    break;
                }

            }

            if (decoder == null) {
                Log.e(TAG, "Can't find video info!");
                return;
            }

            decoder.start();

            if (listener != null) {
                listener.onPrepared();
            }

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void start() {
        if (extractor != null && decoder == null) {
            prepare();
        }

        playbackThread = new PlaybackThread();
        playbackThread.start();

        setState(PlaybackState.Playing);
    }

    public void pause() {
        setState(PlaybackState.Pause);
        playbackThread.interrupt();
    }

    public void stop() {

        if (getState() == PlaybackState.Pause) {
            releaseCodec();
        }
        setState(PlaybackState.Stop);
        playbackThread.interrupt();
    }

    private void onPlaybackThreadFinish() {
        if (getState() == PlaybackState.Stop) {
            releaseCodec();
        }
    }

    private void releaseCodec() {
        Log.w(TAG, "releaseCodec");

        if (extractor != null) {
            extractor.release();
            extractor = null;
        }
        if (decoder != null) {
            decoder.stop();
            decoder.release();
            decoder = null;
        }
    }

    public void seek(long position) {

        decoder.flush();

        extractor.seekTo(position, MediaExtractor.SEEK_TO_CLOSEST_SYNC);

        decodeFrame(-1);
    }

    public void seekNextFrame() {
        Log.d(TAG, "seekNextFrame");
//            decoder.flush();
//            extractor.seekTo(extractor.getSampleTime() + 30000, MediaExtractor.SEEK_TO_NEXT_SYNC);
        decodeFrame(-1);
    }

    public void seekNext() {
        Log.d(TAG, "seekNext");
        decoder.flush();
        extractor.seekTo(extractor.getSampleTime() + JUMP_SEEK_DURATION_US, MediaExtractor.SEEK_TO_NEXT_SYNC);
        decodeFrame(-1);
    }

    public void seekPrev() {
        Log.d(TAG, "seekPrev");
        decoder.flush();
        extractor.seekTo(extractor.getSampleTime() - JUMP_SEEK_DURATION_US, MediaExtractor.SEEK_TO_PREVIOUS_SYNC);
        decodeFrame(-1);
    }

    public void seekPrevFrame() {
        Log.d(TAG, "seekPrevFrame");

        decoder.flush();

        final long targetPTUS = extractor.getSampleTime() - FRAME_DURATION_US;
        extractor.seekTo(extractor.getSampleTime() - JUMP_SEEK_DURATION_US, MediaExtractor.SEEK_TO_PREVIOUS_SYNC);
        decodeFrame(targetPTUS);
    }

    private void decodeFrame(long targetPTUS) {

        if (getState() == PlaybackState.SEEKING) {
            return;
        }
        if (targetPTUS > 0) {
            setState(PlaybackState.SEEKING);
        }

        while(true) {
            int inIndex = decoder.dequeueInputBuffer(10000);
            if (inIndex >= 0) {
                ByteBuffer buffer = videoDecoderInputBuffers[inIndex];
                int sampleSize = extractor.readSampleData(buffer, 0);
                if (sampleSize < 0) {
                    // We shouldn't stop the playback at this point, just pass the EOS
                    // flag to decoder, we will get it again from the
                    // dequeueOutputBuffer
                    Log.d(TAG, "InputBuffer BUFFER_FLAG_END_OF_STREAM");
                    decoder.queueInputBuffer(inIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                    break;
                } else {
                    decoder.queueInputBuffer(inIndex, 0, sampleSize, extractor.getSampleTime(), 0);
                    extractor.advance();
                }
            }

            MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
            int outIndex = decoder.dequeueOutputBuffer(info, 10000);
            switch (outIndex) {
                case MediaCodec.INFO_OUTPUT_FORMAT_CHANGED:
                    Log.d(TAG, "New format " + decoder.getOutputFormat());
                    break;
                case MediaCodec.INFO_TRY_AGAIN_LATER:
                    Log.d(TAG, "dequeueOutputBuffer try again later!");
                    //
                    break;
                case MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED:
                    Log.d(TAG, "dequeueOutputBuffer output buffer changed!");
                    videoDecoderInputBuffers = decoder.getInputBuffers();
                    videoDecoderOutputBuffers = decoder.getOutputBuffers();
                    break;
                default:
                    if (targetPTUS > 0 && info.presentationTimeUs < targetPTUS - FRAME_DURATION_US) {
                        decoder.releaseOutputBuffer(outIndex, false);
                        continue;
                    }

                    decoder.releaseOutputBuffer(outIndex, true);

                    setState(PlaybackState.Pause);

                    if (listener != null) {
                        listener.updatePosition(info.presentationTimeUs, duration);
                    }

                    return;
            }
        }
    }

    private class PlaybackThread extends Thread {
        @Override
        public void run() {

            if (videoDecoderInputBuffers == null) {
                videoDecoderInputBuffers = decoder.getInputBuffers();
            }

            if (videoDecoderOutputBuffers == null) {
                videoDecoderOutputBuffers = decoder.getOutputBuffers();
            }

            boolean isEOS = false;
            long startMs = System.currentTimeMillis() - extractor.getSampleTime() / 1000;

            while (!Thread.interrupted()) {
                if (!isEOS) {
                    int inIndex = decoder.dequeueInputBuffer(10000);
                    if (inIndex >= 0) {
                        ByteBuffer buffer = videoDecoderInputBuffers[inIndex];
                        int sampleSize = extractor.readSampleData(buffer, 0);
                        if (sampleSize < 0) {
                            // We shouldn't stop the playback at this point, just pass the EOS
                            // flag to decoder, we will get it again from the
                            // dequeueOutputBuffer
                            Log.d(TAG, "InputBuffer BUFFER_FLAG_END_OF_STREAM");
                            decoder.queueInputBuffer(inIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                            isEOS = true;
                        } else {
                            decoder.queueInputBuffer(inIndex, 0, sampleSize, extractor.getSampleTime(), 0);
                            extractor.advance();
                        }
                    }
                }

                MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
                int outIndex = decoder.dequeueOutputBuffer(info, 10000);
                switch (outIndex) {
                    case MediaCodec.INFO_OUTPUT_FORMAT_CHANGED:
                        Log.d(TAG, "New format " + decoder.getOutputFormat());
                        break;
                    case MediaCodec.INFO_TRY_AGAIN_LATER:
                        Log.d(TAG, "dequeueOutputBuffer try again later!");
                        break;
                    case MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED:
                        Log.d(TAG, "dequeueOutputBuffer output buffer changed!");
                        videoDecoderInputBuffers = decoder.getInputBuffers();
                        videoDecoderOutputBuffers = decoder.getOutputBuffers();
                        break;
                    default:
                        ByteBuffer buffer = videoDecoderOutputBuffers[outIndex];
                        //Log.v(TAG, "We can't use this buffer but render it due to the API limit, " + buffer);

                        // We use a very simple clock to keep the video FPS, or the video
                        // playback will be too fast
                        while (info.presentationTimeUs / 1000 > System.currentTimeMillis() - startMs) {
                            try {
                                sleep(10);
                            } catch (InterruptedException e) {
                                e.printStackTrace();
                                interrupt();
                                break;
                            }
                        }
                        decoder.releaseOutputBuffer(outIndex, true);

                        if (listener != null) {
                            listener.updatePosition(info.presentationTimeUs, duration);
                        }

                        break;
                }

                // All decoded frames have been rendered, we can stop playing now
                if ((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                    Log.d(TAG, "OutputBuffer BUFFER_FLAG_END_OF_STREAM");
                    break;
                }
            }

            onPlaybackThreadFinish();
        }

    }
}
