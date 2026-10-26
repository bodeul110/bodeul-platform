package com.example.bodeul.ui.booking;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.example.bodeul.MainActivity;
import com.example.bodeul.R;
import com.example.bodeul.data.AuthRepository;
import com.example.bodeul.data.BookingRepository;
import com.example.bodeul.data.RepositoryCallback;
import com.example.bodeul.data.ServiceLocator;
import com.example.bodeul.domain.model.AppointmentFollowUpRecord;
import com.example.bodeul.domain.model.AppointmentRequest;
import com.example.bodeul.domain.model.AppointmentRequestDetail;
import com.example.bodeul.domain.model.AppointmentStatus;
import com.example.bodeul.domain.model.User;
import com.example.bodeul.domain.model.UserRole;
import com.example.bodeul.ui.auth.AuthFlowRouter;
import com.example.bodeul.ui.auth.ProfileCompletionActivity;
import com.example.bodeul.ui.auth.RoleSelectionActivity;
import com.example.bodeul.ui.common.AppointmentProgressComposer;
import com.example.bodeul.ui.report.GuardianReportActivity;
import com.example.bodeul.util.LegacyManagerLocationSharingPolicy;
import com.example.bodeul.util.StatePanelHelper;
import com.google.android.material.button.MaterialButton;

/**
 * 환자와 보호자가 예약 상세와 매칭 대기 상태를 확인하는 공용 화면이다.
 */
public class BookingStatusActivity extends AppCompatActivity {
    private static final String EXTRA_REQUEST_ID = "requestId";

    private AuthRepository authRepository;
    private BookingRepository bookingRepository;
    private BookingStatusCoordinator bookingStatusCoordinator;
    private BookingStatusBinder bookingStatusBinder;

    private View bookingStatusStatePanel;
    private View bookingStatusContentContainer;
    private ProgressBar progressBookingStatus;
    private MaterialButton buttonPrimary;
    private MaterialButton buttonSecondary;
    private MaterialButton buttonBankTransferPayment;
    private MaterialButton buttonGuardianSharingConsent;

    private User currentUser;
    private AppointmentRequestDetail currentDetail;
    private BookingStatusScreenModel currentScreenModel;
    private String requestId;

    public static Intent createIntent(Context context, String requestId) {
        Intent intent = new Intent(context, BookingStatusActivity.class);
        intent.putExtra(EXTRA_REQUEST_ID, requestId);
        return intent;
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_booking_status);

        requestId = getIntent().getStringExtra(EXTRA_REQUEST_ID);
        authRepository = ServiceLocator.provideAuthRepository(this);
        bookingRepository = ServiceLocator.provideBookingRepository(this);

        BookingPresentationFormatter formatter = new BookingPresentationFormatter(this);
        bookingStatusCoordinator = new BookingStatusCoordinator(
                this,
                formatter,
                new AppointmentProgressComposer(this)
        );
        bookingStatusStatePanel = findViewById(R.id.bookingStatusStatePanel);
        bookingStatusContentContainer = findViewById(R.id.bookingStatusContentContainer);
        progressBookingStatus = findViewById(R.id.progressBookingStatus);
        buttonPrimary = findViewById(R.id.buttonBookingStatusPrimary);
        buttonSecondary = findViewById(R.id.buttonBookingStatusSecondary);
        buttonBankTransferPayment = findViewById(
                R.id.buttonBookingStatusBankTransferPayment);
        buttonGuardianSharingConsent = findViewById(
                R.id.buttonBookingStatusGuardianSharingConsent);

