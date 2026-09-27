package com.example.bodeul.debug;

import androidx.annotation.Nullable;

import com.example.bodeul.domain.model.GuideStep;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/** Core API 단계 계약과 같은 debug 미리보기 카탈로그다. */
final class ManagerGuidePreviewCatalog {
    private static final List<GuideStep> STEPS = Collections.unmodifiableList(Arrays.asList(
            new GuideStep("MEETING_CONFIRMATION", 1, "상봉 확인", "매니저와 환자의 상봉을 확인합니다."),
            new GuideStep("HOSPITAL_ROUTE", 2, "병원 이동", "병원 로비와 진료과까지 이동합니다."),
            new GuideStep("RECEPTION_QUEUE", 3, "접수와 대기", "접수 상태와 대기 순서를 확인합니다."),
            new GuideStep("VITALS_CHECK", 4, "기초 측정", "병원에서 확인한 기초 측정값을 기록합니다."),
            new GuideStep("PRE_CONSULTATION", 5, "진료 전 확인", "증상과 전달 사항을 진료 전에 확인합니다."),
            new GuideStep("CONSULTATION_SUPPORT", 6, "진료 동행", "진료 중 핵심 안내를 기록합니다."),
            new GuideStep("CONSULTATION_SUMMARY", 7, "진료 요약", "진료 요약을 검토하고 공유 내용을 확인합니다."),
            new GuideStep("PAYMENT_EVIDENCE", 8, "수납 증빙", "수납 완료와 결제 증빙을 확인합니다."),
            new GuideStep("PHARMACY_ROUTE", 9, "약국 이동", "처방전을 기준으로 주변 약국을 찾습니다."),
            new GuideStep("PRESCRIPTION_DOCUMENTS", 10, "처방 자료", "처방 관련 이미지 자료를 등록합니다."),
            new GuideStep("MEDICATION_CONFIRMATION", 11, "복약 확인", "약 수령과 복약 안내 완료 여부를 확인합니다."),
            new GuideStep("CARE_COMPLETION", 12, "동행 종료", "환자 상태와 인계 내용을 최종 확인합니다."),
            new GuideStep("MANAGER_JOURNAL", 13, "매니저 일지", "동행 내용을 정리하고 최종 일지를 작성합니다.")
    ));

    private ManagerGuidePreviewCatalog() {
    }

    static List<GuideStep> steps() {
        return STEPS;
    }

    static GuideStep resolve(@Nullable String rawCode) {
        GuideStep matching = find(rawCode);
        return matching == null ? STEPS.get(0) : matching;
    }

    @Nullable
    static GuideStep find(@Nullable String rawCode) {
        String code = rawCode == null ? "" : rawCode.trim();
        for (GuideStep step : STEPS) {
            if (step.getCode().equals(code)) {
                return step;
            }
        }
        return null;
    }

    @Nullable
    static GuideStep findByOrder(int order) {
        for (GuideStep step : STEPS) {
            if (step.getOrder() == order) {
                return step;
            }
        }
        return null;
    }
}
