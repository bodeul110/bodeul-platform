package com.example.bodeul.ui.booking;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.view.WindowCompat;

import com.example.bodeul.MainActivity;
import com.example.bodeul.R;
import com.example.bodeul.data.AuthRepository;
import com.example.bodeul.data.BookingRepository;
import com.example.bodeul.data.RepositoryCallback;
import com.example.bodeul.data.ServiceLocator;
import com.example.bodeul.domain.model.AppointmentRequest;
import com.example.bodeul.domain.model.AppointmentStatus;
import com.example.bodeul.domain.model.BookingHospitalSelection;
import com.example.bodeul.domain.model.BookingMeetingLocationSelection;
import com.example.bodeul.domain.model.BookingPaymentApproval;
import com.example.bodeul.domain.model.BookingRequestDraft;
import com.example.bodeul.domain.model.User;
import com.example.bodeul.domain.model.UserRole;
import com.example.bodeul.ui.auth.AuthFlowRouter;
import com.example.bodeul.ui.auth.ProfileCompletionActivity;
import com.example.bodeul.ui.auth.RoleSelectionActivity;
import com.example.bodeul.ui.navigation.ClientBottomNavigationBinder;
import com.example.bodeul.ui.navigation.ClientBottomNavigationRouter;
import com.example.bodeul.ui.navigation.ClientBottomNavigationTab;
import com.example.bodeul.util.EnvironmentModeBadgeHelper;
import com.example.bodeul.util.StatePanelHelper;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

/**
 * 환자와 보호자의 예약 신청 화면에서 흐름 제어만 담당한다.
 */
public class BookingActivity extends AppCompatActivity {
    public enum HomeEntry { LOCATION, APPOINTMENT }
    private static final String EXTRA_HOME_ENTRY = "homeEntry";
    private boolean homeEntryConsumed;

    public static Intent createHomeEntryIntent(Context context, HomeEntry entry) {
        return new Intent(context, BookingActivity.class).putExtra(EXTRA_HOME_ENTRY, entry.name());
    }
    private static final String EXTRA_EDIT_REQUEST_ID = "editRequestId";

    private AuthRepository authRepository;
    private BookingRepository bookingRepository;
    private BookingCoordinator bookingCoordinator;
    private BookingDashboardBinder dashboardBinder;
    private BookingFormBinder formBinder;
    private BookingMainScreenBinder mainScreenBinder;
    private BookingWizardBinder wizard;
    private Bundle restoredState;
    private boolean formInitialized;
    private boolean contentReady;
    private boolean loading;
    private boolean submitting;
    private int loadGeneration;

    private View bookingStatePanel;
    private View bookingContentContainer;
    private ProgressBar progressBooking;
    private TextView textBookingMode;
    private BottomNavigationView bottomNavigation;

    private User currentUser;
    private BookingDashboard currentDashboard;
    private AppointmentRequest editingRequest;
    private String pendingEditRequestId;
    private ActivityResultLauncher<Intent> healthProfileLauncher;
    private ActivityResultLauncher<Intent> appointmentSelectorLauncher;
    private ActivityResultLauncher<Intent> hospitalSelectorLauncher;
    private ActivityResultLauncher<Intent> locationSelectorLauncher;
    private ActivityResultLauncher<Intent> paymentApprovalLauncher;
    @Nullable
    private BookingRequestDraft pendingSubmissionDraft;

    public static Intent createEditIntent(Context context, String requestId) {
        Intent intent = new Intent(context, BookingActivity.class);
        intent.putExtra(EXTRA_EDIT_REQUEST_ID, requestId);
        return intent;
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        setTheme(R.style.Theme_Bodeul_FinalBooking);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_booking);
        pendingEditRequestId = getIntent().getStringExtra(EXTRA_EDIT_REQUEST_ID);
        homeEntryConsumed = savedInstanceState != null && savedInstanceState.getBoolean(EXTRA_HOME_ENTRY);
        restoredState = savedInstanceState == null ? null : savedInstanceState.getBundle("bookingState");

