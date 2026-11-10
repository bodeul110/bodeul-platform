package com.example.bodeul.ui.booking;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;

import com.example.bodeul.R;
import com.example.bodeul.domain.model.BookingHospitalOption;

import java.util.ArrayList;
import java.util.List;

/**
 * 실제 목록 데이터만 표시하고 병원 선택과 지도 실행을 분리한다.
 */
public final class BookingHospitalOptionAdapter extends BaseAdapter {
    public interface Action { void run(BookingHospitalOption option); }
    private final LayoutInflater layoutInflater;
    private final List<BookingHospitalOption> options = new ArrayList<>();
    private final Action select;
    private final Action map;

    public BookingHospitalOptionAdapter(Context context, Action select, Action map) {
        this.layoutInflater = LayoutInflater.from(context);
        this.select = select;
        this.map = map;
    }

    public void submitList(List<BookingHospitalOption> items) {
        options.clear();
        if (items != null) {
            options.addAll(items);
        }
        notifyDataSetChanged();
    }

    @Override
    public int getCount() {
        return options.size();
    }

    @Override
    public BookingHospitalOption getItem(int position) {
        return options.get(position);
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        ViewHolder viewHolder;
        if (convertView == null) {
            convertView = layoutInflater.inflate(R.layout.item_booking_hospital_option, parent, false);
            viewHolder = new ViewHolder(
                    convertView.findViewById(R.id.buttonBookingHospitalOption),
                    convertView.findViewById(R.id.buttonBookingHospitalMap),
                    convertView.findViewById(R.id.textBookingHospitalOptionName),
                    convertView.findViewById(R.id.textBookingHospitalOptionDepartments)
            );
            convertView.setTag(viewHolder);
        } else {
            viewHolder = (ViewHolder) convertView.getTag();
        }

        BookingHospitalOption option = getItem(position);
        if (option.getDepartmentCount() == 0) {
            viewHolder.textDepartments.setText(R.string.final_hospital_no_departments);
        } else {
            viewHolder.textDepartments.setText(parent.getContext().getString(
                    R.string.booking_hospital_selector_count_badge, option.getDepartmentCount()));
        }
        viewHolder.textName.setText(option.getHospitalName());
        viewHolder.select.setOnClickListener(v -> select.run(option));
        convertView.findViewById(R.id.imageBookingHospitalOption).setOnClickListener(v -> select.run(option));
        viewHolder.map.setOnClickListener(v -> map.run(option));
        viewHolder.map.setContentDescription(parent.getContext().getString(
                R.string.final_hospital_map_description, option.getHospitalName()));
        return convertView;
    }

    private static final class ViewHolder {
        private final View select;
        private final View map;
        private final TextView textName;
        private final TextView textDepartments;

        private ViewHolder(
                View select,
                View map,
                TextView textName,
                TextView textDepartments
        ) {
            this.select = select;
            this.map = map;
            this.textName = textName;
            this.textDepartments = textDepartments;
        }
    }
}
