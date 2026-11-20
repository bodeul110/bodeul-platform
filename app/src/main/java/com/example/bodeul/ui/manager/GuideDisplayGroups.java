package com.example.bodeul.ui.manager;

import com.example.bodeul.R;

/** 역할별 화면이 같은 서버 단계 코드를 같은 표시 이름으로 읽게 한다. 단계 변경 권한과는 무관하다. */
public final class GuideDisplayGroups {
    private static final int[] TITLES = {
            R.string.final_guide_title, R.string.final_guide_group_1, R.string.final_guide_group_2,
            R.string.final_guide_group_3, R.string.final_guide_group_4, R.string.final_guide_group_5,
            R.string.final_guide_group_6, R.string.final_guide_group_7, R.string.final_guide_group_8,
            R.string.final_guide_group_9, R.string.final_guide_journal
    };
    private GuideDisplayGroups() {}
    public static int title(int group) { return TITLES[group]; }
    public static int groupOf(String code) { return ManagerGuideTimelinePolicy.groupOf(code); }
}
