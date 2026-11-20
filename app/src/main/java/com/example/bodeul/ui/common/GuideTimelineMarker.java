package com.example.bodeul.ui.common;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.TextView;
import androidx.core.content.ContextCompat;
import com.example.bodeul.R;

/** 두 역할에서 단계 번호·완료 체크·연결선이 같은 좌표를 사용하고 글자 확대도 따르게 한다. */
public final class GuideTimelineMarker {
    private GuideTimelineMarker() {}
    public static void bind(View row,int order,boolean active,boolean done,boolean last) {
        Context c = row.getContext();
        TextView number = row.findViewById(R.id.finalGuideTimelineNumber);
        int size = Math.max(DetailViews.dp(c,20),Math.round(number.getTextSize()+DetailViews.dp(c,8)));
        FrameLayout.LayoutParams p = (FrameLayout.LayoutParams)number.getLayoutParams();
        p.width = p.height = size;
        number.setLayoutParams(p);
        GradientDrawable dot = new GradientDrawable();
        dot.setShape(GradientDrawable.OVAL);
        dot.setColor(ContextCompat.getColor(c,done || active ? R.color.final_brand_primary : R.color.final_input_surface));
        number.setBackground(dot);
        number.setText(done ? "" : String.valueOf(order));
        number.setTextColor(ContextCompat.getColor(c,active ? R.color.final_text_on_primary : R.color.final_text_secondary));
        View line = row.findViewById(R.id.finalGuideTimelineLine);
        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams)line.getLayoutParams();
        lp.setMarginStart(size/2 - DetailViews.dp(c,1));
        line.setLayoutParams(lp);
        line.setVisibility(last ? View.INVISIBLE : View.VISIBLE);
        line.setBackgroundColor(ContextCompat.getColor(c,done ? R.color.final_brand_primary : R.color.final_line));
        View check = row.findViewById(R.id.finalGuideTimelineCheck);
        FrameLayout.LayoutParams cp = (FrameLayout.LayoutParams)check.getLayoutParams();
        cp.setMarginStart((size - cp.width)/2);
        cp.topMargin = DetailViews.dp(c,14)+(size - cp.height)/2;
        check.setLayoutParams(cp);
        check.setVisibility(done ? View.VISIBLE : View.GONE);
    }
}
