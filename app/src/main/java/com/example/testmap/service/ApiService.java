// app/src/main/java/com/example/testmap/service/ApiService.java
package com.example.testmap.service;

import com.example.testmap.dto.ArrivalDto;
import com.example.testmap.dto.BusLocationDto;
import com.example.testmap.dto.BusRouteDto;
import com.example.testmap.dto.DriverLocationDto;
import com.example.testmap.dto.ReservationCreateRequest;
import com.example.testmap.dto.ReservationResponse;
import com.example.testmap.dto.RoutePoint;
import com.example.testmap.dto.StationDto;
import com.example.testmap.model.CancelResult;

import java.util.List;
import java.util.Map;

import okhttp3.MultipartBody;
import okhttp3.RequestBody;
import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.http.*;

/**
 * Retrofit2 API
 * 2025-10 업데이트: 서버(Spring) 경로/보안정책 정합
 */
public interface ApiService {

    // =========================================================
    // ================= 버스 / 정류장 ==========================
    // =========================================================

    /** 공공데이터: 주변 정류장 (tmX=x, tmY=y) */
    @GET("/api/nearstations")
    Call<List<StationDto>> getNearStations(@Query("x") double x,
                                           @Query("y") double y,
                                           @Query("radius") int radius);

    /** 정류장 도착 정보(arsId) */
    @GET("/api/stationStop")
    Call<List<ArrivalDto>> getStationArrivals(@Query("arsId") String arsId);

    /** 노선별 실시간 차량 위치 */
    @GET("/api/busLocation")
    Call<List<BusLocationDto>> getBusLocation(@Query("busRouteId") String busRouteId);

    /** 노선 정류장 목록(방향/seq 포함) */
    @GET("/api/busStopList")
    Call<List<BusRouteDto>> getBusRoute(@Query("busRouteId") String busRouteId);

    /** DB-기반 주변 정류장 (위경도/미터 반경) */
    @GET("/api/stations/nearby")
    Call<List<StationDto>> getNearbyStations(@Query("lon") double lon,
                                             @Query("lat") double lat,
                                             @Query("radius") int radius);

    /** 노선 전체 폴리라인 (인증 필요) */
    @GET("/api/busRoutePath")
    Call<List<RoutePoint>> getFullPath(@Header("Authorization") String bearer,
                                       @Query("busRouteId") String routeId);

    /** 승차~하차 구간 폴리라인 슬라이스 (인증 필요) */
    @GET("/api/busRoutePath/segment")
    Call<List<RoutePoint>> getSegment(@Header("Authorization") String bearer,
                                      @Query("busRouteId") String routeId,
                                      @Query("boardArsId") String boardArsId,
                                      @Query("destArsId") String destArsId);

    // =========================================================
    // ======================= 인증 ============================
    // =========================================================

    class AuthRequest {
        public String userid, password, clientType, deviceId;
        public AuthRequest(String u, String p, String c, String d) {
            userid = u; password = p; clientType = c; deviceId = d;
        }
    }
    class AuthResponse { public String accessToken, refreshToken, tokenType, role; }
    class RegisterResponse { public boolean ok; public String message, userid, reason; }
    class CheckResponse { public boolean useridTaken, emailTaken; }

    @POST("/auth/login")
    Call<AuthResponse> login(@Body AuthRequest body);

    @FormUrlEncoded
    @POST("/auth/refresh")
    Call<AuthResponse> refresh(@Field("refreshToken") String token,
                               @Field("clientType") String clientType,
                               @Field("deviceId") String deviceId);

    @Multipart
    @POST("/auth/register")
    Call<RegisterResponse> register(@Part("data") RequestBody dataJson,
                                    @Part MultipartBody.Part file);

    @GET("/auth/check")
    Call<CheckResponse> checkDup(@Query("userid") String userid,
                                 @Query("email") String email);

    // 이메일 인증
    class SendEmailCodeRequest {
        public String email, purpose;
        public SendEmailCodeRequest(String e, String p){ email=e; purpose=p; }
    }
    class VerifyEmailCodeRequest {
        public String verificationId, email, purpose, code;
        public VerifyEmailCodeRequest(String v, String e, String p, String c){
            verificationId=v; email=e; purpose=p; code=c;
        }
    }
    @POST("/auth/email/send-code")
    Call<Map<String,Object>> sendEmail(@Body SendEmailCodeRequest req);

