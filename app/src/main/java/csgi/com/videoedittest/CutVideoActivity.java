package csgi.com.videoedittest;

import android.content.Intent;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.support.v7.app.ActionBarActivity;
import android.os.Bundle;
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

import java.io.File;
import java.io.IOException;
import java.text.FieldPosition;
import java.text.NumberFormat;
import java.text.ParsePosition;

import csgi.com.cts.Producer;

public class CutVideoActivity extends ActionBarActivity implements SurfaceHolder.Callback {
    private static final String TAG = "CutVideoActivity";

    private static final String SAMPLE = "/mnt/sdcard/Movies/big_buck_bunny.mp4";
    private static final String SAMPLE_OUTPUT_FMT = "/mnt/sdcard/Movies/out-%d.mp4";

    private static final int PICK_FILE_REQUEST = 1234;

    // Views
    private SurfaceView mSurfaceView;
    private Button mBtnPlayPause;
    private Button mBtnSeekPrev;
    private Button mBtnSeekNext;
    private Button mBtnSeekPrevFrame;
    private Button mBtnSeekNextFrame;
    private Button mBtnExport;
    private RangeSeekBar mRangeSeekBar;
    private TextView mDurationTV;

    private Surface mSurface;
    private Player mPlayer = null;
    private Handler mMainhandler;
    private NumberFormat mDurationFormat;

    private Uri mSourceVideoUri = null;

    private State mCurrentState;

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

