package com.example.testmap.activity;

import android.content.Intent;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
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
    private String sessionUserId = null; // me() 결과에서 userid

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

        // 계정 수정(비밀번호 재확인 다이얼로그)
        btnEditAccount.setOnClickListener(v -> showVerifyPasswordDialog());

        // ✅ 회원탈퇴 버튼 -> UserQuitActivity(user_quit.xml) 화면으로 이동
        btnDeleteAccount.setOnClickListener(v ->
                startActivity(new Intent(this, UserQuitActivity.class))
        );

        switchNotice.setOnCheckedChangeListener((b, checked) ->
                Toast.makeText(this, "공지사항 알림: " + (checked ? "ON" : "OFF"), Toast.LENGTH_SHORT).show()
        );
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
        ApiClient.get().refresh(refreshToken, DeviceInfo.getClientType(), DeviceInfo.getDeviceId(this))
                .enqueue(new Callback<ApiService.AuthResponse>() {
                    @Override public void onResponse(Call<ApiService.AuthResponse> call, Response<ApiService.AuthResponse> res) {
                        if (res.isSuccessful() && res.body() != null && !TextUtils.isEmpty(res.body().accessToken)) {
                            TokenStore.saveAccess(SettingsActivity.this, res.body().accessToken);
                            if (!TextUtils.isEmpty(res.body().refreshToken))
                                TokenStore.saveRefresh(SettingsActivity.this, res.body().refreshToken);
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
            @Override public void onFailure(Call<ApiService.UserResponse> call, Throwable t) {
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

        sessionUserId = me.userid;
        if (TextUtils.isEmpty(sessionUserId)) {
            btnEditAccount.setEnabled(false);
            Toast.makeText(this, "사용자 식별값(userid)을 확인할 수 없습니다. 다시 로그인해 주세요.", Toast.LENGTH_SHORT).show();
        } else {
            btnEditAccount.setEnabled(true);
        }

        if (me.hasProfileImage) loadProfileImage(bearer);
        else imageProfile.setImageResource(R.drawable.ic_profile);

        if (btnDeleteAccount != null) btnDeleteAccount.setVisibility(View.VISIBLE);
    }

    private void loadProfileImage(String bearer) {
        ApiClient.get().meImage(bearer).enqueue(new Callback<ResponseBody>() {
            @Override public void onResponse(Call<ResponseBody> call, Response<ResponseBody> res) {
                if (res.isSuccessful() && res.body() != null)
                    imageProfile.setImageBitmap(BitmapFactory.decodeStream(res.body().byteStream()));
            }
            @Override public void onFailure(Call<ResponseBody> call, Throwable t) { }
        });
    }

    // ===== 현재 비밀번호 확인 다이얼로그: /auth/verify-current-password 사용 =====
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
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(
                    new android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT));
        }
        dialog.show();

        btnCancel.setOnClickListener(v -> dialog.dismiss());

        // 입력 중 오류문구 제거
        et.addTextChangedListener(new SimpleTextWatcher(() -> til.setError(null)));

        // IME actionDone(키보드 완료)로 제출
        et.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) { btnOk.performClick(); return true; }
            return false;
        });

        btnOk.setOnClickListener(v -> {
            String pwd = et.getText() == null ? "" : et.getText().toString().trim();
            if (pwd.isEmpty()) { til.setError("비밀번호를 입력하세요"); return; }
            til.setError(null);
            btnOk.setEnabled(false);
            btnCancel.setEnabled(false);

            // 키보드 닫기
            try {
                InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
                if (imm != null) imm.hideSoftInputFromWindow(et.getWindowToken(), 0);
            } catch (Exception ignore) {}

            String at = TokenStore.getAccess(this);
            if (TextUtils.isEmpty(at)) {
                Toast.makeText(this, "세션이 만료되었습니다. 다시 로그인해 주세요.", Toast.LENGTH_SHORT).show();
                startActivity(new Intent(this, LoginActivity.class));
                finish();
                return;
            }
            String bearer = "Bearer " + at;

            String clientType = DeviceInfo.getClientType();
            if (TextUtils.isEmpty(clientType)) clientType = "USER_APP";
            String deviceId = DeviceInfo.getDeviceId(this);
            if (TextUtils.isEmpty(deviceId)) deviceId = "unknown-device";

            // 서버가 요구하는 DTO
            ApiService.VerifyCurrentPasswordRequest body =
                    new ApiService.VerifyCurrentPasswordRequest(pwd, clientType, deviceId);

            verifyCurrentPasswordWithRetry(bearer, body, pwd, dialog, til, btnOk, btnCancel);
        });
    }

    private void verifyCurrentPasswordWithRetry(String bearer,
                                                ApiService.VerifyCurrentPasswordRequest body,
                                                String plainPwd,
                                                AlertDialog dialog,
                                                TextInputLayout til,
                                                View btnOk,
                                                View btnCancel) {
        ApiClient.get().verifyCurrentPassword(bearer, body)
                .enqueue(new retrofit2.Callback<Map<String,Object>>() {
                    @Override public void onResponse(retrofit2.Call<Map<String,Object>> call,
                                                     retrofit2.Response<Map<String,Object>> res) {
                        if (res.isSuccessful()) {
                            if (dialog.isShowing()) dialog.dismiss();
                            Intent i = new Intent(SettingsActivity.this, AccountEditActivity.class);
                            i.putExtra("verified_pw", plainPwd);
                            startActivity(i);
                            return;
                        }

                        // 401이면 refresh 후 1회 재시도
                        if (res.code() == 401) {
                            String rt = TokenStore.getRefresh(SettingsActivity.this);
                            if (!TextUtils.isEmpty(rt)) {
                                ApiClient.get().refresh(rt, DeviceInfo.getClientType(), DeviceInfo.getDeviceId(SettingsActivity.this))
                                        .enqueue(new Callback<ApiService.AuthResponse>() {
                                            @Override public void onResponse(Call<ApiService.AuthResponse> c2,
                                                                             Response<ApiService.AuthResponse> r2) {
                                                if (r2.isSuccessful() && r2.body() != null && !TextUtils.isEmpty(r2.body().accessToken)) {
                                                    TokenStore.saveAccess(SettingsActivity.this, r2.body().accessToken);
                                                    if (!TextUtils.isEmpty(r2.body().refreshToken))
                                                        TokenStore.saveRefresh(SettingsActivity.this, r2.body().refreshToken);
                                                    String newBearer = "Bearer " + r2.body().accessToken;

                                                    ApiClient.get().verifyCurrentPassword(newBearer, body)
                                                            .enqueue(new retrofit2.Callback<Map<String, Object>>() {
                                                                @Override public void onResponse(retrofit2.Call<Map<String, Object>> c3,
                                                                                                 retrofit2.Response<Map<String, Object>> r3) {
                                                                    if (r3.isSuccessful()) {
                                                                        if (dialog.isShowing()) dialog.dismiss();
                                                                        Intent i = new Intent(SettingsActivity.this, AccountEditActivity.class);
                                                                        i.putExtra("verified_pw", plainPwd);
                                                                        startActivity(i);
                                                                    } else {
                                                                        showVerifyErrorForCurrentPwd(r3, til, btnOk, btnCancel);
                                                                    }
                                                                }
                                                                @Override public void onFailure(retrofit2.Call<Map<String, Object>> c3, Throwable t3) {
                                                                    til.setError("네트워크 오류: " + t3.getMessage());
                                                                    btnOk.setEnabled(true); btnCancel.setEnabled(true);
                                                                }
                                                            });
                                                } else {
                                                    til.setError("세션이 만료되었습니다. 다시 로그인해 주세요.");
                                                    btnOk.setEnabled(true); btnCancel.setEnabled(true);
                                                }
                                            }
                                            @Override public void onFailure(Call<ApiService.AuthResponse> c2, Throwable t2) {
                                                til.setError("네트워크 오류: " + t2.getMessage());
                                                btnOk.setEnabled(true); btnCancel.setEnabled(true);
                                            }
                                        });
                                return;
                            }
                        }

                        showVerifyErrorForCurrentPwd(res, til, btnOk, btnCancel);
                    }

                    @Override public void onFailure(retrofit2.Call<Map<String,Object>> call, Throwable t) {
                        til.setError("네트워크 오류: " + t.getMessage());
                        btnOk.setEnabled(true); btnCancel.setEnabled(true);
                    }
                });
    }

    private void showVerifyErrorForCurrentPwd(Response<?> res, TextInputLayout til, View btnOk, View btnCancel) {
        String msg = "인증 실패 (" + res.code() + ")";
        try {
            ResponseBody eb = res.errorBody();
            if (eb != null) {
                String raw = eb.string();
                JSONObject obj = new JSONObject(raw);
                String reason = obj.optString("reason", "");
                if ("BAD_CURRENT_PASSWORD".equals(reason)) {
                    msg = "비밀번호가 올바르지 않습니다";
                } else if ("MISSING_FIELDS".equals(reason)) {
                    msg = "요청 필드가 누락되었습니다. (currentPassword, clientType, deviceId 확인)";
                } else if (!TextUtils.isEmpty(reason)) {
                    msg = msg + " [" + reason + "]";
                }
            }
        } catch (Exception ignore) {}
        til.setError(msg);
        btnOk.setEnabled(true);
        btnCancel.setEnabled(true);
    }

    /** 간단한 텍스트워처 (람다로 afterTextChanged만 처리) */
    private static class SimpleTextWatcher implements android.text.TextWatcher {
        private final Runnable after;
        SimpleTextWatcher(Runnable after) { this.after = after; }
        @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
        @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
        @Override public void afterTextChanged(android.text.Editable s) { if (after != null) after.run(); }
    }
}
