package com.example.bodeul.ui.common;

import android.content.Context;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import com.example.bodeul.R;

/** 동적 목록과 상세 화면에서 쓰는 제목, 빈 상태와 선택 필터를 같은 크기·텍스트 스타일로 표시한다. */
public final class DetailViews {
    private DetailViews() {}
    public static int dp(Context c, int value) {
        return Math.round(c.getResources().getDisplayMetrics().density * value);
    }
    public static TextView text(LinearLayout parent, CharSequence value, int style) {
        TextView v = new androidx.appcompat.widget.AppCompatTextView(parent.getContext());
        v.setTextAppearance(parent.getContext(), style);
        v.setText(value);
        parent.addView(v, -1, -2);
        return v;
    }
    public static void muted(TextView view) {
        view.setTextColor(androidx.core.content.ContextCompat.getColor(view.getContext(), R.color.final_text_secondary));
    }
    public static void space(LinearLayout p, int height) { p.addView(new View(p.getContext()), 1, dp(p.getContext(), height)); }
    public static void line(LinearLayout p) {
        View v = new View(p.getContext());
        v.setBackgroundColor(androidx.core.content.ContextCompat.getColor(p.getContext(), R.color.final_line));
        p.addView(v, -1, dp(p.getContext(), 1));
    }
    public static void image(LinearLayout p, int resource, int size) {
        ImageView v = new ImageView(p.getContext());
        v.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        v.setImageResource(resource);
        p.addView(v, dp(p.getContext(), size), dp(p.getContext(), size));
    }
    public static Button action(LinearLayout p, int label, Runnable click) {
        Button b = (Button) LayoutInflater.from(p.getContext()).inflate(R.layout.item_batch_action, p, false);
        b.setText(label);
        b.setOnClickListener(v -> click.run());
        p.addView(b);
        return b;
    }
    public interface Selection { void select(int index); }
    public static void filters(LinearLayout p, int[] labels, int current, Selection listener) {
        filters(p, labels, current, false, listener);
    }
    public static void filters(LinearLayout p, int[] labels, int current, boolean segmented, Selection listener) {
        Context c = p.getContext();
        HorizontalScrollView scroll = new HorizontalScrollView(c);
        scroll.setHorizontalScrollBarEnabled(false);
        LinearLayout row = new LinearLayout(c);
        if (segmented) {
            scroll.setFillViewport(true);
            row.setBackgroundResource(R.drawable.bg_final_guide_input);
            int pad = dp(c,3);
            row.setPadding(pad,pad,pad,pad);
        }
        scroll.addView(row);
        p.addView(scroll, -1, -2);
        for (int i = 0; i < labels.length; i++) {
            final int index = i;
            TextView tab = (TextView) LayoutInflater.from(c).inflate(R.layout.item_batch_filter, row, false);
            if (segmented) {
                tab.setBackgroundResource(R.drawable.bg_batch_segment);
                // 가로 공간은 균등 배분하고 큰 글자에는 가로 스크롤로 실제 글자 폭을 보장한다.
                tab.setLayoutParams(new LinearLayout.LayoutParams(-2,-2,1));
                tab.setPadding(dp(c,8),dp(c,6),dp(c,8),dp(c,6));
            }
            tab.setText(labels[i]);
            tab.setSelected(i == current);
            tab.setOnClickListener(v -> {
                for (int j = 0; j < row.getChildCount(); j++) row.getChildAt(j).setSelected(j == index);
                listener.select(index);
            });
            row.addView(tab);
            if (i == current) scroll.post(() -> scroll.scrollTo(Math.max(0, tab.getLeft() - dp(c, 8)), 0));
        }
    }
    public static LinearLayout column(LinearLayout p, boolean centered) {
        LinearLayout v = new LinearLayout(p.getContext());
        v.setOrientation(LinearLayout.VERTICAL);
        if (centered) v.setGravity(Gravity.CENTER);
        p.addView(v, -1, -2);
        return v;
    }
}
