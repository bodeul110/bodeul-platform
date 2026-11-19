package com.example.bodeul.ui.profile;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import com.example.bodeul.R;
import com.example.bodeul.domain.model.User;
import com.example.bodeul.domain.model.UserRole;
import com.example.bodeul.ui.health.HealthInfoActivity;
import com.example.bodeul.ui.manager.ManagerHistoryActivity;
import com.example.bodeul.ui.manager.ManagerDocumentRegistrationActivity;
import com.example.bodeul.ui.manager.ManagerSupportActivity;
import com.example.bodeul.ui.support.ClientSupportActivity;
import com.example.bodeul.ui.support.FaqActivity;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

/** 메뉴의 기존 화면 연결과 미연결 계약 안내를 담당하며 서버 역할은 변경하지 않는다. */
public final class ProfileActionRouter {
    private final AppCompatActivity activity;
    private AlertDialog dialog;
    public ProfileActionRouter(AppCompatActivity activity) { this.activity = activity; }

    public void open(ProfileMenuItem item, User user) {
        if (user == null) return;
        switch (item) {
            case SUPPORT:
                activity.startActivity(user.getRole() == UserRole.MANAGER
                        ? ManagerSupportActivity.createIntent(activity) : ClientSupportActivity.createIntent(activity));
                break;
            case ACCOUNT: activity.startActivity(AccountSettingsActivity.createIntent(activity)); break;
            case NOTIFICATIONS: activity.startActivity(NotificationSettingsActivity.createIntent(activity)); break;
            case MATCHING:
            case REPORT:
                if (user.getRole() == UserRole.MANAGER) activity.startActivity(ManagerHistoryActivity.createIntent(activity));
                break;
            case DOCUMENTS:
                if (user.getRole() == UserRole.MANAGER) activity.startActivity(ManagerDocumentRegistrationActivity.createManagementIntent(activity));
                break;
            case VERSION: openInfo(ProfileInfoActivity.Page.VERSION); break;
            case NOTICE: openInfo(ProfileInfoActivity.Page.NOTICE); break;
            case FAQ: activity.startActivity(FaqActivity.createIntent(activity)); break;
            case TERMS: openInfo(ProfileInfoActivity.Page.TERMS); break;
            case PAYMENT: activity.startActivity(PaymentInfoActivity.createIntent(activity)); break;
            case SETTLEMENT: show(item.labelId, R.string.final_profile_settlement_unavailable); break;
        }
    }

    public void showAccount(User user, boolean editing) {
        if (user == null) return;
        dismiss();
        if (editing) { openInfo(ProfileInfoActivity.Page.EDIT); return; }
        String message = activity.getString(R.string.final_profile_account_details,
                value(user.getName()), value(user.getEmail()), value(user.getPhone()), roleLabel(user));
        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(activity)
                .setTitle(R.string.final_profile_account).setMessage(message)
                .setPositiveButton(R.string.permission_guide_confirm, null);
        if (user.getRole() == UserRole.PATIENT || user.getRole() == UserRole.GUARDIAN) {
            builder.setNeutralButton(R.string.final_profile_health,
                    (view, which) -> activity.startActivity(HealthInfoActivity.createIntent(activity)));
        }
        dialog = builder.show();
    }

    public void explainRole(User user) {
        if (user != null) show(R.string.client_profile_line_role,
                activity.getString(R.string.final_profile_switch_unavailable, roleLabel(user)));
    }
    public void confirmSignOut(Runnable signOut) {
        dismiss();
        dialog = new MaterialAlertDialogBuilder(activity).setTitle(R.string.final_profile_sign_out)
                .setMessage(R.string.final_profile_sign_out_confirm).setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.final_profile_sign_out, (view, which) -> signOut.run()).show();
    }
    private String roleLabel(User user) {
        return activity.getString(user.getRole() == UserRole.MANAGER ? R.string.login_role_manager
                : user.getRole() == UserRole.GUARDIAN ? R.string.login_role_guardian : R.string.login_role_patient);
    }
    private String value(String value) {
        return value == null || value.trim().isEmpty() ? activity.getString(R.string.final_profile_unknown) : value.trim();
    }
    private void show(int title, int message) { show(title, activity.getString(message)); }
    private void show(int title, String message) {
        dismiss();
        dialog = new MaterialAlertDialogBuilder(activity)
                .setTitle(title)
                .setMessage(message).setPositiveButton(R.string.permission_guide_confirm, null).show();
    }
    public void dismiss() { if (dialog != null) { dialog.dismiss(); dialog = null; } }
    private void openInfo(ProfileInfoActivity.Page page) { activity.startActivity(ProfileInfoActivity.createIntent(activity, page)); }
}
