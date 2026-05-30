package ru.sv.personaltrainer;

import android.content.ContentValues;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.Image;
import android.media.ImageReader;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.media.MediaRecorder;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.provider.MediaStore;
import android.view.LayoutInflater;
import android.view.Surface;
import android.view.View;
import android.widget.TextView;

import com.google.mediapipe.tasks.components.containers.NormalizedLandmark;
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarkerResult;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

public class VideoRecorder {

    private static final String TAG = "VideoRecorder";

    private static final String VIDEO_MIME    = "video/avc";
    private static final String AUDIO_MIME    = "audio/mp4a-latm";
    private static final int    VIDEO_BITRATE = 6_000_000;
    private static final int    VIDEO_FPS     = 30;
    private static final int    VIDEO_IFRAME  = 1;

    private static final int AUDIO_SAMPLE_RATE = 44100;
    private static final int AUDIO_CHANNELS    = 2;
    private static final int AUDIO_BITRATE     = 128_000;
    private static final int AUDIO_BUFFER_SIZE = 4096;

    private int insetTop = 0;
    private int insetBottom = 0;

    private static final int[][] POSE_CONNECTIONS = {
            {0,1},{1,2},{2,3},{3,7},
            {0,4},{4,5},{5,6},{6,8},
            {9,10},
            {11,12},{11,23},{12,24},{23,24},
            {11,13},{13,15},{15,17},{15,19},{15,21},{17,19},
            {12,14},{14,16},{16,18},{16,20},{16,22},{18,20},
            {23,25},{25,27},{27,29},{27,31},{29,31},
            {24,26},{26,28},{28,30},{28,32},{30,32}
    };

    private static final int[] LEFT_LANDMARKS  = {11,13,15,17,19,21,23,25,27,29,31};
    private static final int[] RIGHT_LANDMARKS = {12,14,16,18,20,22,24,26,28,30,32};

    private volatile boolean   recording       = false;
    private volatile boolean   acceptingFrames = false;
    private final    AtomicLong startTimeUs    = new AtomicLong(-1L);

    private MediaCodec  videoEncoder;
    private Surface     encoderInputSurface;
    private ImageReader imageReader;
    private MediaCodec  audioEncoder;
    private AudioRecord audioRecord;
    private MediaMuxer  muxer;
    private String      outputPath;

    private boolean muxerStarted    = false;
    private int     videoTrackIndex = -1;
    private int     audioTrackIndex = -1;

    private HandlerThread encoderThread;
    private Handler       encoderHandler;
    private Thread        audioThread;
    private HandlerThread imageReaderThread;
    private Handler       imageReaderHandler;

    private final Paint paintLine        = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint paintPoint       = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint paintErrorPoint  = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint paintErrorStroke = new Paint(Paint.ANTI_ALIAS_FLAG);

    private final Context context;
    private final int     videoW;
    private final int     videoH;

    private View     uiOverlay;
    private TextView uiRepCount, uiPhase, uiExerciseName, uiFeedback;
    private TextView uiQuality, uiErrors, uiRecordingTimer;
    private View     uiRecordingDot, uiRecordingLayout;
    private volatile boolean uiReady = false;

    private Bitmap               pendingCameraFrame;
    private PoseLandmarkerResult pendingPoseResult;
    private List<Integer>        pendingErrorLandmarks;

    public interface RecordingCallback {
        void onRecordingStarted();
        void onRecordingSaved(String filePath);
        void onRecordingError(String error);
    }
    private RecordingCallback callback;

    public VideoRecorder(Context context, int screenWidth, int screenHeight) {
        this.context = context;
        this.videoW  = screenWidth;
        this.videoH  = screenHeight;

        paintLine.setStyle(Paint.Style.STROKE);
        paintLine.setStrokeCap(Paint.Cap.ROUND);
        paintPoint.setStyle(Paint.Style.FILL);
        paintErrorPoint.setStyle(Paint.Style.FILL);
        paintErrorStroke.setColor(Color.WHITE);
        paintErrorStroke.setStyle(Paint.Style.STROKE);
    }

    public void setCallback(RecordingCallback cb) { this.callback = cb; }

