package com.example.bodeul.debug;

import android.content.ComponentName;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.example.bodeul.MainActivity;
import com.example.bodeul.data.AdminRepository;
import com.example.bodeul.data.AuthRepository;
import com.example.bodeul.data.BookingRepository;
import com.example.bodeul.data.ClientSupportRepository;
import com.example.bodeul.data.CompanionChatAttachmentUploader;
import com.example.bodeul.data.ManagerDocumentStorageUploader;
import com.example.bodeul.data.ManagerRepository;
import com.example.bodeul.data.RepositoryCallback;
import com.example.bodeul.data.ServiceLocator;
import com.example.bodeul.domain.model.AdminSettlementStatus;
import com.example.bodeul.domain.model.AppointmentFollowUpRecord;
import com.example.bodeul.domain.model.AppointmentFollowUpReviewRating;
import com.example.bodeul.domain.model.AppointmentFollowUpSettlementStatus;
import com.example.bodeul.domain.model.AppointmentRequest;
import com.example.bodeul.domain.model.AppointmentRequestDetail;
import com.example.bodeul.domain.model.AppointmentStatus;
import com.example.bodeul.domain.model.BookingCouponType;
import com.example.bodeul.domain.model.BookingHospitalSelection;
import com.example.bodeul.domain.model.BookingManagerGenderPreference;
import com.example.bodeul.domain.model.BookingMobilitySupport;
import com.example.bodeul.domain.model.BookingPaymentMethod;
import com.example.bodeul.domain.model.BookingRequestDraft;
import com.example.bodeul.domain.model.BookingTripType;
import com.example.bodeul.domain.model.ClientSupportCategory;
import com.example.bodeul.domain.model.ClientSupportRequest;
import com.example.bodeul.domain.model.CompanionChatAttachment;
import com.example.bodeul.domain.model.ManagerDocumentFileMetadata;
import com.example.bodeul.domain.model.ManagerDocumentFileType;
import com.example.bodeul.domain.model.ManagerHomeProfile;
import com.example.bodeul.domain.model.ManagerDashboard;
import com.example.bodeul.domain.model.SupportInquiry;
import com.example.bodeul.domain.model.SupportInquiryCategory;
import com.example.bodeul.domain.model.User;
import com.example.bodeul.domain.model.UserRole;
import com.example.bodeul.ui.admin.AdminActivity;
import com.example.bodeul.ui.auth.AuthFlowRouter;
import com.example.bodeul.ui.booking.BookingActivity;
import com.example.bodeul.ui.booking.BookingFollowUpActivity;
import com.example.bodeul.ui.booking.BookingHospitalSelectorActivity;
import com.example.bodeul.ui.booking.BookingStatusActivity;
import com.example.bodeul.ui.chat.CompanionChatActivity;
import com.example.bodeul.ui.manager.ManagerActivity;
import com.example.bodeul.ui.manager.ManagerGuideActivity;
import com.example.bodeul.ui.manager.ManagerHistoryActivity;
import com.example.bodeul.ui.manager.ManagerProfileActivity;
import com.example.bodeul.ui.manager.ManagerSupportActivity;
import com.example.bodeul.ui.report.GuardianReportActivity;
import com.example.bodeul.ui.support.ClientSupportActivity;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Collections;
import java.util.Locale;
import java.util.TimeZone;

/**
 * 디버그 빌드에서만 adb 자동 진입을 받아 기준선 계정 로그인과 화면 라우팅을 수행한다.
 */