        authRepository = provideAuthRepository();
        bookingRepository = provideBookingRepository();
        bookingCoordinator = new BookingCoordinator(bookingRepository);
        healthProfileLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() != RESULT_OK || result.getData() == null) {
                        return;
                    }
                    BookingHealthProfileSelection selection = BookingHealthProfileActivity.parseResult(
                            result.getData()
                    );
                    if (formBinder != null && selection.hasRequiredCondition()) {
                        formBinder.applyHealthProfileSelection(selection);
                    }
                }
        );
        appointmentSelectorLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() != RESULT_OK || result.getData() == null) {
                        return;
                    }
                    String appointmentAt = BookingAppointmentSelectorActivity.parseResult(
                            result.getData()
                    );
                    if (!TextUtils.isEmpty(appointmentAt) && formBinder != null) {
                        formBinder.applyAppointmentAt(appointmentAt);
                    }
                }
        );
        hospitalSelectorLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() != RESULT_OK || result.getData() == null) {
                        return;
                    }
                    BookingHospitalSelection selection = BookingHospitalSelectorActivity.parseResult(
                            result.getData()
                    );
                    if (!selection.isComplete()) {
                        return;
                    }
                    formBinder.applyHospitalSelection(selection);
                }
        );
        locationSelectorLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() != RESULT_OK || result.getData() == null) {
                        return;
                    }
                    BookingMeetingLocationSelection selection = BookingLocationSelectorActivity.parseResult(
                            result.getData()
                    );
                    if (!selection.isComplete()) {
                        return;
                    }
                    formBinder.applyMeetingLocationSelection(selection);
                }
        );
        paymentApprovalLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> handlePaymentApprovalResult(result.getResultCode(), result.getData())
        );

        bookingStatePanel = findViewById(R.id.bookingStatePanel);
        bookingContentContainer = findViewById(R.id.bookingContentContainer);
        progressBooking = findViewById(R.id.progressBooking);
        textBookingMode = findViewById(R.id.textBookingMode);
        bottomNavigation = findViewById(R.id.clientBottomNavigation);
        wizard = new BookingWizardBinder(findViewById(R.id.bookingRoot));

        mainScreenBinder = new BookingMainScreenBinder(
                this,
                findViewById(R.id.buttonBookingSelectHospital),
                findViewById(R.id.textBookingVisitDateSummary),
                findViewById(R.id.textBookingVisitTimeSummary)
        );

        BookingPresentationFormatter formatter = new BookingPresentationFormatter(this);
        BookingAppointmentSelector appointmentSelector = new BookingAppointmentSelector(
                this,
                findViewById(R.id.layoutBookingAppointmentAt),
                findViewById(R.id.inputBookingAppointmentAt),
                findViewById(R.id.buttonBookingAppointmentSummary),
                findViewById(R.id.textBookingAppointmentError),
                findViewById(R.id.buttonBookingQuickToday),
                findViewById(R.id.buttonBookingQuickTomorrow),
                findViewById(R.id.buttonBookingQuickDayAfterTomorrow),
                findViewById(R.id.buttonBookingQuickMorning),
                findViewById(R.id.buttonBookingQuickAfternoon),
                findViewById(R.id.buttonBookingQuickLateAfternoon),
                appointmentSelectorLauncher
        );
        appointmentSelector.setOnAppointmentChangedListener(mainScreenBinder::bindAppointment);

        dashboardBinder = new BookingDashboardBinder(
                this,
                getLayoutInflater(),
                formatter,
                findViewById(R.id.textBookingRequesterName),
                findViewById(R.id.textBookingRequesterRole),
                findViewById(R.id.textBookingRequesterPhone),
                findViewById(R.id.cardBookingLatest),
                findViewById(R.id.textBookingLatestStatus),
                findViewById(R.id.textBookingLatestTitle),
                findViewById(R.id.textBookingLatestBody),
                findViewById(R.id.bookingRequestsContainer)
        );

        formBinder = new BookingFormBinder(
                this,
                formatter,
                new BookingPriceEstimator(),
                appointmentSelector,
                findViewById(R.id.textBookingFormTitle),
                findViewById(R.id.textBookingFormBadge),
                findViewById(R.id.textBookingFormHelper),
                findViewById(R.id.textBookingLinkedSection),
                findViewById(R.id.textBookingLinkedHelper),
                findViewById(R.id.textBookingEstimateBase),
                findViewById(R.id.textBookingEstimateOption),
                findViewById(R.id.textBookingEstimateDiscount),
                findViewById(R.id.textBookingEstimateFinal),
                findViewById(R.id.textBookingPaymentHelper),
                findViewById(R.id.textBookingHealthProfileSummary),
                findViewById(R.id.textBookingHealthProfileError),
                findViewById(R.id.textBookingHospitalSelectionError),
                findViewById(R.id.layoutBookingHealthSummary),
                findViewById(R.id.layoutBookingMedicationSummary),
                findViewById(R.id.layoutBookingLinkedName),
                findViewById(R.id.layoutBookingLinkedPhone),
                findViewById(R.id.layoutBookingLinkedEmail),
                findViewById(R.id.layoutBookingHospitalName),
                findViewById(R.id.layoutBookingDepartmentName),
                findViewById(R.id.layoutBookingMeetingPlace),
                findViewById(R.id.layoutBookingSpecialNotes),
                findViewById(R.id.inputBookingHealthSummary),
                findViewById(R.id.inputBookingMedicationSummary),
                findViewById(R.id.inputBookingLinkedName),
                findViewById(R.id.inputBookingLinkedPhone),
                findViewById(R.id.inputBookingLinkedEmail),
                findViewById(R.id.inputBookingHospitalName),
                findViewById(R.id.inputBookingDepartmentName),
                findViewById(R.id.inputBookingMeetingPlace),
                findViewById(R.id.inputBookingSpecialNotes),
                findViewById(R.id.buttonBookingSelectHospital),
                findViewById(R.id.buttonBookingSelectMeetingPlace),
                findViewById(R.id.buttonSubmitBooking),
                findViewById(R.id.buttonCancelBookingEdit),
                findViewById(R.id.buttonBookingHealthProfile),
                findViewById(R.id.buttonBookingMobilityIndependent),
                findViewById(R.id.buttonBookingMobilityWalkingAid),
                findViewById(R.id.buttonBookingMobilityWheelchair),
                findViewById(R.id.buttonBookingTripOneWay),
                findViewById(R.id.buttonBookingTripRoundTrip),
                findViewById(R.id.buttonBookingManagerGenderAny),
                findViewById(R.id.buttonBookingManagerGenderFemale),
                findViewById(R.id.buttonBookingManagerGenderMale),
                findViewById(R.id.buttonBookingPaymentBankTransfer),
                findViewById(R.id.buttonBookingPaymentCard),
                findViewById(R.id.buttonBookingPaymentEasyPay),
                findViewById(R.id.buttonBookingPaymentOnSite),
                findViewById(R.id.buttonBookingCouponNone),
                findViewById(R.id.buttonBookingCouponFirstVisit),
                findViewById(R.id.buttonBookingCouponFamily)
        );

        findViewById(R.id.buttonBackBooking).setOnClickListener(view -> goBack());
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override public void handleOnBackPressed() { goBack(); }
        });
        findViewById(R.id.buttonBookingSelf).setOnClickListener(view -> {
            if (currentUser != null && currentUser.getRole() == UserRole.PATIENT) wizard.selectPatient();
            else showGuardianMutationBlockedDialog();
        });
        findViewById(R.id.buttonBookingGuardian).setOnClickListener(view -> showGuardianMutationBlockedDialog());
        findViewById(R.id.buttonBookingNext).setOnClickListener(view -> {
            if (!contentReady || loading || !wizard.hasParticipant()) return;
            if (formBinder.validateStep(wizard.step())) wizard.showStep(wizard.step().next());
        });
        formBinder.setOnHealthProfileSelectorClickListener(view -> openHealthProfile());
        formBinder.setOnHospitalSelectorClickListener(view -> openHospitalSelector());
        formBinder.setOnMeetingPlaceSelectorClickListener(view -> openLocationSelector());
        formBinder.setOnHospitalSelectionChangedListener(selection -> {
            mainScreenBinder.bindHospitalSelection(selection);
        });
        ((MaterialButton) findViewById(R.id.buttonSubmitBooking)).setOnClickListener(view -> submitAppointmentRequest());
        ((MaterialButton) findViewById(R.id.buttonCancelBookingEdit)).setOnClickListener(view -> exitEditMode());
        findViewById(R.id.buttonCloseBooking).setOnClickListener(view -> wizard.showHistory(!wizard.isHistory()));

        bottomNavigation.setVisibility(View.GONE);
        ClientBottomNavigationBinder.bind(
                bottomNavigation,
                ClientBottomNavigationTab.SCHEDULE_HISTORY,
                tab -> ClientBottomNavigationRouter.open(
                        this,
                        ClientBottomNavigationTab.SCHEDULE_HISTORY,
                        tab
                )
        );
        configureSystemBars();

        EnvironmentModeBadgeHelper.bind(
                textBookingMode,
                EnvironmentModeBadgeHelper.resolveUserFacingLabel(
                        this,
                        bookingCoordinator.isFirebaseBacked()
                )
        );
        bookingContentContainer.setVisibility(View.GONE);
    }

    @Override
    protected void onStart() {
        super.onStart();
        reloadDashboard();
    }

    @Override
    protected void onDestroy() {
        loadGeneration++;
        super.onDestroy();
    }

    private void reloadDashboard() {
        final int generation = ++loadGeneration;
        contentReady = false;
        setLoading(true);
        hideBlockingState();
        bookingContentContainer.setVisibility(View.GONE);
        authRepository.getCurrentUser(new RepositoryCallback<User>() {
            @Override public void onSuccess(User result) {
                if (generation != loadGeneration || isDestroyed()) return;
                if (AuthFlowRouter.requiresProfileCompletion(result)) {
                    clearAccountState();
                    openProfileCompletion();
                    return;
                }
                if (!supportsBookingRole(result.getRole())) {
                    clearAccountState();
                    setLoading(false);
                    showPermissionState();
                    return;
                }
                if (currentUser != null && (!TextUtils.equals(currentUser.getId(), result.getId())
                        || currentUser.getRole() != result.getRole())) clearAccountState();
                currentUser = result;
                bookingCoordinator.loadDashboard(result, new RepositoryCallback<BookingDashboard>() {
                    @Override public void onSuccess(BookingDashboard dashboard) {
                        if (generation != loadGeneration || isDestroyed()) return;
                        currentDashboard = dashboard;
                        hideBlockingState();
                        dashboardBinder.bindDashboard(dashboard, new BookingRequestCardBinder.ActionListener() {
                            @Override public void onOpenRequest(AppointmentRequest request) { openRequestDetail(request); }
                            @Override public void onEditRequest(AppointmentRequest request) { startEditingRequest(request); }
                            @Override public void onCancelRequest(AppointmentRequest request) { cancelRequest(request); }
                        });
                        bindFormState(dashboard);
                        contentReady = true;
                        setLoading(false);
                        bookingContentContainer.setVisibility(View.VISIBLE);
                        openHomeEntryOnce();
                    }
                    @Override public void onError(String message) {
                        if (generation != loadGeneration || isDestroyed()) return;
                        setLoading(false);
                        showLoadErrorState(message);
                    }
                });
            }
            @Override public void onError(String message) {
                if (generation != loadGeneration || isDestroyed()) return;
                clearAccountState();
                setLoading(false);
                showAuthState();
            }
        });
    }

    private void bindFormState(BookingDashboard dashboard) {
        if (currentUser == null) return;
        Bundle form = formInitialized ? formBinder.saveInputState() : null;
        Bundle flow = null;
        boolean restore = restoredState != null
                && TextUtils.equals(currentUser.getId(), restoredState.getString("owner"))
                && currentUser.getRole().name().equals(restoredState.getString("role"));
        if (restore) {
            form = restoredState.getBundle("form");
            flow = restoredState.getBundle("wizard");
            pendingEditRequestId = restoredState.getString("edit");
        }
        String editingId = editingRequest == null ? pendingEditRequestId : editingRequest.getId();
        pendingEditRequestId = null;
        if (!TextUtils.isEmpty(editingId)) {
            AppointmentRequest refreshed = findRequestById(dashboard, editingId);
            if (refreshed == null || refreshed.getStatus() != AppointmentStatus.REQUESTED
                    || !BookingMutationPolicy.isAllowed(currentUser.getRole(), BookingMutationPolicy.Operation.UPDATE)) {
                editingRequest = null;
                form = null;
                flow = null;
                pendingSubmissionDraft = null;
                wizard.reset();
                wizard.showHistory(true);
                Toast.makeText(this, R.string.final_booking_edit_unavailable, Toast.LENGTH_LONG).show();
            } else {
                editingRequest = refreshed;
                wizard.selectPatient();
            }
        }
        if (editingRequest == null) formBinder.bindCreateMode(currentUser);
        else formBinder.bindEditMode(currentUser, editingRequest);
        if (form != null) formBinder.restoreInputState(form);
        if (flow != null) wizard.restoreState(flow);
        restoredState = null;
        formInitialized = true;
    }

    private void clearAccountState() {
        currentUser = null;
        currentDashboard = null;
        editingRequest = null;
        pendingEditRequestId = null;
        pendingSubmissionDraft = null;
        restoredState = null;
        formInitialized = false;
        contentReady = false;
        wizard.reset();
    }

    private void openHomeEntryOnce() {
        if (homeEntryConsumed || editingRequest != null || currentUser == null) return;
        homeEntryConsumed = true;
        String entry = getIntent().getStringExtra(EXTRA_HOME_ENTRY);
        if (entry != null && currentUser.getRole() == UserRole.PATIENT) {
            wizard.selectPatient();
            wizard.showStep(BookingStep.VISIT);
        }
        if (HomeEntry.APPOINTMENT.name().equals(entry)) {
            findViewById(R.id.buttonBookingAppointmentSummary).performClick();
        } else if (HomeEntry.LOCATION.name().equals(entry)) {
            if (formBinder.getHospitalSelection().isComplete()) openLocationSelector();
            else openHospitalSelector();
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        outState.putBoolean(EXTRA_HOME_ENTRY, homeEntryConsumed);
        if (currentUser != null && formInitialized) {
            Bundle state = new Bundle();
            state.putString("owner", currentUser.getId());
            state.putString("role", currentUser.getRole().name());
            state.putString("edit", editingRequest == null ? null : editingRequest.getId());
            state.putBundle("form", formBinder.saveInputState());
            state.putBundle("wizard", wizard.saveState());
            outState.putBundle("bookingState", state);
        } else if (restoredState != null) {
            outState.putBundle("bookingState", restoredState);
        }
        super.onSaveInstanceState(outState);
    }

    @Nullable
    private AppointmentRequest findRequestById(BookingDashboard dashboard, String requestId) {
        for (AppointmentRequest request : dashboard.getRequests()) {
            if (request.getId().equals(requestId)) {
                return request;
            }
        }
        return null;
    }

    private void submitAppointmentRequest() {
        if (!contentReady || loading || submitting || pendingSubmissionDraft != null) return;
        if (currentUser == null) {
            showAuthState();
            return;
        }
        BookingMutationPolicy.Operation operation = editingRequest == null
                ? BookingMutationPolicy.Operation.CREATE
                : BookingMutationPolicy.Operation.UPDATE;
        if (!BookingMutationPolicy.isAllowed(currentUser.getRole(), operation)) {
            showGuardianMutationBlockedDialog();
            return;
        }

        for (BookingStep step : BookingStep.values()) {
            if (!formBinder.validateStep(step)) {
                wizard.showStep(step);
                return;
            }
        }

        BookingRequestDraft bookingRequestDraft = formBinder.buildDraft();
        if (bookingRequestDraft == null) {
            return;
        }

        pendingSubmissionDraft = bookingRequestDraft;
        setLoading(true);
        openPaymentApproval(bookingRequestDraft);
    }

    protected void openPaymentApproval(BookingRequestDraft bookingRequestDraft) {
        paymentApprovalLauncher.launch(BookingPaymentApprovalActivity.createIntent(
                this,
                BookingPaymentCheckoutSnapshot.fromDraft(bookingRequestDraft)
        ));
    }

    protected void handlePaymentApprovalResult(int resultCode, @Nullable Intent data) {
        if (resultCode != RESULT_OK || data == null || pendingSubmissionDraft == null) {
            // 프로세스 재생성 뒤에는 작성 내용만 복원하고 이전 결제 결과로 자동 제출하지 않는다.
            if (resultCode == RESULT_OK && pendingSubmissionDraft == null) {
                Toast.makeText(this, R.string.final_booking_payment_retry, Toast.LENGTH_LONG).show();
            }
            pendingSubmissionDraft = null;
            setLoading(false);
            return;
        }
        BookingPaymentApproval paymentApproval = BookingPaymentApprovalActivity.parseResult(data);
        if (paymentApproval == null || !paymentApproval.isCompleted()) {
            pendingSubmissionDraft = null;
            setLoading(false);
            return;
        }
        confirmSubmissionOwner(pendingSubmissionDraft.withPaymentApproval(paymentApproval));
    }

    private void performRequestSubmission(BookingRequestDraft bookingRequestDraft) {
        if (submitting) return;
        if (currentUser == null) {
            showAuthState();
            return;
        }
        BookingMutationPolicy.Operation operation = editingRequest == null
                ? BookingMutationPolicy.Operation.CREATE
                : BookingMutationPolicy.Operation.UPDATE;
        if (!BookingMutationPolicy.isAllowed(currentUser.getRole(), operation)) {
            showGuardianMutationBlockedDialog();
            return;
        }
        submitting = true;
        setLoading(true);
        if (editingRequest == null) {
            BookingMutationPolicy.runIfAllowed(
                    currentUser.getRole(),
                    BookingMutationPolicy.Operation.CREATE,
                    () -> bookingRepository.createAppointmentRequest(
                            currentUser,
                            bookingRequestDraft,
                            new RepositoryCallback<AppointmentRequest>() {
                        @Override
                        public void onSuccess(AppointmentRequest result) {
                            submitting = false;
                            editingRequest = null;
                            setLoading(false);
                            restoreAfterSubmission();
                            openBookingCompletion(result, false);
                        }

                        @Override
                        public void onError(String message) {
                            submitting = false;
                            setLoading(false);
                            Toast.makeText(BookingActivity.this, message, Toast.LENGTH_LONG).show();
                        }
                            }
                    ));
            return;
        }

        BookingMutationPolicy.runIfAllowed(
                currentUser.getRole(),
                BookingMutationPolicy.Operation.UPDATE,
                () -> bookingRepository.updateAppointmentRequest(
                        currentUser,
                        editingRequest.getId(),
                        bookingRequestDraft,
                        new RepositoryCallback<AppointmentRequest>() {
                    @Override
                    public void onSuccess(AppointmentRequest result) {
                        submitting = false;
                        editingRequest = null;
                        setLoading(false);
                        restoreAfterSubmission();
                        openBookingCompletion(result, true);
                    }

                    @Override
                    public void onError(String message) {
                        submitting = false;
                        setLoading(false);
                        Toast.makeText(BookingActivity.this, message, Toast.LENGTH_LONG).show();
                    }
                        }
                ));
    }

    private void confirmSubmissionOwner(BookingRequestDraft draft) {
        User owner = currentUser;
        String editId = editingRequest == null ? null : editingRequest.getId();
        authRepository.getCurrentUser(new RepositoryCallback<User>() {
            @Override public void onSuccess(User user) {
                if (isDestroyed()) return;
                if (owner == null || !TextUtils.equals(owner.getId(), user.getId())
                        || owner.getRole() != user.getRole() || currentUser == null
                        || !TextUtils.equals(owner.getId(), currentUser.getId())
                        || !TextUtils.equals(editId, editingRequest == null ? null : editingRequest.getId())) {
                    clearAccountState();
                    setLoading(false);
                    showAuthState();
                    return;
                }
                pendingSubmissionDraft = null;
                performRequestSubmission(draft);
            }
            @Override public void onError(String message) {
                if (isDestroyed()) return;
                clearAccountState();
                setLoading(false);
                showAuthState();
            }
        });
    }

    private void startEditingRequest(AppointmentRequest request) {
        if (currentUser == null) {
            return;
        }
        if (!BookingMutationPolicy.isAllowed(
                currentUser.getRole(), BookingMutationPolicy.Operation.UPDATE)) {
            showGuardianMutationBlockedDialog();
            return;
        }
        editingRequest = request;
        formBinder.bindEditMode(currentUser, request);
        wizard.selectPatient();
        wizard.showStep(BookingStep.PARTICIPANT);
    }

    protected void openRequestDetail(AppointmentRequest request) {
        startActivity(BookingStatusActivity.createIntent(this, request.getId()));
    }

    private void exitEditMode() {
        editingRequest = null;
        if (currentUser != null) {
            formBinder.bindCreateMode(currentUser);
        }
        wizard.reset();
    }

    private void cancelRequest(AppointmentRequest request) {
        if (currentUser == null || !BookingMutationPolicy.isAllowed(
                currentUser.getRole(), BookingMutationPolicy.Operation.CANCEL)) {
            showGuardianMutationBlockedDialog();
            return;
        }
        int bodyResId = request.getStatus() == AppointmentStatus.MATCHED
                ? R.string.booking_cancel_dialog_body_matched
                : R.string.booking_cancel_dialog_body_requested;
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.booking_cancel_dialog_title)
                .setMessage(bodyResId)
                .setPositiveButton(R.string.booking_cancel_dialog_confirm, (dialogInterface, which) ->
                        performCancelRequest(request))
                .setNegativeButton(R.string.booking_cancel_dialog_keep, null)
                .show();
    }

    private void performCancelRequest(AppointmentRequest request) {
        if (currentUser == null) {
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
                        request.getId(),
                        new RepositoryCallback<AppointmentRequest>() {
                    @Override
                    public void onSuccess(AppointmentRequest result) {
                        if (editingRequest != null && TextUtils.equals(editingRequest.getId(), request.getId())) {
                            editingRequest = null;
                        }
                        setLoading(false);
                        Toast.makeText(BookingActivity.this, R.string.toast_booking_canceled, Toast.LENGTH_SHORT).show();
                        reloadDashboard();
                    }

                    @Override
                    public void onError(String message) {
                        setLoading(false);
                        Toast.makeText(BookingActivity.this, message, Toast.LENGTH_LONG).show();
                    }
                        }
                ));
    }

    private void showGuardianMutationBlockedDialog() {
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.booking_guardian_creation_blocked_title)
                .setMessage(R.string.booking_guardian_creation_blocked_body)
                .setPositiveButton(android.R.string.ok, null)
                .show();
    }

    private boolean supportsBookingRole(UserRole role) {
        return role == UserRole.PATIENT || role == UserRole.GUARDIAN;
    }

    private void setLoading(boolean loading) {
        this.loading = loading;
        progressBooking.setVisibility(loading ? View.VISIBLE : View.GONE);
        formBinder.setLoading(loading || submitting || pendingSubmissionDraft != null);
        wizard.setAvailable(contentReady, loading || submitting || pendingSubmissionDraft != null);
    }

    private void showPermissionState() {
        showBlockingState(
                StatePanelHelper.Tone.WARNING,
                getString(R.string.state_badge_permission),
                getString(R.string.state_permission_title, getString(R.string.feature_booking_title)),
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
                getString(R.string.state_load_error_title, getString(R.string.feature_booking_title)),
                body,
                getString(R.string.state_action_retry),
                view -> reloadDashboard(),
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
                bookingStatePanel,
                tone,
                badge,
                title,
                body,
                primaryText,
                primaryListener,
                secondaryText,
                secondaryListener
        );
        bookingContentContainer.setVisibility(View.GONE);
        contentReady = false;
        wizard.setAvailable(false, false);
        bottomNavigation.setVisibility(View.GONE);
    }

    private void hideBlockingState() {
        StatePanelHelper.hide(bookingStatePanel);
    }

    protected void openHospitalSelector() {
        hospitalSelectorLauncher.launch(BookingHospitalSelectorActivity.createIntent(
                this,
                formBinder.getHospitalSelection()
        ));
    }

    protected void openHealthProfile() {
        healthProfileLauncher.launch(BookingHealthProfileActivity.createIntent(
                this,
                formBinder.getHealthProfileSelection()
        ));
    }

    protected void openLocationSelector() {
        BookingHospitalSelection hospitalSelection = formBinder.getHospitalSelection();
        if (!hospitalSelection.isComplete()) {
            Toast.makeText(this, R.string.booking_location_selector_hospital_required, Toast.LENGTH_SHORT).show();
            return;
        }
        locationSelectorLauncher.launch(BookingLocationSelectorActivity.createIntent(
                this,
                hospitalSelection,
                formBinder.getMeetingLocationSelection()
        ));
    }

    private void restoreAfterSubmission() {
        if (currentUser != null) {
            formBinder.bindCreateMode(currentUser);
        }
        wizard.reset();
        reloadDashboard();
    }

    protected void openBookingCompletion(AppointmentRequest request, boolean isUpdated) {
        startActivity(BookingCompletionActivity.createIntent(this, request, isUpdated));
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

    protected AuthRepository provideAuthRepository() {
        return ServiceLocator.provideAuthRepository(this);
    }

    protected BookingRepository provideBookingRepository() {
        return ServiceLocator.provideBookingRepository(this);
    }

    private void configureSystemBars() {
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        getWindow().setStatusBarColor(android.graphics.Color.TRANSPARENT);
        getWindow().setNavigationBarColor(ContextCompat.getColor(this, R.color.final_surface));
        WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView())
                .setAppearanceLightStatusBars(true);
        WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView())
                .setAppearanceLightNavigationBars(true);
        BookingMainScreenInsets.apply(
                findViewById(R.id.bookingRoot)
        );
    }

    private void goBack() {
        if (submitting || pendingSubmissionDraft != null) return;
        if (wizard.isHistory()) {
            wizard.showHistory(false);
        } else if (wizard.step() != BookingStep.PARTICIPANT) {
            wizard.showStep(wizard.step().previous());
        } else if (wizard.hasParticipant()) {
            new MaterialAlertDialogBuilder(this).setTitle(R.string.final_booking_leave_title)
                    .setMessage(R.string.final_booking_leave_body)
                    .setPositiveButton(R.string.final_booking_leave, (dialog, which) -> finish())
                    .setNegativeButton(R.string.final_booking_stay, null).show();
        } else finish();
    }
}
