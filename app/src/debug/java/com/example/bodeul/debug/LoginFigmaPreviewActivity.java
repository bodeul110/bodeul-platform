package com.example.bodeul.debug;

import android.app.Activity;

import com.example.bodeul.data.AuthRepository;
import com.example.bodeul.data.RepositoryCallback;
import com.example.bodeul.domain.model.User;
import com.example.bodeul.domain.model.UserRole;
import com.example.bodeul.ui.auth.LoginActivity;

/** 실제 인증·알림·서버 호출 없이 로그인 화면과 요청 인자를 검증한다. */
public class LoginFigmaPreviewActivity extends LoginActivity {
    final RecordingAuthRepository repository = new RecordingAuthRepository();
    User authenticatedUser;

    @Override protected AuthRepository provideAuthRepository() { return repository; }
    @Override protected void onAuthenticated(User user) { authenticatedUser = user; }

    static final class RecordingAuthRepository implements AuthRepository {
        int calls;
        String action;
        String email;
        String password;
        String name;
        String phone;
        UserRole role;
        boolean signedOut;
        RepositoryCallback<User> pending;

        private void record(String action, UserRole role, RepositoryCallback<User> callback) {
            calls++;
            this.action = action;
            this.role = role;
            pending = callback;
        }

        @Override public void getCurrentUser(RepositoryCallback<User> callback) {
            callback.onSuccess(null);
        }
        @Override public void signIn(String email, String password, UserRole role,
                                     RepositoryCallback<User> callback) {
            this.email = email;
            this.password = password;
            record("email", role, callback);
        }
        @Override public void signInWithGoogle(Activity activity, UserRole role,
                                               RepositoryCallback<User> callback) {
            record("google", role, callback);
        }
        @Override public void signInWithKakao(Activity activity, UserRole role,
                                              RepositoryCallback<User> callback) {
            record("kakao", role, callback);
        }
        @Override public void signInWithNaver(Activity activity, UserRole role,
                                              RepositoryCallback<User> callback) {
            record("naver", role, callback);
        }
        @Override public void resendVerificationEmail(String email, String password,
                                                       RepositoryCallback<Void> callback) {
            calls++;
            action = "resend";
            this.email = email;
            this.password = password;
            callback.onSuccess(null);
        }
        @Override public void updateCurrentUserProfile(String name, String phone,
                                                       RepositoryCallback<User> callback) {
            throw new AssertionError("로그인 화면에서 프로필 수정은 호출하지 않는다.");
        }
        @Override public void register(String name, String email, String phone, String password,
                                        UserRole role, RepositoryCallback<User> callback) {
            this.name = name;
            this.email = email;
            this.phone = phone;
            this.password = password;
            record("register", role, callback);
        }
        @Override public void resetPassword(String email, RepositoryCallback<Void> callback) {
            calls++;
            action = "reset";
            this.email = email;
            callback.onSuccess(null);
        }
        @Override public void signOut() { signedOut = true; }

        // 화면의 실제 인증 모드만 재현하며 Firebase 인스턴스는 만들지 않는다.
        @Override public boolean isFirebaseBacked() { return true; }

        void succeed() {
            RepositoryCallback<User> callback = pending;
            pending = null;
            callback.onSuccess(new User("synthetic-login", role, "합성 사용자", email, phone));
        }
        void fail() {
            RepositoryCallback<User> callback = pending;
            pending = null;
            callback.onError("합성 인증 오류입니다.");
        }
    }
}
