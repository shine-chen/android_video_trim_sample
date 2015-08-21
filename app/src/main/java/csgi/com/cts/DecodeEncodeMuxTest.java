package csgi.com.cts;

import android.annotation.TargetApi;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.opengl.EGL14;
import android.opengl.EGLConfig;
import android.opengl.EGLContext;
import android.opengl.EGLDisplay;
import android.opengl.EGLExt;
import android.opengl.EGLSurface;
import android.os.Build;
import android.os.Environment;
import android.util.Log;
import android.view.Surface;

import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * Created by Shine on 8/12/15.
 */
public class DecodeEncodeMuxTest {
    private static final String TAG = DecodeEncodeMuxTest.class.getSimpleName();

    public void setListener(Listener listener) {
        this.listener = listener;
    }

    public interface Listener {
        void onProgressUpdate(double percentage);
    }

    private static final boolean bNativeMuxerSupport = (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.JELLY_BEAN_MR2);

    private final String inputFilePath;
    private final String outputFilePath;
    private long startPosition;
    private long endPosition;

    private Listener listener;

    public DecodeEncodeMuxTest(String input, String output, long start, long end) {
        inputFilePath = input;
        outputFilePath = output;
        startPosition = start;
        endPosition = end;
    }

    public void start() {
        PlayerThread player = new PlayerThread(null);
        player.start();
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    private class PlayerThread extends Thread {
        private MediaExtractor extractor;
        private MediaCodec decoder;
        private MediaCodec encoder;
        private MediaMuxer muxer = null;
        private Surface surface;
        private byte[] sps;
        private byte[] pps;
        private boolean mMuxerStarted;
        private int mTrackIndex;

        public PlayerThread(Surface surface) {
            this.surface = surface;
        }

        @Override
        public void run() {
            extractor = new MediaExtractor();
            try {
                extractor.setDataSource(inputFilePath);
            } catch (IOException e1) {
                // TODO Auto-generated catch block
                e1.printStackTrace();
            }

            for (int i = 0; i < extractor.getTrackCount(); i++) {
                MediaFormat format = extractor.getTrackFormat(i);
                String mime = format.getString(MediaFormat.KEY_MIME);
                if (mime.startsWith("video/")) {
                    extractor.selectTrack(i);
                    try {
                        decoder = MediaCodec.createDecoderByType(mime);
                    } catch (IOException e) {
                        e.printStackTrace();
                        return;
                    }
                    decoder.configure(format, null, null, 0);

                    MediaFormat decoderOutputFormat = decoder.getOutputFormat();

                    if (endPosition <= startPosition) {
                        endPosition = format.getLong(MediaFormat.KEY_DURATION);
                    }

                    MediaFormat mediaFormat = MediaFormat.createVideoFormat("video/avc",
                            480, //format.getInteger(MediaFormat.KEY_WIDTH),
                            360); //format.getInteger(MediaFormat.KEY_HEIGHT));
                    mediaFormat.setInteger(MediaFormat.KEY_BIT_RATE, 1500000);
                    mediaFormat.setInteger(MediaFormat.KEY_FRAME_RATE, 19);
                    // mediaFormat.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Planar);
                    // Qualcomm chipset
                    mediaFormat.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar);
                    mediaFormat.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 10);

                    try {
                        encoder = MediaCodec.createEncoderByType("video/avc");
                    } catch (IOException e) {
                        e.printStackTrace();
                        return;
                    }
                    encoder.configure(mediaFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
                    encoder.start();

                    try {
                        Log.d(TAG, "output file is " + outputFilePath);
                        if (bNativeMuxerSupport) {
                            muxer = new MediaMuxer(outputFilePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
                        } else {
                            // Mimic muxer started
                            mMuxerStarted = true;
                        }
                    } catch (IOException ioe) {
                        throw new RuntimeException("MediaMuxer creation failed", ioe);
                    }
                    break;
                }
            }

            if (decoder == null) {
                Log.e(TAG, "Can't find video info!");
                return;
            }

            decoder.start();

            if (startPosition > 0) {
                extractor.seekTo(startPosition, MediaExtractor.SEEK_TO_CLOSEST_SYNC);
            }

            startPosition = extractor.getSampleTime();

            MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
            boolean isEOS = false;

            while (!Thread.interrupted()) {
                if (!isEOS) {
                    int inIndex = decoder.dequeueInputBuffer(10000);
                    if (inIndex >= 0) {
                        ByteBuffer buffer = decoder.getInputBuffer(inIndex);

                        int sampleSize;

                        if (extractor.getSampleTime() < endPosition) {
                            sampleSize = extractor.readSampleData(buffer, 0);
                            Log.d(TAG, "Extracted sample: " + extractor.getSampleTime() + ": " + sampleSize + " bytes");

                            if (listener != null) {
                                listener.onProgressUpdate((double) (extractor.getSampleTime() - startPosition)
                                        / (endPosition - startPosition));
                            }
                        } else {
                            sampleSize = -1;
                            if (listener != null) {
                                listener.onProgressUpdate(1.00);
                            }
                        }

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

                int decoderOutIndex = decoder.dequeueOutputBuffer(info, 10000);
                switch (decoderOutIndex) {
                    case MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED:
                        Log.d(TAG, "INFO_OUTPUT_BUFFERS_CHANGED");
                        break;
                    case MediaCodec.INFO_OUTPUT_FORMAT_CHANGED:
                        MediaFormat nFormat = decoder.getOutputFormat();
                        Log.d(TAG, "New format " + nFormat);
                        break;
                    case MediaCodec.INFO_TRY_AGAIN_LATER:
                        Log.d(TAG, "Decoder: dequeueOutputBuffer try again later!");
                        break;
                    default:
                        ByteBuffer output = decoder.getOutputBuffer(decoderOutIndex);
//					Log.v(TAG, "We can't use this buffer but render it due to the API limit, " + buffer);

                        if (output != null) {
                            info.presentationTimeUs = info.presentationTimeUs - startPosition;
                            offerEncoder(output, info);
                            output.clear();
                        }

                        decoder.releaseOutputBuffer(decoderOutIndex, false);
                }

                // All decoded frames have been rendered, we can stop playing now
                if ((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                    Log.w(TAG, "OutputBuffer BUFFER_FLAG_END_OF_STREAM");
                    break;
                }
            }

            decoder.stop();
            decoder.release();
            extractor.release();

            Log.w(TAG, "drain");
            offerMuxer();

            encoder.stop();
            encoder.release();

            muxer.stop();
            muxer.release();
        }

        private void offerEncoder(ByteBuffer input, MediaCodec.BufferInfo info) {

            Log.d(TAG, "offerEncoder");

            int srcSize = input.remaining();

            byte[] src = new byte[srcSize];
            input.get(src);
            int offset = 0;
            int inputBufferIndex = encoder.dequeueInputBuffer(-1);
            while (inputBufferIndex >= 0) {
                ByteBuffer inputBuffer = encoder.getInputBuffer(inputBufferIndex);

				//Log.d(TAG, "Encoder: inputBuffer " + inputBufferIndex + ": " + inputBuffer.position() + "," + inputBuffer.remaining());

                int size = Math.min(srcSize - offset, inputBuffer.remaining());
                inputBuffer.put(src, offset, size);
                offset += size;
                encoder.queueInputBuffer(inputBufferIndex, 0, size, info.presentationTimeUs, 0);

				Log.d(TAG, "Encoder: queueInputBuffer:" + inputBufferIndex + " : " + size);


                if (offset < srcSize) {
                    inputBufferIndex = encoder.dequeueInputBuffer(-1);
                } else {
                    break;
                }
            }

            Log.d(TAG, "Encoder: inputBufferIndex=" + inputBufferIndex);

            offerMuxer();
        }

        private boolean offerMuxer() {
            Log.d(TAG, "offerMuxer");

            // drainEncoder

            boolean bEOF = false;

            MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();

            while (true) {
                int encoderStatus = encoder.dequeueOutputBuffer(bufferInfo, 100000);
                if (encoderStatus == MediaCodec.INFO_TRY_AGAIN_LATER) {
                    // no output available yet
                    Log.w(TAG, "Encoder: dequeueOutputBuffer timed out!");
                    break;
                } else if (encoderStatus == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED) {
                    // not expected for an encoder
                } else if (encoderStatus == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    // should happen before receiving buffers, and should only happen once
                    if (mMuxerStarted) {
                        // Ignore
                        continue;
//	                    throw new RuntimeException("format changed twice");
                    }
                    MediaFormat newFormat = encoder.getOutputFormat();
                    Log.d(TAG, "encoder output format changed: " + newFormat);

                    // now that we have the Magic Goodies, start the muxer
                    if (muxer != null) {
                        mTrackIndex = muxer.addTrack(newFormat);
                        muxer.start();
                    }
                    mMuxerStarted = true;
                } else if (encoderStatus < 0) {
                    Log.w(TAG, "unexpected result from encoder.dequeueOutputBuffer: " +
                            encoderStatus);
                    // let's ignore it
                } else {
                    ByteBuffer encodedData = encoder.getOutputBuffer(encoderStatus);
                    if (encodedData == null) {
                        throw new RuntimeException("encoderOutputBuffer " + encoderStatus +
                                " was null");
                    }
                    if ((bufferInfo.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
                        // The codec config data was pulled out and fed to the muxer when we got
                        // the INFO_OUTPUT_FORMAT_CHANGED status.  Ignore it.
                        Log.d(TAG, "ignoring BUFFER_FLAG_CODEC_CONFIG");
                        bufferInfo.size = 0;
                    }

                    if (bufferInfo.size != 0) {
                        if (!mMuxerStarted) {
                            throw new RuntimeException("muxer hasn't started");
                        }

                        // adjust the ByteBuffer values to match BufferInfo (not needed?)
                        encodedData.position(bufferInfo.offset);
                        encodedData.limit(bufferInfo.offset + bufferInfo.size);

                        if (muxer != null) {
                            muxer.writeSampleData(mTrackIndex, encodedData, bufferInfo);
                        }
                        Log.d(TAG, "sent " + bufferInfo.size + " bytes to muxer, PTUS = " + bufferInfo.presentationTimeUs);
                    }

                    encoder.releaseOutputBuffer(encoderStatus, false);

                    if ((bufferInfo.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                        bEOF = true;
                        Log.w(TAG, "Muxer: BUFFER_FLAG_END_OF_STREAM");
                        break;      // out of while
                    }
                }
            }

            return bEOF;
        }
    }
}
