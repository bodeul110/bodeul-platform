package com.example.bodeul.ui.manager;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import com.example.bodeul.R;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;

/** 민감 파일과 검토 메모를 arguments에 저장하지 않고 현재 인증된 화면에서만 공급받는다. */
public final class ManagerDocumentDetailSheet extends BottomSheetDialogFragment {
    static final String TAG = "manager_document_detail";
    @Nullable private View content;

    @Override public int getTheme() { return R.style.FinalDocumentSheetTheme; }

    @Nullable @Override public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle state) {
        content = inflater.inflate(R.layout.sheet_manager_document, container, false);
        content.findViewById(R.id.documentDetailClose).setOnClickListener(view -> dismiss());
        ViewCompat.setOnApplyWindowInsetsListener(content, (view, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
            view.setPadding(bars.left, 0, bars.right, bars.bottom);
            return insets;
        });
        return content;
    }
    @Override public void onStart() {
        super.onStart();
        if (getDialog() instanceof BottomSheetDialog) {
            BottomSheetBehavior<?> behavior = ((BottomSheetDialog) getDialog()).getBehavior();
            behavior.setShouldRemoveExpandedCorners(false);
            behavior.setState(BottomSheetBehavior.STATE_EXPANDED);
            behavior.setSkipCollapsed(true);
        }
        host().bindDocumentDetails(this);
        if (content != null) ViewCompat.requestApplyInsets(content);
    }
    void bind(@Nullable ManagerDocumentRegistrationScreenModel model, boolean enabled) {
        if (content == null) return;
        boolean available = model != null && !model.getDocumentItems().isEmpty();
        content.findViewById(R.id.documentDetailContent).setVisibility(available ? View.VISIBLE : View.GONE);
        content.findViewById(R.id.documentDetailLoading).setVisibility(available ? View.GONE : View.VISIBLE);
        if (!available) {
            for (int id : new int[]{R.id.documentDetailTitle, R.id.documentDetailFile, R.id.documentDetailReview}) ((TextView) content.findViewById(id)).setText("");
            return;
        }
        ManagerDocumentRegistrationItemModel item = model.getDocumentItems().get(0);
        ((TextView) content.findViewById(R.id.documentDetailTitle)).setText(item.getTitleText());
        ((TextView) content.findViewById(R.id.documentDetailFile)).setText(item.getFileNameText());
        ManagerDocumentRegistrationBinder.bindStatus(content.findViewById(R.id.documentDetailStatus),
                content.findViewById(R.id.documentDetailStatusIcon), model.getDisplayState());
        TextView review = content.findViewById(R.id.documentDetailReview);
        review.setText(model.getReviewBodyText());
        review.setVisibility(model.isReviewCardVisible() ? View.VISIBLE : View.GONE);
        View preview = content.findViewById(R.id.documentDetailPreview);
        preview.setEnabled(enabled && item.isPreviewAvailable());
        preview.setOnClickListener(view -> host().onDocumentPreviewRequested(item.getPreviewFileType()));
        View replace = content.findViewById(R.id.documentDetailReplace);
        replace.setEnabled(enabled);
        replace.setOnClickListener(view -> { host().onDocumentUploadRequested(item.getUploadFileType()); dismiss(); });
    }
    private ManagerDocumentRegistrationActivity host() { return (ManagerDocumentRegistrationActivity) requireActivity(); }
    @Override public void onDestroyView() { content = null; super.onDestroyView(); }
}
