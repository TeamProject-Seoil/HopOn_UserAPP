package com.example.testmap.activity;

import android.content.Intent;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.View;
import android.widget.*;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

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
 * - 아이디/이메일 중복확인
 * - 이메일 인증 (타이머 표시)
 * - 비밀번호 제약 3가지 실시간 색상 표시(초록/빨강) + 확인 일치/불일치 메시지
 * - 입력 끊김 방지(디바운스)
 */
public class RegisterActivity extends AppCompatActivity {

    // UI
    private ImageButton registerButtonBack, btnBackToSpinner;
    private Button buttonSubmitRegister, buttonCheckId;
    private Button btnSendCode, btnVerifyCode;
    private EditText codeInput;

    private EditText editId, editPw, editPwConfirm, editName, editEmail, editPhone;
    private Spinner spinnerDomain;
    private EditText editDomainCustom;
    private LinearLayout customDomainWrapper;

    private TextView textPasswordHint1, textPasswordHint2, textPasswordHint3; // 제약 3줄
    private TextView textPwMatch, textEmailTimer; // 일치여부 / 이메일 타이머

    private static final String CLIENT_TYPE = "USER_APP";

    private final List<String> defaultDomains = Arrays.asList(
            "gmail.com", "naver.com", "daum.net", "kakao.com", "outlook.com", "icloud.com", "직접 입력"
    );

    private String lastVerifiedEmail = null;
    private String lastVerificationId = null;
    private CountDownTimer emailTimer;

    // 색상 캐시
    private int colorRed, colorGreen, colorGray;
    private boolean lastSubmitEnabled = false;

