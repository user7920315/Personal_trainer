package ru.sv.personaltrainer;

import android.app.Activity;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.provider.MediaStore;
import android.util.Log;

import com.google.mediapipe.tasks.components.containers.NormalizedLandmark;
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarkerResult;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class VideoRecorder {

    private static final String TAG = "VideoRecorder";
    private static final String MIME_TYPE = "video/avc";
    private static final int BIT_RATE = 8_000_000;
    private static final int FRAME_RATE = 30;
    private static final int I_FRAME_INTERVAL = 1;
    private static final int VIDEO_WIDTH = 720;
    private static final int VIDEO_HEIGHT = 1280;


    private static final int COLOR_LEFT_LIMB = 0xFF00BFFF; // голубой
    private static final int COLOR_RIGHT_LIMB = 0xFFFFD700; // жёлтый
    private static final int COLOR_CENTER = 0xFF00FF00; // зелёный
    private static final int COLOR_ERROR_LINE = 0xFFFF0000; // красный


    private static final int[][] POSE_CONNECTIONS = {
            {0, 1}, {1, 2}, {2, 3}, {3, 7},
            {0, 4}, {4, 5}, {5, 6}, {6, 8},
            {9, 10},
            {11, 12}, {11, 23}, {12, 24}, {23, 24},
            {11, 13}, {13, 15}, {15, 17}, {15, 19}, {15, 21}, {17, 19},
            {12, 14}, {14, 16}, {16, 18}, {16, 20}, {16, 22}, {18, 20},
            {23, 25}, {25, 27}, {27, 29}, {27, 31}, {29, 31},
            {24, 26}, {26, 28}, {28, 30}, {28, 32}, {30, 32}
    };

    private static final int[] LEFT_LANDMARKS = {
            11, 13, 15, 17, 19, 21, 23, 25, 27, 29, 31
    };
    private static final int[] RIGHT_LANDMARKS = {
            12, 14, 16, 18, 20, 22, 24, 26, 28, 30, 32
    };


    private volatile boolean isRecording = false;
    private volatile boolean acceptingFrames = false;
    private volatile long startTimeUs = -1;

    private boolean muxerStarted = false;
    private int videoTrackIndex = -1;


    private MediaCodec encoder;
    private android.view.Surface encoderSurface;
    private MediaMuxer muxer;
    private String outputFilePath;

    private HandlerThread encoderThread;
    private Handler encoderHandler;

    private final Paint paintBg;
    private final Paint paintText;
    private final Paint paintRec;
    private final Paint paintLine;
    private final Paint paintPoint;
    private final Paint paintErrorPoint;
    private final Paint paintErrorStroke;
    private final Paint paintGlow;
    private final Paint paintDivider;


    private final Context context;

    private volatile String exerciseNameForRecord = null;
    private volatile String qualityForRecord = "●●●●●";
    private volatile int qualityColorForRecord = 0xFF00FF88;


    public interface RecordingCallback {
        void onRecordingStarted();

        void onRecordingSaved(String filePath);

        void onRecordingError(String error);
    }

    private RecordingCallback callback;


    public VideoRecorder(Context context) {
        this.context = context;

        paintBg = new Paint(Paint.ANTI_ALIAS_FLAG);
        paintBg.setStyle(Paint.Style.FILL);

        paintText = new Paint(Paint.ANTI_ALIAS_FLAG);
        paintText.setColor(Color.WHITE);
        paintText.setTypeface(Typeface.DEFAULT_BOLD);
        paintText.setShadowLayer(4f, 1f, 1f, Color.BLACK);

        paintRec = new Paint(Paint.ANTI_ALIAS_FLAG);
        paintRec.setColor(Color.RED);
        paintRec.setStyle(Paint.Style.FILL);

        paintLine = new Paint(Paint.ANTI_ALIAS_FLAG);
        paintLine.setStyle(Paint.Style.STROKE);
        paintLine.setStrokeWidth(5f);
        paintLine.setStrokeCap(Paint.Cap.ROUND);

        paintPoint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paintPoint.setStyle(Paint.Style.FILL);

        paintErrorPoint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paintErrorPoint.setStyle(Paint.Style.FILL);

        paintErrorStroke = new Paint(Paint.ANTI_ALIAS_FLAG);
        paintErrorStroke.setColor(Color.WHITE);
        paintErrorStroke.setStyle(Paint.Style.STROKE);
        paintErrorStroke.setStrokeWidth(2.5f);

        paintGlow = new Paint(Paint.ANTI_ALIAS_FLAG);
        paintGlow.setStyle(Paint.Style.FILL);

        paintDivider = new Paint(Paint.ANTI_ALIAS_FLAG);
        paintDivider.setStyle(Paint.Style.STROKE);
        paintDivider.setStrokeWidth(1f);
    }

    public void setCallback(RecordingCallback callback) {
        this.callback = callback;
    }


    public void startRecording() {
        if (isRecording) return;

        try {
            outputFilePath = createOutputFile();
            if (outputFilePath == null) {
                notifyError("Не удалось создать файл");
                return;
            }

            setupEncoder();

            encoderThread = new HandlerThread("EncoderThread");
            encoderThread.start();
            encoderHandler = new Handler(encoderThread.getLooper());

            isRecording = true;
            acceptingFrames = true;
            startTimeUs = -1;

            Log.d(TAG, "Запись начата: " + outputFilePath);
            if (callback != null) callback.onRecordingStarted();

        } catch (Exception e) {
            Log.e(TAG, "Ошибка старта: " + e.getMessage());
            notifyError(e.getMessage());
            release();
        }
    }

    private void setupEncoder() throws Exception {
        MediaFormat format = MediaFormat.createVideoFormat(
                MIME_TYPE, VIDEO_WIDTH, VIDEO_HEIGHT);

        format.setInteger(
                MediaFormat.KEY_COLOR_FORMAT,
                MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface);
        format.setInteger(MediaFormat.KEY_BIT_RATE, BIT_RATE);
        format.setInteger(MediaFormat.KEY_FRAME_RATE, FRAME_RATE);
        format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, I_FRAME_INTERVAL);

        encoder = MediaCodec.createEncoderByType(MIME_TYPE);
        encoder.configure(format, null, null,
                MediaCodec.CONFIGURE_FLAG_ENCODE);

        encoderSurface = encoder.createInputSurface();
        encoder.start();

        muxer = new MediaMuxer(outputFilePath,
                MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);

        Log.d(TAG, "Encoder готов: " + VIDEO_WIDTH + "x" + VIDEO_HEIGHT);
    }


    public void submitFrame(
            Bitmap cameraFrame,
            PoseLandmarkerResult poseResult,
            List<Integer> errorLandmarks,
            String repText,
            String phaseText,
            String feedbackText,
            int phaseColor,
            String exerciseName,
            String quality,
            int qualityColor,
            long timestampNs) {

        if (!isRecording || !acceptingFrames) return;
        if (encoder == null) return;
        if (cameraFrame == null || cameraFrame.isRecycled()) return;
        if (encoderHandler == null) return;


        this.exerciseNameForRecord = exerciseName;
        this.qualityForRecord = quality;
        this.qualityColorForRecord = qualityColor;

        final long tsUs = timestampNs / 1000L;

        final Bitmap copy;
        try {
            copy = cameraFrame.copy(Bitmap.Config.ARGB_8888, false);
        } catch (Exception e) {
            Log.e(TAG, "Bitmap copy: " + e.getMessage());
            return;
        }


        final PoseLandmarkerResult resultCopy = poseResult;
        final List<Integer> errorsCopy = errorLandmarks != null
                ? new java.util.ArrayList<>(errorLandmarks)
                : null;


        final String repSnap = repText;
        final String phaseSnap = phaseText;
        final String feedbackSnap = feedbackText;
        final int colorSnap = phaseColor;
        final String qualitySnap = quality;
        final int qualColorSnap = qualityColor;
        final String nameSnap = exerciseName;

        encoderHandler.post(() -> {
            if (!acceptingFrames) {
                if (!copy.isRecycled()) copy.recycle();
                return;
            }
            try {
                writeFrameToSurface(
                        copy, resultCopy, errorsCopy,
                        repSnap, phaseSnap, feedbackSnap,
                        colorSnap, nameSnap,
                        qualitySnap, qualColorSnap, tsUs);
            } catch (Exception e) {
                Log.e(TAG, "submitFrame: " + e.getMessage());
            } finally {
                if (!copy.isRecycled()) copy.recycle();
            }
        });
    }


    private void writeFrameToSurface(
            Bitmap cameraFrame,
            PoseLandmarkerResult poseResult,
            List<Integer> errorLandmarks,
            String repText, String phaseText,
            String feedbackText, int phaseColor,
            String exerciseName,
            String quality, int qualityColor,
            long tsUs) {

        if (encoderSurface == null || !acceptingFrames) return;

        Canvas canvas = null;
        try {
            canvas = encoderSurface.lockCanvas(null);
            if (canvas == null) return;


            canvas.drawColor(Color.BLACK);


            drawCameraFrame(canvas, cameraFrame);

            if (poseResult != null
                    && !poseResult.landmarks().isEmpty()) {
                drawSkeleton(canvas, poseResult, errorLandmarks);
            }

            if (startTimeUs < 0) startTimeUs = tsUs;
            drawUI(canvas, repText, phaseText,
                    feedbackText, phaseColor,
                    exerciseName, quality, qualityColor, tsUs);

        } catch (Exception e) {
            Log.e(TAG, "writeFrame: " + e.getMessage());
            return;
        } finally {
            if (canvas != null && encoderSurface != null) {
                try {
                    encoderSurface.unlockCanvasAndPost(canvas);
                } catch (Exception e) {
                    Log.e(TAG, "unlockCanvas: " + e.getMessage());
                }
            }
        }

        drainEncoder(false);
    }

    private void drawCameraFrame(Canvas canvas, Bitmap frame) {
        if (frame == null || frame.isRecycled()) return;

        int cW = canvas.getWidth();
        int cH = canvas.getHeight();
        int fW = frame.getWidth();
        int fH = frame.getHeight();

        float scale = Math.max((float) cW / fW, (float) cH / fH);
        float sw = fW * scale;
        float sh = fH * scale;
        float dx = (cW - sw) / 2f;
        float dy = (cH - sh) / 2f;

        canvas.drawBitmap(frame, null,
                new RectF(dx, dy, dx + sw, dy + sh), null);
    }


    private void drawSkeleton(Canvas canvas,
                              PoseLandmarkerResult poseResult,
                              List<Integer> errorLandmarks) {

        List<NormalizedLandmark> landmarks =
                poseResult.landmarks().get(0);

        float w = VIDEO_WIDTH;
        float h = VIDEO_HEIGHT;

        for (int[] conn : POSE_CONNECTIONS) {
            int si = conn[0];
            int ei = conn[1];

            if (si >= landmarks.size()
                    || ei >= landmarks.size()) continue;

            NormalizedLandmark start = landmarks.get(si);
            NormalizedLandmark end = landmarks.get(ei);

            if (!isLandmarkVisible(start)
                    || !isLandmarkVisible(end)) continue;

            float x1 = start.x() * w;
            float y1 = start.y() * h;
            float x2 = end.x() * w;
            float y2 = end.y() * h;

            boolean startErr = isError(errorLandmarks, si);
            boolean endErr = isError(errorLandmarks, ei);

            if (startErr || endErr) {
                paintLine.setColor(COLOR_ERROR_LINE);
                paintLine.setStrokeWidth(6f);
            } else {
                paintLine.setColor(
                        getConnectionColor(si, ei));
                paintLine.setStrokeWidth(5f);
            }
            canvas.drawLine(x1, y1, x2, y2, paintLine);
        }

        for (int i = 0; i < landmarks.size(); i++) {
            NormalizedLandmark lm = landmarks.get(i);
            if (!isLandmarkVisible(lm)) continue;

            float x = lm.x() * w;
            float y = lm.y() * h;

            if (isError(errorLandmarks, i)) {
                drawErrorPoint(canvas, x, y);
            } else {
                drawNormalPoint(canvas, x, y, i);
            }
        }
    }

    private void drawNormalPoint(Canvas canvas,
                                 float x, float y, int index) {
        float radius = isKeyLandmark(index) ? 10f : 7f;

        paintPoint.setColor(0x44000000);
        canvas.drawCircle(x + 1f, y + 1f, radius, paintPoint);

        paintPoint.setColor(Color.WHITE);
        canvas.drawCircle(x, y, radius, paintPoint);

        paintPoint.setColor(0xAAFFFFFF);
        canvas.drawCircle(
                x - radius * 0.3f,
                y - radius * 0.3f,
                radius * 0.3f,
                paintPoint);
    }

    private void drawErrorPoint(Canvas canvas, float x, float y) {
        float radius = 14f;

        try {
            RadialGradient gradient = new RadialGradient(
                    x, y, radius * 2f,
                    new int[]{0x88FF0000, 0x44FF0000, 0x00FF0000},
                    new float[]{0f, 0.5f, 1f},
                    Shader.TileMode.CLAMP);
            paintGlow.setShader(gradient);
            canvas.drawCircle(x, y, radius * 2f, paintGlow);
            paintGlow.setShader(null);
        } catch (Exception e) {
            Log.w(TAG, "RadialGradient: " + e.getMessage());
        }

        paintErrorPoint.setColor(0xFFFF3333);
        canvas.drawCircle(x, y, radius, paintErrorPoint);

        canvas.drawCircle(x, y, radius, paintErrorStroke);

        float size = 7f;
        Paint cross = new Paint(Paint.ANTI_ALIAS_FLAG);
        cross.setColor(Color.WHITE);
        cross.setStrokeWidth(2.5f);
        cross.setStrokeCap(Paint.Cap.ROUND);
        canvas.drawLine(x - size, y - size,
                x + size, y + size, cross);
        canvas.drawLine(x + size, y - size,
                x - size, y + size, cross);
    }

    private boolean isLandmarkVisible(NormalizedLandmark lm) {
        if (lm.visibility().isPresent()) {
            return lm.visibility().get() >= 0.5f;
        }
        return true;
    }

    private boolean isError(List<Integer> errors, int index) {
        return errors != null && errors.contains(index);
    }

    private boolean isKeyLandmark(int index) {
        return index == 11 || index == 12
                || index == 23 || index == 24
                || index == 25 || index == 26
                || index == 13 || index == 14;
    }

    private int getConnectionColor(int si, int ei) {
        if (isLeftLandmark(si) || isLeftLandmark(ei))
            return COLOR_LEFT_LIMB;
        if (isRightLandmark(si) || isRightLandmark(ei))
            return COLOR_RIGHT_LIMB;
        return COLOR_CENTER;
    }

    private boolean isLeftLandmark(int idx) {
        for (int i : LEFT_LANDMARKS)
            if (i == idx) return true;
        return false;
    }

    private boolean isRightLandmark(int idx) {
        for (int i : RIGHT_LANDMARKS)
            if (i == idx) return true;
        return false;
    }

    private void drawUI(Canvas canvas,
                        String repText,
                        String phaseText,
                        String feedbackText,
                        int phaseColor,
                        String exerciseName,
                        String quality,
                        int qualityColor,
                        long tsUs) {

        final float density = VIDEO_WIDTH / 360f;

        final int W = VIDEO_WIDTH;
        final int H = VIDEO_HEIGHT;

        drawRepCounter(canvas, repText, density, W);

        drawExerciseName(canvas, exerciseName, density, W);

        drawPhase(canvas, phaseText, phaseColor, density, W);

        drawRecordingIndicator(canvas, tsUs, density);

        drawFeedbackCard(canvas, feedbackText,
                quality, qualityColor, density, W, H);
    }


    private void drawRepCounter(Canvas canvas, String text,
                                float density, int W) {

        float textSize = sp(20, density);
        float padding = dp(10, density);
        float marginT = dp(16, density);
        float marginS = dp(16, density);

        paintText.setTextSize(textSize);
        paintText.setColor(Color.WHITE);
        paintText.setTypeface(Typeface.DEFAULT_BOLD);

        float textW = paintText.measureText(text);
        float left = marginS;
        float top = marginT;
        float right = left + textW + padding * 2;
        float bot = top + textSize + padding * 2;

        paintBg.setColor(0xBB000000);
        canvas.drawRoundRect(left, top, right, bot,
                dp(8, density), dp(8, density), paintBg);

        canvas.drawText(text,
                left + padding,
                top + padding + textSize * 0.85f,
                paintText);
    }


    private void drawExerciseName(Canvas canvas, String name,
                                  float density, int W) {
        if (name == null || name.isEmpty()) return;

        float textSize = sp(16, density);
        float padding = dp(10, density);
        float marginT = dp(16, density);
        float marginEnd = dp(16, density);

        paintText.setTextSize(textSize);
        paintText.setColor(Color.WHITE);
        paintText.setTypeface(Typeface.DEFAULT_BOLD);

        float textW = paintText.measureText(name);
        float right = W - marginEnd;
        float left = right - textW - padding * 2;
        float top = marginT;
        float bot = top + textSize + padding * 2;

        paintBg.setColor(0xBBE94560);
        canvas.drawRoundRect(left, top, right, bot,
                dp(8, density), dp(8, density), paintBg);

        canvas.drawText(name,
                left + padding,
                top + padding + textSize * 0.85f,
                paintText);
    }


    private void drawPhase(Canvas canvas, String text,
                           int color, float density, int W) {
        if (text == null || text.isEmpty()) return;

        float textSize = sp(14, density);
        float padding = dp(8, density);
        float marginT = dp(16, density);

        paintText.setTextSize(textSize);
        paintText.setColor(color);
        paintText.setTypeface(Typeface.DEFAULT_BOLD);

        float textW = paintText.measureText(text);
        float left = (W - textW) / 2f - padding;
        float top = marginT;
        float right = left + textW + padding * 2;
        float bot = top + textSize + padding * 2;

        paintBg.setColor(0xBB003322);
        canvas.drawRoundRect(left, top, right, bot,
                dp(8, density), dp(8, density), paintBg);

        canvas.drawText(text,
                left + padding,
                top + padding + textSize * 0.85f,
                paintText);
    }


    private void drawRecordingIndicator(Canvas canvas,
                                        long tsUs,
                                        float density) {
        long elapsed = (startTimeUs > 0)
                ? (tsUs - startTimeUs) / 1_000_000L : 0L;


        if (elapsed % 2 != 0) return;

        float marginT = dp(64, density);
        float marginS = dp(16, density);
        float padding = dp(8, density);
        float dotSize = dp(10, density);
        float dotMarginEnd = dp(6, density);
        float textSize = sp(13, density);

        int mm = (int) (elapsed / 60);
        int ss = (int) (elapsed % 60);
        String ts = String.format(Locale.US, "%02d:%02d", mm, ss);

        paintText.setTextSize(textSize);
        paintText.setColor(Color.WHITE);
        paintText.setTypeface(Typeface.DEFAULT_BOLD);

        float timeW = paintText.measureText(ts);
        float blockW = padding + dotSize + dotMarginEnd
                + timeW + padding;
        float blockH = Math.max(dotSize, textSize) + padding * 2;

        float left = marginS;
        float top = marginT;

        paintBg.setColor(0xBB000000);
        canvas.drawRoundRect(left, top,
                left + blockW, top + blockH,
                dp(6, density), dp(6, density), paintBg);


        paintRec.setColor(Color.RED);
        float dotCX = left + padding + dotSize / 2f;
        float dotCY = top + blockH / 2f;
        canvas.drawCircle(dotCX, dotCY, dotSize / 2f, paintRec);


        canvas.drawText(ts,
                left + padding + dotSize + dotMarginEnd,
                top + blockH / 2f + textSize * 0.35f,
                paintText);
    }


    private void drawFeedbackCard(Canvas canvas,
                                  String feedbackText,
                                  String quality,
                                  int qualityColor,
                                  float density,
                                  int W, int H) {

        float marginB = dp(16, density);
        float marginH = dp(12, density);
        float padding = dp(16, density);
        float radius = dp(16, density);

        float titleSize = sp(15, density);
        float feedbackSize = sp(15, density);
        float separatorH = dp(20, density);


        float cardW = W - marginH * 2 - padding * 2;
        int lineCount = countLines(feedbackText,
                feedbackSize, cardW);

        float cardH = padding
                + titleSize + dp(10, density)
                + separatorH
                + feedbackSize * lineCount
                + dp(4, density) * (lineCount - 1)
                + padding;

        float cardL = marginH;
        float cardR = W - marginH;
        float cardB = H - marginB;
        float cardT = cardB - cardH;


        paintBg.setColor(0xEE1A1A2E);
        canvas.drawRoundRect(cardL, cardT, cardR, cardB,
                radius, radius, paintBg);

        float curY = cardT + padding;


        {
            paintText.setTextSize(titleSize);
            paintText.setColor(0xFFE94560);
            paintText.setTypeface(Typeface.DEFAULT_BOLD);
            canvas.drawText("Анализ техники",
                    cardL + padding,
                    curY + titleSize * 0.85f,
                    paintText);


            String q = (quality != null) ? quality : "●●●●●";
            int qc = (qualityColor != 0) ? qualityColor : 0xFF00FF88;
            paintText.setColor(qc);
            float qW = paintText.measureText(q);
            canvas.drawText(q,
                    cardR - padding - qW,
                    curY + titleSize * 0.85f,
                    paintText);

            curY += titleSize + dp(10, density);
        }


        {
            paintDivider.setColor(0x33FFFFFF);
            canvas.drawLine(
                    cardL + padding, curY,
                    cardR - padding, curY,
                    paintDivider);
            curY += separatorH;
        }


        if (feedbackText != null && !feedbackText.isEmpty()) {
            paintText.setTextSize(feedbackSize);
            paintText.setColor(Color.WHITE);
            paintText.setTypeface(Typeface.DEFAULT_BOLD);

            drawWrappedText(canvas, feedbackText,
                    cardL + padding, curY,
                    cardR - padding,
                    feedbackSize, dp(4, density));
        }
    }


    private void drawWrappedText(Canvas canvas, String text,
                                 float x, float y,
                                 float maxX,
                                 float lineHeight,
                                 float lineSpacing) {
        String[] words = text.split(" ");
        StringBuilder line = new StringBuilder();
        float curY = y + lineHeight * 0.85f;

        for (String word : words) {
            String test = line.length() > 0
                    ? line + " " + word : word;

            if (paintText.measureText(test) <= (maxX - x)) {
                line = new StringBuilder(test);
            } else {
                if (line.length() > 0) {
                    canvas.drawText(line.toString(), x, curY, paintText);
                    curY += lineHeight + lineSpacing;
                }
                line = new StringBuilder(word);
            }
        }
        if (line.length() > 0) {
            canvas.drawText(line.toString(), x, curY, paintText);
        }
    }


    private int countLines(String text, float textSize, float maxW) {
        if (text == null || text.isEmpty()) return 1;

        Paint p = new Paint();
        p.setTextSize(textSize);

        String[] words = text.split(" ");
        StringBuilder line = new StringBuilder();
        int lines = 1;

        for (String word : words) {
            String test = line.length() > 0
                    ? line + " " + word : word;
            if (p.measureText(test) <= maxW) {
                line = new StringBuilder(test);
            } else {
                lines++;
                line = new StringBuilder(word);
            }
        }
        return lines;
    }


    private float dp(float dp, float density) {
        return dp * density;
    }


    private float sp(float sp, float density) {
        return sp * density;
    }


    private void drainEncoder(boolean endOfStream) {
        if (encoder == null) return;

        if (endOfStream) {
            try {
                encoder.signalEndOfInputStream();
            } catch (Exception e) {
                Log.e(TAG, "signalEOS: " + e.getMessage());
            }
        }

        MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
        int maxLoop = 100;

        while (maxLoop-- > 0) {
            int idx = encoder.dequeueOutputBuffer(info, 10_000);

            if (idx == MediaCodec.INFO_TRY_AGAIN_LATER) {
                if (!endOfStream) break;
                try {
                    Thread.sleep(5);
                } catch (InterruptedException ignored) {
                }

            } else if (idx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                if (!muxerStarted) {
                    videoTrackIndex =
                            muxer.addTrack(encoder.getOutputFormat());
                    muxer.start();
                    muxerStarted = true;
                    Log.d(TAG, "Muxer запущен");
                }

            } else if (idx >= 0) {
                ByteBuffer buf = encoder.getOutputBuffer(idx);

                if (buf != null) {
                    boolean isConfig = (info.flags
                            & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0;

                    if (!isConfig && info.size > 0 && muxerStarted) {
                        buf.position(info.offset);
                        buf.limit(info.offset + info.size);
                        muxer.writeSampleData(
                                videoTrackIndex, buf, info);
                    }
                }

                encoder.releaseOutputBuffer(idx, false);

                if ((info.flags
                        & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                    Log.d(TAG, "EOS получен");
                    break;
                }
            }
        }
    }


    public void stopRecording() {
        if (!isRecording) return;

        isRecording = false;
        acceptingFrames = false;

        Log.d(TAG, "Остановка записи...");

        new Thread(() -> {
            if (encoderHandler != null) {
                try {
                    CountDownLatch latch = new CountDownLatch(1);
                    encoderHandler.post(latch::countDown);
                    boolean done = latch.await(3, TimeUnit.SECONDS);
                    if (!done) Log.w(TAG, "EncoderThread timeout");
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }

            if (encoderThread != null) {
                encoderThread.quitSafely();
                try {
                    encoderThread.join(2000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                encoderThread = null;
                encoderHandler = null;
            }

            try {
                drainEncoder(true);
            } catch (Exception e) {
                Log.e(TAG, "Final drain: " + e.getMessage());
            }

            release();
            addToMediaStore();

        }, "StopThread").start();
    }

    private void release() {
        if (encoder != null) {
            try {
                encoder.stop();
                encoder.release();
            } catch (Exception e) {
                Log.e(TAG, "Encoder: " + e.getMessage());
            }
            encoder = null;
        }
        if (encoderSurface != null) {
            try {
                encoderSurface.release();
            } catch (Exception e) {
                Log.e(TAG, "Surface: " + e.getMessage());
            }
            encoderSurface = null;
        }
        if (muxer != null) {
            try {
                if (muxerStarted) muxer.stop();
                muxer.release();
            } catch (Exception e) {
                Log.e(TAG, "Muxer: " + e.getMessage());
            }
            muxer = null;
        }
        if (encoderThread != null) {
            encoderThread.quitSafely();
            encoderThread = null;
            encoderHandler = null;
        }
        muxerStarted = false;
        videoTrackIndex = -1;
        startTimeUs = -1;
        Log.d(TAG, "Ресурсы освобождены");
    }

    private String createOutputFile() {
        String ts = new SimpleDateFormat(
                "yyyyMMdd_HHmmss", Locale.US).format(new Date());
        File dir = context.getExternalFilesDir(
                Environment.DIRECTORY_MOVIES);
        if (dir == null) dir = context.getFilesDir();
        if (!dir.exists()) //noinspection ResultOfMethodCallIgnored
            dir.mkdirs();
        return new File(dir, "workout_" + ts + ".mp4")
                .getAbsolutePath();
    }

    private void addToMediaStore() {
        if (outputFilePath == null) return;

        File src = new File(outputFilePath);
        if (!src.exists() || src.length() == 0) {
            Log.e(TAG, "Файл пуст: " + src.length() + " байт");
            notifyError("Файл записи пуст");
            return;
        }

        Log.d(TAG, "Сохраняем: " + src.length() + " байт");

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ContentValues v = new ContentValues();
            v.put(MediaStore.Video.Media.DISPLAY_NAME, src.getName());
            v.put(MediaStore.Video.Media.MIME_TYPE, "video/mp4");
            v.put(MediaStore.Video.Media.RELATIVE_PATH,
                    "Movies/ПерсональныйТренер");
            v.put(MediaStore.Video.Media.IS_PENDING, 1);

            Uri uri = context.getContentResolver().insert(
                    MediaStore.Video.Media.EXTERNAL_CONTENT_URI, v);

            if (uri != null) {
                try (InputStream in = new FileInputStream(src);
                     OutputStream out = context.getContentResolver()
                             .openOutputStream(uri)) {

                    if (out != null) {
                        byte[] buf = new byte[65536];
                        int len;
                        while ((len = in.read(buf)) > 0)
                            out.write(buf, 0, len);
                    }
                    v.clear();
                    v.put(MediaStore.Video.Media.IS_PENDING, 0);
                    context.getContentResolver()
                            .update(uri, v, null, null);
                    //noinspection ResultOfMethodCallIgnored
                    src.delete();
                    notifySaved(outputFilePath);

                } catch (Exception e) {
                    Log.e(TAG, "MediaStore: " + e.getMessage());
                    notifyError(e.getMessage());
                }
            } else {
                notifyError("Не удалось создать запись в галерее");
            }
        } else {
            context.sendBroadcast(new Intent(
                    Intent.ACTION_MEDIA_SCANNER_SCAN_FILE,
                    Uri.fromFile(src)));
            notifySaved(outputFilePath);
        }
    }

    private void notifyError(String error) {
        if (callback == null) return;
        ((Activity) context).runOnUiThread(
                () -> callback.onRecordingError(error));
    }

    private void notifySaved(String path) {
        if (callback == null) return;
        ((Activity) context).runOnUiThread(
                () -> callback.onRecordingSaved(path));
    }

    public boolean isRecording() {
        return isRecording;
    }
}