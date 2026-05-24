package ru.sv.personaltrainer;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

import ru.sv.personaltrainer.databinding.ActivityExerciseListBinding;
import ru.sv.personaltrainer.databinding.ItemExerciseBinding;
import ru.sv.personaltrainer.model.ExerciseInfo;
import ru.sv.personaltrainer.viewmodel.ExerciseListViewModel;

public class ExerciseListActivity extends AppCompatActivity {

    private ActivityExerciseListBinding binding;
    private ExerciseListViewModel viewModel;
    private ExerciseAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);

        binding = ActivityExerciseListBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        ViewCompat.setOnApplyWindowInsetsListener(binding.rootList, (v, insets) -> {
            Insets bars = insets.getInsets(
                    WindowInsetsCompat.Type.systemBars() |
                            WindowInsetsCompat.Type.displayCutout());
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return WindowInsetsCompat.CONSUMED;
        });

        viewModel = new ViewModelProvider(this).get(ExerciseListViewModel.class);

        setupRecycler();
        setupSearch();
        setupProfile();

        viewModel.getFilteredExercises().observe(this, list -> adapter.submitList(list));
    }

    private void setupRecycler() {
        binding.recyclerExercises.setLayoutManager(new LinearLayoutManager(this));
        adapter = new ExerciseAdapter(this::onExerciseClick);
        binding.recyclerExercises.setAdapter(adapter);
    }

    private void setupSearch() {
        binding.etSearch.addTextChangedListener(new android.text.TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int st, int c, int a) {}
            @Override public void afterTextChanged(android.text.Editable s) {}
            @Override
            public void onTextChanged(CharSequence s, int start, int b, int count) {
                viewModel.filter(s.toString());
            }
        });
    }

    private void setupProfile() {
        binding.btnProfile.setOnClickListener(v -> {
            startActivity(new Intent(this, ProfileActivity.class));
        });
    }

    private void onExerciseClick(ExerciseInfo exercise) {
        Intent intent = new Intent(this, ExerciseDetailActivity.class);
        intent.putExtra("EXERCISE_ID", exercise.getId());
        startActivity(intent);
    }

    static class ExerciseAdapter extends RecyclerView.Adapter<ExerciseAdapter.ViewHolder> {
        interface OnClickListener {
            void onClick(ExerciseInfo exercise);
        }

        private List<ExerciseInfo> items = new ArrayList<>();
        private final OnClickListener listener;

        ExerciseAdapter(OnClickListener listener) {
            this.listener = listener;
        }

        void submitList(List<ExerciseInfo> list) {
            this.items = list != null ? list : new ArrayList<>();
            notifyDataSetChanged();
        }

        @Override
        public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            ItemExerciseBinding b = ItemExerciseBinding.inflate(
                    LayoutInflater.from(parent.getContext()), parent, false);
            return new ViewHolder(b);
        }

        @Override
        public void onBindViewHolder(ViewHolder holder, int position) {
            ExerciseInfo ex = items.get(position);
            holder.b.tvEmoji.setText(ex.getEmoji());
            holder.b.tvTitle.setText(ex.getTitle());
            holder.b.tvMuscles.setText(ex.getMuscleGroup());
            holder.b.tvDifficulty.setText("● " + ex.getDifficulty());
            holder.b.cardRoot.setBackgroundColor(ex.getColor());
            holder.b.getRoot().setOnClickListener(v -> listener.onClick(ex));
        }

        @Override
        public int getItemCount() {
            return items.size();
        }

        static class ViewHolder extends RecyclerView.ViewHolder {
            ItemExerciseBinding b;
            ViewHolder(ItemExerciseBinding b) {
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