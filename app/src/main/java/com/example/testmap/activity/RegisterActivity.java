package com.example.testmap.activity;

import android.content.Intent;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.View;
import android.widget.*;

import androidx.appcompat.app.AppCompatActivity;

import com.example.testmap.R;
import com.example.testmap.service.ApiClient;
import com.example.testmap.service.ApiService;

import org.json.JSONObject;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.RequestBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * 회원가입 Activity
 * - 아이디 중복확인
 * - 이메일 인증 (타이머 표시)
 * - 비밀번호 유효성 검사 + 제약조건 안내
 * - 이메일 도메인 직접 입력 기능
 */
public class RegisterActivity extends AppCompatActivity {

    // UI 컴포넌트
    private ImageButton registerButtonBack, btnBackToSpinner;
    private Button buttonSubmitRegister, buttonCheckId;
    private Button btnSendCode, btnVerifyCode;
    private EditText codeInput;

    private EditText editId, editPw, editPwConfirm, editName, editEmail, editPhone;
    private Spinner spinnerDomain;
    private EditText editDomainCustom; // 직접 입력용
    private LinearLayout customDomainWrapper; // 직접 입력 영역 wrapper
    private TextView textPasswordHint, textEmailTimer;

    private static final String CLIENT_TYPE = "USER_APP";

    // 이메일 도메인 기본 리스트
    private final List<String> defaultDomains = Arrays.asList(
            "gmail.com", "naver.com", "daum.net", "kakao.com", "outlook.com", "icloud.com", "직접 입력"
    );

