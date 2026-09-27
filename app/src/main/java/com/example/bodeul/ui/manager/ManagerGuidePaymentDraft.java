package com.example.bodeul.ui.manager;

/** 수납 증빙 화면에서 작성 중인 공유 메모와 마지막 서버 기준값을 함께 보존한다. */
final class ManagerGuidePaymentDraft {
    final String note;
    final String baseline;

    private ManagerGuidePaymentDraft(String note, String baseline) {
        this.note = note == null ? "" : note;
        this.baseline = normalized(baseline);
    }

    static ManagerGuidePaymentDraft fromServer(String note) {
        String normalized = normalized(note);
        return new ManagerGuidePaymentDraft(normalized, normalized);
    }

    static ManagerGuidePaymentDraft fromInput(String note, String baseline) {
        return new ManagerGuidePaymentDraft(note, baseline);
    }

    ManagerGuidePaymentDraft reconcileServer(String serverNote) {
        String normalizedServer = normalized(serverNote);
        String normalizedLocal = normalized(note);
        boolean locallyDirty = !normalizedLocal.equals(baseline);
        String resolved = !locallyDirty || normalizedLocal.equals(normalizedServer)
                ? normalizedServer
                : note;
        return new ManagerGuidePaymentDraft(resolved, normalizedServer);
    }

    boolean hasUnsavedChanges() {
        return !normalized(note).equals(baseline);
    }

    private static String normalized(String value) {
        return value == null ? "" : value.trim();
    }
}
