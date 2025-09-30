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

import org.json.JSONObject;

import java.io.IOException;
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
        btnEditAccount.setOnClickListener(v -> startActivity(new Intent(this, AccountEditActivity.class)));

        switchNotice.setOnCheckedChangeListener((b, checked) ->
                Toast.makeText(this, "공지사항 알림: " + (checked ? "ON" : "OFF"), Toast.LENGTH_SHORT).show()
        );
        // 팝업 띄울 > 버튼들 클릭 이벤트
        findViewById(R.id.btn_bus_boarding).setOnClickListener(v -> {
            showChoiceDialog("버스 승차 알림", new String[]{"알림 받지 않음", "1분 전", "3분 전", "5분 전"});
        });

        findViewById(R.id.btn_bus_alighting).setOnClickListener(v -> {
            showChoiceDialog("버스 하차 알림", new String[]{"알림 받지 않음", "1정거장 전", "2정거장 전", "3정거장 전"});
        });

        findViewById(R.id.btn_auto_refresh).setOnClickListener(v -> {
            showChoiceDialog("자동 새로고침 주기", new String[]{"자동 새로 고침 없음", "15초", "30초", "45초"});
        });

        findViewById(R.id.btn_arrival_display_criteria).setOnClickListener(v -> {
            showChoiceDialog("버스 도착정보 노출 기준", new String[]{"노선번호순", "도착시간순", "버스유형순"});
        });

        findViewById(R.id.btn_arrival_text_color).setOnClickListener(v -> {
            showChoiceDialog("버스 도착정보 텍스트 색상", new String[]{"파랑", "초록", "빨강", "검정", "보라", "핑크"});
        });

        findViewById(R.id.btn_app_inquiry).setOnClickListener(v -> {
            showChoiceDialog("어플리케이션 문의", new String[]{"이메일 보내기", "전화하기", "자주 묻는 질문"});
        });
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
        ApiClient.get().refresh(refreshToken, DeviceInfo.getClientType(), DeviceInfo.getDeviceId(this))
                .enqueue(new Callback<ApiService.AuthResponse>() {
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
    }

    private void showLoggedIn(ApiService.UserResponse me, String bearer) {
        sectionLogin.setVisibility(View.GONE);
        viewLoggedIn.setVisibility(View.VISIBLE);

        textName.setText(!TextUtils.isEmpty(me.username) ? me.username : me.userid);
        textPhone.setText(me.tel != null ? me.tel : "");
        textEmail.setText(me.email != null ? me.email : "");

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
                .setPositiveButton("탈퇴", null) // 클릭 리스너는 나중에 세팅해서 중복요청 방지
                .create();
        dialog.show();

        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String pw = et.getText().toString().trim();
            if (pw.isEmpty()) {
                Toast.makeText(this, "비밀번호를 입력하세요.", Toast.LENGTH_SHORT).show();
                return;
            }
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(false);
            requestDeleteAccount(pw, new Runnable() {
                @Override public void run() {
                    // 실패/성공 이후 버튼 다시 활성화(다이얼로그가 닫히지 않은 경우)
                    if (dialog.isShowing()) {
                        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(true);
                    }
                }
            }, new Runnable() {
                @Override public void run() {
                    // 성공 시 다이얼로그 닫기
                    if (dialog.isShowing()) dialog.dismiss();
                }
            });
        });
    }

    /**
     * @param onFinally  네트워크 종료 후(성공/실패 모두) 호출
     * @param onSuccess  성공 시 호출
     */
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

        // 1차 시도
        ApiClient.get().deleteMe(bearer, new ApiService.DeleteAccountRequest(currentPassword))
                .enqueue(new Callback<Map<String, Object>>() {
                    @Override public void onResponse(Call<Map<String, Object>> call, Response<Map<String, Object>> res) {
                        if (res.isSuccessful()) {
                            handleDeleteSuccess(onSuccess);
                        } else if (res.code() == 401) {
                            // 액세스 만료 → 리프레시 후 한 번만 재시도
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
                            // 재시도 (한 번만)
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
            // 서버가 reason 담아주면 표시
            try {
                ResponseBody eb = res.errorBody();
                if (eb != null) {
                    String raw = eb.string();
                    JSONObject obj = new JSONObject(raw);
                    String reason = obj.optString("reason", "");
                    switch (reason) {
                        case "BAD_CURRENT_PASSWORD":
                            message = "비밀번호가 올바르지 않습니다.";
                            break;
                        default:
                            // 추가 reason이 있다면 여기서 분기
                            message = "요청을 처리할 수 없습니다.";
                    }
                }
            } catch (Exception ignore) { /* 파싱 실패 시 기본 문구 */ }
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
