package com.bodeul.core.account;

import java.sql.ResultSet;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataRetrievalFailureException;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class JdbcAccountDeletionImpactRepositoryTests {

    private static final UUID USER_ID = UUID.fromString("8975eeec-85d4-4625-a887-8cd5f42cd6da");

    private NamedParameterJdbcTemplate jdbcTemplate;
    private JdbcAccountDeletionImpactRepository repository;

    @BeforeEach
    void setUp() {
        jdbcTemplate = mock(NamedParameterJdbcTemplate.class);
        repository = new JdbcAccountDeletionImpactRepository(jdbcTemplate);
    }

    @Test
    @SuppressWarnings("unchecked")
    void inspectCallsAggregateOnlyFunctionAndMapsEveryCount() throws Exception {
        ResultSet resultSet = mock(ResultSet.class);
        stubCounts(resultSet);
        stubQuery(resultSet);

        AccountDeletionImpactRepository.PostgreSqlImpact impact = repository.inspect(USER_ID);

        assertThat(impact.profileCount()).isEqualTo(1L);
        assertThat(impact.activeAppointmentCount()).isEqualTo(3L);
        assertThat(impact.assignmentAuditCount()).isEqualTo(8L);
        assertThat(impact.relatedChatAttachmentCount()).isEqualTo(11L);
        assertThat(impact.activeLegalHoldCount()).isEqualTo(14L);
        assertThat(impact.bankTransferPaymentCount()).isEqualTo(15L);
        assertThat(impact.paymentEventCount()).isEqualTo(16L);

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<MapSqlParameterSource> parameters = ArgumentCaptor.forClass(MapSqlParameterSource.class);
        verify(jdbcTemplate).queryForObject(
                sql.capture(),
                parameters.capture(),
                any(RowMapper.class));
        assertThat(sql.getValue())
                .contains("bodeul.account_deletion_postgres_inventory(:userId)")
                .doesNotContain("appointment_requests")
                .doesNotContain("companion_session_assignment_audits");
        assertThat(parameters.getValue().getValue("userId")).isEqualTo(USER_ID);
    }

    @Test
    void missingBookingTablesProduceUnknownInventory() {
        when(jdbcTemplate.queryForObject(anyString(), any(MapSqlParameterSource.class), eq(Boolean.class))).thenReturn(false);
        assertThat(repository.inspectBookingApprovals(USER_ID)).isEmpty();
        verify(jdbcTemplate, never()).queryForObject(anyString(), any(MapSqlParameterSource.class), any(RowMapper.class));
    }

    @Test
    void bookingCountsAreScopedToPatientOrGuardianAndExcludeExpiredApprovals() throws Exception {
        when(jdbcTemplate.queryForObject(anyString(), any(MapSqlParameterSource.class), eq(Boolean.class))).thenReturn(true);
        ResultSet resultSet = mock(ResultSet.class);
        when(resultSet.getObject("approval_count", Long.class)).thenReturn(2L);
        when(resultSet.getObject("active_approval_count", Long.class)).thenReturn(1L);
        when(resultSet.getObject("audit_count", Long.class)).thenReturn(3L);
        stubQuery(resultSet);
        assertThat(repository.inspectBookingApprovals(USER_ID))
                .contains(new AccountDeletionImpactRepository.BookingApprovalImpact(2, 1, 3));
        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<MapSqlParameterSource> parameters = ArgumentCaptor.forClass(MapSqlParameterSource.class);
        verify(jdbcTemplate).queryForObject(sql.capture(), parameters.capture(), any(RowMapper.class));
        assertThat(sql.getValue()).contains("patient_user_id = :userId or guardian_user_id = :userId",
                "revoked_at is null", "expires_at > now()", "guardian_booking_approval_events");
        assertThat(parameters.getValue().getValue("userId")).isEqualTo(USER_ID);
    }

    @Test
    void bookingNullCountFailsClosed() throws Exception {
        when(jdbcTemplate.queryForObject(anyString(), any(MapSqlParameterSource.class), eq(Boolean.class))).thenReturn(true);
        stubQuery(mock(ResultSet.class));
        assertThatThrownBy(() -> repository.inspectBookingApprovals(USER_ID)).isInstanceOf(DataRetrievalFailureException.class);
    }

    @Test
    void inspectRejectsNullCountInsteadOfTreatingItAsZero() throws Exception {
        ResultSet resultSet = mock(ResultSet.class);
        when(resultSet.getObject("profile_count", Long.class)).thenReturn(null);
        stubQuery(resultSet);

        assertThatThrownBy(() -> repository.inspect(USER_ID))
                .isInstanceOf(DataRetrievalFailureException.class)
                .hasMessageContaining("profile_count");
    }

    @Test
    void inspectRejectsNegativeCount() throws Exception {
        ResultSet resultSet = mock(ResultSet.class);
        when(resultSet.getObject("profile_count", Long.class)).thenReturn(1L);
        when(resultSet.getObject("appointment_count", Long.class)).thenReturn(-1L);
        stubQuery(resultSet);

        assertThatThrownBy(() -> repository.inspect(USER_ID))
                .isInstanceOf(DataRetrievalFailureException.class)
                .hasMessageContaining("appointment_count");
    }

    private void stubCounts(ResultSet resultSet) throws Exception {
        when(resultSet.getObject("profile_count", Long.class)).thenReturn(1L);
        when(resultSet.getObject("appointment_count", Long.class)).thenReturn(2L);
        when(resultSet.getObject("active_appointment_count", Long.class)).thenReturn(3L);
        when(resultSet.getObject("companion_session_count", Long.class)).thenReturn(4L);
        when(resultSet.getObject("active_companion_session_count", Long.class)).thenReturn(5L);
        when(resultSet.getObject("session_report_count", Long.class)).thenReturn(6L);
        when(resultSet.getObject("appointment_follow_up_count", Long.class)).thenReturn(7L);
        when(resultSet.getObject("assignment_audit_count", Long.class)).thenReturn(8L);
        when(resultSet.getObject("related_chat_message_count", Long.class)).thenReturn(9L);
        when(resultSet.getObject("sent_chat_message_count", Long.class)).thenReturn(10L);
        when(resultSet.getObject("related_chat_attachment_count", Long.class)).thenReturn(11L);
        when(resultSet.getObject("related_chat_read_receipt_count", Long.class)).thenReturn(12L);
        when(resultSet.getObject("related_location_count", Long.class)).thenReturn(13L);
        when(resultSet.getObject("active_legal_hold_count", Long.class)).thenReturn(14L);
        when(resultSet.getObject("bank_transfer_payment_count", Long.class)).thenReturn(15L);
        when(resultSet.getObject("payment_event_count", Long.class)).thenReturn(16L);
    }

    @SuppressWarnings("unchecked")
    private void stubQuery(ResultSet resultSet) {
        when(jdbcTemplate.queryForObject(
                anyString(),
                any(MapSqlParameterSource.class),
                any(RowMapper.class)))
                .thenAnswer(invocation -> {
                    RowMapper<?> mapper = invocation.getArgument(2);
                    return mapper.mapRow(resultSet, 0);
                });
    }
}
