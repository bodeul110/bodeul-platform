package com.example.bodeul.data.firebase;

import android.app.Activity;
import android.content.Context;
import android.os.CancellationSignal;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.credentials.ClearCredentialStateRequest;
import androidx.credentials.Credential;
import androidx.credentials.CredentialManager;
import androidx.credentials.CredentialManagerCallback;
import androidx.credentials.CustomCredential;
import androidx.credentials.GetCredentialRequest;
import androidx.credentials.GetCredentialResponse;
import androidx.credentials.exceptions.ClearCredentialException;
import androidx.credentials.exceptions.GetCredentialException;
import androidx.credentials.exceptions.NoCredentialException;

import com.example.bodeul.R;
import com.example.bodeul.data.AuthRepository;
import com.example.bodeul.data.NotificationTokenRegistrar;
import com.example.bodeul.data.RepositoryCallback;
import com.example.bodeul.domain.model.User;
import com.example.bodeul.domain.model.AccountSignInInfo;
import com.example.bodeul.domain.model.UserRole;
import com.example.bodeul.util.SafeEnumParser;
import com.example.bodeul.util.UserProfileSanitizer;
import com.google.android.libraries.identity.googleid.GetGoogleIdOption;
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential;
import com.google.firebase.auth.EmailAuthProvider;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException;
import com.google.firebase.auth.FirebaseAuthInvalidUserException;
import com.google.firebase.auth.FirebaseAuthUserCollisionException;
import com.google.firebase.auth.FirebaseAuthWeakPasswordException;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.GoogleAuthProvider;
import com.google.firebase.auth.UserInfo;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Source;
import com.google.firebase.functions.FirebaseFunctions;
import com.google.firebase.functions.FirebaseFunctionsException;
import com.kakao.sdk.auth.model.OAuthToken;
import com.kakao.sdk.common.model.ClientError;
import com.kakao.sdk.common.model.ClientErrorCause;
import com.kakao.sdk.user.UserApiClient;
import com.navercorp.nid.NidOAuth;
import com.navercorp.nid.oauth.util.NidOAuthCallback;

import java.util.HashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.Executor;

import kotlin.Unit;
import kotlin.jvm.functions.Function2;

/**
 * Firebase Authentication과 Firestore를 이용해 로그인과 회원가입을 처리한다.
 */
public class FirebaseAuthRepository implements AuthRepository {
    private static final String FUNCTIONS_REGION = "asia-northeast3";
    private static final String PROVIDER_EMAIL = "EMAIL";
    private static final String PROVIDER_GOOGLE = "GOOGLE";
    private static final String PROVIDER_KAKAO = "KAKAO";
    private static final String PROVIDER_NAVER = "NAVER";

    private final Context appContext;
    private final FirebaseAuth firebaseAuth;
    private final FirebaseFirestore firestore;
    private final FirebaseFunctions functions;
    private final CredentialManager credentialManager;
    private final Executor mainExecutor;
    private final NotificationTokenRegistrar notificationTokenRegistrar;

    // 같은 세션 안에서는 중복 조회를 줄이기 위해 사용자 프로필을 캐시한다.
    @Nullable
    private User cachedUser;

    public FirebaseAuthRepository(
            Context appContext,
            FirebaseAuth firebaseAuth,
            FirebaseFirestore firestore,
            NotificationTokenRegistrar notificationTokenRegistrar
    ) {
        this.appContext = appContext;
        this.firebaseAuth = firebaseAuth;
        this.firestore = firestore;
        this.notificationTokenRegistrar = notificationTokenRegistrar;
        this.functions = FirebaseFunctions.getInstance(FUNCTIONS_REGION);
        this.credentialManager = CredentialManager.create(appContext);
        this.mainExecutor = ContextCompat.getMainExecutor(appContext);
        this.firebaseAuth.setLanguageCode("ko");
    }

    @Override
    public void getCurrentUser(RepositoryCallback<User> callback) {
        // 이미 읽어둔 프로필이 있으면 네트워크 호출 없이 바로 반환한다.
        if (cachedUser != null) {
            callback.onSuccess(cachedUser);
            return;
        }

        FirebaseUser currentUser = firebaseAuth.getCurrentUser();
        if (currentUser == null) {
            callback.onError("로그인이 필요합니다.");
            return;
        }

        // 이메일 로그인 사용자는 메일 인증 여부를 최신 상태로 다시 확인한다.
        currentUser.reload()
                .addOnSuccessListener(unused -> {
                    FirebaseUser reloadedUser = firebaseAuth.getCurrentUser();
                    if (reloadedUser == null) {
                        callback.onError("로그인이 필요합니다.");
                        return;
                    }
                    if (requiresEmailVerification(reloadedUser) && !reloadedUser.isEmailVerified()) {
                        signOut();
                        callback.onError("이메일 인증을 완료한 뒤 다시 로그인해주세요.");
                        return;
                    }
                    loadUserProfile(reloadedUser.getUid(), null, callback);
                })
                .addOnFailureListener(exception -> {
                    signOut();
                    callback.onError("로그인 상태를 확인하지 못했습니다.");
                });
    }

