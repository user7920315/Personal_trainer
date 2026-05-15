package ru.sv.personaltrainer;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
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
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import ru.sv.personaltrainer.overlay.ProfileOnboardingOverlay;

public class ProfileActivity extends AppCompatActivity {

    public static final String PREFS_NAME = "PersonalTrainerPrefs";
    public static final String KEY_HEIGHT = "height";
    public static final String KEY_WEIGHT = "weight";
    public static final String KEY_AGE = "age";
    public static final String KEY_GENDER = "gender";
    public static final String KEY_WEIGHT_LOG = "weight_log";
    public static final String KEY_WORKOUTS = "workouts";

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

    private SharedPreferences prefs;
    private Gson gson;
    private List<WorkoutRecord> workoutHistory;
    private List<WeightRecord> weightLog;
    private WorkoutHistoryAdapter adapter;
    private ProfileOnboardingOverlay profileOnboarding;


    private boolean isMale = true;


    public static class WorkoutRecord {
        public String exerciseId;
        public String exerciseName;
        public String icon;
        public int reps;
        public long timestamp;

        public WorkoutRecord(String exerciseId, String exerciseName,
                             String icon, int reps, long timestamp) {
            this.exerciseId = exerciseId;
            this.exerciseName = exerciseName;
            this.icon = icon;
            this.reps = reps;
            this.timestamp = timestamp;
        }
    }

    public static class WeightRecord {
        public float weight;
        public long timestamp;

        public WeightRecord(float weight, long timestamp) {
            this.weight = weight;
            this.timestamp = timestamp;
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_profile);

        prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        gson = new Gson();

        initViews();
        loadData();
        setupChart();
        updateStats();
        updateHistoryList();

        ScrollView scrollView = findViewById(R.id.scrollViewProfile);
        profileOnboarding = new ProfileOnboardingOverlay(this, scrollView);
        profileOnboarding.startIfNeeded();
    }

    private void initViews() {
        findViewById(R.id.btnBack).setOnClickListener(v -> finish());

        etHeight = findViewById(R.id.etHeight);
        etWeight = findViewById(R.id.etWeight);
        etAge = findViewById(R.id.etAge);
        btnGenderMale = findViewById(R.id.btnGenderMale);
        btnGenderFemale = findViewById(R.id.btnGenderFemale);
        btnCalculateBmi = findViewById(R.id.btnCalculateBmi);
        layoutBmiResult = findViewById(R.id.layoutBmiResult);
        tvBmiValue = findViewById(R.id.tvBmiValue);
        tvBmiResultCategory = findViewById(R.id.tvBmiResultCategory);
        tvBmiNorm = findViewById(R.id.tvBmiNorm);
        tvBmiCategory = findViewById(R.id.tvBmiCategory);
        tvNoWeightData = findViewById(R.id.tvNoWeightData);
        weightChart = findViewById(R.id.weightChart);
        tvTotalWorkouts = findViewById(R.id.tvTotalWorkouts);
        tvCurrentStreak = findViewById(R.id.tvCurrentStreak);
        tvWeekWorkouts = findViewById(R.id.tvWeekWorkouts);
        tvNoHistory = findViewById(R.id.tvNoHistory);
        rvHistory = findViewById(R.id.rvHistory);
        btnClearHistory = findViewById(R.id.btnClearHistory);


        btnGenderMale.setOnClickListener(v -> selectGender(true));
        btnGenderFemale.setOnClickListener(v -> selectGender(false));

        btnCalculateBmi.setOnClickListener(v -> calculateBmi());
        btnClearHistory.setOnClickListener(v -> confirmClearHistory());

        rvHistory.setLayoutManager(new LinearLayoutManager(this));
    }


    private void selectGender(boolean male) {
        isMale = male;


        btnGenderMale.setBackgroundResource(
                male ? R.drawable.bg_gender_selected
                        : R.drawable.bg_gender_unselected);

        btnGenderFemale.setBackgroundResource(
                male ? R.drawable.bg_gender_unselected
                        : R.drawable.bg_gender_selected);


        updateGenderTextColors(male);


        prefs.edit()
                .putString(KEY_GENDER, male ? "male" : "female")
                .apply();
    }

