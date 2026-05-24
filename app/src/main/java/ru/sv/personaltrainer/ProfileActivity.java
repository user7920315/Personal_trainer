package ru.sv.personaltrainer;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.github.mikephil.charting.formatter.ValueFormatter;
import com.google.android.material.textfield.TextInputEditText;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import ru.sv.personaltrainer.databinding.ActivityProfileBinding;
import ru.sv.personaltrainer.databinding.ItemWorkoutHistoryBinding;
import ru.sv.personaltrainer.model.BmiResult;
import ru.sv.personaltrainer.model.UserProfile;
import ru.sv.personaltrainer.model.WeightRecord;
import ru.sv.personaltrainer.model.WorkoutRecord;
import ru.sv.personaltrainer.overlay.ProfileOnboardingOverlay;
import ru.sv.personaltrainer.repository.WorkoutRepository;
import ru.sv.personaltrainer.viewmodel.ProfileViewModel;

public class ProfileActivity extends AppCompatActivity {

    private ActivityProfileBinding binding;
    private ProfileViewModel viewModel;

    private TextInputEditText etHeight, etWeight, etAge;
    private LinearLayout btnGenderMale, btnGenderFemale;
    private Button btnCalculateBmi;
    private View layoutBmiResult;
    private TextView tvBmiValue;
    private TextView tvBmiResultCategory;
    private TextView tvBmiNorm;
    private TextView tvBmiCategory;
    private TextView tvNoWeightData;
    private LineChart weightChart;
    private TextView tvTotalWorkouts;
    private TextView tvCurrentStreak;
    private TextView tvWeekWorkouts;
    private TextView tvNoHistory;
    private RecyclerView rvHistory;
    private Button btnClearHistory;

