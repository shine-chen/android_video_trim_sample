package csgi.com.cts;

import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.media.MediaMuxer;

import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * Created by Shine on 8/12/15.
 */
public class Remuxer {

    public void setListener(RemuxerListener listener) {
        this.listener = listener;
    }

    public interface RemuxerListener {
        void onProgressUpdate(double percentage);
    }

    private static final String TAG = "Remuxer";

    private final String inputFilePath;
    private final String outputFilePath;
    private final long startPosition;
    private final long endPosition;

    private RemuxerListener listener;

    private ExportThread exportThread;

    private long duration;

    public Remuxer(String input, String output, long start, long end) {
        inputFilePath = input;
        outputFilePath = output;
        startPosition = start;
        endPosition = end;
    }

    public void start() {
        exportThread = new ExportThread();
        exportThread.start();
    }

    private class ExportThread extends Thread {
        private MediaExtractor extractor = null;
        private MediaMuxer muxer = null;

        private int videoTrackIndex = -1;

        @Override
        public void run() {
            try {
                extractor = new MediaExtractor();
                extractor.setDataSource(inputFilePath);

                muxer = new MediaMuxer(outputFilePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);

                // Find video track
                for (int i = 0; i < extractor.getTrackCount(); i++) {
                    MediaFormat format = extractor.getTrackFormat(i);
                    String mime = format.getString(MediaFormat.KEY_MIME);
                    if (mime.startsWith("video/")) {
                        extractor.selectTrack(i);
                        duration = format.getLong(MediaFormat.KEY_DURATION);
                        videoTrackIndex = muxer.addTrack(format);
                        break;
                    }
                }

                muxer.start();

                extractor.seekTo(startPosition, MediaExtractor.SEEK_TO_CLOSEST_SYNC);

                long actualStartPosition = extractor.getSampleTime();
                ByteBuffer buffer = ByteBuffer.allocate(1024 * 1024);

                while(true) {
                    MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();
                    int sampleSize = extractor.readSampleData(buffer, 0);
                    if (sampleSize < 0) {
                        // EOS
                        break;
                    }

                    bufferInfo.offset = 0;
                    bufferInfo.size = sampleSize;
                    bufferInfo.presentationTimeUs = extractor.getSampleTime() - actualStartPosition;
                    muxer.writeSampleData(videoTrackIndex, buffer, bufferInfo);

                    extractor.advance();
                }

                muxer.stop();
                muxer.release();
                extractor.release();

            } catch (IOException exception) {
                exception.printStackTrace();
            }
        }
    }
}
