package ru.sv.personaltrainer.overlay;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.View;

import com.google.mediapipe.tasks.components.containers.NormalizedLandmark;
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarkerResult;

import java.util.List;

public class PoseOverlayView extends View {

    private final Paint pointPaint = new Paint();
    private final Paint linePaint = new Paint();
    private final Paint errorPaint = new Paint();
    private final Paint errorStroke = new Paint();
    private final Paint errorLinePaint = new Paint();
    private final Paint glowPaint = new Paint();

    private PoseLandmarkerResult poseResult;
    private List<Integer> errorLandmarks;

    private float pulseRadius = 0f;
    private boolean pulseGrowing = true;

    private static final float PULSE_MIN = 14f;
    private static final float PULSE_MAX = 22f;
    private static final float PULSE_STEP = 0.5f;

    private static final int[][] POSE_CONNECTIONS = {
            // Голова
            {0, 1}, {1, 2}, {2, 3}, {3, 7},
            {0, 4}, {4, 5}, {5, 6}, {6, 8},
            {9, 10},
            // Торс
            {11, 12}, {11, 23}, {12, 24}, {23, 24},
            // Левая рука
            {11, 13}, {13, 15}, {15, 17},
            {15, 19}, {15, 21}, {17, 19},
            // Правая рука
            {12, 14}, {14, 16}, {16, 18},
            {16, 20}, {16, 22}, {18, 20},
            // Левая нога
            {23, 25}, {25, 27}, {27, 29}, {27, 31}, {29, 31},
            // Правая нога
            {24, 26}, {26, 28}, {28, 30}, {28, 32}, {30, 32}
    };

    private static final int[] LEFT_LANDMARKS = {
            11, 13, 15, 17, 19, 21, 23, 25, 27, 29, 31
    };

    private static final int[] RIGHT_LANDMARKS = {
            12, 14, 16, 18, 20, 22, 24, 26, 28, 30, 32
    };


    public PoseOverlayView(Context context) {
        super(context);
        initPaints();
    }

    public PoseOverlayView(Context context, AttributeSet attrs) {
        super(context, attrs);
        initPaints();
    }

    public PoseOverlayView(Context context, AttributeSet attrs,
                           int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        initPaints();
    }


    private void initPaints() {
        pointPaint.setColor(Color.WHITE);
        pointPaint.setStyle(Paint.Style.FILL);
        pointPaint.setAntiAlias(true);

        linePaint.setColor(Color.GREEN);
        linePaint.setStrokeWidth(5f);
        linePaint.setStyle(Paint.Style.STROKE);
        linePaint.setAntiAlias(true);
        linePaint.setStrokeCap(Paint.Cap.ROUND);

        errorPaint.setColor(Color.RED);
        errorPaint.setStyle(Paint.Style.FILL);
        errorPaint.setAntiAlias(true);

        errorStroke.setColor(Color.WHITE);
        errorStroke.setStyle(Paint.Style.STROKE);
        errorStroke.setStrokeWidth(2.5f);
        errorStroke.setAntiAlias(true);

        errorLinePaint.setColor(Color.RED);
        errorLinePaint.setStrokeWidth(6f);
        errorLinePaint.setStyle(Paint.Style.STROKE);
        errorLinePaint.setAntiAlias(true);
        errorLinePaint.setStrokeCap(Paint.Cap.ROUND);

        glowPaint.setStyle(Paint.Style.FILL);
        glowPaint.setAntiAlias(true);

        pulseRadius = PULSE_MIN;
    }

    public void updateResults(PoseLandmarkerResult result,
                              int imageWidth,
                              int imageHeight,
                              List<Integer> errorLandmarks) {
        this.poseResult = result;
        this.errorLandmarks = errorLandmarks;
        invalidate();
    }

    public void drawOnCanvas(Canvas canvas,
                             PoseLandmarkerResult result,
                             int frameWidth,
                             int frameHeight,
                             List<Integer> errors) {

        if (result == null || result.landmarks().isEmpty()) return;

        List<NormalizedLandmark> landmarks = result.landmarks().get(0);

        List<Integer> savedErrors = this.errorLandmarks;
        this.errorLandmarks = errors;

        drawConnections(canvas, landmarks, frameWidth, frameHeight);
        drawLandmarks(canvas, landmarks, frameWidth, frameHeight);

        this.errorLandmarks = savedErrors;
    }


    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        if (poseResult == null
                || poseResult.landmarks().isEmpty()) return;

        List<NormalizedLandmark> landmarks =
                poseResult.landmarks().get(0);

        float viewW = getWidth();
        float viewH = getHeight();

        updatePulse();
        drawConnections(canvas, landmarks, viewW, viewH);
        drawLandmarks(canvas, landmarks, viewW, viewH);

