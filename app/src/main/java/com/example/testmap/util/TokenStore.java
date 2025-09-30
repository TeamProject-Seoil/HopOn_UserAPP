// com.example.testmap.util.TokenStore
package com.example.testmap.util;

import android.content.Context;
import android.content.SharedPreferences;

public class TokenStore {
    private static final String PREF = "auth_pref";
    private static final String KEY_REFRESH = "refresh_token";
    // private static final String KEY_ACCESS  = "access_token"; // <- 더이상 디스크에 저장하지 않음

    // ★ 메모리 전용 Access (프로세스 죽으면 자동 삭제됨)
    private static String volatileAccess = null;

    public static void saveRefresh(Context ctx, String token) {
        prefs(ctx).edit().putString(KEY_REFRESH, token).apply();
    }
    public static String getRefresh(Context ctx) {
        return prefs(ctx).getString(KEY_REFRESH, null);
    }
    public static void clearRefresh(Context ctx) {
        prefs(ctx).edit().remove(KEY_REFRESH).apply();
    }

    // ★ Access는 디스크 X, 메모리만
    public static void saveAccess(Context ctx, String token) {
        volatileAccess = token;
    }
    public static String getAccess(Context ctx) {
        return volatileAccess;
    }
    public static void clearAccess(Context ctx) {
        volatileAccess = null;
    }

    // 필요시 전체 정리
    public static void clearAll(Context ctx) {
        prefs(ctx).edit().clear().apply();
        volatileAccess = null;
    }
    private static SharedPreferences prefs(Context ctx) {
        return ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE);
    }
}
