package ru.sv.personaltrainer.overlay;

import android.app.Activity;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.RectF;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import ru.sv.personaltrainer.MainActivity;
import ru.sv.personaltrainer.R;

public class ProfileOnboardingOverlay {

    private static final String KEY_DONE = "profile_onboarding_done";

    // ── Шаги ─────────────────────────────────────────────────────────
    private static final String[] TITLES = {
            "⚖ Индекс массы тела",
            "🧮 Рассчитать ИМТ",
            "📈 Динамика веса",
            "🏆 Статистика",
            "📋 История тренировок"
    };

    private static final String[] TEXTS = {
            "Введите ваш пол, рост, вес и возраст.\n\n"
                    + "Эти данные используются для расчёта\n"
                    + "индекса массы тела (ИМТ) и отслеживания\n"
                    + "динамики вашего веса.",

            "Нажмите эту кнопку после ввода данных.\n\n"
                    + "Приложение рассчитает ваш ИМТ и покажет,\n"
                    + "соответствует ли он норме с учётом\n"
                    + "вашего пола и возраста.",

            "Здесь отображается график изменения\n"
                    + "вашего веса со временем.\n\n"
                    + "Каждый раз при нажатии «Рассчитать ИМТ»\n"
                    + "вес автоматически записывается в историю.",

            "Три ключевых показателя вашей активности:\n\n"
                    + "• Красный  — всего тренировок\n"
                    + "• Жёлтый   — дней подряд (серия)\n"
                    + "• Синий    — тренировок на этой неделе",

            "Здесь сохраняется история последних 20\n"
                    + "тренировок с датой и количеством повторений.\n\n"
                    + "Кнопка «Очистить» удаляет всю историю."
    };

    private static final int[] TARGET_IDS = {
            R.id.btnGenderMale,
            R.id.btnCalculateBmi,
            R.id.tvNoWeightData,
            R.id.tvTotalWorkouts,
            R.id.tvNoHistory
    };

    private static final int HIGHLIGHT_PADDING_DP = 10;
    private static final int CARD_MARGIN_DP       = 12;

    // ── Поля ──────────────────────────────────────────────────────────
    private final Activity     activity;
    private final ScrollView   scrollView;
    private final FrameLayout  root;       // android.R.id.content

    private SpotlightView spotlight;
    private View          card;
    private int           step = 0;

    // ── Конструктор ───────────────────────────────────────────────────
    public ProfileOnboardingOverlay(Activity activity,
                                    ScrollView scrollView) {
        this.activity   = activity;
        this.scrollView = scrollView;
        this.root       = activity.findViewById(android.R.id.content);
    }

    // ── Точка входа ───────────────────────────────────────────────────
    public void startIfNeeded() {
        SharedPreferences prefs = activity.getSharedPreferences(
                MainActivity.PREFS_NAME, Activity.MODE_PRIVATE);
        if (!prefs.getBoolean(KEY_DONE, false)) {
            step = 0;
            // Ждём первый layout-проход
            root.post(this::showStep);
        }
    }

    // ── Показ шага ────────────────────────────────────────────────────
    private void showStep() {
        if (step >= TARGET_IDS.length) {
            markDone();
            return;
        }

        View target = activity.findViewById(TARGET_IDS[step]);
        if (target == null) {
            step++;
            showStep();
            return;
        }

        // Прокручиваем к target, затем рисуем
        scrollToView(target, () -> {
            // После прокрутки пересчитываем позицию
            RectF rect = computeHighlightRect(target);
            removeViews();
            attachSpotlight(rect);
            attachCard(rect);
        });
    }

    // ── Прокрутка ScrollView к target ────────────────────────────────
    private void scrollToView(View target, Runnable then) {
        // Позиция target внутри ScrollView
        int[] tLoc = new int[2];
        target.getLocationInWindow(tLoc);

        int[] sLoc = new int[2];
        scrollView.getLocationInWindow(sLoc);

        int dy = tLoc[1] - sLoc[1] - dpToPx(100);
        int newY = Math.max(0, scrollView.getScrollY() + dy);

        scrollView.smoothScrollTo(0, newY);
        // 500 мс — запас для завершения анимации прокрутки
        root.postDelayed(then, 500);
    }

