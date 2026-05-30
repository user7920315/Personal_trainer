package ru.sv.personaltrainer.onboarding;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager2.widget.ViewPager2;

import ru.sv.personaltrainer.ExerciseListActivity;
import ru.sv.personaltrainer.R;
import ru.sv.personaltrainer.databinding.ActivityOnboardingBinding;
import ru.sv.personaltrainer.databinding.ItemOnboardingBinding;
import ru.sv.personaltrainer.viewmodel.OnBoardingViewModel;

import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;

public class OnBoardingActivity extends AppCompatActivity {

    private ActivityOnboardingBinding binding;
    private ViewPager2 viewPager;
    private OnBoardingViewModel viewModel;

    private static final int[] TITLE_RES = {
            R.string.onboarding_title_1, R.string.onboarding_title_2,
            R.string.onboarding_title_3, R.string.onboarding_title_4,
            R.string.onboarding_title_5
    };

    private static final int[] DESC_RES = {
            R.string.onboarding_desc_1, R.string.onboarding_desc_2,
            R.string.onboarding_desc_3, R.string.onboarding_desc_4,
            R.string.onboarding_desc_5
    };

    private static final int[] BACKGROUNDS = {
            R.color.onboarding_bg_1, R.color.onboarding_bg_2,
            R.color.onboarding_bg_3, R.color.onboarding_bg_4,
            R.color.onboarding_bg_5
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

        viewModel = new ViewModelProvider(this).get(OnBoardingViewModel.class);
        viewModel.getOnboardingComplete().observe(this, done -> {
            if (done != null && done) {
                startActivity(new Intent(this, ExerciseListActivity.class));
                finish();
            }
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
            if (current < TITLE_RES.length - 1) {
                viewPager.setCurrentItem(current + 1, true);
            } else {
                finishOnBoarding();
            }
        });
    }

    private void updateButtonText(int position) {
        if (position == TITLE_RES.length - 1) {
            binding.btnNext.setText(R.string.onboarding_btn_start);
            binding.btnSkip.setVisibility(View.INVISIBLE);
        } else {
            binding.btnNext.setText(R.string.onboarding_btn_next);
            binding.btnSkip.setVisibility(View.VISIBLE);
        }
    }

    private void updateDots(int selectedPosition) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < TITLE_RES.length; i++) {
            sb.append(i == selectedPosition ? "● " : "○ ");
        }
        binding.tvDots.setText(sb.toString().trim());
    }

    private void finishOnBoarding() {
        viewModel.finishOnboarding();
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
            holder.b.onboardingImage.setImageResource(R.drawable.ic_fitness);
            holder.b.onboardingTitle.setText(TITLE_RES[pos]);
            holder.b.onboardingDescription.setText(DESC_RES[pos]);
            holder.b.getRoot().setBackgroundColor(getColor(BACKGROUNDS[pos]));
        }

        @Override
        public int getItemCount() {
            return TITLE_RES.length;
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