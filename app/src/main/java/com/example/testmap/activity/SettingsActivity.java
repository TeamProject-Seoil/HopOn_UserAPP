package com.example.testmap.activity;

import android.content.Intent;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.text.InputType;
import android.text.TextUtils;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SwitchCompat;

import com.example.testmap.R;
import com.example.testmap.service.ApiClient;
import com.example.testmap.service.ApiService;
import com.example.testmap.util.DeviceInfo;
import com.example.testmap.util.TokenStore;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import org.json.JSONObject;

import java.util.Map;

import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class SettingsActivity extends AppCompatActivity {

    private ImageButton btnBack;
    private LinearLayout sectionLogin, viewLoggedIn, btnEditAccount, btnDeleteAccount;
    private TextView textName, textPhone, textEmail;
    private ImageView imageProfile;
    private SwitchCompat switchNotice;

    // me()에서 받은 userid (비밀번호 재확인용)
    private String sessionUserId = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        btnBack          = findViewById(R.id.btn_back_settings);
        sectionLogin     = findViewById(R.id.section_login);
        viewLoggedIn     = findViewById(R.id.view_logged_in);
        btnEditAccount   = findViewById(R.id.btn_edit_account);
        btnDeleteAccount = findViewById(R.id.btn_delete_account);
        textName         = findViewById(R.id.text_name);
        textPhone        = findViewById(R.id.text_phone);
        textEmail        = findViewById(R.id.text_email);
        imageProfile     = findViewById(R.id.image_profile);
        switchNotice     = findViewById(R.id.switch_notice);

        btnBack.setOnClickListener(v -> finish());
        sectionLogin.setOnClickListener(v -> startActivity(new Intent(this, LoginActivity.class)));

        // 정보수정 → 비밀번호 먼저 확인
        btnEditAccount.setOnClickListener(v -> showVerifyPasswordDialog());

        switchNotice.setOnCheckedChangeListener((b, checked) ->
                Toast.makeText(this, "공지사항 알림: " + (checked ? "ON" : "OFF"), Toast.LENGTH_SHORT).show()
        );

        findViewById(R.id.btn_bus_boarding).setOnClickListener(v ->
                showChoiceDialog("버스 승차 알림", new String[]{"알림 받지 않음", "1분 전", "3분 전", "5분 전"}));
        findViewById(R.id.btn_bus_alighting).setOnClickListener(v ->
                showChoiceDialog("버스 하차 알림", new String[]{"알림 받지 않음", "1정거장 전", "2정거장 전", "3정거장 전"}));
        findViewById(R.id.btn_auto_refresh).setOnClickListener(v ->
                showChoiceDialog("자동 새로고침 주기", new String[]{"자동 새로 고침 없음", "15초", "30초", "45초"}));
        findViewById(R.id.btn_arrival_display_criteria).setOnClickListener(v ->
                showChoiceDialog("버스 도착정보 노출 기준", new String[]{"노선번호순", "도착시간순", "버스유형순"}));
        findViewById(R.id.btn_arrival_text_color).setOnClickListener(v ->
                showChoiceDialog("버스 도착정보 텍스트 색상", new String[]{"파랑", "초록", "빨강", "검정", "보라", "핑크"}));

        btnDeleteAccount.setOnClickListener(v -> showDeleteAccountDialog());
    }

    @Override
    protected void onResume() {
        super.onResume();
        renderByAuth();
    }

    private void renderByAuth() {
        String at = TokenStore.getAccess(this);

        if (TextUtils.isEmpty(at)) {
            String rt = TokenStore.getRefresh(this);
            if (TextUtils.isEmpty(rt)) {
                showLoggedOut();
                return;
            }
            tryRefreshThenRender(rt);
            return;
        }
        callMeAndRender("Bearer " + at);
    }

    private void tryRefreshThenRender(String refreshToken) {
        ApiClient.get().refresh(
                refreshToken,
                DeviceInfo.getClientType(),
                DeviceInfo.getDeviceId(this)
        ).enqueue(new Callback<ApiService.AuthResponse>() {
            @Override public void onResponse(Call<ApiService.AuthResponse> call, Response<ApiService.AuthResponse> res) {
                if (res.isSuccessful() && res.body() != null && !TextUtils.isEmpty(res.body().accessToken)) {
                    TokenStore.saveAccess(SettingsActivity.this, res.body().accessToken);
                    if (!TextUtils.isEmpty(res.body().refreshToken)) {
                        TokenStore.saveRefresh(SettingsActivity.this, res.body().refreshToken);
                    }
                    callMeAndRender("Bearer " + res.body().accessToken);
                } else {
                    showLoggedOut();
                }
            }
            @Override public void onFailure(Call<ApiService.AuthResponse> call, Throwable t) {
                showLoggedOut();
            }
        });
    }

    private void callMeAndRender(String bearer) {
        ApiClient.get().me(bearer).enqueue(new Callback<ApiService.UserResponse>() {
            @Override
            public void onResponse(Call<ApiService.UserResponse> call, Response<ApiService.UserResponse> res) {
                if (res.isSuccessful() && res.body() != null) {
                    showLoggedIn(res.body(), bearer);
                } else {
                    showLoggedOut();
                }
            }
            @Override
            public void onFailure(Call<ApiService.UserResponse> call, Throwable t) {
                showLoggedOut();
            }
        });
    }

    private void showLoggedOut() {
        viewLoggedIn.setVisibility(View.GONE);
        sectionLogin.setVisibility(View.VISIBLE);
        if (btnDeleteAccount != null) btnDeleteAccount.setVisibility(View.GONE);
        sessionUserId = null;
        if (btnEditAccount != null) btnEditAccount.setEnabled(false);
    }

    private void showLoggedIn(ApiService.UserResponse me, String bearer) {
        sectionLogin.setVisibility(View.GONE);
        viewLoggedIn.setVisibility(View.VISIBLE);

        textName.setText(!TextUtils.isEmpty(me.username) ? me.username : me.userid);
        textPhone.setText(me.tel != null ? me.tel : "");
        textEmail.setText(me.email != null ? me.email : "");

        // userid 확보 및 가드
        sessionUserId = me.userid;
        if (TextUtils.isEmpty(sessionUserId)) {
            // 안전장치: userid가 없으면 비번 확인 흐름을 막음
            if (btnEditAccount != null) btnEditAccount.setEnabled(false);
            Toast.makeText(this, "사용자 식별값(userid)을 확인할 수 없습니다. 다시 로그인해 주세요.", Toast.LENGTH_SHORT).show();
        } else {
            if (btnEditAccount != null) btnEditAccount.setEnabled(true);
        }

        if (me.hasProfileImage) {
            loadProfileImage(bearer);
        } else {
            imageProfile.setImageResource(R.drawable.ic_profile);
        }
        if (btnDeleteAccount != null) btnDeleteAccount.setVisibility(View.VISIBLE);
    }

    private void loadProfileImage(String bearer) {
        ApiClient.get().meImage(bearer).enqueue(new Callback<ResponseBody>() {
            @Override public void onResponse(Call<ResponseBody> call, Response<ResponseBody> res) {
                if (!res.isSuccessful() || res.body() == null) return;
                imageProfile.setImageBitmap(BitmapFactory.decodeStream(res.body().byteStream()));
            }
            @Override public void onFailure(Call<ResponseBody> call, Throwable t) { /* ignore */ }
        });
    }

    // ===== 비밀번호 확인 다이얼로그 (커스텀 카드만 보이게) =====
    private void showVerifyPasswordDialog() {
        if (TextUtils.isEmpty(sessionUserId)) {
            Toast.makeText(this, "세션 정보가 만료되었습니다. 다시 로그인해 주세요.", Toast.LENGTH_SHORT).show();
            startActivity(new Intent(this, LoginActivity.class));
            finish();
            return;
        }

        View dialogView = getLayoutInflater().inflate(R.layout.dialog_verify_password_center, null);
        final TextInputLayout til = dialogView.findViewById(R.id.til_password);
        final TextInputEditText et = dialogView.findViewById(R.id.et_password);
        final View btnCancel = dialogView.findViewById(R.id.btn_cancel);
        final View btnOk = dialogView.findViewById(R.id.btn_ok);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setView(dialogView)
                .setCancelable(false)
                .create();

        // 배경 투명 처리 → 내부 MaterialCardView만 보임
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(
                    new android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT));
        }
        dialog.show();

        btnCancel.setOnClickListener(v -> dialog.dismiss());

        // ...생략(다이얼로그 구성 동일)

        btnOk.setOnClickListener(v -> {
            String pwd = et.getText() == null ? "" : et.getText().toString().trim();
            if (pwd.isEmpty()) { til.setError("비밀번호를 입력하세요"); return; }
            til.setError(null);
            btnOk.setEnabled(false);
            btnCancel.setEnabled(false);

            // ✅ 서버가 기대하는 키로 보냄: currentPassword (+ userid는 보수적으로 포함)
            java.util.HashMap<String, Object> body = new java.util.HashMap<>();
            body.put("userid", sessionUserId);         // 일부 서버는 필요 없음. 있어도 무해
            body.put("currentPassword", pwd);          // <-- 핵심: password → currentPassword 로 변경

            ApiClient.get().verifyPwUser(body).enqueue(
                    new retrofit2.Callback<java.util.Map<String, Object>>() {
                        @Override public void onResponse(
                                retrofit2.Call<java.util.Map<String, Object>> call,
                                retrofit2.Response<java.util.Map<String, Object>> res
                        ) {
                            if (res.isSuccessful()) {
                                dialog.dismiss();
                                Intent i = new Intent(SettingsActivity.this, AccountEditActivity.class);
                                i.putExtra("verified_pw", pwd);
                                startActivity(i);
                            } else {
                                String msg = "인증 실패 (" + res.code() + ")";
                                try {
                                    okhttp3.ResponseBody eb = res.errorBody();
                                    if (eb != null) {
                                        String raw = eb.string();
                                        org.json.JSONObject obj = new org.json.JSONObject(raw);
                                        String reason = obj.optString("reason", "");
                                        if ("BAD_CURRENT_PASSWORD".equals(reason)) {
                                            msg = "비밀번호가 올바르지 않습니다";
                                        } else if ("MISSING_FIELD".equals(reason)) {
                                            msg = "요청 필드가 누락되었습니다.";
                                        }
                                    }
                                } catch (Exception ignore) {}
                                til.setError(msg);
                                btnOk.setEnabled(true);
                                btnCancel.setEnabled(true);
                            }
                        }
                        @Override public void onFailure(
                                retrofit2.Call<java.util.Map<String, Object>> call, Throwable t
                        ) {
                            til.setError("네트워크 오류: " + t.getMessage());
                            btnOk.setEnabled(true);
                            btnCancel.setEnabled(true);
                        }
                    }
            );
        });


    }

    // ===== 회원 탈퇴 =====
    private void showDeleteAccountDialog() {
        final EditText et = new EditText(this);
        et.setHint("현재 비밀번호");
        et.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        et.setPadding(24, 24, 24, 24);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("회원 탈퇴")
                .setMessage("계정을 삭제하려면 현재 비밀번호를 입력하세요.\n이 작업은 되돌릴 수 없습니다.")
                .setView(et)
                .setNegativeButton("취소", (d, w) -> d.dismiss())
                .setPositiveButton("탈퇴", null)
                .create();
        dialog.show();

        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String pw = et.getText().toString().trim();
            if (pw.isEmpty()) {
                Toast.makeText(this, "비밀번호를 입력하세요.", Toast.LENGTH_SHORT).show();
                return;
            }
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(false);
            requestDeleteAccount(pw, () -> {
                if (dialog.isShowing()) dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(true);
            }, () -> {
                if (dialog.isShowing()) dialog.dismiss();
            });
        });
    }

    private void requestDeleteAccount(String currentPassword, Runnable onFinally, Runnable onSuccess) {
        String at = TokenStore.getAccess(this);
        if (TextUtils.isEmpty(at)) {
            Toast.makeText(this, "세션이 만료되었습니다. 다시 로그인해주세요.", Toast.LENGTH_SHORT).show();
            startActivity(new Intent(this, LoginActivity.class));
            finish();
            if (onFinally != null) onFinally.run();
            return;
        }
        String bearer = "Bearer " + at;

        ApiClient.get().deleteMe(bearer, new ApiService.DeleteAccountRequest(currentPassword))
                .enqueue(new Callback<Map<String, Object>>() {
                    @Override public void onResponse(Call<Map<String, Object>> call, Response<Map<String, Object>> res) {
                        if (res.isSuccessful()) {
                            handleDeleteSuccess(onSuccess);
                        } else if (res.code() == 401) {
                            tryRefreshAndRetry(currentPassword, onFinally, onSuccess);
                        } else {
                            showDeleteErrorToast(res);
                            if (onFinally != null) onFinally.run();
                        }
                    }
                    @Override public void onFailure(Call<Map<String, Object>> call, Throwable t) {
                        Toast.makeText(SettingsActivity.this, "네트워크 오류", Toast.LENGTH_SHORT).show();
                        if (onFinally != null) onFinally.run();
                    }
                });
    }

    private void tryRefreshAndRetry(String currentPassword, Runnable onFinally, Runnable onSuccess) {
        String rt = TokenStore.getRefresh(this);
        if (TextUtils.isEmpty(rt)) {
            Toast.makeText(this, "세션이 만료되었습니다. 다시 로그인해주세요.", Toast.LENGTH_SHORT).show();
            startActivity(new Intent(this, LoginActivity.class));
            finish();
            if (onFinally != null) onFinally.run();
            return;
        }
        ApiClient.get().refresh(rt, DeviceInfo.getClientType(), DeviceInfo.getDeviceId(this))
                .enqueue(new Callback<ApiService.AuthResponse>() {
                    @Override public void onResponse(Call<ApiService.AuthResponse> call, Response<ApiService.AuthResponse> res) {
                        if (res.isSuccessful() && res.body() != null && !TextUtils.isEmpty(res.body().accessToken)) {
                            TokenStore.saveAccess(SettingsActivity.this, res.body().accessToken);
                            if (!TextUtils.isEmpty(res.body().refreshToken)) {
                                TokenStore.saveRefresh(SettingsActivity.this, res.body().refreshToken);
                            }
                            requestDeleteAccount(currentPassword, onFinally, onSuccess);
                        } else {
                            Toast.makeText(SettingsActivity.this, "세션이 만료되었습니다. 다시 로그인해주세요.", Toast.LENGTH_SHORT).show();
                            startActivity(new Intent(SettingsActivity.this, LoginActivity.class));
                            finish();
                            if (onFinally != null) onFinally.run();
                        }
                    }
                    @Override public void onFailure(Call<ApiService.AuthResponse> call, Throwable t) {
                        Toast.makeText(SettingsActivity.this, "네트워크 오류", Toast.LENGTH_SHORT).show();
                        if (onFinally != null) onFinally.run();
                    }
                });
    }

    private void handleDeleteSuccess(Runnable onSuccess) {
        Toast.makeText(SettingsActivity.this, "계정이 삭제되었습니다.", Toast.LENGTH_LONG).show();
        TokenStore.clearAll(SettingsActivity.this);
        Intent i = new Intent(SettingsActivity.this, LoginActivity.class);
        i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(i);
        finish();
        if (onSuccess != null) onSuccess.run();
    }

    private void showDeleteErrorToast(Response<?> res) {
        String message = "삭제 실패";
        if (res.code() == 400) {
            try {
                ResponseBody eb = res.errorBody(); // ✅ 불필요한 캐스팅 제거
                if (eb != null) {
                    String raw = eb.string();
                    JSONObject obj = new JSONObject(raw);
                    String reason = obj.optString("reason", "");
                    if ("BAD_CURRENT_PASSWORD".equals(reason)) {
                        message = "비밀번호가 올바르지 않습니다.";
                    } else {
                        message = "요청을 처리할 수 없습니다.";
                    }
                }
            } catch (Exception ignore) { /* 기본 문구 유지 */ }
        } else if (res.code() == 403) {
            message = "권한이 없습니다.";
        } else if (res.code() >= 500) {
            message = "서버 오류";
        }
        Toast.makeText(SettingsActivity.this, message, Toast.LENGTH_SHORT).show();
    }

    private void showChoiceDialog(String title, String[] options) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(title);
        builder.setSingleChoiceItems(options, -1, (dialog, which) -> {
            Toast.makeText(this, title + ": " + options[which], Toast.LENGTH_SHORT).show();
            dialog.dismiss();
        });
        builder.setNegativeButton("취소", (dialog, which) -> dialog.dismiss());
        builder.show();
    }
}
