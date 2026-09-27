package com.example.bodeul.ui.manager;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class ManagerGuidePrescriptionSelectionPolicyTest {

    @Test
    public void zeroThroughThreeImages_stayWithinLimit() {
        for (int count = 0; count <= 3; count++) {
            assertFalse(ManagerGuidePrescriptionSelectionPolicy.exceedsLimit(count));
        }
    }

    @Test
    public void fourImages_exceedsLimit() {
        assertTrue(ManagerGuidePrescriptionSelectionPolicy.exceedsLimit(4));
    }
}
