package com.example.bodeul.data.realtime;

import android.content.Context;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.bodeul.R;
import com.example.bodeul.data.firebase.FirebaseSupport;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;

/**
 * Supabase private Broadcast는 변경 신호만 전달하고 실제 상태는 Core API에서 다시 읽게 한다.
 */
public final class SupabaseCompanionRealtimeSubscriber implements CompanionRealtimeSubscriber {
    private static final long HEARTBEAT_INTERVAL_MILLIS = 25_000L;
    private static final long TOKEN_RECONNECT_INTERVAL_MILLIS = 45L * 60L * 1_000L;
    private static final long EVENT_DEBOUNCE_MILLIS = 300L;
    private static final long MAX_RECONNECT_DELAY_MILLIS = 30_000L;
    private static final OkHttpClient CLIENT = new OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(0, TimeUnit.MILLISECONDS)
            .build();

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final String realtimeWebSocketUrl;
    private final boolean firebaseConfigured;
    private final AtomicLong referenceSequence = new AtomicLong(1L);

    @Nullable
    private WebSocket webSocket;
    @Nullable
    private Runnable onChanged;
    private String topic = "";
    private String joinReference = "";
    private boolean stopped = true;
    private int reconnectAttempt;

    public SupabaseCompanionRealtimeSubscriber(Context context) {
        Context appContext = context.getApplicationContext();
        realtimeWebSocketUrl = buildWebSocketUrl(
                appContext.getString(R.string.bodeul_supabase_url),
                appContext.getString(R.string.bodeul_supabase_publishable_key));
        firebaseConfigured = FirebaseSupport.isConfigured(appContext);
    }

    @Override
    public void subscribe(String companionSessionId, Runnable changedCallback) {
        stop();
        topic = "companion-session:" + normalize(companionSessionId);
        onChanged = changedCallback;
        stopped = !hasAuthenticatedRealtimeConfiguration(
                realtimeWebSocketUrl,
                firebaseConfigured)
                || topic.endsWith(":");
        if (!stopped) {
            connect();
        }
    }

    @Override
    public void stop() {
        stopped = true;
        mainHandler.removeCallbacksAndMessages(this);
        Runnable callback = onChanged;
        if (callback != null) {
            mainHandler.removeCallbacks(callback);
        }
        WebSocket current = webSocket;
        webSocket = null;
        if (current != null) {
            current.close(1000, "화면 구독 종료");
        }
        onChanged = null;
        topic = "";
        reconnectAttempt = 0;
    }

    private void connect() {
        if (!hasAuthenticatedRealtimeConfiguration(realtimeWebSocketUrl, firebaseConfigured)) {
            stopped = true;
            return;
        }
        FirebaseUser user;
        try {
            user = FirebaseAuth.getInstance().getCurrentUser();
        } catch (IllegalStateException exception) {
            stopped = true;
            return;
        }
        if (stopped || user == null) {
            scheduleReconnect();
            return;
        }
        user.getIdToken(true).addOnCompleteListener(task -> {
            if (stopped) {
                return;
            }
            String token = task.isSuccessful() && task.getResult() != null
                    ? normalize(task.getResult().getToken())
                    : "";
            if (token.isEmpty()) {
                scheduleReconnect();
                return;
            }
            Request request = new Request.Builder().url(realtimeWebSocketUrl).build();
            webSocket = CLIENT.newWebSocket(request, new Listener(token));
        });
    }

    static boolean hasAuthenticatedRealtimeConfiguration(
            @Nullable String realtimeWebSocketUrl,
            boolean firebaseConfigured
    ) {
        return firebaseConfigured
                && realtimeWebSocketUrl != null
                && !realtimeWebSocketUrl.trim().isEmpty();
    }

    private void scheduleReconnect() {
        if (stopped) {
            return;
        }
        long exponent = Math.min(reconnectAttempt++, 5);
        long delay = Math.min(1_000L << exponent, MAX_RECONNECT_DELAY_MILLIS);
        mainHandler.removeCallbacksAndMessages(this);
        mainHandler.postAtTime(this::connect, this, android.os.SystemClock.uptimeMillis() + delay);
    }

