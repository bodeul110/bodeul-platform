package com.example.bodeul.ui.manager;

import android.content.Context;
import android.content.Intent;
import com.example.bodeul.data.ServiceLocator;
import com.example.bodeul.ui.support.SupportScreenActivity;
import com.example.bodeul.ui.support.SupportSource;

/** 매니저 문의의 저장 경로를 유지하는 고객센터 진입점이다. */
public class ManagerSupportActivity extends SupportScreenActivity {
    public static Intent createIntent(Context context) { return new Intent(context, ManagerSupportActivity.class); }
    @Override protected SupportSource provideSupportSource() { return new SupportSource(ServiceLocator.provideManagerRepository(this)); }
}
