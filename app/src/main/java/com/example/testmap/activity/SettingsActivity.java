package com.example.testmap.activity;

import android.content.Intent;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.text.TextUtils;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SwitchCompat;

import com.example.testmap.R;
import com.example.testmap.service.ApiClient;
import com.example.testmap.service.ApiService;
import com.example.testmap.util.TokenStore;

import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class SettingsActivity extends AppCompatActivity {

    private ImageButton btnBack;
    private LinearLayout sectionLogin, viewLoggedIn, btnEditAccount;
    private TextView textName, textPhone, textEmail;
    private ImageView imageProfile;
    private SwitchCompat switchNotice;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        btnBack        = findViewById(R.id.btn_back_settings);
        sectionLogin   = findViewById(R.id.section_login);
        viewLoggedIn   = findViewById(R.id.view_logged_in);
        btnEditAccount = findViewById(R.id.btn_edit_account);
        textName       = findViewById(R.id.text_name);
        textPhone      = findViewById(R.id.text_phone);
        textEmail      = findViewById(R.id.text_email);
        imageProfile   = findViewById(R.id.image_profile);
        switchNotice   = findViewById(R.id.switch_notice);

        // 뒤로가기
        btnBack.setOnClickListener(v -> finish());

        // 로그인 영역 클릭 → 로그인 화면으로
        sectionLogin.setOnClickListener(v -> {
            startActivity(new Intent(this, LoginActivity.class));
        });

        // 계정 정보 수정 클릭 → 수정 화면
        btnEditAccount.setOnClickListener(v -> {
            startActivity(new Intent(this, AccountEditActivity.class));
        });

        // (예시) 스위치 동작
        switchNotice.setOnCheckedChangeListener((b, checked) ->
                Toast.makeText(this, "공지사항 알림: " + (checked ? "ON" : "OFF"), Toast.LENGTH_SHORT).show()
        );
    }

    @Override
    protected void onResume() {
        super.onResume();
        renderByAuth();
    }

    private void renderByAuth() {
        String at = TokenStore.getAccess(this);
        if (TextUtils.isEmpty(at)) {
            // 로그인 안됨
            viewLoggedIn.setVisibility(android.view.View.GONE);
            sectionLogin.setVisibility(android.view.View.VISIBLE);
            return;
        }

        // 내 정보 불러오기
        String bearer = "Bearer " + at;
        ApiClient.get().me(bearer).enqueue(new Callback<ApiService.UserResponse>() {
            @Override public void onResponse(Call<ApiService.UserResponse> call, Response<ApiService.UserResponse> res) {
                if (res.isSuccessful() && res.body()!=null) {
                    ApiService.UserResponse me = res.body();

                    // UI 토글
                    sectionLogin.setVisibility(android.view.View.GONE);
                    viewLoggedIn.setVisibility(android.view.View.VISIBLE);

                    // 텍스트 채우기
                    textName.setText(!TextUtils.isEmpty(me.username) ? me.username : me.userid);
                    textPhone.setText(me.tel != null ? me.tel : "");
                    textEmail.setText(me.email != null ? me.email : "");

                    if (me.hasProfileImage) {
                        loadProfileImage(bearer);
                    } else {
                        imageProfile.setImageResource(R.drawable.ic_profile);
                    }
                } else {
                    // 토큰 만료 등 → 로그인 요구
                    sectionLogin.setVisibility(android.view.View.VISIBLE);
                    viewLoggedIn.setVisibility(android.view.View.GONE);
                }
            }
            @Override public void onFailure(Call<ApiService.UserResponse> call, Throwable t) {
                sectionLogin.setVisibility(android.view.View.VISIBLE);
                viewLoggedIn.setVisibility(android.view.View.GONE);
            }
        });
    }

    private void loadProfileImage(String bearer) {
        ApiClient.get().meImage(bearer).enqueue(new Callback<ResponseBody>() {
            @Override public void onResponse(Call<ResponseBody> call, Response<ResponseBody> res) {
                if (!res.isSuccessful() || res.body()==null) return;
                imageProfile.setImageBitmap(BitmapFactory.decodeStream(res.body().byteStream()));
            }
            @Override public void onFailure(Call<ResponseBody> call, Throwable t) { /* ignore */ }
        });
    }
}
