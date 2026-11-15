package com.example.bodeul.ui.report;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.core.view.ViewCompat;

import com.example.bodeul.R;

/** 완성본 리포트의 표현만 담당한다. 서버가 비워 준 값을 추론해 채우지 않는다. */
public final class FinalReportBinder {
    private final View root;

    public FinalReportBinder(View root) {
        this.root = root;
        for (int id : new int[]{R.id.textFinalReportTitle, R.id.textFinalReportTreatmentTitle,
                R.id.textGuardianFinalReportManagerMessageLabel}) {
            ViewCompat.setAccessibilityHeading(root.findViewById(id), true);
        }
    }

    public void bind(GuardianReportEntryCardModel model, boolean managerView) {
        text(R.id.textGuardianFinalReportDate, model.getAppointmentDateText());
        text(R.id.textFinalReportPatient, model.getPatientNameText());
        text(R.id.textGuardianFinalReportHospital, model.getHospitalNameText());
        text(R.id.textGuardianFinalReportDepartment, model.getDepartmentNameText());
        visible(R.id.cardGuardianFinalReportVisitSummary,
                hasText(model.getHospitalNameText()) || hasText(model.getDepartmentNameText()));
        text(R.id.textGuardianFinalReportCondition, model.getPatientConditionText());
        visible(R.id.cardGuardianFinalReportCondition,
                !managerView && hasText(model.getPatientConditionText()));

        LinearLayout sections = root.findViewById(R.id.finalReportSections);
        sections.removeAllViews();
        if (!managerView && model.isFinalReportReady()) {
            LayoutInflater inflater = LayoutInflater.from(root.getContext());
            for (GuardianReportSectionModel section : model.getReportSections()) {
                View sectionView = inflater.inflate(R.layout.item_final_report_section, sections, false);
                ((TextView) sectionView.findViewById(R.id.textFinalReportSectionTitle))
                        .setText(section.getTitleText());
                ViewCompat.setAccessibilityHeading(sectionView.findViewById(R.id.textFinalReportSectionTitle), true);
                LinearLayout lines = sectionView.findViewById(R.id.finalReportSectionLines);
                for (GuardianReportLineItem line : section.getLines()) {
                    View lineView = inflater.inflate(R.layout.item_final_report_line, lines, false);
                    ((TextView) lineView.findViewById(R.id.textFinalReportLineLabel)).setText(line.getLabelText());
                    ((TextView) lineView.findViewById(R.id.textFinalReportLineValue)).setText(line.getValueText());
                    addSpaced(lines, lineView, 5);
                }
                addSpaced(sections, sectionView, 30);
            }
        }
        visible(R.id.cardFinalReportTreatment, sections.getChildCount() > 0);
        TextView notice = root.findViewById(R.id.textFinalReportNotice);
        notice.setVisibility(managerView || sections.getChildCount() == 0 ? View.VISIBLE : View.GONE);
        notice.setText(managerView ? R.string.final_report_manager_scope : R.string.final_report_pending);

        visible(R.id.cardGuardianFinalReportManager, hasText(model.getManagerMessageText()));
        text(R.id.textGuardianFinalReportManagerName, model.getManagerNameText());
        text(R.id.textGuardianFinalReportManagerMessageLabel, model.getManagerMessageLabelText());
        text(R.id.textGuardianFinalReportManagerMessage, model.getManagerMessageText());
    }

    private void text(int id, String value) {
        TextView text = root.findViewById(id);
        text.setText(value == null ? "" : value);
        text.setVisibility(hasText(value) ? View.VISIBLE : View.GONE);
    }

    private void visible(int id, boolean show) {
        root.findViewById(id).setVisibility(show ? View.VISIBLE : View.GONE);
    }

    private void addSpaced(LinearLayout parent, View child, int gapDp) {
        ViewGroup.MarginLayoutParams params = (ViewGroup.MarginLayoutParams) child.getLayoutParams();
        params.topMargin = parent.getChildCount() == 0 ? 0
                : Math.round(gapDp * root.getResources().getDisplayMetrics().density);
        parent.addView(child, params);
    }

    private static boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }
}
