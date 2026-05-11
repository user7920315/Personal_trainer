package ru.sv.personaltrainer.overlay;

import android.app.Activity;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Rect;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import ru.sv.personaltrainer.MainActivity;
import ru.sv.personaltrainer.R;

/**
 * Пошаговый онбординг поверх ProfileActivity.
 * Показывает затемнение + карточку с подсказкой рядом с целевым View.
 * Не требует сторонних библиотек.
 */
public class ProfileOnboardingOverlay {

    // ── Ключ в SharedPreferences ──────────────────────────────────────
    private static final String KEY_PROFILE_ONBOARDING_DONE =
            "profile_onboarding_done";

    // ── Данные шагов ──────────────────────────────────────────────────
    private static final String[] STEP_TITLES = {
            "⚖ Индекс массы тела",
            "🧮 Рассчитать ИМТ",
            "📈 Динамика веса",
            "🏆 Статистика",
            "📋 История тренировок"
    };

    private static final String[] STEP_TEXTS = {
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

    // id целевых View из activity_profile.xml
    private static final int[] STEP_TARGET_IDS = {
            R.id.btnGenderMale,       // шаг 1 — секция ИМТ (якорь — кнопка пола)
            R.id.btnCalculateBmi,     // шаг 2 — кнопка расчёта
            R.id.tvNoWeightData,      // шаг 3 — секция графика
            R.id.tvTotalWorkouts,     // шаг 4 — статистика
            R.id.tvNoHistory          // шаг 5 — история
    };

    // ── Поля ─────────────────────────────────────────────────────────
    private final Activity      activity;
    private final ScrollView    scrollView;   // ScrollView профиля
    private final FrameLayout   overlay;      // контейнер для затемнения
    private       int           currentStep = 0;

    // Вью текущего шага
    private View dimView;
    private View cardView;

    // ── Конструктор ───────────────────────────────────────────────────
    public ProfileOnboardingOverlay(Activity activity,
                                    ScrollView scrollView) {
        this.activity   = activity;
        this.scrollView = scrollView;

        // Создаём FrameLayout-оверлей и добавляем его поверх контента
        overlay = new FrameLayout(activity);
        overlay.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));

