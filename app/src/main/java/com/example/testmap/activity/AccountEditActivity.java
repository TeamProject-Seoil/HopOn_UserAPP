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
    private TextView passwordRules, textTimer;

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
        setupPasswordWatcher();

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
       // passwordRules       = findViewById(R.id.password_rules);
        textTimer           = findViewById(R.id.text_timer);
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

    /** ================= 비밀번호 규칙 ================= */

    private void setupPasswordWatcher() {
        passwordEdit.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override public void afterTextChanged(Editable s) { validatePasswordRules(s.toString()); }
        });
    }

    private void validatePasswordRules(String pw) {
        if (passwordRules == null) return;
        boolean lenOk = pw.length() >= 10 && pw.length() <= 16;
        boolean comboOk = pw.matches(".*[A-Za-z].*") && pw.matches(".*\\d.*");
        boolean spaceOk = !pw.contains(" ");
        if (lenOk && comboOk && spaceOk) {
            passwordRules.setTextColor(getResources().getColor(android.R.color.holo_green_dark));
        } else {
            passwordRules.setTextColor(getResources().getColor(android.R.color.holo_red_dark));
        }
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
        String username = safeTrim(nameEdit);
        String tel      = safeTrim(phoneEdit);
        String email    = buildEmail();

        if (TextUtils.isEmpty(username)) { toast("이름을 입력하세요"); return; }
        if (TextUtils.isEmpty(email)) { toast("이메일을 입력하세요"); return; }
        if (TextUtils.isEmpty(tel)) { toast("전화번호를 입력하세요"); return; }

        if (!TextUtils.equals(email, lastVerifiedEmail)) {
            toast("이메일 인증을 완료하세요.");
            return;
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
            dataObj.username = username;
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
