package com.example.bodeul.ui.navigation;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.view.ContextThemeWrapper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.ScrollView;

import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.example.bodeul.R;
import com.example.bodeul.debug.FigmaScreenPreviewSelectorActivity;
import com.google.android.material.bottomnavigation.BottomNavigationView;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** 실제 메뉴 레이아웃에 시스템 영역을 주입하며 기기의 전역 표시 설정은 변경하지 않는다. */
@RunWith(AndroidJUnit4.class)
public class ClientBottomNavigationInsetsTest {
    private static final int[] SCREEN_LAYOUTS = {
            R.layout.activity_client_home_figma,
            R.layout.activity_booking,
            R.layout.activity_client_booking_history,
            R.layout.activity_companion_chat,
            R.layout.activity_client_profile,
            R.layout.activity_guardian_report
    };

    @Test
    public void tabs_preserveContentHeightForGestureAndThreeButtonInsets() {
        withActivity(context -> {
            for (int layout : SCREEN_LAYOUTS) {
                BottomNavigationView navigation = inflateNavigation(context, layout, 1f);
                int height = navigation.getLayoutParams().height;
                int padding = navigation.getPaddingBottom();
                bind(navigation);
                for (int bottomDp : new int[]{24, 48, 24, 0}) {
                    int bottom = dp(context, bottomDp);
                    dispatch(navigation, 0, 0, bottom, 0);
                    assertEquals("화면 " + layout, height + bottom,
                            navigation.getLayoutParams().height);
                    assertEquals(padding + bottom, navigation.getPaddingBottom());
                    measure(navigation, dp(context, 360));
                    assertItemsInsideSafeArea(navigation);
                }
            }
        });
    }

    @Test
    public void rebindAndKeyboardInsets_doNotAccumulateOrExpandNavigation() {
        withActivity(context -> {
            BottomNavigationView navigation = inflateNavigation(
                    context, R.layout.activity_companion_chat, 1f);
            int height = navigation.getLayoutParams().height;
            int bottom = dp(context, 48);
            int left = dp(context, 30);
            int right = dp(context, 12);
            int originalLeft = navigation.getPaddingLeft();
            int originalRight = navigation.getPaddingRight();
            bind(navigation);
            dispatch(navigation, left, right, bottom, 0);
            bind(navigation);
            dispatch(navigation, left, right, bottom, dp(context, 280));
            dispatch(navigation, left, right, bottom, dp(context, 280));
            assertEquals(height + bottom, navigation.getLayoutParams().height);
            assertEquals(originalLeft + left, navigation.getPaddingLeft());
            assertEquals(originalRight + right, navigation.getPaddingRight());
            navigation.setVisibility(View.GONE);
            navigation.setVisibility(View.VISIBLE);
            dispatch(navigation, 0, 0, 0, 0);
            assertEquals(height, navigation.getLayoutParams().height);
            assertEquals(originalLeft, navigation.getPaddingLeft());
            assertEquals(originalRight, navigation.getPaddingRight());
        });
    }

    @Test
    public void compactScreenAndLargeFont_keepLabelsIconsAndTouchTargetsVisible() {
        withActivity(context -> {
            for (float fontScale : new float[]{1f, 2f}) {
                BottomNavigationView navigation = inflateNavigation(
                        context, R.layout.activity_client_profile, fontScale);
                bind(navigation);
                dispatch(navigation, 0, 0, dp(context, 48), 0);
                for (int width : new int[]{360, 320}) {
                    for (int index = 0; index < navigation.getMenu().size(); index++) {
                        ClientBottomNavigationBinder.bind(navigation,
                                ClientBottomNavigationTab.values()[index], tab -> {});
                        measure(navigation, dp(context, width));
                        if (fontScale == 1f && width == 360 && index == 0) {
                            captureNavigationEvidence(navigation);
                        }
                        assertItemsInsideSafeArea(navigation);
                    }
                }
            }
        });
    }

