package com.example.bodeul.ui.manager;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.net.Uri;
import android.os.Bundle;
import android.text.InputFilter;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.RadioGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.lifecycle.ViewModelProvider;

import com.example.bodeul.MainActivity;
import com.example.bodeul.R;
import com.example.bodeul.data.AuthRepository;
import com.example.bodeul.data.CompanionSessionArtifactUploadPolicy;
import com.example.bodeul.data.ManagerRepository;
import com.example.bodeul.data.ServiceLocator;
import com.example.bodeul.data.realtime.CompanionRealtimeSubscriber;
import com.example.bodeul.data.realtime.SupabaseCompanionRealtimeSubscriber;
import com.example.bodeul.data.map.HospitalMapCoordinateQuery;
import com.example.bodeul.data.map.HospitalMapCoordinateResult;
import com.example.bodeul.data.map.KakaoLocalPlaceSearchClient;
import com.example.bodeul.data.map.KakaoPlaceCoordinate;
import com.example.bodeul.domain.model.CompanionSession;
import com.example.bodeul.domain.model.CompanionSessionArtifact;
import com.example.bodeul.domain.model.ManagerDashboard;
import com.example.bodeul.domain.model.MedicationComparisonDecision;
import com.example.bodeul.ui.auth.ProfileCompletionActivity;
import com.example.bodeul.ui.auth.RoleSelectionActivity;
import com.example.bodeul.ui.chat.CompanionChatActivity;
import com.example.bodeul.util.LegacyManagerLocationSharingPolicy;
import com.example.bodeul.util.StatePanelHelper;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.checkbox.MaterialCheckBox;
import com.google.android.material.textfield.TextInputEditText;

import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

import com.kakao.vectormap.KakaoMap;
import com.kakao.vectormap.KakaoMapReadyCallback;
import com.kakao.vectormap.LatLng;
import com.kakao.vectormap.MapLifeCycleCallback;
import com.kakao.vectormap.MapView;
import com.kakao.vectormap.camera.CameraUpdateFactory;
import com.kakao.vectormap.label.Label;
import com.kakao.vectormap.label.LabelOptions;
import com.kakao.vectormap.label.LabelStyle;
import com.kakao.vectormap.label.LabelManager;
import com.kakao.vectormap.label.LabelLayer;

public class ManagerGuideActivity extends AppCompatActivity {
    private static final String TAG = "ManagerGuideActivity";
    private static final int REQUEST_FINE_LOCATION = 1001;
    private static final int LOCATION_ACTION_NONE = 0;
    private static final int LOCATION_ACTION_SHARE_ONCE = 1;
    private static final int LOCATION_ACTION_START_LIVE = 2;
    private static final int LOCATION_ACTION_SHOW_CURRENT_ON_MAP = 3;

    private ManagerGuideViewModel viewModel;
    private ManagerGuideDashboardBinder managerGuideDashboardBinder;
    private ManagerGuideReceptionBinder managerGuideReceptionBinder;
    private ManagerGuidePreConsultationBinder managerGuidePreConsultationBinder;
    private ManagerGuideVitalsBinder managerGuideVitalsBinder;
    private ManagerGuidePrescriptionBinder managerGuidePrescriptionBinder;
    private ManagerGuideConsultationBinder managerGuideConsultationBinder;

    private int pendingLocationPermissionAction = LOCATION_ACTION_NONE;
    private boolean liveLocationActivationInFlight;
    private boolean activityVisible;
    private boolean pharmacySearchNavigationInProgress;
    private boolean bindingPreConsultationConfirmation;
    private boolean mutationInFlight;
    private boolean currentLocationReadInFlight;
    private boolean legacyManagerLocationEnabled;
    private boolean exitConfirmationShowing;
    private ManagerGuidePrimaryAction currentPrimaryAction = ManagerGuidePrimaryAction.NONE;
    private String currentStepCode = "";

    private View managerGuideStatePanel;
    private View managerGuideContentContainer;
    private View managerGuideBottomAction;
    private TextInputEditText inputGuideLocationSummary;
    private TextInputEditText inputGuardianUpdate;
    private TextInputEditText inputGuidePhotoNote;
    private MaterialCheckBox checkGuidePreConsultationConfirmed;
    private MaterialButton buttonAdvanceGuide;
    private TextInputEditText inputMedicationNote;
    private TextInputEditText inputPharmacySummary;
    private TextInputEditText inputReportSummary;
    private TextInputEditText inputReportTreatment;
    private TextInputEditText inputReportMedicationName;
    private TextInputEditText inputReportMedicationChangeSummary;
    private TextInputEditText inputReportMedicationScheduleNote;
    private RadioGroup groupReportMedicationComparisonDecision;
    private TextInputEditText inputReportMedicationComparisonNote;
    private TextInputEditText inputNextVisit;
    private TextView textGuideSessionArtifactTitle;
    private TextView textGuideSessionArtifactStatus;
    private MaterialButton buttonSelectGuideSessionArtifact;
    private MaterialButton buttonClearGuideSessionArtifact;
    private MaterialButton buttonSubmitReport;
    private MaterialButton buttonShowCurrentLocation;
    private ActivityResultLauncher<String[]> paymentEvidencePicker;
    private ActivityResultLauncher<String[]> prescriptionImagePicker;

