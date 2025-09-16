package com.example.testmap.activity;

import android.content.Intent;
import android.os.Bundle;
import android.provider.Settings;
import android.text.TextUtils;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.TextView;            // ← 추가
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.testmap.R;
import com.example.testmap.service.ApiClient;
import com.example.testmap.service.ApiService;
import com.example.testmap.util.TokenStore;
import com.google.android.material.button.MaterialButton;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class LoginActivity extends AppCompatActivity {

    private ImageButton loginButtonBack;
    private EditText editId, editPw;
    private CheckBox cbAuto;
    private MaterialButton btnLogin;
    private TextView textSignup;         // ← 추가

    private static final String CLIENT_TYPE = "USER_APP";

    private String deviceId() {
        return Settings.Secure.getString(getContentResolver(), Settings.Secure.ANDROID_ID);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        loginButtonBack = findViewById(R.id.loginButtonBack);
        editId   = findViewById(R.id.editTextId);
        editPw   = findViewById(R.id.editTextPassword);
        cbAuto   = findViewById(R.id.checkbox_auto_login);
        btnLogin = findViewById(R.id.buttonLogin);
        textSignup = findViewById(R.id.text_signup);   // ← 추가 (레이아웃의 회원가입 TextView)

        // 뒤로가기 → 메인으로
        loginButtonBack.setOnClickListener(v -> {
            Intent intent = new Intent(LoginActivity.this, MainActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            startActivity(intent);
        });

        // 회원가입 화면으로 이동  ← 추가
        textSignup.setOnClickListener(v -> {
            Intent intent = new Intent(LoginActivity.this, RegisterActivity.class);
            startActivity(intent);
        });

        // 앱 실행 시 자동 로그인 시도
        tryAutoLoginIfPossible();

        // 로그인 버튼 → 실제 로그인 호출
        btnLogin.setOnClickListener(v -> doLogin());
    }

    private void tryAutoLoginIfPossible() {
        String savedRefresh = TokenStore.getRefresh(this);
        if (savedRefresh == null) return;

        ApiService api = ApiClient.get();
        api.refresh(savedRefresh, CLIENT_TYPE, deviceId())
                .enqueue(new Callback<ApiService.AuthResponse>() {
                    @Override public void onResponse(Call<ApiService.AuthResponse> call, Response<ApiService.AuthResponse> res) {
                        if (res.isSuccessful() && res.body() != null) {
                            ApiService.AuthResponse a = res.body();
                            TokenStore.saveAccess(LoginActivity.this, a.accessToken);
                            TokenStore.saveRefresh(LoginActivity.this, a.refreshToken); // 회전된 값 갱신
                            goMain();
                        } else {
                            // 실패 시 저장 토큰 정리
                            TokenStore.clearAccess(LoginActivity.this);
                        }
                    }
                    @Override public void onFailure(Call<ApiService.AuthResponse> call, Throwable t) {
                        // 네트워크 오류는 조용히 무시(로그인 화면 유지)
                    }
                });
    }

    private void doLogin() {
        String id = editId.getText().toString().trim();
        String pw = editPw.getText().toString().trim();
        if (TextUtils.isEmpty(id) || TextUtils.isEmpty(pw)) {
            Toast.makeText(this, "아이디/비밀번호를 입력하세요", Toast.LENGTH_SHORT).show();
            return;
        }

        ApiService api = ApiClient.get();
        ApiService.AuthRequest body = new ApiService.AuthRequest(id, pw, CLIENT_TYPE, deviceId());
        api.login(body).enqueue(new Callback<ApiService.AuthResponse>() {
            @Override public void onResponse(Call<ApiService.AuthResponse> call, Response<ApiService.AuthResponse> res) {
                if (res.isSuccessful() && res.body() != null) {
                    ApiService.AuthResponse a = res.body();
                    // access는 보통 메모리/임시 저장 → 여기서는 편의상 보관
                    TokenStore.saveAccess(LoginActivity.this, a.accessToken);

                    if (cbAuto.isChecked()) {
                        TokenStore.saveRefresh(LoginActivity.this, a.refreshToken);
                    } else {
                        TokenStore.clearRefresh(LoginActivity.this);
                    }
                    goMain();
                } else if (res.code() == 401) {
                    Toast.makeText(LoginActivity.this, "아이디 또는 비밀번호가 올바르지 않습니다.", Toast.LENGTH_SHORT).show();
                } else if (res.code() == 409) {
                    Toast.makeText(LoginActivity.this, "다른 기기에서 로그인 중입니다.", Toast.LENGTH_SHORT).show();
                } else if (res.code() == 403) {
                    Toast.makeText(LoginActivity.this, "앱 권한이 없습니다.", Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(LoginActivity.this, "로그인 실패(" + res.code() + ")", Toast.LENGTH_SHORT).show();
                }
            }
            @Override public void onFailure(Call<ApiService.AuthResponse> call, Throwable t) {
                Toast.makeText(LoginActivity.this, "네트워크 오류", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void goMain() {
        startActivity(new Intent(this, MainActivity.class));
        finish();
    }
}