    @POST("/auth/email/verify-code")
    Call<Map<String,Object>> verifyEmail(@Body VerifyEmailCodeRequest req);

    // 비밀번호·계정
    @POST("/auth/find-id-after-verify")
    Call<Map<String,Object>> findIdAfterVerify(@Body Map<String,Object> body);

    @POST("/auth/reset-password-after-verify")
    Call<Map<String,Object>> resetPasswordAfterVerify(@Body Map<String,Object> body);

    @POST("/auth/verify-pw-user")
    Call<Map<String,Object>> verifyPwUser(@Header("Authorization") String bearer,
                                          @Body Map<String,Object> body);

    class VerifyCurrentPasswordRequest {
        public String currentPassword, clientType, deviceId;
        public VerifyCurrentPasswordRequest(String c, String t, String d){
            currentPassword = c; clientType = t; deviceId = d;
        }
    }
    @POST("/auth/verify-current-password")
    Call<Map<String,Object>> verifyCurrentPassword(@Header("Authorization") String bearer,
                                                   @Body VerifyCurrentPasswordRequest body);

    // 로그인 사용자
    class UserResponse {
        public Long userNum; public String userid, username, email, tel, role;
        public boolean hasProfileImage;
        public String company, approvalStatus, lastLoginAtIso, lastRefreshAtIso;
        public Boolean hasDriverLicenseFile;
    }
    class LogoutRequest {
        public String clientType, deviceId, refreshToken;
        public LogoutRequest(String c, String d, String r){
            clientType = c; deviceId = d; refreshToken = r;
        }
    }

    @GET("/users/me")
    Call<UserResponse> me(@Header("Authorization") String bearer);

    @GET("/users/me/profile-image")
    Call<ResponseBody> meImage(@Header("Authorization") String bearer);

    @POST("/auth/logout")
    Call<Map<String,Object>> logout(@Body LogoutRequest body);

    @Multipart
    @PATCH("/users/me")
    Call<UserResponse> updateMe(@Header("Authorization") String bearer,
                                @Part("data") RequestBody dataJson,
                                @Part MultipartBody.Part file);

    class ChangePasswordRequest {
        public String currentPassword, newPassword;
        public ChangePasswordRequest(String c, String n){ currentPassword=c; newPassword=n; }
    }
    @POST("/users/me/password")
    Call<Map<String,Object>> changePassword(@Header("Authorization") String bearer,
                                            @Body ChangePasswordRequest body);

    class DeleteAccountRequest { public String currentPassword; public DeleteAccountRequest(String c){ currentPassword=c; } }
    @HTTP(method="DELETE", path="/users/me", hasBody=true)
    Call<Map<String,Object>> deleteMe(@Header("Authorization") String bearer,
                                      @Body DeleteAccountRequest body);

    // =========================================================
    // ======================= 예약 ============================
    // =========================================================

    /** 예약 생성 (서버가 busRouteType / routeTypeName / boardingStage / delayed 같이 내려줌) */
    @POST("/api/reservations")
    Call<ReservationResponse> createReservation(@Header("Authorization") String bearer,
                                                @Body ReservationCreateRequest body);

    /** 활성 예약 1건 (없으면 204 또는 200 + null) */
    @GET("/api/reservations/active")
    Call<ReservationResponse> getActiveReservation(@Header("Authorization") String bearer);

    /** 전체 예약 목록 */
    @GET("/api/reservations")
    Call<List<ReservationResponse>> getReservations(@Header("Authorization") String bearer);

    /** 예약 취소 (CONFIRMED 상태만 가능) */
    @DELETE("/api/reservations/{id}")
    Call<CancelResult> cancelReservationById(@Header("Authorization") String bearer,
                                             @Path("id") Long id);

    /** 예약과 매칭된 버스 현재 위치(200 OK 또는 204 No Content) */
    @GET("/api/reservations/{id}/location")
    Call<DriverLocationDto> getDriverLocation(
            @Header("Authorization") String bearer,
            @Path("id") Long reservationId
    );

