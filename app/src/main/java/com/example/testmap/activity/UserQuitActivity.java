package com.example.testmap.activity;

import android.app.Dialog;
import android.content.Intent;
import android.os.Bundle;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.testmap.R;
import com.example.testmap.service.ApiClient;
import com.example.testmap.service.ApiService;
import com.example.testmap.util.DeviceInfo;
import com.example.testmap.util.TokenStore;

import org.json.JSONObject;

import java.util.Map;

import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class UserQuitActivity extends AppCompatActivity {

    private Button buttonQuitSubmit;
    private ImageButton registerButtonBack;
    private Button buttonRegisterBack;
    private EditText editPw;
    private CheckBox checkAgree;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.user_quit);

        // 뷰 바인딩
        buttonQuitSubmit   = findViewById(R.id.buttonQuitSubmit);
        registerButtonBack = findViewById(R.id.registerButtonBack);
        buttonRegisterBack = findViewById(R.id.buttonRegisterBack);
        editPw             = findViewById(R.id.editTextPwConfirm);
        checkAgree         = findViewById(R.id.checkAgree);

        // 체크박스 상태에 따라 탈퇴 버튼 활성화
        setQuitEnabled(checkAgree.isChecked());
        checkAgree.setOnCheckedChangeListener((buttonView, isChecked) -> setQuitEnabled(isChecked));

        // 탈퇴 버튼
        buttonQuitSubmit.setOnClickListener(v -> {
            if (!checkAgree.isChecked()) { toast("탈퇴 안내를 모두 확인해 주세요."); return; }
            String pw = safe(editPw.getText());
            if (pw.isEmpty()) { toast("비밀번호를 입력해 주세요."); return; }
            showQuitConfirmPopup(pw);
        });

        // 뒤로가기 → 메인
        if (registerButtonBack != null) {
            registerButtonBack.setOnClickListener(v -> goMain());
        }
        if (buttonRegisterBack != null) {
            buttonRegisterBack.setOnClickListener(v -> goMain());
        }
    }

    private void setQuitEnabled(boolean enabled) {
        buttonQuitSubmit.setEnabled(enabled);
        buttonQuitSubmit.setAlpha(enabled ? 1f : 0.5f);
    }

    /** 탈퇴 확인 1단계 팝업 (확인 시 API 호출) */
    private void showQuitConfirmPopup(String password) {
        Dialog dialog = new Dialog(UserQuitActivity.this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(R.layout.popup_user_quit);

        Window window = dialog.getWindow();
        if (window != null) {
            window.setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.WRAP_CONTENT);
            window.setBackgroundDrawableResource(android.R.color.transparent);
        }

        Button btnCancel  = dialog.findViewById(R.id.cancel_button);
        Button btnConfirm = dialog.findViewById(R.id.bthOk);

        btnCancel.setOnClickListener(v -> dialog.dismiss());
        btnConfirm.setOnClickListener(v -> {
            dialog.dismiss();
            requestDeleteAccount(password, /*onFinally*/null, this::showQuitCompletePopup);
        });

        dialog.show();
    }

    /** 실제 회원 탈퇴 API 호출 (401 시 refresh 후 1회 재시도) */
    private void requestDeleteAccount(String password, Runnable onFinally, Runnable onSuccess) {
        setQuitEnabled(false);

        String at = TokenStore.getAccess(this);
        if (isEmpty(at)) {
            toast("세션이 만료되었습니다. 다시 로그인해 주세요.");
            goLogin();
            if (onFinally != null) onFinally.run();
            return;
        }
        String bearer = "Bearer " + at;

        ApiClient.get().deleteMe(bearer, new ApiService.DeleteAccountRequest(password))
                .enqueue(new Callback<Map<String, Object>>() {
                    @Override
                    public void onResponse(Call<Map<String, Object>> call, Response<Map<String, Object>> res) {
                        if (res.isSuccessful()) {
                            // 성공
                            try { TokenStore.clearAll(UserQuitActivity.this); } catch (Exception ignore) {}
                            if (onSuccess != null) onSuccess.run();
                            return;
                        }

                        if (res.code() == 401) {
                            // 액세스 만료 → refresh 시도 후 재호출
                            String rt = TokenStore.getRefresh(UserQuitActivity.this);
                            if (isEmpty(rt)) {
                                toast("세션이 만료되었습니다. 다시 로그인해 주세요.");
                                goLogin();
                                if (onFinally != null) onFinally.run();
                                return;
                            }
                            ApiClient.get().refresh(rt, DeviceInfo.getClientType(), DeviceInfo.getDeviceId(UserQuitActivity.this))
                                    .enqueue(new Callback<ApiService.AuthResponse>() {
                                        @Override public void onResponse(Call<ApiService.AuthResponse> c2, Response<ApiService.AuthResponse> r2) {
                                            if (r2.isSuccessful() && r2.body()!=null && !isEmpty(r2.body().accessToken)) {
                                                TokenStore.saveAccess(UserQuitActivity.this, r2.body().accessToken);
                                                if (!isEmpty(r2.body().refreshToken))
                                                    TokenStore.saveRefresh(UserQuitActivity.this, r2.body().refreshToken);
                                                // 새 토큰으로 다시 탈퇴 시도
                                                requestDeleteAccount(password, onFinally, onSuccess);
                                            } else {
                                                toast("세션이 만료되었습니다. 다시 로그인해 주세요.");
                                                goLogin();
                                                if (onFinally != null) onFinally.run();
                                            }
                                        }
                                        @Override public void onFailure(Call<ApiService.AuthResponse> c2, Throwable t2) {
                                            toast("네트워크 오류: " + t2.getMessage());
                                            setQuitEnabled(true);
                                            if (onFinally != null) onFinally.run();
                                        }
                                    });
                            return;
                        }

                        // 기타 오류 메시지 파싱
                        showDeleteErrorToast(res);
                        setQuitEnabled(true);
                        if (onFinally != null) onFinally.run();
                    }

                    @Override
                    public void onFailure(Call<Map<String, Object>> call, Throwable t) {
                        toast("네트워크 오류: " + t.getMessage());
                        setQuitEnabled(true);
                        if (onFinally != null) onFinally.run();
                    }
                });
    }

    /** 탈퇴 완료 2단계 팝업 → 로그인 화면으로 이동 */
    private void showQuitCompletePopup() {
        Dialog dialog = new Dialog(UserQuitActivity.this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(R.layout.popup_user_quit_com);

        Window window = dialog.getWindow();
        if (window != null) {
            window.setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.WRAP_CONTENT);
            window.setBackgroundDrawableResource(android.R.color.transparent);
        }

        Button btnConfirm = dialog.findViewById(R.id.bthOk);
        btnConfirm.setOnClickListener(v -> {
            dialog.dismiss();
            goLogin();
        });

        dialog.show();
    }

    /** 서버 응답 코드별 메시지 정리 */
    private void showDeleteErrorToast(Response<?> res) {
        String message = "탈퇴 실패 (" + res.code() + ")";
        try {
            if (res.code() == 400 || res.code() == 404) {
                // 400: BAD_CURRENT_PASSWORD 등 / 404: NOT_FOUND
                ResponseBody eb = res.errorBody();
                if (eb != null) {
                    String raw = eb.string();
                    JSONObject obj = new JSONObject(raw);
                    String reason = obj.optString("reason", "");
                    if ("BAD_CURRENT_PASSWORD".equals(reason)) message = "비밀번호가 올바르지 않습니다.";
                    else if ("NOT_FOUND".equals(reason))      message = "계정을 찾을 수 없습니다.";
                    else if (!isEmpty(reason))                message = "요청을 처리할 수 없습니다. [" + reason + "]";
                    else                                      message = "요청을 처리할 수 없습니다.";
                }
            } else if (res.code() == 403) {
                message = "권한이 없습니다.";
            } else if (res.code() == 409) {
                // FK 제약 등
                ResponseBody eb = res.errorBody();
                if (eb != null) {
                    String raw = eb.string();
                    JSONObject obj = new JSONObject(raw);
                    String reason = obj.optString("reason", "");
                    if ("FK_CONSTRAINT".equals(reason)) message = "연관 데이터가 있어 삭제할 수 없습니다.";
                    else message = "요청 충돌로 처리할 수 없습니다.";
                } else {
                    message = "요청 충돌로 처리할 수 없습니다.";
                }
            } else if (res.code() >= 500) {
                message = "서버 오류";
            }
        } catch (Exception ignore) {}
        toast(message);
    }

    private void goMain() {
        startActivity(new Intent(UserQuitActivity.this, MainActivity.class));
        finish();
    }

    private void goLogin() {
        Intent intent = new Intent(UserQuitActivity.this, LoginActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    private void toast(String s) { Toast.makeText(this, s, Toast.LENGTH_SHORT).show(); }
    private String safe(CharSequence cs) { return cs == null ? "" : cs.toString().trim(); }
    private boolean isEmpty(String s) { return s == null || s.isEmpty(); }
}
