package com.example.bodeul.ui.manager;

/** 진료 요약 초안은 서버 기준값과 함께 ViewModel 메모리에서만 보존한다. */
final class ManagerGuideSummaryDraft {
    final String note;
    final String baseline;

    private ManagerGuideSummaryDraft(String note, String baseline) {
        this.note = note == null ? "" : note;
        this.baseline = normalized(baseline);
    }

    static ManagerGuideSummaryDraft fromServer(String note) {
        return new ManagerGuideSummaryDraft(normalized(note), note);
    }

    static ManagerGuideSummaryDraft fromInput(String note, String baseline) {
        return new ManagerGuideSummaryDraft(note, baseline);
    }

    ManagerGuideSummaryDraft reconcileServer(String serverNote) {
        String server = normalized(serverNote);
        return new ManagerGuideSummaryDraft(
                !hasUnsavedChanges() || normalized(note).equals(server) ? server : note,
                server);
    }

    boolean hasUnsavedChanges() {
        return !normalized(note).equals(baseline);
    }

    private static String normalized(String value) {
        return value == null ? "" : value.trim();
    }
}
