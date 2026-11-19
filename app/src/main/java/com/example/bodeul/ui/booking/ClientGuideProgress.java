package com.example.bodeul.ui.booking;

import com.example.bodeul.domain.model.GuideStep;
import com.example.bodeul.domain.model.SessionStatus;
import com.example.bodeul.ui.manager.GuideDisplayGroups;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 환자에게 공개할 단계만 묶는다. 매니저 일지와 단계 입력값은 투영하지 않는다. */
public final class ClientGuideProgress {
    public enum State { DONE, NOW, NEXT, STOPPED, UNKNOWN }
    public static final class Row {
        public final int group;
        public final String title;
        public final State state;
        Row(int group, String title, State state) { this.group = group; this.title = title; this.state = state; }
    }
    private ClientGuideProgress() {}
    public static List<Row> rows(List<GuideStep> guide, String currentCode, int order, SessionStatus status) {
        List<GuideStep> steps = new ArrayList<>();
        if (guide != null) for (GuideStep s : guide) if (s != null) steps.add(s);
        steps.sort(Comparator.comparingInt(GuideStep::getOrder));
        Integer activeOrder = null;
        String code = currentCode == null ? "" : currentCode.trim();
        for (GuideStep s : steps) {
            if (code.isEmpty() ? s.getOrder() == order && order > 0 : code.equals(s.getCode())) {
                activeOrder = s.getOrder();
                break;
            }
        }
        Map<String, Row> result = new LinkedHashMap<>();
        for (GuideStep s : steps) {
            int group = GuideDisplayGroups.groupOf(s.getCode());
            if (group == 10) continue;
            State state = status == SessionStatus.CANCELED ? State.STOPPED : status == SessionStatus.COMPLETED ? State.DONE
                    : status == SessionStatus.READY ? State.NEXT : activeOrder == null || status == null ? State.UNKNOWN
                    : s.getOrder() < activeOrder ? State.DONE : s.getOrder() == activeOrder && status != SessionStatus.CARE_ENDED
                    ? State.NOW : status == SessionStatus.CARE_ENDED ? State.UNKNOWN : State.NEXT;
            String key = group == 0 ? "step:" + s.getOrder() : "group:" + group;
            Row previous = result.get(key);
            if (previous == null || (previous.state != State.NOW && state != State.DONE))
                result.put(key,new Row(group,s.getTitle(),state));
        }
        return new ArrayList<>(result.values());
    }
}
