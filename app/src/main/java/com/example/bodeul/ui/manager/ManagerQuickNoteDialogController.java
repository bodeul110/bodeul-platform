package com.example.bodeul.ui.manager;

import android.content.Context;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;

import androidx.appcompat.app.AlertDialog;

import com.example.bodeul.R;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

/**
 * 서류 요약과 일정 요약을 같은 입력 규칙으로 수정하도록 공통 대화상자를 제공한다.
 */
public final class ManagerQuickNoteDialogController {
    private final Context context;
    private final LayoutInflater inflater;

    public ManagerQuickNoteDialogController(Context context, LayoutInflater inflater) {
        this.context = context;
        this.inflater = inflater;
    }

    public AlertDialog show(ManagerQuickNoteType noteType, String initialValue, Listener listener) {
        View dialogView = inflater.inflate(R.layout.dialog_manager_quick_note, null, false);
        TextInputLayout inputLayout = dialogView.findViewById(R.id.layoutManagerQuickNote);
        TextInputEditText inputEditText = dialogView.findViewById(R.id.inputManagerQuickNote);

        if (noteType == ManagerQuickNoteType.DOCUMENT) {
            inputLayout.setHint(context.getString(R.string.manager_action_docs_input_hint));
            inputLayout.setHelperText(context.getString(R.string.manager_action_docs_input_helper));
        } else {
            inputLayout.setHint(context.getString(R.string.manager_action_schedule_input_hint));
            inputLayout.setHelperText(context.getString(R.string.manager_action_schedule_input_helper));
        }

        inputEditText.setText(initialValue == null ? "" : initialValue);
        if (inputEditText.getText() != null) {
            inputEditText.setSelection(inputEditText.getText().length());
        }

        AlertDialog dialog = new MaterialAlertDialogBuilder(context)
                .setTitle(noteType == ManagerQuickNoteType.DOCUMENT
                        ? R.string.manager_action_docs_dialog_title
                        : R.string.manager_action_schedule_dialog_title)
                .setView(dialogView)
                .setNegativeButton(R.string.manager_action_dialog_cancel, null)
                .setPositiveButton(R.string.manager_action_dialog_save, null)
                .create();

        dialog.setOnShowListener(dialogInterface -> dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener(view -> {
                    String value = inputEditText.getText() == null
                            ? ""
                            : inputEditText.getText().toString().trim();
                    if (TextUtils.isEmpty(value)) {
                        inputLayout.setError(context.getString(R.string.error_required_field));
                        return;
                    }
                    inputLayout.setError(null);
                    listener.onSave(noteType, value, dialog);
                }));
        dialog.show();
        return dialog;
    }

    public interface Listener {
        void onSave(ManagerQuickNoteType noteType, String value, AlertDialog dialog);
    }
}