    /** 출발 정류장 도착 시, 팝업에서 '탑승했어요' 눌렀을 때 호출 → boardingStage: BOARDED */
    @POST("/api/reservations/{id}/board/confirm")
    Call<ReservationResponse> confirmBoarding(@Header("Authorization") String bearer,
                                              @Path("id") Long id);

    /** 하차 정류장 도착 시, 팝업에서 '하차했어요' 눌렀을 때 호출 → boardingStage: ALIGHTED, status: COMPLETED */
    @POST("/api/reservations/{id}/alight/confirm")
    Call<ReservationResponse> confirmAlighting(@Header("Authorization") String bearer,
                                               @Path("id") Long id);

    // =========================================================
    // ===================== 즐겨찾기 ==========================
    // =========================================================

    class FavoriteCreateRequest {
        public String routeId, direction, boardStopId, boardStopName, boardArsId,
                destStopId, destStopName, destArsId, routeName;
        public Integer busRouteType;   // ⬅ 추가
        public String routeTypeName;   // ⬅ 추가

        public FavoriteCreateRequest(String routeId, String direction,
                                     String boardStopId, String boardStopName, String boardArsId,
                                     String destStopId, String destStopName, String destArsId,
                                     String routeName,
                                     Integer busRouteType, String routeTypeName) {
            this.routeId = routeId;
            this.direction = direction; // null 가능 → 서버에서 ""로 정규화
            this.boardStopId = boardStopId;
            this.boardStopName = boardStopName;
            this.boardArsId = boardArsId;
            this.destStopId = destStopId;
            this.destStopName = destStopName;
            this.destArsId = destArsId;
            this.routeName = routeName;
            this.busRouteType = busRouteType;
            this.routeTypeName = routeTypeName;
        }

        // 구형(이전) 생성자도 유지 (호환용)
        public FavoriteCreateRequest(String routeId, String direction,
                                     String boardStopId, String boardStopName, String boardArsId,
                                     String destStopId, String destStopName, String destArsId,
                                     String routeName) {
            this(routeId, direction, boardStopId, boardStopName, boardArsId,
                    destStopId, destStopName, destArsId, routeName, null, null);
        }
    }

    class FavoriteResponse {
        public Long id;
        public String routeId, direction, boardStopId, boardStopName,
                boardArsId, destStopId, destStopName, destArsId, routeName;
        public Integer busRouteType;   // ⬅ 추가
        public String routeTypeName;   // ⬅ 추가
    }

    @POST("/api/favorites")
    Call<FavoriteResponse> addFavorite(@Header("Authorization") String bearer,
                                       @Body FavoriteCreateRequest body);

    @GET("/api/favorites")
    Call<List<FavoriteResponse>> getFavorites(@Header("Authorization") String bearer);

    @DELETE("/api/favorites/{id}")
    Call<Void> deleteFavorite(@Header("Authorization") String bearer,
                              @Path("id") Long id);

    // =========================================================
    // =============== 공통: Spring Page 응답 ===================
    // =========================================================
    /** 서버 PageResponse와 필드명 일치 (page/size/...) */
    class PageResponse<T> {
        public List<T> content;
        public int page;        // ✅ number → page 로 수정
        public int size;
        public int totalPages;
        public long totalElements;
        public boolean first, last;
    }

    // =========================================================
    // ================== 공지(Notice) API ======================
    // =========================================================

    class NoticeResp {
        public Long id; public String title, content, noticeType, targetRole;
        public long viewCount;
        public String createdAt, updatedAt;
        public String readAt;
    }
    class UnreadCountResp { public long count; }

    /** 공지 목록 (로그인/비로그인 공통; 서버 설정에 따라 Authorization 생략 가능) */
    @GET("/api/notices")
    Call<PageResponse<NoticeResp>> getNotices(@Header("Authorization") String bearer,
                                              @Header("X-User-Role") String role,
                                              @Query("page") int page,
                                              @Query("size") int size,
                                              @Query("sort") String sort,
                                              @Query("q") String q,
                                              @Query("type") String type);

