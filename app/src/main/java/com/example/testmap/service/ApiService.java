package com.example.testmap.service;

import com.example.testmap.dto.ArrivalDto;
import com.example.testmap.dto.BusLocationDto;
import com.example.testmap.dto.BusRouteDto;
import com.example.testmap.dto.ReservationCreateRequest;
import com.example.testmap.dto.ReservationResponse;
import com.example.testmap.dto.StationDto;
import com.example.testmap.model.CancelResult;

import java.util.List;
import java.util.Map;

import okhttp3.MultipartBody;
import okhttp3.RequestBody;
import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.http.*;

public interface ApiService {

    // ====== 기존 버스 관련 ======
    @GET("/api/nearstations")
    Call<List<StationDto>> getNearStations(
            @Query("x") double x,
            @Query("y") double y,
            @Query("radius") int radius
    );

    @GET("/api/stationStop")
    Call<List<ArrivalDto>> getStationArrivals(@Query("arsId") String arsId);

    @GET("/api/busLocation")
    Call<List<BusLocationDto>> getBusLocation(@Query("busRouteId") String busRouteId);

    @GET("/api/busStopList")
    Call<List<BusRouteDto>> getBusRoute(@Query("busRouteId") String busRouteId);

    //db 기반 주변 정류장 조회
    @GET("api/stations/nearby")
    Call<List<StationDto>> getNearbyStations(
            @Query("lon") double longitude,
            @Query("lat") double latitude,
            @Query("radius") int radius
    );

    // ====== 인증 관련 DTO ======
    class AuthRequest {
        public String userid;
        public String password;
        public String clientType;
        public String deviceId;

        public AuthRequest(String u, String p, String c, String d) {
            userid = u; password = p; clientType = c; deviceId = d;
        }
    }

    class AuthResponse {
        public String accessToken;
        public String refreshToken;
        public String tokenType;
        public String role;
    }

    class RegisterResponse {
        public boolean ok;
        public String message;
        public String userid;
        public String reason;
    }

    class CheckResponse {
        public boolean useridTaken;
        public boolean emailTaken;
    }

    // ====== 인증 관련 API ======
    @POST("/auth/login")
    Call<AuthResponse> login(@Body AuthRequest body);

    @FormUrlEncoded
    @POST("/auth/refresh")
    Call<AuthResponse> refresh(
            @Field("refreshToken") String refreshToken,
            @Field("clientType") String clientType,
            @Field("deviceId") String deviceId
    );

    @Multipart
    @POST("/auth/register")
    Call<RegisterResponse> register(
            @Part("data") RequestBody dataJson,
            @Part MultipartBody.Part file
    );

    @GET("/auth/check")
    Call<CheckResponse> checkDup(
            @Query("userid") String userid,
            @Query("email") String email
    );

    // ====== 이메일 인증 ======
    @POST("/auth/email/send-code")
    Call<Map<String, Object>> sendEmail(@Body SendEmailCodeRequest req);

    @POST("/auth/email/verify-code")
    Call<Map<String, Object>> verifyEmail(@Body VerifyEmailCodeRequest req);

    class SendEmailCodeRequest {
        private String email;
        private String purpose;

        public SendEmailCodeRequest(String email, String purpose) {
            this.email = email;
            this.purpose = purpose;
        }
    }

    class VerifyEmailCodeRequest {
        private String verificationId;
        private String email;
        private String purpose;
        private String code;

        public VerifyEmailCodeRequest(String verificationId, String email, String purpose, String code) {
            this.verificationId = verificationId;
            this.email = email;
            this.purpose = purpose;
            this.code = code;
        }
    }

    // ====== 아이디 / 비밀번호 찾기 ======
    @POST("/auth/find-id-after-verify")
    Call<Map<String, Object>> findIdAfterVerify(@Body Map<String, Object> body);

    @POST("/auth/reset-password-after-verify")
    Call<Map<String, Object>> resetPasswordAfterVerify(@Body Map<String, Object> body);

    @POST("/auth/verify-pw-user")
    Call<Map<String, Object>> verifyPwUser(@Body Map<String, Object> body);

    // ====== 로그인 사용자 API ======
    @GET("/users/me")
    Call<UserResponse> me(@Header("Authorization") String bearer);

    @GET("/users/me/profile-image")
    Call<ResponseBody> meImage(@Header("Authorization") String bearer);

    @POST("/auth/logout")
    Call<Void> logout(@Body LogoutRequest body);

    class UserResponse {
        public Long userNum;
        public String userid;
        public String username;
        public String email;
        public String tel;
        public String role;
        public boolean hasProfileImage;
        // 서버에서 더 주는 필드가 있어도 무관 (없으면 무시됨)
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

    // ====== 개인정보 수정 ======
    @Multipart
    @PATCH("users/me")
    Call<UserResponse> updateMe(
            @Header("Authorization") String bearer,
            @Part("data") RequestBody dataJson,
            @Part MultipartBody.Part file
    );

    // ====== 비밀번호 변경 ======
    @POST("users/me/password")
    Call<ResponseBody> changePassword(
            @Header("Authorization") String bearer,
            @Body ChangePasswordRequest body
    );

    class ChangePasswordRequest {
        public String currentPassword;
        public String newPassword;

        public ChangePasswordRequest(String c, String n) {
            this.currentPassword = c;
            this.newPassword = n;
        }
    }

    // ====== 회원 탈퇴 ======
    @HTTP(method = "DELETE", path = "users/me", hasBody = true)
    Call<Map<String, Object>> deleteMe(
            @Header("Authorization") String bearer,
            @Body DeleteAccountRequest body
    );

    class DeleteAccountRequest {
        public String currentPassword;
        public DeleteAccountRequest(String currentPassword) {
            this.currentPassword = currentPassword;
        }
    }

    // ====== 예약 관련 ======
    @POST("/api/reservations")
    Call<ReservationResponse> createReservation(
            @Header("Authorization") String bearer,
            @Body ReservationCreateRequest body
    );

    //======= 예약 조회 =======
    @GET("/api/reservations/active")
    Call<ReservationResponse> getActiveReservation(
            @Header("Authorization") String bearer
    );

    //=========예약 취소=============
    @DELETE("/api/reservations/{id}")
    Call<CancelResult> cancelReservationById(
            @Header("Authorization") String bearer,
            @Path("id") Long reservationId
    );

}
