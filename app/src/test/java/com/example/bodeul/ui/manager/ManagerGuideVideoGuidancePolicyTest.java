package com.example.bodeul.ui.manager;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.example.bodeul.domain.model.GuideStep;

import org.junit.Test;

public class ManagerGuideVideoGuidancePolicyTest {
    @Test
    public void resolve_hidesVideoGuidanceOutsideHospitalRoute() {
        ManagerGuideVideoGuidancePolicy.Result result =
                ManagerGuideVideoGuidancePolicy.resolve(new GuideStep(
                        "MEETING_CONFIRMATION",
                        1,
                        "상봉 확인",
                        "환자와 만납니다.",
                        "unexpected-asset",
                        "v1",
                        "대체 안내"));

        assertEquals(ManagerGuideVideoGuidancePolicy.State.HIDDEN, result.getState());
        assertFalse(result.isVisible());
    }

    @Test
    public void resolve_usesFallbackOnlyWhenAssetMetadataIsMissingOrPartial() {
        ManagerGuideVideoGuidancePolicy.Result missing =
                ManagerGuideVideoGuidancePolicy.resolve(new GuideStep(
                        "HOSPITAL_ROUTE",
                        2,
                        "병원 이동",
                        "기본 단계 설명",
                        "",
                        "",
                        "안내 데스크를 지나 신경과 표지판을 따라가세요."));
        ManagerGuideVideoGuidancePolicy.Result partial =
                ManagerGuideVideoGuidancePolicy.resolve(new GuideStep(
                        "HOSPITAL_ROUTE",
                        2,
                        "병원 이동",
                        "기본 단계 설명",
                        "route-asset",
                        "",
                        ""));
        ManagerGuideVideoGuidancePolicy.Result missingFallback =
                ManagerGuideVideoGuidancePolicy.resolve(new GuideStep(
                        "HOSPITAL_ROUTE",
                        2,
                        "병원 이동",
                        "기본 단계 설명",
                        "route-asset",
                        "v1",
                        ""));

        assertEquals(ManagerGuideVideoGuidancePolicy.State.FALLBACK_ONLY, missing.getState());
        assertEquals(
                "안내 데스크를 지나 신경과 표지판을 따라가세요.",
                missing.getFallbackText());
        assertEquals(ManagerGuideVideoGuidancePolicy.State.FALLBACK_ONLY, partial.getState());
        assertEquals("", partial.getFallbackText());
        assertEquals(
                ManagerGuideVideoGuidancePolicy.State.FALLBACK_ONLY,
                missingFallback.getState());
    }

    @Test
    public void resolve_marksCompleteAssetMetadataWithoutExposingIdentifier() {
        ManagerGuideVideoGuidancePolicy.Result result =
                ManagerGuideVideoGuidancePolicy.resolve(new GuideStep(
                        "HOSPITAL_ROUTE",
                        2,
                        "병원 이동",
                        "기본 단계 설명",
                        "route-asset",
                        "v2",
                        "텍스트 대체 안내"));

        assertEquals(ManagerGuideVideoGuidancePolicy.State.ASSET_REGISTERED, result.getState());
        assertTrue(result.isVisible());
        assertTrue(result.hasRegisteredAsset());
        assertEquals("텍스트 대체 안내", result.getFallbackText());
    }

    @Test
    public void confirmation_requiresRouteStepAdvance() {
        assertTrue(ManagerGuideAdvanceConfirmationPolicy.requiresConfirmation(
                ManagerGuidePrimaryAction.ADVANCE,
                " HOSPITAL_ROUTE "));
        assertTrue(ManagerGuideAdvanceConfirmationPolicy.requiresConfirmation(
                ManagerGuidePrimaryAction.ADVANCE,
                "PHARMACY_ROUTE"));
        assertTrue(ManagerGuideAdvanceConfirmationPolicy.requiresConfirmation(
                ManagerGuidePrimaryAction.ADVANCE,
                "LEGACY_CORE_RECEPTION_PREPARATION"));
        assertFalse(ManagerGuideAdvanceConfirmationPolicy.requiresConfirmation(
                ManagerGuidePrimaryAction.NONE,
                "HOSPITAL_ROUTE"));
        assertFalse(ManagerGuideAdvanceConfirmationPolicy.requiresConfirmation(
                ManagerGuidePrimaryAction.ADVANCE,
                "RECEPTION_QUEUE"));
    }

    @Test
    public void confirmation_appliesOnlyToSameSessionAndStep() {
        assertTrue(ManagerGuideAdvanceConfirmationPolicy.canApplyConfirmation(
                ManagerGuidePrimaryAction.ADVANCE,
                "session-1",
                "HOSPITAL_ROUTE",
                "session-1",
                " HOSPITAL_ROUTE "));
        assertTrue(ManagerGuideAdvanceConfirmationPolicy.canApplyConfirmation(
                ManagerGuidePrimaryAction.ADVANCE,
                "session-1",
                "LEGACY_CORE_RECEPTION_PREPARATION",
                "session-1",
                "LEGACY_CORE_RECEPTION_PREPARATION"));
        assertFalse(ManagerGuideAdvanceConfirmationPolicy.canApplyConfirmation(
                ManagerGuidePrimaryAction.ADVANCE,
                "session-1",
                "HOSPITAL_ROUTE",
                "session-2",
                "HOSPITAL_ROUTE"));
        assertFalse(ManagerGuideAdvanceConfirmationPolicy.canApplyConfirmation(
                ManagerGuidePrimaryAction.ADVANCE,
                "session-1",
                "HOSPITAL_ROUTE",
                "session-1",
                "RECEPTION_QUEUE"));
    }
}
