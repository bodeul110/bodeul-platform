package com.example.bodeul.ui.manager;

import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import com.example.bodeul.R;
import com.example.bodeul.domain.model.ManagerDocumentFileType;
import com.example.bodeul.util.EnvironmentModeBadgeHelper;

/** 등록과 관리 진입점에 같은 파일·검토 상태를 표시한다. */
public final class ManagerDocumentRegistrationBinder {
    public interface Listener {
        void onDocumentUploadRequested(@Nullable ManagerDocumentFileType type);
        void onDocumentPreviewRequested(@Nullable ManagerDocumentFileType type);
        void onDocumentDetailsRequested();
    }
    private final View root;
    private final Listener listener;
    private final boolean management;
    private ManagerDocumentRegistrationScreenModel model;
    private boolean interactionsEnabled;

    public ManagerDocumentRegistrationBinder(View root, Listener listener, boolean management) {
        this.root = root;
        this.listener = listener;
        this.management = management;
        text(R.id.textManagerDocumentToolbar).setText(management ? R.string.final_documents_title : R.string.final_documents_registration);
        text(R.id.textManagerDocumentHeading).setText(management ? R.string.final_documents_heading : R.string.final_documents_registration_heading);
        text(R.id.textManagerDocumentSubtitle).setText(management ? R.string.final_documents_body : R.string.final_documents_registration_body);
        root.findViewById(R.id.cardManagerDocumentUpload).setVisibility(management ? View.GONE : View.VISIBLE);
        root.findViewById(R.id.cardManagerDocumentSummary).setVisibility(management ? View.VISIBLE : View.GONE);
        root.findViewById(R.id.cardManagerDocumentSummary).setOnClickListener(view -> {
            if (model == null || !interactionsEnabled) return;
            if (model.getDisplayState() == ManagerDocumentDisplayState.MISSING) listener.onDocumentUploadRequested(null);
            else listener.onDocumentDetailsRequested();
        });
    }

    public void bindScreen(ManagerDocumentRegistrationScreenModel model) {
        this.model = model;
        EnvironmentModeBadgeHelper.bind(text(R.id.textManagerDocumentMode), model.getModeText());
        text(R.id.textManagerDocumentStatusTitle).setText(statusLabel(model.getDisplayState()));
        text(R.id.textManagerDocumentStatusBody).setText(model.getStatusBodyText());
        text(R.id.buttonManagerDocumentRequest).setText(requestLabel(model.getDisplayState()));
        root.findViewById(R.id.cardManagerDocumentReview).setVisibility(model.isReviewCardVisible() ? View.VISIBLE : View.GONE);
        text(R.id.textManagerDocumentReviewTitle).setText(model.getReviewTitleText());
        text(R.id.textManagerDocumentReviewBody).setText(model.getReviewBodyText());
        if (!model.getDocumentItems().isEmpty()) {
            ManagerDocumentRegistrationItemModel item = model.getDocumentItems().get(0);
            text(R.id.textManagerDocumentPrimaryTitle).setText(item.getTitleText());
            text(R.id.textManagerDocumentSummaryTitle).setText(item.getTitleText());
            optionalText(R.id.textManagerDocumentPrimaryFileName, item.isPreviewAvailable() ? item.getFileNameText() : "");
            optionalText(R.id.textManagerDocumentPrimaryFileMeta, item.isPreviewAvailable() ? item.getFileMetaText() : "");
            root.findViewById(R.id.buttonManagerDocumentPrimaryPreview).setVisibility(item.isPreviewAvailable() ? View.VISIBLE : View.GONE);
            root.findViewById(R.id.buttonManagerDocumentPrimaryPreview).setOnClickListener(view -> listener.onDocumentPreviewRequested(item.getPreviewFileType()));
            text(R.id.buttonManagerDocumentPrimaryUpload).setText(item.isPreviewAvailable() ? R.string.final_documents_replace : R.string.final_documents_upload);
            root.findViewById(R.id.buttonManagerDocumentPrimaryUpload).setOnClickListener(view -> listener.onDocumentUploadRequested(item.getUploadFileType()));
        }
        bindStatus(text(R.id.textManagerDocumentSummaryStatus), root.findViewById(R.id.imageManagerDocumentSummaryStatus), model.getDisplayState());
        arrangeSummaryRow(model.getDisplayState());
        applyInteractionState();
    }