    // ── Вычисляем RectF подсветки в координатах root ──────────────────
    private RectF computeHighlightRect(View target) {
        // Ищем CardView-родителя чтобы подсветить всю секцию
        View highlight = findParentCard(target);

        int[] hLoc   = new int[2];
        int[] rLoc   = new int[2];
        highlight.getLocationInWindow(hLoc);
        root.getLocationInWindow(rLoc);

        int pad = dpToPx(HIGHLIGHT_PADDING_DP);

        float l = hLoc[0] - rLoc[0] - pad;
        float t = hLoc[1] - rLoc[1] - pad;
        float r = l + highlight.getWidth()  + pad * 2;
        float b = t + highlight.getHeight() + pad * 2;

        return new RectF(l, t, r, b);
    }

    // Поднимаемся по иерархии пока не найдём CardView
    private View findParentCard(View v) {
        View current = v;
        while (current != null) {
            if (current instanceof androidx.cardview.widget.CardView) {
                return current;
            }
            if (current.getParent() instanceof View) {
                current = (View) current.getParent();
            } else {
                break;
            }
        }
        return v; // не нашли — возвращаем исходный
    }

    // ── Создаём и добавляем SpotlightView ────────────────────────────
    private void attachSpotlight(RectF rect) {
        spotlight = new SpotlightView(activity, rect);

        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT);

        // ВАЖНО: перехватываем ВСЕ касания — ничего не проходит сквозь overlay
        spotlight.setOnTouchListener((v, e) -> true);

        root.addView(spotlight, lp);

