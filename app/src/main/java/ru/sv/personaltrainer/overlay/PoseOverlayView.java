package ru.sv.personaltrainer.overlay;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.View;

import com.google.mediapipe.tasks.components.containers.NormalizedLandmark;
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarkerResult;

import java.util.List;

public class PoseOverlayView extends View {

    // ═════════════════════════════════════════════════
    //  Кисти для рисования
    // ═════════════════════════════════════════════════

    // Обычные точки (белые)
    private final Paint pointPaint    = new Paint();

    // Линии скелета (зелёные)
    private final Paint linePaint     = new Paint();

    // Ошибочные точки (красные)
    private final Paint errorPaint    = new Paint();

    // Обводка ошибочных точек
    private final Paint errorStroke   = new Paint();

    // Линии между ошибочными точками
    private final Paint errorLinePaint = new Paint();

    // Пульсирующий эффект для ошибочных точек
    private final Paint glowPaint     = new Paint();

    // ═════════════════════════════════════════════════
    //  Данные
    // ═════════════════════════════════════════════════
    private PoseLandmarkerResult poseResult;
    private List<Integer>        errorLandmarks;

    // Анимация пульсации ошибочных точек
    private float pulseRadius = 0f;
    private boolean pulseGrowing = true;
    private static final float PULSE_MIN = 14f;
    private static final float PULSE_MAX = 22f;
    private static final float PULSE_STEP = 0.5f;

    // ═════════════════════════════════════════════════
    //  Связи между точками скелета MediaPipe
    // ═════════════════════════════════════════════════
    private static final int[][] POSE_CONNECTIONS = {

            // Голова
            {0, 1}, {1, 2}, {2, 3}, {3, 7},
            {0, 4}, {4, 5}, {5, 6}, {6, 8},
            {9, 10},

            // Торс
            {11, 12},
            {11, 23},
            {12, 24},
            {23, 24},

            // Левая рука
            {11, 13},
            {13, 15},
            {15, 17},
            {15, 19},
            {15, 21},
            {17, 19},

            // Правая рука
            {12, 14},
            {14, 16},
            {16, 18},
            {16, 20},
            {16, 22},
            {18, 20},

            // Левая нога
            {23, 25},
            {25, 27},
            {27, 29},
            {27, 31},
            {29, 31},

            // Правая нога
            {24, 26},
            {26, 28},
            {28, 30},
            {28, 32},
            {30, 32}
    };

    // Группы точек по цвету линий
    // Левая сторона — голубой
    private static final int[] LEFT_LANDMARKS  = {
            11, 13, 15, 17, 19, 21, 23, 25, 27, 29, 31
    };
    // Правая сторона — жёлтый
    private static final int[] RIGHT_LANDMARKS = {
            12, 14, 16, 18, 20, 22, 24, 26, 28, 30, 32
    };

    // ═════════════════════════════════════════════════
    //  Конструкторы
    // ═════════════════════════════════════════════════
    public PoseOverlayView(Context context) {
        super(context);
        initPaints();
    }

    public PoseOverlayView(Context context, AttributeSet attrs) {
        super(context, attrs);
        initPaints();
    }

