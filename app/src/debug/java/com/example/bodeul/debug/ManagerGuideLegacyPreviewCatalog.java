package com.example.bodeul.debug;

import androidx.annotation.Nullable;

import com.example.bodeul.domain.model.GuideStep;
import com.example.bodeul.domain.model.HospitalGuideFallbackFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** 실제 운영 fallback과 같은 7단계 계약을 최신 화면 연결 상태로 확인한다. */
final class ManagerGuideLegacyPreviewCatalog {
    private static final List<GuideStep> STEPS = Collections.unmodifiableList(
            new ArrayList<>(HospitalGuideFallbackFactory.create("", "").getSteps()));

    private ManagerGuideLegacyPreviewCatalog() {
    }

    static List<GuideStep> steps() {
        return STEPS;
    }

    static GuideStep resolve(@Nullable String rawCode) {
        String code = rawCode == null ? "" : rawCode.trim();
        for (GuideStep step : STEPS) {
            if (step.getCode().equals(code)) {
                return step;
            }
        }
        return STEPS.get(0);
    }
}
