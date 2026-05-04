package ru.sv.personaltrainer;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import ru.sv.personaltrainer.exercises.ExerciseRegistry;
import ru.sv.personaltrainer.model.ExerciseInfo;

import java.util.ArrayList;
import java.util.List;

public class ExerciseListActivity extends AppCompatActivity {

    private RecyclerView recycler;
    private ExerciseAdapter adapter;
    private List<ExerciseInfo> allExercises;
    private List<ExerciseInfo> filteredExercises;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_exercise_list);

        allExercises      = ExerciseRegistry.getAll();
        filteredExercises = new ArrayList<>(allExercises);

        setupRecycler();
        setupSearch();
        setupProfile();
    }

    private void setupRecycler() {
        recycler = findViewById(R.id.recyclerExercises);
        recycler.setLayoutManager(new LinearLayoutManager(this));
        adapter = new ExerciseAdapter(filteredExercises, this::onExerciseClick);
        recycler.setAdapter(adapter);
    }

    private void setupSearch() {
        EditText etSearch = findViewById(R.id.etSearch);
        etSearch.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int st, int c, int a) {}
            @Override public void afterTextChanged(Editable s) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int b, int count) {
                filterExercises(s.toString());
            }
        });
    }

    private void setupProfile() {
        CardView btnProfile = findViewById(R.id.btnProfile);
        btnProfile.setOnClickListener(v -> {
            Intent intent = new Intent(this, ProfileActivity.class);
            startActivity(intent);
        });
    }

    private void filterExercises(String query) {
        filteredExercises.clear();
        if (query.isEmpty()) {
            filteredExercises.addAll(allExercises);
        } else {
            String lower = query.toLowerCase();
            for (ExerciseInfo ex : allExercises) {
                if (ex.getTitle().toLowerCase().contains(lower)
                        || ex.getMuscleGroup().toLowerCase().contains(lower)) {
                    filteredExercises.add(ex);
                }
            }
        }
        adapter.notifyDataSetChanged();
    }

    private void onExerciseClick(ExerciseInfo exercise) {
        Intent intent = new Intent(this, ExerciseActivity.class);
        intent.putExtra("EXERCISE_ID", exercise.getId());
        intent.putExtra("EXERCISE_NAME", exercise.getTitle());
        startActivity(intent);
    }

    static class ExerciseAdapter
            extends RecyclerView.Adapter<ExerciseAdapter.ViewHolder> {

        interface OnClickListener {
            void onClick(ExerciseInfo exercise);
        }

        private final List<ExerciseInfo> items;
        private final OnClickListener    listener;

        ExerciseAdapter(List<ExerciseInfo> items, OnClickListener listener) {
            this.items    = items;
            this.listener = listener;
        }

        @Override
        public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_exercise, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(ViewHolder holder, int position) {
            ExerciseInfo ex = items.get(position);

            holder.tvEmoji.setText(ex.getEmoji());
            holder.tvTitle.setText(ex.getTitle());
            holder.tvMuscles.setText(ex.getMuscleGroup());
            holder.tvDifficulty.setText("● " + ex.getDifficulty());
            holder.cardRoot.setBackgroundColor(ex.getColor());
            holder.itemView.setOnClickListener(v -> listener.onClick(ex));
        }

        @Override
        public int getItemCount() { return items.size(); }

        static class ViewHolder extends RecyclerView.ViewHolder {
            TextView   tvEmoji, tvTitle, tvMuscles, tvDifficulty;
            LinearLayout cardRoot;

            ViewHolder(View view) {
                super(view);
                tvEmoji      = view.findViewById(R.id.tvEmoji);
                tvTitle      = view.findViewById(R.id.tvTitle);
                tvMuscles    = view.findViewById(R.id.tvMuscles);
                tvDifficulty = view.findViewById(R.id.tvDifficulty);
                cardRoot     = view.findViewById(R.id.cardRoot);
            }
        }
    }
}