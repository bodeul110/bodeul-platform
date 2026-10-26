package com.example.bodeul.ui.report;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.example.bodeul.domain.model.AppointmentStatus;
import com.example.bodeul.domain.model.CompanionSession;
import com.example.bodeul.domain.model.SessionReport;
import com.example.bodeul.domain.model.SessionStatus;

import org.junit.Test;

import java.util.Calendar;
import java.util.Collections;
import java.util.List;
import java.util.TimeZone;

public class GuardianFinalReportPolicyTest {
    @Test
    public void finalReport_requiresCompletedAppointmentAndRealSections() {
        List<GuardianReportSectionModel> sections = Collections.singletonList(
                new GuardianReportSectionModel(
                        "진료 내용",
                        Collections.singletonList(new GuardianReportLineItem("요약", "정상 진료", true))
                )
        );

        assertTrue(GuardianFinalReportPolicy.shouldRenderFinalReport(
                AppointmentStatus.COMPLETED,
                sections
        ));
        assertFalse(GuardianFinalReportPolicy.shouldRenderFinalReport(
                AppointmentStatus.IN_PROGRESS,
                sections
        ));
        assertFalse(GuardianFinalReportPolicy.shouldRenderFinalReport(
                AppointmentStatus.COMPLETED,
                Collections.emptyList()
        ));
    }

    @Test
    public void dateText_prefersActualCareEndAndFallsBackToAppointmentContract() {
        CompanionSession session = new CompanionSession(
                "session-id",
                "appointment-id",
                "manager-id",
                13,
                SessionStatus.COMPLETED,
                "",
                "",
                "",
                "",
                "",
                true
        );
        Calendar endedAt = Calendar.getInstance(TimeZone.getTimeZone("Asia/Seoul"));
        endedAt.clear();
        endedAt.set(2026, Calendar.SEPTEMBER, 27, 18, 30);
        session.applyCompletionState(
                endedAt.getTimeInMillis(),
                "안전하게 인계했습니다.",
                "READY",
                1,
                "",
                endedAt.getTimeInMillis(),
                null
        );

        assertEquals(
                "2026년 9월 27일 (일)",
                GuardianFinalReportPolicy.resolveDateText(session, "2026-09-28 10:00")
        );
        assertEquals(
                "2026년 9월 27일 (일)",
                GuardianFinalReportPolicy.resolveDateText(null, " 2026-09-27 10:00 ")
        );
        assertEquals(
                "서버 원문 일정",
                GuardianFinalReportPolicy.resolveDateText(null, "서버 원문 일정")
        );
    }

    @Test
    public void optionalText_neverCreatesMissingServerContent() {
        assertNull(GuardianFinalReportPolicy.optionalText(null));
        assertNull(GuardianFinalReportPolicy.optionalText("   "));
        assertEquals("실제 메모", GuardianFinalReportPolicy.optionalText(" 실제 메모 "));
    }

    @Test
    public void whitespaceOnlySessionReport_hasNoDisplayableContent() {
        SessionReport whitespaceOnly = new SessionReport(
                "report-id",
                "session-id",
                "   ",
                "\n",
                " ",
                "",
                "\t",
                " ",
                null,
                " ",
                "  "
        );
        SessionReport actualSummary = new SessionReport(
                "report-id",
                "session-id",
                "실제 진료 요약",
                "",
                "",
                "",
                "",
                "",
                null,
                "",
                ""
        );

        assertFalse(GuardianFinalReportPolicy.hasReportContent(whitespaceOnly));
        assertTrue(GuardianFinalReportPolicy.hasReportContent(actualSummary));
    }

    @Test
    public void managerMessage_prefersCompletionJournalAndUsesOnlyRealLiveUpdateAsFallback() {
        CompanionSession session = new CompanionSession(
                "session-id",
                "appointment-id",
                "manager-id",
                4,
                SessionStatus.IN_TREATMENT,
                "진료실 입장 안내",
                "",
                "",
                "",
                "",
                false
        );
        assertEquals(
                "진료실 입장 안내",
                GuardianFinalReportPolicy.resolveManagerMessage(session)
        );
        assertFalse(GuardianFinalReportPolicy.hasCompletionJournal(session));

        session.applyCompletionState(1L, "안전하게 인계 완료", "READY", 1, "", 2L, null);
        assertEquals(
                "안전하게 인계 완료",
                GuardianFinalReportPolicy.resolveManagerMessage(session)
        );
        assertTrue(GuardianFinalReportPolicy.hasCompletionJournal(session));
        assertNull(GuardianFinalReportPolicy.resolveManagerMessage(null));
    }
}
