package com.example.bodeul.ui.profile;

import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import com.example.bodeul.R;
import com.example.bodeul.domain.model.AccountSignInInfo;
import com.example.bodeul.domain.model.AccountSignInInfo.Provider;

final class AccountSettingsBinder {
    interface Actions { void resetPassword(); void consent(); void link(); void withdraw(); }
    private final TextView password;

    AccountSettingsBinder(LinearLayout parent, AccountSignInInfo info, Actions actions) {
        View root = LayoutInflater.from(parent.getContext()).inflate(R.layout.include_account_settings_detail, parent, false);
        parent.addView(root);
        TextView email = root.findViewById(R.id.accountSettingsEmail);
        email.setText(info.getEmail().isEmpty() ? parent.getContext().getString(R.string.final_profile_unknown) : info.getEmail());
        password = root.findViewById(R.id.buttonAccountPassword);
        password.setOnClickListener(view -> actions.resetPassword());
        root.findViewById(R.id.accountPasswordSection).setVisibility(info.canResetPassword() ? View.VISIBLE : View.GONE);
        Provider primary = info.getPrimaryProvider();
        if (primary != Provider.EMAIL) {
            TextView providerName = root.findViewById(R.id.accountProviderName);
            providerName.setText(label(primary));
            providerName.setVisibility(View.VISIBLE);
            bindIcon(root.findViewById(R.id.accountProviderIcon), primary);
            email.setTextAppearance(R.style.FinalSettingsLabel);
            email.setTextColor(parent.getContext().getColor(R.color.final_text_secondary));
            View card = root.findViewById(R.id.accountEmailCard);
            int verticalPadding = Math.round(20 * parent.getResources().getDisplayMetrics().density);
            card.setPaddingRelative(card.getPaddingStart(), verticalPadding, card.getPaddingEnd(), verticalPadding);
            root.findViewById(R.id.accountProviderState).setVisibility(primary == Provider.UNKNOWN ? View.GONE : View.VISIBLE);
        }
        root.findViewById(R.id.buttonAccountPrivacyConsent).setOnClickListener(view -> actions.consent());
        root.findViewById(R.id.buttonAccountMarketingConsent).setOnClickListener(view -> actions.consent());
        root.findViewById(R.id.buttonAccountWithdraw).setOnClickListener(view -> actions.withdraw());
        if (info.has(Provider.EMAIL)) {
            root.findViewById(R.id.accountLinkSection).setVisibility(View.VISIBLE);
            LinearLayout rows = root.findViewById(R.id.accountProviderRows);
            for (Provider provider : new Provider[]{Provider.KAKAO, Provider.GOOGLE, Provider.APPLE}) {
                View row = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_account_provider, rows, false);
                bindIcon(row.findViewById(R.id.providerRowIcon), provider);
                ((TextView) row.findViewById(R.id.providerRowName)).setText(label(provider));
                ((TextView) row.findViewById(R.id.providerRowState)).setText(info.has(provider)
                        ? R.string.final_settings_link_complete : R.string.final_settings_link_pending);
                row.setOnClickListener(view -> actions.link());
                rows.addView(row);
            }
        }
    }
    void setSending(boolean sending) {
        password.setEnabled(!sending);
        password.setText(sending ? R.string.final_settings_password_sending : R.string.final_settings_password_change);
    }
    private static int label(Provider provider) {
        switch (provider) {
            case EMAIL: return R.string.final_settings_provider_email;
            case GOOGLE: return R.string.final_settings_provider_google;
            case KAKAO: return R.string.final_settings_provider_kakao;
            case NAVER: return R.string.final_settings_provider_naver;
            case APPLE: return R.string.final_settings_provider_apple;
            default: return R.string.final_settings_login_unknown;
        }
    }
    private static void bindIcon(ImageView icon, Provider provider) {
        icon.setVisibility(View.VISIBLE);
        int padding = Math.round(6 * icon.getResources().getDisplayMetrics().density);
        switch (provider) {
            case KAKAO: icon.setImageResource(R.drawable.bodeul_final_kakao); break;
            case GOOGLE:
                icon.setBackgroundResource(R.drawable.bodeul_final_google_background);
                icon.setImageResource(R.drawable.bodeul_final_google_mark);
                icon.setPadding(padding, padding, padding, padding);
                break;
            case APPLE:
                icon.setBackgroundResource(R.drawable.bodeul_final_apple_background);
                icon.setImageResource(R.drawable.bodeul_final_apple_mark);
                icon.setPadding(padding, padding, padding, padding);
                break;
            default: icon.setVisibility(View.GONE);
        }
    }
}
