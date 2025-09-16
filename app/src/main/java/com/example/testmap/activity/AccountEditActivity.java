package com.example.testmap.activity;

import android.content.ContentResolver;
import android.content.Intent;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.text.InputType;
import android.text.TextUtils;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.Spinner;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.example.testmap.R;
import com.example.testmap.service.ApiClient;
import com.example.testmap.service.ApiService;
import com.example.testmap.util.TokenStore;
import com.google.gson.Gson;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.RequestBody;
import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/** 계정 정보 수정 화면 (인증번호 입력 제거 버전) */
public class AccountEditActivity extends AppCompatActivity {

    private static final String CLIENT_TYPE = "USER_APP";

    private ImageButton backButton;
    private ImageView profileImage;

    private EditText passwordEdit, passwordConfirmEdit, nameEdit, emailIdEdit, phoneEdit;
    private Spinner emailDomainSpinner;
    private Button updateButton;

    private final List<String> defaultDomains = new ArrayList<>();
    private ArrayAdapter<String> domainAdapter;

    // 상태
    private Uri pickedImageUri = null;          // 새로 선택한 프로필 이미지
    private boolean removeProfileImage = false; // 길게 눌러 삭제 토글

    private ApiService.UserResponse me;         // 내 정보 캐시

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_account_edit);

        bindViews();
        setupDomainSpinner();
        setupClicks();

        String at = TokenStore.getAccess(this);
        if (TextUtils.isEmpty(at)) {
            tryRefreshThenLoad();
        } else {
            loadMe("Bearer " + at, true);
        }
    }

    private void bindViews() {
        backButton        = findViewById(R.id.account_back_button);
        profileImage      = findViewById(R.id.profile_image);

        passwordEdit        = findViewById(R.id.edit_password);
        passwordConfirmEdit = findViewById(R.id.edit_password_confirm);
        nameEdit            = findViewById(R.id.edit_name);
        emailIdEdit         = findViewById(R.id.edit_email_id);
        phoneEdit           = findViewById(R.id.edit_phone);

        emailDomainSpinner = findViewById(R.id.spinner_email_domain);
        updateButton       = findViewById(R.id.btn_update);
    }

    private void setupDomainSpinner() {
        defaultDomains.clear();
        defaultDomains.add("gmail.com");
        defaultDomains.add("naver.com");
        defaultDomains.add("daum.net");
        defaultDomains.add("kakao.com");
        defaultDomains.add("outlook.com");

        domainAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, defaultDomains);
        emailDomainSpinner.setAdapter(domainAdapter);
    }

    private void setupClicks() {
        backButton.setOnClickListener(v -> finish());

        profileImage.setOnClickListener(v -> pickImage());

        profileImage.setOnLongClickListener(v -> {
            removeProfileImage = !removeProfileImage;
            pickedImageUri = null; // 삭제 전환 시 새 파일 무효
            if (removeProfileImage) {
                profileImage.setImageResource(R.drawable.ic_profile_placeholder);
                Toast.makeText(this, "프로필 이미지를 삭제합니다.", Toast.LENGTH_SHORT).show();
            } else {
                String at = TokenStore.getAccess(this);
                if (!TextUtils.isEmpty(at) && me != null && me.hasProfileImage) {
                    loadProfileImage("Bearer " + at);
                }
            }
            return true;
        });

        updateButton.setOnClickListener(v -> onClickUpdate());
    }

    // ===== 데이터 로딩 =====

    private void tryRefreshThenLoad() {
        String refresh = TokenStore.getRefresh(this);
        if (TextUtils.isEmpty(refresh)) {
            Toast.makeText(this, "로그인이 필요합니다.", Toast.LENGTH_SHORT).show();
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
                            Toast.makeText(AccountEditActivity.this, "로그인이 필요합니다.", Toast.LENGTH_SHORT).show();
                            startActivity(new Intent(AccountEditActivity.this, LoginActivity.class));
                            finish();
                        }
                    }
                    @Override public void onFailure(Call<ApiService.AuthResponse> call, Throwable t) {
                        Toast.makeText(AccountEditActivity.this, "네트워크 오류", Toast.LENGTH_SHORT).show();
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
                    Toast.makeText(AccountEditActivity.this, "내 정보 로드 실패(" + res.code() + ")", Toast.LENGTH_SHORT).show();
                }
            }
            @Override public void onFailure(Call<ApiService.UserResponse> call, Throwable t) {
                Toast.makeText(AccountEditActivity.this, "네트워크 오류", Toast.LENGTH_SHORT).show();
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
            if (idx < 0) {
                defaultDomains.add(0, domain);
                domainAdapter.notifyDataSetChanged();
                emailDomainSpinner.setSelection(0);
            } else {
                emailDomainSpinner.setSelection(idx);
            }
        }
    }

    private void loadProfileImage(String bearer) {
        ApiClient.get().meImage(bearer).enqueue(new Callback<ResponseBody>() {
            @Override public void onResponse(Call<ResponseBody> call, Response<ResponseBody> res) {
                if (!res.isSuccessful() || res.body() == null) return;
                profileImage.setImageBitmap(BitmapFactory.decodeStream(res.body().byteStream()));
            }
            @Override public void onFailure(Call<ResponseBody> call, Throwable t) { /* ignore */ }
        });
    }

    // ===== 갤러리에서 이미지 선택 =====

    private void pickImage() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("image/*");
        // persistable 권한 요청
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        intent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
        intent.addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        startActivityForResult(intent, 1001);
    }

    @Override
    protected void onActivityResult(int reqCode, int resCode, Intent data) {
        super.onActivityResult(reqCode, resCode, data);
        if (reqCode == 1001 && resCode == RESULT_OK && data != null) {
            Uri uri = data.getData();
            if (uri != null) {
                // Lint 경고 해결: 최소 READ는 항상 부여, WRITE 있으면 추가
                int takeFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION;
                if ((data.getFlags() & Intent.FLAG_GRANT_WRITE_URI_PERMISSION) != 0) {
                    takeFlags |= Intent.FLAG_GRANT_WRITE_URI_PERMISSION;
                }
                try {
                    getContentResolver().takePersistableUriPermission(uri, takeFlags);
                } catch (SecurityException ignore) { }

                pickedImageUri = uri;
                try (InputStream is = getContentResolver().openInputStream(uri)) {
                    profileImage.setImageBitmap(BitmapFactory.decodeStream(is));
                } catch (Exception e) {
                    Toast.makeText(this, "이미지 로드 실패", Toast.LENGTH_SHORT).show();
                }
            }
        }
    }

    // ===== 수정 처리 =====

    private void onClickUpdate() {
        String username = safeTrim(nameEdit);
        String emailId  = safeTrim(emailIdEdit);
        String domain   = (String) emailDomainSpinner.getSelectedItem();
        String tel      = safeTrim(phoneEdit);

        if (TextUtils.isEmpty(username)) { toast("이름을 입력하세요"); return; }
        if (TextUtils.isEmpty(emailId) || TextUtils.isEmpty(domain)) { toast("이메일을 입력하세요"); return; }
        if (TextUtils.isEmpty(tel)) { toast("전화번호를 입력하세요"); return; }

        String email = emailId + "@" + domain;

        try {
            Gson gson = new Gson();
            UpdateData dataObj = new UpdateData();
            dataObj.username = username;
            dataObj.email = email;
            dataObj.tel = tel;
            dataObj.removeProfileImage = removeProfileImage ? Boolean.TRUE : null;

            String dataJson = gson.toJson(dataObj);
            RequestBody dataPart = RequestBody.create(
                    MediaType.parse("application/json"), dataJson);

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

            final RequestBody finalDataPart = dataPart;
            final MultipartBody.Part finalFilePart = filePart;

            String bearer = "Bearer " + TokenStore.getAccess(this);
            ApiClient.get().updateMe(bearer, finalDataPart, finalFilePart)
                    .enqueue(new Callback<ApiService.UserResponse>() {
                        @Override public void onResponse(Call<ApiService.UserResponse> call, Response<ApiService.UserResponse> res) {
                            if (res.isSuccessful()) {
                                tryChangePasswordThenFinish();
                            } else if (res.code() == 409) {
                                toast("이미 사용 중인 이메일입니다.");
                            } else if (res.code() == 400) {
                                toast("변경할 내용이 없습니다.");
                            } else if (res.code() == 401) {
                                tryRefreshThenRetryProfileUpdate(finalDataPart, finalFilePart);
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

    private void tryRefreshThenRetryProfileUpdate(RequestBody dataPart, MultipartBody.Part filePart) {
        String refresh = TokenStore.getRefresh(this);
        if (TextUtils.isEmpty(refresh)) { toast("로그인이 필요합니다."); return; }
        ApiClient.get().refresh(refresh, CLIENT_TYPE, deviceId())
                .enqueue(new Callback<ApiService.AuthResponse>() {
                    @Override public void onResponse(Call<ApiService.AuthResponse> call, Response<ApiService.AuthResponse> res) {
                        if (res.isSuccessful() && res.body() != null) {
                            TokenStore.saveAccess(AccountEditActivity.this, res.body().accessToken);
                            TokenStore.saveRefresh(AccountEditActivity.this, res.body().refreshToken);
                            String bearer = "Bearer " + res.body().accessToken;
                            ApiClient.get().updateMe(bearer, dataPart, filePart)
                                    .enqueue(new Callback<ApiService.UserResponse>() {
                                        @Override public void onResponse(Call<ApiService.UserResponse> call, Response<ApiService.UserResponse> res2) {
                                            if (res2.isSuccessful()) {
                                                tryChangePasswordThenFinish();
                                            } else {
                                                toast("수정 실패(" + res2.code() + ")");
                                            }
                                        }
                                        @Override public void onFailure(Call<ApiService.UserResponse> call, Throwable t) {
                                            toast("네트워크 오류");
                                        }
                                    });
                        } else {
                            toast("로그인 만료. 다시 로그인하세요.");
                            startActivity(new Intent(AccountEditActivity.this, LoginActivity.class));
                            finish();
                        }
                    }
                    @Override public void onFailure(Call<ApiService.AuthResponse> call, Throwable t) {
                        toast("네트워크 오류");
                    }
                });
    }

    // 비번칸이 채워져 있으면 현재 비번 입력 받아 변경
    private void tryChangePasswordThenFinish() {
        String newPw  = safeTrim(passwordEdit);
        String newPw2 = safeTrim(passwordConfirmEdit);

        if (TextUtils.isEmpty(newPw) && TextUtils.isEmpty(newPw2)) {
            toast("수정 완료");
            finish();
            return;
        }
        if (!TextUtils.equals(newPw, newPw2)) {
            toast("새 비밀번호가 일치하지 않습니다.");
            return;
        }

        final EditText currentInput = new EditText(this);
        currentInput.setHint("현재 비밀번호");
        currentInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);

        new AlertDialog.Builder(this)
                .setTitle("비밀번호 변경")
                .setMessage("현재 비밀번호를 입력하세요.")
                .setView(currentInput)
                .setPositiveButton("확인", (d, w) -> {
                    String current = currentInput.getText().toString();
                    if (TextUtils.isEmpty(current)) { toast("현재 비밀번호를 입력하세요."); return; }

                    ApiService.ChangePasswordRequest body = new ApiService.ChangePasswordRequest(current, newPw);
                    String bearer = "Bearer " + TokenStore.getAccess(this);
                    ApiClient.get().changePassword(bearer, body)
                            .enqueue(new Callback<ResponseBody>() {
                                @Override public void onResponse(Call<ResponseBody> call, Response<ResponseBody> res) {
                                    if (res.isSuccessful()) {
                                        toast("비밀번호 변경 완료. 다시 로그인해 주세요.");
                                        TokenStore.clearAccess(AccountEditActivity.this);
                                        TokenStore.clearRefresh(AccountEditActivity.this);
                                        startActivity(new Intent(AccountEditActivity.this, LoginActivity.class));
                                        finish();
                                    } else if (res.code() == 400) {
                                        toast("현재 비밀번호 불일치 또는 새 비번이 기존과 동일합니다.");
                                    } else if (res.code() == 401) {
                                        toast("로그인 만료. 다시 로그인하세요.");
                                        startActivity(new Intent(AccountEditActivity.this, LoginActivity.class));
                                        finish();
                                    } else {
                                        toast("비밀번호 변경 실패(" + res.code() + ")");
                                    }
                                }
                                @Override public void onFailure(Call<ResponseBody> call, Throwable t) {
                                    toast("네트워크 오류");
                                }
                            });
                })
                .setNegativeButton("취소", (d, w) -> {
                    toast("프로필 정보만 수정되었습니다.");
                    finish();
                })
                .show();
    }

    // ===== 유틸 =====

    private String safeTrim(EditText et) {
        return et == null ? "" : et.getText().toString().trim();
    }

    private void toast(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
    }

    private String deviceId() {
        return Settings.Secure.getString(getContentResolver(), Settings.Secure.ANDROID_ID);
    }

    private byte[] readAllBytesFromUri(Uri uri) {
        try (InputStream is = getContentResolver().openInputStream(uri);
             ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
            byte[] buf = new byte[8192];
            int n;
            while ((n = is.read(buf)) > 0) bos.write(buf, 0, n);
            return bos.toByteArray();
        } catch (Exception e) {
            return null;
        }
    }

    private String guessMimeFromUri(Uri uri) {
        ContentResolver cr = getContentResolver();
        return cr.getType(uri);
    }

    private static String nonNull(String s, String fallback) {
        return (s == null || s.isEmpty()) ? fallback : s;
    }

    // 서버로 보내는 data JSON 구조
    static class UpdateData {
        String username;
        String email;
        String tel;
        Boolean removeProfileImage; // null이면 서버에서 무시
    }
}
