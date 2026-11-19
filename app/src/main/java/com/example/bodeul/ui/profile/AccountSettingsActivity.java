package com.example.bodeul.ui.profile;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import androidx.annotation.Nullable;
import androidx.lifecycle.ViewModelProvider;
import com.example.bodeul.R;
import com.example.bodeul.data.RepositoryCallback;
import com.example.bodeul.domain.model.AccountSignInInfo;
import com.example.bodeul.domain.model.User;
import com.example.bodeul.domain.model.UserRole;
import com.example.bodeul.ui.manager.ManagerSupportActivity;
import com.example.bodeul.ui.support.ClientSupportActivity;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

public class AccountSettingsActivity extends SettingsDetailActivity {
    private AccountSettingsBinder binder;
    private AccountSignInInfo account;
    private AccountPasswordResetViewModel reset;
    public static Intent createIntent(Context context) { return new Intent(context, AccountSettingsActivity.class); }
    @Override protected int titleResource() { return R.string.final_settings_account_title; }
    @Override protected void onCreate(@Nullable Bundle state) {
        super.onCreate(state);
        reset = new ViewModelProvider(this).get(AccountPasswordResetViewModel.class);
        reset.status().observe(this, status -> renderResetStatus());
    }
    @Override protected void showUser(User user, int attempt) {
        reset.bind(user.getId());
        auth.getAccountSignInInfo(user.getId(), new RepositoryCallback<AccountSignInInfo>() {
            @Override public void onSuccess(AccountSignInInfo info) {
                if (!isCurrent(attempt)) return;
                if (info == null || !user.getId().equals(info.getUserId())) { showError(); return; }
                ready();
                account = info;
                binder = new AccountSettingsBinder(content, info, new AccountSettingsBinder.Actions() {
                    @Override public void resetPassword() { confirmPasswordReset(); }
                    @Override public void consent() { message(R.string.final_settings_optional_terms, R.string.final_settings_consent_unavailable); }
                    @Override public void link() { message(R.string.final_settings_link_heading, R.string.final_settings_link_unavailable); }
                    @Override public void withdraw() { explainWithdrawal(); }
                });
                renderResetStatus();
            }
            @Override public void onError(String message) { if (isCurrent(attempt)) showError(); }
        });
    }
    @Override protected void onPrivateStateCleared() { binder = null; account = null; }
    private void confirmPasswordReset() {
        if (account == null || !account.canResetPassword() || reset.status().getValue() == AccountPasswordResetViewModel.Status.SENDING) return;
        AccountSignInInfo confirmed = account;
        int attempt = currentAttempt();
        dismissDialog();
        dialog = new MaterialAlertDialogBuilder(this).setTitle(R.string.final_settings_password_change)
                .setMessage(getString(R.string.final_settings_password_confirm, confirmed.getEmail()))
                .setNegativeButton(R.string.final_settings_cancel, null)
                .setPositiveButton(R.string.final_settings_password_send, (view, which) -> {
                    if (isCurrent(attempt) && account == confirmed) reset.send(auth, confirmed);
                }).show();
    }
    private void renderResetStatus() {
        if (binder == null || account == null) return;
        AccountPasswordResetViewModel.Status status = reset.status().getValue();
        binder.setSending(status == AccountPasswordResetViewModel.Status.SENDING);
        if (status == AccountPasswordResetViewModel.Status.SENT || status == AccountPasswordResetViewModel.Status.FAILED) {
            message(R.string.final_settings_password_change, status == AccountPasswordResetViewModel.Status.SENT
                    ? R.string.final_settings_password_sent : R.string.final_settings_password_error);
            reset.consumeNotice();
        }
    }
    private void explainWithdrawal() {
        if (user == null) return;
        int attempt = currentAttempt();
        Intent destination = user.getRole() == UserRole.MANAGER ? ManagerSupportActivity.createIntent(this) : ClientSupportActivity.createIntent(this);
        dismissDialog();
        dialog = new MaterialAlertDialogBuilder(this).setTitle(R.string.final_settings_withdraw)
                .setMessage(R.string.final_settings_withdraw_unavailable).setNegativeButton(R.string.final_settings_cancel, null)
                .setPositiveButton(R.string.final_settings_support, (view, which) -> { if (isCurrent(attempt)) startActivity(destination); }).show();
    }
}