public class AutomationEntryActivity extends AppCompatActivity {
    private static final long AUTH_STATE_SETTLE_DELAY_MILLIS = 750L;
    private static final byte[] SAMPLE_PNG_BYTES = new byte[]{
            (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A,
            0x00, 0x00, 0x00, 0x0D, 0x49, 0x48, 0x44, 0x52,
            0x00, 0x00, 0x00, 0x01, 0x00, 0x00, 0x00, 0x01,
            0x08, 0x06, 0x00, 0x00, 0x00, 0x1F, 0x15, (byte) 0xC4,
            (byte) 0x89, 0x00, 0x00, 0x00, 0x0D, 0x49, 0x44, 0x41,
            0x54, 0x78, (byte) 0x9C, 0x63, (byte) 0xF8, (byte) 0xCF, (byte) 0xC0,
            0x00, 0x00, 0x03, 0x01, 0x01, 0x00, 0x18, (byte) 0xDD,
            (byte) 0x8D, (byte) 0xB3, 0x00, 0x00, 0x00, 0x00, 0x49,
            0x45, 0x4E, 0x44, (byte) 0xAE, 0x42, 0x60, (byte) 0x82
    };

    public static final String EXTRA_ROLE = "role";
    public static final String EXTRA_SCREEN = "screen";
    public static final String EXTRA_REQUEST_ID = "requestId";
    public static final String EXTRA_FORCE_SIGN_IN = "forceSignIn";
    public static final String EXTRA_UPLOAD_DOCUMENT_TYPE = "uploadDocumentType";
    public static final String EXTRA_UPLOAD_DOCUMENT_PATH = "uploadDocumentPath";
    public static final String EXTRA_CHAT_MESSAGE = "chatMessage";
    public static final String EXTRA_CHAT_ATTACHMENT = "chatAttachment";
    public static final String EXTRA_CLIENT_SUPPORT_CATEGORY = "clientSupportCategory";
    public static final String EXTRA_CLIENT_SUPPORT_TITLE = "clientSupportTitle";
    public static final String EXTRA_CLIENT_SUPPORT_BODY = "clientSupportBody";
    public static final String EXTRA_MANAGER_SUPPORT_CATEGORY = "managerSupportCategory";
    public static final String EXTRA_MANAGER_SUPPORT_TITLE = "managerSupportTitle";
    public static final String EXTRA_MANAGER_SUPPORT_BODY = "managerSupportBody";
    public static final String EXTRA_FOLLOW_UP_REVIEW_RATING = "followUpReviewRating";
    public static final String EXTRA_FOLLOW_UP_SETTLEMENT_STATUS = "followUpSettlementStatus";
    public static final String EXTRA_FOLLOW_UP_SETTLEMENT_NOTE = "followUpSettlementNote";
    public static final String EXTRA_ADMIN_SETTLEMENT_STATUS = "adminSettlementStatus";
    public static final String EXTRA_ADMIN_SETTLEMENT_NOTE = "adminSettlementNote";
    public static final String EXTRA_ADMIN_SUPPORT_INQUIRY_ID = "adminSupportInquiryId";
    public static final String EXTRA_ADMIN_SUPPORT_RESPONSE = "adminSupportResponse";
    public static final String EXTRA_ADMIN_CLIENT_SUPPORT_REQUEST_ID = "adminClientSupportRequestId";
    public static final String EXTRA_ADMIN_CLIENT_SUPPORT_RESPONSE = "adminClientSupportResponse";
    public static final String EXTRA_ADMIN_NOTIFICATION_ID = "adminNotificationId";
    public static final String EXTRA_ADMIN_ACTION_OPERATION = "adminActionOperation";

    private static final String TAG = "AutomationEntry";
    private static final String DEFAULT_PASSWORD = "bodeul1234";
    private static final String REQUEST_ID_PROGRESS = "request-seed-progress";
    private static final String REQUEST_ID_COMPLETED = "request-seed-completed";
    private static final String SUPPORT_INQUIRY_ID_RECEIVED = "support-seed-received";
    private static final String CLIENT_SUPPORT_REQUEST_ID_RECEIVED = "client-support-seed-patient-received";
    private static final String ADMIN_NOTIFICATION_ID_SUPPORT = "admin-notification-seed-support";
    private static final String DEFAULT_CHAT_MESSAGE = "실기기 자동화 채팅 점검 메시지";
    private static final String DEFAULT_CLIENT_SUPPORT_TITLE = "실기기 자동화 문의";
    private static final String DEFAULT_CLIENT_SUPPORT_BODY = "실기기 자동화로 문의 등록 경로를 확인합니다.";
    private static final String DEFAULT_MANAGER_SUPPORT_TITLE = "실기기 자동화 매니저 문의";
    private static final String DEFAULT_MANAGER_SUPPORT_BODY = "실기기 자동화로 매니저 문의 등록 경로를 확인합니다.";
    private static final String DEFAULT_SETTLEMENT_NOTE = "실기기 자동화로 정산 후속 저장 경로를 확인합니다.";

    private static final String DEFAULT_ADMIN_SETTLEMENT_NOTE = "실기기 자동화로 정산 후속 처리 경로를 확인합니다.";
    private static final String DEFAULT_ADMIN_SUPPORT_RESPONSE = "실기기 자동화로 매니저 문의 응답 경로를 확인했습니다.";
    private static final String DEFAULT_ADMIN_CLIENT_SUPPORT_RESPONSE = "실기기 자동화로 고객 문의 응답 경로를 확인했습니다.";

    private AuthRepository authRepository;
    private AdminRepository adminRepository;
    private BookingRepository bookingRepository;
    private ClientSupportRepository clientSupportRepository;
    private CompanionChatAttachmentUploader companionChatAttachmentUploader;
    private ManagerRepository managerRepository;
    private ManagerDocumentStorageUploader managerDocumentStorageUploader;
    private TextView textPrimary;
    private TextView textSecondary;
    private AutomationScreen requestedScreen;
    private UserRole requestedRole;
    private String requestedRequestId;
    private boolean forceSignIn;
    private ManagerDocumentFileType requestedUploadDocumentType;
    private String requestedUploadDocumentPath;
    private String requestedChatMessage;
    private boolean requestedChatAttachment;
    private ClientSupportCategory requestedClientSupportCategory;
    private String requestedClientSupportTitle;
    private String requestedClientSupportBody;
    private SupportInquiryCategory requestedManagerSupportCategory;
    private String requestedManagerSupportTitle;
    private String requestedManagerSupportBody;
    private AppointmentFollowUpReviewRating requestedFollowUpReviewRating;
    private AppointmentFollowUpSettlementStatus requestedFollowUpSettlementStatus;
    private String requestedFollowUpSettlementNote;
    private AdminSettlementStatus requestedAdminSettlementStatus;
    private String requestedAdminSettlementNote;
    private String requestedAdminSupportInquiryId;
    private String requestedAdminSupportResponse;
    private String requestedAdminClientSupportRequestId;
    private String requestedAdminClientSupportResponse;
    private String requestedAdminNotificationId;
    private AdminActionOperation requestedAdminActionOperation;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(android.R.layout.simple_list_item_2);

        textPrimary = findViewById(android.R.id.text1);
        textSecondary = findViewById(android.R.id.text2);
        authRepository = ServiceLocator.provideAuthRepository(this);
        adminRepository = ServiceLocator.provideAdminRepository(this);
        bookingRepository = ServiceLocator.provideBookingRepository(this);
        clientSupportRepository = ServiceLocator.provideClientSupportRepository(this);
        companionChatAttachmentUploader =
                ServiceLocator.provideCompanionChatAttachmentUploader(this);
        managerRepository = ServiceLocator.provideManagerRepository(this);
        managerDocumentStorageUploader = ServiceLocator.provideManagerDocumentStorageUploader(this);

        requestedRole = parseRole(getIntent().getStringExtra(EXTRA_ROLE));
        requestedScreen = parseScreen(getIntent().getStringExtra(EXTRA_SCREEN));
        requestedRequestId = getIntent().getStringExtra(EXTRA_REQUEST_ID);
        forceSignIn = getIntent().getBooleanExtra(EXTRA_FORCE_SIGN_IN, false);
        requestedUploadDocumentType = parseDocumentFileType(
                getIntent().getStringExtra(EXTRA_UPLOAD_DOCUMENT_TYPE)
        );
        requestedUploadDocumentPath = getIntent().getStringExtra(EXTRA_UPLOAD_DOCUMENT_PATH);
        requestedChatMessage = normalizeText(getIntent().getStringExtra(EXTRA_CHAT_MESSAGE));
        requestedChatAttachment = getIntent().getBooleanExtra(EXTRA_CHAT_ATTACHMENT, false);
        requestedClientSupportCategory = ClientSupportCategory.fromValue(
                getIntent().getStringExtra(EXTRA_CLIENT_SUPPORT_CATEGORY)
        );
        requestedClientSupportTitle = normalizeText(
                getIntent().getStringExtra(EXTRA_CLIENT_SUPPORT_TITLE)
        );
        requestedClientSupportBody = normalizeText(
                getIntent().getStringExtra(EXTRA_CLIENT_SUPPORT_BODY)
        );
        requestedManagerSupportCategory = SupportInquiryCategory.fromValue(
                getIntent().getStringExtra(EXTRA_MANAGER_SUPPORT_CATEGORY)
        );
        requestedManagerSupportTitle = normalizeText(
                getIntent().getStringExtra(EXTRA_MANAGER_SUPPORT_TITLE)
        );
        requestedManagerSupportBody = normalizeText(
                getIntent().getStringExtra(EXTRA_MANAGER_SUPPORT_BODY)
        );
        requestedFollowUpReviewRating = AppointmentFollowUpReviewRating.fromValue(
                getIntent().getStringExtra(EXTRA_FOLLOW_UP_REVIEW_RATING)
        );
        requestedFollowUpSettlementStatus = AppointmentFollowUpSettlementStatus.fromValue(
                getIntent().getStringExtra(EXTRA_FOLLOW_UP_SETTLEMENT_STATUS)
        );
        requestedFollowUpSettlementNote = normalizeText(
                getIntent().getStringExtra(EXTRA_FOLLOW_UP_SETTLEMENT_NOTE)
        );
        requestedAdminSettlementStatus = parseAdminSettlementStatus(
                getIntent().getStringExtra(EXTRA_ADMIN_SETTLEMENT_STATUS)
        );
        requestedAdminSettlementNote = normalizeText(
                getIntent().getStringExtra(EXTRA_ADMIN_SETTLEMENT_NOTE)
        );
        requestedAdminSupportInquiryId = normalizeText(
                getIntent().getStringExtra(EXTRA_ADMIN_SUPPORT_INQUIRY_ID)
        );
        requestedAdminSupportResponse = normalizeText(
                getIntent().getStringExtra(EXTRA_ADMIN_SUPPORT_RESPONSE)
        );
        requestedAdminClientSupportRequestId = normalizeText(
                getIntent().getStringExtra(EXTRA_ADMIN_CLIENT_SUPPORT_REQUEST_ID)
        );
        requestedAdminClientSupportResponse = normalizeText(
                getIntent().getStringExtra(EXTRA_ADMIN_CLIENT_SUPPORT_RESPONSE)
        );
        requestedAdminNotificationId = normalizeText(
                getIntent().getStringExtra(EXTRA_ADMIN_NOTIFICATION_ID)
        );
        requestedAdminActionOperation = parseAdminActionOperation(
                getIntent().getStringExtra(EXTRA_ADMIN_ACTION_OPERATION)
        );

        if (requestedRole == null) {
            showError("자동 진입 역할이 없습니다.", "role extra를 ADMIN, MANAGER, PATIENT, GUARDIAN 중 하나로 전달해 주세요.");
            return;
        }
        if (requestedScreen == null) {
            showError("자동 진입 화면이 없습니다.", "screen extra를 전달해 주세요.");
            return;
        }

        updateStatus(
                "자동 진입 준비 중",
                requestedRole.name() + " / " + requestedScreen.name()
        );
        routeToRequestedScreen();
    }

