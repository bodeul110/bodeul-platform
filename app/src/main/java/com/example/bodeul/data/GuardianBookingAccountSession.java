package com.example.bodeul.data;

/** 화면 생명주기 동안 로그인 변경을 관찰하고 화면에 인증 SDK를 노출하지 않는다. */
public interface GuardianBookingAccountSession {
    String currentAccountId();
    void observe(Runnable changed);
    void stopObserving();
}