    private MapView mapView;
    private KakaoMap kakaoMap;
    private Label managerMarker;
    private Label hospitalMarker;
    private Label pharmacyMarker;
    private Label trackingLabel;
    private Label currentLocationMarker;
    private ManagerDashboard currentDashboard;
    private KakaoLocalPlaceSearchClient placeSearchClient;
    private HospitalMapCoordinateResult currentCoordinateResult;
    private String currentCoordinateQueryKey = "";
    private boolean coordinateSearchInFlight;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_manager_guide);
        legacyManagerLocationEnabled = isLegacyManagerLocationEnabled();

        paymentEvidencePicker = registerForActivityResult(
                new ActivityResultContracts.OpenDocument(),
                uri -> {
                    if (uri != null) {
                        preserveReadPermission(uri);
                        viewModel.replaceSessionArtifacts(
                                CompanionSessionArtifactUploadPolicy.PAYMENT_EVIDENCE,
                                List.of(uri));
                    }
                });
        prescriptionImagePicker = registerForActivityResult(
                new ActivityResultContracts.OpenMultipleDocuments(),
                uris -> {
                    if (uris == null || uris.isEmpty()) {
                        return;
                    }
                    if (ManagerGuidePrescriptionSelectionPolicy.exceedsLimit(uris.size())) {
                        Toast.makeText(
                                this,
                                R.string.guide_artifact_prescription_limit,
                                Toast.LENGTH_SHORT).show();
                        return;
                    }
                    List<Uri> selected = new ArrayList<>(uris);
                    for (Uri uri : selected) {
                        preserveReadPermission(uri);
                    }
                    viewModel.replaceSessionArtifacts(
                            CompanionSessionArtifactUploadPolicy.PRESCRIPTION_IMAGE,
                            selected);
                });

        AuthRepository authRepository = provideAuthRepository();
        ManagerRepository managerRepository = provideManagerRepository();
        placeSearchClient = new KakaoLocalPlaceSearchClient(this);
        ManagerGuideCoordinator coordinator = new ManagerGuideCoordinator(
                this,
                new ManagerGuidePresentationFormatter(this)
        );

        ManagerGuideViewModel.Factory factory = new ManagerGuideViewModel.Factory(
                this,
                authRepository,
                managerRepository,
                coordinator,
                provideRealtimeSubscriber(),
                legacyManagerLocationEnabled
        );
        viewModel = new ViewModelProvider(this, factory).get(ManagerGuideViewModel.class);

        managerGuideStatePanel = findViewById(R.id.managerGuideStatePanel);
        managerGuideContentContainer = findViewById(R.id.managerGuideContentContainer);
        managerGuideBottomAction = findViewById(R.id.managerGuideBottomAction);
        configureBottomActionInsets();
        inputGuideLocationSummary = findViewById(R.id.inputGuideLocationSummary);
        inputGuardianUpdate = findViewById(R.id.inputGuardianUpdate);
        inputGuidePhotoNote = findViewById(R.id.inputGuidePhotoNote);
        checkGuidePreConsultationConfirmed = findViewById(
                R.id.checkGuidePreConsultationConfirmed);
        buttonAdvanceGuide = findViewById(R.id.buttonAdvanceGuide);
        inputMedicationNote = findViewById(R.id.inputMedicationNote);
        inputPharmacySummary = findViewById(R.id.inputPharmacySummary);
        inputReportSummary = findViewById(R.id.inputReportSummary);
        inputReportSummary.setFilters(new InputFilter[]{new InputFilter.LengthFilter(300)});
        inputReportTreatment = findViewById(R.id.inputReportTreatment);
        inputReportMedicationName = findViewById(R.id.inputReportMedicationName);
        inputReportMedicationChangeSummary = findViewById(R.id.inputReportMedicationChangeSummary);
        inputReportMedicationScheduleNote = findViewById(R.id.inputReportMedicationScheduleNote);
        groupReportMedicationComparisonDecision = findViewById(R.id.groupReportMedicationComparisonDecision);
        inputReportMedicationComparisonNote = findViewById(R.id.inputReportMedicationComparisonNote);
        inputNextVisit = findViewById(R.id.inputNextVisit);
        textGuideSessionArtifactTitle = findViewById(R.id.textGuideSessionArtifactTitle);
        textGuideSessionArtifactStatus = findViewById(R.id.textGuideSessionArtifactStatus);
        buttonSelectGuideSessionArtifact = findViewById(
                R.id.buttonSelectGuideSessionArtifact);
        buttonClearGuideSessionArtifact = findViewById(
                R.id.buttonClearGuideSessionArtifact);
        buttonSubmitReport = findViewById(R.id.buttonSubmitReport);
        buttonShowCurrentLocation = findViewById(R.id.buttonGuideShowCurrentLocation);

        managerGuideDashboardBinder = new ManagerGuideDashboardBinder(
                LayoutInflater.from(this),
                new ManagerGuideStageItemBinder(this),
                findViewById(R.id.textGuideMode),
                findViewById(R.id.textGuideTitle),
                findViewById(R.id.textGuideSubtitle),
                findViewById(R.id.textGuideHeroBadge),
                findViewById(R.id.textGuideHeroTitle),
                findViewById(R.id.textGuideHeroBody),
                findViewById(R.id.textGuideHeroNote),
                findViewById(R.id.viewGuideHospitalMap),
                findViewById(R.id.guideMapActionContainer),
                new ManagerGuideMapActionBinder(this::openMapFallback),
                new ManagerGuideMeetingOverviewBinder(findViewById(android.R.id.content)),
                (LinearLayout) findViewById(R.id.guideStageRailContainer),
                findViewById(R.id.textGuideFocusBadge),
                findViewById(R.id.textGuideFocusTitle),
                findViewById(R.id.textGuideFocusBody),
                findViewById(R.id.textGuideFocusPreviewLabel),
                findViewById(R.id.textGuideFocusPreviewBody),
                findViewById(R.id.viewGuideFocusPreview),
                new ManagerGuideVideoGuidanceBinder(findViewById(android.R.id.content)),
                new ManagerGuideStepSectionsBinder(this, findViewById(android.R.id.content)),
                new ManagerGuideMemoSummaryBinder(
                        LayoutInflater.from(this),
                        findViewById(android.R.id.content)),
                findViewById(R.id.textGuideLiveLocationStatus),
                findViewById(R.id.textGuideLiveLocationHistory),
                inputGuideLocationSummary,
                inputGuardianUpdate,
                inputGuidePhotoNote,
                checkGuidePreConsultationConfirmed,
                inputMedicationNote,
                inputPharmacySummary,
                findViewById(R.id.textGuidePharmacyProgressSummary),
                inputReportSummary,
                inputReportTreatment,
                inputReportMedicationName,
                inputReportMedicationChangeSummary,
                inputReportMedicationScheduleNote,
                groupReportMedicationComparisonDecision,
                findViewById(R.id.radioMedicationComparisonMatched),
                findViewById(R.id.radioMedicationComparisonChanged),
                findViewById(R.id.radioMedicationComparisonRecheck),
                inputReportMedicationComparisonNote,
                inputNextVisit,
                buttonAdvanceGuide,
                (MaterialButton) findViewById(R.id.buttonSaveLocationSummary),
                (MaterialButton) findViewById(R.id.buttonShareCurrentLocation),
                (MaterialButton) findViewById(R.id.buttonStartLiveLocationSharing),
                (MaterialButton) findViewById(R.id.buttonStopLiveLocationSharing),
                (MaterialButton) findViewById(R.id.buttonSaveGuardianUpdate),
                (MaterialButton) findViewById(R.id.buttonSaveGuidePhotoNote),
                (MaterialButton) findViewById(R.id.buttonSaveMedicationNote),
                (MaterialButton) findViewById(R.id.buttonSavePharmacySummary),
                (MaterialButton) findViewById(R.id.buttonTogglePrescriptionCollected),
                (MaterialButton) findViewById(R.id.buttonTogglePharmacyCompleted),
                (MaterialButton) findViewById(R.id.buttonToggleMedicationGuidanceCompleted),
                (MaterialButton) findViewById(R.id.buttonSubmitReport)
        );
        managerGuideReceptionBinder = new ManagerGuideReceptionBinder(findViewById(android.R.id.content));
        managerGuidePreConsultationBinder = new ManagerGuidePreConsultationBinder(
                findViewById(android.R.id.content));
        managerGuideVitalsBinder = new ManagerGuideVitalsBinder(
                findViewById(android.R.id.content), viewModel::saveVitalsDraft);
        managerGuidePrescriptionBinder = new ManagerGuidePrescriptionBinder(
                findViewById(android.R.id.content));
        managerGuideConsultationBinder = new ManagerGuideConsultationBinder(
                findViewById(android.R.id.content),
                isGuidePreviewMode(),
                savedInstanceState,
                viewModel::saveConsultationDraft);

        findViewById(R.id.buttonBackGuide).setOnClickListener(
                view -> attemptExit(this::finish));
        findViewById(R.id.buttonBackGuideReception).setOnClickListener(
                view -> attemptExit(this::finish));
        findViewById(R.id.buttonBackGuidePreConsultation).setOnClickListener(
                view -> attemptExit(this::finish));
        findViewById(R.id.buttonBackGuideVitals).setOnClickListener(
                view -> attemptExit(this::finish));
        findViewById(R.id.buttonBackGuidePrescription).setOnClickListener(
                view -> attemptExit(this::finish));
        findViewById(R.id.buttonBackGuideConsultation).setOnClickListener(
                view -> attemptExit(this::finish));
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                attemptExit(ManagerGuideActivity.this::finish);
            }
        });
        findViewById(R.id.buttonGuideReceptionShare).setOnClickListener(view -> {
            if (!"RECEPTION_QUEUE".equals(currentStepCode) || mutationInFlight) {
                return;
            }
            String message = managerGuideReceptionBinder.buildGuardianUpdate();
            if (message != null) {
                viewModel.saveGuardianUpdate(message);
            }
        });
        findViewById(R.id.navGuideHome).setOnClickListener(
                view -> attemptExit(this::openManagerHome));
        findViewById(R.id.navGuideHistory).setOnClickListener(view -> attemptExit(() ->
                startActivity(new Intent(this, ManagerHistoryActivity.class))));
        findViewById(R.id.navGuideProfile).setOnClickListener(view -> attemptExit(() ->
                startActivity(new Intent(this, ManagerProfileActivity.class))));
        findViewById(R.id.buttonGuidePreConsultationComplete).setOnClickListener(view -> {
            if ("PRE_CONSULTATION".equals(currentStepCode)
                    && !managerGuidePreConsultationBinder.isConfirmed()) {
                if (managerGuidePreConsultationBinder.canConfirm() && !mutationInFlight) {
                    viewModel.updatePreConsultationConfirmed(true);
                }
                return;
            }
            performPrimaryAction();
        });
        buttonAdvanceGuide.setOnClickListener(view -> performPrimaryAction());
        findViewById(R.id.buttonSaveLocationSummary).setOnClickListener(view -> viewModel.saveLocationSummary(valueOf(inputGuideLocationSummary)));
        findViewById(R.id.buttonSaveGuardianUpdate).setOnClickListener(view -> viewModel.saveGuardianUpdate(valueOf(inputGuardianUpdate)));
        findViewById(R.id.buttonSaveGuidePhotoNote).setOnClickListener(view -> viewModel.saveFieldPhotoNote(valueOf(inputGuidePhotoNote)));
        findViewById(R.id.buttonGuideConsultationSaveGuardian).setOnClickListener(view -> {
            if ("CONSULTATION_SUPPORT".equals(currentStepCode) && !mutationInFlight) {
                viewModel.saveConsultationGuardianUpdate(
                        managerGuideConsultationBinder.guardianUpdate());
            }
        });
        findViewById(R.id.buttonGuideConsultationSaveFieldNote).setOnClickListener(view -> {
            if ("CONSULTATION_SUPPORT".equals(currentStepCode) && !mutationInFlight) {
                viewModel.saveConsultationFieldNote(
                        managerGuideConsultationBinder.fieldNote());
            }
        });
        buttonSelectGuideSessionArtifact.setOnClickListener(view -> selectCurrentStepArtifact());
        buttonClearGuideSessionArtifact.setOnClickListener(view -> clearCurrentStepArtifact());
        findViewById(R.id.buttonGuidePrescriptionSelect).setOnClickListener(
                view -> selectCurrentStepArtifact());
        findViewById(R.id.buttonGuidePrescriptionClear).setOnClickListener(
                view -> clearCurrentStepArtifact());
        checkGuidePreConsultationConfirmed.setOnCheckedChangeListener((button, checked) -> {
            if (!bindingPreConsultationConfirmation) {
                checkGuidePreConsultationConfirmed.setEnabled(false);
                buttonAdvanceGuide.setEnabled(false);
                viewModel.updatePreConsultationConfirmed(checked);
            }
        });
        findViewById(R.id.buttonSaveMedicationNote).setOnClickListener(view -> viewModel.saveMedicationNote(valueOf(inputMedicationNote)));
        findViewById(R.id.buttonSavePharmacySummary).setOnClickListener(view -> viewModel.savePharmacySummary(valueOf(inputPharmacySummary)));
        findViewById(R.id.buttonTogglePrescriptionCollected).setOnClickListener(view -> viewModel.togglePrescriptionCollected());
        findViewById(R.id.buttonTogglePharmacyCompleted).setOnClickListener(view -> viewModel.togglePharmacyCompleted());
        findViewById(R.id.buttonToggleMedicationGuidanceCompleted).setOnClickListener(view -> viewModel.toggleMedicationGuidanceCompleted());
        buttonSubmitReport.setOnClickListener(view -> {
            if (currentPrimaryAction == ManagerGuidePrimaryAction.SUBMIT_REPORT) {
                submitCurrentReport();
            }
        });

        findViewById(R.id.buttonGuideOpenChat).setOnClickListener(view -> openCompanionChat());
        findViewById(R.id.buttonGuideMeetingOpenChat).setOnClickListener(view -> openCompanionChat());
        buttonShowCurrentLocation.setOnClickListener(view -> showCurrentLocationOnMap());
        findViewById(R.id.buttonShareCurrentLocation).setOnClickListener(view -> shareCurrentLocation());
        findViewById(R.id.buttonStartLiveLocationSharing).setOnClickListener(view -> startLiveLocationSharing());
        findViewById(R.id.buttonStopLiveLocationSharing).setOnClickListener(view -> stopLiveLocationSharing(true, true));

        mapView = findViewById(R.id.mapViewManagerGuide);
        mapView.start(new MapLifeCycleCallback() {
            @Override
            public void onMapDestroy() {
                kakaoMap = null;
                currentLocationMarker = null;
            }

            @Override
            public void onMapError(Exception e) {
                Log.w(TAG, "카카오 지도 초기화 실패: " + e.getClass().getSimpleName());
            }
        }, new KakaoMapReadyCallback() {
            @Override
            public void onMapReady(KakaoMap map) {
                kakaoMap = map;
                mapView.setVisibility(View.VISIBLE);
                updateMapMarker();
                if (legacyManagerLocationEnabled && hasLocationPermission()) {
                    startMapTracking();
                }
            }
        });

        viewModel.getUiState().observe(this, this::handleUiState);
        viewModel.getToastMessage().observe(this, message -> {
            if (message != null) {
                Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
                viewModel.toastMessageHandled();
            }
        });
        viewModel.getReportSubmittedEvent().observe(this, eventMillis -> {
            if (eventMillis == null) {
                return;
            }
            viewModel.reportSubmittedEventHandled();
            Toast.makeText(this, "동행 리포트를 제출했습니다.", Toast.LENGTH_SHORT).show();
            openManagerHome();
        });
        viewModel.getMutationInFlight().observe(this, inFlight -> {
            mutationInFlight = Boolean.TRUE.equals(inFlight);
            if (mutationInFlight) {
                disableMutationActions();
                return;
            }
            ManagerGuideViewModel.UiState state = viewModel.getUiState().getValue();
            if (state != null && state.screenModel != null) {
                handleUiState(state);
            }
        });

        if (savedInstanceState == null) {
            // Note: viewModel.reload() will be called in onStart()
        }
    }

    /**
     * 운영 화면은 기존 저장소를 쓰고, debug 미리보기는 하위 Activity에서만 로컬 저장소를 주입한다.
     */
    protected AuthRepository provideAuthRepository() {
        return ServiceLocator.provideAuthRepository(this);
    }

    protected ManagerRepository provideManagerRepository() {
        return ServiceLocator.provideManagerRepository(this);
    }

    protected CompanionRealtimeSubscriber provideRealtimeSubscriber() {
        return new SupabaseCompanionRealtimeSubscriber(this);
    }

    protected boolean isLegacyManagerLocationEnabled() {
        return LegacyManagerLocationSharingPolicy.isEnabled(this);
    }

    protected boolean isPlaceSearchEnabled() {
        return true;
    }

    /** debug 미리보기에서만 실제 녹음과 분리된 로컬 상태 시뮬레이션을 노출한다. */
    protected boolean isGuidePreviewMode() {
        return false;
    }

    private void handleUiState(ManagerGuideViewModel.UiState state) {
        if (state == null) return;

        if (state.requireProfileCompletion) {
            openProfileCompletion();
            return;
        }

        if (state.statePanelType != ManagerGuideViewModel.StatePanelType.NONE) {
            managerGuideReceptionBinder.hideForState();
            managerGuidePreConsultationBinder.hideForState();
            managerGuideVitalsBinder.hideForState();
            managerGuidePrescriptionBinder.hideForState();
            managerGuideConsultationBinder.hideForState();
            currentPrimaryAction = ManagerGuidePrimaryAction.NONE;
            currentStepCode = "";
            clearCurrentLocationMarkerOutsideMeetingStep();
            managerGuideContentContainer.setVisibility(View.GONE);
            managerGuideBottomAction.setVisibility(View.GONE);
            switch (state.statePanelType) {
                case PERMISSION:
                    showPermissionState();
                    break;
                case AUTH:
                    showAuthState();
                    break;
                case EMPTY:
                    showEmptyState();
                    break;
                case LOAD_ERROR:
                    showLoadErrorState(state.errorMessage);
                    break;
                default:
                    StatePanelHelper.hide(managerGuideStatePanel);
                    break;
            }
        } else {
            StatePanelHelper.hide(managerGuideStatePanel);
            if (state.screenModel != null) {
                managerGuideContentContainer.setVisibility(View.VISIBLE);
                managerGuideBottomAction.setVisibility(View.VISIBLE);
                currentDashboard = state.dashboard;
                bindingPreConsultationConfirmation = true;
                try {
                    managerGuideDashboardBinder.bindScreen(state.screenModel);
                    managerGuideReceptionBinder.bind(
                            state.screenModel, state.dashboard, mutationInFlight);
                    managerGuidePreConsultationBinder.bind(
                            state.screenModel, state.dashboard, mutationInFlight);
                    managerGuideVitalsBinder.bind(
                            state.screenModel, state.dashboard, mutationInFlight,
                            viewModel.getVitalsDraft(state.dashboard.getSession().getId()));
                    managerGuidePrescriptionBinder.bind(
                            state.screenModel, state.dashboard, mutationInFlight);
                    String sessionId = state.dashboard == null
                            || state.dashboard.getSession() == null
                            ? ""
                            : state.dashboard.getSession().getId();
                    managerGuideConsultationBinder.bind(
                            state.screenModel,
                            state.dashboard,
                            mutationInFlight,
                            viewModel.getConsultationDraft(sessionId));
                    applyReportDraft();
                } finally {
                    bindingPreConsultationConfirmation = false;
                }
                currentPrimaryAction = state.screenModel.getPrimaryAction();
                currentStepCode = state.screenModel.getCurrentStepCode();
                clearCurrentLocationMarkerOutsideMeetingStep();
                bindSessionArtifactSection(state.screenModel.isInputsEnabled());
                if (mutationInFlight) {
                    disableMutationActions();
                }
                updateMapMarker();
            } else {
                managerGuideReceptionBinder.hideForState();
                managerGuidePreConsultationBinder.hideForState();
                managerGuideVitalsBinder.hideForState();
                managerGuidePrescriptionBinder.hideForState();
                managerGuideConsultationBinder.hideForState();
                currentPrimaryAction = ManagerGuidePrimaryAction.NONE;
                currentStepCode = "";
                clearCurrentLocationMarkerOutsideMeetingStep();
                managerGuideContentContainer.setVisibility(View.GONE);
                managerGuideBottomAction.setVisibility(View.GONE);
            }
        }

        syncLiveLocationTrackingWithDashboard(state.dashboard);
    }

    private String valueOf(TextInputEditText input) {
        return input.getText() == null ? "" : input.getText().toString().trim();
    }

    private void submitCurrentReport() {
        viewModel.submitReport(
                valueOf(inputReportSummary),
                valueOf(inputReportTreatment),
                valueOf(inputMedicationNote),
                valueOf(inputReportMedicationName),
                valueOf(inputReportMedicationChangeSummary),
                valueOf(inputReportMedicationScheduleNote),
                resolveMedicationComparisonDecision(),
                valueOf(inputReportMedicationComparisonNote),
                valueOf(inputNextVisit)
        );
    }

    private void applyReportDraft() {
        String sessionId = currentDashboard == null || currentDashboard.getSession() == null
                ? ""
                : currentDashboard.getSession().getId();
        ManagerGuideViewModel.ReportDraft draft = viewModel.getReportDraft(sessionId);
        if (draft == null) {
            return;
        }
        inputReportSummary.setText(draft.summary);
        inputReportTreatment.setText(draft.treatment);
        inputMedicationNote.setText(draft.medication);
        inputReportMedicationName.setText(draft.medicationName);
        inputReportMedicationChangeSummary.setText(draft.medicationChangeSummary);
        inputReportMedicationScheduleNote.setText(draft.medicationScheduleNote);
        inputReportMedicationComparisonNote.setText(draft.medicationComparisonNote);
        inputNextVisit.setText(draft.nextVisit);
        if (draft.medicationComparisonDecision == null) {
            groupReportMedicationComparisonDecision.clearCheck();
        } else {
            int radioId = switch (draft.medicationComparisonDecision) {
                case MATCHED -> R.id.radioMedicationComparisonMatched;
                case CHANGED -> R.id.radioMedicationComparisonChanged;
                case RECHECK_REQUIRED -> R.id.radioMedicationComparisonRecheck;
            };
            groupReportMedicationComparisonDecision.check(radioId);
        }
    }

    private void disableMutationActions() {
        managerGuideReceptionBinder.setShareEnabled(false);
        managerGuideVitalsBinder.setInputsEnabled(false);
        managerGuidePrescriptionBinder.setActionsEnabled(false);
        managerGuideConsultationBinder.setInputsEnabled(false);
        findViewById(R.id.buttonGuidePreConsultationComplete).setEnabled(false);
        buttonAdvanceGuide.setEnabled(false);
        buttonSubmitReport.setEnabled(false);
        buttonSelectGuideSessionArtifact.setEnabled(false);
        buttonClearGuideSessionArtifact.setEnabled(false);
    }

    private void performPrimaryAction() {
        if (currentPrimaryAction == ManagerGuidePrimaryAction.SUBMIT_REPORT) {
            submitCurrentReport();
            return;
        }
        if (currentPrimaryAction == ManagerGuidePrimaryAction.ADVANCE) {
            if ("CONSULTATION_SUPPORT".equals(currentStepCode)
                    && managerGuideConsultationBinder.showUnsavedInputError()) {
                return;
            }
            if ("VITALS_CHECK".equals(currentStepCode)) {
                String note = managerGuideVitalsBinder.buildNote();
                if (note != null) {
                    viewModel.saveVitalsAndAdvance(note);
                }
                return;
            }
            if (ManagerGuideAdvanceConfirmationPolicy.requiresConfirmation(
                    currentPrimaryAction,
                    currentStepCode)) {
                showRouteCompletionConfirmation();
                return;
            }
            viewModel.advanceStep();
            return;
        }
        if (currentPrimaryAction == ManagerGuidePrimaryAction.END_CARE) {
            viewModel.advanceStep();
        }
    }

    private void attemptExit(Runnable exitAction) {
        boolean consultationMayStillBeActive = "CONSULTATION_SUPPORT".equals(currentStepCode)
                || TextUtils.isEmpty(currentStepCode);
        boolean hasUnsavedConsultation = consultationMayStillBeActive
                && managerGuideConsultationBinder != null
                && managerGuideConsultationBinder.hasUnsavedInput();
        if (!hasUnsavedConsultation) {
            exitAction.run();
            return;
        }
        if (mutationInFlight) {
            Toast.makeText(
                    this,
                    R.string.guide_consultation_save_in_progress,
                    Toast.LENGTH_SHORT).show();
            return;
        }
        if (exitConfirmationShowing) {
            return;
        }
        exitConfirmationShowing = true;
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(R.string.guide_consultation_exit_title)
                .setMessage(R.string.guide_consultation_exit_body)
                .setNegativeButton(R.string.guide_consultation_exit_stay, null)
                .setPositiveButton(
                        R.string.guide_consultation_exit_discard,
                        (ignored, which) -> {
                            discardConsultationDraft();
                            exitAction.run();
                        })
                .create();
        dialog.setOnDismissListener(ignored -> exitConfirmationShowing = false);
        dialog.show();
    }

    private void discardConsultationDraft() {
        managerGuideConsultationBinder.discardUnsavedInput();
        String sessionId = currentDashboard == null
                || currentDashboard.getSession() == null
                ? ""
                : currentDashboard.getSession().getId();
        viewModel.clearConsultationDraft(sessionId);
    }

    private void showRouteCompletionConfirmation() {
        String expectedSessionId = currentDashboard == null
                || currentDashboard.getSession() == null
                ? ""
                : currentDashboard.getSession().getId();
        String expectedStepCode = currentStepCode == null ? "" : currentStepCode.trim();
        new AlertDialog.Builder(this)
                .setTitle(R.string.guide_route_confirmation_title)
                .setMessage(R.string.guide_route_confirmation_body)
                .setNegativeButton(R.string.guide_route_confirmation_cancel, null)
                .setPositiveButton(
                        R.string.guide_action_route_confirmed,
                        (dialog, which) -> advanceRouteIfStillCurrent(
                                expectedSessionId,
                                expectedStepCode))
                .show();
    }

    private void advanceRouteIfStillCurrent(String expectedSessionId, String expectedStepCode) {
        String activeSessionId = currentDashboard == null
                || currentDashboard.getSession() == null
                ? ""
                : currentDashboard.getSession().getId();
        if (!ManagerGuideAdvanceConfirmationPolicy.canApplyConfirmation(
                currentPrimaryAction,
                expectedSessionId,
                expectedStepCode,
                activeSessionId,
                currentStepCode)) {
            Toast.makeText(
                    this,
                    R.string.guide_route_confirmation_stale,
                    Toast.LENGTH_SHORT).show();
            return;
        }
        viewModel.advanceStep(expectedSessionId, expectedStepCode);
    }

    private void selectCurrentStepArtifact() {
        String purpose = currentArtifactPurpose();
        if (CompanionSessionArtifactUploadPolicy.PAYMENT_EVIDENCE.equals(purpose)) {
            paymentEvidencePicker.launch(new String[]{"image/jpeg", "image/png", "application/pdf"});
        } else if (CompanionSessionArtifactUploadPolicy.PRESCRIPTION_IMAGE.equals(purpose)) {
            prescriptionImagePicker.launch(new String[]{"image/jpeg", "image/png"});
        }
    }

    private void clearCurrentStepArtifact() {
        String purpose = currentArtifactPurpose();
        if (!purpose.isEmpty()) {
            viewModel.clearSessionArtifacts(purpose);
        }
    }

    private void bindSessionArtifactSection(boolean inputsEnabled) {
        if (currentDashboard == null || currentDashboard.getSession() == null) {
            return;
        }
        String purpose = currentArtifactPurpose();
        if (purpose.isEmpty()) {
            return;
        }
        boolean payment = CompanionSessionArtifactUploadPolicy.PAYMENT_EVIDENCE.equals(purpose);
        textGuideSessionArtifactTitle.setText(payment
                ? R.string.guide_artifact_payment_title
                : R.string.guide_artifact_prescription_title);
        List<CompanionSessionArtifact> artifacts = currentDashboard.getSession().getArtifacts(purpose);
        textGuideSessionArtifactStatus.setText(artifacts.isEmpty()
                ? getString(R.string.guide_artifact_empty)
                : getString(R.string.guide_artifact_count, artifacts.size()));
        buttonSelectGuideSessionArtifact.setText(artifacts.isEmpty()
                ? R.string.guide_artifact_select
                : R.string.guide_artifact_replace);
        buttonSelectGuideSessionArtifact.setEnabled(inputsEnabled && !mutationInFlight);
        buttonClearGuideSessionArtifact.setEnabled(
                inputsEnabled && !mutationInFlight && !artifacts.isEmpty());
    }

    private String currentArtifactPurpose() {
        if (currentDashboard == null || currentDashboard.getSession() == null) {
            return "";
        }
        String stepCode = currentDashboard.getSession().getCurrentStepCode();
        if ("PAYMENT_EVIDENCE".equals(stepCode)) {
            return CompanionSessionArtifactUploadPolicy.PAYMENT_EVIDENCE;
        }
        if ("PRESCRIPTION_DOCUMENTS".equals(stepCode)) {
            return CompanionSessionArtifactUploadPolicy.PRESCRIPTION_IMAGE;
        }
        return "";
    }

    private void preserveReadPermission(Uri uri) {
        try {
            getContentResolver().takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION);
        } catch (SecurityException ignored) {
            // 공급자가 영구 권한을 제공하지 않아도 현재 업로드는 계속 진행한다.
        }
    }

    @Nullable
    private MedicationComparisonDecision resolveMedicationComparisonDecision() {
        int checkedId = groupReportMedicationComparisonDecision.getCheckedRadioButtonId();
        if (checkedId == R.id.radioMedicationComparisonMatched) {
            return MedicationComparisonDecision.MATCHED;
        }
        if (checkedId == R.id.radioMedicationComparisonChanged) {
            return MedicationComparisonDecision.CHANGED;
        }
        if (checkedId == R.id.radioMedicationComparisonRecheck) {
            return MedicationComparisonDecision.RECHECK_REQUIRED;
        }
        return null;
    }

    private void updateMapMarker() {
        if (currentDashboard == null) return;

        // 장소 검색은 지도 SDK 인증 상태와 독립적으로 Core API까지 검증한다.
        updateHospitalAndPharmacyMarkers();
        if (legacyManagerLocationEnabled && kakaoMap != null) {
            updateSharedLocationMarker();
        }
    }

    private void updateSharedLocationMarker() {
        if (kakaoMap == null || currentDashboard == null) {
            return;
        }

        CompanionSession session = currentDashboard.getSession();
        if (session == null) return;

        Double lat = session.getSharedLatitude();
        Double lng = session.getSharedLongitude();

        if (lat != null && lng != null && lat != 0.0 && lng != 0.0) {
            LatLng position = LatLng.from(lat, lng);
            LabelManager labelManager = kakaoMap.getLabelManager();
            LabelLayer layer = labelManager.getLayer();

            if (managerMarker == null) {
                android.graphics.Bitmap markerBitmap = getBitmapFromVectorDrawable(this, R.drawable.ic_map_marker);
                LabelOptions options = LabelOptions.from(position);
                if (markerBitmap != null) {
                    options.setStyles(LabelStyle.from(markerBitmap));
                } else {
                    options.setStyles(LabelStyle.from(R.drawable.ic_map_marker));
                }
                managerMarker = layer.addLabel(options);
            } else {
                managerMarker.moveTo(position);
            }
            kakaoMap.moveCamera(CameraUpdateFactory.newCenterPosition(position));
        }
    }

    private void updateHospitalAndPharmacyMarkers() {
        if (!isPlaceSearchEnabled() || currentDashboard == null) {
            return;
        }
        HospitalMapCoordinateQuery query = new HospitalMapCoordinateQuery(
                currentDashboard.getAppointmentRequest().getHospitalName(),
                currentDashboard.getAppointmentRequest().getDepartmentName()
        );
        if (query.isEmpty() || !placeSearchClient.isConfigured()) {
            return;
        }

        String queryKey = query.buildPrimaryHospitalQuery();
        if (TextUtils.equals(currentCoordinateQueryKey, queryKey) && currentCoordinateResult != null) {
            renderHospitalAndPharmacyMarkers(currentCoordinateResult);
            return;
        }
        if (TextUtils.equals(currentCoordinateQueryKey, queryKey) && coordinateSearchInFlight) {
            return;
        }

        currentCoordinateQueryKey = queryKey;
        coordinateSearchInFlight = true;
        placeSearchClient.searchHospitalAndPharmacy(query, new KakaoLocalPlaceSearchClient.Callback() {
            @Override
            public void onSuccess(HospitalMapCoordinateResult result) {
                if (!TextUtils.equals(currentCoordinateQueryKey, queryKey)) {
                    return;
                }
                coordinateSearchInFlight = false;
                currentCoordinateResult = result;
                renderHospitalAndPharmacyMarkers(result);
            }

            @Override
            public void onError(String message) {
                if (!TextUtils.equals(currentCoordinateQueryKey, queryKey)) {
                    return;
                }
                coordinateSearchInFlight = false;
                currentCoordinateResult = new HospitalMapCoordinateResult(null, null);
            }
        });
    }

    private void configureBottomActionInsets() {
        int initialLeftPadding = managerGuideBottomAction.getPaddingLeft();
        int initialTopPadding = managerGuideBottomAction.getPaddingTop();
        int initialRightPadding = managerGuideBottomAction.getPaddingRight();
        int initialBottomPadding = managerGuideBottomAction.getPaddingBottom();

        ViewCompat.setOnApplyWindowInsetsListener(managerGuideBottomAction, (view, windowInsets) -> {
            Insets navigationInsets = windowInsets.getInsets(
                    WindowInsetsCompat.Type.navigationBars()
            );
            view.setPadding(
                    initialLeftPadding + navigationInsets.left,
                    initialTopPadding,
                    initialRightPadding + navigationInsets.right,
                    initialBottomPadding + navigationInsets.bottom
            );
            return windowInsets;
        });
        ViewCompat.requestApplyInsets(managerGuideBottomAction);
    }

    private void renderHospitalAndPharmacyMarkers(HospitalMapCoordinateResult result) {
        if (kakaoMap == null || result == null) {
            return;
        }
        if (result.getHospitalCoordinate() != null) {
            hospitalMarker = upsertPlaceMarker(
                    hospitalMarker,
                    "guide-hospital",
                    result.getHospitalCoordinate(),
                    R.drawable.ic_map_marker_hospital
            );
        }
        if (result.getPharmacyCoordinate() != null) {
            pharmacyMarker = upsertPlaceMarker(
                    pharmacyMarker,
                    "guide-pharmacy",
                    result.getPharmacyCoordinate(),
                    R.drawable.ic_map_marker_pharmacy
            );
        }
        if ((currentDashboard.getSession() == null || !currentDashboard.getSession().hasSharedLocationCoordinates())
                && result.getHospitalCoordinate() != null) {
            kakaoMap.moveCamera(CameraUpdateFactory.newCenterPosition(LatLng.from(
                    result.getHospitalCoordinate().getLatitude(),
                    result.getHospitalCoordinate().getLongitude()
            )));
        }
    }

    private Label upsertPlaceMarker(
            Label marker,
            String markerId,
            KakaoPlaceCoordinate coordinate,
            int drawableResId
    ) {
        LatLng position = LatLng.from(coordinate.getLatitude(), coordinate.getLongitude());
        if (marker == null) {
            android.graphics.Bitmap markerBitmap = getBitmapFromVectorDrawable(this, drawableResId);
            LabelOptions options = LabelOptions.from(markerId, position);
            if (markerBitmap != null) {
                options.setStyles(LabelStyle.from(markerBitmap));
            } else {
                options.setStyles(LabelStyle.from(drawableResId));
            }
            return kakaoMap.getLabelManager().getLayer().addLabel(options);
        }
        marker.moveTo(position);
        return marker;
    }

    private void startMapTracking() {
        if (!legacyManagerLocationEnabled || kakaoMap == null) return;
        if (trackingLabel == null) {
            android.graphics.Bitmap trackingBitmap = getBitmapFromVectorDrawable(this, R.drawable.ic_tracking_dot);
            LabelOptions options = LabelOptions.from("tracking", LatLng.from(0, 0));
            if (trackingBitmap != null) {
                options.setStyles(LabelStyle.from(trackingBitmap).setAnchorPoint(0.5f, 0.5f));
            } else {
                options.setStyles(LabelStyle.from(R.drawable.ic_tracking_dot).setAnchorPoint(0.5f, 0.5f));
            }
            trackingLabel = kakaoMap.getLabelManager().getLayer().addLabel(options);
        }
        kakaoMap.getTrackingManager().startTracking(trackingLabel);
    }

    private void showCurrentLocationOnMap() {
        if (!ManagerGuideCurrentLocationPolicy.isAvailableFor(currentStepCode)
                || currentLocationReadInFlight) {
            return;
        }
        if (kakaoMap == null) {
            Toast.makeText(this, R.string.guide_current_location_map_preparing, Toast.LENGTH_SHORT).show();
            return;
        }
        if (!hasLocationPermission()) {
            requestLocationPermission(LOCATION_ACTION_SHOW_CURRENT_ON_MAP);
            return;
        }

        currentLocationReadInFlight = true;
        buttonShowCurrentLocation.setEnabled(false);
        ManagerCurrentLocationReader.read(this, new ManagerCurrentLocationReader.Callback() {
            @Override
            public void onSuccess(@androidx.annotation.NonNull Location location) {
                finishCurrentLocationRead();
                if (isFinishing() || isDestroyed()
                        || !ManagerGuideCurrentLocationPolicy.isAvailableFor(currentStepCode)
                        || kakaoMap == null) {
                    return;
                }
                upsertCurrentLocationMarker(location);
                Toast.makeText(
                        ManagerGuideActivity.this,
                        R.string.guide_current_location_displayed,
                        Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onFailure(@androidx.annotation.NonNull ManagerCurrentLocationReader.Failure failure) {
                finishCurrentLocationRead();
                if (isFinishing() || isDestroyed()) {
                    return;
                }
                Toast.makeText(
                        ManagerGuideActivity.this,
                        currentLocationFailureMessage(failure),
                        Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void finishCurrentLocationRead() {
        currentLocationReadInFlight = false;
        if (!isFinishing() && !isDestroyed()) {
            buttonShowCurrentLocation.setEnabled(true);
        }
    }

    private int currentLocationFailureMessage(ManagerCurrentLocationReader.Failure failure) {
        switch (failure) {
            case PERMISSION_REQUIRED:
                return R.string.guide_current_location_permission_denied;
            case LOCATION_SERVICE_DISABLED:
                return R.string.guide_current_location_service_disabled;
            case TIMED_OUT:
                return R.string.guide_current_location_timeout;
            case UNAVAILABLE:
            default:
                return R.string.guide_current_location_unavailable;
        }
    }

    private void upsertCurrentLocationMarker(Location location) {
        LatLng position = LatLng.from(location.getLatitude(), location.getLongitude());
        if (currentLocationMarker == null) {
            android.graphics.Bitmap markerBitmap = getBitmapFromVectorDrawable(
                    this,
                    R.drawable.ic_tracking_dot);
            LabelOptions options = LabelOptions.from("guide-current-location", position);
            if (markerBitmap != null) {
                options.setStyles(LabelStyle.from(markerBitmap).setAnchorPoint(0.5f, 0.5f));
            } else {
                options.setStyles(LabelStyle.from(R.drawable.ic_tracking_dot).setAnchorPoint(0.5f, 0.5f));
            }
            currentLocationMarker = kakaoMap.getLabelManager().getLayer().addLabel(options);
        } else {
            currentLocationMarker.moveTo(position);
        }
        kakaoMap.moveCamera(CameraUpdateFactory.newCenterPosition(position));
    }

    private void clearCurrentLocationMarkerOutsideMeetingStep() {
        if (ManagerGuideCurrentLocationPolicy.isAvailableFor(currentStepCode)
                || currentLocationMarker == null) {
            return;
        }
        currentLocationMarker.remove();
        currentLocationMarker = null;
    }

    private android.graphics.Bitmap getBitmapFromVectorDrawable(android.content.Context context, int drawableId) {
        android.graphics.drawable.Drawable drawable = androidx.core.content.ContextCompat.getDrawable(context, drawableId);
        if (drawable == null) return null;

        android.graphics.Bitmap bitmap = android.graphics.Bitmap.createBitmap(
                Math.max(1, drawable.getIntrinsicWidth()),
                Math.max(1, drawable.getIntrinsicHeight()),
                android.graphics.Bitmap.Config.ARGB_8888);
        android.graphics.Canvas canvas = new android.graphics.Canvas(bitmap);
        drawable.setBounds(0, 0, canvas.getWidth(), canvas.getHeight());
        drawable.draw(canvas);

        return bitmap;
    }

    private void openMapFallback(ManagerGuideMapActionModel model) {
        ManagerGuideMapFallbackLauncher.OpenResult result =
                ManagerGuideMapFallbackLauncher.open(this, model);
        if (model.isKakaoPlaceSearch()
                && result != ManagerGuideMapFallbackLauncher.OpenResult.FAILED) {
            pharmacySearchNavigationInProgress = true;
        }
        switch (result) {
            case OPENED_WEB_FALLBACK:
                Toast.makeText(this, R.string.guide_map_web_fallback_notice, Toast.LENGTH_SHORT).show();
                break;
            case OPENED_APP_STORE:
                Toast.makeText(this, R.string.guide_map_install_fallback_notice, Toast.LENGTH_SHORT).show();
                break;
            case FAILED:
                Toast.makeText(this, R.string.guide_map_open_error, Toast.LENGTH_SHORT).show();
                break;
            case OPENED:
            default:
                break;
        }
    }

    protected void openCompanionChat() {
        startActivity(CompanionChatActivity.createIntent(this));
    }

    private void shareCurrentLocation() {
        if (!legacyManagerLocationEnabled) {
            showLegacyManagerLocationDisabledNotice();
            return;
        }
        if (!hasLocationPermission()) {
            requestLocationPermission(LOCATION_ACTION_SHARE_ONCE);
            return;
        }
        performSingleLocationShare();
    }

    private void startLiveLocationSharing() {
        if (!legacyManagerLocationEnabled) {
            showLegacyManagerLocationDisabledNotice();
            return;
        }
        if (!hasLocationPermission()) {
            requestLocationPermission(LOCATION_ACTION_START_LIVE);
            return;
        }
        performStartLiveLocationSharing();
    }

    private void performSingleLocationShare() {
        if (!legacyManagerLocationEnabled) {
            return;
        }
        ManagerCurrentLocationSharer.share(this, new ManagerCurrentLocationSharer.Callback() {
            @Override
            public void onSuccess(double latitude, double longitude, String summary) {
                viewModel.shareCurrentLocation(latitude, longitude, summary);
            }

            @Override
            public void onError(String message) {
                Toast.makeText(ManagerGuideActivity.this, message, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void performStartLiveLocationSharing() {
        if (!legacyManagerLocationEnabled) {
            return;
        }
        ManagerLocationService.start(this);
        liveLocationActivationInFlight = true;
        viewModel.updateLiveLocationSharingState(true, () -> {
            liveLocationActivationInFlight = false;
        }, () -> {
            liveLocationActivationInFlight = false;
            stopTrackerOnly();
        });
    }

    private void stopLiveLocationSharing(boolean persistRemoteState, boolean showToast) {
        stopTrackerOnly();
        if (!persistRemoteState) {
            return;
        }
        viewModel.updateLiveLocationSharingState(false, () -> {
            if (showToast) {
                Toast.makeText(ManagerGuideActivity.this, R.string.guide_live_location_stopped, Toast.LENGTH_SHORT).show();
            }
        }, null);
    }

    private void stopTrackerOnly() {
        ManagerLocationService.stop(this);
        liveLocationActivationInFlight = false;
        viewModel.resetLiveLocationInFlight();
    }

    private boolean hasLocationPermission() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED;
    }

    private void requestLocationPermission(int action) {
        if (!legacyManagerLocationEnabled && action != LOCATION_ACTION_SHOW_CURRENT_ON_MAP) {
            pendingLocationPermissionAction = LOCATION_ACTION_NONE;
            showLegacyManagerLocationDisabledNotice();
            return;
        }
        pendingLocationPermissionAction = action;
        requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, REQUEST_FINE_LOCATION);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode != REQUEST_FINE_LOCATION) {
            return;
        }
        int action = pendingLocationPermissionAction;
        pendingLocationPermissionAction = LOCATION_ACTION_NONE;
        if (!legacyManagerLocationEnabled && action != LOCATION_ACTION_SHOW_CURRENT_ON_MAP) {
            stopTrackerOnly();
            return;
        }
        if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            if (action == LOCATION_ACTION_SHOW_CURRENT_ON_MAP) {
                showCurrentLocationOnMap();
                return;
            }
            if (kakaoMap != null) {
                startMapTracking();
            }
            if (action == LOCATION_ACTION_START_LIVE) {
                startLiveLocationSharing();
                return;
            }
            if (action == LOCATION_ACTION_SHARE_ONCE) {
                shareCurrentLocation();
                return;
            }
        }
        if (action != LOCATION_ACTION_NONE) {
            int messageResId = action == LOCATION_ACTION_SHOW_CURRENT_ON_MAP
                    ? R.string.guide_current_location_permission_denied
                    : R.string.guide_share_location_permission_denied;
            Toast.makeText(this, messageResId, Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        activityVisible = true;
        StatePanelHelper.hide(managerGuideStatePanel);
        if (pharmacySearchNavigationInProgress) {
            pharmacySearchNavigationInProgress = false;
            return;
        }
        viewModel.reload();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (activityVisible && pharmacySearchNavigationInProgress) {
            pharmacySearchNavigationInProgress = false;
        }
        if (mapView != null) {
            mapView.resume();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (mapView != null) {
            mapView.pause();
        }
    }

    @Override
    protected void onStop() {
        activityVisible = false;
        super.onStop();
        if (isChangingConfigurations()) {
            stopTrackerOnly();
            return;
        }
        if (pharmacySearchNavigationInProgress) {
            return;
        }
        ManagerDashboard dashboard = viewModel.getUiState().getValue() != null ? viewModel.getUiState().getValue().dashboard : null;
        if (dashboard != null && dashboard.getSession().isLiveLocationSharingActive()) {
            stopLiveLocationSharing(true, false);
            return;
        }
        stopTrackerOnly();
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        if (managerGuideConsultationBinder != null) {
            managerGuideConsultationBinder.saveInstanceState(outState);
        }
        super.onSaveInstanceState(outState);
    }

    private void syncLiveLocationTrackingWithDashboard(@Nullable ManagerDashboard dashboard) {
        if (!legacyManagerLocationEnabled) {
            stopTrackerOnly();
            return;
        }
        CompanionSession session = dashboard == null ? null : dashboard.getSession();
        if (!activityVisible) {
            if (!pharmacySearchNavigationInProgress) {
                stopTrackerOnly();
            }
            return;
        }
        if (session == null || !session.isLiveLocationSharingActive()) {
            if (liveLocationActivationInFlight) {
                return;
            }
            stopTrackerOnly();
            return;
        }
        if (!liveLocationActivationInFlight) {
            ManagerLocationService.start(this);
        }
    }

    private void showLegacyManagerLocationDisabledNotice() {
        Toast.makeText(
                this,
                R.string.legacy_manager_location_disabled_body,
                Toast.LENGTH_SHORT
        ).show();
    }

    private void showPermissionState() {
        showBlockingState(
                StatePanelHelper.Tone.WARNING,
                getString(R.string.state_badge_permission),
                getString(R.string.state_permission_title, getString(R.string.guide_title)),
                getString(R.string.state_permission_body),
                getString(R.string.state_action_open_home),
                view -> attemptExit(this::openGeneralHome),
                getString(R.string.state_action_open_login),
                view -> attemptExit(this::openRoleSelection)
        );
    }

    private void showAuthState() {
        showBlockingState(
                StatePanelHelper.Tone.WARNING,
                getString(R.string.state_badge_auth),
                getString(R.string.state_auth_title),
                getString(R.string.state_auth_body),
                getString(R.string.state_action_open_login),
                view -> attemptExit(this::openRoleSelection),
                null,
                null
        );
    }

    private void showEmptyState() {
        showBlockingState(
                StatePanelHelper.Tone.INFO,
                getString(R.string.state_badge_notice),
                getString(R.string.companion_chat_empty_title),
                getString(R.string.companion_chat_empty_session_body),
                getString(R.string.state_action_open_home),
                view -> attemptExit(this::openManagerHome),
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
                getString(R.string.state_load_error_title, getString(R.string.guide_title)),
                body,
                getString(R.string.state_action_retry),
                view -> viewModel.loadDashboard(),
                getString(R.string.state_action_open_home),
                view -> attemptExit(this::openManagerHome)
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
                managerGuideStatePanel,
                tone,
                badge,
                title,
                body,
                primaryText,
                primaryListener,
                secondaryText,
                secondaryListener
        );
        managerGuideContentContainer.setVisibility(View.GONE);
        managerGuideBottomAction.setVisibility(View.GONE);
    }

    private void openGeneralHome() {
        startActivity(new Intent(this, MainActivity.class));
        finish();
    }

    protected void openManagerHome() {
        Intent intent = new Intent(this, ManagerActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(intent);
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
