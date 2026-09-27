package com.example.bodeul.debug;

import android.content.Context;
import android.content.Intent;
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

import java.util.Arrays;
import java.util.List;

/** 이번 피그마 구현 범위를 서버 변경 없이 직접 여는 debug 선택기다. */
public final class FigmaScreenPreviewSelectorActivity extends AppCompatActivity {
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_figma_screen_preview_selector);
        applySystemBarInsets();

        List<PreviewDestination> destinations = Arrays.asList(
                new PreviewDestination(
                        "매니저 7단계 · 진료 요약",
                        "CONSULTATION_SUMMARY · Figma 8:1088",
                        destinationIntent(this, 0)
                ),
                new PreviewDestination(
                        "매니저 9단계 · 약국 이동",
                        "PHARMACY_ROUTE · Figma 8:1254",
                        destinationIntent(this, 1)
                ),
                new PreviewDestination(
                        "매니저 12단계 · 동행 종료",
                        "CARE_COMPLETION · Figma 8:2614",
                        destinationIntent(this, 2)
                ),
                new PreviewDestination(
                        "매니저 13단계 · 매니저 일지",
                        "MANAGER_JOURNAL · Figma 8:1911",
                        destinationIntent(this, 3)
                ),
                new PreviewDestination(
                        "환자 예약 메인",
                        "단계형 예약 화면 · Figma 8:305",
                        destinationIntent(this, 4)
                ),
                new PreviewDestination(
                        "보호자 최종 리포트",
                        "완료된 로컬 동행 데이터 · Figma 8:1464",
                        destinationIntent(this, 5)
                ),
                new PreviewDestination(
                        "매니저 가이드 전체 1~13단계",
                        "기존 전체 단계 선택기 열기",
                        destinationIntent(this, 6)
                )
        );

        ListView listView = findViewById(R.id.listFigmaScreenPreviews);
        listView.setAdapter(new PreviewAdapter(destinations));
        listView.setOnItemClickListener((parent, view, position, id) ->
                startActivity(destinations.get(position).intent));
    }

    static Intent destinationIntent(Context context, int position) {
        switch (position) {
            case 0:
                return ManagerGuidePreviewActivity.createIntent(
                        context, "CONSULTATION_SUMMARY");
            case 1:
                return ManagerGuidePreviewActivity.createIntent(context, "PHARMACY_ROUTE");
            case 2:
                return ManagerGuidePreviewActivity.createIntent(context, "CARE_COMPLETION");
            case 3:
                return ManagerGuidePreviewActivity.createIntent(context, "MANAGER_JOURNAL");
            case 4:
                return BookingFigmaPreviewActivity.createIntent(context);
            case 5:
                return GuardianReportFigmaPreviewActivity.createIntent(context);
            case 6:
                return new Intent(context, ManagerGuidePreviewSelectorActivity.class);
            default:
                throw new IllegalArgumentException("Unknown preview position: " + position);
        }
    }

    private void applySystemBarInsets() {
        View root = findViewById(R.id.figmaScreenPreviewSelectorRoot);
        int start = root.getPaddingStart();
        int top = root.getPaddingTop();
        int end = root.getPaddingEnd();
        int bottom = root.getPaddingBottom();
        ViewCompat.setOnApplyWindowInsetsListener(root, (view, windowInsets) -> {
            Insets systemBars = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars());
            view.setPaddingRelative(
                    start + systemBars.left,
                    top + systemBars.top,
                    end + systemBars.right,
                    bottom + systemBars.bottom
            );
            return windowInsets;
        });
        ViewCompat.requestApplyInsets(root);
    }

    private static final class PreviewDestination {
        final String title;
        final String description;
        final Intent intent;

        PreviewDestination(String title, String description, Intent intent) {
            this.title = title;
            this.description = description;
            this.intent = intent;
        }

        @NonNull
        @Override
        public String toString() {
            return title;
        }
    }

    private final class PreviewAdapter extends ArrayAdapter<PreviewDestination> {
        PreviewAdapter(List<PreviewDestination> destinations) {
            super(
                    FigmaScreenPreviewSelectorActivity.this,
                    R.layout.item_manager_guide_preview_step,
                    destinations
            );
        }

        @NonNull
        @Override
        public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
            View row = convertView;
            if (row == null) {
                row = LayoutInflater.from(getContext()).inflate(
                        R.layout.item_manager_guide_preview_step,
                        parent,
                        false
                );
            }
            PreviewDestination destination = getItem(position);
            if (destination == null) {
                return row;
            }
            TextView title = row.findViewById(R.id.textManagerGuidePreviewStepTitle);
            TextView description = row.findViewById(R.id.textManagerGuidePreviewStepDescription);
            title.setText(destination.title);
            description.setText(destination.description);
            return row;
        }
    }
}