    private WorkoutHistoryAdapter adapter;
    private boolean isMale = true;
    private ProfileOnboardingOverlay profileOnboarding;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);

        binding = ActivityProfileBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        ViewCompat.setOnApplyWindowInsetsListener(binding.rootProfile, (v, insets) -> {
            Insets bars = insets.getInsets(
                    WindowInsetsCompat.Type.systemBars() |
                            WindowInsetsCompat.Type.displayCutout());
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return WindowInsetsCompat.CONSUMED;
        });

        viewModel = new ViewModelProvider(this).get(ProfileViewModel.class);

        initViews();
        setupObservers();
        applyGenderState();

        profileOnboarding = new ProfileOnboardingOverlay(this, binding.scrollViewProfile);
        profileOnboarding.startIfNeeded();
    }

    private void initViews() {
        binding.btnBack.setOnClickListener(v -> finish());

        etHeight = binding.etHeight;
        etWeight = binding.etWeight;
        etAge = binding.etAge;
        btnGenderMale = binding.btnGenderMale;
        btnGenderFemale = binding.btnGenderFemale;
        btnCalculateBmi = binding.btnCalculateBmi;
        layoutBmiResult = binding.layoutBmiResult;
        tvBmiValue = binding.tvBmiValue;
        tvBmiResultCategory = binding.tvBmiResultCategory;
        tvBmiNorm = binding.tvBmiNorm;
        tvBmiCategory = binding.tvBmiCategory;
        tvNoWeightData = binding.tvNoWeightData;
        weightChart = binding.weightChart;
        tvTotalWorkouts = binding.tvTotalWorkouts;
        tvCurrentStreak = binding.tvCurrentStreak;
        tvWeekWorkouts = binding.tvWeekWorkouts;
        tvNoHistory = binding.tvNoHistory;
        rvHistory = binding.rvHistory;
        btnClearHistory = binding.btnClearHistory;

        btnGenderMale.setOnClickListener(v -> selectGender(true));
        btnGenderFemale.setOnClickListener(v -> selectGender(false));
        btnCalculateBmi.setOnClickListener(v -> onCalculateBmi());
        btnClearHistory.setOnClickListener(v -> confirmClearHistory());

        rvHistory.setLayoutManager(new LinearLayoutManager(this));
    }

    private void setupObservers() {
        viewModel.getUserProfile().observe(this, profile -> {
            if (profile == null) return;
            if (profile.height > 0) etHeight.setText(String.valueOf((int) profile.height));
            if (profile.weight > 0) etWeight.setText(String.format(Locale.US, "%.1f", profile.weight));
            if (profile.age > 0) etAge.setText(String.valueOf(profile.age));
            isMale = "male".equals(profile.gender);
            applyGenderState();
        });

        viewModel.getBmiResult().observe(this, this::showBmiResult);
        viewModel.getWeightLog().observe(this, this::setupChart);
        viewModel.getWorkouts().observe(this, this::updateHistoryList);
        viewModel.getStats().observe(this, this::updateStats);
    }

    private void selectGender(boolean male) {
        isMale = male;
        applyGenderState();
    }

    private void applyGenderState() {
        btnGenderMale.setBackgroundResource(isMale ? R.drawable.bg_gender_selected : R.drawable.bg_gender_unselected);
        btnGenderFemale.setBackgroundResource(isMale ? R.drawable.bg_gender_unselected : R.drawable.bg_gender_selected);
        updateGenderTextColors(isMale);
    }

    private void updateGenderTextColors(boolean male) {
        for (int i = 0; i < btnGenderMale.getChildCount(); i++) {
            View child = btnGenderMale.getChildAt(i);
            if (child instanceof TextView) {
                ((TextView) child).setTextColor(male ? android.graphics.Color.WHITE : android.graphics.Color.parseColor("#AAAAAA"));
            }
        }
        for (int i = 0; i < btnGenderFemale.getChildCount(); i++) {
            View child = btnGenderFemale.getChildAt(i);
            if (child instanceof TextView) {
                ((TextView) child).setTextColor(male ? android.graphics.Color.parseColor("#AAAAAA") : android.graphics.Color.WHITE);
            }
        }
    }

    private void onCalculateBmi() {
        String heightStr = etHeight.getText() != null ? etHeight.getText().toString().trim() : "";
        String weightStr = etWeight.getText() != null ? etWeight.getText().toString().trim() : "";
        String ageStr = etAge.getText() != null ? etAge.getText().toString().trim() : "";

        if (heightStr.isEmpty() || weightStr.isEmpty()) {
            Toast.makeText(this, "Введите рост и вес", Toast.LENGTH_SHORT).show();
            return;
        }

        float height, weight;
        int age = 0;
        try {
            height = Float.parseFloat(heightStr);
            weight = Float.parseFloat(weightStr);
            if (!ageStr.isEmpty()) age = Integer.parseInt(ageStr);
        } catch (NumberFormatException e) {
            Toast.makeText(this, "Некорректные данные", Toast.LENGTH_SHORT).show();
            return;
        }

        if (height < 50 || height > 300) {
            Toast.makeText(this, "Введите корректный рост (50–300 см)", Toast.LENGTH_SHORT).show();
            return;
        }
        if (weight < 10 || weight > 500) {
            Toast.makeText(this, "Введите корректный вес (10–500 кг)", Toast.LENGTH_SHORT).show();
            return;
        }
        if (age < 0 || age > 120) {
            Toast.makeText(this, "Введите корректный возраст", Toast.LENGTH_SHORT).show();
            return;
        }

        String gender = isMale ? "male" : "female";
        viewModel.saveProfile(height, weight, age, gender);
        Toast.makeText(this, "Данные сохранены", Toast.LENGTH_SHORT).show();
    }

    private void showBmiResult(BmiResult result) {
        if (result == null) {
            layoutBmiResult.setVisibility(View.GONE);
            return;
        }
        tvBmiValue.setText(String.format(Locale.US, "%.1f", result.bmi));
        tvBmiResultCategory.setText(result.category);
        tvBmiResultCategory.setTextColor(result.color);
        tvBmiNorm.setText(result.normText);
        tvBmiCategory.setText(result.category.replace("\n", " "));
        tvBmiCategory.setTextColor(result.color);
        layoutBmiResult.setVisibility(View.VISIBLE);
    }

    private void setupChart(List<WeightRecord> log) {
        if (log == null || log.isEmpty()) {
            tvNoWeightData.setVisibility(View.VISIBLE);
            weightChart.setVisibility(View.GONE);
            return;
        }
        tvNoWeightData.setVisibility(View.GONE);
        weightChart.setVisibility(View.VISIBLE);

        List<WeightRecord> sorted = new ArrayList<>(log);
        Collections.sort(sorted, (a, b) -> Long.compare(a.timestamp, b.timestamp));
        List<Entry> entries = new ArrayList<>();
        for (int i = 0; i < sorted.size(); i++) {
            entries.add(new Entry(i, sorted.get(i).weight));
        }

        LineDataSet dataSet = new LineDataSet(entries, "Вес (кг)");
        dataSet.setColor(android.graphics.Color.parseColor("#E94560"));
        dataSet.setCircleColor(android.graphics.Color.parseColor("#E94560"));
        dataSet.setCircleHoleColor(android.graphics.Color.parseColor("#0F3460"));
        dataSet.setLineWidth(2.5f);
        dataSet.setCircleRadius(5f);
        dataSet.setCircleHoleRadius(2.5f);
        dataSet.setDrawValues(true);
        dataSet.setValueTextColor(android.graphics.Color.WHITE);
        dataSet.setValueTextSize(10f);
        dataSet.setValueFormatter(new ValueFormatter() {
            @Override
            public String getFormattedValue(float value) {
                return String.format(Locale.US, "%.1f", value);
            }
        });
        dataSet.setMode(sorted.size() > 1 ? LineDataSet.Mode.CUBIC_BEZIER : LineDataSet.Mode.LINEAR);
        dataSet.setDrawFilled(sorted.size() > 1);
        dataSet.setFillColor(android.graphics.Color.parseColor("#33E94560"));

        final List<WeightRecord> finalSorted = sorted;
        final SimpleDateFormat sdf = new SimpleDateFormat("dd.MM", Locale.US);

        weightChart.setData(new LineData(dataSet));
        weightChart.setBackgroundColor(android.graphics.Color.TRANSPARENT);
        weightChart.getDescription().setEnabled(false);
        weightChart.getLegend().setEnabled(false);
        weightChart.setTouchEnabled(true);
        weightChart.setDragEnabled(true);
        weightChart.setScaleEnabled(false);
        weightChart.setPinchZoom(false);
        weightChart.setExtraBottomOffset(8f);

        XAxis xAxis = weightChart.getXAxis();
        xAxis.setPosition(XAxis.XAxisPosition.BOTTOM);
        xAxis.setTextColor(android.graphics.Color.parseColor("#AAAAAA"));
        xAxis.setTextSize(10f);
        xAxis.setGridColor(android.graphics.Color.parseColor("#22FFFFFF"));
        xAxis.setAxisLineColor(android.graphics.Color.parseColor("#33FFFFFF"));
        xAxis.setGranularity(1f);
        xAxis.setLabelCount(Math.min(sorted.size(), 5), false);
        xAxis.setValueFormatter(new ValueFormatter() {
            @Override
            public String getFormattedValue(float value) {
                int idx = Math.round(value);
                if (idx < 0 || idx >= finalSorted.size()) return "";
                return sdf.format(new Date(finalSorted.get(idx).timestamp));
            }
        });

        YAxis yAxisLeft = weightChart.getAxisLeft();
        yAxisLeft.setTextColor(android.graphics.Color.parseColor("#AAAAAA"));
        yAxisLeft.setTextSize(10f);
        yAxisLeft.setGridColor(android.graphics.Color.parseColor("#22FFFFFF"));
        yAxisLeft.setAxisLineColor(android.graphics.Color.parseColor("#33FFFFFF"));

        if (!entries.isEmpty()) {
            float minVal = entries.get(0).getY();
            float maxVal = entries.get(0).getY();
            for (Entry e : entries) {
                if (e.getY() < minVal) minVal = e.getY();
                if (e.getY() > maxVal) maxVal = e.getY();
            }
            yAxisLeft.setAxisMinimum(minVal - 3f);
            yAxisLeft.setAxisMaximum(maxVal + 3f);
        }
        weightChart.getAxisRight().setEnabled(false);
        weightChart.animateX(500);
        weightChart.invalidate();
    }

    private void updateStats(WorkoutRepository.Stats stats) {
        if (stats == null) return;
        tvTotalWorkouts.setText(String.valueOf(stats.total));
        tvCurrentStreak.setText(String.valueOf(stats.streak));
        tvWeekWorkouts.setText(String.valueOf(stats.weekWorkouts));
    }

    private void updateHistoryList(List<WorkoutRecord> history) {
        if (history == null || history.isEmpty()) {
            tvNoHistory.setVisibility(View.VISIBLE);
            rvHistory.setVisibility(View.GONE);
            return;
        }
        tvNoHistory.setVisibility(View.GONE);
        rvHistory.setVisibility(View.VISIBLE);

        List<WorkoutRecord> reversed = new ArrayList<>(history);
        Collections.reverse(reversed);
        if (reversed.size() > 20) reversed = reversed.subList(0, 20);

        adapter = new WorkoutHistoryAdapter(reversed);
        rvHistory.setAdapter(adapter);
    }

    private void confirmClearHistory() {
        List<WorkoutRecord> current = viewModel.getWorkouts().getValue();
        if (current == null || current.isEmpty()) {
            Toast.makeText(this, "История уже пуста", Toast.LENGTH_SHORT).show();
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle("Очистить историю?")
                .setMessage("Все записи тренировок будут удалены.")
                .setPositiveButton("Удалить", (d, w) -> viewModel.clearHistory())
                .setNegativeButton("Отмена", null)
                .show();
    }

    private static class WorkoutHistoryAdapter extends RecyclerView.Adapter<WorkoutHistoryAdapter.ViewHolder> {
        private final List<WorkoutRecord> items;
        private final SimpleDateFormat sdf = new SimpleDateFormat("dd.MM.yyyy  HH:mm", Locale.US);

        WorkoutHistoryAdapter(List<WorkoutRecord> items) {
            this.items = items;
        }

        @Override
        public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            ItemWorkoutHistoryBinding b = ItemWorkoutHistoryBinding.inflate(
                    LayoutInflater.from(parent.getContext()), parent, false);
            return new ViewHolder(b);
        }

        @Override
        public void onBindViewHolder(ViewHolder h, int position) {
            WorkoutRecord r = items.get(position);
            h.b.tvWorkoutIcon.setText(r.icon != null ? r.icon : "💪");
            h.b.tvWorkoutName.setText(r.exerciseName);
            h.b.tvWorkoutDate.setText(sdf.format(new Date(r.timestamp)));
            h.b.tvWorkoutReps.setText(String.valueOf(r.reps));
        }

        @Override
        public int getItemCount() {
            return items.size();
        }

        static class ViewHolder extends RecyclerView.ViewHolder {
            ItemWorkoutHistoryBinding b;
            ViewHolder(ItemWorkoutHistoryBinding b) {
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