        mBtnSeekNextFrame = (Button) findViewById(R.id.seek_next_frame_button);
        mBtnSeekNextFrame.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {
                seekNextFrame();
            }
        });

        mBtnSeekNext = (Button) findViewById(R.id.seek_next_button);
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

        mBtnSeekPrevFrame = (Button) findViewById(R.id.seek_prev_frame_button);
        mBtnSeekPrevFrame.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {
                seekPrevFrame();
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

        setCurrentState(IdleState);
    }

    private void setCurrentState(State state) {
        mCurrentState = state;
        mCurrentState.onChangedToThisState();
    }

    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == PICK_FILE_REQUEST && resultCode == RESULT_OK && data != null) {
            mSourceVideoUri = data.getData();
            if (mSourceVideoUri == null) {
                Toast.makeText(this, "Empty picked file uri", Toast.LENGTH_SHORT).show();
            }
        }
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

        if (mCurrentState == IdleState && mSourceVideoUri != null) {
            playVideo(mSourceVideoUri);
        }
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

    @Override
    public void onDestroy() {
        super.onDestroy();

        if (mExporter != null) {
        }
    }

    private long endPTUS = 0;
    private long startPTUS = 0;

    private void seekRangeChanged(final long start, final long end) {
        Log.d(TAG, "seekRangeChanged: " + start + ", " + end);
        if (mPlayer == null || mPlayer.getState() != Player.PlaybackState.Pause) {
            return;
        }

        if (start != startPTUS) {
            startPTUS = start;
            mPlayer.seek(start);
        } else if (end != endPTUS) {
            endPTUS = end;
            updatePosition(startPTUS, end);
        }
    }

    private void seekNextFrame() {
        if (mPlayer == null || mPlayer.getState() != Player.PlaybackState.Pause) {
            return;
        }

        mPlayer.seekNextFrame();
    }

    private void seekNext() {
        if (mPlayer == null || mPlayer.getState() != Player.PlaybackState.Pause) {
            return;
        }

        mPlayer.seekNext();
    }

    private void seekPrev() {
        if (mPlayer == null || mPlayer.getState() != Player.PlaybackState.Pause) {
            return;
        }

        mPlayer.seekPrev();
    }

    private void seekPrevFrame() {
        if (mPlayer == null || mPlayer.getState() != Player.PlaybackState.Pause) {
            return;
        }

        Toast.makeText(this, "TODO: Optimize", Toast.LENGTH_SHORT).show();

        mPlayer.seekPrevFrame();
    }

    private Producer mExporter;

    private void export() {
        Log.d(TAG, "export");

        if (mExporter != null) {
            return;
        }

        if (mPlayer != null) {
            mPlayer.stop();
            mPlayer = null;
        }

        if (mSourceVideoUri != null) {
            mExporter = new Producer(
                    this,
                    mSourceVideoUri,
                    String.format(SAMPLE_OUTPUT_FMT, System.currentTimeMillis()),
                    mRangeSeekBar.getSelectedMinValue().longValue(),
                    mRangeSeekBar.getSelectedMaxValue().longValue());
        } else {
            mExporter = new Producer(
                    SAMPLE,
                    String.format(SAMPLE_OUTPUT_FMT, System.currentTimeMillis()),
                    mRangeSeekBar.getSelectedMinValue().longValue(),
                    mRangeSeekBar.getSelectedMaxValue().longValue());
        }


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
            public void onProductionComplete(final String outputFilePath) {
                Log.d(TAG, "Exporter onProductionComplete");

                mMainhandler.post(new Runnable() {

                    @Override
                    public void run() {
                        Toast.makeText(CutVideoActivity.this, "Completed output to: " + outputFilePath, Toast.LENGTH_SHORT)
                                .show();

                        mExporter = null;
                        setCurrentState(IdleState);

                        CutVideoActivity.this.sendBroadcast(new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE, Uri.fromFile(new File(outputFilePath))));
                    }
                });
            }

            @Override
            public void onProductionError(final Exception e) {
                mMainhandler.post(new Runnable() {
                    @Override
                    public void run() {
                        Toast.makeText(CutVideoActivity.this, e.toString(), Toast.LENGTH_SHORT).show();
                    }
                });
            }
        });

        mExporter.start();

        setCurrentState(ExportingState);
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

    /**
     * Pick mp4 file
     */
    private void pickFile() {
        Intent pickIntent = new Intent(Intent.ACTION_PICK,
                android.provider.MediaStore.Video.Media.EXTERNAL_CONTENT_URI);
        pickIntent.setType("video/mp4");
        startActivityForResult(pickIntent, PICK_FILE_REQUEST);
    }

    private void playVideo(String videoFilePath) {
        if (mPlayer == null) {
            mPlayer = new Player(mSurface);

            try {
                mPlayer.setDataSource(videoFilePath);
                doPlayVideo();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private void playVideo(Uri uri) {
        if (mPlayer == null) {
            mPlayer = new Player(mSurface);

            try {
                mPlayer.setDataSource(this, uri);
                doPlayVideo();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private void doPlayVideo() {
        mPlayer.setListener(new Player.Listener() {

            @Override
            public void onPrepared() {
                mRangeSeekBar.setRangeValues(0, mPlayer.getDuration());
            }

            @Override
            public void updatePosition(long position, long duration) {
                if (endPTUS == 0) {
                    endPTUS = duration;
                }
                CutVideoActivity.this.updatePosition(position, endPTUS);
            }
        });

        mPlayer.start();

        setCurrentState(PlayingState);
    }

    interface State {
        void onChangedToThisState();
    }

    private long latestPlayPauseInvokedTime = 0;

    private State IdleState = new State() {

        @Override
        public void onChangedToThisState() {
            mBtnExport.setEnabled(false);
            mBtnSeekNext.setEnabled(false);
            mBtnSeekNextFrame.setEnabled(false);
            mBtnSeekPrev.setEnabled(false);
            mBtnSeekPrevFrame.setEnabled(false);
            mBtnPlayPause.setEnabled(true);
            mRangeSeekBar.setEnabled(false);

            mBtnPlayPause.setText("Pick");

            mBtnPlayPause.setOnClickListener(new View.OnClickListener() {

                @Override
                public void onClick(View v) {
                    if (System.currentTimeMillis() - latestPlayPauseInvokedTime < 1000) {
                        return;
                    }
                    latestPlayPauseInvokedTime = System.currentTimeMillis();

                    pickFile();
                    return;
                }
            });
        }
    };

    private State PlayingState = new State() {

        @Override
        public void onChangedToThisState() {
            mBtnExport.setEnabled(false);
            mBtnSeekNext.setEnabled(false);
            mBtnSeekNextFrame.setEnabled(false);
            mBtnSeekPrev.setEnabled(false);
            mBtnSeekPrevFrame.setEnabled(false);
            mBtnPlayPause.setEnabled(true);
            mRangeSeekBar.setEnabled(false);

            mBtnPlayPause.setText("Pause");

            mBtnPlayPause.setOnClickListener(new View.OnClickListener() {

                @Override
                public void onClick(View v) {
                    if (System.currentTimeMillis() - latestPlayPauseInvokedTime < 1000) {
                        return;
                    }
                    latestPlayPauseInvokedTime = System.currentTimeMillis();

                    if (mPlayer != null) {
                        mPlayer.pause();

                        setCurrentState(PauseState);
                    }
                }
            });
        }
    };

    private State PauseState = new State() {

        @Override
        public void onChangedToThisState() {
            mBtnExport.setEnabled(true);
            mBtnSeekNext.setEnabled(true);
            mBtnSeekNextFrame.setEnabled(true);
            mBtnSeekPrev.setEnabled(true);
            mBtnSeekPrevFrame.setEnabled(true);
            mBtnPlayPause.setEnabled(true);
            mRangeSeekBar.setEnabled(true);

            mBtnPlayPause.setText("Resume");

            mBtnPlayPause.setOnClickListener(new View.OnClickListener() {

                @Override
                public void onClick(View v) {
                    if (System.currentTimeMillis() - latestPlayPauseInvokedTime < 1000) {
                        return;
                    }
                    latestPlayPauseInvokedTime = System.currentTimeMillis();

                    if (mPlayer != null) {
                        mPlayer.start();

                        setCurrentState(PlayingState);
                    }
                }
            });
        }
    };

    private State ExportingState = new State() {

        @Override
        public void onChangedToThisState() {
            mBtnExport.setEnabled(false);
            mBtnSeekNext.setEnabled(false);
            mBtnSeekNextFrame.setEnabled(false);
            mBtnSeekPrev.setEnabled(false);
            mBtnSeekPrevFrame.setEnabled(false);
            mBtnPlayPause.setEnabled(false);
            mRangeSeekBar.setEnabled(false);

            mBtnPlayPause.setText("Pick");

            mBtnPlayPause.setOnClickListener(null);
        }
    };


}
