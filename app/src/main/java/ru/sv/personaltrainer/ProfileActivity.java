package ru.sv.personaltrainer;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
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

public class ProfileActivity extends AppCompatActivity {

    // ── SharedPreferences ─────────────────────────────
    public static final String PREFS_NAME      = "PersonalTrainerPrefs";
    public static final String KEY_HEIGHT      = "height";
    public static final String KEY_WEIGHT      = "weight";
    public static final String KEY_WEIGHT_LOG  = "weight_log";  // JSON список
    public static final String KEY_WORKOUTS    = "workouts";    // JSON список

    // ── UI ───────────────────────────────────────────
    private TextInputEditText etHeight, etWeight;
    private Button            btnCalculateBmi;
    private View              layoutBmiResult;
    private TextView          tvBmiValue;
    private TextView          tvBmiResultCategory;
    private TextView          tvBmiCategory;
    private TextView          tvNoWeightData;
    private LineChart         weightChart;
    private TextView          tvTotalWorkouts;
    private TextView          tvCurrentStreak;
    private TextView          tvWeekWorkouts;
    private TextView          tvNoHistory;
    private RecyclerView      rvHistory;
    private Button            btnClearHistory;

    // ── Данные ───────────────────────────────────────
    private SharedPreferences prefs;
    private Gson              gson;
    private List<WorkoutRecord>  workoutHistory;
    private List<WeightRecord>   weightLog;
    private WorkoutHistoryAdapter adapter;

    // ── Модели данных ─────────────────────────────────

    public static class WorkoutRecord {
        public String exerciseId;   // "SQUAT", "PUSHUP", "PLANK"
        public String exerciseName; // "Приседания"
        public String icon;         // "🏋"
        public int    reps;         // повторений
        public long   timestamp;    // System.currentTimeMillis()

        public WorkoutRecord(String exerciseId, String exerciseName,
                             String icon, int reps, long timestamp) {
            this.exerciseId   = exerciseId;
            this.exerciseName = exerciseName;
            this.icon         = icon;
            this.reps         = reps;
            this.timestamp    = timestamp;
        }
    }

    public static class WeightRecord {
        public float weight;
        public long  timestamp;

        public WeightRecord(float weight, long timestamp) {
            this.weight    = weight;
            this.timestamp = timestamp;
        }
    }

