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

    // 노선 유형(선택)
    private static final String ARG_ROUTE_TYPE_CODE = "route_type_code";
    private static final String ARG_ROUTE_TYPE_NAME = "route_type_name";

    private ImageView btnFavorite; // 우선 btnFavoriteActive, 없으면 btnFavorite
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

    /** 기존(표시 위주) 팩토리 */
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

    /** 확장: 노선유형 없이(13개 인자) */
    public static ReservationBottomSheet newInstanceFull(
            String routeId, @Nullable String routeName, @Nullable String direction,
            String boardStopId, @Nullable String boardStopName, @Nullable String boardArsId,
            String destStopId, @Nullable String destStopName, @Nullable String destArsId,
            boolean isFavorite, @Nullable Long favoriteId
    ) {
        return newInstance(routeId, routeName, direction,
                boardStopId, boardStopName, boardArsId,
                destStopId, destStopName, destArsId,
                isFavorite, favoriteId);
    }

    /** 확장: 노선유형 포함(15개 인자) */
    public static ReservationBottomSheet newInstanceFull(
            String routeId, @Nullable String routeName, @Nullable String direction,
            String boardStopId, @Nullable String boardStopName, @Nullable String boardArsId,
            String destStopId, @Nullable String destStopName, @Nullable String destArsId,
            boolean isFavorite, @Nullable Long favoriteId,
            @Nullable Integer routeTypeCode, @Nullable String routeTypeName
    ) {
        ReservationBottomSheet sheet = newInstance(
                routeId, routeName, direction,
                boardStopId, boardStopName, boardArsId,
                destStopId, destStopName, destArsId,
                isFavorite, favoriteId
        );
        Bundle b = sheet.getArguments();
        if (b == null) b = new Bundle();
        if (routeTypeCode != null) b.putInt(ARG_ROUTE_TYPE_CODE, routeTypeCode);
        if (!TextUtils.isEmpty(routeTypeName)) b.putString(ARG_ROUTE_TYPE_NAME, routeTypeName);
        sheet.setArguments(b);
        return sheet;
    }

    @Nullable @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.reservation_bottomsheet, container, false);

        // ★ btnFavoriteActive 먼저, 없으면 btnFavorite로 폴백
        btnFavorite = v.findViewById(R.id.btnFavoriteActive);
        if (btnFavorite == null) btnFavorite = v.findViewById(R.id.btnFavoriteActive);

        tvBusNumber     = v.findViewById(R.id.tvBusNumber);
        tvBusDirection  = v.findViewById(R.id.tvBusDirection);
        tvRidingStation = v.findViewById(R.id.riging_station);
        tvOutStation    = v.findViewById(R.id.out_station);

        View exit = v.findViewById(R.id.exit_button);
        if (exit != null) exit.setOnClickListener(view -> dismiss());
        View reserve = v.findViewById(R.id.btnReserve);
        if (reserve != null) reserve.setOnClickListener(view -> dismiss());

        Bundle args = getArguments() == null ? new Bundle() : getArguments();

        // ==== API 원본 값 ====
        final String api_routeId       = args.getString(ARG_ROUTE_ID);
        final String api_routeName     = args.getString(ARG_ROUTE_NAME);
        final String api_direction     = args.getString(ARG_DIRECTION);
        final String api_boardStopId   = args.getString(ARG_BOARD_STOP_ID);
        final String api_boardStopName = args.getString(ARG_BOARD_STOP_NAME);
        final String api_boardArsId    = args.getString(ARG_BOARD_ARS_ID);
        final String api_destStopId    = args.getString(ARG_DEST_STOP_ID);
        final String api_destStopName  = args.getString(ARG_DEST_STOP_NAME);
        final String api_destArsId     = args.getString(ARG_DEST_ARS_ID);

        // ==== 유형 값(선택) ====
        final Integer api_routeTypeCode = args.containsKey(ARG_ROUTE_TYPE_CODE) ? args.getInt(ARG_ROUTE_TYPE_CODE) : null;
        final String  api_routeTypeName = args.getString(ARG_ROUTE_TYPE_NAME, null);

        // ==== UI 표시용 ====
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

        // ==== 버스 아이콘 색상 적용 (노선유형별) ====
        ImageView busIconBottom = v.findViewById(R.id.imgBusIconbottom);
        if (busIconBottom != null) {
            // 단색(or body 레이어) 아이콘 보장
            busIconBottom.setImageResource(R.drawable.vector);

            // 로컬 매핑으로 실제 색 계산(@ColorInt)
            int color = localBusColorInt(api_routeTypeCode, api_routeTypeName);

            // 틴트 적용
            ImageViewCompat.setImageTintList(busIconBottom, ColorStateList.valueOf(color));
            // 필요시 모드 지정 (대부분 기본값으로 충분)
            // ImageViewCompat.setImageTintMode(busIconBottom, PorterDuff.Mode.SRC_IN);
        }

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

                boolean hasMinimumKeys =
                        !isEmpty(api_routeId) && !isEmpty(api_boardStopId) && !isEmpty(api_destStopId);

                if (!hasMinimumKeys) {
                    isFavorite = !isFavorite;
                    applyStarIcon(isFavorite);
                    Toast.makeText(requireContext(), "즐겨찾기 정보가 부족해 서버 동기화 없이 표시만 변경합니다.", Toast.LENGTH_SHORT).show();
                    return;
                }

                busy = true;
                btnFavorite.setEnabled(false);

                if (!isFavorite) {
                    // ===== 추가 =====
                    ApiService.FavoriteCreateRequest body;
                    if (api_routeTypeCode != null || !TextUtils.isEmpty(api_routeTypeName)) {
                        // 유형 포함 생성자(있어야 함)
                        body = new ApiService.FavoriteCreateRequest(
                                api_routeId, api_direction,
                                api_boardStopId, api_boardStopName, api_boardArsId,
                                api_destStopId, api_destStopName, api_destArsId,
                                api_routeName,
                                api_routeTypeCode, api_routeTypeName
                        );
                    } else {
                        // 기존 생성자
                        body = new ApiService.FavoriteCreateRequest(
                                api_routeId, api_direction,
                                api_boardStopId, api_boardStopName, api_boardArsId,
                                api_destStopId, api_destStopName, api_destArsId,
                                api_routeName
                        );
                    }

                    ApiClient.get().addFavorite(bearer, body)
                            .enqueue(new Callback<ApiService.FavoriteResponse>() {
                                @Override public void onResponse(
                                        Call<ApiService.FavoriteResponse> call,
                                        Response<ApiService.FavoriteResponse> res) {
                                    try {
                                        if (res.isSuccessful() && res.body()!=null) {
                                            isFavorite = true;
                                            favoriteId = res.body().id;
                                            applyStarIcon(true);
                                            Toast.makeText(requireContext(), "즐겨찾기에 추가되었습니다.", Toast.LENGTH_SHORT).show();
                                            if (onFavoriteChangedListener != null)
                                                onFavoriteChangedListener.onFavoriteChanged(true, favoriteId);
                                        } else if (res.code() == 409) {
                                            // 이미 존재 → ID 동기화
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
                                    } finally {
                                        busy = false;
                                        btnFavorite.setEnabled(true);
                                    }
                                }
                                @Override public void onFailure(Call<ApiService.FavoriteResponse> call, Throwable t) {
                                    busy = false;
                                    btnFavorite.setEnabled(true);
                                    Toast.makeText(requireContext(), "네트워크 오류로 추가 실패", Toast.LENGTH_SHORT).show();
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
                        resolveFavoriteIdThen(bearer, api_routeId, api_direction, api_boardStopId, api_destStopId, id -> {
                            if (id == null) {
                                busy = false;
                                btnFavorite.setEnabled(true);
                                Toast.makeText(requireContext(), "삭제할 즐겨찾기를 찾지 못했습니다.", Toast.LENGTH_SHORT).show();
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

    // 노선유형 → 실제 색값(@ColorInt) 로컬 매핑
    private int localBusColorInt(@Nullable Integer code, @Nullable String name) {
        if (code != null) {
            switch (code) {
                case 3: return Color.parseColor("#2B7DE9"); // 간선(파랑)
                case 4: return Color.parseColor("#42A05B"); // 지선(초록)
                case 6: return Color.parseColor("#D2473B"); // 광역(빨강)
                case 5: return Color.parseColor("#E3B021"); // 순환(노랑)
                case 2: return Color.parseColor("#42A05B"); // 마을=초록 취급
                case 8: return Color.parseColor("#42A05B"); // 경기=초록 취급(필요시 분리)
                case 1: return Color.parseColor("#7E57C2"); // 공항(예시)
                default: return Color.parseColor("#42A05B"); // 기본 초록
            }
        }
        if (!TextUtils.isEmpty(name)) {
            switch (name.trim()) {
                case "간선": return Color.parseColor("#2B7DE9");
                case "지선": return Color.parseColor("#42A05B");
                case "광역": return Color.parseColor("#D2473B");
                case "순환": return Color.parseColor("#E3B021");
                case "마을": return Color.parseColor("#42A05B");
                case "경기": return Color.parseColor("#42A05B");
                case "공항": return Color.parseColor("#7E57C2");
            }
        }
        return Color.parseColor("#42A05B");
    }

    private void deleteFavorite(String bearer, long id, Runnable onOk) {
        ApiClient.get().deleteFavorite(bearer, id)
                .enqueue(new Callback<Void>() {
                    @Override public void onResponse(Call<Void> call, Response<Void> res) {
                        busy = false;
                        if (btnFavorite != null) btnFavorite.setEnabled(true);
                        if (res.isSuccessful() || res.code()==404) {
                            onOk.run();
                        } else if (res.code()==401) {
                            Toast.makeText(requireContext(), "로그인이 만료되었습니다.", Toast.LENGTH_SHORT).show();
                            LoginRequiredDialogFragment.show(getParentFragmentManager());
                        } else {
                            Toast.makeText(requireContext(), "삭제 실패 ("+res.code()+")", Toast.LENGTH_SHORT).show();
                        }
                    }
                    @Override public void onFailure(Call<Void> call, Throwable t) {
                        busy = false;
                        if (btnFavorite != null) btnFavorite.setEnabled(true);
                        Toast.makeText(requireContext(), "네트워크 오류로 삭제 실패", Toast.LENGTH_SHORT).show();
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

    /** 별 아이콘 스타일/애니메이션 */
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