    @Test
    public void insetChanges_preserveTabSelectionAndCallbacks() {
        withActivity(context -> {
            BottomNavigationView navigation = inflateNavigation(
                    context, R.layout.activity_client_profile, 1f);
            List<ClientBottomNavigationTab> selected = new ArrayList<>();
            ClientBottomNavigationBinder.bind(navigation, ClientBottomNavigationTab.PROFILE, selected::add);
            dispatch(navigation, 0, 0, dp(context, 48), 0);
            assertEquals(R.id.clientNavProfile, navigation.getSelectedItemId());
            navigation.findViewById(R.id.clientNavProfile).performClick();
            assertTrue("현재 탭 재선택은 화면을 중복 생성하지 않는다", selected.isEmpty());
            navigation.findViewById(R.id.clientNavHome).performClick();
            navigation.findViewById(R.id.clientNavScheduleHistory).performClick();
            navigation.findViewById(R.id.clientNavCompanionRoom).performClick();
            dispatch(navigation, 0, 0, dp(context, 24), 0);
            assertEquals("이동 이벤트가 발생해도 원래 화면의 선택은 유지한다",
                    R.id.clientNavProfile, navigation.getSelectedItemId());
            assertEquals(Arrays.asList(ClientBottomNavigationTab.HOME,
                    ClientBottomNavigationTab.SCHEDULE_HISTORY,
                    ClientBottomNavigationTab.COMPANION_ROOM), selected);
        });
    }

    @Test
    public void wrapContentHeight_isNotConvertedToInvalidFixedHeight() {
        withActivity(context -> {
            BottomNavigationView navigation = inflateNavigation(
                    context, R.layout.activity_client_profile, 1f);
            navigation.getLayoutParams().height = ViewGroup.LayoutParams.WRAP_CONTENT;
            bind(navigation);
            dispatch(navigation, 0, 0, dp(context, 48), 0);
            assertEquals(ViewGroup.LayoutParams.WRAP_CONTENT, navigation.getLayoutParams().height);
        });
    }

    @Test
    public void scrollContent_preservesExistingSpacingAndFollowsKeyboardWithoutAccumulation() {
        withActivity(context -> {
            for (int layout : new int[]{R.layout.activity_companion_chat, R.layout.activity_client_profile}) {
                View screen = LayoutInflater.from(context).inflate(layout, null, false);
                View content = screen.findViewById(layout == R.layout.activity_companion_chat
                        ? R.id.scrollCompanionChat : R.id.scrollClientProfile);
                content.setPadding(4, 8, 12, 16);
                ClientBottomNavigationInsets.applyScrollableContent(content);
                WindowInsetsCompat bars = new WindowInsetsCompat.Builder()
                        .setInsets(WindowInsetsCompat.Type.systemBars(), Insets.of(20, 24, 28, 48))
                        .build();
                ViewCompat.dispatchApplyWindowInsets(content, bars);
                ClientBottomNavigationInsets.applyScrollableContent(content);
                ViewCompat.dispatchApplyWindowInsets(content, new WindowInsetsCompat.Builder(bars)
                        .setInsets(WindowInsetsCompat.Type.ime(), Insets.of(0, 0, 0, 280))
                        .build());
                assertEquals(24, content.getPaddingLeft());
                assertEquals(32, content.getPaddingTop());
                assertEquals(40, content.getPaddingRight());
                assertEquals(296, content.getPaddingBottom());
                ViewCompat.dispatchApplyWindowInsets(content, bars);
                assertEquals(64, content.getPaddingBottom());

                BottomNavigationView navigation = screen.findViewById(R.id.clientBottomNavigation);
                bind(navigation);
                ViewCompat.dispatchApplyWindowInsets(navigation, bars);
                screen.measure(View.MeasureSpec.makeMeasureSpec(dp(context, 360), View.MeasureSpec.EXACTLY),
                        View.MeasureSpec.makeMeasureSpec(dp(context, 640), View.MeasureSpec.EXACTLY));
                screen.layout(0, 0, screen.getMeasuredWidth(), screen.getMeasuredHeight());
                ScrollView scroll = (ScrollView) content;
                View body = scroll.getChildAt(0);
                scroll.scrollTo(0, body.getHeight());
                Rect bodyBounds = new Rect();
                body.getDrawingRect(bodyBounds);
                ((ViewGroup) screen).offsetDescendantRectToMyCoords(body, bodyBounds);
                assertTrue("스크롤 끝 내용이 하단 탭 위에 남는다",
                        bodyBounds.bottom - body.getPaddingBottom() <= navigation.getTop());
            }
        });
    }

    private static BottomNavigationView inflateNavigation(Context context, int layout, float fontScale) {
        Configuration configuration = new Configuration(context.getResources().getConfiguration());
        configuration.fontScale = fontScale;
        Context themed = new ContextThemeWrapper(context.createConfigurationContext(configuration),
                R.style.Theme_Bodeul);
        View screen = LayoutInflater.from(themed).inflate(layout, null, false);
        BottomNavigationView navigation = screen.findViewById(R.id.clientBottomNavigation);
        assertNotNull(navigation);
        return navigation;
    }

