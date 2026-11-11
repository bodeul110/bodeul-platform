package com.example.bodeul.ui.booking;

import android.content.Context;
import android.view.Gravity;
import android.view.View;
import android.widget.GridLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.core.content.res.ResourcesCompat;
import androidx.core.view.AccessibilityDelegateCompat;
import androidx.core.view.ViewCompat;
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat;
import com.example.bodeul.R;
import java.util.Calendar;

/** 달력의 실제 요일·월 경계와 선택 가능 날짜를 화면에 연결한다. */
final class BookingScheduleCalendarBinder {
    interface Listener { void onSelect(Calendar date); }
    private final Context context;
    private final GridLayout grid;

    BookingScheduleCalendarBinder(GridLayout grid) {
        this.grid = grid;
        context = grid.getContext();
    }

    void render(Calendar month, Calendar selected, long nowMillis, Listener listener) {
        grid.removeAllViews();
        String[] weekdays = context.getResources().getStringArray(R.array.booking_appointment_schedule_weekdays);
        for (int i = 0; i < 7; i++) {
            TextView header = text(weekdays[i], 13, R.color.final_text_secondary);
            grid.addView(header, params(i, 0, Math.max(22, Math.round(22 * fontScale()))));
        }
        Calendar start = (Calendar) month.clone();
        start.set(Calendar.DAY_OF_MONTH, 1);
        int offset = start.get(Calendar.DAY_OF_WEEK) - Calendar.SUNDAY;
        int days = start.getActualMaximum(Calendar.DAY_OF_MONTH);
        int cells = ((offset + days + 6) / 7) * 7;
        Calendar today = BookingAppointmentDateTime.newCalendar();
        today.setTimeInMillis(nowMillis);
        for (int i = 0; i < cells; i++) {
            int day = i - offset + 1;
            if (day < 1 || day > days) {
                View blank = new View(context);
                blank.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
                grid.addView(blank, params(i % 7, i / 7 + 1, cellHeight()));
                continue;
            }
            Calendar date = (Calendar) start.clone();
            date.set(Calendar.DAY_OF_MONTH, day);
            boolean checked = sameDay(date, selected);
            boolean isToday = sameDay(date, today);
            boolean enabled = !BookingAppointmentDateTime.isBeforeToday(date, nowMillis);
            LinearLayout cell = new LinearLayout(context);
            cell.setOrientation(LinearLayout.VERTICAL);
            cell.setGravity(Gravity.TOP | Gravity.CENTER_HORIZONTAL);
            cell.setPadding(0, dp(5), 0, 0);
            cell.setTag(BookingAppointmentDateTime.format(date, 0, 0));
            cell.setSelected(checked);
            cell.setEnabled(enabled);
            cell.setFocusable(enabled);
            String description = context.getString(R.string.booking_appointment_schedule_date_description,
                    date.get(Calendar.YEAR), date.get(Calendar.MONTH) + 1, day);
            cell.setContentDescription(checked ? context.getString(
                    R.string.booking_appointment_schedule_date_selected_description, description) : description);
            ViewCompat.setAccessibilityDelegate(cell, new AccessibilityDelegateCompat() {
                @Override public void onInitializeAccessibilityNodeInfo(@NonNull View host, @NonNull AccessibilityNodeInfoCompat info) {
                    super.onInitializeAccessibilityNodeInfo(host, info);
                    info.setClassName("android.widget.RadioButton");
                    info.setCheckable(true);
                    info.setChecked(host.isSelected());
                }
            });
            TextView number = text(String.valueOf(day), 14, checked ? R.color.final_surface
                    : enabled ? R.color.final_text_primary : R.color.final_disabled);
            number.setTypeface(ResourcesCompat.getFont(context, R.font.pretendard_semibold));
            int circle = Math.max(30, Math.round(22 * fontScale()));
            if (checked) number.setBackgroundResource(R.drawable.bodeul_final_calendar_selected);
            else if (isToday) number.setBackgroundResource(R.drawable.bodeul_final_calendar_today);
            number.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            cell.addView(number, new LinearLayout.LayoutParams(dp(circle), dp(circle)));
            if (isToday) {
                TextView label = text(context.getString(R.string.final_schedule_today), 10, R.color.final_text_secondary);
                label.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
                cell.addView(label, new LinearLayout.LayoutParams(-1, -2));
            }
            if (enabled) cell.setOnClickListener(v -> listener.onSelect(date));
            grid.addView(cell, params(i % 7, i / 7 + 1, cellHeight()));
        }
    }

    static boolean sameDay(Calendar first, Calendar second) {
        return first.get(Calendar.YEAR) == second.get(Calendar.YEAR)
                && first.get(Calendar.DAY_OF_YEAR) == second.get(Calendar.DAY_OF_YEAR);
    }

    private TextView text(String value, int sp, int color) {
        TextView text = new TextView(context);
        text.setText(value);
        text.setTextSize(sp);
        text.setGravity(Gravity.CENTER);
        text.setIncludeFontPadding(false);
        text.setTypeface(ResourcesCompat.getFont(context, R.font.pretendard_medium));
        text.setTextColor(ContextCompat.getColor(context, color));
        return text;
    }

    private GridLayout.LayoutParams params(int column, int row, int height) {
        GridLayout.LayoutParams params = new GridLayout.LayoutParams(GridLayout.spec(row), GridLayout.spec(column, 1f));
        params.width = 0;
        params.height = dp(height);
        return params;
    }
    private int cellHeight() { return Math.max(52, Math.round(36 * fontScale())); }
    private float fontScale() { return context.getResources().getConfiguration().fontScale; }
    private int dp(int value) { return Math.round(value * context.getResources().getDisplayMetrics().density); }
}