        bookingStatusBinder = new BookingStatusBinder(
                this,
                getLayoutInflater(),
                findViewById(R.id.textBookingStatusMode),
                findViewById(R.id.textBookingStatusHeroBadge),
                findViewById(R.id.textBookingStatusHeroTitle),
                findViewById(R.id.textBookingStatusHeroBody),
                findViewById(R.id.textBookingStatusProgressTitle),
                findViewById(R.id.textBookingStatusProgressBody),
                findViewById(R.id.bookingStatusStageContainer),
                findViewById(R.id.cardBookingStatusGuide),
                findViewById(R.id.textBookingStatusGuideTitle),
                findViewById(R.id.textBookingStatusGuideBody),
                findViewById(R.id.bookingStatusParticipantContainer),
                findViewById(R.id.bookingStatusSummaryContainer),
                findViewById(R.id.cardBookingStatusLive),
                findViewById(R.id.bookingStatusLiveContainer),
                findViewById(R.id.cardBookingStatusReport),
                findViewById(R.id.bookingStatusReportContainer),
                buttonPrimary,
                buttonSecondary
        );

        findViewById(R.id.buttonBackBookingStatus).setOnClickListener(view -> finish());
        buttonPrimary.setOnClickListener(view -> handleAction(
                currentScreenModel == null ? null : currentScreenModel.getPrimaryAction()
        ));
        buttonSecondary.setOnClickListener(view -> handleAction(
                currentScreenModel == null ? null : currentScreenModel.getSecondaryAction()
        ));
        buttonBankTransferPayment.setOnClickListener(view -> openBankTransferPayment());
        buttonGuardianSharingConsent.setOnClickListener(view -> openGuardianSharingConsent());
        bookingStatusContentContainer.setVisibility(View.GONE);
    }

    @Override
    protected void onStart() {
        super.onStart();
        reloadDetail();
    }

    private void reloadDetail() {
        if (TextUtils.isEmpty(requestId)) {
            showLoadErrorState(getString(R.string.booking_status_request_missing));
            return;
        }

        setLoading(true);
        hideBlockingState();
        authRepository.getCurrentUser(new RepositoryCallback<User>() {
            @Override
            public void onSuccess(User result) {
                if (AuthFlowRouter.requiresProfileCompletion(result)) {
                    openProfileCompletion();
                    return;
                }
                if (result.getRole() != UserRole.PATIENT && result.getRole() != UserRole.GUARDIAN) {
                    setLoading(false);
                    showPermissionState();
                    return;
                }

                currentUser = result;
                bookingRepository.getAppointmentRequestDetail(result, requestId, new RepositoryCallback<AppointmentRequestDetail>() {
                    @Override
                    public void onSuccess(AppointmentRequestDetail result) {
                        currentDetail = result;
                        loadFollowUpIfNeeded(result);
                    }

                    @Override
                    public void onError(String message) {
                        setLoading(false);
                        showLoadErrorState(message);
                    }
                });
            }

            @Override
            public void onError(String message) {
                setLoading(false);
                showAuthState();
            }
        });
    }

    private void loadFollowUpIfNeeded(AppointmentRequestDetail detail) {
        if (currentUser == null) {
            setLoading(false);
            showAuthState();
            return;
        }
        if (detail.getAppointmentRequest().getStatus() != AppointmentStatus.COMPLETED) {
            renderDetail(detail, null);
            return;
        }

        bookingRepository.getAppointmentFollowUp(
                currentUser,
                detail.getAppointmentRequest().getId(),
                new RepositoryCallback<AppointmentFollowUpRecord>() {
                    @Override
                    public void onSuccess(AppointmentFollowUpRecord result) {
                        renderDetail(detail, result);
                    }

                    @Override
                    public void onError(String message) {
                        renderDetail(
                                detail,
                                AppointmentFollowUpRecord.empty(detail.getAppointmentRequest().getId())
                        );
                    }
                }
        );
    }

    private void renderDetail(
            AppointmentRequestDetail detail,
            @Nullable AppointmentFollowUpRecord followUpRecord
    ) {
        currentScreenModel = bookingStatusCoordinator.createScreenModel(
                currentUser,
                detail,
                bookingRepository.isFirebaseBacked(),
                followUpRecord
        );
        bookingStatusBinder.bindScreen(currentScreenModel);
        buttonBankTransferPayment.setVisibility(
                currentUser != null && BankTransferPaymentAccessPolicy.canOpen(
                        currentUser.getRole(),
                        detail.getAppointmentRequest().getPaymentMethodCode())
                        ? View.VISIBLE
                        : View.GONE);
        buttonGuardianSharingConsent.setVisibility(
                currentUser != null && currentUser.getRole() == UserRole.PATIENT
                        ? View.VISIBLE
                        : View.GONE);
        bookingStatusContentContainer.setVisibility(View.VISIBLE);
        hideBlockingState();
        setLoading(false);
    }

    private void handleAction(@Nullable BookingStatusActionModel actionModel) {
        if (actionModel == null || currentDetail == null) {
            return;
        }
        if (currentUser == null || !BookingMutationPolicy.isStatusActionAllowed(
                currentUser.getRole(), actionModel.getActionType())) {
            showGuardianMutationBlockedDialog();
            return;
        }
        switch (actionModel.getActionType()) {
            case EDIT:
                openEditRequest();
                return;
            case CANCEL:
                confirmCancelRequest();
                return;
            case OPEN_LIVE_TRACKING:
                openLiveTracking();
                return;
            case OPEN_REPORT:
                openGuardianReport();
                return;
            case OPEN_FOLLOW_UP:
                openFollowUp();
                return;
            case OPEN_HISTORY:
                openBookingHistory();
                return;
            case REFRESH:
            default:
                reloadDetail();
        }
    }

    private void openEditRequest() {
        startActivity(BookingActivity.createEditIntent(this, currentDetail.getAppointmentRequest().getId()));
    }

    private void confirmCancelRequest() {
        AppointmentRequest request = currentDetail.getAppointmentRequest();
        int bodyResId = request.getStatus() == AppointmentStatus.MATCHED
                ? R.string.booking_cancel_dialog_body_matched
                : R.string.booking_cancel_dialog_body_requested;
        new AlertDialog.Builder(this)
                .setTitle(R.string.booking_cancel_dialog_title)
                .setMessage(bodyResId)
                .setPositiveButton(R.string.booking_cancel_dialog_confirm, (dialogInterface, which) ->
                        performCancelRequest())
                .setNegativeButton(R.string.booking_cancel_dialog_keep, null)
                .show();
    }

    private void performCancelRequest() {
        if (currentUser == null || currentDetail == null) {
            showAuthState();
            return;
        }

        if (!BookingMutationPolicy.isAllowed(
                currentUser.getRole(), BookingMutationPolicy.Operation.CANCEL)) {
            showGuardianMutationBlockedDialog();
            return;
        }

        setLoading(true);
        BookingMutationPolicy.runIfAllowed(
                currentUser.getRole(),
                BookingMutationPolicy.Operation.CANCEL,
                () -> bookingRepository.cancelAppointmentRequest(
                        currentUser,
                        currentDetail.getAppointmentRequest().getId(),
                        new RepositoryCallback<AppointmentRequest>() {
                    @Override
                    public void onSuccess(AppointmentRequest result) {
                        setLoading(false);
                        reloadDetail();
                    }

                    @Override
                    public void onError(String message) {
                        setLoading(false);
                        showLoadErrorState(message);
                    }
                        }
                ));
    }

    private void showGuardianMutationBlockedDialog() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.booking_guardian_creation_blocked_title)
                .setMessage(R.string.booking_guardian_creation_blocked_body)
                .setPositiveButton(android.R.string.ok, null)
                .show();
    }

    private void openGuardianReport() {
        startActivity(new Intent(this, GuardianReportActivity.class));
    }

    private void openLiveTracking() {
        if (!LegacyManagerLocationSharingPolicy.isEnabled(this)) {
            Toast.makeText(
                    this,
                    R.string.legacy_manager_location_disabled_body,
                    Toast.LENGTH_SHORT
            ).show();
            return;
        }
        if (currentDetail == null) {
            return;
        }
        startActivity(BookingLiveLocationActivity.createIntent(
                this,
                currentDetail.getAppointmentRequest().getId()
        ));
    }

    private void openFollowUp() {
        if (currentDetail == null) {
            return;
        }
        startActivity(BookingFollowUpActivity.createIntent(
                this,
                currentDetail.getAppointmentRequest().getId()
        ));
    }

    private void openBookingHistory() {
        Intent intent = new Intent(this, ClientBookingHistoryActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(intent);
        finish();
    }

    private void openGuardianSharingConsent() {
        if (currentDetail == null || currentUser == null
                || currentUser.getRole() != UserRole.PATIENT) {
            return;
        }
        startActivity(GuardianSharingConsentActivity.createIntent(
                this,
                currentDetail.getAppointmentRequest().getId()
        ));
    }

    private void openBankTransferPayment() {
        if (currentDetail == null || currentUser == null
                || !BankTransferPaymentAccessPolicy.canOpen(
                currentUser.getRole(),
                currentDetail.getAppointmentRequest().getPaymentMethodCode())) {
            return;
        }
        startActivity(BankTransferPaymentActivity.createIntent(
                this,
                currentDetail.getAppointmentRequest().getId()));
    }

    private void setLoading(boolean loading) {
        progressBookingStatus.setVisibility(loading ? View.VISIBLE : View.GONE);
        buttonPrimary.setEnabled(!loading);
        buttonSecondary.setEnabled(!loading);
        buttonBankTransferPayment.setEnabled(!loading);
        buttonGuardianSharingConsent.setEnabled(!loading);
    }

    private void showPermissionState() {
        showBlockingState(
                StatePanelHelper.Tone.WARNING,
                getString(R.string.state_badge_permission),
                getString(R.string.state_permission_title, getString(R.string.booking_status_title)),
                getString(R.string.state_permission_body),
                getString(R.string.state_action_open_home),
                view -> openHome(),
                getString(R.string.state_action_open_login),
                view -> openRoleSelection()
        );
    }

    private void showAuthState() {
        showBlockingState(
                StatePanelHelper.Tone.WARNING,
                getString(R.string.state_badge_auth),
                getString(R.string.state_auth_title),
                getString(R.string.state_auth_body),
                getString(R.string.state_action_open_login),
                view -> openRoleSelection(),
                null,
                null
        );
    }

    private void showLoadErrorState(String message) {
        String body = getString(R.string.state_load_error_body);
        if (!TextUtils.isEmpty(message)) {
            body = body + "\n\n" + message;
        }
        showBlockingState(
                StatePanelHelper.Tone.ERROR,
                getString(R.string.state_badge_error),
                getString(R.string.state_load_error_title, getString(R.string.booking_status_title)),
                body,
                getString(R.string.state_action_retry),
                view -> reloadDetail(),
                getString(R.string.state_action_open_home),
                view -> openHome()
        );
    }

    private void showBlockingState(
            StatePanelHelper.Tone tone,
            CharSequence badge,
            CharSequence title,
            CharSequence body,
            @Nullable CharSequence primaryText,
            @Nullable View.OnClickListener primaryListener,
            @Nullable CharSequence secondaryText,
            @Nullable View.OnClickListener secondaryListener
    ) {
        StatePanelHelper.show(
                bookingStatusStatePanel,
                tone,
                badge,
                title,
                body,
                primaryText,
                primaryListener,
                secondaryText,
                secondaryListener
        );
        bookingStatusContentContainer.setVisibility(View.GONE);
    }

    private void hideBlockingState() {
        StatePanelHelper.hide(bookingStatusStatePanel);
    }

    private void openHome() {
        startActivity(new Intent(this, MainActivity.class));
        finish();
    }

    private void openRoleSelection() {
        Intent intent = new Intent(this, RoleSelectionActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    private void openProfileCompletion() {
        Intent intent = ProfileCompletionActivity.createIntent(this);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }
}
