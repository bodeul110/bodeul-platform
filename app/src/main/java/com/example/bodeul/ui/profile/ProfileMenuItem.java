package com.example.bodeul.ui.profile;

import com.example.bodeul.R;

/** 화면 배치와 동작이 같은 식별자를 사용하도록 메뉴 계약을 모은다. */
public enum ProfileMenuItem {
    SUPPORT(R.id.profileMenuSupport, R.string.final_profile_support, R.drawable.bodeul_final_profile_support),
    NOTICE(R.id.profileMenuNotice, R.string.final_profile_notice, R.drawable.bodeul_final_profile_announcement),
    FAQ(R.id.profileMenuFaq, R.string.final_profile_faq, R.drawable.bodeul_final_profile_faq),
    TERMS(R.id.profileMenuTerms, R.string.final_profile_terms, R.drawable.bodeul_final_profile_terms),
    VERSION(R.id.profileMenuVersion, R.string.final_profile_version, R.drawable.bodeul_final_profile_info),
    PAYMENT(R.id.profileMenuPayment, R.string.final_profile_payment, R.drawable.bodeul_final_profile_payment),
    ACCOUNT(R.id.profileMenuAccount, R.string.final_profile_account, R.drawable.bodeul_final_profile_account),
    NOTIFICATIONS(R.id.profileMenuNotifications, R.string.final_profile_notifications, R.drawable.bodeul_final_profile_alarm),
    SETTLEMENT(R.id.profileMenuSettlement, R.string.final_profile_settlement, R.drawable.bodeul_final_profile_settlement),
    MATCHING(R.id.profileMenuMatching, R.string.final_profile_matching, R.drawable.bodeul_final_profile_matching),
    REPORT(R.id.profileMenuReport, R.string.final_profile_report, R.drawable.bodeul_final_profile_report),
    DOCUMENTS(R.id.profileMenuDocuments, R.string.final_profile_documents, R.drawable.bodeul_final_profile_document);

    public final int viewId;
    public final int labelId;
    public final int iconId;
    ProfileMenuItem(int viewId, int labelId, int iconId) {
        this.viewId = viewId; this.labelId = labelId; this.iconId = iconId;
    }
}
