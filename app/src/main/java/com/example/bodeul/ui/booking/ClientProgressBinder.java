package com.example.bodeul.ui.booking;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.core.content.ContextCompat;
import com.example.bodeul.R;
import com.example.bodeul.domain.model.AppointmentRequest;
import com.example.bodeul.domain.model.AppointmentRequestDetail;
import com.example.bodeul.domain.model.AppointmentStatus;
import com.example.bodeul.domain.model.CompanionSession;
import com.example.bodeul.domain.model.SessionStatus;
import com.example.bodeul.ui.common.DetailViews;
import com.example.bodeul.ui.common.GuideTimelineMarker;
import com.example.bodeul.ui.manager.GuideDisplayGroups;
import com.google.android.material.card.MaterialCardView;
import java.util.List;

final class ClientProgressBinder {
    static boolean hasSession(AppointmentRequestDetail d) {
        return d != null && d.getAppointmentRequest() != null && d.getSession() != null
                && d.getSession().getAppointmentRequestId() != null
                && d.getSession().getAppointmentRequestId().equals(d.getAppointmentRequest().getId());
    }
    static void render(LinearLayout content, AppointmentRequestDetail d, Runnable openReport) {
        Context c = content.getContext();
        content.removeAllViews();
        AppointmentRequest r = d.getAppointmentRequest();
        CompanionSession session = d.getSession();
        List<ClientGuideProgress.Row> rows = ClientGuideProgress.rows(d.getHospitalGuide() == null ? null :
                d.getHospitalGuide().getSteps(), session.getCurrentStepCode(),session.getCurrentStepOrder(),
                r.getStatus() == AppointmentStatus.CANCELED ? SessionStatus.CANCELED
                        : session.getStatus() != SessionStatus.COMPLETED && session.getCareEndedAtMillis() > 0
                        ? SessionStatus.CARE_ENDED : session.getStatus());
        if (rows.isEmpty()) DetailViews.text(content,c.getString(R.string.batch_progress_missing),R.style.FinalProfileBody);
        for (int i = 0; i < rows.size(); i++) {
            ClientGuideProgress.Row row = rows.get(i);
            View band = LayoutInflater.from(c).inflate(R.layout.item_final_guide_timeline,content,false);
            boolean active = row.state == ClientGuideProgress.State.NOW;
            GuideTimelineMarker.bind(band,row.group == 0 ? i+1 : row.group,active,
                    row.state == ClientGuideProgress.State.DONE,i == rows.size()-1);
            MaterialCardView card = band.findViewById(R.id.finalGuideTimelineCard);
            card.setStrokeColor(ContextCompat.getColor(c,active ? R.color.final_brand_primary : R.color.final_guide_outline));
            LinearLayout header = band.findViewById(R.id.finalGuideTimelineHeader);
            header.setBackgroundColor(ContextCompat.getColor(c,active ? R.color.final_guide_light_blue : R.color.final_input_surface));
            ((View)band.findViewById(R.id.finalGuideTimelineNext).getParent()).setVisibility(View.GONE);
            TextView title = band.findViewById(R.id.finalGuideTimelineTitle);
            title.setText(row.group == 0 ? row.title : c.getString(GuideDisplayGroups.title(row.group)));
            int label = switch (row.state) {
                case DONE -> R.string.batch_progress_done;
                case NOW -> R.string.batch_progress_now;
                case NEXT -> R.string.batch_progress_next;
                case STOPPED -> R.string.batch_progress_stopped;
                case UNKNOWN -> R.string.batch_progress_unknown;
            };
            TextView state = new androidx.appcompat.widget.AppCompatTextView(c);
            state.setTextAppearance(c,R.style.FinalSettingsValue);
            state.setTextColor(ContextCompat.getColor(c,active ? R.color.final_text_accent_accessible : R.color.final_text_secondary));
            state.setText(label);
            header.addView(state,-2,-2);
            LinearLayout body = band.findViewById(R.id.finalGuideTimelineBody);
            body.removeAllViews();
            if (row.group == 1) optional(body,r.getMeetingPlace());
            if (row.group == 2) { optional(body,r.getHospitalName()); optional(body,r.getDepartmentName()); }
            if (body.getChildCount() == 0) body.setVisibility(View.GONE);
            content.addView(band);
        }
        if (r.getStatus() == AppointmentStatus.COMPLETED && d.getSessionReport() != null
                && session.getId() != null && session.getId().equals(d.getSessionReport().getSessionId()))
            DetailViews.action(content,R.string.batch_report,openReport);
    }
    private static void optional(LinearLayout card, String value) {
        if (value != null && !value.trim().isEmpty()) DetailViews.text(card,value,R.style.FinalProfileBody);
    }
}
