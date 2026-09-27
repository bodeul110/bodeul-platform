package com.example.bodeul.ui.manager;

/** 진료 보조 화면에서 회전 중만 보존할 입력과 마지막 서버 기준값이다. */
final class ManagerGuideConsultationDraft {
    final String guardianUpdate;
    final String fieldNote;
    final String guardianBaseline;
    final String fieldNoteBaseline;

    private ManagerGuideConsultationDraft(
            String guardianUpdate,
            String fieldNote,
            String guardianBaseline,
            String fieldNoteBaseline
    ) {
        this.guardianUpdate = guardianUpdate == null ? "" : guardianUpdate;
        this.fieldNote = fieldNote == null ? "" : fieldNote;
        this.guardianBaseline = normalized(guardianBaseline);
        this.fieldNoteBaseline = normalized(fieldNoteBaseline);
    }

    static ManagerGuideConsultationDraft fromServer(
            String guardianUpdate,
            String fieldNote
    ) {
        String guardian = normalized(guardianUpdate);
        String note = normalized(fieldNote);
        return new ManagerGuideConsultationDraft(guardian, note, guardian, note);
    }

    static ManagerGuideConsultationDraft fromInputs(
            String guardianUpdate,
            String fieldNote,
            String guardianBaseline,
            String fieldNoteBaseline
    ) {
        return new ManagerGuideConsultationDraft(
                guardianUpdate,
                fieldNote,
                guardianBaseline,
                fieldNoteBaseline);
    }

    ManagerGuideConsultationDraft reconcileServer(
            String serverGuardianUpdate,
            String serverFieldNote
    ) {
        String guardianServer = normalized(serverGuardianUpdate);
        String fieldServer = normalized(serverFieldNote);
        return new ManagerGuideConsultationDraft(
                resolvedValue(guardianUpdate, guardianBaseline, guardianServer),
                resolvedValue(fieldNote, fieldNoteBaseline, fieldServer),
                guardianServer,
                fieldServer);
    }

    boolean isGuardianDirty() {
        return !normalized(guardianUpdate).equals(guardianBaseline);
    }

    boolean isFieldNoteDirty() {
        return !normalized(fieldNote).equals(fieldNoteBaseline);
    }

    boolean hasUnsavedChanges() {
        return isGuardianDirty() || isFieldNoteDirty();
    }

    private static String resolvedValue(
            String localValue,
            String previousBaseline,
            String serverValue
    ) {
        String normalizedLocal = normalized(localValue);
        boolean locallyDirty = !normalizedLocal.equals(normalized(previousBaseline));
        if (!locallyDirty || normalizedLocal.equals(serverValue)) {
            return serverValue;
        }
        return localValue == null ? "" : localValue;
    }

    private static String normalized(String value) {
        return value == null ? "" : value.trim();
    }
}
