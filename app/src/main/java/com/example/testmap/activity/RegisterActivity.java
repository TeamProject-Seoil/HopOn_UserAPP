package com.example.testmap.activity;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.widget.*;

import androidx.appcompat.app.AppCompatActivity;

import com.example.testmap.R;
import com.example.testmap.service.ApiClient;
import com.example.testmap.service.ApiService;

import org.json.JSONObject;

import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.RequestBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class RegisterActivity extends AppCompatActivity {

    ImageButton registerButtonBack;
    Button buttonSubmitRegister;
    Button buttonCheckId; // ← 중복확인 버튼

    private EditText editId, editPw, editPwConfirm, editName, editEmail, editPhone;
    private Spinner spinnerDomain;

    private static final String CLIENT_TYPE = "USER_APP";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_register);

        // 뒤로가기
        registerButtonBack = findViewById(R.id.registerButtonBack);
        registerButtonBack.setOnClickListener(v -> finish());

        // 필드 바인딩
        editId        = findViewById(R.id.editTextId);
        editPw        = findViewById(R.id.editTextPw);
        editPwConfirm = findViewById(R.id.editTextPwConfirm);
        editName      = findViewById(R.id.editTextName);
        editEmail     = findViewById(R.id.editTextEmail);
        editPhone     = findViewById(R.id.editTextPhone);
        spinnerDomain = findViewById(R.id.spinner_email_domain);

        // 버튼 바인딩
        buttonSubmitRegister = findViewById(R.id.buttonSubmitRegister);
        buttonCheckId        = findViewById(R.id.buttonCheckId);

        // (신규) 실시간 비밀번호 일치/강도 검사 + 버튼 활성/비활성
        buttonSubmitRegister.setEnabled(false); // 초기엔 잠가둠
        TextWatcher pwWatcher = new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override public void afterTextChanged(Editable s) { validatePasswordsAndToggleButton(); }
        };
        editPw.addTextChangedListener(pwWatcher);
        editPwConfirm.addTextChangedListener(pwWatcher);

        // 중복확인 버튼 동작
        buttonCheckId.setOnClickListener(v -> doCheckDuplicate());

        // 회원가입 버튼
        buttonSubmitRegister.setOnClickListener(v -> doRegister());
    }

    /** 비밀번호/확인란 실시간 검증 + 버튼 활성/비활성 제어 */
    private void validatePasswordsAndToggleButton() {
        String pw  = s(editPw);
        String pw2 = s(editPwConfirm);

        // 에러 초기화
        editPw.setError(null);
        editPwConfirm.setError(null);

        // 입력 전부 없거나 하나라도 비어있으면 버튼 비활성화
        if (TextUtils.isEmpty(pw) || TextUtils.isEmpty(pw2)) {
            buttonSubmitRegister.setEnabled(false);
            return;
        }

        // 일치 검사
        if (!pw.equals(pw2)) {
            editPwConfirm.setError("비밀번호가 일치하지 않습니다");
            buttonSubmitRegister.setEnabled(false);
            return;
        }

        // (선택) 간단 강도 검사 — 필요 없으면 이 블록 삭제 가능
        if (pw.length() < 8) {
            editPw.setError("8자 이상으로 설정하세요");
            buttonSubmitRegister.setEnabled(false);
            return;
        }
        if (!pw.matches(".*[A-Za-z].*") || !pw.matches(".*\\d.*")) {
            editPw.setError("영문과 숫자를 모두 포함하세요");
            buttonSubmitRegister.setEnabled(false);
            return;
        }
        if (pw.contains(" ")) {
            editPw.setError("공백은 포함할 수 없습니다");
            buttonSubmitRegister.setEnabled(false);
            return;
        }

        // 여기까지 통과하면 버튼 활성화
        buttonSubmitRegister.setEnabled(true);
    }

    private void doCheckDuplicate() {
        String userid  = s(editId);
        String emailId = s(editEmail);
        String domain  = spinnerDomain.getSelectedItem() != null ? spinnerDomain.getSelectedItem().toString() : "";
        String email   = (TextUtils.isEmpty(emailId) || TextUtils.isEmpty(domain)) ? null : (emailId + "@" + domain);

        if (TextUtils.isEmpty(userid) && email == null) {
            toast("아이디나 이메일 중 하나는 입력하세요");
            return;
        }

        ApiService api = ApiClient.get();
        api.checkDup(TextUtils.isEmpty(userid) ? null : userid, email)
                .enqueue(new Callback<ApiService.CheckResponse>() {
                    @Override
                    public void onResponse(Call<ApiService.CheckResponse> call, Response<ApiService.CheckResponse> res) {
                        if (!res.isSuccessful() || res.body() == null) {
                            toast("중복확인 실패(" + res.code() + ")");
                            return;
                        }
                        ApiService.CheckResponse b = res.body();
                        boolean okId = !b.useridTaken;
                        boolean okEmail = !b.emailTaken;

                        if (!TextUtils.isEmpty(userid)) {
                            if (okId) {
                                editId.setError(null);
                                toast("사용 가능한 아이디입니다");
                            } else {
                                editId.setError("이미 사용 중인 아이디입니다");
                            }
                        }
                        if (email != null) {
                            if (okEmail) {
                                editEmail.setError(null);
                                toast("사용 가능한 이메일입니다");
                            } else {
                                editEmail.setError("이미 사용 중인 이메일입니다");
                            }
                        }
                    }
                    @Override
                    public void onFailure(Call<ApiService.CheckResponse> call, Throwable t) {
                        toast("네트워크 오류: " + t.getMessage());
                    }
                });
    }

    private void doRegister() {
        String userid   = s(editId);
        String pw       = s(editPw);
        String pw2      = s(editPwConfirm);
        String name     = s(editName);
        String emailId  = s(editEmail);
        String domain   = spinnerDomain.getSelectedItem() != null ? spinnerDomain.getSelectedItem().toString() : "";
        String email    = emailId + "@" + domain;
        String tel      = s(editPhone); // 선택사항

        // 필수 항목
        if (empty(userid, pw, pw2, name, emailId, domain)) {
            toast("필수 항목을 입력하세요");
            return;
        }
        // 이중 방어
        if (!pw.equals(pw2)) {
            toast("비밀번호가 일치하지 않습니다");
            return;
        }

        try {
            JSONObject data = new JSONObject();
            data.put("userid", userid);
            data.put("password", pw);
            data.put("email", email);
            data.put("username", name);
            data.put("tel", tel);
            data.put("clientType", CLIENT_TYPE);

            RequestBody dataJson = RequestBody.create(
                    data.toString(),
                    MediaType.parse("application/json; charset=utf-8")
            );

            MultipartBody.Part filePart = null; // 프로필 이미지 없음

            ApiService api = ApiClient.get();
            api.register(dataJson, filePart).enqueue(new Callback<ApiService.RegisterResponse>() {
                @Override public void onResponse(Call<ApiService.RegisterResponse> call, Response<ApiService.RegisterResponse> res) {
                    if (res.isSuccessful() && res.body() != null && res.body().ok) {
                        toast("회원가입 완료: " + res.body().userid);
                        startActivity(new Intent(RegisterActivity.this, MembershipComplete.class));
                        finish();
                    } else if (res.code() == 409 && res.body() != null) {
                        if ("DUPLICATE_USERID".equals(res.body().reason)) {
                            editId.setError("이미 사용 중인 아이디입니다");
                            toast("이미 사용 중인 아이디입니다");
                        } else if ("DUPLICATE_EMAIL".equals(res.body().reason)) {
                            editEmail.setError("이미 사용 중인 이메일입니다");
                            toast("이미 사용 중인 이메일입니다");
                        } else {
                            toast("회원가입 실패(중복): " + res.body().reason);
                        }
                    } else {
                        toast("회원가입 실패(" + res.code() + ")");
                    }
                }
                @Override public void onFailure(Call<ApiService.RegisterResponse> call, Throwable t) {
                    toast("네트워크 오류: " + t.getMessage());
                }
            });

        } catch (Exception e) {
            toast("요청 생성 오류: " + e.getMessage());
        }
    }

    private String s(EditText e) { return e.getText() == null ? "" : e.getText().toString().trim(); }
    private boolean empty(String...v) { for (String s : v) if (TextUtils.isEmpty(s)) return true; return false; }
    private void toast(String m) { Toast.makeText(this, m, Toast.LENGTH_SHORT).show(); }
}
