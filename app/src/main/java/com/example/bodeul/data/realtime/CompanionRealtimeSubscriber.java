package com.example.bodeul.data.realtime;

/**
 * 동행 세션 변경 신호를 구독하는 최소 계약이다.
 */
public interface CompanionRealtimeSubscriber {
    void subscribe(String companionSessionId, Runnable changedCallback);

    void stop();
}
