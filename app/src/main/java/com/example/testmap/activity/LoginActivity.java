package com.example.testmap.activity;

import android.content.Intent;
import android.os.Bundle;
import android.provider.Settings;
import android.text.TextUtils;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.Button;

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
    private TextView textSignup;
    private Button buttonFindIdPw;

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
        textSignup = findViewById(R.id.text_signup);
        buttonFindIdPw = findViewById(R.id.button_find_idpw);

        loginButtonBack.setOnClickListener(v -> {
            Intent intent = new Intent(LoginActivity.this, MainActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            startActivity(intent);
        });

        textSignup.setOnClickListener(v -> {
            Intent intent = new Intent(LoginActivity.this, RegisterActivity.class);
            startActivity(intent);
        });

        buttonFindIdPw.setOnClickListener(v -> {
            Intent intent = new Intent(LoginActivity.this, FindAccountActivity.class);
            startActivity(intent);
        });

        tryAutoLoginIfPossible();

        btnLogin.setOnClickListener(v -> doLogin());
    }

    private void tryAutoLoginIfPossible() {
        // 자동로그인은 '영구 refresh'가 있을 때만 시도
        String savedRefresh = TokenStore.getRefresh(this);
        if (savedRefresh == null) return;

        ApiService api = ApiClient.get();
        api.refresh(savedRefresh, CLIENT_TYPE, deviceId())
                .enqueue(new Callback<ApiService.AuthResponse>() {
                    @Override public void onResponse(Call<ApiService.AuthResponse> call, Response<ApiService.AuthResponse> res) {
                        if (res.isSuccessful() && res.body() != null) {
                            ApiService.AuthResponse a = res.body();

                            // access: 메모리
                            TokenStore.saveAccess(LoginActivity.this, a.accessToken);

                            // refresh: 메모리에도 세팅
                            if (!TextUtils.isEmpty(a.refreshToken)) {
                                TokenStore.setRefreshVolatile(a.refreshToken);
                                // 자동로그인 경로이므로 영구 refresh도 회전 반영
                                TokenStore.saveRefresh(LoginActivity.this, a.refreshToken);
                            }

                            goMain();
                        } else {
                            // 실패 시 access만 정리 (영구 refresh는 사용자가 자동로그인 체크했던 값이라 남겨둠)
                            TokenStore.clearAccess(LoginActivity.this);
                        }
                    }
                    @Override public void onFailure(Call<ApiService.AuthResponse> call, Throwable t) { }
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

                    // access: 메모리
                    TokenStore.saveAccess(LoginActivity.this, a.accessToken);

                    // refresh: 항상 메모리에는 보관(실행 중 조용한 갱신용)
                    TokenStore.setRefreshVolatile(a.refreshToken);

                    if (cbAuto.isChecked()) {
                        // 자동로그인 ON → 영구 보관
                        TokenStore.saveRefresh(LoginActivity.this, a.refreshToken);
                    } else {
                        // 자동로그인 OFF → 영구 보관 지움(재시작 시 로그인 화면)
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
