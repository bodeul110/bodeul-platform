package com.example.bodeul.debug;

import android.content.Intent;
import com.example.bodeul.data.AuthRepository;
import com.example.bodeul.data.BookingRepository;
import com.example.bodeul.data.ManagerRepository;
import com.example.bodeul.ui.booking.ClientProgressActivity;
import com.example.bodeul.ui.chat.ChatRoomsActivity;
import com.example.bodeul.ui.profile.PaymentInfoActivity;
import com.example.bodeul.ui.profile.ProfileInfoActivity;

/** 계측 전용 진입점. 운영 인증·예약 서비스에 연결하지 않고 실제 화면의 이동 결과를 관찰한다. */
public final class BatchPreviewActivities {
    static BookingRepository bookings;
    static ManagerRepository managers;
    private BatchPreviewActivities() {}
    public static final class Info extends ProfileInfoActivity {
        protected AuthRepository provideAuthRepository() { return ProfilePreviewDependencies.requireAuth(); }
        public void startActivity(Intent i) { ProfilePreviewDependencies.lastNavigation = i; }
    }
    public static final class Payment extends PaymentInfoActivity {
        protected AuthRepository provideAuthRepository() { return ProfilePreviewDependencies.requireAuth(); }
        protected BookingRepository bookings() { return bookings; }
        public void startActivity(Intent i) { ProfilePreviewDependencies.lastNavigation = i; }
    }
    public static final class Rooms extends ChatRoomsActivity {
        protected AuthRepository provideAuthRepository() { return ProfilePreviewDependencies.requireAuth(); }
        protected BookingRepository bookings() { return bookings; }
        protected ManagerRepository managers() { return managers; }
        public void startActivity(Intent i) { ProfilePreviewDependencies.lastNavigation = i; }
    }
    public static final class Progress extends ClientProgressActivity {
        protected AuthRepository provideAuthRepository() { return ProfilePreviewDependencies.requireAuth(); }
        protected BookingRepository bookings() { return bookings; }
        public void startActivity(Intent i) { ProfilePreviewDependencies.lastNavigation = i; }
    }
}
