package com.example.bodeul.data;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import com.example.bodeul.domain.model.ManagerDashboard;
import com.example.bodeul.domain.model.SessionStatus;

import org.junit.Test;

/** 시연용 7단계 진행 상태가 화면 문구와 일치하는지 확인한다. */
public class MockBodeulRepositoryGuideProgressTest {
    @Test
    public void finalGuideStep_movesSessionToCareEnded() {
        MockBodeulRepository repository = new MockBodeulRepository();
        ManagerDashboard dashboard = repository.getManagerDashboard("manager-1");
        assertNotNull(dashboard);

        int totalSteps = dashboard.getHospitalGuide().getSteps().size();
        for (int currentOrder = dashboard.getSession().getCurrentStepOrder();
             currentOrder < totalSteps;
             currentOrder++) {
            dashboard = repository.advanceManagerSession("manager-1");
            assertNotNull(dashboard);
        }

        assertEquals(totalSteps, dashboard.getSession().getCurrentStepOrder());
        assertEquals(SessionStatus.CARE_ENDED, dashboard.getSession().getStatus());
    }
}