    private void updateGenderTextColors(boolean male) {

        for (int i = 0; i < btnGenderMale.getChildCount(); i++) {
            View child = btnGenderMale.getChildAt(i);
            if (child instanceof TextView) {
                ((TextView) child).setTextColor(
                        male ? Color.WHITE : Color.parseColor("#AAAAAA"));
            }
        }
        for (int i = 0; i < btnGenderFemale.getChildCount(); i++) {
            View child = btnGenderFemale.getChildAt(i);
            if (child instanceof TextView) {
                ((TextView) child).setTextColor(
                        male ? Color.parseColor("#AAAAAA") : Color.WHITE);
            }
        }
    }


    private void loadData() {

        float savedHeight = prefs.getFloat(KEY_HEIGHT, 0f);
        float savedWeight = prefs.getFloat(KEY_WEIGHT, 0f);
        int savedAge = prefs.getInt(KEY_AGE, 0);

        if (savedHeight > 0)
            etHeight.setText(String.valueOf((int) savedHeight));
        if (savedWeight > 0)
            etWeight.setText(String.format(Locale.US, "%.1f", savedWeight));
        if (savedAge > 0)
            etAge.setText(String.valueOf(savedAge));

        String savedGender = prefs.getString(KEY_GENDER, "male");
        isMale = "male".equals(savedGender);
        selectGender(isMale);

        if (savedHeight > 0 && savedWeight > 0) {
            showBmiResult(savedHeight, savedWeight, savedAge);
        }

        String weightJson = prefs.getString(KEY_WEIGHT_LOG, "[]");
        Type weightType = new TypeToken<List<WeightRecord>>() {
        }.getType();
        weightLog = gson.fromJson(weightJson, weightType);
        if (weightLog == null) weightLog = new ArrayList<>();

        String workoutsJson = prefs.getString(KEY_WORKOUTS, "[]");
        Type workoutsType = new TypeToken<List<WorkoutRecord>>() {
        }.getType();
        workoutHistory = gson.fromJson(workoutsJson, workoutsType);
        if (workoutHistory == null) workoutHistory = new ArrayList<>();
    }

    private void calculateBmi() {
        String heightStr = etHeight.getText() != null
                ? etHeight.getText().toString().trim() : "";
        String weightStr = etWeight.getText() != null
                ? etWeight.getText().toString().trim() : "";
        String ageStr = etAge.getText() != null
                ? etAge.getText().toString().trim() : "";

        if (heightStr.isEmpty() || weightStr.isEmpty()) {
            Toast.makeText(this,
                    "Введите рост и вес", Toast.LENGTH_SHORT).show();
            return;
        }

        float height, weight;
        int age = 0;

        try {
            height = Float.parseFloat(heightStr);
            weight = Float.parseFloat(weightStr);
            if (!ageStr.isEmpty()) age = Integer.parseInt(ageStr);
        } catch (NumberFormatException e) {
            Toast.makeText(this,
                    "Некорректные данные", Toast.LENGTH_SHORT).show();
            return;
        }

        if (height < 50 || height > 300) {
            Toast.makeText(this,
                    "Введите корректный рост (50–300 см)",
                    Toast.LENGTH_SHORT).show();
            return;
        }
        if (weight < 10 || weight > 500) {
            Toast.makeText(this,
                    "Введите корректный вес (10–500 кг)",
                    Toast.LENGTH_SHORT).show();
            return;
        }
        if (age < 0 || age > 120) {
            Toast.makeText(this,
                    "Введите корректный возраст",
                    Toast.LENGTH_SHORT).show();
            return;
        }


        prefs.edit()
                .putFloat(KEY_HEIGHT, height)
                .putFloat(KEY_WEIGHT, weight)
                .putInt(KEY_AGE, age)
                .apply();


        addWeightRecord(weight);


        showBmiResult(height, weight, age);


        setupChart();

        Toast.makeText(this, "Данные сохранены", Toast.LENGTH_SHORT).show();
    }

