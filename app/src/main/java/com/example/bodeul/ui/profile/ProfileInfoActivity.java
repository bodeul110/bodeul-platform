package com.example.bodeul.ui.profile;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.annotation.Nullable;
import com.example.bodeul.BuildConfig;
import com.example.bodeul.R;
import com.example.bodeul.domain.model.User;
import com.example.bodeul.domain.model.UserRole;
import com.example.bodeul.ui.common.DetailViews;
import com.example.bodeul.ui.health.HealthInfoActivity;
import com.example.bodeul.ui.manager.ManagerSupportActivity;
import com.example.bodeul.ui.support.ClientSupportActivity;

/** 앱 정보·안내 화면. 원문이나 사용자 변경을 제공할 수 없는 경우 그 상태를 명시한다. */
public class ProfileInfoActivity extends SettingsDetailActivity {
    public enum Page {
        VERSION(R.string.batch_version), NOTICE(R.string.final_profile_notice),
        TERMS(R.string.final_profile_terms), EDIT(R.string.batch_edit);
        final int title;
        Page(int title) { this.title = title; }
    }
    private static final String PAGE = "profile_info_page", TAB = "filter_index";
    private static final int[] NOTICES = {R.string.batch_all, R.string.batch_notice, R.string.batch_update,
            R.string.batch_event, R.string.batch_other};
    private static final int[] TERMS = {R.string.batch_policy_privacy, R.string.batch_policy_terms};
    private Page page;
    private ProfileActionRouter accountActions;
    @Override protected void onPrivateStateCleared() { if (accountActions != null) accountActions.dismiss(); }
    private int tab;
    public static Intent createIntent(Context c, Page page) {
        return new Intent(c, ProfileInfoActivity.class).putExtra(PAGE, page.name());
    }
    @Override protected void onCreate(@Nullable Bundle state) {
        for (Page candidate : Page.values()) if (candidate.name().equals(getIntent().getStringExtra(PAGE))) page = candidate;
        super.onCreate(state);
        if (page == null) { finish(); return; }
        tab = state == null ? 0 : state.getInt(TAB);
    }
    @Override protected int titleResource() { return page == null ? R.string.final_profile_settings : page.title; }
    @Override protected void onSaveInstanceState(Bundle out) { super.onSaveInstanceState(out); out.putInt(TAB, tab); }
    @Override protected void showUser(User current, int attempt) {
        if (page == null) return;
        ready();
        if (page == Page.VERSION || page == Page.EDIT) {
            boolean edit = page == Page.EDIT;
            content.setGravity(edit ? Gravity.TOP | Gravity.CENTER_HORIZONTAL : Gravity.CENTER);
            DetailViews.image(content, edit ? R.drawable.bodeul_final_profile_avatar : R.drawable.bodeul_final_version_mark, edit ? 100 : 60);
            String name = current.getName() == null || current.getName().trim().isEmpty()
                    ? getString(R.string.final_profile_unknown) : current.getName();
            TextView label = DetailViews.text(content, edit ? name : getString(R.string.batch_installed_version, BuildConfig.VERSION_NAME),
                    edit ? R.style.FinalHomeTitle : R.style.FinalSettingsLabel);
            label.setGravity(Gravity.CENTER);
            if (edit) {
                DetailViews.space(content, 24);
                DetailViews.action(content, R.string.batch_edit_action, () -> message(R.string.batch_edit, R.string.final_profile_edit_unavailable));
                accountActions = new ProfileActionRouter(this);
                DetailViews.action(content, R.string.final_profile_account, () -> accountActions.showAccount(current,false));
                if (current.getRole() != UserRole.MANAGER) {
                    DetailViews.action(content, R.string.final_profile_health, () -> startActivity(HealthInfoActivity.createIntent(this)));
                }
            }
            return;
        }
        int[] options = page == Page.NOTICE ? NOTICES : TERMS;
        if (tab < 0 || tab >= options.length) tab = 0;
        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        DetailViews.filters(content, options, tab, index -> { tab = index; renderPage(body, options, current); });
        DetailViews.space(content, 20);
        content.addView(body, -1, -2);
        renderPage(body, options, current);
    }
    private void renderPage(LinearLayout body, int[] options, User current) {
        body.removeAllViews();
        String title = getString(options[tab]);
        DetailViews.text(body, title, R.style.FinalHomeTitle);
        DetailViews.space(body, 10);
        DetailViews.muted(DetailViews.text(body, getString(page == Page.TERMS ? R.string.batch_policy_missing
                : R.string.batch_notice_unavailable, title), R.style.FinalProfileBody));
        if (page == Page.NOTICE && tab == 0 && current.getRole() != UserRole.MANAGER) {
            DetailViews.space(body, 20);
            DetailViews.text(body, getString(R.string.batch_guide), R.style.FinalHomeTitle);
            java.util.Set<Integer> shown = new java.util.HashSet<>();
            for (com.example.bodeul.ui.home.ClientHomeNotice notice :
                    new com.example.bodeul.ui.home.ClientHomeNoticeProvider().createNotices(current.getRole())) {
                if (!shown.add(notice.getTitleResId())) continue;
                DetailViews.space(body, 12);
                DetailViews.text(body, getString(notice.getTitleResId()), R.style.FinalProfileBody);
                DetailViews.muted(DetailViews.text(body, getString(notice.getBodyResId()), R.style.FinalSettingsValue));
            }
        }
        DetailViews.space(body, 20);
        DetailViews.action(body, R.string.final_profile_support, () -> startActivity(current.getRole() == UserRole.MANAGER
                ? ManagerSupportActivity.createIntent(this) : ClientSupportActivity.createIntent(this)));
    }
}