    public PoseOverlayView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        initPaints();
    }

    // ═════════════════════════════════════════════════
    //  Инициализация кистей
    // ═════════════════════════════════════════════════
    private void initPaints() {

        // Обычные точки
        pointPaint.setColor(Color.WHITE);
        pointPaint.setStyle(Paint.Style.FILL);
        pointPaint.setAntiAlias(true);

        // Линии скелета
        linePaint.setColor(Color.GREEN);
        linePaint.setStrokeWidth(5f);
        linePaint.setStyle(Paint.Style.STROKE);
        linePaint.setAntiAlias(true);
        linePaint.setStrokeCap(Paint.Cap.ROUND);

        // Ошибочные точки — заливка
        errorPaint.setColor(Color.RED);
        errorPaint.setStyle(Paint.Style.FILL);
        errorPaint.setAntiAlias(true);

        // Ошибочные точки — обводка
        errorStroke.setColor(Color.WHITE);
        errorStroke.setStyle(Paint.Style.STROKE);
        errorStroke.setStrokeWidth(2.5f);
        errorStroke.setAntiAlias(true);

        // Линии между ошибочными точками
        errorLinePaint.setColor(Color.RED);
        errorLinePaint.setStrokeWidth(6f);
        errorLinePaint.setStyle(Paint.Style.STROKE);
        errorLinePaint.setAntiAlias(true);
        errorLinePaint.setStrokeCap(Paint.Cap.ROUND);

        // Свечение вокруг ошибочных точек
        glowPaint.setStyle(Paint.Style.FILL);
        glowPaint.setAntiAlias(true);

        // Начальный размер пульсации
        pulseRadius = PULSE_MIN;
    }

    // ═════════════════════════════════════════════════
    //  Публичный метод обновления данных
    // ═════════════════════════════════════════════════
    public void updateResults(PoseLandmarkerResult result,
                              int imageWidth,
                              int imageHeight,
                              List<Integer> errorLandmarks) {
        this.poseResult     = result;
        this.errorLandmarks = errorLandmarks;
        invalidate(); // запрашиваем перерисовку
    }

    // ═════════════════════════════════════════════════
    //  Главный метод рисования
    // ═════════════════════════════════════════════════
    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        // Нечего рисовать
        if (poseResult == null || poseResult.landmarks().isEmpty()) return;

        List<NormalizedLandmark> landmarks = poseResult.landmarks().get(0);

        float viewW = getWidth();
        float viewH = getHeight();

        // Обновляем пульсацию
        updatePulse();

        // 1. Рисуем линии скелета
        drawConnections(canvas, landmarks, viewW, viewH);

        // 2. Рисуем точки
        drawLandmarks(canvas, landmarks, viewW, viewH);

        // 3. Запрашиваем следующий кадр анимации
        if (errorLandmarks != null && !errorLandmarks.isEmpty()) {
            postInvalidateDelayed(16); // ~60 FPS анимация
        }
    }

    // ═════════════════════════════════════════════════
    //  Рисование линий скелета
    // ═════════════════════════════════════════════════
    private void drawConnections(Canvas canvas,
                                 List<NormalizedLandmark> landmarks,
                                 float viewW, float viewH) {
        for (int[] connection : POSE_CONNECTIONS) {
            int startIdx = connection[0];
            int endIdx   = connection[1];

            if (startIdx >= landmarks.size() ||
                    endIdx   >= landmarks.size()) continue;

            NormalizedLandmark start = landmarks.get(startIdx);
            NormalizedLandmark end   = landmarks.get(endIdx);

            // Пропускаем невидимые точки
            if (!isVisible(start) || !isVisible(end)) continue;

            float startX = start.x() * viewW;
            float startY = start.y() * viewH;
            float endX   = end.x()   * viewW;
            float endY   = end.y()   * viewH;

            // Выбираем цвет линии
            boolean startIsError = isErrorLandmark(startIdx);
            boolean endIsError   = isErrorLandmark(endIdx);

            if (startIsError || endIsError) {
                // Красная линия если один из концов — ошибка
                canvas.drawLine(startX, startY, endX, endY, errorLinePaint);
            } else {
                // Цвет зависит от стороны тела
                linePaint.setColor(getConnectionColor(startIdx, endIdx));
                canvas.drawLine(startX, startY, endX, endY, linePaint);
            }
        }
    }

    // ═════════════════════════════════════════════════
    //  Рисование точек
    // ═════════════════════════════════════════════════
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

    // ── Обычная точка ─────────────────────────────────
    private void drawNormalPoint(Canvas canvas, float x, float y, int index) {
        // Размер точки зависит от важности
        float radius = isKeyLandmark(index) ? 10f : 7f;

        // Тень под точкой
        pointPaint.setColor(0x44000000);
        canvas.drawCircle(x + 1f, y + 1f, radius, pointPaint);

        // Сама точка
        pointPaint.setColor(Color.WHITE);
        canvas.drawCircle(x, y, radius, pointPaint);

        // Маленький блик
        pointPaint.setColor(0xAAFFFFFF);
        canvas.drawCircle(x - radius * 0.3f, y - radius * 0.3f,
                radius * 0.3f, pointPaint);
    }

    // ── Ошибочная точка с анимацией ───────────────────
    private void drawErrorPoint(Canvas canvas, float x, float y) {

        // Свечение (gradient glow)
        RadialGradient gradient = new RadialGradient(
                x, y,
                pulseRadius * 1.5f,
                new int[]{
                        0x88FF0000,  // красный центр
                        0x44FF0000,  // полупрозрачный
                        0x00FF0000   // прозрачный край
                },
                new float[]{0f, 0.5f, 1f},
                Shader.TileMode.CLAMP
        );
        glowPaint.setShader(gradient);
        canvas.drawCircle(x, y, pulseRadius * 1.5f, glowPaint);

        // Пульсирующий круг
        errorPaint.setColor(0xAAFF0000);
        canvas.drawCircle(x, y, pulseRadius, errorPaint);

        // Основной красный круг
        errorPaint.setColor(0xFFFF3333);
        canvas.drawCircle(x, y, 14f, errorPaint);

        // Белая обводка
        canvas.drawCircle(x, y, 14f, errorStroke);

        // Белый крест внутри
        drawCross(canvas, x, y, 7f);
    }

    // ── Крестик внутри ошибочной точки ───────────────
    private void drawCross(Canvas canvas, float x, float y, float size) {
        Paint crossPaint = new Paint();
        crossPaint.setColor(Color.WHITE);
        crossPaint.setStrokeWidth(2.5f);
        crossPaint.setAntiAlias(true);
        crossPaint.setStrokeCap(Paint.Cap.ROUND);

        canvas.drawLine(x - size, y - size, x + size, y + size, crossPaint);
        canvas.drawLine(x + size, y - size, x - size, y + size, crossPaint);
    }

    // ═════════════════════════════════════════════════
    //  Анимация пульсации
    // ═════════════════════════════════════════════════
    private void updatePulse() {
        if (pulseGrowing) {
            pulseRadius += PULSE_STEP;
            if (pulseRadius >= PULSE_MAX) pulseGrowing = false;
        } else {
            pulseRadius -= PULSE_STEP;
            if (pulseRadius <= PULSE_MIN) pulseGrowing = true;
        }
    }

    // ═════════════════════════════════════════════════
    //  Вспомогательные методы
    // ═════════════════════════════════════════════════

    // Видима ли точка (confidence > 50%)
    private boolean isVisible(NormalizedLandmark lm) {
        if (lm.visibility().isPresent()) {
            return lm.visibility().get() >= 0.5f;
        }
        return true;
    }

    // Является ли точка ошибочной
    private boolean isErrorLandmark(int index) {
        return errorLandmarks != null && errorLandmarks.contains(index);
    }

    // Ключевые точки (рисуются крупнее)
    private boolean isKeyLandmark(int index) {
        return index == 11 || index == 12 || // плечи
                index == 23 || index == 24 || // бёдра
                index == 25 || index == 26 || // колени
                index == 13 || index == 14;   // локти
    }

    // Цвет линии в зависимости от стороны тела
    private int getConnectionColor(int startIdx, int endIdx) {
        boolean startLeft  = isLeftLandmark(startIdx);
        boolean startRight = isRightLandmark(startIdx);
        boolean endLeft    = isLeftLandmark(endIdx);
        boolean endRight   = isRightLandmark(endIdx);

        if (startLeft  || endLeft)  return 0xFF00BFFF; // голубой — левая
        if (startRight || endRight) return 0xFFFFD700; // жёлтый  — правая
        return 0xFF00FF00;                              // зелёный — центр
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