    private void showBmiResult(float height, float weight, int age) {
        float heightM = height / 100f;
        float bmi = weight / (heightM * heightM);

        String category;
        int color;

        if (bmi < 16.0f) {
            category = "Выраженный\nдефицит";
            color = Color.parseColor("#FF4444");
        } else if (bmi < 18.5f) {
            category = "Недостаточный\nвес";
            color = Color.parseColor("#FF8800");
        } else if (bmi < 25.0f) {
            category = "Норма ✓";
            color = Color.parseColor("#00FF88");
        } else if (bmi < 30.0f) {
            category = "Избыточный\nвес";
            color = Color.parseColor("#FFFF00");
        } else if (bmi < 35.0f) {
            category = "Ожирение I";
            color = Color.parseColor("#FF8800");
        } else if (bmi < 40.0f) {
            category = "Ожирение II";
            color = Color.parseColor("#FF4444");
        } else {
            category = "Ожирение III";
            color = Color.parseColor("#CC0000");
        }

        String normText = buildNormText(bmi, age);

        tvBmiValue.setText(String.format(Locale.US, "%.1f", bmi));
        tvBmiResultCategory.setText(category);
        tvBmiResultCategory.setTextColor(color);
        tvBmiNorm.setText(normText);

        tvBmiCategory.setText(category.replace("\n", " "));
        tvBmiCategory.setTextColor(color);

        layoutBmiResult.setVisibility(View.VISIBLE);
    }

    private String buildNormText(float bmi, int age) {
        if (age <= 0) {
            return isMale
                    ? "Норма для мужчин: 18.5 – 24.9"
                    : "Норма для женщин: 18.5 – 24.9";
        }

        float normMin = 18.5f;
        float normMax;

        if (isMale) {
            if (age < 45) normMax = 24.9f;
            else if (age < 55) normMax = 25.9f;
            else if (age < 65) normMax = 26.9f;
            else normMax = 27.9f;
        } else {
            if (age < 25) normMax = 24.9f;
            else if (age < 45) normMax = 25.9f;
            else if (age < 55) normMax = 26.9f;
            else if (age < 65) normMax = 27.9f;
            else normMax = 28.9f;
        }

        String gender = isMale ? "мужчин" : "женщин";
        String inNorm = (bmi >= normMin && bmi <= normMax)
                ? " ✓" : "";

        return String.format(Locale.US,
                "Норма для %s %d лет: %.1f – %.1f%s",
                gender, age, normMin, normMax, inNorm);
    }

    private void addWeightRecord(float weight) {
        long now = System.currentTimeMillis();

        Calendar cal = Calendar.getInstance();
        int todayDay = cal.get(Calendar.DAY_OF_YEAR);
        int todayYear = cal.get(Calendar.YEAR);

        for (int i = 0; i < weightLog.size(); i++) {
            cal.setTimeInMillis(weightLog.get(i).timestamp);
            if (cal.get(Calendar.DAY_OF_YEAR) == todayDay
                    && cal.get(Calendar.YEAR) == todayYear) {
                weightLog.get(i).weight = weight;
                saveWeightLog();
                return;
            }
        }

        weightLog.add(new WeightRecord(weight, now));
        if (weightLog.size() > 30) weightLog.remove(0);
        saveWeightLog();
    }

    private void saveWeightLog() {
        prefs.edit()
                .putString(KEY_WEIGHT_LOG, gson.toJson(weightLog))
                .apply();
    }

