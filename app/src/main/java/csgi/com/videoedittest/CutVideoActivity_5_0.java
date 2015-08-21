package csgi.com.videoedittest;

import android.annotation.TargetApi;
import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.support.v7.app.ActionBarActivity;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import org.florescu.android.rangeseekbar.RangeSeekBar;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.text.FieldPosition;
import java.text.NumberFormat;
import java.text.ParsePosition;

import csgi.com.cts.Producer;

public class CutVideoActivity_5_0 extends ActionBarActivity implements SurfaceHolder.Callback {
    private static final String TAG = "CutVideoActivity";

    private static final String SAMPLE = "/mnt/sdcard/Movies/big_buck_bunny.mp4";
    private static final String SAMPLE_OUTPUT = "/mnt/sdcard/Movies/out.mp4";

    enum PlaybackState {
        Playing,
        Pause,
        Stop,
    }

    // Views
    private SurfaceView mSurfaceView;
    private Button mBtnPlayPause;
    private Button mBtnSeekPrev;
    private Button mBtnSeekNext;
    private Button mBtnExport;
    private RangeSeekBar mRangeSeekBar;
    private TextView mDurationTV;

    private Surface mSurface;
    private Player mPlayer = null;
    private Handler mMainhandler;
    private NumberFormat mDurationFormat;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_cut_video);

        Log.w(TAG, "test video path = " + SAMPLE);

        mMainhandler = new Handler(Looper.getMainLooper());

        mDurationFormat = new NumberFormat() {
            long microseconds = 1000 * 1000;
            @Override
            public StringBuffer format(double value, StringBuffer buffer, FieldPosition field) {
                return format((long) value, buffer, field);
            }

            @Override
            public StringBuffer format(long value, StringBuffer buffer, FieldPosition field) {
                long hour = value / 3600 / microseconds;
                value -= hour * 3600 * microseconds;
                long minutes = value / 60 / microseconds;
                value -= minutes * 60 * microseconds;
                long seconds = value / microseconds;
                value -= seconds * microseconds;
                long milliseconds = value / 1000;
                return buffer.append(String.format("%02d:", hour)).append(String.format("%02d:", minutes))
                        .append(String.format("%02d.", seconds)).append(String.format("%03d", milliseconds));
            }

            @Override
            public Number parse(String string, ParsePosition position) {
                return null;
            }
        };

        mSurfaceView = (SurfaceView) findViewById(R.id.surface_view);
        mSurfaceView.getHolder().addCallback(this);

        mDurationTV = (TextView) findViewById(R.id.duration_tv);

        mBtnPlayPause = (Button) findViewById(R.id.play_pause_button);

        mBtnPlayPause.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {
                playPause();
            }

        });

        mBtnSeekNext = (Button) findViewById(R.id.seek_next_frame_button);
        mBtnSeekNext.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {
                seekNext();
            }
        });

        mBtnSeekPrev = (Button) findViewById(R.id.seek_prev_button);
        mBtnSeekPrev.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {
                seekPrev();
            }
        });

        mBtnExport = (Button) findViewById(R.id.export_button);
        mBtnExport.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {
                export();
            }
        });

        mRangeSeekBar = (RangeSeekBar) findViewById(R.id.range_seek_bar);
        mRangeSeekBar.setNotifyWhileDragging(true);
        mRangeSeekBar.setOnRangeSeekBarChangeListener(new RangeSeekBar.OnRangeSeekBarChangeListener() {
            @Override
            public void onRangeSeekBarValuesChanged(RangeSeekBar rangeSeekBar, Object o, Object t1) {
                seekRangeChanged(rangeSeekBar.getSelectedMinValue().longValue(), rangeSeekBar.getSelectedMaxValue().longValue());
            }
        });
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_cut_video, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        mSurface = holder.getSurface();
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {

    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        if (mPlayer != null) {
            mPlayer.stop();
        }
    }

    private long latestPlayPauseInvokedTime = 0;
    private void playPause() {

        // Prevent double taps.
        if (System.currentTimeMillis() - latestPlayPauseInvokedTime < 1000) {
            return;
        }
        latestPlayPauseInvokedTime = System.currentTimeMillis();

        if (mPlayer == null) {
            mPlayer = new Player(mSurface);
            mPlayer.start();
            mBtnPlayPause.setText("Pause");
        } else if (mPlayer.getState() == PlaybackState.Playing) {
            mPlayer.pause();
            mBtnPlayPause.setText("Resume");
        } else if (mPlayer.getState() == PlaybackState.Pause) {
            mPlayer.start();
            mBtnPlayPause.setText("Pause");
        }
    }

    private void seekRangeChanged(final long start, final long end) {
        Log.d(TAG, "seekRangeChanged: " + start + ", " + end);
        if (mPlayer == null || mPlayer.getState() != PlaybackState.Pause) {
            return;
        }

        mPlayer.seek(start);
    }

    private void seekNext() {
        if (mPlayer == null || mPlayer.getState() != PlaybackState.Pause) {
            return;
        }

        mPlayer.seekNextFrame();
    }

    private void seekPrev() {
        if (mPlayer == null || mPlayer.getState() != PlaybackState.Pause) {
            return;
        }

        mPlayer.seekPrev();
    }

