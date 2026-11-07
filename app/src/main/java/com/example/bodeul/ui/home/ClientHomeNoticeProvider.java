package com.example.bodeul.ui.home;

import com.example.bodeul.R;
import com.example.bodeul.domain.model.UserRole;

import java.util.ArrayList;
import java.util.List;

/**
 * 홈 하단 안내 카드 목록을 역할에 맞게 제공한다.
 */
public final class ClientHomeNoticeProvider {
    public List<ClientHomeNotice> createNotices(UserRole role) {
        List<ClientHomeNotice> notices = new ArrayList<>();
        notices.add(new ClientHomeNotice(
                R.drawable.bodeul_final_home_article_1,
                R.string.client_home_notice_prepare_eyebrow,
                R.string.client_home_notice_prepare_title,
                R.string.client_home_notice_prepare_body
        ));
        notices.add(new ClientHomeNotice(
                R.drawable.bodeul_final_home_article_2,
                role == UserRole.GUARDIAN
                        ? R.string.client_home_notice_guardian_eyebrow
                        : R.string.client_home_notice_patient_eyebrow,
                role == UserRole.GUARDIAN
                        ? R.string.client_home_notice_guardian_title
                        : R.string.client_home_notice_patient_title,
                role == UserRole.GUARDIAN
                        ? R.string.client_home_notice_guardian_body
                        : R.string.client_home_notice_patient_body
        ));
        notices.add(new ClientHomeNotice(R.drawable.bodeul_final_home_article_3,
                R.string.client_home_notice_patient_eyebrow,
                R.string.final_home_guide_steps_title, R.string.final_home_guide_steps_body));
        notices.add(new ClientHomeNotice(R.drawable.bodeul_final_home_article_4,
                R.string.final_home_support,
                R.string.final_home_guide_support_title, R.string.final_home_guide_support_body));
        return notices;
    }
}