        spotlight.setAlpha(0f);
        spotlight.animate().alpha(1f).setDuration(300).start();
    }

    // ── Создаём и добавляем карточку ─────────────────────────────────
    private void attachCard(RectF rect) {
        card = LayoutInflater.from(activity)
                .inflate(R.layout.item_profile_onboarding_card,
                        root, false);

        // Заполняем текст
        ((TextView) card.findViewById(R.id.tvOnbTitle)).setText(TITLES[step]);
        ((TextView) card.findViewById(R.id.tvOnbText)).setText(TEXTS[step]);
        ((TextView) card.findViewById(R.id.tvOnbCounter))
                .setText((step + 1) + " / " + TARGET_IDS.length);

        Button btnNext = card.findViewById(R.id.btnOnbNext);
        Button btnSkip = card.findViewById(R.id.btnOnbSkip);

        boolean last = (step == TARGET_IDS.length - 1);
        btnNext.setText(last ? "Готово!" : "Далее →");
        btnSkip.setVisibility(last ? View.GONE : View.VISIBLE);

        btnNext.setOnClickListener(v -> { step++; showStep(); });
        btnSkip.setOnClickListener(v -> markDone());

        // Добавляем в root, но пока невидимую — нужен layout-проход
        // чтобы знать высоту карточки
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                dpToPx(300),
                ViewGroup.LayoutParams.WRAP_CONTENT);
        card.setLayoutParams(lp);
        card.setAlpha(0f);
        root.addView(card);

        // После измерения позиционируем карточку
        card.getViewTreeObserver().addOnPreDrawListener(
                new ViewTreeObserver.OnPreDrawListener() {
                    @Override
                    public boolean onPreDraw() {
                        card.getViewTreeObserver()
                                .removeOnPreDrawListener(this);
                        placeCard(rect, card);
                        card.animate().alpha(1f).setDuration(300).start();
                        return true;
                    }
                });
    }

    // ── Позиционирование карточки строго ВНЕ зоны подсветки ──────────
    private void placeCard(RectF rect, View card) {
        int rootH     = root.getHeight();
        int rootW     = root.getWidth();
        int cardW     = dpToPx(300);
        int cardH     = card.getMeasuredHeight();
        int margin    = dpToPx(CARD_MARGIN_DP);

        // Горизонтально — по центру экрана, с проверкой границ
        int leftMargin = (rootW - cardW) / 2;
        leftMargin = Math.max(margin, Math.min(rootW - cardW - margin, leftMargin));

        // Вертикально: сначала пробуем снизу, потом сверху
        int topMargin;

        float spaceBelow = rootH - rect.bottom - margin;
        float spaceAbove = rect.top - margin;

        if (spaceBelow >= cardH + margin) {
            // Места снизу достаточно
            topMargin = (int) rect.bottom + margin;
        } else if (spaceAbove >= cardH + margin) {
            // Места сверху достаточно
            topMargin = (int) rect.top - cardH - margin;
        } else if (spaceBelow >= spaceAbove) {
            // Обоих не хватает — выбираем где больше места
            topMargin = (int) rect.bottom + margin;
        } else {
            topMargin = Math.max(margin, (int) rect.top - cardH - margin);
        }

        // Гарантируем что карточка внутри экрана
        topMargin = Math.max(margin, topMargin);
        topMargin = Math.min(rootH - cardH - margin, topMargin);

        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(cardW, cardH);
        lp.leftMargin = leftMargin;
        lp.topMargin  = topMargin;
        card.setLayoutParams(lp);
    }

    // ── Удаляем View текущего шага ────────────────────────────────────
    private void removeViews() {
        if (spotlight != null) { root.removeView(spotlight); spotlight = null; }
        if (card      != null) { root.removeView(card);      card      = null; }
    }

    // ── Завершение и сохранение флага ────────────────────────────────
    private void markDone() {
        removeViews();
        activity.getSharedPreferences(
                        MainActivity.PREFS_NAME, Activity.MODE_PRIVATE)
                .edit()
                .putBoolean(KEY_DONE, true)
                .apply();
    }

    // ── dp → px ──────────────────────────────────────────────────────
    private int dpToPx(int dp) {
        return Math.round(dp
                * activity.getResources().getDisplayMetrics().density);
    }


    // ════════════════════════════════════════════════════════════════
    //  SpotlightView
    //  Рисует затемнение с прозрачным окном через PorterDuff.CLEAR
    // ════════════════════════════════════════════════════════════════
    private static class SpotlightView extends View {

        private final Paint  dimPaint    = new Paint();
        private final Paint  clearPaint  = new Paint();
        private final Paint  borderPaint = new Paint();
        private final RectF  hole;
        private final float  radius;

        SpotlightView(Activity ctx, RectF hole) {
            super(ctx);
            this.hole   = hole;
            this.radius = 16f * ctx.getResources()
                    .getDisplayMetrics().density;

            // ОБЯЗАТЕЛЬНО для работы PorterDuff.CLEAR
            setLayerType(LAYER_TYPE_SOFTWARE, null);

            // Затемнение
            dimPaint.setColor(Color.parseColor("#CC000000"));
            dimPaint.setStyle(Paint.Style.FILL);

            // «Ластик» — делает пиксели полностью прозрачными
            clearPaint.setAntiAlias(true);
            clearPaint.setXfermode(
                    new PorterDuffXfermode(PorterDuff.Mode.CLEAR));

            // Цветная рамка вокруг окна
            borderPaint.setColor(Color.parseColor("#E94560"));
            borderPaint.setStyle(Paint.Style.STROKE);
            borderPaint.setStrokeWidth(
                    3f * ctx.getResources().getDisplayMetrics().density);
            borderPaint.setAntiAlias(true);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            // 1. Заливаем весь экран тёмным
            canvas.drawRect(0, 0, getWidth(), getHeight(), dimPaint);

            // 2. Вырезаем прозрачное окно
            canvas.drawRoundRect(hole, radius, radius, clearPaint);

            // 3. Рисуем цветную рамку
            canvas.drawRoundRect(hole, radius, radius, borderPaint);
        }
    }
}