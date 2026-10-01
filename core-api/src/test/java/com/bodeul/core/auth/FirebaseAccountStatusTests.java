package com.bodeul.core.auth;

import com.google.firebase.auth.AuthErrorCode;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthException;
import com.google.firebase.auth.UserRecord;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class FirebaseAccountStatusTests {
    @Test
    void enabledAndDisabledUsersAreDistinguished() throws Exception {
        FirebaseAuth auth = mock(FirebaseAuth.class);
        UserRecord user = mock(UserRecord.class);
        when(auth.getUser("test-uid")).thenReturn(user);
        var status = FirebaseAdminConfiguration.accountStatus(auth);
        assertThat(status.isActive("test-uid")).isTrue();
        when(user.isDisabled()).thenReturn(true);
        assertThat(status.isActive("test-uid")).isFalse();
    }

    @Test
    void deletedUserIsInactiveButRemoteFailureIsUnavailable() throws Exception {
        FirebaseAuth auth = mock(FirebaseAuth.class);
        FirebaseAuthException error = mock(FirebaseAuthException.class);
        when(auth.getUser("test-uid")).thenThrow(error);
        when(error.getAuthErrorCode()).thenReturn(AuthErrorCode.USER_NOT_FOUND);
        var status = FirebaseAdminConfiguration.accountStatus(auth);
        assertThat(status.isActive("test-uid")).isFalse();
        when(error.getAuthErrorCode()).thenReturn(null);
        assertThatThrownBy(() -> status.isActive("test-uid"))
                .isInstanceOf(FirebaseAccountStatus.UnavailableException.class)
                .hasMessageNotContaining("test-uid");
    }

    @Test
    void invalidUidsDoNotCallFirebase() {
        FirebaseAuth auth = mock(FirebaseAuth.class);
        var status = FirebaseAdminConfiguration.accountStatus(auth);
        assertThat(status.isActive(null)).isFalse();
        assertThat(status.isActive(" ")).isFalse();
        assertThat(status.isActive("a".repeat(129))).isFalse();
        verifyNoInteractions(auth);
    }

    @Test
    void missingProjectDoesNotFallbackToAnotherFirebaseProject() {
        var status = new FirebaseAdminConfiguration().firebaseAccountStatus("");
        assertThatThrownBy(() -> status.isActive("test-uid"))
                .isInstanceOf(FirebaseAccountStatus.UnavailableException.class);
    }
}
