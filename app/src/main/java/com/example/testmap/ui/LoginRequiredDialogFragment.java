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

import com.example.testmap.R;
import com.example.testmap.activity.LoginActivity;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

public class LoginRequiredDialogFragment extends DialogFragment {

    public static LoginRequiredDialogFragment newInstance() {
        return new LoginRequiredDialogFragment();
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        View v = LayoutInflater.from(requireContext())
                .inflate(R.layout.dialog_login_required, null, false);

        v.findViewById(R.id.btnCancel).setOnClickListener(view -> dismiss());
        v.findViewById(R.id.btnOk).setOnClickListener(view -> {
            // 로그인 화면으로 이동
            Intent i = new Intent(requireContext(), LoginActivity.class);
            i.putExtra("from", "reservation");
            startActivity(i);
            dismiss();
        });

        // Material 스타일 알림창 + 커스텀 뷰
        AlertDialog dlg = new MaterialAlertDialogBuilder(requireContext())
                .setView(v)
                .create();

        // 배경 모서리 둥글게 (MaterialAlertDialog는 테마로도 가능하지만 안전하게)
        dlg.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        return dlg;
    }

    public static void show(androidx.fragment.app.FragmentManager fm) {
        newInstance().show(fm, "login_required");
    }
}
