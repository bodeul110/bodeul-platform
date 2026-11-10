package com.example.bodeul.ui.booking;

import org.junit.Test;
import java.util.Arrays;
import java.util.Collections;
import static org.junit.Assert.*;

public class BookingDepartmentOptionsTest {
    @Test public void catalogKeepsItsOrderAndRemovesBlanksAndDuplicates() {
        assertEquals(Arrays.asList("내과", "외과"), BookingDepartmentOptions.build(
                Arrays.asList(" 내과 ", null, "", "외과", "내과"), Arrays.asList("기타"), ""));
    }
    @Test public void unknownHospitalUsesDefaultChoices() {
        assertEquals(Arrays.asList("내과", "외과"), BookingDepartmentOptions.build(
                Collections.emptyList(), Arrays.asList("내과", "외과"), ""));
    }
    @Test public void customDraftDepartmentIsNotLost() {
        assertEquals(Arrays.asList("내과", "특수 진료과"), BookingDepartmentOptions.build(
                Arrays.asList("내과"), Collections.emptyList(), "특수 진료과"));
    }
}