//    private Remuxer mRemuxer;

    private Producer mExporter;

    private void export() {
        Log.d(TAG, "export");

        if (mExporter != null) {
            return;
        }

        if (mPlayer != null) {
            mPlayer.stop();
            mPlayer = null;

            mBtnPlayPause.setText("Start");
        }

        mExporter = new Producer(SAMPLE, SAMPLE_OUTPUT,
                mRangeSeekBar.getSelectedMinValue().longValue(),
                mRangeSeekBar.getSelectedMaxValue().longValue());
        mExporter.setListener(new Producer.Listener() {

            @Override
            public void onProgressUpdate(final double percentage) {
                Log.d(TAG, "Exporter onProgressUpdate: " + percentage);

                mMainhandler.post(new Runnable() {

                    @Override
                    public void run() {
                        mDurationTV.setText(String.format("%.2f%%", percentage * 100));
                    }
                });
            }

            @Override
            public void onProductionComplete(String outputFilePath) {

            }

            @Override
            public void onProductionError(Exception e) {

            }
        });

        mExporter.start();
    }

    private void updatePosition(final long position, final long duration) {
        mMainhandler.post(new Runnable() {

            @Override
            public void run() {
                NumberFormat numberFormat = NumberFormat.getNumberInstance();
                mDurationTV.setText(mDurationFormat.format(position) + "/" + mDurationFormat.format(duration));
            }
        });
    }

    private class Player {

        private static final String TAG = "Player";

        private MediaExtractor extractor;
        private MediaCodec decoder;
        private Surface surface;
        private long duration;

        private Thread playbackThread;

        private PlaybackState mPlaybackState = PlaybackState.Pause;

        public Player(Surface surface) {
            this.surface = surface;

            init();
        }

        public PlaybackState getState() {
            return mPlaybackState;
        }

        private void init() {
            try {
                extractor = new MediaExtractor();

                extractor.setDataSource(SAMPLE);

                for (int i = 0; i < extractor.getTrackCount(); i++) {
                    MediaFormat format = extractor.getTrackFormat(i);
                    String mime = format.getString(MediaFormat.KEY_MIME);
                    if (mime.startsWith("video/")) {
                        extractor.selectTrack(i);
                        decoder = MediaCodec.createDecoderByType(mime);
                        decoder.configure(format, surface, null, 0);

                        duration = format.getLong(MediaFormat.KEY_DURATION);
                        mRangeSeekBar.setRangeValues(0, duration);
                        break;
                    }

                }

                updatePosition(0, duration);

                if (decoder == null) {
                    Log.e(TAG, "Can't find video info!");
                    return;
                }

                decoder.start();

            } catch (IOException e) {
                e.printStackTrace();
                Toast.makeText(CutVideoActivity_5_0.this, "Init error", Toast.LENGTH_SHORT).show();
            }
        }

        private void start() {
            playbackThread = new PlaybackThread();
            playbackThread.start();

            mPlaybackState = PlaybackState.Playing;
        }

        private void pause() {
            mPlaybackState = PlaybackState.Pause;
            playbackThread.interrupt();
        }

        private void stop() {
            mPlaybackState = PlaybackState.Stop;
            playbackThread.interrupt();
        }

        private void onPlaybackThreadFinish() {
            if (mPlaybackState == PlaybackState.Stop) {
                releaseCodec();
            }
        }

        private void releaseCodec() {
            decoder.stop();
            decoder.release();
            extractor.release();
        }

        private void seek(long position) {

            decoder.flush();

            extractor.seekTo(position, MediaExtractor.SEEK_TO_CLOSEST_SYNC);

            decodeFrame();
        }

        private void seekNextFrame() {
            Log.d(TAG, "seekNextFrame");
//            decoder.flush();
//            extractor.seekTo(extractor.getSampleTime() + 30000, MediaExtractor.SEEK_TO_NEXT_SYNC);
            decodeFrame();
        }

        private void seekPrev() {
            Log.d(TAG, "seekPrev");
            decoder.flush();
            extractor.seekTo(extractor.getSampleTime() - 100000, MediaExtractor.SEEK_TO_PREVIOUS_SYNC);
            decodeFrame();
        }

        @TargetApi(Build.VERSION_CODES.LOLLIPOP)
        private void decodeFrame() {
            while(true) {
                int inIndex = decoder.dequeueInputBuffer(10000);
                if (inIndex >= 0) {
                    ByteBuffer buffer = decoder.getInputBuffer(inIndex);
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
                        break;
                    default:
                        ByteBuffer buffer = decoder.getOutputBuffer(outIndex);
                        decoder.releaseOutputBuffer(outIndex, true);

                        updatePosition(info.presentationTimeUs, duration);

                        return;
                }
            }
        }

        private class PlaybackThread extends Thread {
            @TargetApi(Build.VERSION_CODES.LOLLIPOP)
            @Override
            public void run() {

                boolean isEOS = false;
                long startMs = System.currentTimeMillis() - extractor.getSampleTime() / 1000;

                while (!Thread.interrupted()) {
                    if (!isEOS) {
                        int inIndex = decoder.dequeueInputBuffer(10000);
                        if (inIndex >= 0) {
                            ByteBuffer buffer = decoder.getInputBuffer(inIndex);
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
                            break;
                        default:
                            ByteBuffer buffer = decoder.getOutputBuffer(outIndex);
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

                            updatePosition(info.presentationTimeUs, duration);

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
}
