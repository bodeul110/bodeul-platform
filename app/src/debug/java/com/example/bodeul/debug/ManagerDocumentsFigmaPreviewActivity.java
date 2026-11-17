package com.example.bodeul.debug;

import android.content.Intent;
import android.net.Uri;
import com.example.bodeul.data.*;
import com.example.bodeul.ui.manager.ManagerDocumentRegistrationActivity;

/** 실제 화면에 합성 저장소를 주입하며 외부 파일 열기·화면 이동만 계측한다. */
public final class ManagerDocumentsFigmaPreviewActivity extends ManagerDocumentRegistrationActivity {
    static AuthRepository auth;
    static ManagerRepository repository;
    static ManagerDocumentStorageUploader uploader;
    static ManagerDocumentPreviewResolver resolver;
    static String[] pickerMimeTypes;
    static int pickerCount, openedPreviews;
    static Intent lastNavigation;
    @Override protected AuthRepository provideAuthRepository() { return auth; }
    @Override protected ManagerRepository provideManagerRepository() { return repository; }
    @Override protected ManagerDocumentStorageUploader provideStorageUploader() { return uploader; }
    @Override protected ManagerDocumentPreviewResolver providePreviewResolver() { return resolver; }
    @Override protected void launchDocumentPicker(String[] mimeTypes) { pickerMimeTypes = mimeTypes; pickerCount++; }
    @Override protected boolean openDocumentPreview(Uri uri, String contentType) { openedPreviews++; return true; }
    public void deliverFile(Uri uri) { handleDocumentPicked(uri); }
    @Override public void startActivity(Intent intent) { lastNavigation = intent; }
}
