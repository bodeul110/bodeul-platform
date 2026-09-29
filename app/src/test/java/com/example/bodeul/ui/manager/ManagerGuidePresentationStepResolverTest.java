package com.example.bodeul.ui.manager;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import com.example.bodeul.domain.model.GuideStep;

import org.junit.Test;

public class ManagerGuidePresentationStepResolverTest {
    @Test
    public void resolve_mapsLegacySevenStepContractToLatestScreens() {
        assertEquals("MEETING_CONFIRMATION", resolve("LEGACY_CORE_PATIENT_CONTACT"));
        assertEquals("HOSPITAL_ROUTE", resolve("LEGACY_CORE_RECEPTION_PREPARATION"));
        assertEquals("RECEPTION_QUEUE", resolve("LEGACY_CORE_RECEPTION"));
        assertEquals("CONSULTATION_SUPPORT", resolve("LEGACY_CORE_CONSULTATION"));
        assertEquals("PAYMENT_EVIDENCE", resolve("LEGACY_CORE_PAYMENT"));
        assertEquals("MEDICATION_CONFIRMATION", resolve("LEGACY_CORE_PHARMACY"));
        assertEquals("MANAGER_JOURNAL", resolve("LEGACY_CORE_RETURN_AND_CLOSE"));
    }

    @Test
    public void resolve_keepsCanonicalAndUnknownCodesStable() {
        assertEquals("PAYMENT_EVIDENCE", resolve(" PAYMENT_EVIDENCE "));
        assertEquals("HOSPITAL_EXTENSION", resolve("HOSPITAL_EXTENSION"));
        assertEquals("", resolve(null));
    }

    @Test
    public void toPresentationStep_preservesServerMetadataAndVideoContract() {
        GuideStep raw = new GuideStep(
                "LEGACY_CORE_RECEPTION_PREPARATION",
                2,
                "병원 이동 준비",
                "접수처까지 이동합니다.",
                "route-video",
                "v3",
                "영상이 없으면 안내 표지판을 확인하세요.");

        GuideStep presentation =
                ManagerGuidePresentationStepResolver.toPresentationStep(raw);

        assertEquals("HOSPITAL_ROUTE", presentation.getCode());
        assertEquals(raw.getOrder(), presentation.getOrder());
        assertEquals(raw.getTitle(), presentation.getTitle());
        assertEquals(raw.getDescription(), presentation.getDescription());
        assertEquals(raw.getVideoAssetId(), presentation.getVideoAssetId());
        assertEquals(raw.getVideoAssetVersion(), presentation.getVideoAssetVersion());
        assertEquals(raw.getVideoFallbackText(), presentation.getVideoFallbackText());
        assertNull(ManagerGuidePresentationStepResolver.toPresentationStep(null));
    }

    private String resolve(String code) {
        return ManagerGuidePresentationStepResolver.resolve(code);
    }
}