        if (errorLandmarks != null && !errorLandmarks.isEmpty()) {
            postInvalidateDelayed(16);
        }
    }


    private void drawConnections(Canvas canvas,
                                 List<NormalizedLandmark> landmarks,
                                 float viewW, float viewH) {
        for (int[] connection : POSE_CONNECTIONS) {
            int startIdx = connection[0];
            int endIdx = connection[1];

            if (startIdx >= landmarks.size()
                    || endIdx >= landmarks.size()) continue;

            NormalizedLandmark start = landmarks.get(startIdx);
            NormalizedLandmark end = landmarks.get(endIdx);

            if (!isVisible(start) || !isVisible(end)) continue;

            float startX = start.x() * viewW;
            float startY = start.y() * viewH;
            float endX = end.x() * viewW;
            float endY = end.y() * viewH;

            boolean startIsError = isErrorLandmark(startIdx);
            boolean endIsError = isErrorLandmark(endIdx);

            if (startIsError || endIsError) {
                canvas.drawLine(startX, startY,
                        endX, endY, errorLinePaint);
            } else {
                linePaint.setColor(
                        getConnectionColor(startIdx, endIdx));
                canvas.drawLine(startX, startY,
                        endX, endY, linePaint);
            }
        }
    }


    private void drawLandmarks(Canvas canvas,
                               List<NormalizedLandmark> landmarks,
                               float viewW, float viewH) {
        for (int i = 0; i < landmarks.size(); i++) {
            NormalizedLandmark lm = landmarks.get(i);
            if (!isVisible(lm)) continue;

            float x = lm.x() * viewW;
            float y = lm.y() * viewH;

            if (isErrorLandmark(i)) {
                drawErrorPoint(canvas, x, y);
            } else {
                drawNormalPoint(canvas, x, y, i);
            }
        }
    }

    private void drawNormalPoint(Canvas canvas,
                                 float x, float y, int index) {
        float radius = isKeyLandmark(index) ? 10f : 7f;

        pointPaint.setColor(0x44000000);
        canvas.drawCircle(x + 1f, y + 1f, radius, pointPaint);

        pointPaint.setColor(Color.WHITE);
        canvas.drawCircle(x, y, radius, pointPaint);

        pointPaint.setColor(0xAAFFFFFF);
        canvas.drawCircle(
                x - radius * 0.3f,
                y - radius * 0.3f,
                radius * 0.3f,
                pointPaint);
    }


    private void drawErrorPoint(Canvas canvas, float x, float y) {
        RadialGradient gradient = new RadialGradient(
                x, y,
                pulseRadius * 1.5f,
                new int[]{0x88FF0000, 0x44FF0000, 0x00FF0000},
                new float[]{0f, 0.5f, 1f},
                Shader.TileMode.CLAMP);
        glowPaint.setShader(gradient);
        canvas.drawCircle(x, y, pulseRadius * 1.5f, glowPaint);

        errorPaint.setColor(0xAAFF0000);
        canvas.drawCircle(x, y, pulseRadius, errorPaint);

        errorPaint.setColor(0xFFFF3333);
        canvas.drawCircle(x, y, 14f, errorPaint);

        canvas.drawCircle(x, y, 14f, errorStroke);

        drawCross(canvas, x, y, 7f);
    }


    private void drawCross(Canvas canvas,
                           float x, float y, float size) {
        Paint crossPaint = new Paint();
        crossPaint.setColor(Color.WHITE);
        crossPaint.setStrokeWidth(2.5f);
        crossPaint.setAntiAlias(true);
        crossPaint.setStrokeCap(Paint.Cap.ROUND);

        canvas.drawLine(x - size, y - size,
                x + size, y + size, crossPaint);
        canvas.drawLine(x + size, y - size,
                x - size, y + size, crossPaint);
    }


    private void updatePulse() {
        if (pulseGrowing) {
            pulseRadius += PULSE_STEP;
            if (pulseRadius >= PULSE_MAX) pulseGrowing = false;
        } else {
            pulseRadius -= PULSE_STEP;
            if (pulseRadius <= PULSE_MIN) pulseGrowing = true;
        }
    }


    private boolean isVisible(NormalizedLandmark lm) {
        if (lm.visibility().isPresent()) {
            return lm.visibility().get() >= 0.5f;
        }
        return true;
    }

    private boolean isErrorLandmark(int index) {
        return errorLandmarks != null
                && errorLandmarks.contains(index);
    }

    private boolean isKeyLandmark(int index) {
        return index == 11 || index == 12
                || index == 23 || index == 24
                || index == 25 || index == 26
                || index == 13 || index == 14;
    }

    private int getConnectionColor(int startIdx, int endIdx) {
        boolean startLeft = isLeftLandmark(startIdx);
        boolean startRight = isRightLandmark(startIdx);
        boolean endLeft = isLeftLandmark(endIdx);
        boolean endRight = isRightLandmark(endIdx);

        if (startLeft || endLeft) return 0xFF00BFFF;
        if (startRight || endRight) return 0xFFFFD700;
        return 0xFF00FF00;
    }

    private boolean isLeftLandmark(int index) {
        for (int i : LEFT_LANDMARKS) {
            if (i == index) return true;
        }
        return false;
    }

    private boolean isRightLandmark(int index) {
        for (int i : RIGHT_LANDMARKS) {
            if (i == index) return true;
        }
        return false;
    }
}