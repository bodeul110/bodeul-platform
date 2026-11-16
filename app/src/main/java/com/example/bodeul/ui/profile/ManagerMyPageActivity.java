package com.example.bodeul.ui.profile;

/** 매니저 진입점에서도 조회한 서버 역할을 검사하며 일반 화면과 표시 코드를 공유한다. */
public class ManagerMyPageActivity extends ClientProfileActivity {
    @Override protected boolean isManagerPage() { return true; }
}
