package com.example.testmap.activity;

import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.BitmapFactory;
import android.graphics.Color;
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

    private LinearLayout btnTermsPrivacy;   // 약관/개인정보
    private LinearLayout btnAppInquiry;     // 문의 목록

    // me() 결과에서 세션 정보
    private String sessionUserId = null;
    private String sessionEmail  = null;
    private String sessionName   = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        btnTermsPrivacy  = findViewById(R.id.btn_terms_privacy);
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
        btnAppInquiry    = findViewById(R.id.btn_app_inquiry);

        btnBack.setOnClickListener(v -> finish());
        sectionLogin.setOnClickListener(v -> startActivity(new Intent(this, LoginActivity.class)));

        // ✅ 약관 및 개인정보 이동 - onCreate에서 바로 연결
        if (btnTermsPrivacy != null) {
            btnTermsPrivacy.setOnClickListener(v ->
                    startActivity(new Intent(SettingsActivity.this, TermsPrivacyActivity.class)));
        }

        // 계정 수정(비밀번호 재확인 다이얼로그)
        btnEditAccount.setOnClickListener(v -> showVerifyPasswordDialog());

        // 회원탈퇴 화면 이동
        btnDeleteAccount.setOnClickListener(v ->
                startActivity(new Intent(this, UserQuitActivity.class))
        );

        // 공지 알림 스위치 (임시 토스트)
        switchNotice.setOnCheckedChangeListener((b, checked) ->
                Toast.makeText(this, "공지사항 알림: " + (checked ? "ON" : "OFF"), Toast.LENGTH_SHORT).show()
        );

        // 어플리케이션 문의 화면으로 이동 (목록 화면)
        btnAppInquiry.setOnClickListener(v ->
                startActivity(new Intent(this, InquiryActivity.class))
        );

        // ===== 팝업으로 고르는 설정 버튼들 =====
        // 버스 승차 알림
        View btnBoarding = findViewById(R.id.btn_bus_boarding);
        if (btnBoarding != null) {
            btnBoarding.setOnClickListener(v ->
                    showChoiceDialog("버스 승차 알림",
                            new String[]{"알림 받지 않음", "1분 전", "3분 전", "5분 전"}));
        }

        // 버스 하차 알림
        View btnAlighting = findViewById(R.id.btn_bus_alighting);
        if (btnAlighting != null) {
            btnAlighting.setOnClickListener(v ->
                    showChoiceDialog("버스 하차 알림",
                            new String[]{"알림 받지 않음", "1정거장 전", "2정거장 전", "3정거장 전"}));
        }

        // 자동 새로고침 주기
        View btnAutoRefresh = findViewById(R.id.btn_auto_refresh);
        if (btnAutoRefresh != null) {
            btnAutoRefresh.setOnClickListener(v ->
                    showChoiceDialog("자동 새로고침 주기",
                            new String[]{"자동 새로 고침 없음", "15초", "30초", "45초"}));
        }

        // 버스 도착정보 노출 기준
        View btnArrivalCriteria = findViewById(R.id.btn_arrival_display_criteria);
        if (btnArrivalCriteria != null) {
            btnArrivalCriteria.setOnClickListener(v ->
                    showChoiceDialog("버스 도착정보 노출 기준",
                            new String[]{"노선번호순", "도착시간순", "버스유형순"}));
        }

        // 버스 도착정보 텍스트 색상
        View btnArrivalTextColor = findViewById(R.id.btn_arrival_text_color);
        if (btnArrivalTextColor != null) {
            btnArrivalTextColor.setOnClickListener(v ->
                    showChoiceDialog("버스 도착정보 텍스트 색상",
                            new String[]{"파랑", "초록", "빨강", "검정", "보라", "핑크"}));
        }
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

        // 세션 정보 초기화
        sessionUserId = null;
        sessionEmail  = null;
        sessionName   = null;

        if (btnEditAccount != null) btnEditAccount.setEnabled(false);
    }

    private void showLoggedIn(ApiService.UserResponse me, String bearer) {
        sectionLogin.setVisibility(View.GONE);
        viewLoggedIn.setVisibility(View.VISIBLE);

        // 세션 정보 저장 (문의 화면 프리필용)
        sessionUserId = me.userid;
        sessionEmail  = me.email;
        sessionName   = !TextUtils.isEmpty(me.username) ? me.username : me.userid;

        textName.setText(sessionName);
        textPhone.setText(me.tel != null ? me.tel : "");
        textEmail.setText(sessionEmail != null ? sessionEmail : "");

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

    // ===== 현재 비밀번호 확인 다이얼로그 =====
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

        et.addTextChangedListener(new SimpleTextWatcher(() -> til.setError(null)));
        et.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) { btnOk.performClick(); return true; }
            return false;
        });

        // ❌ (삭제) 여기에서 btnTermsPrivacy 리스너를 등록하지 않습니다.

        btnOk.setOnClickListener(v -> {
            String pwd = et.getText() == null ? "" : et.getText().toString().trim();
            if (pwd.isEmpty()) { til.setError("비밀번호를 입력하세요"); return; }
            til.setError(null);
            btnOk.setEnabled(false);
            btnCancel.setEnabled(false);

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

    // ===== 공통 선택 다이얼로그 + 선택 처리 핸들러 =====
    private void showChoiceDialog(String title, String[] options) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(title);
        builder.setSingleChoiceItems(options, -1, (dialog, which) -> {
            String selected = options[which];
            Toast.makeText(this, title + ": " + selected, Toast.LENGTH_SHORT).show();
            dialog.dismiss();

            switch (title) {
                case "버스 승차 알림":
                    handleBusBoardingAlert(selected);
                    break;
                case "버스 하차 알림":
                    handleBusAlightingAlert(selected);
                    break;
                case "자동 새로고침 주기":
                    handleAutoRefresh(selected);
                    break;
                case "버스 도착정보 노출 기준":
                    handleArrivalDisplay(selected);
                    break;
                case "버스 도착정보 텍스트 색상":
                    handleTextColor(selected);
                    break;
            }
        });
        builder.setNegativeButton("취소", (dialog, which) -> dialog.dismiss());
        builder.show();
    }

    private void handleBusBoardingAlert(String option) {
        // TODO: 버스 승차 알림 기능 구현 시 저장/적용
    }

    private void handleBusAlightingAlert(String option) {
        // TODO: 버스 하차 알림 기능 구현 시 저장/적용
    }

    private void handleAutoRefresh(String option) {
        // TODO: 자동 새로고침 기능 구현 시 저장/적용
    }

    private void handleArrivalDisplay(String option) {
        // TODO: 필요 시 SharedPreferences에 저장
        // SharedPreferences prefs = getSharedPreferences("settings", MODE_PRIVATE);
        // prefs.edit().putString("arrival_display_criteria", option).apply();
        // Toast.makeText(this, "도착정보 정렬 기준이 \"" + option + "\"으로 설정되었습니다.", Toast.LENGTH_SHORT).show();
    }

    private void handleTextColor(String option) {
        int color = Color.BLACK;
        switch (option) {
            case "파랑": color = Color.BLUE; break;
            case "초록": color = Color.GREEN; break;
            case "빨강": color = Color.RED; break;
            case "보라": color = Color.MAGENTA; break;
            case "핑크": color = Color.rgb(255, 105, 180); break;
            case "검정": color = Color.BLACK; break;
        }

        // 색상값을 SharedPreferences에 저장
        SharedPreferences prefs = getSharedPreferences("settings", MODE_PRIVATE);
        prefs.edit().putInt("arrival_text_color", color).apply();

        Toast.makeText(this, "텍스트 색상이 변경되었습니다.", Toast.LENGTH_SHORT).show();
    }
}
