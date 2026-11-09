package com.example.bodeul.ui.common;

import android.content.Context;
import android.graphics.Matrix;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import com.example.bodeul.R;
import com.example.bodeul.ui.home.ClientHomeNotice;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import java.util.List;

/** 환자와 매니저 완성본에 같은 원본 자산·크롭·안내 대화상자를 적용한다. */
public final class FinalHomeNoticeBinder {
    private FinalHomeNoticeBinder() { }

    public static void bind(Context context, LinearLayout container, List<ClientHomeNotice> notices) {
        container.removeAllViews();
        for (ClientHomeNotice notice : notices) {
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

    private static void applyOriginalImageCrop(ImageView image, int imageId) {
        if (imageId != R.drawable.bodeul_final_home_article_2
                && imageId != R.drawable.bodeul_final_home_article_4) return;
        image.setScaleType(ImageView.ScaleType.MATRIX);
        // 자산을 다시 자르지 않고 원본 Figma의 위치와 배율로 표시한다.
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
