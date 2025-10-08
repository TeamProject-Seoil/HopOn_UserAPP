package com.example.testmap.activity;

import android.content.Intent;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.*;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.example.testmap.R;
import com.example.testmap.service.ApiClient;
import com.example.testmap.service.ApiService;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class FindAccountActivity extends AppCompatActivity {

    private Button btnFindId, btnFindPw;
    private CountDownTimer timer;

    // 기본 도메인 + "직접 입력"
    private final List<String> defaultDomains = Arrays.asList(
            "naver.com", "gmail.com", "daum.net", "kakao.com", "outlook.com", "직접 입력"
    );

    private String verificationId = null;
    private String currentEmail = null;
    private String currentPurpose = null;

    // 루트 (탭)
    private View layoutFindId;
    private View layoutFindPw;

    // 동적 오버레이
    private FrameLayout frameContent;
    private View idResultView;   // layout_find_id_result
    private View pwResetView;    // layout_find_pw_result

    // 최근 성공한 사용자 정보
    private String lastFoundUserId;
    private String lastFoundName;
    private String lastFoundEmail;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.find_id);

        btnFindId = findViewById(R.id.btn_find_id);
        btnFindPw = findViewById(R.id.btn_find_pw);
        frameContent = findViewById(R.id.frame_content);

        layoutFindId = findViewById(R.id.layout_find_id_root);
        layoutFindPw = findViewById(R.id.layout_find_pw_root);

        ImageButton backButton = findViewById(R.id.notice_back_button);
        backButton.setOnClickListener(v -> finish());

        // 탭 전환
        btnFindId.setOnClickListener(v -> {
            removeOverlays();
            showFindId();
        });
        btnFindPw.setOnClickListener(v -> {
            removeOverlays();
            showFindPw();
        });

        // 기본: 아이디 찾기
        showFindId();

        setupFindIdForm();
        setupFindPwForm();
    }

    private void removeOverlays() {
        if (idResultView != null) {
            frameContent.removeView(idResultView);
            idResultView = null;
        }
        if (pwResetView != null) {
            frameContent.removeView(pwResetView);
            pwResetView = null;
        }
    }

    /** 아이디 찾기 탭 표시 */
    private void showFindId() {
        layoutFindId.setVisibility(View.VISIBLE);
        layoutFindPw.setVisibility(View.GONE);

        btnFindId.setBackgroundColor(ContextCompat.getColor(this, android.R.color.white));
        btnFindId.setTextColor(ContextCompat.getColor(this, android.R.color.black));
        btnFindPw.setBackgroundColor(ContextCompat.getColor(this, R.color.mainblue));
        btnFindPw.setTextColor(ContextCompat.getColor(this, android.R.color.white));

        frameContent.removeAllViews();
        View idView = View.inflate(this, R.layout.layout_find_id, frameContent);
        // 🔹 layout_find_pw.xml 내부 뷰 연결
        Button btnSendCode = idView.findViewById(R.id.btn_send_code);
        LinearLayout layoutCodeSection = idView.findViewById(R.id.layout_code_section); // 인증코드 입력 부분

        // 처음엔 안보이게 (XML에서 visibility="gone" 해놨을 가능성 있음)
        layoutCodeSection.setVisibility(View.GONE);

        // 인증요청 버튼 클릭 시 나타나게
        btnSendCode.setOnClickListener(v -> {
            layoutCodeSection.setVisibility(View.VISIBLE);
            layoutCodeSection.setAlpha(0f);
            layoutCodeSection.animate().alpha(1f).setDuration(300).start(); // 자연스러운 페이드인
        });
    }

    /** 비밀번호 찾기 탭 표시 */
    private void showFindPw() {
        layoutFindId.setVisibility(View.GONE);
        layoutFindPw.setVisibility(View.VISIBLE);

        btnFindPw.setBackgroundColor(ContextCompat.getColor(this, android.R.color.white));
        btnFindPw.setTextColor(ContextCompat.getColor(this, android.R.color.black));
        btnFindId.setBackgroundColor(ContextCompat.getColor(this, R.color.mainblue));
        btnFindId.setTextColor(ContextCompat.getColor(this, android.R.color.white));

        // 최근 아이디/이메일 채워넣기
        if (lastFoundUserId != null) {
            EditText etId = layoutFindPw.findViewById(R.id.et_id);
            etId.setText(lastFoundUserId);
        }
        if (lastFoundEmail != null) {
            int at = lastFoundEmail.indexOf('@');
            if (at > 0) {
                String id = lastFoundEmail.substring(0, at);
                String domain = lastFoundEmail.substring(at + 1);

                EditText etEmailId = layoutFindPw.findViewById(R.id.et_email_id);
                Spinner spinner = layoutFindPw.findViewById(R.id.spinner_email_domain);
                LinearLayout customWrapper = layoutFindPw.findViewById(R.id.custom_domain_wrapper);
                EditText etDomainCustom = layoutFindPw.findViewById(R.id.et_email_domain_custom);

                etEmailId.setText(id);
                ArrayAdapter<String> ad = new ArrayAdapter<>(this,
                        android.R.layout.simple_spinner_dropdown_item, defaultDomains);
                spinner.setAdapter(ad);

                int idx = defaultDomains.indexOf(domain);
                if (idx >= 0) {
                    // 기본 목록에 있는 도메인 → 스피너 사용
                    spinner.setVisibility(View.VISIBLE);
                    customWrapper.setVisibility(View.GONE);
                    spinner.setSelection(idx);
                } else {
                    // 기본 목록에 없음 → 직접입력 모드
                    spinner.setVisibility(View.GONE);
                    customWrapper.setVisibility(View.VISIBLE);
                    etDomainCustom.setText(domain);
                }
            }
        }
        frameContent.removeAllViews();
        View pwView = View.inflate(this, R.layout.layout_find_pw, frameContent);

        // 🔹 layout_find_pw.xml 내부 뷰 연결
        Button btnSendCode = pwView.findViewById(R.id.btn_send_code);
        LinearLayout layoutCodeSection = pwView.findViewById(R.id.layout_code_section); // 인증코드 입력 부분

        // 처음엔 안보이게 (XML에서 visibility="gone" 해놨을 가능성 있음)
        layoutCodeSection.setVisibility(View.GONE);

        // 인증요청 버튼 클릭 시 나타나게
        btnSendCode.setOnClickListener(v -> {
            layoutCodeSection.setVisibility(View.VISIBLE);
            layoutCodeSection.setAlpha(0f);
            layoutCodeSection.animate().alpha(1f).setDuration(300).start(); // 자연스러운 페이드인
        });
    }

    /** 아이디 찾기 폼 */
    private void setupFindIdForm() {
        Spinner spinner = layoutFindId.findViewById(R.id.spinner_email_domain);
        LinearLayout customWrapper = layoutFindId.findViewById(R.id.custom_domain_wrapper);
        EditText etDomainCustom = layoutFindId.findViewById(R.id.et_email_domain_custom);
        ImageButton btnBack = layoutFindId.findViewById(R.id.btn_back_to_spinner);

        // 스피너 어댑터
        spinner.setAdapter(new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_dropdown_item, defaultDomains));

        // "직접 입력" 선택하면 스피너 숨기고 커스텀 입력 보이기
        spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if ("직접 입력".equals(defaultDomains.get(position))) {
                    spinner.setVisibility(View.GONE);
                    customWrapper.setVisibility(View.VISIBLE);
                    etDomainCustom.requestFocus();
                }
            }
            @Override public void onNothingSelected(AdapterView<?> parent) {}
        });

        // X 버튼 → 스피너 복귀
        btnBack.setOnClickListener(v -> {
            customWrapper.setVisibility(View.GONE);
            spinner.setVisibility(View.VISIBLE);
            spinner.setSelection(0);
        });

        EditText etName = layoutFindId.findViewById(R.id.et_name);
        EditText etEmailId = layoutFindId.findViewById(R.id.et_email_id);
        EditText etCode = layoutFindId.findViewById(R.id.code_input);
        Button btnSendCode = layoutFindId.findViewById(R.id.btn_send_code);
        Button btnCheckCode = layoutFindId.findViewById(R.id.check_code);
        Button btnFindIdResult = layoutFindId.findViewById(R.id.btn_find_id_result);
        TextView textTimer = layoutFindId.findViewById(R.id.text_timer);

        btnFindIdResult.setEnabled(false);

        // 인증번호 발송
        btnSendCode.setOnClickListener(v -> {
            String name = etName.getText().toString().trim();
            String emailId = etEmailId.getText().toString().trim();

            // 현재 보이는 위젯 기준으로 도메인 결정
            String domain = (customWrapper.getVisibility() == View.VISIBLE)
                    ? etDomainCustom.getText().toString().trim()
                    : spinner.getSelectedItem().toString();

            if (name.isEmpty() || emailId.isEmpty() || domain.isEmpty()) {
                toast("이름과 이메일을 입력하세요");
                return;
            }
            currentEmail = emailId + "@" + domain;
            currentPurpose = "FIND_ID";

            ApiService.SendEmailCodeRequest req =
                    new ApiService.SendEmailCodeRequest(currentEmail, currentPurpose);
            ApiClient.get().sendEmail(req).enqueue(new Callback<Map<String,Object>>() {
                @Override public void onResponse(Call<Map<String,Object>> call, Response<Map<String,Object>> res) {
                    if (res.isSuccessful() && res.body() != null) {
                        verificationId = String.valueOf(res.body().get("verificationId"));
                        startTimer(textTimer, 10 * 60);
                        toast("인증코드를 " + currentEmail + " 로 전송했습니다.");
                    } else {
                        toast("전송 실패(" + res.code() + ")");
                    }
                }
                @Override public void onFailure(Call<Map<String,Object>> call, Throwable t) { toast("네트워크 오류"); }
            });
        });

        // 인증코드 확인
        btnCheckCode.setOnClickListener(v -> {
            String code = etCode.getText().toString().trim();
            if (code.isEmpty()) { toast("인증코드를 입력하세요"); return; }
            if (verificationId == null) { toast("먼저 인증코드를 발송하세요"); return; }

            ApiService.VerifyEmailCodeRequest req =
                    new ApiService.VerifyEmailCodeRequest(verificationId, currentEmail, currentPurpose, code);
            ApiClient.get().verifyEmail(req).enqueue(new Callback<Map<String,Object>>() {
                @Override public void onResponse(Call<Map<String,Object>> call, Response<Map<String,Object>> res) {
                    if (res.isSuccessful() && res.body() != null) {
                        btnFindIdResult.setEnabled(true);
                        toast("인증 성공!");
                    } else {
                        toast("인증 실패!");
                    }
                }
                @Override public void onFailure(Call<Map<String,Object>> call, Throwable t) { toast("네트워크 오류"); }
            });
        });

        // 아이디 찾기 결과 요청
        btnFindIdResult.setOnClickListener(v -> {
            String name = etName.getText().toString().trim();
            if (name.isEmpty() || currentEmail == null || verificationId == null) {
                toast("이름과 이메일을 입력하세요");
                return;
            }

            Map<String, Object> body = new HashMap<>();
            body.put("username", name);
            body.put("email", currentEmail);
            body.put("verificationId", verificationId);

            ApiClient.get().findIdAfterVerify(body).enqueue(new Callback<Map<String,Object>>() {
                @Override public void onResponse(Call<Map<String,Object>> call, Response<Map<String,Object>> res) {
                    if (res.isSuccessful() && res.body() != null && Boolean.TRUE.equals(res.body().get("ok"))) {
                        // 결과 저장
                        lastFoundUserId = (String) res.body().get("userid");
                        lastFoundName = name;
                        lastFoundEmail = currentEmail;

                        // 결과 오버레이 표시
                        layoutFindId.setVisibility(View.GONE);
                        idResultView = getLayoutInflater().inflate(R.layout.layout_find_id_result, frameContent, false);

                        ((TextView) idResultView.findViewById(R.id.tv_result_id)).setText(lastFoundUserId);
                        ((TextView) idResultView.findViewById(R.id.tv_result_name)).setText(lastFoundName);
                        ((TextView) idResultView.findViewById(R.id.tv_result_email)).setText(lastFoundEmail);

                        idResultView.findViewById(R.id.btn_find_pw).setOnClickListener(view -> {
                            removeOverlays();
                            showFindPw();
                            prefillFindPwFromLast();
                        });

                        idResultView.findViewById(R.id.btn_go_login).setOnClickListener(view -> {
                            startActivity(new Intent(FindAccountActivity.this, LoginActivity.class));
                            finish();
                        });

                        frameContent.addView(idResultView);
                    } else {
                        toast("회원 정보가 일치하지 않습니다.");
                    }
                }
                @Override public void onFailure(Call<Map<String,Object>> call, Throwable t) { toast("네트워크 오류"); }
            });
        });
    }

    /** 이전 결과로 비번 찾기 입력 채우기 */
    private void prefillFindPwFromLast() {
        if (lastFoundUserId != null) {
            EditText etId = layoutFindPw.findViewById(R.id.et_id);
            etId.setText(lastFoundUserId);
        }
        if (lastFoundEmail != null) {
            int at = lastFoundEmail.indexOf('@');
            if (at > 0) {
                String id = lastFoundEmail.substring(0, at);
                String domain = lastFoundEmail.substring(at + 1);

                EditText etEmailId = layoutFindPw.findViewById(R.id.et_email_id);
                Spinner spinner = layoutFindPw.findViewById(R.id.spinner_email_domain);
                LinearLayout customWrapper = layoutFindPw.findViewById(R.id.custom_domain_wrapper);
                EditText etDomainCustom = layoutFindPw.findViewById(R.id.et_email_domain_custom);

                etEmailId.setText(id);
                ArrayAdapter<String> ad = new ArrayAdapter<>(this,
                        android.R.layout.simple_spinner_dropdown_item, defaultDomains);
                spinner.setAdapter(ad);

                int idx = defaultDomains.indexOf(domain);
                if (idx >= 0) {
                    spinner.setVisibility(View.VISIBLE);
                    customWrapper.setVisibility(View.GONE);
                    spinner.setSelection(idx);
                } else {
                    spinner.setVisibility(View.GONE);
                    customWrapper.setVisibility(View.VISIBLE);
                    etDomainCustom.setText(domain);
                }
            }
        }
    }

    /** 비밀번호 찾기 폼 */
    private void setupFindPwForm() {
        Spinner spinner = layoutFindPw.findViewById(R.id.spinner_email_domain);
        LinearLayout customWrapper = layoutFindPw.findViewById(R.id.custom_domain_wrapper);
        EditText etDomainCustom = layoutFindPw.findViewById(R.id.et_email_domain_custom);
        ImageButton btnBack = layoutFindPw.findViewById(R.id.btn_back_to_spinner);

        spinner.setAdapter(new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_dropdown_item, defaultDomains));

        spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if ("직접 입력".equals(defaultDomains.get(position))) {
                    spinner.setVisibility(View.GONE);
                    customWrapper.setVisibility(View.VISIBLE);
                    etDomainCustom.requestFocus();
                }
            }
            @Override public void onNothingSelected(AdapterView<?> parent) {}
        });

        btnBack.setOnClickListener(v -> {
            customWrapper.setVisibility(View.GONE);
            spinner.setVisibility(View.VISIBLE);
            spinner.setSelection(0);
        });

        EditText etId = layoutFindPw.findViewById(R.id.et_id);
        EditText etEmailId = layoutFindPw.findViewById(R.id.et_email_id);
        EditText etCode = layoutFindPw.findViewById(R.id.code_input);
        Button btnSendCode = layoutFindPw.findViewById(R.id.btn_send_code);
        Button btnCheckCode = layoutFindPw.findViewById(R.id.check_code);
        Button btnFindPwNext = layoutFindPw.findViewById(R.id.btn_find_pw_next);
        TextView textTimer = layoutFindPw.findViewById(R.id.text_timer);

        btnFindPwNext.setEnabled(false);

        // 인증번호 발송
        btnSendCode.setOnClickListener(v -> {
            String userId = etId.getText().toString().trim();
            String emailId = etEmailId.getText().toString().trim();
            String domain = (customWrapper.getVisibility() == View.VISIBLE)
                    ? etDomainCustom.getText().toString().trim()
                    : spinner.getSelectedItem().toString();

            if (userId.isEmpty() || emailId.isEmpty() || domain.isEmpty()) {
                toast("아이디와 이메일을 입력하세요");
                return;
            }
            currentEmail = emailId + "@" + domain;
            currentPurpose = "FIND_PW";

            ApiService.SendEmailCodeRequest req =
                    new ApiService.SendEmailCodeRequest(currentEmail, currentPurpose);
            ApiClient.get().sendEmail(req).enqueue(new Callback<Map<String,Object>>() {
                @Override public void onResponse(Call<Map<String,Object>> call, Response<Map<String,Object>> res) {
                    if (res.isSuccessful() && res.body() != null) {
                        verificationId = String.valueOf(res.body().get("verificationId"));
                        startTimer(textTimer, 10 * 60);
                        toast("인증코드를 " + currentEmail + " 로 전송했습니다.");
                    } else {
                        toast("전송 실패(" + res.code() + ")");
                    }
                }
                @Override public void onFailure(Call<Map<String,Object>> call, Throwable t) { toast("네트워크 오류"); }
            });
        });

        // 인증코드 확인
        btnCheckCode.setOnClickListener(v -> {
            String code = etCode.getText().toString().trim();
            if (code.isEmpty()) { toast("인증코드를 입력하세요"); return; }
            if (verificationId == null) { toast("먼저 인증코드를 발송하세요"); return; }

            ApiService.VerifyEmailCodeRequest req =
                    new ApiService.VerifyEmailCodeRequest(verificationId, currentEmail, currentPurpose, code);
            ApiClient.get().verifyEmail(req).enqueue(new Callback<Map<String,Object>>() {
                @Override public void onResponse(Call<Map<String,Object>> call, Response<Map<String,Object>> res) {
                    if (res.isSuccessful() && res.body() != null) {
                        btnFindPwNext.setEnabled(true);
                        toast("인증 성공!");
                    } else {
                        toast("인증 실패!");
                    }
                }
                @Override public void onFailure(Call<Map<String,Object>> call, Throwable t) { toast("네트워크 오류"); }
            });
        });

        // 새 비번 설정 화면으로
        btnFindPwNext.setOnClickListener(v -> {
            String userId = etId.getText().toString().trim();
            if (userId.isEmpty() || currentEmail == null || verificationId == null) {
                toast("아이디와 이메일 인증을 완료하세요");
                return;
            }

            Map<String, Object> body = new HashMap<>();
            body.put("userid", userId);
            body.put("email", currentEmail);
            body.put("verificationId", verificationId);

            ApiClient.get().verifyPwUser(body).enqueue(new Callback<Map<String,Object>>() {
                @Override public void onResponse(Call<Map<String,Object>> call, Response<Map<String,Object>> res) {
                    if (res.isSuccessful() && res.body() != null && Boolean.TRUE.equals(res.body().get("ok"))) {
                        lastFoundUserId = userId;
                        lastFoundEmail = currentEmail;
                        showPwReset(userId);
                    } else {
                        toast("회원 정보가 일치하지 않습니다.");
                    }
                }
                @Override public void onFailure(Call<Map<String,Object>> call, Throwable t) { toast("네트워크 오류"); }
            });
        });
    }

    /** 비밀번호 재설정 화면 (실시간 검증) */
    private void showPwReset(String userId) {
        layoutFindPw.setVisibility(View.GONE);
        removeOverlays();
        pwResetView = getLayoutInflater().inflate(R.layout.layout_find_pw_result, frameContent, false);

        EditText etNewPw = pwResetView.findViewById(R.id.et_new_pw);
        EditText etConfirmPw = pwResetView.findViewById(R.id.et_confirm_pw);
        TextView tvMismatch = pwResetView.findViewById(R.id.tv_pw_mismatch);

        TextView tvRuleLen = pwResetView.findViewById(R.id.tv_pw_rule_length);
        TextView tvRuleMix = pwResetView.findViewById(R.id.tv_pw_rule_mix);
        TextView tvRuleSeq = pwResetView.findViewById(R.id.tv_pw_rule_sequence);

        Button btnCancel = pwResetView.findViewById(R.id.btn_cancel_pw);
        Button btnConfirm = pwResetView.findViewById(R.id.btn_confirm_pw);
        btnConfirm.setEnabled(false);

        final int RED = ContextCompat.getColor(this, android.R.color.holo_red_dark);
        final int GREEN = ContextCompat.getColor(this, android.R.color.holo_green_dark);

        TextWatcher watcher = new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int st, int c, int a) {}
            @Override public void onTextChanged(CharSequence s, int st, int b, int c) {}
            @Override public void afterTextChanged(Editable s) {
                String pw = etNewPw.getText().toString();
                String confirm = etConfirmPw.getText().toString();

                boolean lenOk = pw.length() >= 10 && pw.length() <= 16 && pw.matches("^[A-Za-z0-9]+$");
                boolean mixOk = hasAtLeastTwoClasses(pw);
                boolean seqOk = !(hasSequentialAlphaOrDigit(pw) || hasKeyboardSequence(pw));

                tvRuleLen.setTextColor(lenOk ? GREEN : RED);
                tvRuleMix.setTextColor(mixOk ? GREEN : RED);
                tvRuleSeq.setTextColor(seqOk ? GREEN : RED);

                boolean allPolicyOk = lenOk && mixOk && seqOk;
                boolean match = !confirm.isEmpty() && pw.equals(confirm);

                tvMismatch.setVisibility(match ? View.GONE : View.VISIBLE);
                btnConfirm.setEnabled(allPolicyOk && match);
            }
        };
        etNewPw.addTextChangedListener(watcher);
        etConfirmPw.addTextChangedListener(watcher);

        btnCancel.setOnClickListener(v -> {
            removeOverlays();
            showFindPw();
        });

        btnConfirm.setOnClickListener(v -> {
            String newPw = etNewPw.getText().toString().trim();
            String confirmPw = etConfirmPw.getText().toString().trim();

            if (newPw.isEmpty() || confirmPw.isEmpty()) {
                toast("비밀번호를 입력하세요");
                return;
            }
            if (!newPw.equals(confirmPw)) {
                tvMismatch.setVisibility(View.VISIBLE);
                return;
            }
            if (userId.isEmpty() || currentEmail == null || verificationId == null) {
                toast("회원 정보를 확인하세요");
                return;
            }

            Map<String, Object> body = new HashMap<>();
            body.put("userid", userId);
            body.put("email", currentEmail);
            body.put("verificationId", verificationId);
            body.put("newPassword", newPw);

            ApiClient.get().resetPasswordAfterVerify(body).enqueue(new Callback<Map<String,Object>>() {
                @Override public void onResponse(Call<Map<String,Object>> call, Response<Map<String,Object>> res) {
                    if (res.isSuccessful() && res.body() != null && Boolean.TRUE.equals(res.body().get("ok"))) {
                        AlertDialog.Builder builder = new AlertDialog.Builder(FindAccountActivity.this);
                        View dialogView = getLayoutInflater().inflate(R.layout.layout_pw_popup_result, null);
                        builder.setView(dialogView);

                        TextView tvMsg = dialogView.findViewById(R.id.tv_result_message);
                        if (tvMsg != null) {
                            String displayName = (lastFoundName != null && !lastFoundName.isBlank())
                                    ? lastFoundName : (lastFoundUserId != null ? lastFoundUserId : "");
                            if (!displayName.isEmpty()) {
                                tvMsg.setText(displayName + "님의 비밀번호가 변경되었습니다");
                            } else {
                                tvMsg.setText("비밀번호가 변경되었습니다");
                            }
                        }

                        AlertDialog dialog = builder.create();
                        dialog.setCancelable(false);

                        dialogView.findViewById(R.id.btn_go_login).setOnClickListener(view -> {
                            startActivity(new Intent(FindAccountActivity.this, LoginActivity.class));
                            finish();
                            dialog.dismiss();
                        });

                        dialog.show();
                    } else {
                        toast("비밀번호 변경 실패");
                    }
                }
                @Override public void onFailure(Call<Map<String,Object>> call, Throwable t) { toast("네트워크 오류"); }
            });
        });

        frameContent.addView(pwResetView);
    }

    private void startTimer(TextView textTimer, int seconds) {
        if (timer != null) timer.cancel();
        timer = new CountDownTimer(seconds * 1000L, 1000) {
            @Override public void onTick(long millisUntilFinished) {
                long min = millisUntilFinished / 1000 / 60;
                long sec = (millisUntilFinished / 1000) % 60;
                textTimer.setText(String.format(Locale.KOREA, "⏱ %02d:%02d", min, sec));
            }
            @Override public void onFinish() {
                textTimer.setText("시간 초과. 다시 요청하세요.");
            }
        }.start();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (timer != null) timer.cancel();
    }

    private void toast(String m) {
        Toast.makeText(this, m, Toast.LENGTH_SHORT).show();
    }

    // ===== 비밀번호 정책 검증 헬퍼 =====
    private boolean hasAtLeastTwoClasses(String s) {
        boolean upper = s.chars().anyMatch(c -> c >= 'A' && c <= 'Z');
        boolean lower = s.chars().anyMatch(c -> c >= 'a' && c <= 'z');
        boolean digit = s.chars().anyMatch(c -> c >= '0' && c <= '9');
        int classes = (upper?1:0) + (lower?1:0) + (digit?1:0);
        return classes >= 2;
    }

    private boolean hasSequentialAlphaOrDigit(String s) {
        if (s == null || s.length() < 3) return false;
        for (int i = 0; i <= s.length() - 3; i++) {
            char a = s.charAt(i), b = s.charAt(i+1), c = s.charAt(i+2);
            if (Character.isDigit(a) && Character.isDigit(b) && Character.isDigit(c)) {
                if ((b==a+1 && c==b+1) || (b==a-1 && c==b-1)) return true;
            }
            if (Character.isLetter(a) && Character.isLetter(b) && Character.isLetter(c)) {
                int x = Character.toLowerCase(a), y = Character.toLowerCase(b), z = Character.toLowerCase(c);
                if ((y==x+1 && z==y+1) || (y==x-1 && z==y-1)) return true;
            }
        }
        return false;
    }

    private boolean hasKeyboardSequence(String s) {
        if (s == null) return false;
        String lower = s.toLowerCase();
        String[] rows = new String[]{
                "qwertyuiop","asdfghjkl","zxcvbnm","1234567890","0987654321"
        };
        for (String row : rows) {
            for (int i = 0; i <= row.length() - 3; i++) {
                if (lower.contains(row.substring(i, i+3))) return true;
            }
            String rev = new StringBuilder(row).reverse().toString();
            for (int i = 0; i <= rev.length() - 3; i++) {
                if (lower.contains(rev.substring(i, i+3))) return true;
            }
        }
        return false;
    }
}
