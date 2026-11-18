package com.example.bodeul.ui.support;

import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import androidx.core.view.ViewCompat;
import com.example.bodeul.R;
import java.util.EnumMap;

final class FaqBinder {
    private final View root;
    private final FaqSelection selection;
    private final EnumMap<FaqSelection.Category, TextView> filters = new EnumMap<>(FaqSelection.Category.class);
    private final EnumMap<FaqSelection.Question, View> rows = new EnumMap<>(FaqSelection.Question.class);

    FaqBinder(View root, FaqSelection selection) {
        this.root = root;
        this.selection = selection;
    }

    void bind() {
        LayoutInflater inflater = LayoutInflater.from(root.getContext());
        LinearLayout chips = root.findViewById(R.id.faqFilters);
        LinearLayout list = root.findViewById(R.id.faqRows);
        ScrollView scroll = root.findViewById(R.id.faqScroll);
        for (FaqSelection.Category category : FaqSelection.Category.values()) {
            TextView chip = (TextView) inflater.inflate(R.layout.item_faq_filter, chips, false);
            LinearLayout.LayoutParams layout = (LinearLayout.LayoutParams) chip.getLayoutParams();
            if (chips.getChildCount() > 0) layout.setMarginStart(Math.round(5 * root.getResources().getDisplayMetrics().density));
            chip.setText(category.label);
            chip.setOnClickListener(view -> {
                if (category == selection.category()) return;
                selection.select(category);
                render();
                scroll.scrollTo(0, 0);
            });
            chips.addView(chip, layout);
            filters.put(category, chip);
        }
        for (FaqSelection.Question question : FaqSelection.Question.values()) {
            View row = inflater.inflate(R.layout.item_faq_question, list, false);
            ((TextView) row.findViewById(R.id.faqCategory)).setText(question.category.label);
            ((TextView) row.findViewById(R.id.faqQuestion)).setText(question.title);
            ((TextView) row.findViewById(R.id.faqAnswer)).setText(question.answer);
            View toggle = row.findViewById(R.id.faqToggle);
            toggle.setContentDescription(root.getResources().getString(R.string.faq_toggle_label,
                    root.getResources().getString(question.category.label), root.getResources().getString(question.title)));
            toggle.setOnClickListener(view -> { selection.toggle(question); render(); });
            list.addView(row);
            rows.put(question, row);
        }
        render();
    }

    private void render() {
        for (FaqSelection.Category category : filters.keySet()) {
            filters.get(category).setSelected(category == selection.category());
        }
        boolean empty = true;
        for (FaqSelection.Question question : rows.keySet()) {
            View row = rows.get(question);
            boolean included = selection.includes(question);
            boolean expanded = question == selection.expanded();
            if (included) empty = false;
            row.setVisibility(included ? View.VISIBLE : View.GONE);
            row.findViewById(R.id.faqAnswer).setVisibility(expanded ? View.VISIBLE : View.GONE);
            row.findViewById(R.id.faqDivider).setVisibility(expanded ? View.GONE : View.VISIBLE);
            ((ImageView) row.findViewById(R.id.faqChevron)).setImageResource(expanded
                    ? R.drawable.bodeul_final_booking_dropdown_up : R.drawable.bodeul_final_booking_dropdown_down);
            ViewCompat.setStateDescription(row.findViewById(R.id.faqToggle), root.getResources().getString(
                    expanded ? R.string.faq_expanded : R.string.faq_collapsed));
        }
        root.findViewById(R.id.faqEmpty).setVisibility(empty ? View.VISIBLE : View.GONE);
    }
}
