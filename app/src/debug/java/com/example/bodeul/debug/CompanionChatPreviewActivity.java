package com.example.bodeul.debug;

import com.example.bodeul.data.*;
import com.example.bodeul.data.realtime.CompanionRealtimeSubscriber;
import com.example.bodeul.ui.chat.CompanionChatActivity;

/** 실제 인증·서버 대신 계측 테스트가 제공한 메모리 저장소만 사용한다. */
public final class CompanionChatPreviewActivity extends CompanionChatActivity {
    static android.content.Intent lastNavigation;
    @Override public void startActivity(android.content.Intent intent) { lastNavigation = intent; }
    static AuthRepository auth;
    static BookingRepository bookings;
    static ManagerRepository managers;
    static CompanionChatAttachmentUploader uploader;
    static CompanionRealtimeSubscriber realtime;
    static CompanionChatAttachmentPreviewResolver resolver;

    private <T> T require(T value) {
        if (value == null) throw new IllegalStateException("테스트 저장소가 필요합니다.");
        return value;
    }
    @Override protected AuthRepository provideAuthRepository() { return require(auth); }
    @Override protected BookingRepository provideBookingRepository() { return require(bookings); }
    @Override protected ManagerRepository provideManagerRepository() { return require(managers); }
    @Override protected CompanionChatAttachmentUploader provideAttachmentUploader() { return require(uploader); }
    @Override protected CompanionRealtimeSubscriber provideRealtimeSubscriber() { return require(realtime); }
    @Override protected CompanionChatAttachmentPreviewResolver provideAttachmentPreviewResolver() { return require(resolver); }
}
