package com.example.bodeul.domain.model;

import static org.junit.Assert.*;
import com.example.bodeul.domain.model.AccountSignInInfo.Provider;
import java.util.Arrays;
import java.util.Collections;
import org.junit.Test;

public class AccountSignInInfoTest {
    @Test public void emailAddressAloneDoesNotEnablePasswordReset() {
        AccountSignInInfo info = new AccountSignInInfo("user", "user@gmail.com", null, "EMAIL");
        assertFalse(info.canResetPassword());
        assertEquals(Provider.UNKNOWN, info.getPrimaryProvider());
    }
    @Test public void passwordProviderRequiresEmailAndUid() {
        assertTrue(new AccountSignInInfo("u", "u@example.test", Arrays.asList("firebase", "password"), null).canResetPassword());
        assertFalse(new AccountSignInInfo("u", " ", Collections.singleton("password"), null).canResetPassword());
        assertFalse(new AccountSignInInfo(null, "u@example.test", Collections.singleton("password"), null).canResetPassword());
    }
    @Test public void googleAccountDoesNotHavePasswordUnlessActuallyLinked() {
        assertFalse(new AccountSignInInfo("u", "u@example.test", Collections.singleton("google.com"), null).canResetPassword());
        AccountSignInInfo linked = new AccountSignInInfo("u", "u@example.test", Arrays.asList("google.com", "password"), null);
        assertTrue(linked.canResetPassword());
        assertTrue(linked.has(Provider.GOOGLE));
        assertEquals(Provider.EMAIL, linked.getPrimaryProvider());
    }
    @Test public void customProviderRequiresMatchingServerUidAndMetadata() {
        assertTrue(new AccountSignInInfo("kakao_123", "u@example.test", null, "KAKAO").has(Provider.KAKAO));
        assertTrue(new AccountSignInInfo("naver_123", "u@example.test", null, "NAVER").has(Provider.NAVER));
        assertFalse(new AccountSignInInfo("google_123", "u@example.test", null, "KAKAO").has(Provider.KAKAO));
        assertFalse(new AccountSignInInfo("kakao_123", "u@example.test", null, "NAVER").has(Provider.NAVER));
        assertFalse(new AccountSignInInfo("kakao_123", "u@example.test", null, "KAKAO").canResetPassword());
    }
    @Test public void profileCannotPretendToBeGoogleOrAppleLink() {
        assertEquals(Provider.UNKNOWN, new AccountSignInInfo("u", "u@example.test", null, "GOOGLE").getPrimaryProvider());
        assertEquals(Provider.UNKNOWN, new AccountSignInInfo("u", "u@example.test", null, "APPLE").getPrimaryProvider());
    }
    @Test public void appleIsRecognizedWithoutEnablingPassword() {
        AccountSignInInfo info = new AccountSignInInfo("u", "relay@example.test", Collections.singleton("apple.com"), null);
        assertEquals(Provider.APPLE, info.getPrimaryProvider());
        assertFalse(info.canResetPassword());
    }
    @Test public void resetConfirmationMustMatchBothUidAndEmail() {
        AccountSignInInfo info = new AccountSignInInfo("u", " User@Example.test ", Collections.singleton("password"), null);
        assertTrue(info.matches("u", "user@example.test"));
        assertFalse(info.matches("other", "user@example.test"));
        assertFalse(info.matches("u", "old@example.test"));
    }
    @Test public void missingValuesRemainUnknownNotExampleAccount() {
        AccountSignInInfo info = new AccountSignInInfo(null, null, Arrays.asList("firebase", "unknown"), null);
        assertEquals("", info.getEmail());
        assertEquals(Provider.UNKNOWN, info.getPrimaryProvider());
        assertFalse(info.matches("", ""));
    }
}
