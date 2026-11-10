package com.example.bodeul.ui.booking;

import android.content.Context;
import android.content.res.ColorStateList;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;

import com.example.bodeul.R;
import com.google.android.material.button.MaterialButton;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 여러 선택 버튼을 하나의 단일 선택 그룹처럼 다룬다.
 */
public final class BookingOptionGroupBinder<T> {
    private final Context context;
    private final LinkedHashMap<T, MaterialButton> optionButtons;
    private T selectedOption;
    private Runnable selectionChangedListener;
    private boolean finalBookingStyle;

    public BookingOptionGroupBinder(
            Context context,
            LinkedHashMap<T, MaterialButton> optionButtons,
            @NonNull T initialSelection
    ) {
        this.context = context;
        this.optionButtons = optionButtons;
        bindClickListeners();
        setSelection(initialSelection);
    }

    public void setSelectionChangedListener(Runnable selectionChangedListener) {
        this.selectionChangedListener = selectionChangedListener;
    }

    public void setSelection(@NonNull T option) {
        this.selectedOption = option;
        refreshStyles();
        if (selectionChangedListener != null) {
            selectionChangedListener.run();
        }
    }

    public T getSelection() {
        return selectedOption;
    }

    public void useFinalBookingStyle() {
        finalBookingStyle = true;
        refreshStyles();
    }

    public void setEnabled(boolean enabled) {
        for (MaterialButton button : optionButtons.values()) {
            button.setEnabled(enabled);
        }
    }

    private void bindClickListeners() {
        for (Map.Entry<T, MaterialButton> entry : optionButtons.entrySet()) {
            entry.getValue().setOnClickListener(view -> setSelection(entry.getKey()));
        }
    }

    private void refreshStyles() {
        for (Map.Entry<T, MaterialButton> entry : optionButtons.entrySet()) {
            boolean selected = entry.getKey().equals(selectedOption);
            MaterialButton button = entry.getValue();
            if (finalBookingStyle) {
                button.setSelected(selected);
                button.setCheckable(true);
                button.setChecked(selected);
                button.setStrokeWidth(0);
                button.setBackgroundTintList(ColorStateList.valueOf(ContextCompat.getColor(context,
                        selected ? R.color.final_booking_selected : R.color.final_input_surface)));
                button.setTextColor(ContextCompat.getColor(context,
                        selected ? R.color.final_text_accent_accessible : R.color.final_text_primary));
                continue;
            }
            if (selected) {
                button.setBackgroundTintList(ColorStateList.valueOf(
                        ContextCompat.getColor(context, R.color.bodeul_primary)
                ));
                button.setStrokeColor(ColorStateList.valueOf(
                        ContextCompat.getColor(context, R.color.bodeul_primary)
                ));
                button.setTextColor(ContextCompat.getColor(context, R.color.white));
            } else {
                button.setBackgroundTintList(ColorStateList.valueOf(
                        ContextCompat.getColor(context, R.color.white)
                ));
                button.setStrokeColor(ColorStateList.valueOf(
                        ContextCompat.getColor(context, R.color.bodeul_outline)
                ));
                button.setTextColor(ContextCompat.getColor(context, R.color.bodeul_primary));
            }
        }
    }
}
