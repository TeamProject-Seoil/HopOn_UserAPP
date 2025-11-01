package com.example.testmap;

import android.app.Application;
import android.text.TextUtils;

import com.example.testmap.service.ApiClient;
import com.example.testmap.service.ApiService;
import com.example.testmap.util.TokenStore;
import com.example.testmap.util.DeviceInfo;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class App extends Application {
    private static final String CLIENT_TYPE = "USER_APP";

    @Override public void onCreate() {
        super.onCreate();

        // 저장된 refresh가 있으면 전역에서 1회 자동 갱신
        final String savedRefresh = TokenStore.getRefresh(this);
        if (TextUtils.isEmpty(savedRefresh)) return;

        ApiService api = ApiClient.get();
        api.refresh(savedRefresh, CLIENT_TYPE, DeviceInfo.getDeviceId(this))
                .enqueue(new Callback<ApiService.AuthResponse>() {
                    @Override public void onResponse(Call<ApiService.AuthResponse> call, Response<ApiService.AuthResponse> res) {
                        if (res.isSuccessful() && res.body()!=null) {
                            ApiService.AuthResponse a = res.body();
                            // 메모리 access 저장
                            TokenStore.saveAccess(App.this, a.accessToken);
                            // 최신 refresh를 메모리+디스크 모두 반영
                            if (!TextUtils.isEmpty(a.refreshToken)) {
                                TokenStore.setRefreshVolatile(a.refreshToken);
                                TokenStore.saveRefresh(App.this, a.refreshToken);
                            }
                        } else {
                            // 갱신 실패: access, volatile refresh만 비우고 디스크 refresh는 보존
                            TokenStore.clearAccess(App.this);
                            TokenStore.clearRefreshVolatile();
                        }
                    }
                    @Override public void onFailure(Call<ApiService.AuthResponse> call, Throwable t) {
                        // 네트워크 실패 시에도 다음에 다시 시도되도록 그대로 둠
                    }
                });
    }
}
