package com.example.bodeul.data.coreapi;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.fail;

import com.example.bodeul.domain.model.BookingPaymentMethod;
import com.example.bodeul.domain.model.BookingPriceSummary;
import com.example.bodeul.domain.model.BookingRequestDraft;

import org.json.JSONObject;
import org.junit.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class CoreApiAppointmentClientTest {
    private static final String UPDATE_MESSAGE = "예약 요금 업데이트를 준비 중입니다.";

    @Test
    public void createBodySendsTheDisplayedAmountInsteadOfReplacingItWithServerPrice() throws Exception {
        for (int amount : new int[]{0, 40_000, 69_000, 96_000}) {
            JSONObject body = CoreApiAppointmentClient.buildCreateDraftBody(draft(amount));
            assertEquals("mvp-fixed-40000-v1", body.getString("pricePolicyVersion"));
            assertEquals(amount, body.getInt("expectedFinalPrice"));
        }
    }

    @Test
    public void editBodyDoesNotImposeTheNewPriceContractOnLegacyAppointments() throws Exception {
        JSONObject body = CoreApiAppointmentClient.buildDraftBody(draft(96_000));
        assertFalse(body.has("pricePolicyVersion"));
        assertFalse(body.has("expectedFinalPrice"));
    }

    @Test
    public void createUsesOnlyThePriceConfirmedPath() throws Exception {
        JSONObject body = requestBody();
        JSONObject expected = new JSONObject().put("id", UUID.randomUUID().toString());
        JSONObject response = CoreApiAppointmentClient.requestCreateWithRetry(body, (path, payload) -> {
            assertEquals("/api/appointments/price-confirmed", path);
            assertSame(body, payload);
            return expected;
        }, UPDATE_MESSAGE);
        assertSame(expected, response);
    }

    @Test
    public void transportRetryKeepsTheSamePathBodyAndRequestId() throws Exception {
        JSONObject body = requestBody();
        List<String> paths = new ArrayList<>();
        List<JSONObject> attempts = new ArrayList<>();
        CoreApiAppointmentClient.requestCreateWithRetry(body, (path, payload) -> {
            paths.add(path);
            attempts.add(payload);
            if (attempts.size() == 1) {
                throw new IOException("응답 유실");
            }
            return new JSONObject();
        }, UPDATE_MESSAGE);

        assertEquals(List.of("/api/appointments/price-confirmed", "/api/appointments/price-confirmed"), paths);
        assertSame(body, attempts.get(0));
        assertSame(body, attempts.get(1));
        assertEquals(body.getString("clientRequestId"), attempts.get(1).getString("clientRequestId"));
        assertEquals(40_000, attempts.get(1).getInt("expectedFinalPrice"));
    }

    @Test
    public void oldServerNeverFallsBackToUnconfirmedCreate() throws Exception {
        for (int status : new int[]{404, 405}) {
            List<String> paths = new ArrayList<>();
            try {
                CoreApiAppointmentClient.requestCreateWithRetry(requestBody(), (path, body) -> {
                    paths.add(path);
                    throw new CoreApiAuthenticatedClient.ApiException(status, "이전 서버 오류");
                }, UPDATE_MESSAGE);
                fail("구형 서버에서는 신규 예약을 중단해야 합니다.");
            } catch (CoreApiAuthenticatedClient.ApiException expected) {
                assertEquals(status, expected.getStatusCode());
                assertEquals(UPDATE_MESSAGE, expected.getUserMessage());
            }
            assertEquals(List.of("/api/appointments/price-confirmed"), paths);
        }
    }

    @Test
    public void priceConflictAndAuthorizationFailuresAreNotRetriedOrReplaced() throws Exception {
        for (int status : new int[]{401, 403, 409, 503}) {
            List<String> paths = new ArrayList<>();
            CoreApiAuthenticatedClient.ApiException failure =
                    new CoreApiAuthenticatedClient.ApiException(status, "서버 안내");
            try {
                CoreApiAppointmentClient.requestCreateWithRetry(requestBody(), (path, body) -> {
                    paths.add(path);
                    throw failure;
                }, UPDATE_MESSAGE);
                fail("오류를 사용자에게 전달해야 합니다.");
            } catch (CoreApiAuthenticatedClient.ApiException expected) {
                assertSame(failure, expected);
            }
            assertEquals(1, paths.size());
        }
    }

    @Test
    public void rollbackDuringTransportRetryStillDoesNotUseTheLegacyPath() throws Exception {
        List<String> paths = new ArrayList<>();
        try {
            CoreApiAppointmentClient.requestCreateWithRetry(requestBody(), (path, body) -> {
                paths.add(path);
                if (paths.size() == 1) {
                    throw new IOException("첫 응답 유실");
                }
                throw new CoreApiAuthenticatedClient.ApiException(404, "서버 롤백");
            }, UPDATE_MESSAGE);
            fail("롤백 중에는 구형 생성 경로로 재시도하지 않아야 합니다.");
        } catch (CoreApiAuthenticatedClient.ApiException expected) {
            assertEquals(UPDATE_MESSAGE, expected.getUserMessage());
        }
        assertEquals(List.of("/api/appointments/price-confirmed", "/api/appointments/price-confirmed"), paths);
    }

    private static BookingRequestDraft draft(int amount) {
        return BookingRequestDraft.builder()
                .paymentMethod(BookingPaymentMethod.BANK_TRANSFER)
                .priceSummary(new BookingPriceSummary(amount, 0, 0, amount))
                .build();
    }

    private static JSONObject requestBody() throws Exception {
        return CoreApiAppointmentClient.buildCreateDraftBody(draft(40_000))
                .put("clientRequestId", UUID.randomUUID().toString());
    }
}
