package com.example.bodeul.debug;

import static org.junit.Assert.assertEquals;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.os.SystemClock;
import android.view.View;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.example.bodeul.R;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.concurrent.atomic.AtomicInteger;

@RunWith(AndroidJUnit4.class)
public class ManagerGuideLegacyPreviewActivityTest {
    @Test
    public void eachLegacyStep_opensMappedLatestContent() {
        String[] stepCodes = {
                "LEGACY_CORE_PATIENT_CONTACT",
                "LEGACY_CORE_RECEPTION_PREPARATION",
                "LEGACY_CORE_RECEPTION",
                "LEGACY_CORE_CONSULTATION",
                "LEGACY_CORE_PAYMENT",
                "LEGACY_CORE_PHARMACY",
                "LEGACY_CORE_RETURN_AND_CLOSE"
        };
        int[] expectedContentIds = {
                R.id.managerGuideMeetingOverview,
                R.id.cardGuideMap,
                R.id.managerGuideReceptionContent,
                R.id.managerGuideConsultationContent,
                R.id.managerGuidePaymentContent,
                R.id.managerGuideMedicationContent,
                R.id.managerGuideJournalContent
        };
        Instrumentation instrumentation =
                InstrumentationRegistry.getInstrumentation();
        Context context = instrumentation.getTargetContext();

        for (int index = 0; index < stepCodes.length; index++) {
            Intent intent = ManagerGuidePreviewActivity.createLegacyIntent(
                    context, stepCodes[index]);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            Activity activity = instrumentation.startActivitySync(intent);
            instrumentation.waitForIdleSync();

            assertEquals(
                    "단계 화면이 보이지 않습니다: " + stepCodes[index],
                    View.VISIBLE,
                    waitForVisibility(
                            instrumentation,
                            activity,
                            expectedContentIds[index]));

            instrumentation.runOnMainSync(activity::finish);
            instrumentation.waitForIdleSync();
        }
    }

    @Test
    public void legacyConsultationAdvance_showsPaymentContentInSameActivity() {
        Instrumentation instrumentation =
                InstrumentationRegistry.getInstrumentation();
        Context context = instrumentation.getTargetContext();
        Intent intent = ManagerGuidePreviewActivity.createLegacyIntent(
                context, "LEGACY_CORE_CONSULTATION");
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        Activity activity = instrumentation.startActivitySync(intent);
        instrumentation.waitForIdleSync();

        assertEquals(
                View.VISIBLE,
                waitForVisibility(
                        instrumentation,
                        activity,
                        R.id.managerGuideConsultationContent));

        instrumentation.runOnMainSync(() ->
                activity.findViewById(R.id.buttonAdvanceGuide).performClick());
        instrumentation.waitForIdleSync();

        assertEquals(
                View.VISIBLE,
                waitForVisibility(
                        instrumentation,
                        activity,
                        R.id.managerGuidePaymentContent));
        assertEquals(
                View.GONE,
                readVisibility(
                        instrumentation,
                        activity,
                        R.id.managerGuideConsultationContent));

        instrumentation.runOnMainSync(activity::finish);
        instrumentation.waitForIdleSync();
    }

    private int waitForVisibility(
            Instrumentation instrumentation,
            Activity activity,
            int viewId
    ) {
        int visibility = readVisibility(instrumentation, activity, viewId);
        for (int attempt = 0; attempt < 20 && visibility != View.VISIBLE; attempt++) {
            SystemClock.sleep(50L);
            instrumentation.waitForIdleSync();
            visibility = readVisibility(instrumentation, activity, viewId);
        }
        return visibility;
    }

    private int readVisibility(
            Instrumentation instrumentation,
            Activity activity,
            int viewId
    ) {
        AtomicInteger visibility = new AtomicInteger(-1);
        instrumentation.runOnMainSync(() -> {
            View view = activity.findViewById(viewId);
            visibility.set(view == null ? -1 : view.getVisibility());
        });
        return visibility.get();
    }
}
