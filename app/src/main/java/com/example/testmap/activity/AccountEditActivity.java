package com.example.testmap.activity;

import android.content.ContentResolver;
import android.content.Intent;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.provider.Settings;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.widget.*;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.example.testmap.R;
import com.example.testmap.service.ApiClient;
import com.example.testmap.service.ApiService;
import com.example.testmap.util.TokenStore;
import com.google.gson.Gson;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.*;

import okhttp3.*;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/** 계정 정보 수정 화면 */
public class AccountEditActivity extends AppCompatActivity {

    private static final String CLIENT_TYPE = "USER_APP";

    private ImageButton backButton, btnBackToSpinner;
    private ImageView profileImage;
    private EditText passwordEdit, passwordConfirmEdit, nameEdit, emailIdEdit, phoneEdit, editDomainCustom;
    private Spinner emailDomainSpinner;
    private LinearLayout customDomainWrapper;
    private Button updateButton, btnSendCode, btnVerifyCode;
    private TextView textTimer;

    // ✅ 비밀번호 규칙/상태 뷰 (색상 토글)
    private TextView pwRule1, pwRule2, pwRule3, pwMatchStatus;

    private final List<String> defaultDomains = new ArrayList<>();
    private ArrayAdapter<String> domainAdapter;

    // 상태값
    private Uri pickedImageUri = null;
    private boolean removeProfileImage = false;
    private String lastVerifiedEmail = null;
    private String lastVerificationId = null;
    private CountDownTimer timer; // ⏱ 타이머