    private void arrangeSummaryRow(ManagerDocumentDisplayState state) {
        // 긴 검토 안내와 큰 글자는 다음 줄로 보내 원본의 좌우 배치를 읽기 어렵게 축소하지 않는다.
        boolean wrap = root.getResources().getConfiguration().fontScale > 1.3f
                || state == ManagerDocumentDisplayState.REJECTED || state == ManagerDocumentDisplayState.REPLACEMENT;
        LinearLayout row = root.findViewById(R.id.managerDocumentSummaryRow);
        row.setOrientation(wrap ? LinearLayout.VERTICAL : LinearLayout.HORIZONTAL);
        text(R.id.textManagerDocumentSummaryTitle).setLayoutParams(new LinearLayout.LayoutParams(
                wrap ? LinearLayout.LayoutParams.MATCH_PARENT : 0, LinearLayout.LayoutParams.WRAP_CONTENT, wrap ? 0 : 1));
        View status = root.findViewById(R.id.managerDocumentSummaryStatusRow);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                wrap ? LinearLayout.LayoutParams.MATCH_PARENT : LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        params.topMargin = wrap ? Math.round(5 * root.getResources().getDisplayMetrics().density) : 0;
        status.setLayoutParams(params);
    }

    public void setInteractionsEnabled(boolean enabled) { interactionsEnabled = enabled; applyInteractionState(); }

    private void applyInteractionState() {
        boolean enabled = interactionsEnabled && model != null && !model.getDocumentItems().isEmpty();
        for (int id : new int[]{R.id.buttonManagerDocumentPrimaryPreview, R.id.buttonManagerDocumentPrimaryUpload,
                R.id.cardManagerDocumentSummary, R.id.buttonManagerDocumentHistory}) root.findViewById(id).setEnabled(enabled);
        View request = root.findViewById(R.id.buttonManagerDocumentRequest);
        boolean requestEnabled = enabled && model.isRequestButtonEnabled();
        request.setEnabled(requestEnabled);
    }
    private TextView text(int id) { return root.findViewById(id); }
    private void optionalText(int id, String value) {
        text(id).setText(value);
        text(id).setVisibility(value.isEmpty() ? View.GONE : View.VISIBLE);
    }
    static int statusLabel(ManagerDocumentDisplayState state) {
        switch (state) {
            case APPROVED: return R.string.final_documents_approved;
            case PENDING: return R.string.final_documents_pending;
            case REJECTED: return R.string.final_documents_rejected;
            case REPLACEMENT: return R.string.final_documents_replacement;
            case DRAFT: return R.string.final_documents_draft;
            default: return R.string.final_documents_add;
        }
    }
    static int requestLabel(ManagerDocumentDisplayState state) {
        if (state == ManagerDocumentDisplayState.APPROVED) return R.string.final_documents_approved;
        if (state == ManagerDocumentDisplayState.PENDING) return R.string.final_documents_pending;
        if (state == ManagerDocumentDisplayState.REJECTED) return R.string.final_documents_retry;
        return R.string.final_documents_submit;
    }
    static void bindStatus(TextView text, ImageView icon, ManagerDocumentDisplayState state) {
        text.setText(statusLabel(state));
        boolean warning = state == ManagerDocumentDisplayState.REJECTED || state == ManagerDocumentDisplayState.REPLACEMENT;
        text.setTextColor(ContextCompat.getColor(text.getContext(), warning ? R.color.final_text_error
                : state == ManagerDocumentDisplayState.APPROVED ? R.color.final_text_accent_accessible : R.color.final_text_secondary));
        icon.setVisibility(warning || state == ManagerDocumentDisplayState.APPROVED ? View.VISIBLE : View.GONE);
        icon.setImageResource(warning ? R.drawable.bodeul_final_document_warning : R.drawable.bodeul_final_document_check);
    }
}
