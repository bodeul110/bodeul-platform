package com.example.bodeul.ui.manager;

import android.content.Context;
import android.text.TextUtils;

import androidx.annotation.Nullable;

import com.example.bodeul.R;
import com.example.bodeul.data.ManagerDocumentUploadPolicy;
import com.example.bodeul.domain.model.ManagerDocumentFileMetadata;
import com.example.bodeul.domain.model.ManagerDocumentFileType;
import com.example.bodeul.domain.model.ManagerDocumentOverview;
import com.example.bodeul.domain.model.ManagerDocumentStatus;
import com.example.bodeul.domain.model.ManagerHomeProfile;
import com.example.bodeul.util.EnvironmentModeBadgeHelper;

import java.util.ArrayList;
import java.util.List;

/**
 * 서류 등록 화면에서 사용할 상태 문구와 카드 모델을 조합한다.
 */
public final class ManagerDocumentRegistrationCoordinator {
    private final Context context;
    private final ManagerHomePresentationFormatter formatter;

    public ManagerDocumentRegistrationCoordinator(
            Context context,
            ManagerHomePresentationFormatter formatter
    ) {
        this.context = context.getApplicationContext();
        this.formatter = formatter;
    }

    public ManagerDocumentRegistrationScreenModel createScreenModel(
            ManagerDocumentOverview overview,
            boolean firebaseBacked
    ) {
        ManagerHomeProfile profile = overview.getProfile();
        boolean allRequiredUploaded = hasRequiredFiles(profile);
        ManagerDocumentStatus status = profile.getDocumentStatus();
        ManagerDocumentDisplayState displayState = ManagerDocumentDisplayState.from(profile);

        return new ManagerDocumentRegistrationScreenModel(
                EnvironmentModeBadgeHelper.resolveUserFacingLabel(context, firebaseBacked),
                context.getString(R.string.manager_document_registration_status_badge),
                formatter.toDocumentStatusLabel(status),
                buildStatusBody(displayState),
                createDocumentItems(profile),
                ManagerHomePresentationFormatter.shouldShowReviewDecision(status)
                        && !TextUtils.isEmpty(profile.getDocumentReviewNote()),
                status == ManagerDocumentStatus.REJECTED
                        ? context.getString(R.string.manager_document_registration_review_rejected_title)
                        : context.getString(R.string.manager_document_registration_review_title),
                formatter.buildDocumentReviewNote(profile),
                buildRequestButtonText(status, allRequiredUploaded),
                canRequestReview(profile),
                displayState
        );
    }

    public boolean canRequestReview(ManagerHomeProfile profile) {
        if (profile == null || !hasRequiredFiles(profile)) {
            return false;
        }
        ManagerDocumentStatus status = profile.getDocumentStatus();
        return status != ManagerDocumentStatus.PENDING_REVIEW
                && status != ManagerDocumentStatus.APPROVED;
    }

    public String buildRequestSummary(ManagerHomeProfile profile) {
        if (!hasRequiredFiles(profile)) {
            return "";
        }
        ManagerDocumentFileType qualificationType = isUploaded(
                profile.getDocumentFile(ManagerDocumentFileType.NURSING_LICENSE)
        ) ? ManagerDocumentFileType.NURSING_LICENSE : ManagerDocumentFileType.LICENSE;

        return context.getString(
                R.string.manager_document_registration_request_summary_format,
                getDocumentLabel(qualificationType)
        );
    }

    private List<ManagerDocumentRegistrationItemModel> createDocumentItems(ManagerHomeProfile profile) {
        List<ManagerDocumentRegistrationItemModel> items = new ArrayList<>();
        items.add(createCombinedLicenseItemModel(profile));
        return items;
    }

    private ManagerDocumentRegistrationItemModel createCombinedLicenseItemModel(ManagerHomeProfile profile) {
        ManagerDocumentFileMetadata nursingMetadata = profile.getDocumentFile(
                ManagerDocumentFileType.NURSING_LICENSE
        );
        ManagerDocumentFileMetadata licenseMetadata = profile.getDocumentFile(ManagerDocumentFileType.LICENSE);
        ManagerDocumentFileMetadata legacyNursingMetadata = profile.getDocumentFile(
                ManagerDocumentFileType.HEALTH_CERTIFICATE
        );

        ManagerDocumentFileMetadata activeMetadata = firstPresent(
                nursingMetadata,
                licenseMetadata,
                legacyNursingMetadata
        );
        boolean uploaded = hasRequiredFiles(profile);
        boolean replacementRequired = requiresQualificationReplacement(profile);
        String label = activeMetadata == null
                ? context.getString(R.string.manager_document_registration_document_nursing_or_elderly_care_license)
                : getDocumentLabel(activeMetadata.getFileType());

        return new ManagerDocumentRegistrationItemModel(
                null,
                activeMetadata == null ? null : activeMetadata.getFileType(),
                label,
                context.getString(R.string.manager_document_registration_nursing_or_elderly_care_license_helper),
                context.getString(
                        uploaded
                                ? R.string.manager_profile_document_state_uploaded
                                : replacementRequired
                                ? R.string.manager_document_registration_status_replace_required
                                : R.string.manager_document_registration_status_needed
                ),
                uploaded ? R.color.bodeul_soft_blue : R.color.bodeul_soft_yellow,
                uploaded ? R.color.bodeul_primary : R.color.bodeul_text_primary,
                activeMetadata == null ? "" : activeMetadata.getFileName(),
                activeMetadata != null
                        ? context.getString(
                                replacementRequired
                                        ? R.string.manager_document_registration_replace_file_body
                                        : R.string.manager_profile_document_file_card_uploaded_at,
                                replacementRequired
                                        ? getDocumentLabel(activeMetadata.getFileType())
                                        : formatter.formatTimestamp(activeMetadata.getUploadedAtMillis())
                        )
                        : context.getString(
                        R.string.manager_document_registration_missing_file_body,
                        label
                ),
                context.getString(activeMetadata != null
                        ? R.string.manager_document_registration_replace_button
                        : R.string.manager_document_registration_upload_button)
        );
    }

