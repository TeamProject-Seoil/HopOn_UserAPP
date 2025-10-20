package com.example.testmap.ui;

import android.app.Dialog;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.TooltipCompat;
import androidx.core.widget.ImageViewCompat;
import androidx.fragment.app.DialogFragment;

import android.content.res.ColorStateList;

import com.example.testmap.R;
import com.example.testmap.service.ApiClient;
import com.example.testmap.service.ApiService;
import com.example.testmap.util.TokenStore;

import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class ReserveDialogFragment extends DialogFragment {

    private static final String ARG_DEPARTURE_NAME = "departure_name";
    private static final String ARG_ARRIVAL_NAME   = "arrival_name";
    private static final String ARG_ROUTE_NAME     = "route_name";

    // 즐겨찾기/서버 연동용 (Optional)
    private static final String ARG_ROUTE_ID    = "route_id";
    private static final String ARG_DIRECTION   = "direction";
    private static final String ARG_BOARD_ID    = "board_id";
    private static final String ARG_BOARD_NAME  = "board_name";
    private static final String ARG_BOARD_ARS   = "board_ars";
    private static final String ARG_DEST_ID     = "dest_id";
    private static final String ARG_DEST_NAME   = "dest_name";
    private static final String ARG_DEST_ARS    = "dest_ars";
    private static final String ARG_IS_FAVORITE = "is_favorite";
    private static final String ARG_FAVORITE_ID = "favorite_id";

    public interface OnReserveListener {
        void onReserveComplete(String departureName, String arrivalName, String routeName,
                               boolean boardingAlarm, boolean dropOffAlarm);
        default void onFavoriteChanged(boolean isFav, Long favId) {}
    }

    private OnReserveListener listener;

    public void setOnReserveListener(OnReserveListener listener) {
        this.listener = listener;
    }

    /** 표시만 (서버 연동 없이 UI 토글 가능) */
    public static ReserveDialogFragment newInstance(String departureName, String arrivalName, String routeName) {
        ReserveDialogFragment f = new ReserveDialogFragment();
        Bundle args = new Bundle();
        args.putString(ARG_DEPARTURE_NAME, departureName);
        args.putString(ARG_ARRIVAL_NAME,   arrivalName);
        args.putString(ARG_ROUTE_NAME,     routeName);
        f.setArguments(args);
        return f;
    }

    /** 서버 연동까지 가능한 버전 */
    public static ReserveDialogFragment newInstanceFull(
            String departureName, String arrivalName, String routeName,
            String routeId, String direction,
            String boardId, String boardName, String boardArs,
            String destId,  String destName,  String destArs,
            boolean isFavorite, Long favoriteId
    ) {
        ReserveDialogFragment f = new ReserveDialogFragment();
        Bundle args = new Bundle();
        args.putString(ARG_DEPARTURE_NAME, departureName);
        args.putString(ARG_ARRIVAL_NAME,   arrivalName);
        args.putString(ARG_ROUTE_NAME,     routeName);

        args.putString(ARG_ROUTE_ID,   routeId);
        args.putString(ARG_DIRECTION,  direction);
        args.putString(ARG_BOARD_ID,   boardId);
        args.putString(ARG_BOARD_NAME, boardName);
        args.putString(ARG_BOARD_ARS,  boardArs);
        args.putString(ARG_DEST_ID,    destId);
        args.putString(ARG_DEST_NAME,  destName);
        args.putString(ARG_DEST_ARS,   destArs);

        args.putBoolean(ARG_IS_FAVORITE, isFavorite);
        if (favoriteId != null) args.putLong(ARG_FAVORITE_ID, favoriteId);

        f.setArguments(args);
        return f;
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        View view = LayoutInflater.from(requireContext()).inflate(R.layout.reserve_screen, null, false);

        Bundle args = getArguments() != null ? getArguments() : new Bundle();

        String departureName = args.getString(ARG_DEPARTURE_NAME, "출발역");
        String arrivalName   = args.getString(ARG_ARRIVAL_NAME,   "도착역");
        String routeName     = args.getString(ARG_ROUTE_NAME,     "버스");

        TextView tvBusNumber     = view.findViewById(R.id.tvBusNumber);
        TextView tvRidingStation = view.findViewById(R.id.riging_station);
        TextView tvOutStation    = view.findViewById(R.id.out_station);
        CheckBox cbBoarding      = view.findViewById(R.id.checkBoardingAlarm);
        CheckBox cbDropOff       = view.findViewById(R.id.checkDropOffAlarm);
        Button btnCancel         = view.findViewById(R.id.btnCancel);
        Button btnReserve        = view.findViewById(R.id.btnReserve);
        android.widget.ImageView star = view.findViewById(R.id.btnFavoriteActive);

        tvBusNumber.setText(routeName);
        tvRidingStation.setText(departureName);
        tvOutStation.setText(arrivalName);

        // 한 번에 하나만 체크
        cbBoarding.setOnCheckedChangeListener((b, checked) -> { if (checked) cbDropOff.setChecked(false); });
        cbDropOff.setOnCheckedChangeListener((b, checked) -> { if (checked) cbBoarding.setChecked(false); });

        btnCancel.setOnClickListener(v -> dismiss());
        btnReserve.setOnClickListener(v -> {
            if (listener != null) {
                listener.onReserveComplete(
                        departureName, arrivalName, routeName,
                        cbBoarding.isChecked(), cbDropOff.isChecked()
                );
            }
            dismiss();
        });

        // ===== 즐겨찾기 토글 =====
        final boolean[] isFav = { args.getBoolean(ARG_IS_FAVORITE, false) };
        final long[] favId    = { args.containsKey(ARG_FAVORITE_ID) ? args.getLong(ARG_FAVORITE_ID) : -1L };
        final boolean[] busy  = { false }; // 중복 요청 방지

        applyStar(star, isFav[0]);

        star.setOnClickListener(v -> {
            if (busy[0]) return; // 요청 중이면 무시

            boolean hasAllParams =
                    notEmpty(args.getString(ARG_ROUTE_ID)) &&
                            notEmpty(args.getString(ARG_BOARD_ID)) &&
                            notEmpty(args.getString(ARG_DEST_ID));

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

            // UI 가드
            busy[0] = true;
            star.setEnabled(false);

            if (isFav[0]) {
                // ===== 삭제 =====
                Runnable finalizeUi = () -> {
                    isFav[0] = false;
                    favId[0] = -1L;
                    applyStar(star, false);
                    if (listener != null) listener.onFavoriteChanged(false, null);
                    busy[0] = false;
                    star.setEnabled(true);
                };

                if (favId[0] <= 0) {
                    resolveFavoriteIdThen(bearer, args, id -> {
                        if (id == null) {
                            android.widget.Toast.makeText(requireContext(), "삭제할 즐겨찾기를 찾지 못했습니다.", android.widget.Toast.LENGTH_SHORT).show();
                            busy[0] = false;
                            star.setEnabled(true);
                            return;
                        }
                        favId[0] = id;
                        deleteFavoriteWith401(bearer, favId[0], finalizeUi, () -> {
                            busy[0] = false;
                            star.setEnabled(true);
                        });
                    });
                } else {
                    deleteFavoriteWith401(bearer, favId[0], finalizeUi, () -> {
                        busy[0] = false;
                        star.setEnabled(true);
                    });
                }
            } else {
                // ===== 추가 =====
                ApiService.FavoriteCreateRequest body = new ApiService.FavoriteCreateRequest(
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
                ApiClient.get().addFavorite(bearer, body).enqueue(new Callback<ApiService.FavoriteResponse>() {
                    @Override public void onResponse(Call<ApiService.FavoriteResponse> call, Response<ApiService.FavoriteResponse> res) {
                        if (res.isSuccessful() && res.body()!=null) {
                            isFav[0] = true;
                            favId[0] = res.body().id;
                            applyStar(star, true);
                            if (listener != null) listener.onFavoriteChanged(true, favId[0]);
                        } else if (res.code()==409) {
                            // 이미 존재 → id 조회해서 동기화
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
                        busy[0] = false;
                        star.setEnabled(true);
                    }
                    @Override public void onFailure(Call<ApiService.FavoriteResponse> call, Throwable t) {
                        android.widget.Toast.makeText(requireContext(), "네트워크 오류", android.widget.Toast.LENGTH_SHORT).show();
                        busy[0] = false;
                        star.setEnabled(true);
                    }
                });
            }
        });

        AlertDialog dialog = new AlertDialog.Builder(requireContext())
                .setView(view)
                .create();

        dialog.setOnShowListener(d -> {
            Window w = dialog.getWindow();
            if (w != null) {
                w.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
                View decor = w.getDecorView();
                if (decor != null) decor.setPadding(0, 0, 0, 0);
                w.setLayout(WindowManager.LayoutParams.WRAP_CONTENT,
                        WindowManager.LayoutParams.WRAP_CONTENT);
            }
        });

        return dialog;
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

    private void deleteFavoriteWith401(String bearer, long id, Runnable onOk, Runnable onFinally) {
        ApiClient.get().deleteFavorite(bearer, id).enqueue(new Callback<Void>() {
            @Override public void onResponse(Call<Void> call, Response<Void> res) {
                try {
                    if (res.isSuccessful() || res.code()==404) {
                        onOk.run();
                    } else if (res.code()==401) {
                        android.widget.Toast.makeText(requireContext(), "로그인이 만료되었습니다.", android.widget.Toast.LENGTH_SHORT).show();
                        LoginRequiredDialogFragment.show(getParentFragmentManager());
                    } else {
                        android.widget.Toast.makeText(requireContext(), "삭제 실패("+res.code()+")", android.widget.Toast.LENGTH_SHORT).show();
                    }
                } finally {
                    if (onFinally != null) onFinally.run();
                }
            }
            @Override public void onFailure(Call<Void> call, Throwable t) {
                android.widget.Toast.makeText(requireContext(), "네트워크 오류", android.widget.Toast.LENGTH_SHORT).show();
                if (onFinally != null) onFinally.run();
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

    private boolean notEmpty(String s) { return s != null && !s.trim().isEmpty(); }

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