    public void prepareUIOverlay() {
        if (uiReady) return;
        LayoutInflater inflater = LayoutInflater.from(context);
        uiOverlay = inflater.inflate(R.layout.activity_exercise, null);
        measureAndLayout();

        uiRepCount        = uiOverlay.findViewById(R.id.tvRepCount);
        uiPhase           = uiOverlay.findViewById(R.id.tvPhase);
        uiExerciseName    = uiOverlay.findViewById(R.id.tvExerciseName);
        uiFeedback        = uiOverlay.findViewById(R.id.tvFeedback);
        uiQuality         = uiOverlay.findViewById(R.id.tvQuality);
        uiErrors          = uiOverlay.findViewById(R.id.tvErrors);
        uiRecordingTimer  = uiOverlay.findViewById(R.id.tvRecordingTimer);
        uiRecordingDot    = uiOverlay.findViewById(R.id.viewRecordingDot);
        uiRecordingLayout = uiOverlay.findViewById(R.id.layoutRecordingIndicator);

        uiReady = true;
    }

    private void measureAndLayout() {
        if (uiOverlay instanceof android.view.ViewGroup) {
            android.view.ViewGroup root = (android.view.ViewGroup) uiOverlay;
            for (int i = 0; i < root.getChildCount(); i++) {
                View child = root.getChildAt(i);
                android.view.ViewGroup.MarginLayoutParams params =
                        (android.view.ViewGroup.MarginLayoutParams) child.getLayoutParams();
                params.setMargins(0, 0, 0, 0);
                child.setLayoutParams(params);
            }
        }

        uiOverlay.measure(
                View.MeasureSpec.makeMeasureSpec(videoW, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(videoH, View.MeasureSpec.EXACTLY));
        uiOverlay.layout(0, 0, videoW, videoH);
    }
    public void setInsets(int top, int bottom) {
        this.insetTop = top;
        this.insetBottom = bottom;
        if (uiOverlay != null) {
            measureAndLayout();
        }
    }

    public void updateUIState(String repText, String phaseText, String feedbackText,
                              int phaseColor, String exerciseName, String quality,
                              int qualityColor, long elapsedSeconds, boolean isRec,
                              List<String> errors) {
        if (!uiReady) return;

        uiRepCount.setText(repText     != null ? repText     : "0");
        uiPhase.setText(phaseText      != null ? phaseText   : "");
        uiPhase.setTextColor(phaseColor);
        uiExerciseName.setText(exerciseName != null ? exerciseName : "");
        uiFeedback.setText(feedbackText != null ? feedbackText : "");
        uiQuality.setText(quality != null ? quality
                : context.getString(R.string.vm_quality_full));
        uiQuality.setTextColor(qualityColor);

        if (errors != null && !errors.isEmpty()) {
            uiErrors.setVisibility(View.VISIBLE);
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < errors.size() && i < 3; i++) {
                sb.append(errors.get(i));
                if (i < errors.size() - 1 && i < 2) sb.append("\n");
            }
            uiErrors.setText(sb.toString());
        } else {
            uiErrors.setVisibility(View.GONE);
        }

        if (isRec) {
            uiRecordingLayout.setVisibility(View.VISIBLE);
            uiRecordingTimer.setText(String.format(Locale.US, "%02d:%02d",
                    elapsedSeconds / 60, elapsedSeconds % 60));
            uiRecordingDot.setVisibility(
                    elapsedSeconds % 2 == 0 ? View.VISIBLE : View.INVISIBLE);
        } else {
            uiRecordingLayout.setVisibility(View.GONE);
        }

        measureAndLayout();
    }

    public boolean isRecording() { return recording;}
    public void startRecording() {
        if (recording) return;

        release();

        try {
            outputPath = createOutputFile();
            if (outputPath == null) { notifyError("Cannot create output file"); return; }

            prepareUIOverlay();

            setupVideoEncoder();
            setupAudioEncoder();
            muxer = new MediaMuxer(outputPath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);

            encoderThread = new HandlerThread("EncoderThread");
            encoderThread.start();
            encoderHandler = new Handler(encoderThread.getLooper());

            recording       = true;
            acceptingFrames = true;
            startTimeUs.set(-1L);

            startAudioRecording();
            if (callback != null) callback.onRecordingStarted();

        } catch (Exception e) {
            notifyError(e.getMessage());
            release();
        }
    }

