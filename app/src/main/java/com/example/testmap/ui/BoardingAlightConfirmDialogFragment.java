package com.example.testmap.ui;

import android.app.Dialog;
import android.content.DialogInterface;
import android.graphics.Color;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.Window;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;

import com.example.testmap.R;

public class BoardingAlightConfirmDialogFragment extends DialogFragment {

    public enum Mode {
        BOARDING,  // 승차 확인
        ALIGHTING  // 하차 확인
    }

    public interface Listener {
        void onConfirmed();   // 사용자가 버튼 눌러 확인
        void onTimeout();     // 20초 안 눌러서 자동 처리
        void onCancelled();   // "나중에" 또는 다이얼로그 닫힘
    }

    private static final String ARG_MODE       = "mode";
    private static final String ARG_ROUTE_NAME = "route_name";
    private static final String ARG_STOP_NAME  = "stop_name";

    private static final long LIMIT_MS = 20_000L; // 20초

    private Listener listener;
    private CountDownTimer timer;
    private TextView tvCountdown;
    private Button btnConfirm;
    private Button btnLater;

    private boolean callbackFired = false; // ★ 어떤 콜백이든 한 번이라도 호출됐는지


    public static BoardingAlightConfirmDialogFragment newInstance(
            Mode mode,
            @Nullable String routeName,
            @Nullable String stopName
    ) {
        BoardingAlightConfirmDialogFragment f = new BoardingAlightConfirmDialogFragment();
        Bundle b = new Bundle();
        b.putString(ARG_MODE, mode.name());
        b.putString(ARG_ROUTE_NAME,
                TextUtils.isEmpty(routeName) ? "" : routeName);
        b.putString(ARG_STOP_NAME,
                TextUtils.isEmpty(stopName) ? "" : stopName);
        f.setArguments(b);
        return f;
    }

    public void setListener(@Nullable Listener l) {
        this.listener = l;
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        Dialog dialog = new Dialog(requireContext());
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        View v = LayoutInflater.from(getContext())
                .inflate(R.layout.dialog_board_alight_confirm, null, false);
        dialog.setContentView(v);
        dialog.setCanceledOnTouchOutside(false);

        ImageView imgIcon   = v.findViewById(R.id.imgIcon);
        TextView tvTitle    = v.findViewById(R.id.tvTitle);
        TextView tvRoute    = v.findViewById(R.id.tvRouteName);
        TextView tvStopName = v.findViewById(R.id.tvStopName);
        TextView tvMessage  = v.findViewById(R.id.tvMessage);
        tvCountdown         = v.findViewById(R.id.tvCountdown);
        btnConfirm          = v.findViewById(R.id.btnConfirm);
        btnLater            = v.findViewById(R.id.btnLater);

        Bundle args = getArguments() != null ? getArguments() : new Bundle();
        String modeStr   = args.getString(ARG_MODE, Mode.BOARDING.name());
        String routeName = args.getString(ARG_ROUTE_NAME, "");
        String stopName  = args.getString(ARG_STOP_NAME, "");
        Mode mode        = Mode.valueOf(modeStr);

        // 텍스트 세팅
        String routeText = TextUtils.isEmpty(routeName) ? "" : routeName;
        tvRoute.setText(routeText);

        if (mode == Mode.BOARDING) {
            tvTitle.setText("승차를 확인해주세요");
            btnConfirm.setText("네, 탑승했어요");
            if (!TextUtils.isEmpty(stopName)) {
                tvStopName.setText(stopName + " 정류장에서 승차하셨나요?");
            } else {
                tvStopName.setText("지금 버스에 탑승하셨나요?");
            }
            tvMessage.setText("20초 안에 확인하지 않으면 예약이 자동으로 취소될 수 있어요.");
        } else {
            tvTitle.setText("하차를 확인해주세요");
            btnConfirm.setText("네, 하차했어요");
            if (!TextUtils.isEmpty(stopName)) {
                tvStopName.setText(stopName + " 정류장에서 하차하셨나요?");
            } else {
                tvStopName.setText("지금 버스에서 하차하셨나요?");
            }
            tvMessage.setText("20초 안에 확인하지 않아도 자동으로 하차 처리됩니다.");
        }

        // 클릭 리스너
        btnConfirm.setOnClickListener(v1 -> {
            stopTimer();
            callbackFired = true;
            if (listener != null) listener.onConfirmed();
            dismissAllowingStateLoss();
        });

        btnLater.setOnClickListener(v12 -> {
            stopTimer();
            callbackFired = true;
            if (listener != null) listener.onCancelled();
            dismissAllowingStateLoss();
        });

        startTimer(mode);

        // 다이얼로그 폭 줄이기 (둥글둥글 카드 느낌)
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }

        return dialog;
    }

    private void startTimer(Mode mode) {
        stopTimer();
        if (tvCountdown != null) {
            int color = (mode == Mode.BOARDING)
                    ? Color.parseColor("#DC2626") // 진한 빨강
                    : Color.parseColor("#6B7280"); // 회색 톤
            tvCountdown.setTextColor(color);
        }
        timer = new CountDownTimer(LIMIT_MS, 1000L) {
            @Override
            public void onTick(long millisUntilFinished) {
                long sec = millisUntilFinished / 1000L;
                if (tvCountdown != null) {
                    tvCountdown.setText(sec + "초 남음");
                }
            }

            @Override
            public void onFinish() {
                if (!isAdded()) return;
                if (tvCountdown != null) tvCountdown.setText("0초 남음");
                callbackFired = true;
                if (listener != null) listener.onTimeout();
                dismissAllowingStateLoss();
            }
        }.start();
    }


    private void stopTimer() {
        if (timer != null) {
            timer.cancel();
            timer = null;
        }
    }

    @Override
    public void onDismiss(@NonNull DialogInterface dialog) {
        super.onDismiss(dialog);
        stopTimer();
        // ★ 아직 아무 콜백도 안 나갔는데 다이얼로그가 사라졌으면 -> 취소로 처리
        if (!callbackFired && listener != null) {
            listener.onCancelled();
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        stopTimer();
    }
}
