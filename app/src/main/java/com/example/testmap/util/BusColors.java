package com.example.testmap.util;

import androidx.annotation.ColorRes;
import androidx.annotation.Nullable;
import com.example.testmap.R;

public final class BusColors {
    private BusColors() {}

    @ColorRes
    public static int forRouteType(@Nullable String routeType) {
        if (routeType == null) return R.color.bus_default;
        switch (routeType) {
            case "1": return R.color.bus_airport;
            case "2": return R.color.bus_maeul;
            case "3": return R.color.bus_ganseon;
            case "4": return R.color.bus_jiseon;
            case "5": return R.color.bus_sunhwan;
            case "6": return R.color.bus_gwangyeok;
            // 9 = 폐지 → 기본색
            default:  return R.color.bus_default;
        }
    }
}