    public void stopRecording() {
        if (!recording) return;
        recording       = false;
        acceptingFrames = false;

        new Thread(() -> {
            if (encoderHandler != null) {
                try {
                    CountDownLatch latch = new CountDownLatch(1);
                    encoderHandler.post(latch::countDown);
                    latch.await(3, TimeUnit.SECONDS);
                } catch (InterruptedException ignored) {
                    Thread.currentThread().interrupt();
                }
            }
            if (encoderThread != null) {
                encoderThread.quitSafely();
                try { encoderThread.join(2000); } catch (InterruptedException ignored) {}
                encoderThread  = null;
                encoderHandler = null;
            }

            if (audioRecord != null) {
                try { audioRecord.stop(); } catch (Exception ignored) {}
            }
            if (audioThread != null) {
                try { audioThread.join(1500); } catch (InterruptedException ignored) {}
                audioThread = null;
            }
            if (audioRecord != null) {
                try { audioRecord.release(); } catch (Exception ignored) {}
                audioRecord = null;
            }

            try { drainVideoEncoder(true);  } catch (Exception ignored) {}
            try { drainAudioEndOfStream();  } catch (Exception ignored) {}

            release();
            addToMediaStore();
        }, "StopThread").start();
    }

    public void submitFrame(Bitmap cameraFrame,
                            PoseLandmarkerResult poseResult,
                            List<Integer> errorLandmarks,
                            String repText, String phaseText, String feedbackText,
                            int phaseColor, String exerciseName,
                            String quality, int qualityColor,
                            List<String> errors) {

        if (!recording || !acceptingFrames) return;
        if (videoEncoder == null || encoderHandler == null) return;

        final long nowUs      = System.nanoTime() / 1000L;
        final long startUs    = startTimeUs.get();
        final long elapsedSec = (startUs > 0) ? (nowUs - startUs) / 1_000_000L : 0L;

        final Bitmap scaled = scaleBitmapToScreen(cameraFrame);

        synchronized (this) {
            if (pendingCameraFrame != null) pendingCameraFrame.recycle();
            pendingCameraFrame    = scaled;
            pendingPoseResult     = poseResult;
            pendingErrorLandmarks = errorLandmarks != null
                    ? new ArrayList<>(errorLandmarks) : null;
        }

        final String      repSnap      = repText;
        final String      phaseSnap    = phaseText;
        final String      feedbackSnap = feedbackText;
        final int         colorSnap    = phaseColor;
        final String      qualitySnap  = quality;
        final int         qualClr      = qualityColor;
        final String      nameSnap     = exerciseName;
        final long        elSnap       = elapsedSec;
        final List<String> errSnap     = errors;
        final long        tsUs         = nowUs;

        encoderHandler.post(() -> {
            if (!acceptingFrames) return;
            startTimeUs.compareAndSet(-1L, tsUs);
            updateUIState(repSnap, phaseSnap, feedbackSnap, colorSnap,
                    nameSnap, qualitySnap, qualClr, elSnap, true, errSnap);
            try {
                writeFrame(tsUs);
            } catch (Exception e) {
            }
        });
    }

    private Bitmap scaleBitmapToScreen(Bitmap src) {
        if (src == null) return null;
        float scale  = Math.max((float) videoW / src.getWidth(),
                (float) videoH / src.getHeight());
        float transX = (videoW - src.getWidth()  * scale) / 2f;
        float transY = (videoH - src.getHeight() * scale) / 2f;

        Matrix m = new Matrix();
        m.postScale(scale, scale);
        m.postTranslate(transX, transY);

        Bitmap result = Bitmap.createBitmap(videoW, videoH, Bitmap.Config.ARGB_8888);
        new Canvas(result).drawBitmap(src, m, null);
        return result;
    }

    private void setupVideoEncoder() throws Exception {
        MediaFormat fmt = MediaFormat.createVideoFormat(VIDEO_MIME, videoW, videoH);
        fmt.setInteger(MediaFormat.KEY_COLOR_FORMAT,
                MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface);
        fmt.setInteger(MediaFormat.KEY_BIT_RATE,          VIDEO_BITRATE);
        fmt.setInteger(MediaFormat.KEY_FRAME_RATE,        VIDEO_FPS);
        fmt.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL,  VIDEO_IFRAME);

