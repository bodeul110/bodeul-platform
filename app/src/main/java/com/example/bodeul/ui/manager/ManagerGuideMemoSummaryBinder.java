package com.example.bodeul.ui.manager;

import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.example.bodeul.R;

import java.util.List;

/** 최종 일지 입력과 분리된 읽기 전용 단계 메모 목록을 렌더링한다. */
final class ManagerGuideMemoSummaryBinder {
    private final LayoutInflater inflater;
    private final View group;
    private final LinearLayout container;

    ManagerGuideMemoSummaryBinder(LayoutInflater inflater, View root) {
        this.inflater = inflater;
        group = root.findViewById(R.id.groupGuideMemoSummary);
        container = root.findViewById(R.id.guideMemoSummaryContainer);
    }

    void bind(List<ManagerGuideMemoItem> items, boolean visible) {
        group.setVisibility(visible ? View.VISIBLE : View.GONE);
        container.removeAllViews();
        if (!visible) {
            return;
        }
        for (ManagerGuideMemoItem item : items) {
            View itemView = inflater.inflate(
                    R.layout.item_manager_guide_memo_summary,
                    container,
                    false);
            TextView title = itemView.findViewById(R.id.textGuideMemoItemTitle);
            TextView body = itemView.findViewById(R.id.textGuideMemoItemBody);
            title.setText(item.getTitle());
            body.setText(item.getBody());
            body.setTextColor(itemView.getContext().getColor(item.isEmpty()
                    ? R.color.figma_mvp_text_hint
                    : R.color.bodeul_text_primary));
            container.addView(itemView);
        }
    }
}