    /** 공지 상세 (increase/read 처리 지원) */
    @GET("/api/notices/{id}")
    Call<NoticeResp> getNoticeDetail(@Header("Authorization") String bearer,
                                     @Path("id") Long id,
                                     @Query("increase") boolean increase,
                                     @Query("markRead") boolean markRead);

    /** 미확인 개수 (로그인 필요) */
    @GET("/api/notices/unread-count")
    Call<UnreadCountResp> getNoticeUnreadCount(@Header("Authorization") String bearer);

    /** 특정 공지 읽음 처리 (로그인 필요) */
    @POST("/api/notices/{id}/read")
    Call<Void> markNoticeRead(@Header("Authorization") String bearer,
                              @Path("id") Long id);

    // =========================================================
    // ================== 문의(Inquiry) API =====================
    // =========================================================

    class InquiryAtt { public Long id; public String filename, contentType; public long size; }
    class InquiryRep { public Long id; public String message, createdAt; }
    class InquiryResp {
        public Long id; public String name, email, userid, title, content, status;
        public boolean secret, hasPassword;
        public List<InquiryAtt> attachments; public List<InquiryRep> replies;
        public String createdAt, updatedAt;
    }

    /** (공개 목록, 비로그인 가능) */
    @GET("/api/inquiries/public")
    Call<PageResponse<InquiryResp>> getPublicInquiries(@Query("page") int page,
                                                       @Query("size") int size,
                                                       @Query("sort") String sort,
                                                       @Query("q") String q,
                                                       @Query("status") String status);

    /** (내 문의 목록, 로그인 필요) */
    @GET("/api/inquiries")
    Call<PageResponse<InquiryResp>> getMyInquiries(@Header("Authorization") String bearer,
                                                   @Header("X-User-Id") String userId,
                                                   @Header("X-User-Email") String email,
                                                   @Header("X-User-Role") String role,
                                                   @Query("page") int page,
                                                   @Query("size") int size,
                                                   @Query("sort") String sort,
                                                   @Query("q") String q,
                                                   @Query("status") String status);

    /** (내 문의 상세, 로그인 필요) */
    @GET("/api/inquiries/{id}")
    Call<InquiryResp> getMyInquiryDetail(@Header("Authorization") String bearer,
                                         @Header("X-User-Id") String userId,
                                         @Header("X-User-Email") String email,
                                         @Header("X-User-Role") String role,
                                         @Path("id") Long id);

    /** (공개 상세, 비밀번호 필요 시 password 전송) */
    @GET("/api/inquiries/{id}/public")
    Call<InquiryResp> getInquiryPublicDetail(@Path("id") Long id,
                                             @Query("password") String password);

    /** (첨부 다운로드: 내 문의 / 공개) */
    @GET("/api/inquiries/{inquiryId}/attachments/{attId}")
    Call<ResponseBody> downloadInquiryAttachment(@Header("Authorization") String bearer,
                                                 @Header("X-User-Id") String userId,
                                                 @Header("X-User-Email") String email,
                                                 @Header("X-User-Role") String role,
                                                 @Path("inquiryId") Long inquiryId,
                                                 @Path("attId") Long attId,
                                                 @Query("inline") boolean inline);

    @GET("/api/inquiries/{inquiryId}/attachments/{attId}/public")
    Call<ResponseBody> downloadInquiryAttachmentPublic(@Path("inquiryId") Long inquiryId,
                                                       @Path("attId") Long attId,
                                                       @Query("password") String password,
                                                       @Query("inline") boolean inline);

    /** (문의 작성: multipart, 로그인/비로그인 모두 가능 — 서버는 X-User-* 또는 JWT로 식별) */
    @Multipart
    @POST("/api/inquiries")
    Call<InquiryResp> createInquiry(@Header("X-User-Id") String userId,
                                    @Header("X-User-Email") String email,
                                    @Header("X-User-Role") String role,
                                    @Part("title") RequestBody title,
                                    @Part("content") RequestBody content,
                                    @Part("name") RequestBody name,
                                    @Part("secret") RequestBody secret,
                                    @Part("password") RequestBody password,
                                    @Part List<MultipartBody.Part> files);



}
