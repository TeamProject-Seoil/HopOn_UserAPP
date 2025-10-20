package com.example.testmap.ui;

import android.app.Dialog;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.FragmentManager;

import com.example.testmap.R;
import com.example.testmap.activity.LoginActivity;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

/**
 * 로그인 필요 안내 다이얼로그
 * - "안내 보지 않기" 체크박스 및 관련 로직 제거
 * - 타이틀/포인트 영역 표시 여부 및 호출 출처(from)만 옵션으로 유지
 */
public class LoginRequiredDialogFragment extends DialogFragment {

    private static final String ARG_TITLE       = "title";
    private static final String ARG_SHOW_POINTS = "show_points";
    private static final String ARG_FROM        = "from";

    public static LoginRequiredDialogFragment newInstance(
            @Nullable String title,
            boolean showPoints,
            @Nullable String from
    ) {
        Bundle b = new Bundle();
        if (title != null) b.putString(ARG_TITLE, title);
        b.putBoolean(ARG_SHOW_POINTS, showPoints);
        if (from != null) b.putString(ARG_FROM, from);

        LoginRequiredDialogFragment f = new LoginRequiredDialogFragment();
        f.setArguments(b);
        return f;
    }

    /** 기본 인스턴스 */
    public static LoginRequiredDialogFragment newInstance() {
        return newInstance(null, true, null);
    }

    /** 강제 표시 */
    public static void show(@NonNull FragmentManager fm) {
        newInstance().show(fm, "login_required");
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        View v = LayoutInflater.from(requireContext())
                .inflate(R.layout.dialog_login_required, null, false);

        // 옵션 적용
        Bundle args = getArguments() == null ? Bundle.EMPTY : getArguments();

        // 타이틀 커스텀(옵션)
        String title = args.getString(ARG_TITLE, null);
        if (title != null) {
            android.widget.TextView tvTitle = v.findViewById(R.id.tvTitle);
            if (tvTitle != null) tvTitle.setText(title);
        }

        // 포인트(장점) 영역 표시 여부
        boolean showPoints = args.getBoolean(ARG_SHOW_POINTS, true);
        View groupPoints = v.findViewById(R.id.groupPoints);
        if (groupPoints != null) groupPoints.setVisibility(showPoints ? View.VISIBLE : View.GONE);

        // 버튼 리스너
        v.findViewById(R.id.btnCancel).setOnClickListener(view -> dismiss());
        v.findViewById(R.id.btnOk).setOnClickListener(view -> {
            Intent i = new Intent(requireContext(), LoginActivity.class);
            String from = args.getString(ARG_FROM, "login_prompt");
            i.putExtra("from", from);
            startActivity(i);
            dismiss();
        });

        // Material 스타일 알림창 + 커스텀 뷰
        AlertDialog dlg = new MaterialAlertDialogBuilder(requireContext())
                .setView(v)
                .create();

        // 배경 투명 처리(카드 라운드가 살아나도록)
        if (dlg.getWindow() != null) {
            dlg.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }
        return dlg;
    }
}
