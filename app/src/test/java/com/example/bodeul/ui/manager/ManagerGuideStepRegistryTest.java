package com.example.bodeul.ui.manager;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import com.example.bodeul.domain.model.GuideStep;

public class ManagerGuideStepRegistryTest {
    private static final String[] PRODUCT_CODES = {
            "MEETING_CONFIRMATION",
            "HOSPITAL_ROUTE",
            "RECEPTION_QUEUE",
            "VITALS_CHECK",
            "PRE_CONSULTATION",
            "CONSULTATION_SUPPORT",
            "CONSULTATION_SUMMARY",
            "PAYMENT_EVIDENCE",
            "PHARMACY_ROUTE",
            "PRESCRIPTION_DOCUMENTS",
            "MEDICATION_CONFIRMATION",
            "CARE_COMPLETION",
            "MANAGER_JOURNAL"
    };

    private static final String[] LEGACY_CODES = {
            "LEGACY_CORE_PATIENT_CONTACT",
            "LEGACY_CORE_RECEPTION_PREPARATION",
            "LEGACY_CORE_RECEPTION",
            "LEGACY_CORE_CONSULTATION",
            "LEGACY_CORE_PAYMENT",
            "LEGACY_CORE_PHARMACY",
            "LEGACY_CORE_RETURN_AND_CLOSE"
    };

    @Test
    public void resolve_recognizesProductAndLegacyCodes() {
        for (String code : PRODUCT_CODES) {
            assertNotEquals(
                    code,
                    ManagerGuideStepRegistry.PresentationType.GENERAL,
                    ManagerGuideStepRegistry.resolve(code));
        }
        for (String code : LEGACY_CODES) {
            assertNotEquals(
                    code,
                    ManagerGuideStepRegistry.PresentationType.GENERAL,
                    ManagerGuideStepRegistry.resolve(code));
        }
    }

    @Test
    public void resolve_returnsGeneralForUnknownCode() {
        assertEquals(
                ManagerGuideStepRegistry.PresentationType.GENERAL,
                ManagerGuideStepRegistry.resolve("UNLISTED_EXTENSION"));
        assertEquals(
                ManagerGuideStepRegistry.PresentationType.GENERAL,
                ManagerGuideStepRegistry.resolve((GuideStep) null));
    }

    @Test
    public void isPharmacyRoute_matchesOnlyStablePharmacyRouteCode() {
        assertTrue(ManagerGuideStepRegistry.isPharmacyRoute("PHARMACY_ROUTE"));
        assertTrue(ManagerGuideStepRegistry.isPharmacyRoute(" PHARMACY_ROUTE "));
        assertFalse(ManagerGuideStepRegistry.isPharmacyRoute("PRESCRIPTION_DOCUMENTS"));
        assertFalse(ManagerGuideStepRegistry.isPharmacyRoute("LEGACY_CORE_PHARMACY"));
        assertFalse(ManagerGuideStepRegistry.isPharmacyRoute(null));
    }

    @Test
    public void isHospitalRoute_matchesCanonicalAndLegacyRouteCodes() {
        assertTrue(ManagerGuideStepRegistry.isHospitalRoute("HOSPITAL_ROUTE"));
        assertTrue(ManagerGuideStepRegistry.isHospitalRoute(" HOSPITAL_ROUTE "));
        assertTrue(ManagerGuideStepRegistry.isHospitalRoute(
                "LEGACY_CORE_RECEPTION_PREPARATION"));
        assertFalse(ManagerGuideStepRegistry.isHospitalRoute("MEETING_CONFIRMATION"));
        assertFalse(ManagerGuideStepRegistry.isHospitalRoute("LEGACY_CORE_PATIENT_CONTACT"));
        assertFalse(ManagerGuideStepRegistry.isHospitalRoute(null));
    }

    @Test
    public void resolveGuideStep_usesStableCodeBeforeLegacyOrderFallback() {
        GuideStep codedStep = new GuideStep("PHARMACY_ROUTE", 1, "약국 이동", "");
        GuideStep legacyOrderStep = new GuideStep("", 6, "복약", "");

        assertEquals(
                ManagerGuideStepRegistry.PresentationType.MEDICATION,
                ManagerGuideStepRegistry.resolve(codedStep));
        assertEquals(
                ManagerGuideStepRegistry.PresentationType.MEDICATION,
                ManagerGuideStepRegistry.resolve(legacyOrderStep));
    }

}
