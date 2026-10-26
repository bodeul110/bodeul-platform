package com.example.bodeul.debug;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.view.ContextThemeWrapper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.core.graphics.ColorUtils;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.example.bodeul.R;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

/** 실제 XML을 사용하되 합성 메모만 입력하며 기기 설정·서버 데이터는 바꾸지 않는다. */
@RunWith(AndroidJUnit4.class)
public class MemoReadabilityTest {
    private static final int[][] INPUTS = {
            {R.layout.dialog_manager_quick_note, R.id.inputManagerQuickNote},
            {R.layout.include_manager_guide_notes_card, R.id.inputGuidePhotoNote},
            {R.layout.include_manager_guide_notes_card, R.id.inputMedicationNote},
            {R.layout.include_manager_guide_notes_card, R.id.inputPharmacySummary},
            {R.layout.include_manager_guide_location_card, R.id.inputGuideLocationSummary},
            {R.layout.include_manager_guide_location_card, R.id.inputGuardianUpdate},
            {R.layout.include_manager_guide_consultation_summary, R.id.inputGuideSummaryNote},
            {R.layout.include_manager_guide_medication, R.id.inputGuideMedicationPharmacyNote},
            {R.layout.include_manager_guide_medication, R.id.inputGuideMedicationGuidanceNote},
            {R.layout.include_booking_form_visit, R.id.inputBookingSpecialNotes},
            {R.layout.include_manager_guide_report_card, R.id.inputReportSummary},
            {R.layout.include_manager_guide_report_card, R.id.inputReportTreatment},
            {R.layout.include_manager_guide_report_card, R.id.inputReportMedicationComparisonNote},
            {R.layout.include_manager_guide_report_card, R.id.inputReportMedicationChangeSummary}
    };

    @Test
    public void memoTextAndHints_remainReadableInDayNightAndDisabledStates() {
        withContext(context -> {
            for (int mode : new int[]{Configuration.UI_MODE_NIGHT_NO, Configuration.UI_MODE_NIGHT_YES}) {
                Context themed = themed(context, mode, 1f);
                for (int[] field : INPUTS) {
                    View screen = inflate(themed, field[0]);
                    TextInputEditText input = screen.findViewById(field[1]);
                    TextInputLayout layout = inputLayout(input);
                    layout.setHintAnimationEnabled(false);
                    input.setText("보호자 전달 사항\n확인한 내용을 직접 기록했습니다.");
                    for (boolean enabled : new boolean[]{true, false, true}) {
                        layout.setEnabled(enabled);
                        measure(screen, themed, 320);
                        int background = ColorUtils.compositeColors(layout.getBoxBackgroundColor(), Color.WHITE);
                        assertContrast("메모 본문 " + field[1] + " 야간=" + mode + " 활성=" + enabled,
                                input.getCurrentTextColor(), background);
                        input.setText("");
                        assertNotNull(layout.getDefaultHintTextColor());
                        assertContrast("빈 입력 안내 " + field[1],
                                layout.getDefaultHintTextColor().getColorForState(
                                        layout.getDrawableState(), layout.getDefaultHintTextColor().getDefaultColor()),
                                background);
                        input.setText("보호자 전달 사항\n확인한 내용을 직접 기록했습니다.");
                        if (enabled) {
                            input.requestFocus();
                            assertContrast("포커스 안내 " + field[1],
                                    layout.getHintTextColor().getColorForState(
                                            input.getDrawableState(), layout.getHintTextColor().getDefaultColor()),
                                    background);
                            input.clearFocus();
                        }
                    }
                    input.setKeyListener(null);
                    assertContrast("읽기 전용 메모 " + field[1], input.getCurrentTextColor(),
                            ColorUtils.compositeColors(layout.getBoxBackgroundColor(), Color.WHITE));
                    if (field[1] == R.id.inputManagerQuickNote && mode == Configuration.UI_MODE_NIGHT_YES) {
                        measure(screen, themed, 320);
                        capture(screen, "memo-night");
                    }
                }
            }
        });
    }

