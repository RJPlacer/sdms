package com.sdms.util;

public final class AppInfo {

    private static final String APP_NAME = "Athlete Track";
    private static final String FALLBACK_VERSION = "1.0-SNAPSHOT";

    private AppInfo() {}

    public static String appName() {
        return APP_NAME;
    }

    public static String version() {
        String v = AppInfo.class.getPackage().getImplementationVersion();
        return (v == null || v.isBlank()) ? FALLBACK_VERSION : v;
    }

    public static String displayVersion() {
        return "v" + version();
    }
}