    @Override
    public void signIn(String email, String password, UserRole expectedRole, RepositoryCallback<User> callback) {
        // 인증 성공 후에는 Firestore의 사용자 프로필을 읽어 역할까지 검증한다.
        String normalizedEmail = UserProfileSanitizer.normalizeEmail(email);
        firebaseAuth.signInWithEmailAndPassword(normalizedEmail, password)
                .addOnSuccessListener(authResult -> {
                    FirebaseUser firebaseUser = authResult.getUser();
                    if (firebaseUser == null) {
                        callback.onError("로그인에 실패했습니다.");
                        return;
                    }
                    if (!firebaseUser.isEmailVerified()) {
                        firebaseUser.sendEmailVerification()
                                .addOnSuccessListener(unused -> {
                                    signOut();
                                    callback.onError("이메일 인증이 필요합니다. 인증 메일을 다시 보냈습니다.");
                                })
                                .addOnFailureListener(exception -> {
                                    signOut();
                                    callback.onError(resolveVerificationEmailMessage(exception, true));
                                });
                        return;
                    }
                    loadUserProfile(firebaseUser.getUid(), expectedRole, callback);
                })
                .addOnFailureListener(exception ->
                        callback.onError(resolveSignInMessage(exception)));
    }

    @Override
    public void signInWithGoogle(Activity activity, UserRole expectedRole, RepositoryCallback<User> callback) {
        String serverClientId = resolveGoogleServerClientId(activity);
        if (TextUtils.isEmpty(serverClientId)) {
            callback.onError("구글 로그인 설정이 아직 완료되지 않았습니다. Firebase 콘솔에서 Google 로그인을 활성화하고 google-services.json을 다시 받아주세요.");
            return;
        }

        requestGoogleCredential(activity, serverClientId, true, expectedRole, callback);
    }

    @Override
    public void signInWithKakao(Activity activity, UserRole expectedRole, RepositoryCallback<User> callback) {
        // 카카오 로그인은 SDK 로그인 후 Firebase Functions에서 custom token을 발급받는다.
        if (TextUtils.isEmpty(appContext.getString(R.string.kakao_native_app_key))) {
            callback.onError("카카오 로그인 설정이 아직 완료되지 않았습니다.");
            return;
        }

        startKakaoLogin(activity, expectedRole, callback, true);
    }

    @Override
    public void signInWithNaver(Activity activity, UserRole expectedRole, RepositoryCallback<User> callback) {
        // 네이버 로그인도 SDK 로그인 후 Firebase Functions에서 custom token을 발급받는다.
        if (!isNaverConfigured()) {
            callback.onError("네이버 로그인은 보안 설정 정비 중이라 잠시 사용할 수 없습니다.");
            return;
        }

        NidOAuth.INSTANCE.requestLogin(activity, new NidOAuthCallback() {
            @Override
            public void onSuccess() {
                String accessToken = NidOAuth.INSTANCE.getAccessToken();
                if (TextUtils.isEmpty(accessToken)) {
                    callback.onError("네이버 로그인 토큰을 확인하지 못했습니다.");
                    return;
                }
                requestNaverCustomToken(accessToken, expectedRole, callback);
            }

            @Override
            public void onFailure(String errorCode, String errorDesc) {
                callback.onError(resolveNaverRequestMessage(errorCode, errorDesc));
            }
        });
    }

    @Override
    public void resendVerificationEmail(String email, String password, RepositoryCallback<Void> callback) {
        // 인증 메일 재전송은 계정 본인 확인을 위해 이메일 비밀번호를 다시 검증한 뒤 수행한다.
        String normalizedEmail = UserProfileSanitizer.normalizeEmail(email);
        firebaseAuth.signInWithEmailAndPassword(normalizedEmail, password)
                .addOnSuccessListener(authResult -> {
                    FirebaseUser firebaseUser = authResult.getUser();
                    if (firebaseUser == null) {
                        callback.onError("계정 정보를 확인하지 못했습니다.");
                        return;
                    }
                    if (firebaseUser.isEmailVerified()) {
                        signOut();
                        callback.onError("이미 이메일 인증이 완료된 계정입니다.");
                        return;
                    }

                    firebaseUser.sendEmailVerification()
                            .addOnSuccessListener(unused -> {
                                signOut();
                                callback.onSuccess(null);
                            })
                            .addOnFailureListener(exception -> {
                                signOut();
                                callback.onError(resolveVerificationEmailMessage(exception, true));
                            });
                })
                .addOnFailureListener(exception ->
                        callback.onError(resolveSignInMessage(exception)));
    }

