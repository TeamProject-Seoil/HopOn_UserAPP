package com.example.testmap.service;

import com.example.testmap.dto.ArrivalDto;
import com.example.testmap.dto.BusLocationDto;
import com.example.testmap.dto.BusRouteDto;
import com.example.testmap.dto.StationDto;

import java.util.List;

import okhttp3.MultipartBody;
import okhttp3.RequestBody;
import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.http.*;

// ApiService.java
public interface ApiService {

    // ====== 기존 버스 관련 ======
    @GET("/api/nearstations")
    Call<List<StationDto>> getNearStations(
            @Query("x") double x,
            @Query("y") double y,
            @Query("radius") int radius
    );

    @GET("/api/stationStop")
    Call<List<ArrivalDto>> getStationArrivals(
            @Query("arsId") String arsId
    );

    @GET("/api/busLocation")
    Call<List<BusLocationDto>> getBusLocation(
            @Query("busRouteId") String busRouteId
    );

    @GET("/api/busStopList")
    Call<List<BusRouteDto>> getBusRoute(
            @Query("busRouteId") String busRouteId
    );
    // ====== 인증 관련 DTO ======
    class AuthRequest {
        public String userid;
        public String password;
        public String clientType; // e.g. "USER_APP"
        public String deviceId;
        public AuthRequest(String u, String p, String c, String d) {
            userid = u; password = p; clientType = c; deviceId = d;
        }
    }
    class AuthResponse {
        public String accessToken;
        public String refreshToken;
        public String tokenType; // "Bearer"
        public String role;      // "ROLE_*"
    }
    class RegisterResponse {
        public boolean ok;
        public String message; // "REGISTERED"
        public String userid;  // 성공 시
        public String reason;  // 409 시 "DUPLICATE_USERID"/"DUPLICATE_EMAIL"
    }

    // ====== 인증 관련 API ======
    @POST("/auth/login")
    Call<AuthResponse> login(@Body AuthRequest body);

    @FormUrlEncoded
    @POST("/auth/refresh")
    Call<AuthResponse> refresh(@Field("refreshToken") String refreshToken,
                               @Field("clientType") String clientType,
                               @Field("deviceId") String deviceId);

    // 서버는 multipart/form-data, data 파트(JSON), file 파트(optional)
    @Multipart
    @POST("/auth/register")
    Call<RegisterResponse> register(@Part("data") RequestBody dataJson,
                                    @Part MultipartBody.Part file /* nullable */);

    // ApiService.java 내부에 추가
    @GET("/auth/check")
    Call<CheckResponse> checkDup(@Query("userid") String userid, @Query("email") String email);

    class CheckResponse {
        public boolean useridTaken;
        public boolean emailTaken;
    }

    // 로그인 사용자 조회
    @GET("/users/me")
    Call<UserResponse> me(@Header("Authorization") String bearer);

    // 프로필 이미지 (바이너리)
    @GET("/users/me/profile-image")
    Call<ResponseBody> meImage(@Header("Authorization") String bearer);

    // 로그아웃
    @POST("/auth/logout")
    Call<Void> logout(@Body LogoutRequest body);

    // ====== DTO ======
    class UserResponse {
        public Long userNum;
        public String userid;
        public String username;
        public String email;
        public String tel;
        public String role;
        public boolean hasProfileImage;
    }
    class LogoutRequest {
        public String clientType;
        public String deviceId;
        public String refreshToken;
        public LogoutRequest(String clientType, String deviceId, String refreshToken) {
            this.clientType = clientType;
            this.deviceId = deviceId;
            this.refreshToken = refreshToken;
        }
    }

    // 1) 개인정보 수정 (multipart PATCH)
    @Multipart
    @PATCH("users/me")
    Call<UserResponse> updateMe(
            @Header("Authorization") String bearer,
            @Part("data") RequestBody dataJson,     // application/json
            @Part MultipartBody.Part file           // null 가능
    );

    // 2) 비밀번호 변경
    @POST("users/me/password")
    Call<ResponseBody> changePassword(
            @Header("Authorization") String bearer,
            @Body ChangePasswordRequest body
    );
    class ChangePasswordRequest {
        public String currentPassword;
        public String newPassword;
        public ChangePasswordRequest(String c, String n){ this.currentPassword=c; this.newPassword=n;}
    }
}
