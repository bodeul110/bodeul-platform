package com.example.bodeul.ui.support;

import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import com.example.bodeul.R;

/** FAQ에는 계정 데이터 없이 분류와 펼친 질문 한 개만 보관한다. */
final class FaqSelection {
    enum Category {
        ALL(R.string.faq_filter_all), MATCHING(R.string.faq_filter_matching),
        PAYMENT(R.string.faq_filter_payment), SERVICE(R.string.faq_filter_service),
        OTHER(R.string.faq_filter_other);

        @StringRes final int label;
        Category(@StringRes int label) { this.label = label; }
    }

    enum Question {
        SERVICE(Category.SERVICE, R.string.faq_service_question, R.string.faq_service_answer),
        BOOKING(Category.MATCHING, R.string.faq_booking_question, R.string.faq_booking_answer),
        MANAGER(Category.MATCHING, R.string.faq_manager_question, R.string.faq_manager_answer),
        PAYMENT(Category.PAYMENT, R.string.faq_payment_question, R.string.faq_payment_answer);

        final Category category;
        @StringRes final int title;
        @StringRes final int answer;
        Question(Category category, @StringRes int title, @StringRes int answer) {
            this.category = category;
            this.title = title;
            this.answer = answer;
        }
    }

    private Category category = Category.ALL;
    @Nullable private Question expanded;

    Category category() { return category; }
    @Nullable Question expanded() { return expanded; }
    boolean includes(Question question) { return category == Category.ALL || category == question.category; }

    void select(Category next) {
        category = next;
        if (expanded != null && !includes(expanded)) expanded = null;
    }

    void toggle(Question question) {
        if (includes(question)) expanded = expanded == question ? null : question;
    }

    static FaqSelection restore(@Nullable String categoryKey, @Nullable String questionKey) {
        FaqSelection result = new FaqSelection();
        for (Category value : Category.values()) {
            if (value.name().equals(categoryKey)) {
                result.select(value);
                for (Question question : Question.values()) {
                    if (question.name().equals(questionKey)) result.toggle(question);
                }
                break;
            }
        }
        return result;
    }
}
