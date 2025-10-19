// app/src/main/java/com/example/testmap/ui/ReserveCardDialogFragment.java
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

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.DialogFragment;

import com.example.testmap.R;
import com.example.testmap.service.ApiClient;
import com.example.testmap.service.ApiService;
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

    private OnActionListener listener;

    public void setOnActionListener(OnActionListener l) { this.listener = l; }

    /** 기존: 표시 데이터만 (서버 연동 없이 UI 토글만 가능) */
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

    /** 서버 연동까지 가능한 전체 파라미터 버전 */
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

    @NonNull @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        View v = LayoutInflater.from(requireContext()).inflate(R.layout.reserve_screen, null, false);

        Bundle args = getArguments() != null ? getArguments() : new Bundle();

        // ===== 표시 데이터 바인딩 =====
        String busNo = args.getString(ARG_BUS_NO, "");
        String dir   = args.getString(ARG_DIR,    "");
        String from  = args.getString(ARG_FROM,   "");
        String to    = args.getString(ARG_TO,     "");

        ((android.widget.TextView) v.findViewById(R.id.tvBusNumber)).setText(busNo);
        ((android.widget.TextView) v.findViewById(R.id.tvBusDirection)).setText(dir);
        ((android.widget.TextView) v.findViewById(R.id.riging_station)).setText(from);
        ((android.widget.TextView) v.findViewById(R.id.out_station)).setText(to);

        v.findViewById(R.id.btnCancel).setOnClickListener(view -> {
            if (listener != null) listener.onCancelClicked();
            dismiss();
        });
        v.findViewById(R.id.btnReserve).setOnClickListener(view -> {
            boolean boarding = ((android.widget.CheckBox) v.findViewById(R.id.checkBoardingAlarm)).isChecked();
            boolean dropOff  = ((android.widget.CheckBox) v.findViewById(R.id.checkDropOffAlarm)).isChecked();
            if (listener != null) listener.onReserveClicked(boarding, dropOff);
        });

        // ===== 즐겨찾기 토글 =====
        android.widget.ImageView star = v.findViewById(R.id.btnFavorite);

        final boolean[] isFav = { args.getBoolean(ARG_IS_FAVORITE, false) };
        final long[] favId    = { args.containsKey(ARG_FAVORITE_ID) ? args.getLong(ARG_FAVORITE_ID) : -1L };

        updateStarIcon(star, isFav[0]);

        star.setOnClickListener(view -> {
            // 서버 연동 가능한지 체크
            boolean hasAllParams =
                    !isEmpty(args.getString(ARG_ROUTE_ID)) &&
                            !isEmpty(args.getString(ARG_BOARD_ID)) &&
                            !isEmpty(args.getString(ARG_DEST_ID));

            String access = TokenStore.getAccess(requireContext());
            String bearer = !TextUtils.isEmpty(access) ? ("Bearer " + access) : null;

            if (bearer == null) {
                android.widget.Toast.makeText(requireContext(), "로그인이 필요합니다.", android.widget.Toast.LENGTH_SHORT).show();
                return;
            }

            if (!hasAllParams) {
                // 필수 값 없으면 UI만 토글
                isFav[0] = !isFav[0];
                updateStarIcon(star, isFav[0]);
                android.widget.Toast
                        .makeText(requireContext(), "즐겨찾기 정보가 부족해 서버 동기화 없이 표시만 바꿔요.", android.widget.Toast.LENGTH_SHORT)
                        .show();
                return;
            }

            if (isFav[0]) {
                // ===== 삭제 흐름 =====
                if (favId[0] <= 0) {
                    // ID가 없으면 현재 내 즐겨찾기 목록에서 동일 항목을 찾아 ID를 먼저 해석
                    resolveFavoriteIdThen(bearer, args, id -> {
                        if (id == null) {
                            android.widget.Toast.makeText(requireContext(), "삭제할 즐겨찾기를 찾지 못했습니다.", android.widget.Toast.LENGTH_SHORT).show();
                            return;
                        }
                        favId[0] = id;
                        deleteFavorite(bearer, favId[0], () -> {
                            isFav[0] = false;
                            favId[0] = -1L;
                            updateStarIcon(star, false);
                            if (listener != null) listener.onFavoriteChanged(false, null);
                        });
                    });
                } else {
                    deleteFavorite(bearer, favId[0], () -> {
                        isFav[0] = false;
                        favId[0] = -1L;
                        updateStarIcon(star, false);
                        if (listener != null) listener.onFavoriteChanged(false, null);
                    });
                }
            } else {
                // ===== 추가 흐름 =====
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
                            updateStarIcon(star, true);
                            if (listener != null) listener.onFavoriteChanged(true, favId[0]);
                        } else if (res.code()==409) {
                            // 이미 존재 → 목록 조회로 ID 파악 후 상태 동기화
                            resolveFavoriteIdThen(bearer, args, id -> {
                                isFav[0] = true;
                                if (id != null) favId[0] = id;
                                updateStarIcon(star, true);
                                if (listener != null) listener.onFavoriteChanged(true, favId[0] > 0 ? favId[0] : null);
                                android.widget.Toast.makeText(requireContext(), "이미 즐겨찾기에 있어요.", android.widget.Toast.LENGTH_SHORT).show();
                            });
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

        // ===== AlertDialog 구성 =====
        AlertDialog dlg = new AlertDialog.Builder(requireContext())
                .setView(v)
                .create();

        dlg.setOnShowListener(d -> {
            Window w = dlg.getWindow();
            if (w != null) {
                w.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
                View decor = w.getDecorView();
                if (decor != null) decor.setPadding(0, 0, 0, 0);
                w.setLayout(WindowManager.LayoutParams.WRAP_CONTENT,
                        WindowManager.LayoutParams.WRAP_CONTENT);
            }
        });

        return dlg;
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
                if (res.isSuccessful()) {
                    onOk.run();
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

    private boolean isEmpty(String s) {
        return s == null || s.trim().isEmpty();
    }

    private void updateStarIcon(android.widget.ImageView star, boolean isFav) {
        star.setImageResource(isFav ? R.drawable.ic_star_filled : R.drawable.ic_star_outline);
        int color = ContextCompat.getColor(
                requireContext(),
                isFav ? R.color.blue_500 : R.color.gray_500
        );
        star.setColorFilter(color);
    }
}
