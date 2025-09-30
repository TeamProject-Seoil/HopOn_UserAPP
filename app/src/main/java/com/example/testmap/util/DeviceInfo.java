// com/example/testmap/util/DeviceInfo.java
package com.example.testmap.util;

import android.content.Context;
import android.provider.Settings;

public class DeviceInfo {
    public static String getDeviceId(Context ctx) {
        try {
            return Settings.Secure.getString(ctx.getContentResolver(), Settings.Secure.ANDROID_ID);
        } catch (Exception e) {
            return "unknown-device";
        }
    }
    public static String getClientType() { return "ANDROID"; }
}
