package com.example.bodeul.ui.manager;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ManagerGuideSummaryDraftTest {
    @Test
    public void refreshKeepsDirtyInputButUpdatesBaseline() {
        ManagerGuideSummaryDraft draft = ManagerGuideSummaryDraft.fromInput("작성 중", "이전 기록")
                .reconcileServer("다른 기기에서 저장");
        assertEquals("작성 중", draft.note);
        assertEquals("다른 기기에서 저장", draft.baseline);
        assertTrue(draft.hasUnsavedChanges());
    }

    @Test
    public void savedValueBecomesCleanAndCleanValueFollowsServer() {
        ManagerGuideSummaryDraft draft = ManagerGuideSummaryDraft.fromInput(" 새 기록 ", "이전 기록")
                .reconcileServer("새 기록");
        assertFalse(draft.hasUnsavedChanges());
        assertEquals("새 기록", draft.note);
        assertEquals("최신 기록", draft.reconcileServer("최신 기록").note);
    }

    @Test
    public void clearingExistingMemoRemainsDirtyUntilSaveSucceeds() {
        ManagerGuideSummaryDraft draft = ManagerGuideSummaryDraft.fromInput("", "기존 기록")
                .reconcileServer("기존 기록");
        assertEquals("", draft.note);
        assertTrue(draft.hasUnsavedChanges());
        assertFalse(draft.reconcileServer("").hasUnsavedChanges());
    }
}