        videoEncoder = MediaCodec.createEncoderByType(VIDEO_MIME);
        videoEncoder.configure(fmt, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
        encoderInputSurface = videoEncoder.createInputSurface();
        videoEncoder.start();

        imageReader = ImageReader.newInstance(videoW, videoH,
                android.graphics.PixelFormat.RGBA_8888, 3);

        imageReaderThread = new HandlerThread("ImageReaderThread");
        imageReaderThread.start();
        imageReaderHandler = new Handler(imageReaderThread.getLooper());

        imageReader.setOnImageAvailableListener(reader -> {
            Image image = null;
            try {
                image = reader.acquireLatestImage();
                if (image == null) return;

                Image.Plane plane  = image.getPlanes()[0];
                ByteBuffer  buffer = plane.getBuffer();
                int rowStride      = plane.getRowStride();
                int pixelStride    = plane.getPixelStride();
                int width          = image.getWidth();
                int height         = image.getHeight();

                Canvas c = encoderInputSurface.lockCanvas(null);
                if (c != null) {
                    try {
                        Bitmap bmp = Bitmap.createBitmap(width, height,
                                Bitmap.Config.ARGB_8888);
                        if (rowStride == width * pixelStride) {
                            buffer.rewind();
                            bmp.copyPixelsFromBuffer(buffer);
                        } else {
                            byte[] row  = new byte[rowStride];
                            byte[] data = new byte[width * height * 4];
                            int    off  = 0;
                            for (int y = 0; y < height; y++) {
                                buffer.position(y * rowStride);
                                buffer.get(row, 0,
                                        Math.min(rowStride, buffer.remaining()));
                                System.arraycopy(row, 0, data, off, width * pixelStride);
                                off += width * pixelStride;
                            }
                            bmp.copyPixelsFromBuffer(ByteBuffer.wrap(data));
                        }
                        c.drawBitmap(bmp, 0f, 0f, null);
                        bmp.recycle();
                    } finally {
                        encoderInputSurface.unlockCanvasAndPost(c);
                    }
                }
            } catch (Exception e) {
            } finally {
                if (image != null) image.close();
            }
            drainVideoEncoder(false);
        }, imageReaderHandler);
    }