    private void setupChart() {
        if (weightLog.isEmpty()) {
            tvNoWeightData.setVisibility(View.VISIBLE);
            weightChart.setVisibility(View.GONE);
            return;
        }

        tvNoWeightData.setVisibility(View.GONE);
        weightChart.setVisibility(View.VISIBLE);

        List<WeightRecord> sorted = new ArrayList<>(weightLog);
        Collections.sort(sorted,
                (a, b) -> Long.compare(a.timestamp, b.timestamp));

        List<Entry> entries = new ArrayList<>();
        for (int i = 0; i < sorted.size(); i++) {
            entries.add(new Entry(i, sorted.get(i).weight));
        }

        LineDataSet dataSet = new LineDataSet(entries, "Вес (кг)");
        dataSet.setColor(Color.parseColor("#E94560"));
        dataSet.setCircleColor(Color.parseColor("#E94560"));
        dataSet.setCircleHoleColor(Color.parseColor("#0F3460"));
        dataSet.setLineWidth(2.5f);
        dataSet.setCircleRadius(5f);
        dataSet.setCircleHoleRadius(2.5f);
        dataSet.setDrawValues(true);
        dataSet.setValueTextColor(Color.WHITE);
        dataSet.setValueTextSize(10f);
        dataSet.setValueFormatter(new ValueFormatter() {
            @Override
            public String getFormattedValue(float value) {
                return String.format(Locale.US, "%.1f", value);
            }
        });

        dataSet.setMode(sorted.size() > 1
                ? LineDataSet.Mode.CUBIC_BEZIER
                : LineDataSet.Mode.LINEAR);
        dataSet.setDrawFilled(sorted.size() > 1);
        dataSet.setFillColor(Color.parseColor("#33E94560"));


        final List<WeightRecord> finalSorted = sorted;
        final SimpleDateFormat sdf =
                new SimpleDateFormat("dd.MM", Locale.US);


        weightChart.setData(new LineData(dataSet));
        weightChart.setBackgroundColor(Color.TRANSPARENT);
        weightChart.getDescription().setEnabled(false);
        weightChart.getLegend().setEnabled(false);
        weightChart.setTouchEnabled(true);
        weightChart.setDragEnabled(true);
        weightChart.setScaleEnabled(false);
        weightChart.setPinchZoom(false);
        weightChart.setExtraBottomOffset(8f);


        XAxis xAxis = weightChart.getXAxis();
        xAxis.setPosition(XAxis.XAxisPosition.BOTTOM);
        xAxis.setTextColor(Color.parseColor("#AAAAAA"));
        xAxis.setTextSize(10f);
        xAxis.setGridColor(Color.parseColor("#22FFFFFF"));
        xAxis.setAxisLineColor(Color.parseColor("#33FFFFFF"));
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
        yAxisLeft.setTextColor(Color.parseColor("#AAAAAA"));
        yAxisLeft.setTextSize(10f);
        yAxisLeft.setGridColor(Color.parseColor("#22FFFFFF"));
        yAxisLeft.setAxisLineColor(Color.parseColor("#33FFFFFF"));


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


    private void updateStats() {
        tvTotalWorkouts.setText(String.valueOf(workoutHistory.size()));
        tvCurrentStreak.setText(String.valueOf(calcStreak()));
        tvWeekWorkouts.setText(String.valueOf(calcWeekWorkouts()));
    }

    private int calcStreak() {
        if (workoutHistory.isEmpty()) return 0;

        SimpleDateFormat dayFmt =
                new SimpleDateFormat("yyyyMMdd", Locale.US);
        List<String> uniqueDays = new ArrayList<>();

        for (WorkoutRecord r : workoutHistory) {
            String day = dayFmt.format(new Date(r.timestamp));
            if (!uniqueDays.contains(day)) uniqueDays.add(day);
        }

        Collections.sort(uniqueDays, Collections.reverseOrder());

        Calendar today = Calendar.getInstance();
        int streak = 0;

        for (String dayStr : uniqueDays) {
            String todayStr = dayFmt.format(today.getTime());
            if (dayStr.equals(todayStr)) {
                streak++;
                today.add(Calendar.DAY_OF_YEAR, -1);
            } else {
                break;
            }
        }
        return streak;
    }

    private int calcWeekWorkouts() {
        Calendar startOfWeek = Calendar.getInstance();
        startOfWeek.set(Calendar.DAY_OF_WEEK,
                startOfWeek.getFirstDayOfWeek());
        startOfWeek.set(Calendar.HOUR_OF_DAY, 0);
        startOfWeek.set(Calendar.MINUTE, 0);
        startOfWeek.set(Calendar.SECOND, 0);
        startOfWeek.set(Calendar.MILLISECOND, 0);

        long weekStart = startOfWeek.getTimeInMillis();
        int count = 0;
        for (WorkoutRecord r : workoutHistory) {
            if (r.timestamp >= weekStart) count++;
        }
        return count;
    }


    private void updateHistoryList() {
        if (workoutHistory.isEmpty()) {
            tvNoHistory.setVisibility(View.VISIBLE);
            rvHistory.setVisibility(View.GONE);
            return;
        }

        tvNoHistory.setVisibility(View.GONE);
        rvHistory.setVisibility(View.VISIBLE);

        List<WorkoutRecord> reversed = new ArrayList<>(workoutHistory);
        Collections.reverse(reversed);
        if (reversed.size() > 20) reversed = reversed.subList(0, 20);

        adapter = new WorkoutHistoryAdapter(reversed);
        rvHistory.setAdapter(adapter);
    }

    private void confirmClearHistory() {
        if (workoutHistory.isEmpty()) {
            Toast.makeText(this,
                    "История уже пуста", Toast.LENGTH_SHORT).show();
            return;
        }

        new AlertDialog.Builder(this)
                .setTitle("Очистить историю?")
                .setMessage("Все записи тренировок будут удалены.")
                .setPositiveButton("Удалить", (d, w) -> {
                    workoutHistory.clear();
                    prefs.edit().putString(KEY_WORKOUTS, "[]").apply();
                    updateStats();
                    updateHistoryList();
                    Toast.makeText(this,
                            "История очищена", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Отмена", null)
                .show();
    }


    public static void saveWorkout(Context context,
                                   String exerciseId,
                                   String exerciseName,
                                   String icon,
                                   int reps) {
        if (reps <= 0) return;

        SharedPreferences prefs =
                context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        Gson gson = new Gson();

        String json = prefs.getString(KEY_WORKOUTS, "[]");
        Type type = new TypeToken<List<WorkoutRecord>>() {
        }.getType();
        List<WorkoutRecord> history = gson.fromJson(json, type);
        if (history == null) history = new ArrayList<>();

        history.add(new WorkoutRecord(
                exerciseId, exerciseName, icon,
                reps, System.currentTimeMillis()));

        if (history.size() > 100) history.remove(0);

        prefs.edit()
                .putString(KEY_WORKOUTS, gson.toJson(history))
                .apply();
    }


    private static class WorkoutHistoryAdapter
            extends RecyclerView.Adapter<WorkoutHistoryAdapter.ViewHolder> {

        private final List<WorkoutRecord> items;
        private final SimpleDateFormat sdf =
                new SimpleDateFormat("dd.MM.yyyy  HH:mm", Locale.US);

        WorkoutHistoryAdapter(List<WorkoutRecord> items) {
            this.items = items;
        }

        @Override
        public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_workout_history, parent, false);
            return new ViewHolder(v);
        }

        @Override
        public void onBindViewHolder(ViewHolder h, int position) {
            WorkoutRecord r = items.get(position);
            h.tvIcon.setText(r.icon != null ? r.icon : "💪");
            h.tvName.setText(r.exerciseName);
            h.tvDate.setText(sdf.format(new Date(r.timestamp)));
            h.tvReps.setText(String.valueOf(r.reps));
        }

        @Override
        public int getItemCount() {
            return items.size();
        }

        static class ViewHolder extends RecyclerView.ViewHolder {
            TextView tvIcon, tvName, tvDate, tvReps;

            ViewHolder(View v) {
                super(v);
                tvIcon = v.findViewById(R.id.tvWorkoutIcon);
                tvName = v.findViewById(R.id.tvWorkoutName);
                tvDate = v.findViewById(R.id.tvWorkoutDate);
                tvReps = v.findViewById(R.id.tvWorkoutReps);
            }
        }
    }
}