    // ═════════════════════════════════════════════════
    //  onCreate
    // ═════════════════════════════════════════════════
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_profile);

        prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        gson  = new Gson();

        initViews();
        loadData();
        setupChart();
        updateStats();
        updateHistoryList();
    }

    // ═════════════════════════════════════════════════
    //  Инициализация UI
    // ═════════════════════════════════════════════════
    private void initViews() {
        findViewById(R.id.btnBack).setOnClickListener(v -> finish());

        etHeight            = findViewById(R.id.etHeight);
        etWeight            = findViewById(R.id.etWeight);
        btnCalculateBmi     = findViewById(R.id.btnCalculateBmi);
        layoutBmiResult     = findViewById(R.id.layoutBmiResult);
        tvBmiValue          = findViewById(R.id.tvBmiValue);
        tvBmiResultCategory = findViewById(R.id.tvBmiResultCategory);
        tvBmiCategory       = findViewById(R.id.tvBmiCategory);
        tvNoWeightData      = findViewById(R.id.tvNoWeightData);
        weightChart         = findViewById(R.id.weightChart);
        tvTotalWorkouts     = findViewById(R.id.tvTotalWorkouts);
        tvCurrentStreak     = findViewById(R.id.tvCurrentStreak);
        tvWeekWorkouts      = findViewById(R.id.tvWeekWorkouts);
        tvNoHistory         = findViewById(R.id.tvNoHistory);
        rvHistory           = findViewById(R.id.rvHistory);
        btnClearHistory     = findViewById(R.id.btnClearHistory);

        // Кнопка рассчитать ИМТ
        btnCalculateBmi.setOnClickListener(v -> calculateBmi());

        // Кнопка очистить историю
        btnClearHistory.setOnClickListener(v -> confirmClearHistory());

        // Настраиваем RecyclerView
        rvHistory.setLayoutManager(new LinearLayoutManager(this));
    }

    // ═════════════════════════════════════════════════
    //  Загрузка данных из SharedPreferences
    // ═════════════════════════════════════════════════
    private void loadData() {

        // Рост и вес
        float savedHeight = prefs.getFloat(KEY_HEIGHT, 0f);
        float savedWeight = prefs.getFloat(KEY_WEIGHT, 0f);

        if (savedHeight > 0) etHeight.setText(String.valueOf((int) savedHeight));
        if (savedWeight > 0) etWeight.setText(String.format(Locale.US,
                "%.1f", savedWeight));

        // Если есть сохранённые данные — показываем ИМТ
        if (savedHeight > 0 && savedWeight > 0) {
            showBmiResult(savedHeight, savedWeight);
        }

        // История веса
        String weightJson = prefs.getString(KEY_WEIGHT_LOG, "[]");
        Type   weightType = new TypeToken<List<WeightRecord>>(){}.getType();
        weightLog = gson.fromJson(weightJson, weightType);
        if (weightLog == null) weightLog = new ArrayList<>();

        // История тренировок
        String workoutsJson = prefs.getString(KEY_WORKOUTS, "[]");
        Type   workoutsType = new TypeToken<List<WorkoutRecord>>(){}.getType();
        workoutHistory = gson.fromJson(workoutsJson, workoutsType);
        if (workoutHistory == null) workoutHistory = new ArrayList<>();
    }

    // ═════════════════════════════════════════════════
    //  Расчёт ИМТ
    //
    //  ИМТ = вес(кг) / рост(м)²
    //
    //  < 16.0  → Выраженный дефицит
    //  16–18.5 → Недостаточный вес
    //  18.5–25 → Норма ✅
    //  25–30   → Избыточный вес
    //  30–35   → Ожирение I
    //  35–40   → Ожирение II
    //  > 40    → Ожирение III
    // ═════════════════════════════════════════════════
    private void calculateBmi() {
        String heightStr = etHeight.getText() != null
                ? etHeight.getText().toString().trim() : "";
        String weightStr = etWeight.getText() != null
                ? etWeight.getText().toString().trim() : "";

        if (heightStr.isEmpty() || weightStr.isEmpty()) {
            Toast.makeText(this,
                    "Введите рост и вес", Toast.LENGTH_SHORT).show();
            return;
        }

        float height, weight;
        try {
            height = Float.parseFloat(heightStr);
            weight = Float.parseFloat(weightStr);
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

        // Сохраняем
        prefs.edit()
                .putFloat(KEY_HEIGHT, height)
                .putFloat(KEY_WEIGHT, weight)
                .apply();

        // Добавляем точку в лог веса
        addWeightRecord(weight);

        // Показываем результат
        showBmiResult(height, weight);

        // Обновляем график
        setupChart();

        Toast.makeText(this, "Данные сохранены", Toast.LENGTH_SHORT).show();
    }

    private void showBmiResult(float height, float weight) {
        float heightM = height / 100f;
        float bmi     = weight / (heightM * heightM);

        // Категория и цвет
        String category;
        int    color;

        if (bmi < 16.0f) {
            category = "Выраженный\nдефицит";
            color    = Color.parseColor("#FF4444");
        } else if (bmi < 18.5f) {
            category = "Недостаточный\nвес";
            color    = Color.parseColor("#FF8800");
        } else if (bmi < 25.0f) {
            category = "Норма ✓";
            color    = Color.parseColor("#00FF88");
        } else if (bmi < 30.0f) {
            category = "Избыточный\nвес";
            color    = Color.parseColor("#FFFF00");
        } else if (bmi < 35.0f) {
            category = "Ожирение I";
            color    = Color.parseColor("#FF8800");
        } else if (bmi < 40.0f) {
            category = "Ожирение II";
            color    = Color.parseColor("#FF4444");
        } else {
            category = "Ожирение III";
            color    = Color.parseColor("#CC0000");
        }

        // Обновляем UI
        tvBmiValue.setText(String.format(Locale.US, "%.1f", bmi));
        tvBmiResultCategory.setText(category);
        tvBmiResultCategory.setTextColor(color);
        tvBmiCategory.setText(category.replace("\n", " "));
        tvBmiCategory.setTextColor(color);

        layoutBmiResult.setVisibility(View.VISIBLE);
    }

    // ═════════════════════════════════════════════════
    //  Добавление записи веса в лог
    // ═════════════════════════════════════════════════
    private void addWeightRecord(float weight) {
        long now = System.currentTimeMillis();

        // Проверяем: если сегодня уже есть запись — обновляем
        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(now);
        int todayDay  = cal.get(Calendar.DAY_OF_YEAR);
        int todayYear = cal.get(Calendar.YEAR);

        for (int i = 0; i < weightLog.size(); i++) {
            cal.setTimeInMillis(weightLog.get(i).timestamp);
            if (cal.get(Calendar.DAY_OF_YEAR) == todayDay
                    && cal.get(Calendar.YEAR) == todayYear) {
                // Обновляем существующую запись за сегодня
                weightLog.get(i).weight = weight;
                saveWeightLog();
                return;
            }
        }

        // Новая запись
        weightLog.add(new WeightRecord(weight, now));

        // Держим максимум 30 записей
        if (weightLog.size() > 30) {
            weightLog.remove(0);
        }

        saveWeightLog();
    }

    private void saveWeightLog() {
        prefs.edit()
                .putString(KEY_WEIGHT_LOG, gson.toJson(weightLog))
                .apply();
    }

    // ═════════════════════════════════════════════════
    //  График динамики веса
    // ═════════════════════════════════════════════════
    private void setupChart() {
        if (weightLog.size() < 2) {
            tvNoWeightData.setVisibility(View.VISIBLE);
            weightChart.setVisibility(View.GONE);
            return;
        }

        tvNoWeightData.setVisibility(View.GONE);
        weightChart.setVisibility(View.VISIBLE);

        // Сортируем по времени
        List<WeightRecord> sorted = new ArrayList<>(weightLog);
        Collections.sort(sorted, (a, b) ->
                Long.compare(a.timestamp, b.timestamp));

        // Формируем точки графика
        List<Entry> entries = new ArrayList<>();
        for (int i = 0; i < sorted.size(); i++) {
            entries.add(new Entry(i, sorted.get(i).weight));
        }

        // Датасет
        LineDataSet dataSet = new LineDataSet(entries, "Вес (кг)");
        dataSet.setColor(Color.parseColor("#E94560"));
        dataSet.setCircleColor(Color.parseColor("#E94560"));
        dataSet.setLineWidth(2f);
        dataSet.setCircleRadius(4f);
        dataSet.setDrawValues(true);
        dataSet.setValueTextColor(Color.WHITE);
        dataSet.setValueTextSize(10f);
        dataSet.setMode(LineDataSet.Mode.CUBIC_BEZIER);
        dataSet.setDrawFilled(true);
        dataSet.setFillColor(Color.parseColor("#33E94560"));

        // Форматтер оси X — даты
        final List<WeightRecord> finalSorted = sorted;
        final SimpleDateFormat   sdf = new SimpleDateFormat("dd.MM", Locale.US);

        // Настройка графика
        weightChart.setData(new LineData(dataSet));
        weightChart.setBackgroundColor(Color.TRANSPARENT);
        weightChart.getDescription().setEnabled(false);
        weightChart.getLegend().setEnabled(false);
        weightChart.setTouchEnabled(false);
        weightChart.setDragEnabled(false);
        weightChart.setScaleEnabled(false);

        // Ось X
        XAxis xAxis = weightChart.getXAxis();
        xAxis.setPosition(XAxis.XAxisPosition.BOTTOM);
        xAxis.setTextColor(Color.parseColor("#AAAAAA"));
        xAxis.setTextSize(10f);
        xAxis.setGridColor(Color.parseColor("#22FFFFFF"));
        xAxis.setAxisLineColor(Color.parseColor("#33FFFFFF"));
        xAxis.setGranularity(1f);
        xAxis.setValueFormatter(new ValueFormatter() {
            @Override
            public String getFormattedValue(float value) {
                int idx = (int) value;
                if (idx < 0 || idx >= finalSorted.size()) return "";
                return sdf.format(new Date(finalSorted.get(idx).timestamp));
            }
        });

        // Ось Y левая
        YAxis yAxisLeft = weightChart.getAxisLeft();
        yAxisLeft.setTextColor(Color.parseColor("#AAAAAA"));
        yAxisLeft.setTextSize(10f);
        yAxisLeft.setGridColor(Color.parseColor("#22FFFFFF"));
        yAxisLeft.setAxisLineColor(Color.parseColor("#33FFFFFF"));

        // Ось Y правая — скрываем
        weightChart.getAxisRight().setEnabled(false);

        weightChart.invalidate();
    }

    // ═════════════════════════════════════════════════
    //  Статистика тренировок
    // ═════════════════════════════════════════════════
    private void updateStats() {
        int total     = workoutHistory.size();
        int streak    = calcStreak();
        int weekCount = calcWeekWorkouts();

        tvTotalWorkouts.setText(String.valueOf(total));
        tvCurrentStreak.setText(String.valueOf(streak));
        tvWeekWorkouts.setText(String.valueOf(weekCount));
    }

    // Подсчёт дней подряд
    private int calcStreak() {
        if (workoutHistory.isEmpty()) return 0;

        // Получаем уникальные дни тренировок
        List<Long> days = new ArrayList<>();
        SimpleDateFormat dayFmt =
                new SimpleDateFormat("yyyyMMdd", Locale.US);

        List<String> uniqueDays = new ArrayList<>();
        for (WorkoutRecord r : workoutHistory) {
            String day = dayFmt.format(new Date(r.timestamp));
            if (!uniqueDays.contains(day)) {
                uniqueDays.add(day);
                days.add(r.timestamp);
            }
        }

        if (days.isEmpty()) return 0;

        // Сортируем по убыванию
        Collections.sort(days, Collections.reverseOrder());

        Calendar cal    = Calendar.getInstance();
        Calendar today  = Calendar.getInstance();
        int streak      = 0;
        long checkTime  = today.getTimeInMillis();

        for (long ts : days) {
            cal.setTimeInMillis(ts);

            Calendar check = Calendar.getInstance();
            check.setTimeInMillis(checkTime);

            // Совпадают ли дни?
            if (cal.get(Calendar.YEAR)        == check.get(Calendar.YEAR)
                    && cal.get(Calendar.DAY_OF_YEAR) == check.get(Calendar.DAY_OF_YEAR)) {
                streak++;
                // Переходим к предыдущему дню
                check.add(Calendar.DAY_OF_YEAR, -1);
                checkTime = check.getTimeInMillis();
            } else {
                break;
            }
        }

        return streak;
    }

    // Тренировок на этой неделе
    private int calcWeekWorkouts() {
        Calendar startOfWeek = Calendar.getInstance();
        startOfWeek.set(Calendar.DAY_OF_WEEK,
                startOfWeek.getFirstDayOfWeek());
        startOfWeek.set(Calendar.HOUR_OF_DAY, 0);
        startOfWeek.set(Calendar.MINUTE, 0);
        startOfWeek.set(Calendar.SECOND, 0);
        startOfWeek.set(Calendar.MILLISECOND, 0);

        long weekStart = startOfWeek.getTimeInMillis();
        int  count     = 0;

        for (WorkoutRecord r : workoutHistory) {
            if (r.timestamp >= weekStart) count++;
        }
        return count;
    }

    // ═════════════════════════════════════════════════
    //  Список истории тренировок
    // ═════════════════════════════════════════════════
    private void updateHistoryList() {
        if (workoutHistory.isEmpty()) {
            tvNoHistory.setVisibility(View.VISIBLE);
            rvHistory.setVisibility(View.GONE);
            return;
        }

        tvNoHistory.setVisibility(View.GONE);
        rvHistory.setVisibility(View.VISIBLE);

        // Показываем в обратном порядке (новые сверху)
        List<WorkoutRecord> reversed = new ArrayList<>(workoutHistory);
        Collections.reverse(reversed);

        // Показываем максимум 20 последних
        if (reversed.size() > 20) {
            reversed = reversed.subList(0, 20);
        }

        adapter = new WorkoutHistoryAdapter(reversed);
        rvHistory.setAdapter(adapter);
    }

    // ═════════════════════════════════════════════════
    //  Диалог подтверждения очистки истории
    // ═════════════════════════════════════════════════
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
                    prefs.edit()
                            .putString(KEY_WORKOUTS, "[]")
                            .apply();
                    updateStats();
                    updateHistoryList();
                    Toast.makeText(this,
                            "История очищена", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Отмена", null)
                .show();
    }

    // ═════════════════════════════════════════════════
    //  Сохранение тренировки (вызывается из ExerciseActivity)
    // ═════════════════════════════════════════════════
    public static void saveWorkout(Context context,
                                   String exerciseId,
                                   String exerciseName,
                                   String icon,
                                   int reps) {
        if (reps <= 0) return; // не сохраняем пустые тренировки

        SharedPreferences prefs =
                context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        Gson gson = new Gson();

        // Загружаем существующую историю
        String json = prefs.getString(KEY_WORKOUTS, "[]");
        Type   type = new TypeToken<List<WorkoutRecord>>(){}.getType();
        List<WorkoutRecord> history = gson.fromJson(json, type);
        if (history == null) history = new ArrayList<>();

        // Добавляем новую запись
        history.add(new WorkoutRecord(
                exerciseId,
                exerciseName,
                icon,
                reps,
                System.currentTimeMillis()
        ));

        // Держим максимум 100 записей
        if (history.size() > 100) {
            history.remove(0);
        }

        prefs.edit()
                .putString(KEY_WORKOUTS, gson.toJson(history))
                .apply();
    }

    // ═════════════════════════════════════════════════
    //  RecyclerView адаптер для истории
    // ═════════════════════════════════════════════════
    private static class WorkoutHistoryAdapter
            extends RecyclerView.Adapter<WorkoutHistoryAdapter.ViewHolder> {

        private final List<WorkoutRecord>  items;
        private final SimpleDateFormat     sdf =
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
        public int getItemCount() { return items.size(); }

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