    private void setupAudioEncoder() throws Exception {
        MediaFormat fmt = MediaFormat.createAudioFormat(
                AUDIO_MIME, AUDIO_SAMPLE_RATE, AUDIO_CHANNELS);
        fmt.setInteger(MediaFormat.KEY_BIT_RATE,    AUDIO_BITRATE);
        fmt.setInteger(MediaFormat.KEY_AAC_PROFILE,
                MediaCodecInfo.CodecProfileLevel.AACObjectLC);

        audioEncoder = MediaCodec.createEncoderByType(AUDIO_MIME);
        audioEncoder.configure(fmt, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
        audioEncoder.start();
    }

    private void startAudioRecording() {
        int minBuf = AudioRecord.getMinBufferSize(
                AUDIO_SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_STEREO,
                AudioFormat.ENCODING_PCM_16BIT);
        try {
            audioRecord = new AudioRecord(
                    MediaRecorder.AudioSource.MIC,
                    AUDIO_SAMPLE_RATE,
                    AudioFormat.CHANNEL_IN_STEREO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    Math.max(minBuf, AUDIO_BUFFER_SIZE * 4));

            if (audioRecord.getState() != AudioRecord.STATE_INITIALIZED) {
                notifyError("Microphone not available");
                return;
            }
            audioRecord.startRecording();
            audioThread = new Thread(this::audioLoop, "AudioThread");
            audioThread.start();
        } catch (SecurityException e) {
            notifyError("Microphone permission denied");
        }
    }

    private void audioLoop() {
        byte[] buf = new byte[AUDIO_BUFFER_SIZE];
        while (recording) {
            int read = audioRecord.read(buf, 0, buf.length);
            if (read > 0) feedAudioEncoder(buf, read);
        }
    }

    private void feedAudioEncoder(byte[] data, int length) {
        if (audioEncoder == null) return;
        try {
            int idx = audioEncoder.dequeueInputBuffer(0);
            if (idx >= 0) {
                ByteBuffer buf = audioEncoder.getInputBuffer(idx);
                if (buf != null) {
                    buf.clear();
                    buf.put(data, 0, length);
                    audioEncoder.queueInputBuffer(idx, 0, length,
                            System.nanoTime() / 1000L, 0);
                }
            }
            drainAudioEncoder();
        } catch (Exception ignored) {}
    }

    private synchronized void drainAudioEncoder() {
        if (audioEncoder == null) return;
        MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
        while (true) {
            int idx = audioEncoder.dequeueOutputBuffer(info, 0);
            if (idx == MediaCodec.INFO_TRY_AGAIN_LATER) break;
            if (idx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                if (!muxerStarted && muxer != null) {
                    audioTrackIndex = muxer.addTrack(audioEncoder.getOutputFormat());
                    tryStartMuxer();
                }
            } else if (idx >= 0) {
                ByteBuffer buf = audioEncoder.getOutputBuffer(idx);
                boolean isConfig =
                        (info.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0;
                if (!isConfig && buf != null && muxerStarted && info.size > 0) {
                    buf.position(info.offset);
                    buf.limit(info.offset + info.size);
                    muxer.writeSampleData(audioTrackIndex, buf, info);
                }
                audioEncoder.releaseOutputBuffer(idx, false);
                if ((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) break;
            }
        }
    }

    private void drainAudioEndOfStream() {
        if (audioEncoder == null) return;
        try {
            int idx = audioEncoder.dequeueInputBuffer(0);
            if (idx >= 0) {
                audioEncoder.queueInputBuffer(idx, 0, 0, 0,
                        MediaCodec.BUFFER_FLAG_END_OF_STREAM);
            }
            MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
            while (true) {
                int out = audioEncoder.dequeueOutputBuffer(info, 100_000);
                if (out == MediaCodec.INFO_TRY_AGAIN_LATER) break;
                if (out == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) continue;
                if (out >= 0) {
                    ByteBuffer buf = audioEncoder.getOutputBuffer(out);
                    boolean isConfig =
                            (info.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0;
                    if (!isConfig && buf != null && muxerStarted && info.size > 0) {
                        buf.position(info.offset);
                        buf.limit(info.offset + info.size);
                        muxer.writeSampleData(audioTrackIndex, buf, info);
                    }
                    audioEncoder.releaseOutputBuffer(out, false);
                    if ((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) break;
                }
            }
        } catch (Exception ignored) {}
        try { audioEncoder.stop();    } catch (Exception ignored) {}
        try { audioEncoder.release(); } catch (Exception ignored) {}
        audioEncoder = null;
    }

    private synchronized void drainVideoEncoder(boolean endOfStream) {
        if (videoEncoder == null) return;
        if (endOfStream) {
            try { videoEncoder.signalEndOfInputStream(); }
            catch (Exception ignored) {}
        }

        MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
        int safety = 300;
        while (safety-- > 0) {
            int idx = videoEncoder.dequeueOutputBuffer(info, 10_000);
            if (idx == MediaCodec.INFO_TRY_AGAIN_LATER) {
                if (!endOfStream) break;
                try { Thread.sleep(5); } catch (InterruptedException ignored) {}
            } else if (idx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                if (!muxerStarted && muxer != null) {
                    videoTrackIndex = muxer.addTrack(videoEncoder.getOutputFormat());
                    tryStartMuxer();
                }
            } else if (idx >= 0) {
                ByteBuffer buf     = videoEncoder.getOutputBuffer(idx);
                boolean    isConfig =
                        (info.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0;
                if (!isConfig && buf != null && muxerStarted && info.size > 0) {
                    buf.position(info.offset);
                    buf.limit(info.offset + info.size);
                    muxer.writeSampleData(videoTrackIndex, buf, info);
                }
                videoEncoder.releaseOutputBuffer(idx, false);
                if ((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) break;
            }
        }
    }

    private synchronized void tryStartMuxer() {
        if (muxer != null && videoTrackIndex >= 0
                && audioTrackIndex >= 0 && !muxerStarted) {
            muxer.start();
            muxerStarted = true;
        }
    }

    private void writeFrame(long tsUs) {
        if (imageReader == null || !acceptingFrames) return;

        Bitmap frame;
        PoseLandmarkerResult pose;
        List<Integer> errLm;
        synchronized (this) {
            frame              = pendingCameraFrame;
            pendingCameraFrame = null;
            pose               = pendingPoseResult;
            errLm              = pendingErrorLandmarks;
        }

        Bitmap output = Bitmap.createBitmap(videoW, videoH, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(output);

        if (frame != null && !frame.isRecycled()) {
            canvas.drawBitmap(frame, 0f, 0f, null);
        }

        if (pose != null && !pose.landmarks().isEmpty()) {
            drawPoseOnCanvas(canvas, pose, videoW, videoH, errLm);
        }

        drawUIWidgets(canvas);

        Surface readerSurface = imageReader.getSurface();
        Canvas sc = null;
        try {
            sc = readerSurface.lockCanvas(null);
            if (sc != null) {
                sc.drawBitmap(output, 0f, 0f, null);
            }
        } catch (Exception e) {
        } finally {
            if (sc != null) {
                try { readerSurface.unlockCanvasAndPost(sc); }
                catch (Exception ignored) {}
            }
            output.recycle();
            if (frame != null) frame.recycle();
        }
    }

    private void drawUIWidgets(Canvas canvas) {
        if (!uiReady) return;

        if (uiRepCount != null && uiRepCount.getVisibility() == View.VISIBLE) {
            canvas.save();
            canvas.translate(uiRepCount.getLeft(), uiRepCount.getTop());
            uiRepCount.draw(canvas);
            canvas.restore();
        }

        if (uiExerciseName != null && uiExerciseName.getVisibility() == View.VISIBLE) {
            canvas.save();
            canvas.translate(uiExerciseName.getLeft(), uiExerciseName.getTop());
            uiExerciseName.draw(canvas);
            canvas.restore();
        }

        if (uiPhase != null && uiPhase.getVisibility() == View.VISIBLE) {
            canvas.save();
            canvas.translate(uiPhase.getLeft(), uiPhase.getTop());
            uiPhase.draw(canvas);
            canvas.restore();
        }

        if (uiRecordingLayout != null && uiRecordingLayout.getVisibility() == View.VISIBLE) {
            canvas.save();
            canvas.translate(uiRecordingLayout.getLeft(), uiRecordingLayout.getTop());
            uiRecordingLayout.draw(canvas);
            canvas.restore();
        }

        View cardFeedback = uiOverlay.findViewById(R.id.cardFeedback);
        if (cardFeedback != null && cardFeedback.getVisibility() == View.VISIBLE) {
            canvas.save();
            canvas.translate(cardFeedback.getLeft(), cardFeedback.getTop());
            cardFeedback.draw(canvas);
            canvas.restore();
        }
    }

    private void drawPoseOnCanvas(Canvas canvas, PoseLandmarkerResult result,
                                  int fw, int fh, List<Integer> errorLandmarks) {
        List<NormalizedLandmark> lms = result.landmarks().get(0);

        for (int[] conn : POSE_CONNECTIONS) {
            int si = conn[0], ei = conn[1];
            if (si >= lms.size() || ei >= lms.size()) continue;

            NormalizedLandmark s = lms.get(si), e = lms.get(ei);
            if (!isVisible(s) || !isVisible(e)) continue;

            boolean isErr = errorLandmarks != null
                    && (errorLandmarks.contains(si) || errorLandmarks.contains(ei));

            paintLine.setStrokeWidth(isErr ? 6f : 4f);
            paintLine.setColor(isErr ? Color.RED : getConnectionColor(si, ei));
            canvas.drawLine(s.x() * fw, s.y() * fh, e.x() * fw, e.y() * fh, paintLine);
        }

        for (int i = 0; i < lms.size(); i++) {
            NormalizedLandmark lm = lms.get(i);
            if (!isVisible(lm)) continue;

            float x = lm.x() * fw, y = lm.y() * fh;

            if (errorLandmarks != null && errorLandmarks.contains(i)) {
                paintErrorPoint.setColor(Color.RED);
                canvas.drawCircle(x, y, 12f, paintErrorPoint);
                paintErrorStroke.setStrokeWidth(2f);
                canvas.drawCircle(x, y, 12f, paintErrorStroke);
            } else {
                paintPoint.setColor(Color.WHITE);
                canvas.drawCircle(x, y, 8f, paintPoint);
                paintPoint.setColor(getConnectionColor(i, i));
                canvas.drawCircle(x, y, 5f, paintPoint);
            }
        }
    }

    private boolean isVisible(NormalizedLandmark lm) {
        return lm.visibility().isPresent() ? lm.visibility().get() >= 0.5f : true;
    }

    private int getConnectionColor(int si, int ei) {
        if (isLeftLandmark(si)  || isLeftLandmark(ei))  return 0xFF00BFFF;
        if (isRightLandmark(si) || isRightLandmark(ei)) return 0xFFFFD700;
        return 0xFF00FF00;
    }

    private boolean isLeftLandmark(int idx) {
        for (int i : LEFT_LANDMARKS)  if (i == idx) return true;
        return false;
    }
    private boolean isRightLandmark(int idx) {
        for (int i : RIGHT_LANDMARKS) if (i == idx) return true;
        return false;
    }

    private void release() {
        if (videoEncoder != null) {
            try { videoEncoder.stop();    } catch (Exception ignored) {}
            try { videoEncoder.release(); } catch (Exception ignored) {}
            videoEncoder = null;
        }
        if (encoderInputSurface != null) {
            try { encoderInputSurface.release(); } catch (Exception ignored) {}
            encoderInputSurface = null;
        }
        if (imageReader != null) {
            try { imageReader.close(); } catch (Exception ignored) {}
            imageReader = null;
        }
        if (imageReaderThread != null) {
            imageReaderThread.quitSafely();
            try { imageReaderThread.join(1000); } catch (Exception ignored) {}
            imageReaderThread = null;
            imageReaderHandler = null;
        }
        if (muxer != null) {
            try { if (muxerStarted) muxer.stop(); } catch (Exception ignored) {}
            try { muxer.release(); }                catch (Exception ignored) {}
            muxer = null;
        }
        if (encoderThread != null) {
            encoderThread.quitSafely();
            try { encoderThread.join(1000); } catch (Exception ignored) {}
            encoderThread  = null;
            encoderHandler = null;
        }
        muxerStarted    = false;
        videoTrackIndex = -1;
        audioTrackIndex = -1;
        startTimeUs.set(-1L);
        uiReady   = false;
        uiOverlay = null;

        synchronized (this) {
            if (pendingCameraFrame != null) {
                pendingCameraFrame.recycle();
                pendingCameraFrame = null;
            }
            pendingPoseResult     = null;
            pendingErrorLandmarks = null;
        }
    }

    private String createOutputFile() {
        String ts  = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
        File   dir = context.getExternalFilesDir(Environment.DIRECTORY_MOVIES);
        if (dir == null) dir = context.getFilesDir();
        if (!dir.exists()) dir.mkdirs();
        return new File(dir, "workout_" + ts + ".mp4").getAbsolutePath();
    }

    private void addToMediaStore() {
        if (outputPath == null) return;
        File src = new File(outputPath);
        if (!src.exists() || src.length() == 0) {
            notifyError("Empty recording file");
            return;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ContentValues cv = new ContentValues();
            cv.put(MediaStore.Video.Media.DISPLAY_NAME,  src.getName());
            cv.put(MediaStore.Video.Media.MIME_TYPE,     "video/mp4");
            cv.put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/PersonalTrainer");
            cv.put(MediaStore.Video.Media.IS_PENDING,    1);

            Uri uri = context.getContentResolver()
                    .insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, cv);
            if (uri != null) {
                try (InputStream  in  = new FileInputStream(src);
                     OutputStream out = context.getContentResolver().openOutputStream(uri)) {
                    if (out != null) {
                        byte[] buf = new byte[65536];
                        int    len;
                        while ((len = in.read(buf)) > 0) out.write(buf, 0, len);
                    }
                    cv.clear();
                    cv.put(MediaStore.Video.Media.IS_PENDING, 0);
                    context.getContentResolver().update(uri, cv, null, null);
                    src.delete();
                    notifySaved(outputPath);
                } catch (Exception e) {
                    notifyError(e.getMessage());
                }
            }
        } else {
            context.sendBroadcast(new android.content.Intent(
                    android.content.Intent.ACTION_MEDIA_SCANNER_SCAN_FILE,
                    Uri.fromFile(src)));
            notifySaved(outputPath);
        }
    }

    private void notifyError(String error) {
        if (callback == null) return;
        ((android.app.Activity) context).runOnUiThread(
                () -> callback.onRecordingError(error));
    }

    private void notifySaved(String path) {
        if (callback == null) return;
        ((android.app.Activity) context).runOnUiThread(
                () -> callback.onRecordingSaved(path));
    }
}