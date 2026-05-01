package ru.sv.personaltrainer.onboarding;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager2.widget.ViewPager2;

import java.util.Arrays;
import java.util.List;

import ru.sv.personaltrainer.ExerciseListActivity;
import ru.sv.personaltrainer.MainActivity;
import ru.sv.personaltrainer.R;

public class OnBoardingActivity extends AppCompatActivity {

    private ViewPager2 viewPager;
    private Button btnNext;
    private TextView btnSkip;

    private static final int[] ICONS = {
            R.drawable.ic_fitness,
            R.drawable.ic_fitness,
            R.drawable.ic_fitness,
            R.drawable.ic_fitness,
            R.drawable.ic_fitness
    };

    private static final String[] TITLES = {
            "Добро пожаловать!",
            "Анализ в реальном времени",
            "Голосовые подсказки",
            "Запись тренировки",
            "Готовы начать?"
    };

    private static final String[] DESCRIPTIONS = {
            "\"Персональный тренер\" следит за вашей техникой\n"
                    + "с помощью камеры телефона и нейросети.\n"
                    + "Получайте мгновенные советы прямо во время\n"
                    + "выполнения упражнений!",

            "Направьте камеру на себя во время тренировки.\n\n"
                    + "Нейросеть обнаруживает ключевые точки\n"
                    + "на вашем теле и анализирует технику\n"
                    + "в режиме реального времени.",

            "При ошибке техники вы сразу услышите подсказку:\n"
                    + "• «Выпрямите спину»\n"
                    + "• «Опуститесь глубже»\n"
                    + "• «Не отрывайте пятки от пола»\n\n"
                    + "Приложение само считает повторения.",

            "Кнопка \"⏺ Запись\" сохраняет видео\n"
                    + "вашей тренировки в Галерею.\n"
                    + "Кнопка \"↺ Сброс\" обнуляет счётчик.\n"
                    + "Кнопка \"← Назад\" возвращает к выбору упражнения.",

            "Выберите упражнение из списка,\n"
                    + "встаньте перед камерой\n"
                    + "и начните тренироваться!\n\n"
                    + "Тренер всегда рядом 💪"
    };

    private static final int[] BACKGROUNDS = {
            0xFF1A1A2E,   // тёмно-синий
            0xFF0F3460,   // синий
            0xFF16213E,   // тёмный
            0xFF533483,   // фиолетовый
            0xFF1B4332    // тёмно-зелёный
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_onboarding);

        viewPager = findViewById(R.id.onboarding_viewpager);
        btnNext = findViewById(R.id.btn_next);
        btnSkip = findViewById(R.id.btn_skip);

        viewPager.setAdapter(new OnBoardingPagerAdapter());

        updateDots(0);

        viewPager.registerOnPageChangeCallback(
                new ViewPager2.OnPageChangeCallback() {
                    @Override
                    public void onPageSelected(int position) {
                        updateDots(position);
                        updateButtonText(position);
                    }
                });

        btnSkip.setOnClickListener(v -> finishOnBoarding());

        btnNext.setOnClickListener(v -> {
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
            btnNext.setText("Начать!");
            btnSkip.setVisibility(View.INVISIBLE);
        } else {
            btnNext.setText("Далее →");
            btnSkip.setVisibility(View.VISIBLE);
        }
    }

    private void updateDots(int selectedPosition) {
        TextView tvDots = findViewById(R.id.tv_dots);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < TITLES.length; i++) {
            if (i == selectedPosition) sb.append("● ");
            else sb.append("○ ");
        }
        tvDots.setText(sb.toString().trim());
    }

    private void finishOnBoarding() {
        getSharedPreferences(MainActivity.PREFS_NAME, MODE_PRIVATE)
                .edit()
                .putBoolean(MainActivity.KEY_ONBOARDING_DONE, true)
                .apply();

        startActivity(new Intent(this, ExerciseListActivity.class));
        finish();
    }

    private class OnBoardingPagerAdapter
            extends RecyclerView.Adapter<OnBoardingPagerAdapter.VH> {

        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent,
                                     int viewType) {
            View v = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_onboarding,
                            parent, false);
            return new VH(v);
        }

        @Override
        public void onBindViewHolder(@NonNull VH holder, int pos) {
            holder.root.setBackgroundColor(BACKGROUNDS[pos]);
            holder.image.setImageResource(ICONS[pos]);
            holder.title.setText(TITLES[pos]);
            holder.description.setText(DESCRIPTIONS[pos]);
        }

        @Override
        public int getItemCount() {
            return TITLES.length;
        }

        class VH extends RecyclerView.ViewHolder {
            View root;
            ImageView image;
            TextView title, description;

            VH(@NonNull View v) {
                super(v);
                root = v;
                image = v.findViewById(R.id.onboarding_image);
                title = v.findViewById(R.id.onboarding_title);
                description = v.findViewById(R.id.onboarding_description);
            }
        }
    }
}