    @Test
    public void completionMemos_keepReadableTextBeforeCareEnds() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        try (ActivityScenario<ManagerGuidePreviewActivity> scenario = ActivityScenario.launch(
                ManagerGuidePreviewActivity.createIntent(context, "CARE_COMPLETION"))) {
            scenario.onActivity(activity -> {
                ViewGroup memos = activity.findViewById(R.id.guideCompletionMemoContainer);
                assertTrue("종료 전 합성 메모가 표시되어야 합니다.", memos.getChildCount() > 0);
                for (int i = 0; i < memos.getChildCount(); i++) {
                    TextView body = memos.getChildAt(i).findViewById(R.id.textGuideMemoItemBody);
                    assertContrast("종료 전 읽기 전용 메모", body.getCurrentTextColor(),
                            activity.getColor(R.color.bodeul_surface_alt));
                }
            });
        }
    }

    @Test
    public void longMemos_keepLinesAndContentAtLargeFont() {
        withContext(context -> {
            for (float scale : new float[]{1f, 2f}) {
                Context themed = themed(context, Configuration.UI_MODE_NIGHT_NO, scale);
                for (int[] field : INPUTS) {
                    View screen = inflate(themed, field[0]);
                    TextInputEditText input = screen.findViewById(field[1]);
                    String text = "합성 검증 메모입니다. 긴 내용도 빠짐없이 표시합니다.\n"
                            + "다음 줄의 안내 사항을 확인합니다. ".repeat(12);
                    input.setText(text);
                    measure(screen, themed, 320);
                    assertEquals(text, input.getText().toString());
                    assertTextFits(input);
                }
            }
        });
    }

    @Test
    public void medicationLabels_andManualOnlyNotice_areReadableAtLargeFont() {
        withContext(context -> {
            for (float scale : new float[]{1f, 2f}) {
                Context themed = themed(context, Configuration.UI_MODE_NIGHT_NO, scale);
                View screen = LayoutInflater.from(themed).inflate(R.layout.include_manager_guide_medication, null, false);
                TextView progress = screen.findViewById(R.id.textGuideMedicationProgress);
                progress.setText(themed.getString(R.string.guide_medication_progress_format, 0));
                measure(screen, themed, 320);
                checkMedicationText(screen);
                assertTrue(hasText(screen, themed.getString(R.string.guide_medication_prescription_disclaimer)));
                assertTrue(hasText(screen, themed.getString(R.string.guide_medication_existing_empty)));
                TextView existing = screen.findViewById(R.id.textGuideMedicationExistingSummary);
                existing.setText("환자가 직접 남긴 검증용 기록\n복약 관련 문의는 약사에게 확인합니다.");
                TextView prescription = screen.findViewById(R.id.textGuideMedicationPrescriptionStatus);
                prescription.setText(themed.getString(R.string.guide_medication_prescription_count, 1));
                measure(screen, themed, 320);
                checkMedicationText(screen);
                if (scale == 1f) {
                    capture(screen, "medication");
                }
            }
        });
    }

    private static void checkMedicationText(View view) {
        if (view.getVisibility() != View.VISIBLE) {
            return;
        }
        if (view instanceof TextView && !(view instanceof android.widget.Button)
                && !(view instanceof android.widget.EditText)) {
            TextView text = (TextView) view;
            if (text.getText().length() > 0) {
                assertTextFits(text);
                assertContrast(text.getText().toString(), text.getCurrentTextColor(),
                        view.getContext().getColor(R.color.guide_medication_neutral_card));
            }
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                checkMedicationText(group.getChildAt(i));
            }
        }
    }

    private static void assertContrast(String name, int foreground, int background) {
        double ratio = ColorUtils.calculateContrast(ColorUtils.compositeColors(foreground, background), background);
        assertTrue(name + " 대비 부족: " + ratio, ratio >= 4.5);
    }

    private static void assertTextFits(TextView text) {
        assertNotNull(text.getLayout());
        assertTrue("세로 잘림: " + text.getText(), text.getLayout().getHeight()
                <= text.getHeight() - text.getCompoundPaddingTop() - text.getCompoundPaddingBottom());
        for (int line = 0; line < text.getLineCount(); line++) {
            assertEquals("말줄임: " + text.getText(), 0, text.getLayout().getEllipsisCount(line));
            // 줄 바꿈 직전 공백은 보이는 글리프가 아니므로 폭 계산에서 제외한다.
            assertTrue("가로 잘림: " + text.getText(), text.getLayout().getLineMax(line)
                    <= text.getWidth() - text.getCompoundPaddingLeft() - text.getCompoundPaddingRight() + 1);
        }
    }

    private static boolean hasText(View view, String value) {
        if (view instanceof TextView && value.contentEquals(((TextView) view).getText())) {
            return true;
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                if (hasText(group.getChildAt(i), value)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static void capture(View view, String name) {
        if (!"true".equals(InstrumentationRegistry.getArguments().getString("readabilityEvidence"))) {
            return;
        }
        // 서버·기기 전체 화면 대신 합성 내용으로 만든 검사 대상 View만 보관한다.
        Bitmap bitmap = Bitmap.createBitmap(view.getWidth(), view.getHeight(), Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        canvas.drawColor(Color.WHITE);
        view.draw(canvas);
        File file = new File(view.getContext().getExternalCacheDir(), "issue-447-" + name + ".png");
        try (FileOutputStream output = new FileOutputStream(file)) {
            assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output));
        } catch (IOException error) {
            throw new AssertionError("합성 화면 검증 이미지 저장 실패", error);
        } finally {
            bitmap.recycle();
        }
    }

    private static TextInputLayout inputLayout(View input) {
        for (android.view.ViewParent parent = input.getParent(); parent != null; parent = parent.getParent()) {
            if (parent instanceof TextInputLayout) {
                return (TextInputLayout) parent;
            }
        }
        throw new AssertionError("메모 입력 컨테이너를 찾지 못했습니다.");
    }

    private static Context themed(Context context, int night, float scale) {
        Configuration configuration = new Configuration(context.getResources().getConfiguration());
        configuration.uiMode = (configuration.uiMode & ~Configuration.UI_MODE_NIGHT_MASK) | night;
        configuration.fontScale = scale;
        return new ContextThemeWrapper(context.createConfigurationContext(configuration), R.style.Theme_Bodeul);
    }

    private static void measure(View view, Context context, int widthDp) {
        view.measure(View.MeasureSpec.makeMeasureSpec(
                        Math.round(widthDp * context.getResources().getDisplayMetrics().density), View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
        view.layout(0, 0, view.getMeasuredWidth(), view.getMeasuredHeight());
    }

    private static View inflate(Context context, int layout) {
        LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        LayoutInflater.from(context).inflate(layout, root, true);
        return root;
    }

    private static void withContext(java.util.function.Consumer<Context> assertion) {
        try (ActivityScenario<FigmaScreenPreviewSelectorActivity> scenario =
                     ActivityScenario.launch(FigmaScreenPreviewSelectorActivity.class)) {
            scenario.onActivity(assertion::accept);
        }
    }
}
