package com.example.bodeul.debug;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.content.res.Configuration;
import android.view.ContextThemeWrapper;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;

import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.example.bodeul.R;

import org.junit.Test;
import org.junit.runner.RunWith;

/** 완료 카드의 두 동작이 큰 글자에서도 잘리지 않고 같은 높이를 유지하는지 검사한다. */
@RunWith(AndroidJUnit4.class)
public class ClientBookingHistoryLargeTextTest {
    @Test
    public void completedActions_expandTogetherWithoutClippingAtLargeFont() {
        try (ActivityScenario<FigmaScreenPreviewSelectorActivity> scenario =
                     ActivityScenario.launch(FigmaScreenPreviewSelectorActivity.class)) {
            scenario.onActivity(activity -> {
                for (float scale : new float[]{1f, 1.3f, 2f}) {
                    Context context = themed(activity, scale);
                    View card = LayoutInflater.from(context).inflate(
                            R.layout.item_client_booking_history,
                            null,
                            false
                    );
                    card.findViewById(R.id.layoutBookingRequestActions)
                            .setVisibility(View.VISIBLE);
                    TextView review = card.findViewById(R.id.buttonBookingRequestReview);
                    TextView report = card.findViewById(R.id.buttonBookingRequestReport);
                    review.setVisibility(View.VISIBLE);
                    report.setVisibility(View.VISIBLE);

                    // 360dp 화면에서 이용내역 콘텐츠의 좌우 15dp 여백을 제외한 실제 카드 폭이다.
                    measure(card, context, 330);

                    assertTextFits(review);
                    assertTextFits(report);
                    assertTrue(review.getHeight() >= dp(context, 48));
                    assertEquals(review.getHeight(), report.getHeight());
                }
            });
        }
    }

    private static Context themed(Context context, float scale) {
        Configuration configuration = new Configuration(
                context.getResources().getConfiguration()
        );
        configuration.fontScale = scale;
        return new ContextThemeWrapper(
                context.createConfigurationContext(configuration),
                R.style.Theme_Bodeul_FinalHome
        );
    }

    private static void measure(View view, Context context, int widthDp) {
        view.measure(
                View.MeasureSpec.makeMeasureSpec(dp(context, widthDp), View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        );
        view.layout(0, 0, view.getMeasuredWidth(), view.getMeasuredHeight());
    }

    private static void assertTextFits(TextView text) {
        assertNotNull(text.getLayout());
        assertTrue(
                "세로 잘림: " + text.getText(),
                text.getLayout().getHeight()
                        <= text.getHeight()
                        - text.getCompoundPaddingTop()
                        - text.getCompoundPaddingBottom()
        );
        for (int line = 0; line < text.getLineCount(); line++) {
            assertEquals("말줄임: " + text.getText(), 0, text.getLayout().getEllipsisCount(line));
        }
    }

    private static int dp(Context context, int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }
}
