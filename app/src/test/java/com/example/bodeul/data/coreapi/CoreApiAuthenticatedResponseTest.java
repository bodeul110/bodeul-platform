package com.example.bodeul.data.coreapi;

import static org.junit.Assert.*;

import org.json.JSONException;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

public class CoreApiAuthenticatedResponseTest {
    @Test
    public void arrayResponse_preservesUtf8AndClosesStream() throws Exception {
        Connection connection = new Connection(200, "[{\"name\":\"합성 환자\"}]");
        assertEquals("합성 환자", CoreApiAuthenticatedClient.readJsonArrayResponse(connection)
                .getJSONObject(0).getString("name"));
        assertTrue(connection.closed);
    }

    @Test
    public void objectResponse_preservesExistingContract() throws Exception {
        Connection connection = new Connection(200, "{\"version\":1}");
        assertEquals(1, CoreApiAuthenticatedClient.readJsonResponse(connection).getInt("version"));
        assertTrue(connection.closed);
    }

    @Test
    public void responseTypeMismatchDoesNotBecomeEmptyArrayOrObject() throws Exception {
        for (String body : new String[]{"{}", "null", "", "<html>실패</html>"}) {
            assertThrows(JSONException.class, () -> CoreApiAuthenticatedClient.readJsonArrayResponse(
                    new Connection(200, body)));
        }
        assertThrows(JSONException.class, () -> CoreApiAuthenticatedClient.readJsonResponse(new Connection(200, "[]")));
        assertEquals(0, CoreApiAuthenticatedClient.readJsonArrayResponse(new Connection(200, "[]")).length());
    }

    @Test
    public void errorStatusPreservesTypedFailureInsteadOfParsingAsList() throws Exception {
        for (int status : new int[]{401, 403, 404, 409, 429, 503}) {
            Connection connection = new Connection(status, "{\"message\":\"합성 오류\"}");
            CoreApiAuthenticatedClient.ApiException error = assertThrows(CoreApiAuthenticatedClient.ApiException.class,
                    () -> CoreApiAuthenticatedClient.readJsonArrayResponse(connection));
            assertEquals(status, error.getStatusCode());
            assertEquals("합성 오류", error.getUserMessage());
            assertTrue(connection.closed);
        }
    }

    @Test
    public void nonJsonAndMissingErrorStreamStillFailWithHttpStatus() throws Exception {
        CoreApiAuthenticatedClient.ApiException error = assertThrows(CoreApiAuthenticatedClient.ApiException.class,
                () -> CoreApiAuthenticatedClient.readJsonArrayResponse(new Connection(503, "gateway failure")));
        assertEquals(503, error.getStatusCode());
        assertFalse(error.getUserMessage().contains("gateway"));
        error = assertThrows(CoreApiAuthenticatedClient.ApiException.class,
                () -> CoreApiAuthenticatedClient.readJsonArrayResponse(new Connection(401, null)));
        assertEquals(401, error.getStatusCode());
    }

    @Test
    public void arraysShareOneMiBResponseLimitWithObjects() throws Exception {
        String oversized = "[\"" + "x".repeat(1024 * 1024) + "\"]";
        Connection connection = new Connection(200, oversized);
        assertThrows(IOException.class, () -> CoreApiAuthenticatedClient.readJsonArrayResponse(connection));
        assertTrue(connection.closed);
        assertThrows(IOException.class, () -> CoreApiAuthenticatedClient.readJsonResponse(
                new Connection(200, "{\"value\":\"" + "x".repeat(1024 * 1024) + "\"}")));
    }

    private static final class Connection extends HttpURLConnection {
        private final int status;
        private final String body;
        boolean closed;

        Connection(int status, String body) throws Exception {
            super(new URL("https://example.invalid"));
            this.status = status;
            this.body = body;
        }

        @Override public int getResponseCode() { return status; }
        @Override public InputStream getInputStream() { return stream(); }
        @Override public InputStream getErrorStream() { return stream(); }
        @Override public void disconnect() { }
        @Override public boolean usingProxy() { return false; }
        @Override public void connect() { }

        private InputStream stream() {
            if (body == null) return null;
            return new ByteArrayInputStream(body.getBytes(StandardCharsets.UTF_8)) {
                @Override public void close() throws IOException { closed = true; super.close(); }
            };
        }
    }
}