    private void routeToRequestedScreen() {
        authRepository.getCurrentUser(new RepositoryCallback<User>() {
            @Override
            public void onSuccess(User result) {
                if (!forceSignIn
                        && result.getRole() == requestedRole
                        && !AuthFlowRouter.requiresProfileCompletion(result)) {
                    runRequestedAction(result);
                    return;
                }
                authRepository.signOut();
                // 로그아웃 직후 재로그인하면 Firestore가 이전 인증 상태로 첫 조회를 처리할 수 있다.
                textPrimary.postDelayed(
                        AutomationEntryActivity.this::signInBaseline,
                        AUTH_STATE_SETTLE_DELAY_MILLIS
                );
            }

            @Override
            public void onError(String message) {
                signInBaseline();
            }
        });
    }

    private void signInBaseline() {
        BaselineAccount baselineAccount = BaselineAccount.fromRole(requestedRole);
        if (baselineAccount == null) {
            showError("기준선 계정을 찾지 못했습니다.", requestedRole.name());
            return;
        }

        updateStatus(
                "기준선 계정 로그인 중",
                baselineAccount.email
        );
        authRepository.signIn(
                baselineAccount.email,
                DEFAULT_PASSWORD,
                requestedRole,
                new RepositoryCallback<User>() {
                    @Override
                    public void onSuccess(User result) {
                        runRequestedAction(result);
                    }

                    @Override
                    public void onError(String message) {
                        showError("기준선 계정 로그인에 실패했습니다.", message);
                    }
                }
        );
    }

    private void runRequestedAction(User user) {
        if (requestedScreen == AutomationScreen.BOOKING_API_SMOKE) {
            runBookingApiSmoke(user);
            return;
        }
        runRequestedAction(user, () -> openRequestedScreen(user));
    }

    private void runBookingApiSmoke(User user) {
        if (user.getRole() != UserRole.PATIENT) {
            showError("예약 API 점검은 환자 기준선 계정으로 실행해 주세요.", user.getRole().name());
            return;
        }

        BookingRequestDraft createDraft = BookingRequestDraft.builder()
                .linkedParticipantName("김하나")
                .linkedParticipantPhone("010-0000-0002")
                .linkedParticipantEmail("guardian@bodeul.app")
                .patientConditionSummary("Core API 실기기 전환 검증")
                .medicationSummary("없음")
                .hospitalName("서울대학교병원")
                .departmentName("내과")
                .hospitalLatitude(37.579617)
                .hospitalLongitude(126.999016)
                .appointmentAt(createFutureAppointmentAt())
                .meetingPlace("본관 1층 안내 데스크")
                .specialNotes("생성 단계")
                .mobilitySupport(BookingMobilitySupport.INDEPENDENT)
                .tripType(BookingTripType.ONE_WAY)
                .managerGenderPreference(BookingManagerGenderPreference.ANY)
                .paymentMethod(BookingPaymentMethod.ON_SITE)
                .couponType(BookingCouponType.NONE)
                .build();

        updateStatus("예약 API 생성 검증 중", "PostgreSQL 신규 예약을 생성합니다.");
        bookingRepository.createAppointmentRequest(
                user,
                createDraft,
                new RepositoryCallback<AppointmentRequest>() {
                    @Override
                    public void onSuccess(AppointmentRequest created) {
                        if (created.getStatus() != AppointmentStatus.REQUESTED) {
                            showError("예약 API 생성 상태가 올바르지 않습니다.", created.getStatus().name());
                            return;
                        }
                        if (created.getBasePrice() != 69_000
                                || created.getFinalPrice() != 69_000
                                || !"DEFERRED".equals(created.getPaymentStatusCode())) {
                            showError(
                                    "예약 API 서버 계산 결과가 올바르지 않습니다.",
                                    created.getFinalPrice() + " / " + created.getPaymentStatusCode());
                            return;
                        }
                        updateBookingApiSmoke(user, created, createDraft);
                    }

                    @Override
                    public void onError(String message) {
                        showError("예약 API 생성 검증에 실패했습니다.", message);
                    }
                });
    }

