package com.example.bodeul.debug;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.example.bodeul.R;
import com.example.bodeul.domain.model.GuideStep;

import java.util.List;

/** debug 사용자가 원하는 가이드 단계를 고르는 화면이다. */
public final class ManagerGuidePreviewSelectorActivity extends AppCompatActivity {
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_manager_guide_preview_selector);
        applySystemBarInsets();

        List<GuideStep> steps = ManagerGuidePreviewCatalog.steps();
        ListView listView = findViewById(R.id.listManagerGuidePreviewSteps);
        listView.setAdapter(new StepAdapter(steps));
        listView.setOnItemClickListener((parent, view, position, id) -> {
            GuideStep step = steps.get(position);
            startActivity(ManagerGuidePreviewActivity.createIntent(this, step.getCode()));
        });
    }

    private void applySystemBarInsets() {
        View root = findViewById(R.id.managerGuidePreviewSelectorRoot);
        int start = root.getPaddingStart();
        int top = root.getPaddingTop();
        int end = root.getPaddingEnd();
        int bottom = root.getPaddingBottom();
        ViewCompat.setOnApplyWindowInsetsListener(root, (view, windowInsets) -> {
            Insets systemBars = windowInsets.getInsets(
                    WindowInsetsCompat.Type.systemBars());
            view.setPaddingRelative(
                    start + systemBars.left,
                    top + systemBars.top,
                    end + systemBars.right,
                    bottom + systemBars.bottom);
            return windowInsets;
        });
        ViewCompat.requestApplyInsets(root);
    }

    private final class StepAdapter extends ArrayAdapter<GuideStep> {
        StepAdapter(List<GuideStep> steps) {
            super(
                    ManagerGuidePreviewSelectorActivity.this,
                    R.layout.item_manager_guide_preview_step,
                    steps);
        }

        @NonNull
        @Override
        public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
            View row = convertView;
            if (row == null) {
                row = LayoutInflater.from(getContext()).inflate(
                        R.layout.item_manager_guide_preview_step,
                        parent,
                        false);
            }
            GuideStep step = getItem(position);
            if (step == null) {
                return row;
            }
            TextView title = row.findViewById(R.id.textManagerGuidePreviewStepTitle);
            TextView description = row.findViewById(
                    R.id.textManagerGuidePreviewStepDescription);
            title.setText(getString(
                    R.string.debug_manager_guide_preview_step_label,
                    step.getOrder(),
                    step.getTitle()));
            description.setText(getString(
                    R.string.debug_manager_guide_preview_step_description,
                    step.getCode(),
                    step.getDescription()));
            return row;
        }
    }
}