    @Override
    public void updateCurrentUserProfile(String name, String phone, RepositoryCallback<User> callback) {
        FirebaseUser firebaseUser = firebaseAuth.getCurrentUser();
        if (firebaseUser == null) {
            callback.onError("로그인이 필요합니다.");
            return;
        }

        String normalizedName = UserProfileSanitizer.normalizeName(name);
        String normalizedPhone = UserProfileSanitizer.normalizePhone(phone);

        firestore.collection("users")
                .document(firebaseUser.getUid())
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    User existingUser = toUser(documentSnapshot);
                    if (existingUser == null) {
                        callback.onError("users 컬렉션에서 계정 정보를 찾지 못했습니다.");
                        return;
                    }

                    Map<String, Object> updates = new HashMap<>();
                    updates.put("name", normalizedName);
                    updates.put("phone", normalizedPhone);

                    documentSnapshot.getReference()
                            .update(updates)
                            .addOnSuccessListener(unused -> {
                                User updatedUser = new User(
                                        existingUser.getId(),
                                        existingUser.getRole(),
                                        normalizedName,
                                        existingUser.getEmail(),
                                        normalizedPhone
                                );
                                cachedUser = updatedUser;
                                callback.onSuccess(updatedUser);
                            })
                            .addOnFailureListener(exception ->
                                    callback.onError("프로필 정보를 저장하지 못했습니다."));
                })
                .addOnFailureListener(exception ->
                        callback.onError("사용자 정보를 불러오지 못했습니다."));
    }

    @Override
    public void register(
            String name,
            String email,
            String phone,
            String password,
            UserRole role,
            RepositoryCallback<User> callback
    ) {
        // Auth 계정 생성과 Firestore 프로필 저장을 순서대로 처리한다.
        String normalizedName = UserProfileSanitizer.normalizeName(name);
        String normalizedEmail = UserProfileSanitizer.normalizeEmail(email);
        String normalizedPhone = UserProfileSanitizer.normalizePhone(phone);

        firebaseAuth.createUserWithEmailAndPassword(normalizedEmail, password)
                .addOnSuccessListener(authResult -> {
                    FirebaseUser firebaseUser = authResult.getUser();
                    if (firebaseUser == null) {
                        callback.onError("회원가입에 실패했습니다.");
                        return;
                    }

                    Map<String, Object> userDocument = new HashMap<>();
                    userDocument.put("name", normalizedName);
                    userDocument.put("email", normalizedEmail);
                    userDocument.put("phone", normalizedPhone);
                    userDocument.put("role", role.name());
                    userDocument.put("provider", PROVIDER_EMAIL);
                    userDocument.put("providerUserId", firebaseUser.getUid());

                    firestore.collection("users")
                            .document(firebaseUser.getUid())
                            .set(userDocument)
                            .addOnSuccessListener(unused -> {
                                User user = new User(
                                        firebaseUser.getUid(),
                                        role,
                                        normalizedName,
                                        normalizedEmail,
                                        normalizedPhone
                                );
                                cachedUser = user;

                                firebaseUser.sendEmailVerification()
                                        .addOnSuccessListener(ignored -> {
                                            signOut();
                                            callback.onSuccess(user);
                                        })
                                        .addOnFailureListener(exception -> {
                                            signOut();
                                            callback.onError(resolveVerificationEmailMessage(exception, false));
                                        });
                            })
                            .addOnFailureListener(exception -> {
                                firebaseUser.delete().addOnCompleteListener(task -> signOut());
                                callback.onError("사용자 프로필을 저장하지 못했습니다.");
                            });
                })
                .addOnFailureListener(exception ->
                        callback.onError(resolveRegisterMessage(exception)));
    }

    @Override
    public void getAccountSignInInfo(String expectedUserId, RepositoryCallback<AccountSignInInfo> callback) {
        FirebaseUser current = firebaseAuth.getCurrentUser();
        if (current == null || !current.getUid().equals(expectedUserId)) {
            callback.onError(appContext.getString(R.string.final_settings_account_changed));
            return;
        }
        current.reload().addOnSuccessListener(unused -> {
            FirebaseUser refreshed = firebaseAuth.getCurrentUser();
            if (refreshed == null || !refreshed.getUid().equals(expectedUserId)) {
                callback.onError(appContext.getString(R.string.final_settings_account_changed));
                return;
            }
            List<String> providers = new ArrayList<>();
            for (UserInfo info : refreshed.getProviderData()) providers.add(info.getProviderId());
            if (!expectedUserId.startsWith("kakao_") && !expectedUserId.startsWith("naver_")) {
                callback.onSuccess(new AccountSignInInfo(expectedUserId, refreshed.getEmail(), providers, null));
                return;
            }
            firestore.collection("users").document(expectedUserId).get(Source.SERVER)
                    .addOnSuccessListener(document -> {
                        FirebaseUser active = firebaseAuth.getCurrentUser();
                        if (active == null || !active.getUid().equals(expectedUserId) || !document.exists()) {
                            callback.onError(appContext.getString(R.string.final_settings_account_changed));
                            return;
                        }
                        callback.onSuccess(new AccountSignInInfo(expectedUserId,
                                TextUtils.isEmpty(active.getEmail()) ? document.getString("email") : active.getEmail(),
                                providers, document.getString("provider")));
                    })
                    .addOnFailureListener(error -> callback.onError(appContext.getString(R.string.final_settings_account_read_error)));
        }).addOnFailureListener(error -> callback.onError(appContext.getString(R.string.final_settings_account_read_error)));
    }

    @Override
    public void resetCurrentAccountPassword(String expectedUserId, String expectedEmail, RepositoryCallback<Void> callback) {
        getAccountSignInInfo(expectedUserId, new RepositoryCallback<AccountSignInInfo>() {
            @Override public void onSuccess(AccountSignInInfo info) {
                FirebaseUser active = firebaseAuth.getCurrentUser();
                if (active == null || !expectedUserId.equals(active.getUid())
                        || !info.matches(expectedUserId, expectedEmail) || !info.canResetPassword()) {
                    callback.onError(appContext.getString(R.string.final_settings_account_changed));
                    return;
                }
                resetPassword(info.getEmail(), callback);
            }
            @Override public void onError(String message) { callback.onError(message); }
        });
    }

    @Override
    public void resetPassword(String email, RepositoryCallback<Void> callback) {
        // Firebase가 제공하는 비밀번호 재설정 메일 발송 기능을 그대로 사용한다.
        firebaseAuth.sendPasswordResetEmail(UserProfileSanitizer.normalizeEmail(email))
                .addOnSuccessListener(unused -> callback.onSuccess(null))
                .addOnFailureListener(exception ->
                        callback.onError(resolveResetPasswordMessage(exception)));
    }

    @Override
    public void signOut() {
        // Firebase 세션과 구글 자격 증명 상태를 함께 정리한다.
        cachedUser = null;
        notificationTokenRegistrar.clearCurrentUserToken();
        firebaseAuth.signOut();
        logoutFromNaver();
        clearCredentialState();
    }

    @Override
    public boolean isFirebaseBacked() {
        return true;
    }

    private void startKakaoLogin(
            Activity activity,
            UserRole expectedRole,
            RepositoryCallback<User> callback,
            boolean preferTalk
    ) {
        if (preferTalk && UserApiClient.getInstance().isKakaoTalkLoginAvailable(activity)) {
            UserApiClient.getInstance().loginWithKakaoTalk(
                    activity,
                    createKakaoCallback(activity, expectedRole, callback, true)
            );
            return;
        }

        UserApiClient.getInstance().loginWithKakaoAccount(
                activity,
                createKakaoCallback(activity, expectedRole, callback, false)
        );
    }

    private Function2<OAuthToken, Throwable, Unit> createKakaoCallback(
            Activity activity,
            UserRole expectedRole,
            RepositoryCallback<User> callback,
            boolean fromKakaoTalk
    ) {
        return (token, error) -> {
            if (error != null) {
                if (fromKakaoTalk
                        && !(error instanceof ClientError
                        && ((ClientError) error).getReason() == ClientErrorCause.Cancelled)) {
                    startKakaoLogin(activity, expectedRole, callback, false);
                    return Unit.INSTANCE;
                }

                if (error instanceof ClientError
                        && ((ClientError) error).getReason() == ClientErrorCause.Cancelled) {
                    callback.onError("카카오 로그인 요청이 취소되었습니다.");
                    return Unit.INSTANCE;
                }

                callback.onError("카카오 로그인에 실패했습니다. 잠시 후 다시 시도해주세요.");
                return Unit.INSTANCE;
            }

            if (token == null || TextUtils.isEmpty(token.getAccessToken())) {
                callback.onError("카카오 로그인 토큰을 확인하지 못했습니다.");
                return Unit.INSTANCE;
            }

            requestKakaoCustomToken(token.getAccessToken(), expectedRole, callback);
            return Unit.INSTANCE;
        };
    }

    private void requestKakaoCustomToken(
            String accessToken,
            UserRole expectedRole,
            RepositoryCallback<User> callback
    ) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("accessToken", accessToken);
        payload.put("role", expectedRole.name());

        functions.getHttpsCallable("kakaoCustomToken")
                .call(payload)
                .addOnSuccessListener(result -> {
                    if (!(result.getData() instanceof Map)) {
                        callback.onError("카카오 로그인 응답을 해석하지 못했습니다.");
                        return;
                    }

                    Map<?, ?> data = (Map<?, ?>) result.getData();
                    String firebaseToken = asString(data.get("firebaseToken"));
                    SocialProfile socialProfile = toSocialProfile(PROVIDER_KAKAO, data.get("profile"));
                    if (TextUtils.isEmpty(firebaseToken) || socialProfile == null) {
                        callback.onError("카카오 로그인 응답을 해석하지 못했습니다.");
                        return;
                    }

                    signInWithCustomToken(firebaseToken, expectedRole, socialProfile, callback);
                })
                .addOnFailureListener(exception ->
                        callback.onError(resolveKakaoCallableMessage(exception)));
    }

    private void requestNaverCustomToken(
            String accessToken,
            UserRole expectedRole,
            RepositoryCallback<User> callback
    ) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("accessToken", accessToken);
        payload.put("role", expectedRole.name());

        functions.getHttpsCallable("naverCustomToken")
                .call(payload)
                .addOnSuccessListener(result -> {
                    if (!(result.getData() instanceof Map)) {
                        callback.onError("네이버 로그인 응답을 해석하지 못했습니다.");
                        return;
                    }

                    Map<?, ?> data = (Map<?, ?>) result.getData();
                    String firebaseToken = asString(data.get("firebaseToken"));
                    SocialProfile socialProfile = toSocialProfile(PROVIDER_NAVER, data.get("profile"));
                    if (TextUtils.isEmpty(firebaseToken) || socialProfile == null) {
                        callback.onError("네이버 로그인 응답을 해석하지 못했습니다.");
                        return;
                    }

                    signInWithCustomToken(firebaseToken, expectedRole, socialProfile, callback);
                })
                .addOnFailureListener(exception ->
                        callback.onError(resolveNaverCallableMessage(exception)));
    }

    private void signInWithCustomToken(
            String firebaseToken,
            UserRole expectedRole,
            SocialProfile socialProfile,
            RepositoryCallback<User> callback
    ) {
        firebaseAuth.signInWithCustomToken(firebaseToken)
                .addOnSuccessListener(authResult -> {
                    FirebaseUser firebaseUser = authResult.getUser();
                    if (firebaseUser == null) {
                        callback.onError("소셜 로그인에 실패했습니다.");
                        return;
                    }
                    syncSocialUserProfile(firebaseUser, expectedRole, socialProfile, callback);
                })
                .addOnFailureListener(exception ->
                        callback.onError("소셜 로그인에 실패했습니다. 잠시 후 다시 시도해주세요."));
    }

    private void requestGoogleCredential(
            Activity activity,
            String serverClientId,
            boolean filterByAuthorizedAccounts,
            UserRole expectedRole,
            RepositoryCallback<User> callback
    ) {
        GetGoogleIdOption googleIdOption = new GetGoogleIdOption.Builder()
                .setFilterByAuthorizedAccounts(filterByAuthorizedAccounts)
                .setServerClientId(serverClientId)
                .build();

        GetCredentialRequest request = new GetCredentialRequest.Builder()
                .addCredentialOption(googleIdOption)
                .build();

        // 먼저 기존에 승인된 계정을 우선 보여주고, 없으면 전체 계정 선택으로 한 번 더 시도한다.
        credentialManager.getCredentialAsync(
                activity,
                request,
                new CancellationSignal(),
                mainExecutor,
                new CredentialManagerCallback<GetCredentialResponse, GetCredentialException>() {
                    @Override
                    public void onResult(GetCredentialResponse result) {
                        handleGoogleCredential(result.getCredential(), expectedRole, callback);
                    }

                    @Override
                    public void onError(@NonNull GetCredentialException exception) {
                        if (filterByAuthorizedAccounts && exception instanceof NoCredentialException) {
                            requestGoogleCredential(activity, serverClientId, false, expectedRole, callback);
                            return;
                        }
                        callback.onError(resolveGoogleRequestMessage(exception));
                    }
                }
        );
    }

    private void handleGoogleCredential(
            Credential credential,
            UserRole expectedRole,
            RepositoryCallback<User> callback
    ) {
        // Credential Manager가 돌려준 값이 구글 ID 토큰인지 확인한 뒤 Firebase 인증으로 교환한다.
        if (!(credential instanceof CustomCredential)
                || !GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL.equals(credential.getType())) {
            callback.onError("구글 계정 인증 정보를 확인하지 못했습니다.");
            return;
        }

        CustomCredential customCredential = (CustomCredential) credential;
        try {
            GoogleIdTokenCredential googleIdTokenCredential =
                    GoogleIdTokenCredential.createFrom(customCredential.getData());
            firebaseAuthWithGoogle(googleIdTokenCredential.getIdToken(), expectedRole, callback);
        } catch (RuntimeException exception) {
            callback.onError("구글 계정 인증 정보를 해석하지 못했습니다.");
        }
    }

    private void firebaseAuthWithGoogle(
            String idToken,
            UserRole expectedRole,
            RepositoryCallback<User> callback
    ) {
        firebaseAuth.signInWithCredential(GoogleAuthProvider.getCredential(idToken, null))
                .addOnSuccessListener(authResult -> {
                    FirebaseUser firebaseUser = authResult.getUser();
                    if (firebaseUser == null) {
                        callback.onError("구글 로그인에 실패했습니다.");
                        return;
                    }

                    SocialProfile socialProfile = new SocialProfile(
                            PROVIDER_GOOGLE,
                            firebaseUser.getUid(),
                            UserProfileSanitizer.normalizeName(firebaseUser.getDisplayName()),
                            normalizeEmailOrFallback(firebaseUser.getEmail(), "google_" + firebaseUser.getUid()),
                            UserProfileSanitizer.normalizePhone(firebaseUser.getPhoneNumber())
                    );
                    syncSocialUserProfile(firebaseUser, expectedRole, socialProfile, callback);
                })
                .addOnFailureListener(exception ->
                        callback.onError(resolveGoogleFirebaseMessage(exception)));
    }

    private void syncSocialUserProfile(
            FirebaseUser firebaseUser,
            UserRole expectedRole,
            SocialProfile socialProfile,
            RepositoryCallback<User> callback
    ) {
        firestore.collection("users")
                .document(firebaseUser.getUid())
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (!documentSnapshot.exists()) {
                        createSocialUserProfile(firebaseUser, expectedRole, socialProfile, callback);
                        return;
                    }

                    User user = toUser(documentSnapshot);
                    if (user == null) {
                        signOut();
                        callback.onError("users 컬렉션의 계정 정보를 확인해주세요.");
                        return;
                    }

                    if (user.getRole() != expectedRole) {
                        signOut();
                        callback.onError("선택한 사용자 유형과 계정 유형이 일치하지 않습니다.");
                        return;
                    }

                    refreshExistingSocialUserProfile(documentSnapshot, user, socialProfile, callback);
                })
                .addOnFailureListener(exception ->
                        callback.onError("사용자 정보를 불러오지 못했습니다."));
    }

    private void createSocialUserProfile(
            FirebaseUser firebaseUser,
            UserRole expectedRole,
            SocialProfile socialProfile,
            RepositoryCallback<User> callback
    ) {
        // 같은 이메일로 다른 로그인 방식 계정이 있으면 Functions 중계로 중복 여부를 확인한다.
        Map<String, Object> payload = new HashMap<>();
        payload.put("email", socialProfile.email);

        functions.getHttpsCallable("findSocialDuplicateEmailProvider")
                .call(payload)
                .addOnSuccessListener(result -> {
                    String duplicateProvider = extractDuplicateProvider(result.getData());
                    if (!TextUtils.isEmpty(duplicateProvider)) {
                        signOut();
                        callback.onError(resolveDuplicateEmailMessage(duplicateProvider));
                        return;
                    }

                    Map<String, Object> userDocument = new HashMap<>();
                    userDocument.put("name", socialProfile.name);
                    userDocument.put("email", socialProfile.email);
                    userDocument.put("phone", socialProfile.phone);
                    userDocument.put("role", expectedRole.name());
                    userDocument.put("provider", socialProfile.provider);
                    userDocument.put("providerUserId", socialProfile.providerUserId);

                    firestore.collection("users")
                            .document(firebaseUser.getUid())
                            .set(userDocument)
                            .addOnSuccessListener(unused -> {
                                User user = new User(
                                        firebaseUser.getUid(),
                                        expectedRole,
                                        socialProfile.name,
                                        socialProfile.email,
                                        socialProfile.phone
                                );
                                cachedUser = user;
                                callback.onSuccess(user);
                            })
                            .addOnFailureListener(exception -> {
                                signOut();
                                callback.onError("소셜 계정 프로필을 저장하지 못했습니다.");
                            });
                })
                .addOnFailureListener(exception -> {
                    signOut();
                    callback.onError(resolveSocialDuplicateEmailMessage(exception));
                });
    }

    private void refreshExistingSocialUserProfile(
            DocumentSnapshot documentSnapshot,
            User user,
            SocialProfile socialProfile,
            RepositoryCallback<User> callback
    ) {
        Map<String, Object> updates = new HashMap<>();

        // 제공자에서 더 최신 프로필을 받은 경우 누락된 값을 보강한다.
        if (!TextUtils.isEmpty(socialProfile.name) && !socialProfile.name.equals(user.getName())) {
            updates.put("name", socialProfile.name);
        }
        if (!TextUtils.isEmpty(socialProfile.email) && !socialProfile.email.equals(user.getEmail())) {
            updates.put("email", socialProfile.email);
        }
        if (!TextUtils.isEmpty(socialProfile.phone) && !socialProfile.phone.equals(user.getPhone())) {
            updates.put("phone", socialProfile.phone);
        }
        if (TextUtils.isEmpty(documentSnapshot.getString("provider"))) {
            updates.put("provider", socialProfile.provider);
        }
        if (TextUtils.isEmpty(documentSnapshot.getString("providerUserId"))) {
            updates.put("providerUserId", socialProfile.providerUserId);
        }

        if (updates.isEmpty()) {
            cachedUser = user;
            callback.onSuccess(user);
            return;
        }

        documentSnapshot.getReference()
                .update(updates)
                .addOnSuccessListener(unused -> {
                    User refreshedUser = new User(
                            user.getId(),
                            user.getRole(),
                            updates.containsKey("name") ? socialProfile.name : user.getName(),
                            updates.containsKey("email") ? socialProfile.email : user.getEmail(),
                            updates.containsKey("phone") ? socialProfile.phone : user.getPhone()
                    );
                    cachedUser = refreshedUser;
                    callback.onSuccess(refreshedUser);
                })
                .addOnFailureListener(exception -> {
                    cachedUser = user;
                    callback.onSuccess(user);
                });
    }

    private void loadUserProfile(
            String userId,
            @Nullable UserRole expectedRole,
            RepositoryCallback<User> callback
    ) {
        // 로그인 직후에는 로컬 캐시가 비어 있을 수 있어 서버 기준 재확인을 한 번 허용한다.
        firestore.collection("users")
                .document(userId)
                .get()
                .addOnSuccessListener(documentSnapshot ->
                        handleLoadedUserProfile(documentSnapshot, expectedRole, callback))
                .addOnFailureListener(exception ->
                        callback.onError("사용자 정보를 불러오지 못했습니다."));
    }

    private void handleLoadedUserProfile(
            DocumentSnapshot documentSnapshot,
            @Nullable UserRole expectedRole,
            RepositoryCallback<User> callback
    ) {
        User user = toUser(documentSnapshot);
        if (user != null) {
            deliverLoadedUserProfile(user, expectedRole, callback);
            return;
        }

        documentSnapshot.getReference()
                .get(Source.SERVER)
                .addOnSuccessListener(serverSnapshot -> {
                    User serverUser = toUser(serverSnapshot);
                    if (serverUser == null) {
                        signOut();
                        callback.onError("users 컬렉션에서 계정 정보를 찾지 못했습니다.");
                        return;
                    }
                    deliverLoadedUserProfile(serverUser, expectedRole, callback);
                })
                .addOnFailureListener(exception ->
                        callback.onError("사용자 정보를 불러오지 못했습니다."));
    }

    private void deliverLoadedUserProfile(
            User user,
            @Nullable UserRole expectedRole,
            RepositoryCallback<User> callback
    ) {
        // 로그인 역할과 실제 저장된 역할이 다르면 즉시 로그아웃시켜 화면 분기를 단순화한다.
        if (expectedRole != null && user.getRole() != expectedRole) {
            signOut();
            callback.onError("선택한 사용자 유형과 계정 유형이 일치하지 않습니다.");
            return;
        }

        cachedUser = user;
        callback.onSuccess(user);
    }

    @Nullable
    private User toUser(DocumentSnapshot documentSnapshot) {
        // users 문서의 필수 필드가 하나라도 비어 있으면 앱 모델로 변환하지 않는다.
        if (!documentSnapshot.exists()) {
            return null;
        }

        String roleValue = documentSnapshot.getString("role");
        String name = documentSnapshot.getString("name");
        String email = documentSnapshot.getString("email");
        String phone = documentSnapshot.getString("phone");
        if (roleValue == null || name == null || email == null) {
            return null;
        }

        UserRole role = SafeEnumParser.parseOrNull(UserRole.class, roleValue);
        if (role == null) {
            return null;
        }

        return new User(
                documentSnapshot.getId(),
                role,
                UserProfileSanitizer.normalizeName(name),
                UserProfileSanitizer.normalizeEmail(email),
                UserProfileSanitizer.normalizePhone(phone)
        );
    }

    @Nullable
    private SocialProfile toSocialProfile(String provider, @Nullable Object rawProfile) {
        if (!(rawProfile instanceof Map)) {
            return null;
        }

        Map<?, ?> profileMap = (Map<?, ?>) rawProfile;
        String providerUserId = asString(profileMap.get("providerUserId"));
        String name = UserProfileSanitizer.normalizeName(asString(profileMap.get("name")));
        String email = normalizeEmailOrFallback(
                asString(profileMap.get("email")),
                provider.toLowerCase(Locale.ROOT) + "_" + providerUserId
        );
        String phone = UserProfileSanitizer.normalizePhone(asString(profileMap.get("phone")));
        if (TextUtils.isEmpty(providerUserId) || TextUtils.isEmpty(email)) {
            return null;
        }
        return new SocialProfile(provider, providerUserId, name, email, phone);
    }

    private boolean requiresEmailVerification(FirebaseUser firebaseUser) {
        for (UserInfo userInfo : firebaseUser.getProviderData()) {
            if (EmailAuthProvider.PROVIDER_ID.equals(userInfo.getProviderId())) {
                return true;
            }
        }
        return false;
    }

    @Nullable
    private String resolveGoogleServerClientId(Context context) {
        // Dependabot CI처럼 google-services.json이 없는 환경에서는 생성 리소스가 없을 수 있다.
        int resourceId = context.getResources().getIdentifier(
                "default_web_client_id",
                "string",
                context.getPackageName()
        );
        if (resourceId == 0) {
            return null;
        }
        String serverClientId = context.getString(resourceId);
        return TextUtils.isEmpty(serverClientId) ? null : serverClientId;
    }

    private void clearCredentialState() {
        ClearCredentialStateRequest clearRequest = new ClearCredentialStateRequest();
        credentialManager.clearCredentialStateAsync(
                clearRequest,
                new CancellationSignal(),
                mainExecutor,
                new CredentialManagerCallback<Void, ClearCredentialException>() {
                    @Override
                    public void onResult(Void result) {
                        // 로그아웃 후에는 추가 작업이 없다.
                    }

                    @Override
                    public void onError(@NonNull ClearCredentialException exception) {
                        // 자격 증명 정리 실패는 다음 로그인 시 재시도되므로 무시한다.
                    }
                }
        );
    }

    private void logoutFromNaver() {
        if (!isNaverConfigured()) {
            return;
        }

        NidOAuth.INSTANCE.logout(new NidOAuthCallback() {
            @Override
            public void onSuccess() {
                // 로그아웃 후 추가 작업은 없다.
            }

            @Override
            public void onFailure(String errorCode, String errorDesc) {
                // 다음 로그인에서 다시 초기화할 수 있으므로 실패를 무시한다.
            }
        });
    }

    private boolean isNaverConfigured() {
        return appContext.getResources().getBoolean(R.bool.naver_login_enabled)
                && !TextUtils.isEmpty(appContext.getString(R.string.naver_client_id))
                && !TextUtils.isEmpty(appContext.getString(R.string.naver_client_name));
    }

    private String normalizeEmailOrFallback(@Nullable String email, String prefix) {
        String normalizedEmail = UserProfileSanitizer.normalizeEmail(email);
        if (!TextUtils.isEmpty(normalizedEmail)) {
            return normalizedEmail;
        }
        return prefix + "@bodeul.local";
    }

    @Nullable
    private String asString(@Nullable Object rawValue) {
        return rawValue == null ? null : String.valueOf(rawValue);
    }

    private String resolveSignInMessage(Exception exception) {
        if (exception instanceof FirebaseAuthInvalidCredentialsException
                || exception instanceof FirebaseAuthInvalidUserException) {
            return "이메일 또는 비밀번호를 확인해주세요.";
        }
        return "로그인에 실패했습니다. 잠시 후 다시 시도해주세요.";
    }

    private String resolveRegisterMessage(Exception exception) {
        if (exception instanceof FirebaseAuthWeakPasswordException) {
            return "비밀번호는 6자 이상 입력해주세요.";
        }
        if (exception instanceof FirebaseAuthUserCollisionException) {
            return "이미 가입된 이메일입니다.";
        }
        if (exception instanceof FirebaseAuthInvalidCredentialsException) {
            return "올바른 이메일 형식을 입력해주세요.";
        }
        return "회원가입에 실패했습니다. 잠시 후 다시 시도해주세요.";
    }

    private String resolveResetPasswordMessage(Exception exception) {
        if (exception instanceof FirebaseAuthInvalidCredentialsException) {
            return "올바른 이메일 형식을 입력해주세요.";
        }
        if (exception instanceof FirebaseAuthInvalidUserException) {
            return "가입한 이메일 정보를 찾지 못했습니다.";
        }
        return "비밀번호 재설정 메일을 보내지 못했습니다. 잠시 후 다시 시도해주세요.";
    }

    private String resolveVerificationEmailMessage(Exception exception, boolean resend) {
        if (exception instanceof FirebaseAuthInvalidUserException) {
            return "계정 정보를 다시 확인해주세요.";
        }
        if (resend) {
            return "이메일 인증은 필요하지만 인증 메일을 다시 보내지 못했습니다. 잠시 후 다시 시도해주세요.";
        }
        return "회원가입은 완료됐지만 인증 메일을 보내지 못했습니다. 로그인 화면에서 다시 시도해주세요.";
    }

    private String resolveGoogleRequestMessage(GetCredentialException exception) {
        if (exception instanceof NoCredentialException) {
            return "기기에서 선택할 수 있는 구글 계정을 찾지 못했습니다.";
        }

        String simpleName = exception.getClass().getSimpleName();
        if (simpleName.contains("Cancel") || simpleName.contains("Interrupt")) {
            return "구글 로그인 요청이 취소되었습니다.";
        }
        return "구글 로그인 창을 열지 못했습니다. 다시 시도해주세요.";
    }

    private String resolveGoogleFirebaseMessage(Exception exception) {
        if (exception instanceof FirebaseAuthUserCollisionException) {
            return "이미 다른 로그인 방식으로 가입된 계정입니다. 기존 방식으로 로그인해주세요.";
        }
        if (exception instanceof FirebaseAuthInvalidCredentialsException) {
            return "구글 인증 정보를 확인하지 못했습니다.";
        }
        return "구글 로그인에 실패했습니다. 잠시 후 다시 시도해주세요.";
    }

    private String resolveKakaoCallableMessage(Exception exception) {
        if (exception instanceof FirebaseFunctionsException) {
            FirebaseFunctionsException functionsException = (FirebaseFunctionsException) exception;
            return resolveFunctionsErrorMessage(
                    functionsException,
                    "카카오 로그인 요청 정보를 확인하지 못했습니다.",
                    "카카오 로그인 권한을 확인하지 못했습니다.",
                    "카카오 로그인 처리 응답이 지연되고 있습니다. 잠시 후 다시 시도해주세요.",
                    "카카오 로그인 처리에 실패했습니다. 잠시 후 다시 시도해주세요."
            );
        }
        return "카카오 로그인 처리에 실패했습니다. 잠시 후 다시 시도해주세요.";
    }

    private String resolveNaverRequestMessage(String errorCode, String errorDesc) {
        if (!TextUtils.isEmpty(errorCode) && errorCode.contains("CANCEL")) {
            return "네이버 로그인 요청이 취소되었습니다.";
        }
        String normalizedErrorCode = errorCode == null ? "" : errorCode.toUpperCase(Locale.ROOT);
        if (normalizedErrorCode.contains("NETWORK")
                || normalizedErrorCode.contains("CONNECTION")) {
            return "네이버 로그인 요청에 실패했습니다. 네트워크 상태를 확인한 뒤 다시 시도해주세요.";
        }
        return "네이버 로그인 요청을 완료하지 못했습니다. 잠시 후 다시 시도해주세요.";
    }

    private String resolveNaverCallableMessage(Exception exception) {
        if (exception instanceof FirebaseFunctionsException) {
            FirebaseFunctionsException functionsException = (FirebaseFunctionsException) exception;
            return resolveFunctionsErrorMessage(
                    functionsException,
                    "네이버 로그인 요청 정보를 확인하지 못했습니다.",
                    "네이버 로그인 권한을 확인하지 못했습니다.",
                    "네이버 로그인 처리 응답이 지연되고 있습니다. 잠시 후 다시 시도해주세요.",
                    "네이버 로그인 처리에 실패했습니다. 잠시 후 다시 시도해주세요."
            );
        }
        return "네이버 로그인 처리에 실패했습니다. 잠시 후 다시 시도해주세요.";
    }

    @Nullable
    @SuppressWarnings("unchecked")
    private String extractDuplicateProvider(@Nullable Object rawData) {
        if (!(rawData instanceof Map)) {
            return null;
        }
        Object duplicateValue = ((Map<String, Object>) rawData).get("duplicate");
        if (!(duplicateValue instanceof Map)) {
            return null;
        }
        return asString(((Map<?, ?>) duplicateValue).get("provider"));
    }

    private String resolveSocialDuplicateEmailMessage(Exception exception) {
        if (exception instanceof FirebaseFunctionsException) {
            FirebaseFunctionsException functionsException = (FirebaseFunctionsException) exception;
            return resolveFunctionsErrorMessage(
                    functionsException,
                    "기존 계정 중복 확인 요청이 올바르지 않습니다.",
                    "기존 계정 중복 확인 권한이 없습니다.",
                    "기존 계정 정보를 확인하는 응답이 지연되고 있습니다. 잠시 후 다시 시도해주세요.",
                    "기존 계정 정보를 확인하지 못했습니다."
            );
        }
        return "기존 계정 정보를 확인하지 못했습니다.";
    }

    private String resolveFunctionsErrorMessage(
            FirebaseFunctionsException functionsException,
            String invalidArgumentMessage,
            String permissionMessage,
            String unavailableMessage,
            String defaultMessage
    ) {
        Object details = functionsException.getDetails();
        if (details instanceof Map) {
            String message = asString(((Map<?, ?>) details).get("message"));
            if (!TextUtils.isEmpty(message)) {
                return message;
            }
        }

        switch (functionsException.getCode()) {
            case INVALID_ARGUMENT:
                return invalidArgumentMessage;
            case PERMISSION_DENIED:
                return permissionMessage;
            case UNAUTHENTICATED:
                return "로그인 상태가 만료되었습니다. 다시 로그인해주세요.";
            case DEADLINE_EXCEEDED:
            case UNAVAILABLE:
                return unavailableMessage;
            default:
                return defaultMessage;
        }
    }

    private String resolveDuplicateEmailMessage(String provider) {
        if (TextUtils.isEmpty(provider) || PROVIDER_EMAIL.equals(provider)) {
            return "같은 이메일로 이미 이메일 로그인 계정이 있습니다. 기존 로그인 방식으로 로그인해주세요.";
        }
        return "같은 이메일로 이미 " + toProviderLabel(provider) + " 로그인 계정이 있습니다. 기존 로그인 방식으로 로그인해주세요.";
    }

    private String toProviderLabel(String provider) {
        switch (provider) {
            case PROVIDER_GOOGLE:
                return "구글";
            case PROVIDER_KAKAO:
                return "카카오";
            case PROVIDER_NAVER:
                return "네이버";
            case PROVIDER_EMAIL:
            default:
                return "이메일";
        }
    }

    private static class SocialProfile {
        private final String provider;
        private final String providerUserId;
        private final String name;
        private final String email;
        private final String phone;

        private SocialProfile(
                String provider,
                String providerUserId,
                String name,
                String email,
                String phone
        ) {
            this.provider = provider;
            this.providerUserId = providerUserId;
            this.name = name;
            this.email = email;
            this.phone = phone;
        }
    }
}
