package com.example.bodeul.data;

import android.content.Intent;

/** 기기 전체에 적용되는 Android 알림 설정을 읽고 시스템 설정 목적지를 제공한다. */
public interface NotificationSettingsRepository {
    boolean isChatEnabled();
    Intent chatSettingsIntent();
    Intent appSettingsIntent();
}
