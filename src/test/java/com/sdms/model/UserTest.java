package com.sdms.model;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class UserTest {

    @Test
    void rolePermissionsAreEnforced() {
        User user = new User();

        user.setRole("VIEWER");
        assertFalse(user.canEdit());
        assertFalse(user.canDelete());

        user.setRole("ENCODER");
        assertTrue(user.canEdit());
        assertFalse(user.canDelete());

        user.setRole("ADMIN");
        assertTrue(user.canEdit());
        assertTrue(user.canDelete());
    }
}