    // 디바운스
    private final Handler pwHandler = new Handler(Looper.getMainLooper());
    private static final long PW_DEBOUNCE_MS = 120L;
    private final Runnable pwValidateRunnable = this::validatePasswordsAndToggleButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_register);

        bindViews();
        cacheColors();
        initSpinner();
        setupPasswordWatcher();
        setupClicks();
        setSubmitEnabled(false); // 초기 비활성화
        resetHintsToGray();      // 초기 회색
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (emailTimer != null) {
            emailTimer.cancel();
            emailTimer = null;
        }
        pwHandler.removeCallbacksAndMessages(null);
    }

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

        textPasswordHint1  = findViewById(R.id.textPasswordHint1);
        textPasswordHint2  = findViewById(R.id.textPasswordHint2);
        textPasswordHint3  = findViewById(R.id.textPasswordHint3);
        textPwMatch        = findViewById(R.id.textPwMatch);
        textEmailTimer     = findViewById(R.id.textEmailTimer);

        buttonSubmitRegister = findViewById(R.id.buttonSubmitRegister);
        buttonCheckId        = findViewById(R.id.buttonCheckId);
        btnSendCode          = findViewById(R.id.btn_send_code);
        btnVerifyCode        = findViewById(R.id.btn_verify_code);
        codeInput            = findViewById(R.id.code_input);
    }

    private void cacheColors() {
        colorRed   = ContextCompat.getColor(this, android.R.color.holo_red_dark);
        colorGreen = ContextCompat.getColor(this, android.R.color.holo_green_dark);
        colorGray  = 0xFF666666; // 초기 안내색
    }

    private void initSpinner() {
        ArrayAdapter<String> domainAdapter = new ArrayAdapter<>(
                this, android.R.layout.simple_spinner_dropdown_item, defaultDomains);
        spinnerDomain.setAdapter(domainAdapter);

        spinnerDomain.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> parent, View view, int pos, long id) {
                String selected = spinnerDomain.getSelectedItem() != null
                        ? spinnerDomain.getSelectedItem().toString() : "";
                boolean isCustom = "직접 입력".equals(selected);
                spinnerDomain.setVisibility(isCustom ? View.GONE : View.VISIBLE);
                customDomainWrapper.setVisibility(isCustom ? View.VISIBLE : View.GONE);
                if (isCustom) editDomainCustom.requestFocus();
            }
            @Override public void onNothingSelected(AdapterView<?> parent) {}
        });

        btnBackToSpinner.setOnClickListener(v -> {
            editDomainCustom.setText("");
            customDomainWrapper.setVisibility(View.GONE);
            spinnerDomain.setVisibility(View.VISIBLE);
            spinnerDomain.setSelection(0);
        });
    }

    private void setupClicks() {
        registerButtonBack.setOnClickListener(v -> finish());
        buttonCheckId.setOnClickListener(v -> doCheckDuplicate());
        btnSendCode.setOnClickListener(v -> sendVerificationEmail());
        btnVerifyCode.setOnClickListener(v -> verifyEmailCode());

        buttonSubmitRegister.setOnClickListener(v -> {
            String email = buildEmail();
            if (TextUtils.isEmpty(email)) { toast("이메일을 입력하세요"); return; }
            if (!TextUtils.equals(email, lastVerifiedEmail)) { toast("이메일 인증을 완료하세요"); return; }
            doRegisterInternal();
        });
    }

    /** 초기 힌트 회색으로 */
    private void resetHintsToGray() {
        textPasswordHint1.setTextColor(colorGray);
        textPasswordHint2.setTextColor(colorGray);
        textPasswordHint3.setTextColor(colorGray);
        textPwMatch.setText("");
        textPwMatch.setTextColor(colorGray);
    }

    /** 비밀번호 워처(디바운스) */
    private void setupPasswordWatcher() {
        TextWatcher pwWatcher = new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                pwHandler.removeCallbacks(pwValidateRunnable);
                pwHandler.postDelayed(pwValidateRunnable, PW_DEBOUNCE_MS);
            }
            @Override public void afterTextChanged(Editable s) {}
        };
        editPw.addTextChangedListener(pwWatcher);
        editPwConfirm.addTextChangedListener(pwWatcher);
    }

    /** 제약 3개 색상 업데이트 + 일치/불일치 메시지 + 버튼 활성화 */
    private void validatePasswordsAndToggleButton() {
        String pw  = get(editPw);
        String pw2 = get(editPwConfirm);

        boolean lenOk    = pw.length() >= 10 && pw.length() <= 16 && pw.matches("^[A-Za-z0-9]+$");
        boolean comboOk  = hasAtLeastTwoKinds(pw);                     // 대/소/숫자 2종류 이상
        boolean noSeqOk  = !hasSequentialRun(pw, 3);                   // 3자리 이상 연속/역순 금지
        boolean matchOk  = !pw.isEmpty() && pw.equals(pw2);

        // 제약 3줄 색상 갱신
        textPasswordHint1.setTextColor(lenOk   ? colorGreen : colorRed);
        textPasswordHint2.setTextColor(comboOk ? colorGreen : colorRed);
        textPasswordHint3.setTextColor(noSeqOk ? colorGreen : colorRed);

        // 일치/불일치 메시지 (둘 다 표시)
        if (pw2.isEmpty()) {
            textPwMatch.setText("");
            textPwMatch.setTextColor(colorGray);
        } else if (matchOk) {
            textPwMatch.setText("✅ 비밀번호가 일치합니다.");
            textPwMatch.setTextColor(colorGreen);
        } else {
            textPwMatch.setText("❌ 비밀번호가 일치하지 않습니다.");
            textPwMatch.setTextColor(colorRed);
        }

        setSubmitEnabled(lenOk && comboOk && noSeqOk && matchOk);
    }

    private void setSubmitEnabled(boolean enabled) {
        if (lastSubmitEnabled == enabled) return;
        buttonSubmitRegister.setEnabled(enabled);
        buttonSubmitRegister.setAlpha(enabled ? 1f : 0.5f);
        lastSubmitEnabled = enabled;
    }

    /** 종류 2개 이상 포함 (대문자/소문자/숫자) */
    private boolean hasAtLeastTwoKinds(String s) {
        if (TextUtils.isEmpty(s)) return false;
        boolean up = false, lo = false, dg = false;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c >= 'A' && c <= 'Z') up = true;
            else if (c >= 'a' && c <= 'z') lo = true;
            else if (c >= '0' && c <= '9') dg = true;
            if ((up && lo) || (up && dg) || (lo && dg)) return true;
        }
        return false;
    }

    /** 3자리 이상 연속/역순(문자 또는 숫자) 여부 */
    private boolean hasSequentialRun(String s, int run) {
        if (s == null || s.length() < run) return false;
        for (int i = 0; i <= s.length() - run; i++) {
            boolean asc = true, desc = true;
            for (int j = 1; j < run; j++) {
                char prev = s.charAt(i + j - 1);
                char curr = s.charAt(i + j);
                // 같은 타입(둘다 숫자 or 둘다 문자)만 연속으로 판단
                boolean bothDigit = Character.isDigit(prev) && Character.isDigit(curr);
                boolean bothAlpha = Character.isLetter(prev) && Character.isLetter(curr);
                if (!(bothDigit || bothAlpha)) { asc = desc = false; break; }
                if (curr - prev != 1) asc = false;
                if (prev - curr != 1) desc = false;
                if (!asc && !desc) break;
            }
            if (asc || desc) return true;
        }
        return false;
    }

    /** 이메일 조립 */
    private String buildEmail() {
        String emailId = get(editEmail);
        String domain;
        if (spinnerDomain.getVisibility() == View.VISIBLE) {
            String selected = spinnerDomain.getSelectedItem() != null
                    ? spinnerDomain.getSelectedItem().toString() : "";
            domain = "직접 입력".equals(selected) ? get(editDomainCustom) : selected;
        } else {
            domain = get(editDomainCustom);
        }
        return (TextUtils.isEmpty(emailId) || TextUtils.isEmpty(domain)) ? "" : (emailId + "@" + domain);
    }

    /** 아이디/이메일 중복 확인 */
    private void doCheckDuplicate() {
        String userid  = get(editId);
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
        if (TextUtils.isEmpty(email)) { toast("이메일을 입력하세요"); return; }

        ApiService.SendEmailCodeRequest req = new ApiService.SendEmailCodeRequest(email, "REGISTER");
        ApiClient.get().sendEmail(req).enqueue(new Callback<Map<String, Object>>() {
            @Override public void onResponse(Call<Map<String, Object>> call, Response<Map<String, Object>> res) {
                if (res.isSuccessful() && res.body() != null) {
                    Object vid = res.body().get("verificationId");
                    lastVerificationId = vid instanceof String ? (String) vid : null;
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
        String code = get(codeInput);
        String email = buildEmail();

        if (TextUtils.isEmpty(code)) { toast("코드를 입력하세요"); return; }
        if (TextUtils.isEmpty(lastVerificationId)) { toast("먼저 인증 메일을 발송하세요"); return; }

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

    /** 회원가입 요청 */
    private void doRegisterInternal() {
        try {
            JSONObject data = new JSONObject();
            data.put("userid", get(editId));
            data.put("password", get(editPw));
            data.put("email", buildEmail());
            data.put("username", get(editName));
            data.put("tel", get(editPhone));
            data.put("clientType", CLIENT_TYPE);
            data.put("verificationId", lastVerificationId);

            RequestBody dataJson = RequestBody.create(
                    data.toString(), MediaType.parse("application/json; charset=utf-8"));

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
        emailTimer = new CountDownTimer(minutes * 60 * 1000L, 1000L) {
            public void onTick(long millisUntilFinished) {
                long sec = millisUntilFinished / 1000L;
                textEmailTimer.setText("인증 유효 시간: " + (sec / 60) + "분 " + (sec % 60) + "초 남음");
            }
            public void onFinish() {
                textEmailTimer.setText("⏰ 인증 시간이 만료되었습니다.");
            }
        }.start();
    }

    // 유틸
    private String get(EditText e) { return e.getText() == null ? "" : e.getText().toString().trim(); }
    private void toast(String m) { Toast.makeText(this, m, Toast.LENGTH_SHORT).show(); }
}