    private static void bind(BottomNavigationView navigation) {
        ClientBottomNavigationBinder.bind(navigation, ClientBottomNavigationTab.PROFILE, tab -> {});
    }

    private static void dispatch(View view, int left, int right, int bottom, int keyboard) {
        ViewCompat.dispatchApplyWindowInsets(view, new WindowInsetsCompat.Builder()
                .setInsets(WindowInsetsCompat.Type.navigationBars(), Insets.of(0, 0, 0, bottom))
                .setInsets(WindowInsetsCompat.Type.displayCutout(), Insets.of(left, 0, right, 0))
                .setInsets(WindowInsetsCompat.Type.ime(), Insets.of(0, 0, 0, keyboard))
                .build());
    }

    private static void measure(View navigation, int width) {
        navigation.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(navigation.getLayoutParams().height,
                        View.MeasureSpec.EXACTLY));
        navigation.layout(0, 0, navigation.getMeasuredWidth(), navigation.getMeasuredHeight());
    }

    private static void assertItemsInsideSafeArea(BottomNavigationView navigation) {
        for (int index = 0; index < navigation.getMenu().size(); index++) {
            View item = navigation.findViewById(navigation.getMenu().getItem(index).getItemId());
            assertTrue("탭 터치 높이는 48dp 이상", item.getHeight() >= dp(navigation.getContext(), 48));
            assertTrue("탭 터치 너비는 48dp 이상", item.getWidth() >= dp(navigation.getContext(), 48));
            assertInside(navigation, item);
            View icon = item.findViewById(com.google.android.material.R.id.navigation_bar_item_icon_view);
            assertInside(navigation, icon);
            int labelId = navigation.getMenu().getItem(index).isChecked()
                    ? com.google.android.material.R.id.navigation_bar_item_large_label_view
                    : com.google.android.material.R.id.navigation_bar_item_small_label_view;
            TextView label = item.findViewById(labelId);
            assertInside(navigation, label);
            assertNotNull(label.getLayout());
            for (int line = 0; line < label.getLineCount(); line++) {
                assertEquals("탭 이름 말줄임 없음", 0, label.getLayout().getEllipsisCount(line));
            }
            assertTrue("탭 이름 세로 잘림 없음", label.getLayout().getHeight()
                    <= label.getHeight() - label.getCompoundPaddingTop() - label.getCompoundPaddingBottom());
        }
    }

    private static void assertInside(ViewGroup navigation, View child) {
        Rect bounds = new Rect();
        child.getDrawingRect(bounds);
        navigation.offsetDescendantRectToMyCoords(child, bounds);
        assertTrue("상단 잘림: " + bounds, bounds.top >= navigation.getPaddingTop());
        assertTrue("하단 잘림: " + bounds,
                bounds.bottom <= navigation.getHeight() - navigation.getPaddingBottom());
        assertTrue("왼쪽 잘림: " + bounds, bounds.left >= navigation.getPaddingLeft());
        assertTrue("오른쪽 잘림: " + bounds,
                bounds.right <= navigation.getWidth() - navigation.getPaddingRight());
    }

    private static int dp(Context context, int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }

    private static void captureNavigationEvidence(View navigation) {
        String suffix = InstrumentationRegistry.getArguments().getString("navigationEvidence");
        if (!"before".equals(suffix) && !"after".equals(suffix)) {
            return;
        }
        // 개인정보가 없는 합성 메뉴만 그린다. 기기 전체 화면은 캡처하지 않는다.
        Bitmap bitmap = Bitmap.createBitmap(navigation.getWidth(), navigation.getHeight(),
                Bitmap.Config.ARGB_8888);
        navigation.draw(new Canvas(bitmap));
        File file = new File(navigation.getContext().getExternalCacheDir(),
                "navigation-insets-" + suffix + ".png");
        try (FileOutputStream output = new FileOutputStream(file)) {
            assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output));
        } catch (IOException exception) {
            throw new AssertionError("메뉴 검증 이미지 저장 실패", exception);
        } finally {
            bitmap.recycle();
        }
    }

    private static void withActivity(java.util.function.Consumer<Context> assertion) {
        try (ActivityScenario<FigmaScreenPreviewSelectorActivity> scenario =
                     ActivityScenario.launch(FigmaScreenPreviewSelectorActivity.class)) {
            scenario.onActivity(assertion::accept);
        }
    }
}
