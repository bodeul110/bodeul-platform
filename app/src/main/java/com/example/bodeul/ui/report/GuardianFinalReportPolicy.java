package com.example.bodeul.ui.report;

import androidx.annotation.Nullable;

import com.example.bodeul.domain.model.AppointmentStatus;
import com.example.bodeul.domain.model.CompanionSession;
import com.example.bodeul.domain.model.SessionReport;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

/**
 * 완료 리포트 UI를 노출할 수 있는 최소 데이터 조건을 정의한다.
 */
final class GuardianFinalReportPolicy {
    private static final String SEOUL_TIME_ZONE = "Asia/Seoul";

    private GuardianFinalReportPolicy() {
    }

    static boolean shouldRenderFinalReport(
            AppointmentStatus status,
            List<GuardianReportSectionModel> sections
    ) {
        return status == AppointmentStatus.COMPLETED && !sections.isEmpty();
    }

    static boolean hasReportContent(@Nullable SessionReport report) {
        return report != null && (
                optionalText(report.getSummary()) != null
                        || optionalText(report.getTreatmentNotes()) != null
                        || optionalText(report.getNextVisitAt()) != null
                        || optionalText(report.getMedicationNotes()) != null
                        || optionalText(report.getMedicationName()) != null
                        || optionalText(report.getMedicationChangeSummary()) != null
                        || optionalText(report.getMedicationScheduleNote()) != null
                        || report.getMedicationComparisonDecision() != null
                        || optionalText(report.getMedicationComparisonNote()) != null
        );
    }

    static String resolveDateText(
            @Nullable CompanionSession session,
            @Nullable String appointmentAt
    ) {
        if (session != null && session.getCareEndedAtMillis() > 0L) {
            SimpleDateFormat formatter = new SimpleDateFormat("yyyy년 M월 d일 (E)", Locale.KOREA);
            formatter.setTimeZone(TimeZone.getTimeZone(SEOUL_TIME_ZONE));
            return formatter.format(new Date(session.getCareEndedAtMillis()));
        }
        String normalizedAppointment = appointmentAt == null ? "" : appointmentAt.trim();
        if (normalizedAppointment.isEmpty()) {
            return "";
        }
        if (!normalizedAppointment.matches("^\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}$")) {
            return normalizedAppointment;
        }

        SimpleDateFormat appointmentFormatter = new SimpleDateFormat(
                "yyyy-MM-dd HH:mm",
                Locale.KOREA
        );
        appointmentFormatter.setLenient(false);
        appointmentFormatter.setTimeZone(TimeZone.getTimeZone(SEOUL_TIME_ZONE));
        try {
            Date appointmentDate = appointmentFormatter.parse(normalizedAppointment);
            if (appointmentDate != null) {
                SimpleDateFormat displayFormatter = new SimpleDateFormat(
                        "yyyy년 M월 d일 (E)",
                        Locale.KOREA
                );
                displayFormatter.setTimeZone(TimeZone.getTimeZone(SEOUL_TIME_ZONE));
                return displayFormatter.format(appointmentDate);
            }
        } catch (ParseException ignored) {
            // 이전 저장소의 다른 포맷은 원문을 그대로 보여 데이터 손실을 피한다.
        }
        return normalizedAppointment;
    }

    @Nullable
    static String resolveManagerMessage(@Nullable CompanionSession session) {
        if (session == null) {
            return null;
        }
        String journal = optionalText(session.getManagerJournal());
        return journal != null ? journal : optionalText(session.getGuardianUpdate());
    }

    static boolean hasCompletionJournal(@Nullable CompanionSession session) {
        return session != null && optionalText(session.getManagerJournal()) != null;
    }

    @Nullable
    static String optionalText(@Nullable String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }
}
