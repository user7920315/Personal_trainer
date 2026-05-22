package ru.sv.personaltrainer.onboarding;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager2.widget.ViewPager2;

import java.util.List;

import ru.sv.personaltrainer.ExerciseListActivity;
import ru.sv.personaltrainer.MainActivity;
import ru.sv.personaltrainer.R;
import ru.sv.personaltrainer.databinding.ActivityOnboardingBinding;
import ru.sv.personaltrainer.databinding.ItemOnboardingBinding;

import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;

public class OnBoardingActivity extends AppCompatActivity {

    private ActivityOnboardingBinding binding;
    private ViewPager2 viewPager;

    private static final int[] ICONS = {
            R.drawable.ic_fitness, R.drawable.ic_fitness,
            R.drawable.ic_fitness, R.drawable.ic_fitness, R.drawable.ic_fitness
    };

    private static final String[] TITLES = {
            "Добро пожаловать!",
            "Анализ в реальном времени",
            "Голосовые подсказки",
            "Запись тренировки",
            "Готовы начать?"
    };

    private static final String[] DESCRIPTIONS = {
            "\"Персональный тренер\" следит за вашей техникой\nс помощью камеры телефона и нейросети.\nПолучайте мгновенные советы прямо во время\nвыполнения упражнений!",
            "Направьте камеру на себя во время тренировки.\n\nНейросеть обнаруживает ключевые точки\nна вашем теле и анализирует технику\nв режиме реального времени.",
            "При ошибке техники вы сразу услышите подсказку:\n• «Выпрямите спину»\n• «Опуститесь глубже»\n• «Не отрывайте пятки от пола»\n\nПриложение само считает повторения.",
            "Кнопка \"⏺ Запись\" сохраняет видео\nвашей тренировки в Галерею.\nКнопка \"↺ Сброс\" обнуляет счётчик.\nКнопка \"← Назад\" возвращает к выбору упражнения.",
            "Выберите упражнение из списка,\nвстаньте перед камерой\nи начните тренироваться!\n\nТренер всегда рядом 💪"
    };

    private static final int[] BACKGROUNDS = {
            0xFF1A1A2E, 0xFF0F3460, 0xFF16213E, 0xFF533483, 0xFF1B4332
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);

        binding = ActivityOnboardingBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        ViewCompat.setOnApplyWindowInsetsListener(binding.rootOnboarding, (v, insets) -> {
            Insets bars = insets.getInsets(
                    WindowInsetsCompat.Type.systemBars() |
                            WindowInsetsCompat.Type.displayCutout());
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return WindowInsetsCompat.CONSUMED;
        });

        viewPager = binding.onboardingViewpager;
        viewPager.setAdapter(new OnBoardingPagerAdapter());

        updateDots(0);

        viewPager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                updateDots(position);
                updateButtonText(position);
            }
        });

        binding.btnSkip.setOnClickListener(v -> finishOnBoarding());
        binding.btnNext.setOnClickListener(v -> {
            int current = viewPager.getCurrentItem();
            if (current < TITLES.length - 1) {
                viewPager.setCurrentItem(current + 1, true);
            } else {
                finishOnBoarding();
            }
        });
    }

    private void updateButtonText(int position) {
        if (position == TITLES.length - 1) {
            binding.btnNext.setText("Начать!");
            binding.btnSkip.setVisibility(View.INVISIBLE);
        } else {
            binding.btnNext.setText("Далее →");
            binding.btnSkip.setVisibility(View.VISIBLE);
        }
    }

    private void updateDots(int selectedPosition) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < TITLES.length; i++) {
            sb.append(i == selectedPosition ? "● " : "○ ");
        }
        binding.tvDots.setText(sb.toString().trim());
    }

    private void finishOnBoarding() {
        getSharedPreferences(MainActivity.PREFS_NAME, MODE_PRIVATE)
                .edit()
                .putBoolean(MainActivity.KEY_ONBOARDING_DONE, true)
                .apply();
        startActivity(new Intent(this, ExerciseListActivity.class));
        finish();
    }

    private class OnBoardingPagerAdapter extends RecyclerView.Adapter<OnBoardingPagerAdapter.VH> {

        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            ItemOnboardingBinding b = ItemOnboardingBinding.inflate(
                    LayoutInflater.from(parent.getContext()), parent, false);
            return new VH(b);
        }

        @Override
        public void onBindViewHolder(@NonNull VH holder, int pos) {
            holder.b.onboardingImage.setImageResource(ICONS[pos]);
            holder.b.onboardingTitle.setText(TITLES[pos]);
            holder.b.onboardingDescription.setText(DESCRIPTIONS[pos]);
            holder.b.getRoot().setBackgroundColor(BACKGROUNDS[pos]);
        }

        @Override
        public int getItemCount() {
            return TITLES.length;
        }

        class VH extends RecyclerView.ViewHolder {
            ItemOnboardingBinding b;
            VH(ItemOnboardingBinding b) {
                super(b.getRoot());
                this.b = b;
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        binding = null;
    }
}