    // 이메일 인증 상태 저장
    private String lastVerifiedEmail = null;
    private String lastVerificationId = null;
    private CountDownTimer emailTimer;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_register);

        bindViews();

        // ✅ 스피너 초기화
        ArrayAdapter<String> domainAdapter = new ArrayAdapter<>(
                this,
                android.R.layout.simple_spinner_dropdown_item,
                defaultDomains
        );
        spinnerDomain.setAdapter(domainAdapter);

        // "직접 입력" 선택 시 스피너 숨기고 입력칸 표시
        spinnerDomain.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> parent, View view, int pos, long id) {
                String selected = spinnerDomain.getSelectedItem().toString();
                if ("직접 입력".equals(selected)) {
                    spinnerDomain.setVisibility(View.GONE);
                    customDomainWrapper.setVisibility(View.VISIBLE);
                    editDomainCustom.requestFocus();
                }
            }
            @Override public void onNothingSelected(AdapterView<?> parent) {}
        });

        // ❌ 버튼 눌렀을 때 다시 스피너로 전환
        btnBackToSpinner.setOnClickListener(v -> {
            editDomainCustom.setText("");
            customDomainWrapper.setVisibility(View.GONE);
            spinnerDomain.setVisibility(View.VISIBLE);
            spinnerDomain.setSelection(0); // 기본값 선택
        });

        setupPasswordWatcher();
        setupClicks();
    }

    /** View 바인딩 */
    private void bindViews() {
        registerButtonBack = findViewById(R.id.registerButtonBack);
        btnBackToSpinner   = findViewById(R.id.btn_back_to_spinner);

        editId             = findViewById(R.id.editTextId);
        editPw             = findViewById(R.id.editTextPw);
        editPwConfirm      = findViewById(R.id.editTextPwConfirm);
        editName           = findViewById(R.id.editTextName);
        editEmail          = findViewById(R.id.editTextEmail);
        editPhone          = findViewById(R.id.editTextPhone);
        spinnerDomain      = findViewById(R.id.spinner_email_domain);

        customDomainWrapper= findViewById(R.id.custom_domain_wrapper);
        editDomainCustom   = findViewById(R.id.editTextDomainCustom);

        //textPasswordHint   = findViewById(R.id.textPasswordHint);
        textEmailTimer     = findViewById(R.id.textEmailTimer);

        buttonSubmitRegister = findViewById(R.id.buttonSubmitRegister);
        buttonCheckId        = findViewById(R.id.buttonCheckId);
        btnSendCode          = findViewById(R.id.btn_send_code);
        btnVerifyCode        = findViewById(R.id.btn_verify_code);
        codeInput            = findViewById(R.id.code_input);
    }

    /** 클릭 이벤트 설정 */
    private void setupClicks() {
        registerButtonBack.setOnClickListener(v -> finish());
        buttonCheckId.setOnClickListener(v -> doCheckDuplicate());
        btnSendCode.setOnClickListener(v -> sendVerificationEmail());
        btnVerifyCode.setOnClickListener(v -> verifyEmailCode());

        buttonSubmitRegister.setOnClickListener(v -> {
            String email = buildEmail();
            if (TextUtils.isEmpty(email)) {
                toast("이메일을 입력하세요");
                return;
            }
            if (!TextUtils.equals(email, lastVerifiedEmail)) {
                toast("이메일 인증을 완료하세요");
                return;
            }
            doRegisterInternal();
        });
    }

    /** 이메일 조립 (스피너 or 직접 입력) */
    private String buildEmail() {
        String emailId = s(editEmail);
        String domain;
        if (spinnerDomain.getVisibility() == View.VISIBLE &&
                "직접 입력".equals(spinnerDomain.getSelectedItem().toString())) {
            domain = s(editDomainCustom);
        } else if (spinnerDomain.getVisibility() == View.VISIBLE) {
            domain = spinnerDomain.getSelectedItem() != null ? spinnerDomain.getSelectedItem().toString() : "";
        } else {
            domain = s(editDomainCustom);
        }
        return (TextUtils.isEmpty(emailId) || TextUtils.isEmpty(domain)) ? "" : (emailId + "@" + domain);
    }

    /** 비밀번호 유효성 검사 */
    private void setupPasswordWatcher() {
        buttonSubmitRegister.setEnabled(false);

        TextWatcher pwWatcher = new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override public void afterTextChanged(Editable s) { validatePasswordsAndToggleButton(); }
        };

        editPw.addTextChangedListener(pwWatcher);
        editPwConfirm.addTextChangedListener(pwWatcher);
    }

    private void validatePasswordsAndToggleButton() {
        String pw  = s(editPw);
        String pw2 = s(editPwConfirm);

        if (TextUtils.isEmpty(pw) || TextUtils.isEmpty(pw2)) {
            buttonSubmitRegister.setEnabled(false);
            textPasswordHint.setText("비밀번호를 입력하세요.");
            textPasswordHint.setTextColor(getColor(android.R.color.holo_red_dark));
            return;
        }
        if (!pw.equals(pw2)) {
            buttonSubmitRegister.setEnabled(false);
            textPasswordHint.setText("❌ 비밀번호가 일치하지 않습니다.");
            textPasswordHint.setTextColor(getColor(android.R.color.holo_red_dark));
            return;
        }
        if (pw.length() < 10 || pw.length() > 16) {
            buttonSubmitRegister.setEnabled(false);
            textPasswordHint.setText("❌ 비밀번호는 10~16자여야 합니다.");
            textPasswordHint.setTextColor(getColor(android.R.color.holo_red_dark));
            return;
        }
        if (!pw.matches("^[A-Za-z0-9]+$")) {
            buttonSubmitRegister.setEnabled(false);
            textPasswordHint.setText("❌ 영문과 숫자만 사용 가능합니다.");
            textPasswordHint.setTextColor(getColor(android.R.color.holo_red_dark));
            return;
        }

        buttonSubmitRegister.setEnabled(true);
        textPasswordHint.setText("✅ 사용 가능한 비밀번호입니다.");
        textPasswordHint.setTextColor(getColor(android.R.color.holo_green_dark));
    }

    /** 아이디/이메일 중복 확인 */
    private void doCheckDuplicate() {
        String userid  = s(editId);
        String email   = buildEmail();

        ApiClient.get().checkDup(TextUtils.isEmpty(userid) ? null : userid, email)
                .enqueue(new Callback<ApiService.CheckResponse>() {
                    @Override
                    public void onResponse(Call<ApiService.CheckResponse> call, Response<ApiService.CheckResponse> res) {
                        if (!res.isSuccessful() || res.body() == null) {
                            toast("중복확인 실패(" + res.code() + ")");
                            return;
                        }
                        ApiService.CheckResponse b = res.body();
                        if (!TextUtils.isEmpty(userid) && b.useridTaken)
                            editId.setError("이미 사용 중인 아이디입니다");
                        if (!TextUtils.isEmpty(email) && b.emailTaken)
                            toast("이미 사용 중인 이메일입니다");
                        if (!b.useridTaken && !b.emailTaken)
                            toast("사용 가능한 아이디/이메일입니다.");
                    }
                    @Override public void onFailure(Call<ApiService.CheckResponse> call, Throwable t) {
                        toast("네트워크 오류: " + t.getMessage());
                    }
                });
    }

    /** 인증 메일 발송 */
    private void sendVerificationEmail() {
        String email = buildEmail();
        if (TextUtils.isEmpty(email)) {
            toast("이메일을 입력하세요");
            return;
        }

        ApiService.SendEmailCodeRequest req = new ApiService.SendEmailCodeRequest(email, "REGISTER");
        ApiClient.get().sendEmail(req).enqueue(new Callback<Map<String, Object>>() {
            @Override public void onResponse(Call<Map<String, Object>> call, Response<Map<String, Object>> res) {
                if (res.isSuccessful() && res.body() != null) {
                    lastVerificationId = (String) res.body().get("verificationId");
                    toast("인증코드를 전송했습니다.");
                    startEmailTimer(10);
                } else {
                    toast("코드 전송 실패(" + res.code() + ")");
                }
            }
            @Override public void onFailure(Call<Map<String, Object>> call, Throwable t) {
                toast("네트워크 오류: " + t.getMessage());
            }
        });
    }

    /** 인증 코드 확인 */
    private void verifyEmailCode() {
        String code = codeInput.getText().toString().trim();
        String email = buildEmail();

        if (TextUtils.isEmpty(code)) {
            toast("코드를 입력하세요");
            return;
        }
        if (TextUtils.isEmpty(lastVerificationId)) {
            toast("먼저 인증 메일을 발송하세요");
            return;
        }

        ApiService.VerifyEmailCodeRequest req =
                new ApiService.VerifyEmailCodeRequest(lastVerificationId, email, "REGISTER", code);

        ApiClient.get().verifyEmail(req).enqueue(new Callback<Map<String, Object>>() {
            @Override public void onResponse(Call<Map<String, Object>> call, Response<Map<String, Object>> res) {
                if (res.isSuccessful()) {
                    lastVerifiedEmail = email;
                    if (emailTimer != null) emailTimer.cancel();
                    textEmailTimer.setText("✅ 인증 완료");
                    toast("이메일 인증 성공");
                } else toast("인증 실패(" + res.code() + ")");
            }
            @Override public void onFailure(Call<Map<String, Object>> call, Throwable t) {
                toast("네트워크 오류: " + t.getMessage());
            }
        });
    }

    /** 실제 회원가입 진행 */
    private void doRegisterInternal() {
        try {
            JSONObject data = new JSONObject();
            data.put("userid", s(editId));
            data.put("password", s(editPw));
            data.put("email", buildEmail());
            data.put("username", s(editName));
            data.put("tel", s(editPhone));
            data.put("clientType", CLIENT_TYPE);
            data.put("verificationId", lastVerificationId);

            RequestBody dataJson = RequestBody.create(
                    data.toString(),
                    MediaType.parse("application/json; charset=utf-8")
            );

            ApiClient.get().register(dataJson, (MultipartBody.Part) null)
                    .enqueue(new Callback<ApiService.RegisterResponse>() {
                        @Override public void onResponse(Call<ApiService.RegisterResponse> call, Response<ApiService.RegisterResponse> res) {
                            if (res.isSuccessful() && res.body() != null && res.body().ok) {
                                startActivity(new Intent(RegisterActivity.this, MembershipComplete.class));
                                finish();
                            } else toast("회원가입 실패(" + res.code() + ")");
                        }
                        @Override public void onFailure(Call<ApiService.RegisterResponse> call, Throwable t) {
                            toast("네트워크 오류: " + t.getMessage());
                        }
                    });

        } catch (Exception e) {
            toast("요청 생성 오류: " + e.getMessage());
        }
    }

    /** 인증 타이머 */
    private void startEmailTimer(int minutes) {
        if (emailTimer != null) emailTimer.cancel();
        emailTimer = new CountDownTimer(minutes * 60 * 1000, 1000) {
            public void onTick(long millisUntilFinished) {
                long sec = millisUntilFinished / 1000;
                textEmailTimer.setText("인증 유효 시간: " + (sec / 60) + "분 " + (sec % 60) + "초 남음");
            }
            public void onFinish() {
                textEmailTimer.setText("⏰ 인증 시간이 만료되었습니다.");
            }
        }.start();
    }

    // ===== 유틸 =====
    private String s(EditText e) { return e.getText() == null ? "" : e.getText().toString().trim(); }
    private void toast(String m) { Toast.makeText(this, m, Toast.LENGTH_SHORT).show(); }
}
