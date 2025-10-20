package com.example.testmap.ui;

import android.content.res.ColorStateList;
import android.graphics.Color;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.TooltipCompat;
import androidx.core.widget.ImageViewCompat;

import com.example.testmap.R;
import com.example.testmap.service.ApiClient;
import com.example.testmap.service.ApiService;
import com.example.testmap.util.TokenStore;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;

import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class ReservationBottomSheet extends BottomSheetDialogFragment {

    private static final String ARG_ROUTE_ID        = "route_id";
    private static final String ARG_ROUTE_NAME      = "route_name";
    private static final String ARG_DIRECTION       = "direction";
    private static final String ARG_BOARD_STOP_ID   = "board_stop_id";
    private static final String ARG_BOARD_STOP_NAME = "board_stop_name";
    private static final String ARG_BOARD_ARS_ID    = "board_ars_id";
    private static final String ARG_DEST_STOP_ID    = "dest_stop_id";
    private static final String ARG_DEST_STOP_NAME  = "dest_stop_name";
    private static final String ARG_DEST_ARS_ID     = "dest_ars_id";
    private static final String ARG_IS_FAVORITE     = "is_favorite";
    private static final String ARG_FAVORITE_ID     = "favorite_id";

    private ImageView btnFavorite; // 실제로는 btnFavoriteActive를 우선 찾고, 없으면 btnFavorite로 폴백
    private TextView tvBusNumber, tvBusDirection, tvRidingStation, tvOutStation;

    private boolean isFavorite = false;
    private Long favoriteId = null;
    private boolean busy = false; // 중복 탭 방지

    public interface OnFavoriteChangedListener {
        void onFavoriteChanged(boolean nowFavorite, @Nullable Long newFavoriteId);
    }
    private OnFavoriteChangedListener onFavoriteChangedListener;
    public void setOnFavoriteChangedListener(OnFavoriteChangedListener l) {
        this.onFavoriteChangedListener = l;
    }

    public static ReservationBottomSheet newInstance(
            String routeId,
            @Nullable String routeName,
            @Nullable String direction,
            String boardStopId,
            @Nullable String boardStopName,
            @Nullable String boardArsId,
            String destStopId,
            @Nullable String destStopName,
            @Nullable String destArsId,
            boolean isFavorite,
            @Nullable Long favoriteId
    ) {
        ReservationBottomSheet sheet = new ReservationBottomSheet();
        Bundle b = new Bundle();
        b.putString(ARG_ROUTE_ID, routeId);
        b.putString(ARG_ROUTE_NAME, routeName);
        b.putString(ARG_DIRECTION, direction);
        b.putString(ARG_BOARD_STOP_ID, boardStopId);
        b.putString(ARG_BOARD_STOP_NAME, boardStopName);
        b.putString(ARG_BOARD_ARS_ID, boardArsId);
        b.putString(ARG_DEST_STOP_ID, destStopId);
        b.putString(ARG_DEST_STOP_NAME, destStopName);
        b.putString(ARG_DEST_ARS_ID, destArsId);
        b.putBoolean(ARG_IS_FAVORITE, isFavorite);
        if (favoriteId != null) b.putLong(ARG_FAVORITE_ID, favoriteId);
        sheet.setArguments(b);
        return sheet;
    }

    @Nullable @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.reservation_bottomsheet, container, false);

        // ★ 우선 btnFavoriteActive를 찾고, 없으면 btnFavorite로 폴백 (XML 교체 전후 모두 동작)
        btnFavorite      = v.findViewById(R.id.btnFavoriteActive);
        if (btnFavorite == null) btnFavorite = v.findViewById(R.id.btnFavoriteActive);

        tvBusNumber      = v.findViewById(R.id.tvBusNumber);
        tvBusDirection   = v.findViewById(R.id.tvBusDirection);
        tvRidingStation  = v.findViewById(R.id.riging_station);
        tvOutStation     = v.findViewById(R.id.out_station);

        View exit = v.findViewById(R.id.exit_button);
        if (exit != null) exit.setOnClickListener(view -> dismiss());
        View reserve = v.findViewById(R.id.btnReserve);
        if (reserve != null) reserve.setOnClickListener(view -> dismiss());

        Bundle args = getArguments() == null ? new Bundle() : getArguments();

        // ==== API로 보낼 원본 값들(null 허용 그대로 유지) ====
        final String api_routeId       = args.getString(ARG_ROUTE_ID);
        final String api_routeName     = args.getString(ARG_ROUTE_NAME);
        final String api_direction     = args.getString(ARG_DIRECTION);
        final String api_boardStopId   = args.getString(ARG_BOARD_STOP_ID);
        final String api_boardStopName = args.getString(ARG_BOARD_STOP_NAME);
        final String api_boardArsId    = args.getString(ARG_BOARD_ARS_ID);
        final String api_destStopId    = args.getString(ARG_DEST_STOP_ID);
        final String api_destStopName  = args.getString(ARG_DEST_STOP_NAME);
        final String api_destArsId     = args.getString(ARG_DEST_ARS_ID);

        // ==== 화면 표시용 안전 문자열 ====
        final String ui_routeName = !TextUtils.isEmpty(api_routeName) ? api_routeName : (api_routeId == null ? "" : api_routeId);
        final String ui_direction = api_direction == null ? "" : api_direction;
        final String ui_boardName = api_boardStopName == null ? "" : api_boardStopName;
        final String ui_destName  = api_destStopName  == null ? "" : api_destStopName;

        isFavorite = args.getBoolean(ARG_IS_FAVORITE, false);
        favoriteId = args.containsKey(ARG_FAVORITE_ID) ? args.getLong(ARG_FAVORITE_ID) : null;

        tvBusNumber.setText(ui_routeName);
        tvBusDirection.setText(ui_direction);
        tvRidingStation.setText(ui_boardName);
        tvOutStation.setText(ui_destName);
        applyStarIcon(isFavorite);

        if (btnFavorite != null) {
            btnFavorite.setOnClickListener(v1 -> {
                if (busy) return;

                String access = TokenStore.getAccess(requireContext());
                if (TextUtils.isEmpty(access)) {
                    Toast.makeText(requireContext(), "로그인이 필요합니다.", Toast.LENGTH_SHORT).show();
                    LoginRequiredDialogFragment.show(getParentFragmentManager());
                    return;
                }
                final String bearer = "Bearer " + access;

                // 서버에 보낼 최소 키가 있는지 확인
                boolean hasMinimumKeys =
                        !isEmpty(api_routeId) && !isEmpty(api_boardStopId) && !isEmpty(api_destStopId);

                if (!hasMinimumKeys) {
                    // 서버 연동 불가 → UI만 토글
                    isFavorite = !isFavorite;
                    applyStarIcon(isFavorite);
                    Toast.makeText(requireContext(), "즐겨찾기 정보가 부족해 서버 동기화 없이 표시만 변경합니다.", Toast.LENGTH_SHORT).show();
                    return;
                }

                busy = true;
                btnFavorite.setEnabled(false);

                if (!isFavorite) {
                    // ===== 추가 =====
                    ApiService.FavoriteCreateRequest body =
                            new ApiService.FavoriteCreateRequest(
                                    api_routeId, api_direction,
                                    api_boardStopId, api_boardStopName, api_boardArsId,
                                    api_destStopId, api_destStopName, api_destArsId,
                                    api_routeName
                            );

                    ApiClient.get().addFavorite(bearer, body)
                            .enqueue(new Callback<ApiService.FavoriteResponse>() {
                                @Override public void onResponse(
                                        Call<ApiService.FavoriteResponse> call,
                                        Response<ApiService.FavoriteResponse> res) {
                                    if (res.isSuccessful() && res.body()!=null) {
                                        isFavorite = true;
                                        favoriteId = res.body().id;
                                        applyStarIcon(true);
                                        Toast.makeText(requireContext(), "즐겨찾기에 추가되었습니다.", Toast.LENGTH_SHORT).show();
                                        if (onFavoriteChangedListener != null)
                                            onFavoriteChangedListener.onFavoriteChanged(true, favoriteId);
                                    } else if (res.code() == 409) {
                                        // 이미 존재 → ID 해석해서 동기화
                                        resolveFavoriteIdThen(bearer, api_routeId, api_direction, api_boardStopId, api_destStopId, id -> {
                                            isFavorite = true;
                                            if (id != null) favoriteId = id;
                                            applyStarIcon(true);
                                            if (onFavoriteChangedListener != null)
                                                onFavoriteChangedListener.onFavoriteChanged(true, favoriteId);
                                            Toast.makeText(requireContext(), "이미 즐겨찾기에 있습니다.", Toast.LENGTH_SHORT).show();
                                        });
                                    } else if (res.code() == 401) {
                                        Toast.makeText(requireContext(), "로그인이 만료되었습니다.", Toast.LENGTH_SHORT).show();
                                        LoginRequiredDialogFragment.show(getParentFragmentManager());
                                    } else {
                                        Toast.makeText(requireContext(), "추가 실패 ("+res.code()+")", Toast.LENGTH_SHORT).show();
                                    }
                                    busy = false;
                                    btnFavorite.setEnabled(true);
                                }
                                @Override public void onFailure(Call<ApiService.FavoriteResponse> call, Throwable t) {
                                    Toast.makeText(requireContext(), "네트워크 오류로 추가 실패", Toast.LENGTH_SHORT).show();
                                    busy = false;
                                    btnFavorite.setEnabled(true);
                                }
                            });

                } else {
                    // ===== 삭제 =====
                    Runnable finalizeUi = () -> {
                        isFavorite = false;
                        favoriteId = null;
                        applyStarIcon(false);
                        Toast.makeText(requireContext(), "즐겨찾기에서 제거되었습니다.", Toast.LENGTH_SHORT).show();
                        if (onFavoriteChangedListener != null)
                            onFavoriteChangedListener.onFavoriteChanged(false, null);
                    };

                    if (favoriteId == null || favoriteId <= 0) {
                        // ID 모르면 먼저 해석
                        resolveFavoriteIdThen(bearer, api_routeId, api_direction, api_boardStopId, api_destStopId, id -> {
                            if (id == null) {
                                Toast.makeText(requireContext(), "삭제할 즐겨찾기를 찾지 못했습니다.", Toast.LENGTH_SHORT).show();
                                busy = false;
                                btnFavorite.setEnabled(true);
                                return;
                            }
                            favoriteId = id;
                            deleteFavorite(bearer, favoriteId, finalizeUi);
                        });
                    } else {
                        deleteFavorite(bearer, favoriteId, finalizeUi);
                    }
                }
            });
        }

        return v;
    }

    // ===== 유틸 / 공통 네트워크 처리 =====

    private void deleteFavorite(String bearer, long id, Runnable onOk) {
        ApiClient.get().deleteFavorite(bearer, id)
                .enqueue(new Callback<Void>() {
                    @Override public void onResponse(Call<Void> call, Response<Void> res) {
                        if (res.isSuccessful() || res.code()==404) {
                            onOk.run();
                        } else if (res.code()==401) {
                            Toast.makeText(requireContext(), "로그인이 만료되었습니다.", Toast.LENGTH_SHORT).show();
                            LoginRequiredDialogFragment.show(getParentFragmentManager());
                        } else {
                            Toast.makeText(requireContext(), "삭제 실패 ("+res.code()+")", Toast.LENGTH_SHORT).show();
                        }
                        busy = false;
                        if (btnFavorite != null) btnFavorite.setEnabled(true);
                    }
                    @Override public void onFailure(Call<Void> call, Throwable t) {
                        Toast.makeText(requireContext(), "네트워크 오류로 삭제 실패", Toast.LENGTH_SHORT).show();
                        busy = false;
                        if (btnFavorite != null) btnFavorite.setEnabled(true);
                    }
                });
    }

    private interface IdCallback { void onResolved(@Nullable Long id); }

    private void resolveFavoriteIdThen(String bearer,
                                       String routeId, @Nullable String direction,
                                       String boardStopId, String destStopId,
                                       IdCallback cb) {
        ApiClient.get().getFavorites(bearer)
                .enqueue(new Callback<List<ApiService.FavoriteResponse>>() {
                    @Override public void onResponse(Call<List<ApiService.FavoriteResponse>> call,
                                                     Response<List<ApiService.FavoriteResponse>> res) {
                        if (!res.isSuccessful() || res.body()==null) {
                            cb.onResolved(null); return;
                        }
                        Long id = findMatchingFavoriteId(
                                res.body(),
                                routeId,
                                nullToEmpty(direction),
                                boardStopId,
                                destStopId
                        );
                        cb.onResolved(id);
                    }
                    @Override public void onFailure(Call<List<ApiService.FavoriteResponse>> call, Throwable t) {
                        cb.onResolved(null);
                    }
                });
    }

    private Long findMatchingFavoriteId(List<ApiService.FavoriteResponse> list,
                                        String routeId, String direction,
                                        String boardStopId, String destStopId) {
        for (ApiService.FavoriteResponse f : list) {
            boolean same =
                    TextUtils.equals(f.routeId, routeId) &&
                            TextUtils.equals(nullToEmpty(f.direction), nullToEmpty(direction)) &&
                            TextUtils.equals(f.boardStopId, boardStopId) &&
                            TextUtils.equals(f.destStopId, destStopId);
            if (same) return f.id;
        }
        return null;
    }

    private static String nullToEmpty(@Nullable String s) { return s == null ? "" : s; }
    private static boolean isEmpty(@Nullable String s) { return s == null || s.trim().isEmpty(); }

    /** 바텀시트/다이얼로그와 동일한 별 아이콘 스타일 적용 */
    private void applyStarIcon(boolean fav) {
        if (btnFavorite == null) return;
        btnFavorite.setImageResource(R.drawable.ic_star2);
        int color = fav ? Color.parseColor("#FFC107") : Color.parseColor("#BDBDBD");
        ImageViewCompat.setImageTintList(btnFavorite, ColorStateList.valueOf(color));

        String tip = fav ? "즐겨찾기 제거" : "즐겨찾기 추가";
        btnFavorite.setContentDescription(tip);
        TooltipCompat.setTooltipText(btnFavorite, tip);

        btnFavorite.animate()
                .scaleX(1.12f).scaleY(1.12f)
                .setDuration(120)
                .withEndAction(() ->
                        btnFavorite.animate().scaleX(1f).scaleY(1f).setDuration(90).start()
                ).start();
    }
}
