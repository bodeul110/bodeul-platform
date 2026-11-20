package com.example.bodeul.ui.booking;

import com.example.bodeul.domain.model.GuideStep;
import com.example.bodeul.domain.model.SessionStatus;
import java.util.Arrays;
import java.util.List;
import org.junit.Test;
import static org.junit.Assert.*;

public class ClientGuideProgressTest {
    private final List<GuideStep> steps = Arrays.asList(new GuideStep("MANAGER_JOURNAL",5,"내부 일지",""),
            new GuideStep("CONSULTATION_SUMMARY",3,"진료 정리",""),new GuideStep("CONSULTATION_SUPPORT",2,"진료 보조",""),
            new GuideStep("LEGACY_CORE_PATIENT_CONTACT",1,"만남",""),new GuideStep("CUSTOM_STEP",4,"안내",""));
    @Test public void groupedStepsSortInGuideOrderAndHideManagerJournal() {
        List<ClientGuideProgress.Row> rows = ClientGuideProgress.rows(steps,"CONSULTATION_SUMMARY",1,SessionStatus.IN_TREATMENT);
        assertEquals(3,rows.size());
        assertEquals(1,rows.get(0).group);
        assertEquals(ClientGuideProgress.State.DONE,rows.get(0).state);
        assertEquals(6,rows.get(1).group);
        assertEquals(ClientGuideProgress.State.NOW,rows.get(1).state);
        assertEquals("안내",rows.get(2).title);
        assertEquals(ClientGuideProgress.State.NEXT,rows.get(2).state);
    }
    @Test public void unknownCodeDoesNotMakeAnUnrelatedStageActiveAndNoGuideAddsNoStages() {
        for (ClientGuideProgress.Row row : ClientGuideProgress.rows(steps,"LATER_SCHEMA",2,SessionStatus.MEETING))
            assertEquals(ClientGuideProgress.State.UNKNOWN,row.state);
        assertTrue(ClientGuideProgress.rows(null,"CONSULTATION_SUMMARY",3,SessionStatus.COMPLETED).isEmpty());
    }
    @Test public void legacyOrderReadyOngoingCanceledAndEndedRemainDistinct() {
        for (ClientGuideProgress.Row row : ClientGuideProgress.rows(steps,"",2,SessionStatus.READY))
            assertEquals(ClientGuideProgress.State.NEXT,row.state);
        assertEquals(ClientGuideProgress.State.NOW,ClientGuideProgress.rows(steps,"",2,SessionStatus.MEETING).get(1).state);
        for (ClientGuideProgress.Row row : ClientGuideProgress.rows(steps,"",2,SessionStatus.CANCELED))
            assertEquals(ClientGuideProgress.State.STOPPED,row.state);
        assertEquals(ClientGuideProgress.State.UNKNOWN,ClientGuideProgress.rows(steps,"",2,SessionStatus.CARE_ENDED).get(1).state);
        for (ClientGuideProgress.Row row : ClientGuideProgress.rows(steps,"",2,SessionStatus.COMPLETED))
            assertEquals(ClientGuideProgress.State.DONE,row.state);
    }
}