        ViewGroup root = activity.findViewById(android.R.id.content);
        root.addView(overlay);
    }

    // ── Публичный метод запуска ───────────────────────────────────────
    public void startIfNeeded() {
        SharedPreferences prefs =
                activity.getSharedPreferences(
                        MainActivity.PREFS_NAME,
                        Activity.MODE_PRIVATE);

        if (!prefs.getBoolean(KEY_PROFILE_ONBOARDING_DONE, false)) {
            currentStep = 0;
            // Ждём первый layout-проход чтобы View были измерены
            overlay.post(this::showCurrentStep);
        }
    }

    // ── Показ текущего шага ───────────────────────────────────────────
    private void showCurrentStep() {
        if (currentStep >= STEP_TARGET_IDS.length) {
            finish();
            return;
        }

        View target = activity.findViewById(STEP_TARGET_IDS[currentStep]);

        if (target == null) {
            // View не найдена — пропускаем шаг
            currentStep++;
            showCurrentStep();
            return;
        }

        // Прокручиваем ScrollView к целевому View
        scrollToTarget(target, () -> {
            removeCurrentViews();
            buildAndShowStep(target);
        });
    }

    // ── Прокрутка к View, затем callback ─────────────────────────────
    private void scrollToTarget(View target, Runnable afterScroll) {
        // Вычисляем позицию target относительно ScrollView
        int[] targetPos = new int[2];
        target.getLocationOnScreen(targetPos);

        int[] scrollPos = new int[2];
        scrollView.getLocationOnScreen(scrollPos);

        int scrollY = scrollView.getScrollY()
                + (targetPos[1] - scrollPos[1])
                - 120; // отступ сверху

        scrollView.smoothScrollTo(0, Math.max(0, scrollY));

        // Ждём завершения прокрутки (~400мс)
        overlay.postDelayed(afterScroll, 420);
    }

    // ── Построение шага: затемнение + карточка ────────────────────────
    private void buildAndShowStep(View target) {
        // 1. Затемняющий фон
        dimView = new View(activity);
        dimView.setLayoutParams(new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT));
        dimView.setBackgroundColor(Color.parseColor("#BB000000"));

        // Клик на фон — пропустить весь онбординг
        dimView.setOnClickListener(v -> finish());
        overlay.addView(dimView);

        // 2. Карточка подсказки
        cardView = LayoutInflater.from(activity)
                .inflate(R.layout.item_profile_onboarding_card,
                        overlay, false);

        // Заполняем текст
        ((TextView) cardView.findViewById(R.id.tvOnbTitle))
                .setText(STEP_TITLES[currentStep]);
        ((TextView) cardView.findViewById(R.id.tvOnbText))
                .setText(STEP_TEXTS[currentStep]);

        // Счётчик шагов
        ((TextView) cardView.findViewById(R.id.tvOnbCounter))
                .setText((currentStep + 1) + " / " + STEP_TARGET_IDS.length);

        // Кнопки
        Button btnNext = cardView.findViewById(R.id.btnOnbNext);
        Button btnSkip = cardView.findViewById(R.id.btnOnbSkip);

        boolean isLast = (currentStep == STEP_TARGET_IDS.length - 1);
        btnNext.setText(isLast ? "Готово!" : "Далее →");
        btnSkip.setVisibility(isLast ? View.GONE : View.VISIBLE);

        btnNext.setOnClickListener(v -> {
            currentStep++;
            showCurrentStep();
        });
        btnSkip.setOnClickListener(v -> finish());

        // Позиционируем карточку рядом с target
        positionCard(target);

        overlay.addView(cardView);

        // Анимация появления
        cardView.setAlpha(0f);
        cardView.animate().alpha(1f).setDuration(200).start();
    }

    // ── Позиционирование карточки относительно target ─────────────────
    private void positionCard(View target) {
        // Координаты target на экране
        int[] targetScreenPos = new int[2];
        target.getLocationOnScreen(targetScreenPos);

        // Координаты overlay на экране
        int[] overlayScreenPos = new int[2];
        overlay.getLocationOnScreen(overlayScreenPos);

        // Позиция target относительно overlay
        int targetTop  = targetScreenPos[1] - overlayScreenPos[1];
        int targetBottom = targetTop + target.getHeight();

        int screenHeight = overlay.getHeight();
        int cardWidth    = dpToPx(300);
        int cardMargin   = dpToPx(16);

        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                cardWidth,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        params.gravity = Gravity.NO_GRAVITY;

        // Горизонтально — по центру экрана
        params.leftMargin = (overlay.getWidth() - cardWidth) / 2;

        // Вертикально — под target если места достаточно, иначе над ним
        int spaceBelow = screenHeight - targetBottom - cardMargin;
        if (spaceBelow >= dpToPx(180)) {
            // Ставим карточку ПОД целевым View
            params.topMargin = targetBottom + cardMargin;
        } else {
            // Ставим карточку НАД целевым View
            params.topMargin = Math.max(
                    cardMargin,
                    targetTop - dpToPx(180) - cardMargin);
        }

        cardView.setLayoutParams(params);
    }

    // ── Удаление текущих View шага ────────────────────────────────────
    private void removeCurrentViews() {
        if (dimView  != null) overlay.removeView(dimView);
        if (cardView != null) overlay.removeView(cardView);
        dimView  = null;
        cardView = null;
    }

    // ── Завершение онбординга ─────────────────────────────────────────
    private void finish() {
        removeCurrentViews();
        overlay.setVisibility(View.GONE);

        // Сохраняем флаг
        activity.getSharedPreferences(
                        MainActivity.PREFS_NAME,
                        Activity.MODE_PRIVATE)
                .edit()
                .putBoolean(KEY_PROFILE_ONBOARDING_DONE, true)
                .apply();
    }

    // ── Утилита: dp → px ─────────────────────────────────────────────
    private int dpToPx(int dp) {
        float density =
                activity.getResources().getDisplayMetrics().density;
        return Math.round(dp * density);
    }
}