    private void scheduleHeartbeat() {
        if (stopped) {
            return;
        }
        mainHandler.postAtTime(() -> {
            WebSocket current = webSocket;
            if (current == null) {
                return;
            }
            try {
                current.send(SupabaseRealtimeProtocol.heartbeat(nextReference()));
                scheduleHeartbeat();
            } catch (Exception exception) {
                current.cancel();
            }
        }, this, android.os.SystemClock.uptimeMillis() + HEARTBEAT_INTERVAL_MILLIS);
    }

    private void scheduleTokenReconnect() {
        mainHandler.postAtTime(() -> {
            WebSocket current = webSocket;
            if (current != null) {
                current.close(1000, "인증 갱신");
            }
        }, this, android.os.SystemClock.uptimeMillis() + TOKEN_RECONNECT_INTERVAL_MILLIS);
    }

    private void notifyChanged() {
        Runnable callback = onChanged;
        if (stopped || callback == null) {
            return;
        }
        mainHandler.removeCallbacks(callback);
        mainHandler.postDelayed(callback, EVENT_DEBOUNCE_MILLIS);
    }

    private String nextReference() {
        return Long.toString(referenceSequence.getAndIncrement());
    }

    private static String buildWebSocketUrl(String baseUrl, String publishableKey) {
        String normalizedUrl = normalize(baseUrl);
        String normalizedKey = normalize(publishableKey);
        if (normalizedUrl.isEmpty() || normalizedKey.isEmpty()) {
            return "";
        }
        while (normalizedUrl.endsWith("/")) {
            normalizedUrl = normalizedUrl.substring(0, normalizedUrl.length() - 1);
        }
        if (normalizedUrl.startsWith("https://")) {
            normalizedUrl = "wss://" + normalizedUrl.substring("https://".length());
        } else if (normalizedUrl.startsWith("http://")) {
            normalizedUrl = "ws://" + normalizedUrl.substring("http://".length());
        }
        return normalizedUrl + "/realtime/v1/websocket?apikey="
                + Uri.encode(normalizedKey) + "&vsn=1.0.0";
    }

    private static String normalize(@Nullable String value) {
        return value == null ? "" : value.trim();
    }

    private final class Listener extends WebSocketListener {
        private final String accessToken;

        private Listener(String accessToken) {
            this.accessToken = accessToken;
        }

        @Override
        public void onOpen(@NonNull WebSocket socket, @NonNull Response response) {
            if (stopped || socket != webSocket) {
                socket.close(1000, "사용하지 않는 연결");
                return;
            }
            joinReference = nextReference();
            try {
                socket.send(SupabaseRealtimeProtocol.join(topic, accessToken, joinReference));
            } catch (Exception exception) {
                socket.cancel();
            }
        }

        @Override
        public void onMessage(@NonNull WebSocket socket, @NonNull String text) {
            if (stopped || socket != webSocket) {
                return;
            }
            if (SupabaseRealtimeProtocol.isSuccessfulJoin(text, joinReference)) {
                reconnectAttempt = 0;
                mainHandler.removeCallbacksAndMessages(SupabaseCompanionRealtimeSubscriber.this);
                scheduleHeartbeat();
                scheduleTokenReconnect();
                notifyChanged();
                return;
            }
            if (SupabaseRealtimeProtocol.isJoinReply(text, joinReference)) {
                socket.close(1008, "채널 인가 갱신");
                return;
            }
            if (SupabaseRealtimeProtocol.isStateEvent(text, topic)) {
                notifyChanged();
            }
        }

        @Override
        public void onClosing(@NonNull WebSocket socket, int code, @NonNull String reason) {
            socket.close(code, reason);
        }

        @Override
        public void onClosed(@NonNull WebSocket socket, int code, @NonNull String reason) {
            if (socket == webSocket) {
                webSocket = null;
                scheduleReconnect();
            }
        }

        @Override
        public void onFailure(
                @NonNull WebSocket socket,
                @NonNull Throwable throwable,
                @Nullable Response response
        ) {
            if (socket == webSocket) {
                webSocket = null;
                scheduleReconnect();
            }
        }
    }
}
