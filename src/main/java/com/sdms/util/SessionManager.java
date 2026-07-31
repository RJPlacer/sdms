package com.sdms.util;

import com.sdms.model.User;

public class SessionManager {
    private static User currentUser;

    public static void login(User user) { currentUser = user; }
    public static void logout()         { currentUser = null; }
    public static User getCurrentUser() { return currentUser; }
    public static boolean isLoggedIn()  { return currentUser != null; }
    public static boolean canEdit()     { return currentUser != null && currentUser.canEdit(); }
    public static boolean canDelete()   { return currentUser != null && currentUser.canDelete(); }
    public static boolean isAdmin()     { return currentUser != null && currentUser.isAdmin(); }
}