    private String buildStatusBody(ManagerDocumentDisplayState state) {
        return context.getString(switch (state) {
            case MISSING -> R.string.manager_document_registration_status_body_not_submitted;
            case REPLACEMENT -> R.string.manager_document_registration_status_body_replacement;
            case DRAFT -> R.string.manager_document_registration_status_body_ready;
            case PENDING -> R.string.manager_document_registration_status_body_pending;
            case APPROVED -> R.string.manager_document_registration_status_body_approved;
            case REJECTED -> R.string.manager_document_registration_status_body_rejected_ready;
        });
    }

    private String buildRequestButtonText(ManagerDocumentStatus status, boolean allRequiredUploaded) {
        if (status == ManagerDocumentStatus.PENDING_REVIEW) {
            return context.getString(R.string.manager_document_registration_request_pending_button);
        }
        if (status == ManagerDocumentStatus.APPROVED) {
            return context.getString(R.string.manager_document_registration_request_done_button);
        }
        if (!allRequiredUploaded) {
            return context.getString(R.string.manager_document_registration_request_disabled_button);
        }
        if (status == ManagerDocumentStatus.REJECTED) {
            return context.getString(R.string.manager_document_registration_request_retry_button);
        }
        return context.getString(R.string.manager_document_registration_request_button);
    }

    static boolean hasRequiredFiles(ManagerHomeProfile profile) {
        if (profile == null) {
            return false;
        }

        ManagerDocumentFileMetadata license = profile.getDocumentFile(ManagerDocumentFileType.LICENSE);
        ManagerDocumentFileMetadata nursingLicense = profile.getDocumentFile(
                ManagerDocumentFileType.NURSING_LICENSE
        );
        int canonicalFileCount = (isPresent(license) ? 1 : 0) + (isPresent(nursingLicense) ? 1 : 0);
        if (canonicalFileCount != 1) {
            return false;
        }
        return isUploaded(isPresent(nursingLicense) ? nursingLicense : license);
    }

    static boolean requiresQualificationReplacement(ManagerHomeProfile profile) {
        if (profile == null || hasRequiredFiles(profile)) {
            return false;
        }
        return firstPresent(
                profile.getDocumentFile(ManagerDocumentFileType.NURSING_LICENSE),
                profile.getDocumentFile(ManagerDocumentFileType.LICENSE),
                profile.getDocumentFile(ManagerDocumentFileType.HEALTH_CERTIFICATE)
        ) != null;
    }

    @Nullable
    private static ManagerDocumentFileMetadata firstPresent(ManagerDocumentFileMetadata... candidates) {
        for (ManagerDocumentFileMetadata candidate : candidates) {
            if (isPresent(candidate)) {
                return candidate;
            }
        }
        return null;
    }

    private static boolean isPresent(ManagerDocumentFileMetadata metadata) {
        return metadata != null && !metadata.isEmpty();
    }

    private static boolean isUploaded(ManagerDocumentFileMetadata metadata) {
        return metadata != null
                && !metadata.isEmpty()
                && metadata.isReferenceConsistent()
                && ManagerDocumentUploadPolicy.isAllowedContentType(metadata.getContentType());
    }

    private String getDocumentLabel(ManagerDocumentFileType fileType) {
        if (fileType == ManagerDocumentFileType.LICENSE) {
            return context.getString(R.string.manager_document_registration_document_elderly_care_license);
        }
        if (fileType == ManagerDocumentFileType.NURSING_LICENSE
                || fileType == ManagerDocumentFileType.HEALTH_CERTIFICATE) {
            return context.getString(R.string.manager_document_registration_document_nursing_license);
        }
        return context.getString(R.string.manager_document_registration_document_nursing_or_elderly_care_license);
    }
}
