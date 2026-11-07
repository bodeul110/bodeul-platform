package com.example.bodeul.data.firebase;

import android.content.Context;

import com.example.bodeul.data.GuardianBookingAccountSession;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

public final class FirebaseGuardianBookingAccountSession implements GuardianBookingAccountSession {
    private final FirebaseAuth auth;
    private FirebaseAuth.AuthStateListener listener;

    public FirebaseGuardianBookingAccountSession(Context context) {
        auth = FirebaseSupport.isConfigured(context) ? FirebaseAuth.getInstance() : null;
    }

    @Override public String currentAccountId() {
        FirebaseUser user = auth == null ? null : auth.getCurrentUser();
        return user == null ? null : user.getUid();
    }

    @Override public void observe(Runnable changed) {
        stopObserving();
        if (auth == null) { changed.run(); return; }
        listener = ignored -> changed.run();
        auth.addAuthStateListener(listener);
    }

    @Override public void stopObserving() {
        if (auth != null && listener != null) auth.removeAuthStateListener(listener);
        listener = null;
    }
}
