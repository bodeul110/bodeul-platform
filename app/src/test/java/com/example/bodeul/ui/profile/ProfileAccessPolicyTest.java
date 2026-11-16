package com.example.bodeul.ui.profile;

import com.example.bodeul.domain.model.UserRole;
import org.junit.Test;
import static org.junit.Assert.*;

public class ProfileAccessPolicyTest {
    @Test public void generalHomeAllowsOnlyPatientAndGuardian() {
        assertTrue(ProfileAccessPolicy.canOpenHome(UserRole.PATIENT, false));
        assertTrue(ProfileAccessPolicy.canOpenHome(UserRole.GUARDIAN, false));
        assertFalse(ProfileAccessPolicy.canOpenHome(UserRole.MANAGER, false));
        assertFalse(ProfileAccessPolicy.canOpenHome(UserRole.ADMIN, false));
        assertFalse(ProfileAccessPolicy.canOpenHome(null, false));
    }
    @Test public void managerHomeRequiresManagerRole() {
        for (UserRole role : UserRole.values()) assertEquals(role == UserRole.MANAGER, ProfileAccessPolicy.canOpenHome(role, true));
        assertFalse(ProfileAccessPolicy.canOpenHome(null, true));
    }
    @Test public void settingsExcludeUnknownAndAdminRoles() {
        assertTrue(ProfileAccessPolicy.canOpenSettings(UserRole.PATIENT));
        assertTrue(ProfileAccessPolicy.canOpenSettings(UserRole.GUARDIAN));
        assertTrue(ProfileAccessPolicy.canOpenSettings(UserRole.MANAGER));
        assertFalse(ProfileAccessPolicy.canOpenSettings(UserRole.ADMIN));
        assertFalse(ProfileAccessPolicy.canOpenSettings(null));
    }
}
