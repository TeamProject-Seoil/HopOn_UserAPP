package com.example.testmap.ui;

import android.app.Dialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.view.LayoutInflater;
import android.view.Window;
import android.view.WindowManager;
import android.widget.TextView;

import androidx.annotation.NonNull;

import com.example.testmap.R;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

public final class UiDialogs {

    private UiDialogs() {}

    /** 머티리얼 로딩 다이얼로그 (취소 불가) */
    public static Dialog showLoading(@NonNull Context ctx, CharSequence message) {
        Dialog d = new Dialog(ctx);
        d.requestWindowFeature(Window.FEATURE_NO_TITLE);
        d.setContentView(LayoutInflater.from(ctx).inflate(R.layout.dialog_loading, null, false));
        d.setCancelable(false);
        d.setCanceledOnTouchOutside(false);

        // 둥근/투명 배경
        if (d.getWindow() != null) {
            d.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            d.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
        }

        TextView tv = d.findViewById(R.id.tvMessage);
        if (tv != null && message != null) tv.setText(message);

        d.show();
        return d;
    }

    /** 예약 완료 다이얼로그:  autoDismissMs <= 0 이면 버튼 노출; 아니면 자동 닫힘 */
    public static Dialog showReservationDone(@NonNull Context ctx,
                                             String routeText,
                                             String fromToText,
                                             long autoDismissMs,
                                             Runnable onDismiss) {
        Dialog d = new Dialog(ctx);
        d.requestWindowFeature(Window.FEATURE_NO_TITLE);
        d.setContentView(LayoutInflater.from(ctx).inflate(R.layout.dialog_reservation_done, null, false));
        d.setCancelable(false);
        d.setCanceledOnTouchOutside(false);

        if (d.getWindow() != null) {
            d.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }

        TextView tvRoute = d.findViewById(R.id.tvRoute);
        TextView tvStops = d.findViewById(R.id.tvStops);
        TextView tvTitle = d.findViewById(R.id.tvTitle);
        if (tvTitle != null) tvTitle.setText("예약이 완료되었습니다");
        if (tvRoute != null) tvRoute.setText(routeText);
        if (tvStops != null) tvStops.setText(fromToText);

        // 자동 닫힘 / 버튼 모드
        android.view.View btnOk = d.findViewById(R.id.btnOk);
        if (autoDismissMs > 0) {
            if (btnOk != null) btnOk.setVisibility(android.view.View.GONE);
            d.show();
            d.getWindow().getDecorView().postDelayed(() -> {
                try { d.dismiss(); } catch (Throwable ignore) {}
                if (onDismiss != null) onDismiss.run();
            }, autoDismissMs);
        } else {
            if (btnOk != null) {
                btnOk.setVisibility(android.view.View.VISIBLE);
                btnOk.setOnClickListener(v -> {
                    try { d.dismiss(); } catch (Throwable ignore) {}
                    if (onDismiss != null) onDismiss.run();
                });
            }
            d.show();
        }
        return d;
    }
}
