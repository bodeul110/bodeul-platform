package com.example.bodeul.ui.manager;

import static org.junit.Assert.assertEquals;
import com.example.bodeul.domain.model.*;
import java.util.Arrays;
import org.junit.Test;

public class ManagerDocumentDisplayStateTest {
    @Test public void missingFileNeverLooksApproved() {
        assertEquals(ManagerDocumentDisplayState.MISSING, ManagerDocumentDisplayState.from(null));
        assertEquals(ManagerDocumentDisplayState.MISSING, state(ManagerDocumentStatus.APPROVED));
    }
    @Test public void draftIsNotPendingReview() { assertEquals(ManagerDocumentDisplayState.DRAFT, state(ManagerDocumentStatus.NOT_SUBMITTED, file(ManagerDocumentFileType.LICENSE))); }
    @Test public void pendingComesFromServer() { assertEquals(ManagerDocumentDisplayState.PENDING, state(ManagerDocumentStatus.PENDING_REVIEW, file(ManagerDocumentFileType.LICENSE))); }
    @Test public void approvalComesFromServer() { assertEquals(ManagerDocumentDisplayState.APPROVED, state(ManagerDocumentStatus.APPROVED, file(ManagerDocumentFileType.NURSING_LICENSE))); }
    @Test public void rejectionComesFromServer() { assertEquals(ManagerDocumentDisplayState.REJECTED, state(ManagerDocumentStatus.REJECTED, file(ManagerDocumentFileType.LICENSE))); }
    @Test public void legacyFileRequiresReplacementEvenWithOldApproval() { assertEquals(ManagerDocumentDisplayState.REPLACEMENT, state(ManagerDocumentStatus.APPROVED, file(ManagerDocumentFileType.HEALTH_CERTIFICATE))); }
    @Test public void multipleQualificationsDoNotBecomeMultipleApprovals() { assertEquals(ManagerDocumentDisplayState.REPLACEMENT, state(ManagerDocumentStatus.APPROVED, file(ManagerDocumentFileType.LICENSE), file(ManagerDocumentFileType.NURSING_LICENSE))); }
    @Test public void forbiddenDocumentsAreNotRegistrationCandidates() { assertEquals(ManagerDocumentDisplayState.MISSING, state(ManagerDocumentStatus.APPROVED, file(ManagerDocumentFileType.ID_CARD), file(ManagerDocumentFileType.CRIMINAL_RECORD))); }
    private ManagerDocumentDisplayState state(ManagerDocumentStatus status, ManagerDocumentFileMetadata... files) {
        return ManagerDocumentDisplayState.from(new ManagerHomeProfile("", "", status, "", 0, 0, "", Arrays.asList(files)));
    }
    private ManagerDocumentFileMetadata file(ManagerDocumentFileType type) {
        return new ManagerDocumentFileMetadata(type, "manager-documents/owner/" + type.getStorageKey() + "/sample.png", "sample.png", "image/png", 100, "");
    }
}
