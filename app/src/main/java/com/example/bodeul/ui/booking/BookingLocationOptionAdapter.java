package com.example.bodeul.ui.booking;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.core.content.res.ResourcesCompat;
import androidx.core.view.AccessibilityDelegateCompat;
import androidx.core.view.ViewCompat;
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat;
import com.example.bodeul.R;
import com.example.bodeul.domain.model.BookingMeetingPointOption;
import java.util.ArrayList;
import java.util.List;

/** 기존 만남 지점을 완성본의 평면 목록과 선택 상태로 표시한다. */
public final class BookingLocationOptionAdapter extends BaseAdapter {
    private final LayoutInflater inflater;
    private final List<BookingMeetingPointOption> options = new ArrayList<>();
    private String selectedPointId = "";

    public BookingLocationOptionAdapter(Context context) { inflater = LayoutInflater.from(context); }
    public void submitList(List<BookingMeetingPointOption> items) {
        options.clear();
        if (items != null) options.addAll(items);
        notifyDataSetChanged();
    }
    public void setSelectedPointId(String id) { selectedPointId = id == null ? "" : id; notifyDataSetChanged(); }
    @Override public int getCount() { return options.size(); }
    @Override public BookingMeetingPointOption getItem(int position) { return options.get(position); }
    @Override public long getItemId(int position) { return position; }

    @Override public View getView(int position, View view, ViewGroup parent) {
        if (view == null) {
            view = inflater.inflate(R.layout.item_booking_location_option, parent, false);
            ViewCompat.setAccessibilityDelegate(view, new AccessibilityDelegateCompat() {
                @Override public void onInitializeAccessibilityNodeInfo(@NonNull View host, @NonNull AccessibilityNodeInfoCompat info) {
                    super.onInitializeAccessibilityNodeInfo(host, info);
                    info.setClassName("android.widget.RadioButton");
                    info.setCheckable(true);
                    info.setChecked(host.isSelected());
                }
            });
        }
        BookingMeetingPointOption option = getItem(position);
        boolean selected = option.getId().equals(selectedPointId);
        view.setTag(option.getId());
        view.setSelected(selected);
        view.setContentDescription(selected ? parent.getContext().getString(R.string.final_location_selected, option.getMeetingPlace())
                : option.getMeetingPlace());
        TextView title = view.findViewById(R.id.textBookingLocationOptionTitle);
        title.setText(option.getTitle());
        title.setTextColor(ContextCompat.getColor(parent.getContext(), R.color.final_text_primary));
        title.setTypeface(ResourcesCompat.getFont(parent.getContext(), selected ? R.font.pretendard_bold : R.font.pretendard_semibold));
        ((TextView) view.findViewById(R.id.textBookingLocationOptionBody)).setText(option.getDescription());
        view.findViewById(R.id.checkBookingLocationOption).setVisibility(selected ? View.VISIBLE : View.INVISIBLE);
        return view;
    }
}