    private String createFutureAppointmentAt() {
        TimeZone seoul = TimeZone.getTimeZone("Asia/Seoul");
        Calendar calendar = Calendar.getInstance(seoul, Locale.KOREA);
        calendar.add(Calendar.DAY_OF_YEAR, 30);
        calendar.set(Calendar.HOUR_OF_DAY, 10);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.KOREA);
        formatter.setTimeZone(seoul);
        return formatter.format(calendar.getTime());
    }

    private void updateBookingApiSmoke(
            User user,
            AppointmentRequest created,
            BookingRequestDraft createDraft
    ) {
        BookingRequestDraft updateDraft = createDraft.toBuilder()
                .meetingPlace("암병원 1층 안내 데스크")
                .specialNotes("수정 단계")
                .build();
        updateStatus("예약 API 수정 검증 중", "생성한 예약의 장소와 메모를 수정합니다.");
        bookingRepository.updateAppointmentRequest(
                user,
                created.getId(),
                updateDraft,
                new RepositoryCallback<AppointmentRequest>() {
                    @Override
                    public void onSuccess(AppointmentRequest updated) {
                        if (!"암병원 1층 안내 데스크".equals(updated.getMeetingPlace())) {
                            showError("예약 API 수정 결과가 올바르지 않습니다.", updated.getMeetingPlace());
                            return;
                        }
                        cancelBookingApiSmoke(user, updated);
                    }

                    @Override
                    public void onError(String message) {
                        showError("예약 API 수정 검증에 실패했습니다.", message);
                    }
                });
    }

    private void cancelBookingApiSmoke(User user, AppointmentRequest updated) {
        updateStatus("예약 API 취소 검증 중", "생성한 검증 예약을 취소 상태로 마감합니다.");
        bookingRepository.cancelAppointmentRequest(
                user,
                updated.getId(),
                new RepositoryCallback<AppointmentRequest>() {
                    @Override
                    public void onSuccess(AppointmentRequest canceled) {
                        if (canceled.getStatus() != AppointmentStatus.CANCELED) {
                            showError("예약 API 취소 상태가 올바르지 않습니다.", canceled.getStatus().name());
                            return;
                        }
                        updateStatus(
                                "예약 API 생명주기 검증 완료",
                                "생성, 수정, 취소와 서버 가격 계산을 확인했습니다.");
                    }

                    @Override
                    public void onError(String message) {
                        showError("예약 API 취소 검증에 실패했습니다.", message);
                    }
                });
    }

    private void runRequestedAction(User user, Runnable completionAction) {
        if (shouldUploadManagerDocument()) {
            uploadManagerDocument(user, () -> runPostUploadActions(user, completionAction));
            return;
        }
        runPostUploadActions(user, completionAction);
    }

    private void runPostUploadActions(User user, Runnable completionAction) {
        if (shouldSendChatMessage()) {
            sendChatMessage(user, () -> runPostChatActions(user, completionAction));
            return;
        }
        runPostChatActions(user, completionAction);
    }

    private void runPostChatActions(User user, Runnable completionAction) {
        if (shouldSubmitClientSupport()) {
            submitClientSupport(user, () -> runPostClientSupportActions(user, completionAction));
            return;
        }
        runPostClientSupportActions(user, completionAction);
    }

    private void runPostClientSupportActions(User user, Runnable completionAction) {
        if (shouldSubmitManagerSupport()) {
            submitManagerSupport(user, () -> runPostManagerSupportActions(user, completionAction));
            return;
        }
        runPostManagerSupportActions(user, completionAction);
    }

    private void runPostManagerSupportActions(User user, Runnable completionAction) {
        if (shouldSaveFollowUpReview()) {
            saveFollowUpReview(user, () -> runPostFollowUpReviewActions(user, completionAction));
            return;
        }
        runPostFollowUpReviewActions(user, completionAction);
    }

    private void runPostFollowUpReviewActions(User user, Runnable completionAction) {
        if (shouldSaveFollowUpSettlement()) {
            saveFollowUpSettlement(user, () -> runPostFollowUpSettlementActions(user, completionAction));
            return;
        }
        runPostFollowUpSettlementActions(user, completionAction);
    }

    private void runPostFollowUpSettlementActions(User user, Runnable completionAction) {
        if (shouldSaveAdminSettlement()) {
            saveAdminSettlement(user, () -> runPostAdminSettlementActions(user, completionAction));
            return;
        }
        runPostAdminSettlementActions(user, completionAction);
    }

    private void runPostAdminSettlementActions(User user, Runnable completionAction) {
        if (shouldRespondAdminSupport()) {
            respondAdminSupport(user, () -> runPostAdminSupportActions(user, completionAction));
            return;
        }
        runPostAdminSupportActions(user, completionAction);
    }

    private void runPostAdminSupportActions(User user, Runnable completionAction) {
        if (shouldRespondAdminClientSupport()) {
            respondAdminClientSupport(user, () -> runPostAdminClientSupportActions(user, completionAction));
            return;
        }
        runPostAdminClientSupportActions(user, completionAction);
    }

    private void runPostAdminClientSupportActions(User user, Runnable completionAction) {
        if (shouldHandleAdminNotification()) {
            handleAdminNotification(user, completionAction);
            return;
        }
        completionAction.run();
    }

    private boolean shouldUploadManagerDocument() {
        return requestedRole == UserRole.MANAGER
                && requestedUploadDocumentType != null;
    }

    private boolean shouldSendChatMessage() {
        return !TextUtils.isEmpty(requestedChatMessage) || requestedChatAttachment;
    }

    private boolean shouldSubmitClientSupport() {
        return !TextUtils.isEmpty(requestedClientSupportTitle)
                || !TextUtils.isEmpty(requestedClientSupportBody);
    }

    private boolean shouldSubmitManagerSupport() {
        return !TextUtils.isEmpty(requestedManagerSupportTitle)
                || !TextUtils.isEmpty(requestedManagerSupportBody);
    }

    private boolean shouldSaveFollowUpReview() {
        return requestedFollowUpReviewRating != null;
    }

    private boolean shouldSaveFollowUpSettlement() {
        return requestedFollowUpSettlementStatus != null;
    }

    private boolean shouldSaveAdminSettlement() {
        return requestedAdminSettlementStatus != null;
    }

    private boolean shouldRespondAdminSupport() {
        return !TextUtils.isEmpty(requestedAdminSupportResponse);
    }

    private boolean shouldRespondAdminClientSupport() {
        return !TextUtils.isEmpty(requestedAdminClientSupportResponse);
    }

    private boolean shouldHandleAdminNotification() {
        return requestedAdminActionOperation != null;
    }

    private void uploadManagerDocument(User user, Runnable completionAction) {
        if (user.getRole() != UserRole.MANAGER) {
            showError("서류 업로드는 매니저 계정만 지원합니다.", user.getRole().name());
            return;
        }

        File localFile = resolveUploadFile();
        if (localFile == null || !localFile.exists() || !localFile.isFile()) {
            showError("업로드할 파일을 준비하지 못했습니다.", requestedUploadDocumentPath);
            return;
        }

        updateStatus(
                "원본 파일 업로드 중",
                requestedUploadDocumentType.name() + " / " + localFile.getName()
        );
        managerDocumentStorageUploader.uploadDocument(
                user.getId(),
                requestedUploadDocumentType,
                Uri.fromFile(localFile),
                new RepositoryCallback<ManagerDocumentFileMetadata>() {
                    @Override
                    public void onSuccess(ManagerDocumentFileMetadata result) {
                        saveUploadedDocumentMetadata(user, result, completionAction);
                    }

                    @Override
                    public void onError(String message) {
                        showError("원본 파일 업로드에 실패했습니다.", message);
                    }
                }
        );
    }

    private void saveUploadedDocumentMetadata(
            User user,
            ManagerDocumentFileMetadata uploadedMetadata,
            Runnable completionAction
    ) {
        updateStatus(
                "서류 메타데이터 저장 중",
                uploadedMetadata.getFileName()
        );
        managerRepository.saveManagerDocumentFileMetadata(
                user.getId(),
                uploadedMetadata,
                new RepositoryCallback<ManagerHomeProfile>() {
                    @Override
                    public void onSuccess(ManagerHomeProfile result) {
                        completionAction.run();
                    }

                    @Override
                    public void onError(String message) {
                        showError("서류 메타데이터 저장에 실패했습니다.", message);
                    }
                }
        );
    }

    private void sendChatMessage(User user, Runnable completionAction) {
        String message = requestedChatMessage;
        if (TextUtils.isEmpty(message)) {
            message = DEFAULT_CHAT_MESSAGE;
        }

        if (requestedChatAttachment) {
            prepareChatAttachment(user, message, completionAction);
            return;
        }
        sendChatMessageWithAttachments(
                user,
                message,
                Collections.<CompanionChatAttachment>emptyList(),
                completionAction
        );
    }

    private void prepareChatAttachment(User user, String message, Runnable completionAction) {
        updateStatus("채팅 첨부 세션 확인 중", user.getRole().name());
        if (user.getRole() == UserRole.MANAGER) {
            managerRepository.getManagerDashboard(user.getId(), new RepositoryCallback<ManagerDashboard>() {
                @Override
                public void onSuccess(ManagerDashboard result) {
                    uploadChatAttachmentAndSend(
                            user,
                            result.getAppointmentRequest().getId(),
                            result.getSession() == null ? "" : result.getSession().getId(),
                            message,
                            completionAction
                    );
                }

                @Override
                public void onError(String errorMessage) {
                    showError("채팅 첨부 세션을 찾지 못했습니다.", errorMessage);
                }
            });
            return;
        }

        if (user.getRole() != UserRole.PATIENT && user.getRole() != UserRole.GUARDIAN) {
            showError("채팅 자동화는 환자, 보호자, 매니저만 지원합니다.", user.getRole().name());
            return;
        }
        bookingRepository.getAppointmentRequestDetail(
                user,
                resolveChatRequestId(),
                new RepositoryCallback<AppointmentRequestDetail>() {
                    @Override
                    public void onSuccess(AppointmentRequestDetail result) {
                        uploadChatAttachmentAndSend(
                                user,
                                resolveChatRequestId(),
                                result.getSession() == null ? "" : result.getSession().getId(),
                                message,
                                completionAction
                        );
                    }

                    @Override
                    public void onError(String errorMessage) {
                        showError("채팅 첨부 세션을 찾지 못했습니다.", errorMessage);
                    }
                }
        );
    }

    private void uploadChatAttachmentAndSend(
            User user,
            String expectedRequestId,
            String sessionId,
            String message,
            Runnable completionAction
    ) {
        if (TextUtils.isEmpty(sessionId)) {
            showError("채팅 첨부 세션 정보가 없습니다.", resolveChatRequestId());
            return;
        }

        File sampleFile = createSamplePngFile("automation-companion-chat.png");
        if (sampleFile == null) {
            showError("채팅 첨부 샘플을 만들지 못했습니다.", sessionId);
            return;
        }

        updateStatus("채팅 첨부 업로드 중", sampleFile.getName());
        companionChatAttachmentUploader.uploadAttachment(
                sessionId,
                Uri.fromFile(sampleFile),
                new RepositoryCallback<CompanionChatAttachment>() {
                    @Override
                    public void onSuccess(CompanionChatAttachment result) {
                        sendChatMessageWithAttachments(
                                user,
                                expectedRequestId,
                                sessionId,
                                message,
                                Collections.singletonList(result),
                                completionAction,
                                () -> deleteSampleFile(sampleFile)
                        );
                    }

                    @Override
                    public void onError(String errorMessage) {
                        deleteSampleFile(sampleFile);
                        showError("채팅 첨부 업로드에 실패했습니다.", errorMessage);
                    }
                }
        );
    }

    private void sendChatMessageWithAttachments(
            User user,
            String message,
            java.util.List<CompanionChatAttachment> attachments,
            Runnable completionAction
    ) {
        if (user.getRole() == UserRole.MANAGER) {
            managerRepository.getManagerDashboard(user.getId(), new RepositoryCallback<ManagerDashboard>() {
                @Override public void onSuccess(ManagerDashboard result) {
                    sendChatMessageWithAttachments(user, result.getAppointmentRequest().getId(),
                            result.getSession().getId(), message, attachments, completionAction, () -> { });
                }
                @Override public void onError(String error) {
                    showError("채팅 세션을 찾지 못했습니다.", error);
                }
            });
            return;
        }
        sendChatMessageWithAttachments(
                user,
                resolveChatRequestId(),
                "",
                message,
                attachments,
                completionAction,
                () -> { }
        );
    }

    private void sendChatMessageWithAttachments(
            User user,
            String expectedRequestId,
            String expectedSessionId,
            String message,
            java.util.List<CompanionChatAttachment> attachments,
            Runnable completionAction,
            Runnable cleanupAction
    ) {
        updateStatus("채팅 메시지 저장 중", message);
        if (user.getRole() == UserRole.MANAGER) {
            managerRepository.sendCompanionChatMessage(
                    user.getId(),
                    expectedRequestId,
                    expectedSessionId,
                    message,
                    attachments,
                    new RepositoryCallback<ManagerDashboard>() {
                        @Override
                        public void onSuccess(ManagerDashboard result) {
                            cleanupAction.run();
                            completionAction.run();
                        }

                        @Override
                        public void onError(String message) {
                            cleanupAction.run();
                            showError("채팅 메시지 저장에 실패했습니다.", message);
                        }
                    }
            );
            return;
        }

        if (user.getRole() != UserRole.PATIENT && user.getRole() != UserRole.GUARDIAN) {
            showError("채팅 자동화는 환자, 보호자, 매니저만 지원합니다.", user.getRole().name());
            return;
        }

        bookingRepository.sendCompanionChatMessage(
                user,
                expectedRequestId,
                message,
                attachments,
                new RepositoryCallback<AppointmentRequestDetail>() {
                    @Override
                    public void onSuccess(AppointmentRequestDetail result) {
                        cleanupAction.run();
                        completionAction.run();
                    }

                    @Override
                    public void onError(String message) {
                        cleanupAction.run();
                        showError("채팅 메시지 저장에 실패했습니다.", message);
                    }
                }
        );
    }

    private void submitClientSupport(User user, Runnable completionAction) {
        if (user.getRole() != UserRole.PATIENT && user.getRole() != UserRole.GUARDIAN) {
            showError("고객 문의 자동화는 환자와 보호자만 지원합니다.", user.getRole().name());
            return;
        }

        String title = TextUtils.isEmpty(requestedClientSupportTitle)
                ? DEFAULT_CLIENT_SUPPORT_TITLE
                : requestedClientSupportTitle;
        String body = TextUtils.isEmpty(requestedClientSupportBody)
                ? DEFAULT_CLIENT_SUPPORT_BODY
                : requestedClientSupportBody;

        updateStatus("고객 문의 저장 중", title);
        clientSupportRepository.submitClientSupportRequest(
                user,
                resolveClientSupportRequestId(),
                requestedClientSupportCategory,
                title,
                body,
                new RepositoryCallback<java.util.List<ClientSupportRequest>>() {
                    @Override
                    public void onSuccess(java.util.List<ClientSupportRequest> result) {
                        completionAction.run();
                    }

                    @Override
                    public void onError(String message) {
                        showError("고객 문의 저장에 실패했습니다.", message);
                    }
                }
        );
    }

    private void submitManagerSupport(User user, Runnable completionAction) {
        if (user.getRole() != UserRole.MANAGER) {
            showError("매니저 문의 자동화는 매니저 계정만 지원합니다.", user.getRole().name());
            return;
        }

        String title = TextUtils.isEmpty(requestedManagerSupportTitle)
                ? DEFAULT_MANAGER_SUPPORT_TITLE
                : requestedManagerSupportTitle;
        String body = TextUtils.isEmpty(requestedManagerSupportBody)
                ? DEFAULT_MANAGER_SUPPORT_BODY
                : requestedManagerSupportBody;

        updateStatus("매니저 문의 저장 중", title);
        managerRepository.submitSupportInquiry(
                user.getId(),
                requestedManagerSupportCategory,
                title,
                body,
                new RepositoryCallback<java.util.List<SupportInquiry>>() {
                    @Override
                    public void onSuccess(java.util.List<SupportInquiry> result) {
                        completionAction.run();
                    }

                    @Override
                    public void onError(String message) {
                        showError("매니저 문의 저장에 실패했습니다.", message);
                    }
                }
        );
    }

    private void saveFollowUpReview(User user, Runnable completionAction) {
        if (user.getRole() != UserRole.PATIENT && user.getRole() != UserRole.GUARDIAN) {
            showError("후기 저장 자동화는 환자와 보호자만 지원합니다.", user.getRole().name());
            return;
        }

        updateStatus("후기 저장 중", requestedFollowUpReviewRating.getValue());
        bookingRepository.saveAppointmentFollowUpReview(
                user,
                resolveFollowUpRequestId(),
                requestedFollowUpReviewRating,
                new RepositoryCallback<AppointmentFollowUpRecord>() {
                    @Override
                    public void onSuccess(AppointmentFollowUpRecord result) {
                        completionAction.run();
                    }

                    @Override
                    public void onError(String message) {
                        showError("후기 저장에 실패했습니다.", message);
                    }
                }
        );
    }

    private void saveFollowUpSettlement(User user, Runnable completionAction) {
        if (user.getRole() != UserRole.PATIENT && user.getRole() != UserRole.GUARDIAN) {
            showError("정산 후속 저장 자동화는 환자와 보호자만 지원합니다.", user.getRole().name());
            return;
        }

        String settlementNote = TextUtils.isEmpty(requestedFollowUpSettlementNote)
                ? DEFAULT_SETTLEMENT_NOTE
                : requestedFollowUpSettlementNote;

        updateStatus("정산 후속 저장 중", requestedFollowUpSettlementStatus.getValue());
        bookingRepository.saveAppointmentFollowUpSettlement(
                user,
                resolveFollowUpRequestId(),
                requestedFollowUpSettlementStatus,
                settlementNote,
                new RepositoryCallback<AppointmentFollowUpRecord>() {
                    @Override
                    public void onSuccess(AppointmentFollowUpRecord result) {
                        completionAction.run();
                    }

                    @Override
                    public void onError(String message) {
                        showError("정산 후속 저장에 실패했습니다.", message);
                    }
                }
        );
    }

    private void saveAdminSettlement(User user, Runnable completionAction) {
        if (user.getRole() != UserRole.ADMIN) {
            showError("정산 후속 자동화는 관리자 계정만 지원합니다.", user.getRole().name());
            return;
        }

        String settlementNote = TextUtils.isEmpty(requestedAdminSettlementNote)
                ? DEFAULT_ADMIN_SETTLEMENT_NOTE
                : requestedAdminSettlementNote;
        updateStatus("정산 후속 저장 중", requestedAdminSettlementStatus.name());
        adminRepository.saveSettlementRecord(
                user,
                resolveAdminRequestId(),
                requestedAdminSettlementStatus,
                settlementNote,
                new RepositoryCallback<com.example.bodeul.domain.model.AdminDashboard>() {
                    @Override
                    public void onSuccess(com.example.bodeul.domain.model.AdminDashboard result) {
                        completionAction.run();
                    }

                    @Override
                    public void onError(String message) {
                        showError("정산 후속 저장에 실패했습니다.", message);
                    }
                }
        );
    }

    private void respondAdminSupport(User user, Runnable completionAction) {
        if (user.getRole() != UserRole.ADMIN) {
            showError("매니저 문의 응답 자동화는 관리자 계정만 지원합니다.", user.getRole().name());
            return;
        }

        String response = TextUtils.isEmpty(requestedAdminSupportResponse)
                ? DEFAULT_ADMIN_SUPPORT_RESPONSE
                : requestedAdminSupportResponse;
        updateStatus("매니저 문의 응답 저장 중", resolveAdminSupportInquiryId());
        adminRepository.respondSupportInquiry(
                user,
                resolveAdminSupportInquiryId(),
                response,
                new RepositoryCallback<com.example.bodeul.domain.model.AdminDashboard>() {
                    @Override
                    public void onSuccess(com.example.bodeul.domain.model.AdminDashboard result) {
                        completionAction.run();
                    }

                    @Override
                    public void onError(String message) {
                        showError("매니저 문의 응답 저장에 실패했습니다.", message);
                    }
                }
        );
    }

    private void respondAdminClientSupport(User user, Runnable completionAction) {
        if (user.getRole() != UserRole.ADMIN) {
            showError("고객 문의 응답 자동화는 관리자 계정만 지원합니다.", user.getRole().name());
            return;
        }

        String response = TextUtils.isEmpty(requestedAdminClientSupportResponse)
                ? DEFAULT_ADMIN_CLIENT_SUPPORT_RESPONSE
                : requestedAdminClientSupportResponse;
        updateStatus("고객 문의 응답 저장 중", resolveAdminClientSupportRequestId());
        adminRepository.respondClientSupportRequest(
                user,
                resolveAdminClientSupportRequestId(),
                response,
                new RepositoryCallback<com.example.bodeul.domain.model.AdminDashboard>() {
                    @Override
                    public void onSuccess(com.example.bodeul.domain.model.AdminDashboard result) {
                        completionAction.run();
                    }

                    @Override
                    public void onError(String message) {
                        showError("고객 문의 응답 저장에 실패했습니다.", message);
                    }
                }
        );
    }

    private void handleAdminNotification(User user, Runnable completionAction) {
        if (user.getRole() != UserRole.ADMIN) {
            showError("관리자 액션 센터 자동화는 관리자 계정만 지원합니다.", user.getRole().name());
            return;
        }

        String notificationId = resolveAdminNotificationId();
        if (requestedAdminActionOperation == AdminActionOperation.READ) {
            updateStatus("관리자 알림 읽음 처리 중", notificationId);
            adminRepository.markActionNotificationRead(
                    user,
                    notificationId,
                    new RepositoryCallback<com.example.bodeul.domain.model.AdminDashboard>() {
                        @Override
                        public void onSuccess(com.example.bodeul.domain.model.AdminDashboard result) {
                            completionAction.run();
                        }

                        @Override
                        public void onError(String message) {
                            showError("관리자 알림 읽음 처리에 실패했습니다.", message);
                        }
                    }
            );
            return;
        }

        boolean resolved = requestedAdminActionOperation == AdminActionOperation.RESOLVE;
        updateStatus(
                resolved ? "관리자 알림 해결 처리 중" : "관리자 알림 재열기 처리 중",
                notificationId
        );
        adminRepository.updateActionNotificationResolved(
                user,
                notificationId,
                resolved,
                new RepositoryCallback<com.example.bodeul.domain.model.AdminDashboard>() {
                    @Override
                    public void onSuccess(com.example.bodeul.domain.model.AdminDashboard result) {
                        completionAction.run();
                    }

                    @Override
                    public void onError(String message) {
                        showError("관리자 알림 상태 저장에 실패했습니다.", message);
                    }
                }
        );
    }

    @Nullable
    private File resolveUploadFile() {
        if (!TextUtils.isEmpty(requestedUploadDocumentPath)) {
            File candidate = new File(requestedUploadDocumentPath);
            if (candidate.exists() && candidate.isFile()) {
                return candidate;
            }
        }
        return createSampleUploadFile();
    }

    @Nullable
    private File createSampleUploadFile() {
        if (requestedUploadDocumentType == null) {
            return null;
        }
        return createSamplePngFile(
                "automation-" + requestedUploadDocumentType.getStorageKey() + ".png"
        );
    }

    @Nullable
    private File createSamplePngFile(String fileName) {
        File outputFile = new File(getCacheDir(), fileName);
        FileOutputStream outputStream = null;
        try {
            outputStream = new FileOutputStream(outputFile, false);
            outputStream.write(SAMPLE_PNG_BYTES);
            outputStream.flush();
            return outputFile;
        } catch (IOException exception) {
            Log.e(TAG, "자동 업로드 샘플 파일 생성 실패", exception);
            return null;
        } finally {
            if (outputStream != null) {
                try {
                    outputStream.close();
                } catch (IOException ignored) {
                    // 닫기 실패는 업로드 경로 판단에 영향이 없다.
                }
            }
        }
    }

    private void deleteSampleFile(File file) {
        if (file.exists() && !file.delete()) {
            Log.w(TAG, "자동화 샘플 파일을 삭제하지 못했습니다: " + file.getName());
        }
    }

    private void openRequestedScreen(User user) {
        Intent targetIntent = buildTargetIntent(user);
        if (targetIntent == null) {
            showError("대상 화면 인텐트를 만들지 못했습니다.", requestedScreen.name());
            return;
        }

        targetIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        ComponentName componentName = targetIntent.getComponent();
        updateStatus(
                "대상 화면으로 이동 중",
                componentName == null ? requestedScreen.name() : componentName.flattenToShortString()
        );
        startActivity(targetIntent);
        finish();
    }

    @Nullable
    private Intent buildTargetIntent(User user) {
        switch (requestedScreen) {
            case HOME:
                return AuthFlowRouter.createPostAuthIntent(this, user);
            case CLIENT_HOME:
                return new Intent(this, MainActivity.class);
            case BOOKING:
                return new Intent(this, BookingActivity.class);
            case BOOKING_HOSPITAL_SELECTOR:
                return BookingHospitalSelectorActivity.createIntent(
                        this,
                        new BookingHospitalSelection("", "")
                );
            case BOOKING_STATUS:
                return BookingStatusActivity.createIntent(this, resolveRequestId(false));
            case BOOKING_FOLLOW_UP:
                return BookingFollowUpActivity.createIntent(this, resolveRequestId(true));
            case COMPANION_CHAT:
                return user.getRole() == UserRole.MANAGER
                        ? CompanionChatActivity.createIntent(this)
                        : CompanionChatActivity.createIntent(this, resolveChatRequestId());
            case CLIENT_SUPPORT:
                return ClientSupportActivity.createIntent(this, resolveClientSupportRequestId());
            case GUARDIAN_REPORT:
                return new Intent(this, GuardianReportActivity.class);
            case MANAGER_HOME:
                return new Intent(this, ManagerActivity.class);
            case MANAGER_HISTORY:
                return ManagerHistoryActivity.createIntent(this);
            case MANAGER_GUIDE:
                return new Intent(this, ManagerGuideActivity.class);
            case MANAGER_SUPPORT:
                return ManagerSupportActivity.createIntent(this);
            case MANAGER_PROFILE:
                return ManagerProfileActivity.createIntent(this);
            case ADMIN_DASHBOARD:
                return new Intent(this, AdminActivity.class);
            default:
                return null;
        }
    }

    private String resolveRequestId(boolean completedScreen) {
        if (!TextUtils.isEmpty(requestedRequestId)) {
            return requestedRequestId;
        }
        return completedScreen ? REQUEST_ID_COMPLETED : REQUEST_ID_PROGRESS;
    }

    private String resolveChatRequestId() {
        return resolveRequestId(false);
    }

    private String resolveClientSupportRequestId() {
        if (!TextUtils.isEmpty(requestedRequestId)) {
            return requestedRequestId;
        }
        return REQUEST_ID_COMPLETED;
    }

    private String resolveFollowUpRequestId() {
        return resolveRequestId(true);
    }

    private String resolveAdminRequestId() {
        return resolveRequestId(true);
    }

    private String resolveAdminSupportInquiryId() {
        if (!TextUtils.isEmpty(requestedAdminSupportInquiryId)) {
            return requestedAdminSupportInquiryId;
        }
        return SUPPORT_INQUIRY_ID_RECEIVED;
    }

    private String resolveAdminClientSupportRequestId() {
        if (!TextUtils.isEmpty(requestedAdminClientSupportRequestId)) {
            return requestedAdminClientSupportRequestId;
        }
        return CLIENT_SUPPORT_REQUEST_ID_RECEIVED;
    }

    private String resolveAdminNotificationId() {
        if (!TextUtils.isEmpty(requestedAdminNotificationId)) {
            return requestedAdminNotificationId;
        }
        return ADMIN_NOTIFICATION_ID_SUPPORT;
    }

    @Nullable
    private UserRole parseRole(@Nullable String roleName) {
        if (TextUtils.isEmpty(roleName)) {
            return null;
        }
        try {
            return UserRole.valueOf(roleName.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            Log.w(TAG, "지원하지 않는 자동 진입 역할입니다.");
            return null;
        }
    }

    @Nullable
    private AutomationScreen parseScreen(@Nullable String screenName) {
        if (TextUtils.isEmpty(screenName)) {
            return null;
        }
        try {
            return AutomationScreen.valueOf(screenName.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            Log.w(TAG, "지원하지 않는 자동 진입 화면입니다.");
            return null;
        }
    }

    @Nullable
    private ManagerDocumentFileType parseDocumentFileType(@Nullable String documentTypeName) {
        if (TextUtils.isEmpty(documentTypeName)) {
            return null;
        }
        String normalizedName = documentTypeName.trim();
        ManagerDocumentFileType byStorageKey = ManagerDocumentFileType.fromStorageKey(normalizedName);
        if (byStorageKey != null) {
            return byStorageKey;
        }
        try {
            return ManagerDocumentFileType.valueOf(normalizedName.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            Log.w(TAG, "지원하지 않는 자동 업로드 서류 유형입니다.");
            return null;
        }
    }

    @Nullable
    private AdminSettlementStatus parseAdminSettlementStatus(@Nullable String statusName) {
        if (TextUtils.isEmpty(statusName)) {
            return null;
        }
        try {
            return AdminSettlementStatus.valueOf(statusName.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            Log.w(TAG, "지원하지 않는 관리자 정산 상태입니다.");
            return null;
        }
    }

    @Nullable
    private AdminActionOperation parseAdminActionOperation(@Nullable String operationName) {
        if (TextUtils.isEmpty(operationName)) {
            return null;
        }
        try {
            return AdminActionOperation.valueOf(operationName.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            Log.w(TAG, "지원하지 않는 관리자 알림 작업입니다.");
            return null;
        }
    }

    private void updateStatus(String title, String detail) {
        textPrimary.setText(title);
        textSecondary.setText(detail);
        Log.i(TAG, title + " / " + detail);
    }

    private void showError(String title, String detail) {
        updateStatus(title, detail);
    }

    private String normalizeText(@Nullable String value) {
        return value == null ? "" : value.trim();
    }

    private enum AutomationScreen {
        HOME,
        CLIENT_HOME,
        BOOKING,
        BOOKING_HOSPITAL_SELECTOR,
        BOOKING_API_SMOKE,
        BOOKING_STATUS,
        BOOKING_FOLLOW_UP,
        COMPANION_CHAT,
        CLIENT_SUPPORT,
        GUARDIAN_REPORT,
        MANAGER_HOME,
        MANAGER_HISTORY,
        MANAGER_GUIDE,
        MANAGER_SUPPORT,
        MANAGER_PROFILE,
        ADMIN_DASHBOARD
    }

    private enum AdminActionOperation {
        READ,
        RESOLVE,
        REOPEN
    }

    private enum BaselineAccount {
        ADMIN(UserRole.ADMIN, "admin@bodeul.app"),
        MANAGER(UserRole.MANAGER, "manager@bodeul.app"),
        PATIENT(UserRole.PATIENT, "patient@bodeul.app"),
        GUARDIAN(UserRole.GUARDIAN, "guardian@bodeul.app");

        private final UserRole role;
        private final String email;

        BaselineAccount(UserRole role, String email) {
            this.role = role;
            this.email = email;
        }

        @Nullable
        private static BaselineAccount fromRole(UserRole role) {
            for (BaselineAccount account : values()) {
                if (account.role == role) {
                    return account;
                }
            }
            return null;
        }
    }
}