    private ApiService.UserResponse me;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_account_edit);

        bindViews();
        setupDomainSpinner();
        setupClicks();
        setupPasswordWatcher(); // ✅ 비밀번호 정책/일치 표시

        // 이름은 변경하지 않음
        if (nameEdit != null) {
            nameEdit.setEnabled(false);         // UI 비활성화
            nameEdit.setFocusable(false);
        }

        String at = TokenStore.getAccess(this);
        if (TextUtils.isEmpty(at)) {
            tryRefreshThenLoad();
        } else {
            loadMe("Bearer " + at, true);
        }
    }

    private void bindViews() {
        backButton   = findViewById(R.id.account_back_button);
        profileImage = findViewById(R.id.profile_image);

        passwordEdit        = findViewById(R.id.edit_password);
        passwordConfirmEdit = findViewById(R.id.edit_password_confirm);
        nameEdit            = findViewById(R.id.edit_name);
        emailIdEdit         = findViewById(R.id.edit_email_id);
        phoneEdit           = findViewById(R.id.edit_phone);

        emailDomainSpinner  = findViewById(R.id.spinner_email_domain);
        customDomainWrapper = findViewById(R.id.custom_domain_wrapper);
        editDomainCustom    = findViewById(R.id.editTextDomainCustom);
        btnBackToSpinner    = findViewById(R.id.btn_back_to_spinner);

        updateButton        = findViewById(R.id.btn_update);
        btnSendCode         = findViewById(R.id.btn_send_code);
        btnVerifyCode       = findViewById(R.id.btn_verify_code);
        textTimer           = findViewById(R.id.text_timer);

        // ✅ 규칙/상태 텍스트뷰
        pwRule1        = findViewById(R.id.password_rules1);
        pwRule2        = findViewById(R.id.password_rules2);
        pwRule3        = findViewById(R.id.password_rules3);
        pwMatchStatus  = findViewById(R.id.tv_pw_match_status); // XML 패치에 추가됨
    }

    private void setupDomainSpinner() {
        defaultDomains.clear();
        Collections.addAll(defaultDomains,
                "gmail.com","naver.com","daum.net","kakao.com","outlook.com","직접 입력");

        domainAdapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_dropdown_item, defaultDomains);
        emailDomainSpinner.setAdapter(domainAdapter);

        emailDomainSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> parent, android.view.View view, int position, long id) {
                String selected = defaultDomains.get(position);
                if ("직접 입력".equals(selected)) {
                    emailDomainSpinner.setVisibility(android.view.View.GONE);
                    customDomainWrapper.setVisibility(android.view.View.VISIBLE);
                }
            }
            @Override public void onNothingSelected(AdapterView<?> parent) {}
        });

        btnBackToSpinner.setOnClickListener(v -> {
            customDomainWrapper.setVisibility(android.view.View.GONE);
            emailDomainSpinner.setVisibility(android.view.View.VISIBLE);
            emailDomainSpinner.setSelection(0); // 기본값
        });
    }

    private void pickImage() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("image/*");
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        intent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
        intent.addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        startActivityForResult(intent, 1001);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == 1001 && resultCode == RESULT_OK && data != null) {
            Uri uri = data.getData();
            if (uri != null) {
                int takeFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION;
                if ((data.getFlags() & Intent.FLAG_GRANT_WRITE_URI_PERMISSION) != 0) {
                    takeFlags |= Intent.FLAG_GRANT_WRITE_URI_PERMISSION;
                }
                try {
                    getContentResolver().takePersistableUriPermission(uri, takeFlags);
                } catch (SecurityException ignore) {}

                pickedImageUri = uri;
                try (InputStream is = getContentResolver().openInputStream(uri)) {
                    profileImage.setImageBitmap(BitmapFactory.decodeStream(is));
                } catch (Exception e) {
                    toast("이미지 로드 실패");
                }
            }
        }
    }

    private void setupClicks() {
        backButton.setOnClickListener(v -> finish());

        profileImage.setOnClickListener(v -> pickImage());

        profileImage.setOnLongClickListener(v -> {
            removeProfileImage = !removeProfileImage;
            pickedImageUri = null;
            if (removeProfileImage) {
                profileImage.setImageResource(R.drawable.ic_profile_placeholder);
                toast("프로필 이미지를 삭제합니다.");
            } else {
                String at = TokenStore.getAccess(this);
                if (!TextUtils.isEmpty(at) && me != null && me.hasProfileImage) {
                    loadProfileImage("Bearer " + at);
                }
            }
            return true;
        });

        btnSendCode.setOnClickListener(v -> sendVerificationEmail());
        btnVerifyCode.setOnClickListener(v -> verifyEmailCode());

        updateButton.setOnClickListener(v -> onClickUpdate());
    }

    /** ================= 인증 메일 ================= */

    private void sendVerificationEmail() {
        String email = buildEmail();
        if (TextUtils.isEmpty(email)) {
            toast("이메일을 입력하세요");
            return;
        }

        ApiService.SendEmailCodeRequest req = new ApiService.SendEmailCodeRequest(email, "CHANGE_EMAIL");
        ApiClient.get().sendEmail(req).enqueue(new Callback<Map<String,Object>>() {
            @Override public void onResponse(Call<Map<String,Object>> call, Response<Map<String,Object>> res) {
                if (res.isSuccessful() && res.body()!=null) {
                    Object vId = res.body().get("verificationId");
                    if (vId != null) lastVerificationId = vId.toString();
                    toast("인증코드를 전송했습니다.");
                    startTimer(10 * 60); // ⏱ 10분 제한
                } else toast("전송 실패(" + res.code() + ")");
            }
            @Override public void onFailure(Call<Map<String,Object>> call, Throwable t) {
                toast("네트워크 오류");
            }
        });
    }

    private void verifyEmailCode() {
        String code = ((EditText)findViewById(R.id.code_input)).getText().toString().trim();
        String email = buildEmail();

        if (TextUtils.isEmpty(code)) { toast("코드를 입력하세요"); return; }
        if (TextUtils.isEmpty(lastVerificationId)) { toast("먼저 메일 발송하세요"); return; }

        ApiService.VerifyEmailCodeRequest req =
                new ApiService.VerifyEmailCodeRequest(lastVerificationId, email, "CHANGE_EMAIL", code);
        ApiClient.get().verifyEmail(req).enqueue(new Callback<Map<String,Object>>() {
            @Override public void onResponse(Call<Map<String,Object>> call, Response<Map<String,Object>> res) {
                if (res.isSuccessful() && res.body()!=null) {
                    lastVerifiedEmail = email;
                    toast("인증 성공");
                    if (timer != null) timer.cancel();
                    textTimer.setText("인증 완료");
                } else toast("인증 실패(" + res.code() + ")");
            }
            @Override public void onFailure(Call<Map<String,Object>> call, Throwable t) { toast("네트워크 오류"); }
        });
    }

    private void startTimer(int seconds) {
        if (timer != null) timer.cancel();
        timer = new CountDownTimer(seconds*1000L, 1000) {
            @Override public void onTick(long millisUntilFinished) {
                long min = millisUntilFinished/1000/60;
                long sec = (millisUntilFinished/1000)%60;
                textTimer.setText(String.format(Locale.KOREA, "남은 시간: %02d:%02d", min, sec));
            }
            @Override public void onFinish() {
                textTimer.setText("시간 초과. 다시 요청하세요.");
            }
        }.start();
    }

    /** ================= 비밀번호 규칙/일치 표시 ================= */

    private void setupPasswordWatcher() {
        TextWatcher watcher = new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override public void afterTextChanged(Editable s) { validatePasswords(); }
        };
        if (passwordEdit != null) passwordEdit.addTextChangedListener(watcher);
        if (passwordConfirmEdit != null) passwordConfirmEdit.addTextChangedListener(watcher);

        // 최초 1회 상태 반영
        validatePasswords();
    }

    private void validatePasswords() {
        String pw = safeTrim(passwordEdit);
        String confirm = safeTrim(passwordConfirmEdit);

        final int RED   = ContextCompat.getColor(this, android.R.color.holo_red_dark);
        final int GREEN = ContextCompat.getColor(this, android.R.color.holo_green_dark);
        final int GRAY  = ContextCompat.getColor(this, android.R.color.darker_gray);

        // 규칙: 10~16자, 영문/숫자만 허용, 대/소/숫자 중 2종류 이상, 연속문자/키보드 시퀀스 금지
        boolean lenOk = pw.length() >= 10 && pw.length() <= 16 && pw.matches("^[A-Za-z0-9]+$");
        boolean mixOk = hasAtLeastTwoClasses(pw);
        boolean seqOk = !(hasSequentialAlphaOrDigit(pw) || hasKeyboardSequence(pw));

        if (pwRule1 != null) pwRule1.setTextColor(pw.isEmpty() ? GRAY : (lenOk ? GREEN : RED));
        if (pwRule2 != null) pwRule2.setTextColor(pw.isEmpty() ? GRAY : (mixOk ? GREEN : RED));
        if (pwRule3 != null) pwRule3.setTextColor(pw.isEmpty() ? GRAY : (seqOk ? GREEN : RED));

        boolean bothFilled = !pw.isEmpty() && !confirm.isEmpty();
        boolean match = bothFilled && pw.equals(confirm);
        if (pwMatchStatus != null) {
            if (!bothFilled) {
                pwMatchStatus.setText(""); // 비움
            } else if (match) {
                pwMatchStatus.setText("일치합니다");
                pwMatchStatus.setTextColor(GREEN);
            } else {
                pwMatchStatus.setText("일치하지 않습니다");
                pwMatchStatus.setTextColor(RED);
            }
        }
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

    /** ================= 서버와 통신: 내 정보 로드/업데이트 ================= */

    private void tryRefreshThenLoad() {
        String refresh = TokenStore.getRefresh(this);
        if (TextUtils.isEmpty(refresh)) {
            toast("로그인이 필요합니다.");
            startActivity(new Intent(this, LoginActivity.class));
            finish();
            return;
        }
        ApiClient.get().refresh(refresh, CLIENT_TYPE, deviceId())
                .enqueue(new Callback<ApiService.AuthResponse>() {
                    @Override public void onResponse(Call<ApiService.AuthResponse> call, Response<ApiService.AuthResponse> res) {
                        if (res.isSuccessful() && res.body() != null) {
                            TokenStore.saveAccess(AccountEditActivity.this, res.body().accessToken);
                            TokenStore.saveRefresh(AccountEditActivity.this, res.body().refreshToken);
                            loadMe("Bearer " + res.body().accessToken, false);
                        } else {
                            toast("로그인이 필요합니다.");
                            startActivity(new Intent(AccountEditActivity.this, LoginActivity.class));
                            finish();
                        }
                    }
                    @Override public void onFailure(Call<ApiService.AuthResponse> call, Throwable t) {
                        toast("네트워크 오류");
                        startActivity(new Intent(AccountEditActivity.this, LoginActivity.class));
                        finish();
                    }
                });
    }

    private void loadMe(String bearer, boolean allowRefresh) {
        ApiClient.get().me(bearer).enqueue(new Callback<ApiService.UserResponse>() {
            @Override public void onResponse(Call<ApiService.UserResponse> call, Response<ApiService.UserResponse> res) {
                if (res.isSuccessful() && res.body() != null) {
                    me = res.body();
                    prefillFields(me);
                    if (me.hasProfileImage) loadProfileImage(bearer);
                } else if (res.code() == 401 && allowRefresh) {
                    tryRefreshThenLoad();
                } else {
                    toast("내 정보 로드 실패(" + res.code() + ")");
                }
            }
            @Override public void onFailure(Call<ApiService.UserResponse> call, Throwable t) {
                toast("네트워크 오류");
            }
        });
    }

    private void prefillFields(ApiService.UserResponse me) {
        EditText editId = findViewById(R.id.edit_id);
        if (editId != null) editId.setText(me.userid);

        if (nameEdit != null) nameEdit.setText(nonNull(me.username, me.userid));
        if (phoneEdit != null) phoneEdit.setText(nonNull(me.tel, ""));

        if (!TextUtils.isEmpty(me.email) && me.email.contains("@")) {
            String[] parts = me.email.split("@", 2);
            String id = parts[0];
            String domain = parts[1];
            emailIdEdit.setText(id);
            int idx = defaultDomains.indexOf(domain);
            if (idx >= 0) {
                emailDomainSpinner.setSelection(idx);
            } else {
                emailDomainSpinner.setSelection(defaultDomains.size() - 1); // 직접 입력
                customDomainWrapper.setVisibility(android.view.View.VISIBLE);
                emailDomainSpinner.setVisibility(android.view.View.GONE);
                editDomainCustom.setText(domain);
            }
            lastVerifiedEmail = me.email;
        }
    }

    private void loadProfileImage(String bearer) {
        ApiClient.get().meImage(bearer).enqueue(new Callback<ResponseBody>() {
            @Override public void onResponse(Call<ResponseBody> call, Response<ResponseBody> res) {
                if (!res.isSuccessful() || res.body() == null) return;
                profileImage.setImageBitmap(BitmapFactory.decodeStream(res.body().byteStream()));
            }
            @Override public void onFailure(Call<ResponseBody> call, Throwable t) { }
        });
    }

    private void onClickUpdate() {
        // 이름은 변경하지 않음 → 서버로는 기존 me.username 전송
        String username = (me != null) ? nonNull(me.username, "") : "";

        String tel      = safeTrim(phoneEdit);
        String email    = buildEmail();

        if (TextUtils.isEmpty(username)) { toast("이름 정보를 확인할 수 없습니다."); return; }
        if (TextUtils.isEmpty(email)) { toast("이메일을 입력하세요"); return; }
        if (TextUtils.isEmpty(tel)) { toast("전화번호를 입력하세요"); return; }

        // 이메일 인증 필수
        if (!TextUtils.equals(email, lastVerifiedEmail)) {
            toast("이메일 인증을 완료하세요.");
            return;
        }

        // 비밀번호 입력이 있다면 정책/일치 체크만 수행(현재 API에 비번 필드는 없음)
        String pw = safeTrim(passwordEdit);
        String confirm = safeTrim(passwordConfirmEdit);
        if (!pw.isEmpty() || !confirm.isEmpty()) {
            boolean lenOk = pw.length() >= 10 && pw.length() <= 16 && pw.matches("^[A-Za-z0-9]+$");
            boolean mixOk = hasAtLeastTwoClasses(pw);
            boolean seqOk = !(hasSequentialAlphaOrDigit(pw) || hasKeyboardSequence(pw));
            boolean match = !pw.isEmpty() && pw.equals(confirm);
            if (!(lenOk && mixOk && seqOk && match)) {
                toast("비밀번호 조건을 만족하고 서로 일치해야 합니다.");
                return;
            }
            // TODO: 비밀번호 변경이 필요하다면 서버 API에 맞게 필드 추가 필요
        }

        performProfileUpdate(username, email, tel, lastVerificationId);
    }

    private String buildEmail() {
        String id = safeTrim(emailIdEdit);
        String domain;
        if (customDomainWrapper.getVisibility() == android.view.View.VISIBLE) {
            domain = safeTrim(editDomainCustom);
        } else {
            domain = (String) emailDomainSpinner.getSelectedItem();
        }
        if (TextUtils.isEmpty(id) || TextUtils.isEmpty(domain)) return "";
        return id + "@" + domain;
    }

    private void performProfileUpdate(String username, String email, String tel, String verificationId) {
        try {
            Gson gson = new Gson();
            UpdateData dataObj = new UpdateData();
            dataObj.username = username; // ← 이름 변경하지 않지만 서버 요구 시 그대로 전달(읽기전용 취급)
            dataObj.email = email;
            dataObj.tel = tel;
            dataObj.removeProfileImage = removeProfileImage ? Boolean.TRUE : null;
            dataObj.emailVerificationId = verificationId;

            String dataJson = gson.toJson(dataObj);
            RequestBody dataPart = RequestBody.create(MediaType.parse("application/json"), dataJson);

            MultipartBody.Part filePart = null;
            if (pickedImageUri != null) {
                byte[] bytes = readAllBytesFromUri(pickedImageUri);
                if (bytes != null) {
                    String mime = guessMimeFromUri(pickedImageUri);
                    if (mime == null) mime = "image/jpeg";
                    if (bytes.length > 2 * 1024 * 1024) { toast("이미지 용량이 2MB를 초과합니다."); return; }
                    RequestBody rb = RequestBody.create(MediaType.parse(mime), bytes);
                    filePart = MultipartBody.Part.createFormData("file", "profile", rb);
                }
            }

            String bearer = "Bearer " + TokenStore.getAccess(this);
            ApiClient.get().updateMe(bearer, dataPart, filePart)
                    .enqueue(new Callback<ApiService.UserResponse>() {
                        @Override public void onResponse(Call<ApiService.UserResponse> call, Response<ApiService.UserResponse> res) {
                            if (res.isSuccessful()) {
                                toast("수정 완료");
                                Intent intent = new Intent(AccountEditActivity.this, SettingsActivity.class);
                                intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
                                startActivity(intent);
                                finish();
                            } else {
                                toast("수정 실패(" + res.code() + ")");
                            }
                        }
                        @Override public void onFailure(Call<ApiService.UserResponse> call, Throwable t) {
                            toast("네트워크 오류");
                        }
                    });

        } catch (Exception e) {
            toast("요청 준비 중 오류");
        }
    }

    /** ================= 유틸 ================= */
    private String safeTrim(EditText et) { return et==null ? "" : et.getText().toString().trim(); }
    private void toast(String msg) { Toast.makeText(this, msg, Toast.LENGTH_SHORT).show(); }
    private String deviceId() { return Settings.Secure.getString(getContentResolver(), Settings.Secure.ANDROID_ID); }
    private byte[] readAllBytesFromUri(Uri uri) { try (InputStream is = getContentResolver().openInputStream(uri); ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
        byte[] buf = new byte[8192]; int n; while ((n = is.read(buf)) > 0) bos.write(buf,0,n); return bos.toByteArray(); } catch (Exception e) { return null; } }
    private String guessMimeFromUri(Uri uri) { ContentResolver cr = getContentResolver(); return cr.getType(uri); }
    private static String nonNull(String s, String fallback) { return (s == null || s.isEmpty()) ? fallback : s; }

    static class UpdateData {
        String username;
        String email;
        String tel;
        Boolean removeProfileImage;
        String emailVerificationId;
    }
}
