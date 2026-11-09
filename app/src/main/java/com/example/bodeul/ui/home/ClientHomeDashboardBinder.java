package com.example.bodeul.ui.home;

import android.content.Context;
import android.graphics.Matrix;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.example.bodeul.R;
import com.example.bodeul.domain.model.AppointmentRequest;
import com.example.bodeul.domain.model.AppointmentStatus;
import com.example.bodeul.domain.model.GuardianReportEntry;
import com.example.bodeul.ui.booking.BookingPresentationFormatter;
import com.example.bodeul.ui.common.AttentionBannerBinder;
import com.example.bodeul.ui.common.AppointmentProgressOverviewModel;
import com.example.bodeul.ui.common.AppointmentProgressStageItemBinder;
import com.example.bodeul.ui.common.AppointmentProgressStageModel;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

/** 완성본 홈에 실제 예약 상태와 안내 콘텐츠를 연결한다. */
public final class ClientHomeDashboardBinder {
    private final Context context;
    private final View root;
    private final AttentionBannerBinder supportBannerBinder;

    public ClientHomeDashboardBinder(Context context, View root) {
        this.context = context;
        this.root = root;
        supportBannerBinder = new AttentionBannerBinder(context,
                root.findViewById(R.id.layoutHomeSupportBanner));
    }

    public void bindDashboard(ClientHomeDashboard dashboard) {
        TextView badge = root.getRootView().findViewById(R.id.textHomeSupportHeaderBadge);
        int count = dashboard.getUnreadSupportResponseCount();
        badge.setVisibility(count > 0 ? View.VISIBLE : View.GONE);
        badge.setText(String.valueOf(count));
        String notificationDescription = count > 0
                ? context.getString(R.string.final_home_unread, count)
                : context.getString(R.string.final_home_notifications);
        if (dashboard.hasStaleUnreadSupportResponses()) {
            notificationDescription += ", " + context.getString(R.string.client_home_support_header_badge_overdue,
                    dashboard.getStaleUnreadSupportResponseCount());
        }
        root.getRootView().findViewById(R.id.buttonHomeSupport).setContentDescription(notificationDescription);
        supportBannerBinder.bind(dashboard.getSupportBanner());
        bindProgress(dashboard);
        bindNotices(dashboard);
    }

    public void setOnSupportBannerClickListener(View.OnClickListener listener) {
        supportBannerBinder.setOnActionClickListener(listener);
    }

    private void bindProgress(ClientHomeDashboard dashboard) {
        View section = root.findViewById(R.id.layoutHomeProgressSection);
        AppointmentProgressOverviewModel progress = dashboard.getProgressOverview();
        LinearLayout stages = root.findViewById(R.id.layoutHomeProgressStageContainer);
        stages.removeAllViews();
        if (progress == null || dashboard.getPrimaryRequest() == null) {
            section.setVisibility(View.GONE);
            return;
        }
        section.setVisibility(View.VISIBLE);
        setText(R.id.textHomeProgressBadge, progress.getBadgeText());
        setText(R.id.textHomeProgressTitle, progress.getTitleText());
        AppointmentRequest request = dashboard.getPrimaryRequest();
        String body = context.getString(R.string.final_home_current_booking,
                request.getAppointmentAt(), TextUtils.isEmpty(request.getMeetingPlace())
                        ? context.getString(R.string.booking_status_place_missing) : request.getMeetingPlace())
                + "\n\n" + progress.getBodyText();
        if (request.getStatus() == AppointmentStatus.COMPLETED && dashboard.getPrimaryFollowUpRecord() != null) {
            body = body + "\n" + new BookingPresentationFormatter(context)
                    .buildFollowUpSummary(dashboard.getPrimaryFollowUpRecord());
        } else if (dashboard.isGuardianUser()) {
            GuardianReportEntry entry = dashboard.getHighlightGuardianEntry();
            if (entry != null && entry.getSession() != null) {
                String update = entry.getSession().getGuardianUpdate();
                if (!TextUtils.isEmpty(update) && !body.contains(update)) body += "\n" + update;
            }
        }
        setText(R.id.textHomeProgressBody, body);
        setText(R.id.buttonHomeProgressDetail, context.getString(dashboard.isGuardianUser()
                ? R.string.client_home_hero_report_button : R.string.client_home_progress_detail_button));
        AppointmentProgressStageItemBinder binder = new AppointmentProgressStageItemBinder(context);
        for (AppointmentProgressStageModel stage : progress.getStages()) {
            View row = LayoutInflater.from(context).inflate(R.layout.item_appointment_progress_stage, stages, false);
            binder.bind(row, stage);
            stages.addView(row);
        }
    }

    private void bindNotices(ClientHomeDashboard dashboard) {
        LinearLayout container = root.findViewById(R.id.layoutHomeNoticeContainer);
        container.removeAllViews();
        for (ClientHomeNotice notice : dashboard.getNotices()) {
            View row = LayoutInflater.from(context).inflate(R.layout.item_final_home_notice, container, false);
            ImageView image = row.findViewById(R.id.imageNoticeBanner);
            image.setImageResource(notice.getBannerBackgroundResId());
            applyOriginalImageCrop(image, notice.getBannerBackgroundResId());
            ((TextView) row.findViewById(R.id.textNoticeTitle)).setText(notice.getTitleResId());
            ((TextView) row.findViewById(R.id.textNoticeEyebrow)).setText(notice.getEyebrowResId());
            row.setOnClickListener(view -> new MaterialAlertDialogBuilder(context)
                    .setTitle(notice.getTitleResId()).setMessage(notice.getBodyResId())
                    .setPositiveButton(R.string.permission_guide_confirm, null).show());
            if (container.getChildCount() > 0) {
                ((LinearLayout.LayoutParams) row.getLayoutParams()).topMargin =
                        Math.round(25 * context.getResources().getDisplayMetrics().density);
            }
            container.addView(row);
        }
    }

    private void setText(int id, CharSequence value) {
        ((TextView) root.findViewById(id)).setText(value);
    }

    private void applyOriginalImageCrop(ImageView image, int imageId) {
        if (imageId != R.drawable.bodeul_final_home_article_2
                && imageId != R.drawable.bodeul_final_home_article_4) return;
        image.setScaleType(ImageView.ScaleType.MATRIX);
        // 원본 자산을 자르거나 다시 저장하지 않고 Figma의 이미지 배치 비율을 유지한다.
        image.addOnLayoutChangeListener((view, l, t, r, b, ol, ot, or, ob) -> {
            boolean second = imageId == R.drawable.bodeul_final_home_article_2;
            Matrix matrix = new Matrix();
            matrix.setScale((float) (image.getWidth() * (second ? 1.3721 : 1.9714) / image.getDrawable().getIntrinsicWidth()),
                    (float) (image.getHeight() * (second ? 1.3721 : 1.3143) / image.getDrawable().getIntrinsicHeight()));
            matrix.postTranslate((float) (-image.getWidth() * (second ? 0.1861 : 0.2426)),
                    (float) (-image.getHeight() * (second ? 0.0128 : 0.1571)));
            image.setImageMatrix(matrix);
        });
    }
}
