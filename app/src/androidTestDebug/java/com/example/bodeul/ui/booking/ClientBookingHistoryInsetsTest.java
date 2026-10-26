package com.example.bodeul.ui.booking;

import static org.junit.Assert.assertEquals;

import android.view.ViewGroup;
import android.widget.FrameLayout;

import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.example.bodeul.debug.FigmaScreenPreviewSelectorActivity;
import com.google.android.material.bottomnavigation.BottomNavigationView;

import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class ClientBookingHistoryInsetsTest {
    @Test
    public void apply_keepsNavigationContentAboveSystemBar_withoutAccumulatingInsets() {
        try (ActivityScenario<FigmaScreenPreviewSelectorActivity> scenario =
                     ActivityScenario.launch(FigmaScreenPreviewSelectorActivity.class)) {
            scenario.onActivity(activity -> {
                FrameLayout content = new FrameLayout(activity);
                BottomNavigationView navigation = new BottomNavigationView(activity);
                int contentLeft = 21;
                int contentTop = 22;
                int contentRight = 23;
                int contentBottom = 112;
                int initialHeight = 84;
                int initialLeft = 4;
                int initialTop = 5;
                int initialRight = 6;
                int initialBottom = 7;
                content.setPadding(contentLeft, contentTop, contentRight, contentBottom);
                content.setLayoutParams(new FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                ));
                navigation.setPadding(initialLeft, initialTop, initialRight, initialBottom);
                navigation.setLayoutParams(new FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        initialHeight
                ));
                activity.addContentView(content, content.getLayoutParams());
                activity.addContentView(navigation, navigation.getLayoutParams());

                ClientBookingHistoryInsets.apply(content, navigation);
                WindowInsetsCompat insets = new WindowInsetsCompat.Builder()
                        .setInsets(
                                WindowInsetsCompat.Type.systemBars(),
                                Insets.of(11, 19, 13, 29)
                        )
                        .build();

                ViewCompat.dispatchApplyWindowInsets(content, insets);
                ViewCompat.dispatchApplyWindowInsets(content, insets);
                ViewCompat.dispatchApplyWindowInsets(navigation, insets);
                ViewCompat.dispatchApplyWindowInsets(navigation, insets);

                assertEquals(contentLeft + 11, content.getPaddingLeft());
                assertEquals(contentTop + 19, content.getPaddingTop());
                assertEquals(contentRight + 13, content.getPaddingRight());
                assertEquals(contentBottom + 29, content.getPaddingBottom());
                assertEquals(initialLeft + 11, navigation.getPaddingLeft());
                assertEquals(initialTop, navigation.getPaddingTop());
                assertEquals(initialRight + 13, navigation.getPaddingRight());
                assertEquals(initialBottom + 29, navigation.getPaddingBottom());
                assertEquals(initialHeight + 29, navigation.getLayoutParams().height);
            });
        }
    }
}
