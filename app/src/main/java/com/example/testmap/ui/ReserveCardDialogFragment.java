package com.example.testmap.ui;

import android.app.Dialog;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.TooltipCompat;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.drawable.DrawableCompat;
import androidx.core.widget.ImageViewCompat;
import androidx.fragment.app.DialogFragment;

import android.content.res.ColorStateList;
import android.widget.ImageView;

import com.example.testmap.R;
import com.example.testmap.service.ApiClient;
import com.example.testmap.service.ApiService;
import com.example.testmap.util.BusColors;
import com.example.testmap.util.TokenStore;

import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class ReserveCardDialogFragment extends DialogFragment {

    public interface OnActionListener {
        void onReserveClicked(boolean boardingAlarm, boolean dropOffAlarm);
        void onCancelClicked();
        default void onFavoriteChanged(boolean isFav, Long favId) {}
    }

    // ===== 표시용 기본 아규먼트 =====
    private static final String ARG_BUS_NO = "bus_no";
    private static final String ARG_DIR    = "dir";
    private static final String ARG_FROM   = "from";
    private static final String ARG_TO     = "to";

    // ===== 즐겨찾기 토글용(서버 연동) 아규먼트 =====
    private static final String ARG_ROUTE_ID    = "route_id";
    private static final String ARG_DIRECTION   = "direction";
    private static final String ARG_BOARD_ID    = "board_id";
    private static final String ARG_BOARD_NAME  = "board_name";
    private static final String ARG_BOARD_ARS   = "board_ars";
    private static final String ARG_DEST_ID     = "dest_id";
    private static final String ARG_DEST_NAME   = "dest_name";
    private static final String ARG_DEST_ARS    = "dest_ars";
    private static final String ARG_ROUTE_NAME  = "route_name";

    // 초기 즐겨찾기 상태
    private static final String ARG_IS_FAVORITE = "is_favorite";
    private static final String ARG_FAVORITE_ID = "favorite_id";

    // 노선 유형(선택)
    private static final String ARG_ROUTE_TYPE_CODE = "route_type_code";
    private static final String ARG_ROUTE_TYPE_NAME = "route_type_name";

    private OnActionListener listener;
    public void setOnActionListener(OnActionListener l) { this.listener = l; }

    /** 표시만 (서버 연동 없이 UI 토글만 가능) */
    public static ReserveCardDialogFragment newInstance(String busNo, String dir, String from, String to) {
        ReserveCardDialogFragment f = new ReserveCardDialogFragment();
        Bundle b = new Bundle();
        b.putString(ARG_BUS_NO, busNo);
        b.putString(ARG_DIR,    dir);
        b.putString(ARG_FROM,   from);
        b.putString(ARG_TO,     to);
        f.setArguments(b);
        return f;
    }

    /** 서버 연동(13개 인자) */
    public static ReserveCardDialogFragment newInstanceFull(
            // 표시용
            String busNo, String dirLabel, String fromLabel, String toLabel,
            // 서버 연동용
            String routeId, String direction,
            String boardId, String boardName, String boardArs,
            String destId,  String destName,  String destArs,
            String routeName,
            boolean isFavorite, Long favoriteId
    ) {
        ReserveCardDialogFragment f = new ReserveCardDialogFragment();
        Bundle b = new Bundle();

        b.putString(ARG_BUS_NO, busNo);
        b.putString(ARG_DIR,    dirLabel);
        b.putString(ARG_FROM,   fromLabel);
        b.putString(ARG_TO,     toLabel);

        b.putString(ARG_ROUTE_ID,   routeId);
        b.putString(ARG_DIRECTION,  direction);
        b.putString(ARG_BOARD_ID,   boardId);
        b.putString(ARG_BOARD_NAME, boardName);
        b.putString(ARG_BOARD_ARS,  boardArs);
        b.putString(ARG_DEST_ID,    destId);
        b.putString(ARG_DEST_NAME,  destName);
        b.putString(ARG_DEST_ARS,   destArs);
        b.putString(ARG_ROUTE_NAME, routeName);

        b.putBoolean(ARG_IS_FAVORITE, isFavorite);
        if (favoriteId != null) b.putLong(ARG_FAVORITE_ID, favoriteId);

        f.setArguments(b);
        return f;
    }

    /** 서버 연동 + 노선유형 포함(15개 인자) */
    public static ReserveCardDialogFragment newInstanceFull(
            // 표시용
            String busNo, String dirLabel, String fromLabel, String toLabel,
            // 서버 연동용
            String routeId, String direction,
            String boardId, String boardName, String boardArs,
            String destId,  String destName,  String destArs,
            String routeName,
            boolean isFavorite, Long favoriteId,
            @Nullable Integer routeTypeCode, @Nullable String routeTypeName
    ) {
        ReserveCardDialogFragment f = newInstanceFull(
                busNo, dirLabel, fromLabel, toLabel,
                routeId, direction,
                boardId, boardName, boardArs,
                destId, destName, destArs,
                routeName, isFavorite, favoriteId
        );
        Bundle b = f.getArguments();
        if (b == null) b = new Bundle();
        if (routeTypeCode != null) b.putInt(ARG_ROUTE_TYPE_CODE, routeTypeCode);
        if (!TextUtils.isEmpty(routeTypeName)) b.putString(ARG_ROUTE_TYPE_NAME, routeTypeName);
        f.setArguments(b);
        return f;
    }

    @NonNull @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        View v = LayoutInflater.from(requireContext()).inflate(R.layout.reserve_screen, null, false);

        // ▼ 눌림/리플 상태 강제 종료
        clearPressedRecursive(v);
        v.jumpDrawablesToCurrentState();

        Bundle args = getArguments() != null ? getArguments() : new Bundle();

        // ===== 표시 데이터 바인딩 =====
        ImageView busIcon = v.findViewById(R.id.imgBusIcon);
        String busNo = args.getString(ARG_BUS_NO, "");
        String dir   = args.getString(ARG_DIR,    "");
        String from  = args.getString(ARG_FROM,   "");
        String to    = args.getString(ARG_TO,     "");

        ((android.widget.TextView) v.findViewById(R.id.tvBusNumber)).setText(busNo);
        ((android.widget.TextView) v.findViewById(R.id.tvBusDirection)).setText(dir + " 방면");
        ((android.widget.TextView) v.findViewById(R.id.riging_station)).setText(from);
        ((android.widget.TextView) v.findViewById(R.id.out_station)).setText(to);

        // ===== ARS 개별 표시 =====
        String boardArs = args.getString(ARG_BOARD_ARS);
        String destArs  = args.getString(ARG_DEST_ARS);

        android.widget.TextView ridingArsTv = v.findViewById(R.id.arrival_information_riding);
        android.widget.TextView outArsTv    = v.findViewById(R.id.arrival_information);

        if (ridingArsTv != null) {
            if (!TextUtils.isEmpty(boardArs)) {
                ridingArsTv.setText(boardArs);
                ridingArsTv.setVisibility(View.VISIBLE);
            } else {
                ridingArsTv.setVisibility(View.GONE);
            }
        }
        if (outArsTv != null) {
            if (!TextUtils.isEmpty(destArs)) {
                outArsTv.setText(destArs);
                outArsTv.setVisibility(View.VISIBLE);
            } else {
                outArsTv.setVisibility(View.GONE);
            }
        }

        // ===== 아이콘 색상 적용 (노선유형별) =====
        if (busIcon != null) {
            Integer typeCodeArg = getArguments().containsKey(ARG_ROUTE_TYPE_CODE)
                    ? (Integer) getArguments().get(ARG_ROUTE_TYPE_CODE) : null;
            String  typeNameArg = getArguments().getString(ARG_ROUTE_TYPE_NAME, null);

            int color = localBusColorInt(typeCodeArg, typeNameArg); // ← 여기!

            busIcon.setImageResource(R.drawable.vector); // 단색/레이어 버스 아이콘
            tintBusIcon(busIcon, color);                // 이미 클래스에 있는 유틸
            // 또는: ImageViewCompat.setImageTintList(busIcon, ColorStateList.valueOf(color));
        }

        // 체크박스 상호배타
        android.widget.CheckBox cbBoarding = v.findViewById(R.id.checkBoardingAlarm);
        android.widget.CheckBox cbDropOff  = v.findViewById(R.id.checkDropOffAlarm);
        cbBoarding.setOnCheckedChangeListener((b, checked) -> { if (checked) cbDropOff.setChecked(false); });
        cbDropOff.setOnCheckedChangeListener((b, checked) -> { if (checked) cbBoarding.setChecked(false); });

        v.findViewById(R.id.btnCancel).setOnClickListener(view -> {
            if (listener != null) listener.onCancelClicked();
            dismiss();
        });
        v.findViewById(R.id.btnReserve).setOnClickListener(view -> {
            boolean boarding = cbBoarding.isChecked();
            boolean dropOff  = cbDropOff.isChecked();
            if (listener != null) listener.onReserveClicked(boarding, dropOff);
        });

        // ===== 즐겨찾기 토글 =====
        android.widget.ImageView star = v.findViewById(R.id.btnFavoriteActive);

        final boolean[] isFav = { args.getBoolean(ARG_IS_FAVORITE, false) };
        final long[] favId    = { args.containsKey(ARG_FAVORITE_ID) ? args.getLong(ARG_FAVORITE_ID) : -1L };

        applyStar(star, isFav[0]);

        star.setOnClickListener(view -> {
            boolean hasAllParams =
                    !isEmpty(args.getString(ARG_ROUTE_ID)) &&
                            !isEmpty(args.getString(ARG_BOARD_ID)) &&
                            !isEmpty(args.getString(ARG_DEST_ID));

            String access = TokenStore.getAccess(requireContext());
            String bearer = !TextUtils.isEmpty(access) ? ("Bearer " + access) : null;

            if (bearer == null) {
                android.widget.Toast.makeText(requireContext(), "로그인이 필요합니다.", android.widget.Toast.LENGTH_SHORT).show();
                LoginRequiredDialogFragment.show(getParentFragmentManager());
                return;
            }

            if (!hasAllParams) {
                isFav[0] = !isFav[0];
                applyStar(star, isFav[0]);
                android.widget.Toast.makeText(requireContext(), "즐겨찾기 정보가 부족해 서버 동기화 없이 표시만 바꿔요.", android.widget.Toast.LENGTH_SHORT).show();
                return;
            }

            if (isFav[0]) {
                if (favId[0] <= 0) {
                    resolveFavoriteIdThen(bearer, args, id -> {
                        if (id == null) {
                            android.widget.Toast.makeText(requireContext(), "삭제할 즐겨찾기를 찾지 못했습니다.", android.widget.Toast.LENGTH_SHORT).show();
                            return;
                        }
                        favId[0] = id;
                        deleteFavorite(bearer, favId[0], () -> {
                            isFav[0] = false;
                            favId[0] = -1L;
                            applyStar(star, false);
                            if (listener != null) listener.onFavoriteChanged(false, null);
                        });
                    });
                } else {
                    deleteFavorite(bearer, favId[0], () -> {
                        isFav[0] = false;
                        favId[0] = -1L;
                        applyStar(star, false);
                        if (listener != null) listener.onFavoriteChanged(false, null);
                    });
                }
            } else {
                Integer typeCode = args.containsKey(ARG_ROUTE_TYPE_CODE) ? args.getInt(ARG_ROUTE_TYPE_CODE) : null;
                String  typeName = args.getString(ARG_ROUTE_TYPE_NAME, null);

                ApiService.FavoriteCreateRequest body;
                if (typeCode != null || !TextUtils.isEmpty(typeName)) {
                    body = new ApiService.FavoriteCreateRequest(
                            args.getString(ARG_ROUTE_ID),
                            args.getString(ARG_DIRECTION),
                            args.getString(ARG_BOARD_ID),
                            args.getString(ARG_BOARD_NAME),
                            args.getString(ARG_BOARD_ARS),
                            args.getString(ARG_DEST_ID),
                            args.getString(ARG_DEST_NAME),
                            args.getString(ARG_DEST_ARS),
                            args.getString(ARG_ROUTE_NAME),
                            typeCode,
                            typeName
                    );
                } else {
                    body = new ApiService.FavoriteCreateRequest(
                            args.getString(ARG_ROUTE_ID),
                            args.getString(ARG_DIRECTION),
                            args.getString(ARG_BOARD_ID),
                            args.getString(ARG_BOARD_NAME),
                            args.getString(ARG_BOARD_ARS),
                            args.getString(ARG_DEST_ID),
                            args.getString(ARG_DEST_NAME),
                            args.getString(ARG_DEST_ARS),
                            args.getString(ARG_ROUTE_NAME)
                    );
                }

                ApiClient.get().addFavorite(bearer, body).enqueue(new Callback<ApiService.FavoriteResponse>() {
                    @Override public void onResponse(Call<ApiService.FavoriteResponse> call, Response<ApiService.FavoriteResponse> res) {
                        if (res.isSuccessful() && res.body()!=null) {
                            isFav[0] = true;
                            favId[0] = res.body().id;
                            applyStar(star, true);
                            if (listener != null) listener.onFavoriteChanged(true, favId[0]);
                        } else if (res.code()==409) {
                            resolveFavoriteIdThen(bearer, args, id -> {
                                isFav[0] = true;
                                if (id != null) favId[0] = id;
                                applyStar(star, true);
                                if (listener != null) listener.onFavoriteChanged(true, favId[0] > 0 ? favId[0] : null);
                                android.widget.Toast.makeText(requireContext(), "이미 즐겨찾기에 있어요.", android.widget.Toast.LENGTH_SHORT).show();
                            });
                        } else if (res.code()==401) {
                            android.widget.Toast.makeText(requireContext(), "로그인이 만료되었습니다.", android.widget.Toast.LENGTH_SHORT).show();
                            LoginRequiredDialogFragment.show(getParentFragmentManager());
                        } else {
                            android.widget.Toast.makeText(requireContext(), "추가 실패("+res.code()+")", android.widget.Toast.LENGTH_SHORT).show();
                        }
                    }
                    @Override public void onFailure(Call<ApiService.FavoriteResponse> call, Throwable t) {
                        android.widget.Toast.makeText(requireContext(), "네트워크 오류", android.widget.Toast.LENGTH_SHORT).show();
                    }
                });
            }
        });

        // ★ 커스텀 Dialog
        Dialog dlg = new Dialog(requireContext(), R.style.HopOn_ReserveCard);
        dlg.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dlg.setContentView(v);

        Window w = dlg.getWindow();
        if (w != null) {
            w.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            w.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            w.setDimAmount(0.35f);
            int width = (int) (370 * requireContext().getResources().getDisplayMetrics().density);
            w.setLayout(width, WindowManager.LayoutParams.WRAP_CONTENT);
        }
        return dlg;
    }

    // ▼ 유틸
    // ReserveCardDialogFragment 안에 추가
    private int localBusColorInt(@Nullable Integer code, @Nullable String name) {
        // 코드 우선 → 없으면 한글 라벨로
        if (code != null) {
            switch (code) {
                case 3: return Color.parseColor("#2B7DE9"); // 간선(파랑)
                case 4: return Color.parseColor("#42A05B"); // 지선(초록)
                case 6: return Color.parseColor("#D2473B"); // 광역(빨강)
                case 5: return Color.parseColor("#E3B021"); // 순환(노랑)
                case 2: return Color.parseColor("#42A05B"); // 마을=초록 취급
                case 8: return Color.parseColor("#42A05B"); // 경기=초록 취급(필요시 분리)
                case 1: return Color.parseColor("#7E57C2"); // 공항(예시 보라)
                default: return Color.parseColor("#42A05B"); // 기본 초록
            }
        }
        if (!TextUtils.isEmpty(name)) {
            String label = name.trim();
            switch (label) {
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


    /** @ColorRes / @ColorInt를 모두 안전하게 처리 */
    private int resolveBusColor(@Nullable String label) {
        int c = com.example.testmap.util.BusColors.forRouteType(label);
        try {
            return ContextCompat.getColor(requireContext(), c);
        } catch (Exception ignored) {
            return c;
        }
    }

    /** 어떤 종류의 드로어블이 와도 색을 강제로 반영 */
    /** 어떤 종류의 드로어블이 와도 색을 강제로 반영 (LayerDrawable/Vector 대응) */
    private void tintBusIcon(@NonNull ImageView view, int color) {
        Drawable d = view.getDrawable();
        if (d == null) { // 아직 드로어블이 없으면 1차 폴백
            view.setColorFilter(color, PorterDuff.Mode.SRC_IN);
            return;
        }
        try {
            Drawable m = d.mutate();

            if (m instanceof android.graphics.drawable.LayerDrawable) {
                android.graphics.drawable.LayerDrawable ld = (android.graphics.drawable.LayerDrawable) m;

                // 레이어 중 "버스 몸통" 레이어가 있으면 그 레이어만 칠함
                // (ic_xxx.xml에서 android:id="@+id/bus_body" 로 지정되어 있어야 함)
                Drawable body = ld.findDrawableByLayerId(R.id.bus_body);
                if (body != null) {
                    body = DrawableCompat.wrap(body.mutate());
                    DrawableCompat.setTint(body, color);
                    DrawableCompat.setTintMode(body, PorterDuff.Mode.SRC_IN);
                } else {
                    // 없으면 전체에 틴트(폴백)
                    Drawable wrapped = DrawableCompat.wrap(m);
                    DrawableCompat.setTint(wrapped, color);
                    DrawableCompat.setTintMode(wrapped, PorterDuff.Mode.SRC_IN);
                }
                view.setImageDrawable(ld);

            } else {
                // 벡터/비트맵 등 단일 드로어블
                Drawable wrapped = DrawableCompat.wrap(m);
                DrawableCompat.setTint(wrapped, color);
                DrawableCompat.setTintMode(wrapped, PorterDuff.Mode.SRC_IN);
                view.setImageDrawable(wrapped);
            }
        } catch (Throwable ignore) {
            // 최종 폴백
            view.setColorFilter(color, PorterDuff.Mode.SRC_IN);
        }

        // 배경에도 아이콘이 들어있는 레이아웃을 대비(옵션)
        Drawable bg = view.getBackground();
        if (bg != null) {
            try {
                Drawable bgWrap = DrawableCompat.wrap(bg.mutate());
                DrawableCompat.setTint(bgWrap, color);
                DrawableCompat.setTintMode(bgWrap, PorterDuff.Mode.SRC_IN);
                view.setBackground(bgWrap);
            } catch (Throwable ignored) {
                bg.setColorFilter(color, PorterDuff.Mode.SRC_IN);
            }
        }
    }


    private void clearPressedRecursive(View root) {
        root.setPressed(false);
        root.jumpDrawablesToCurrentState();
        if (root instanceof ViewGroup) {
            ViewGroup vg = (ViewGroup) root;
            for (int i = 0; i < vg.getChildCount(); i++) {
                clearPressedRecursive(vg.getChildAt(i));
            }
        }
    }

    private interface IdCallback { void onResolved(Long id); }

    private void resolveFavoriteIdThen(String bearer, Bundle args, IdCallback cb) {
        ApiClient.get().getFavorites(bearer).enqueue(new Callback<List<ApiService.FavoriteResponse>>() {
            @Override public void onResponse(Call<List<ApiService.FavoriteResponse>> call, Response<List<ApiService.FavoriteResponse>> res) {
                if (!res.isSuccessful() || res.body() == null) { cb.onResolved(null); return; }
                Long id = findMatchingFavoriteId(
                        res.body(),
                        args.getString(ARG_ROUTE_ID),
                        nullToEmpty(args.getString(ARG_DIRECTION)),
                        args.getString(ARG_BOARD_ID),
                        args.getString(ARG_DEST_ID)
                );
                cb.onResolved(id);
            }
            @Override public void onFailure(Call<List<ApiService.FavoriteResponse>> call, Throwable t) {
                cb.onResolved(null);
            }
        });
    }

    private void deleteFavorite(String bearer, long id, Runnable onOk) {
        ApiClient.get().deleteFavorite(bearer, id).enqueue(new Callback<Void>() {
            @Override public void onResponse(Call<Void> call, Response<Void> res) {
                if (res.isSuccessful() || res.code()==404) {
                    onOk.run();
                } else if (res.code()==401) {
                    android.widget.Toast.makeText(requireContext(), "로그인이 만료되었습니다.", android.widget.Toast.LENGTH_SHORT).show();
                    LoginRequiredDialogFragment.show(getParentFragmentManager());
                } else {
                    android.widget.Toast.makeText(requireContext(), "삭제 실패("+res.code()+")", android.widget.Toast.LENGTH_SHORT).show();
                }
            }
            @Override public void onFailure(Call<Void> call, Throwable t) {
                android.widget.Toast.makeText(requireContext(), "네트워크 오류", android.widget.Toast.LENGTH_SHORT).show();
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

    private String nullToEmpty(String s) { return s == null ? "" : s; }
    private boolean isEmpty(String s) { return s == null || s.trim().isEmpty(); }

    /** 바텀시트와 동일한 비주얼 적용 */
    private void applyStar(android.widget.ImageView star, boolean fav) {
        star.setImageResource(R.drawable.ic_star2);
        int color = fav ? Color.parseColor("#FFC107") : Color.parseColor("#BDBDBD");
        ImageViewCompat.setImageTintList(star, ColorStateList.valueOf(color));
        String tip = fav ? "즐겨찾기 제거" : "즐겨찾기 추가";
        star.setContentDescription(tip);
        TooltipCompat.setTooltipText(star, tip);
    }
}
