package com.example.testmap.ui;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.testmap.R;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;

public class ReservationBottomSheet extends BottomSheetDialogFragment {
    private static final String ARG_ROUTE_NAME   = "route_name";   // 선택(노선표시용)
    private static final String ARG_DIRECTION    = "direction";
    private static final String ARG_BOARD_NAME   = "board_stop_name";
    private static final String ARG_DEST_NAME    = "dest_stop_name";

    public static ReservationBottomSheet newInstance(String routeName,
                                                     String direction,
                                                     String boardName,
                                                     String destName) {
        ReservationBottomSheet sheet = new ReservationBottomSheet();
        Bundle b = new Bundle();
        b.putString(ARG_ROUTE_NAME, routeName);
        b.putString(ARG_DIRECTION, direction);
        b.putString(ARG_BOARD_NAME, boardName);
        b.putString(ARG_DEST_NAME, destName);
        sheet.setArguments(b);
        return sheet;
    }

    @Nullable @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.reservation_bottomsheet, container, false);

        String routeName = getArguments() != null ? getArguments().getString(ARG_ROUTE_NAME, "") : "";
        String direction = getArguments() != null ? getArguments().getString(ARG_DIRECTION, "") : "";
        String board     = getArguments() != null ? getArguments().getString(ARG_BOARD_NAME, "") : "";
        String dest      = getArguments() != null ? getArguments().getString(ARG_DEST_NAME, "") : "";

        TextView tvBusNumber      = v.findViewById(R.id.tvBusNumber);
        TextView tvBusDirection   = v.findViewById(R.id.tvBusDirection);
        TextView tvRidingStation  = v.findViewById(R.id.riging_station);
        TextView tvOutStation     = v.findViewById(R.id.out_station);

        tvBusNumber.setText(routeName);
        tvBusDirection.setText(direction);
        tvRidingStation.setText(board);
        tvOutStation.setText(dest);

        v.findViewById(R.id.exit_button).setOnClickListener(view -> dismiss());
        v.findViewById(R.id.btnReserve).setOnClickListener(view -> {
            // 여기선 "알림취소" 같은 동작 예정이면 연결, 지금은 닫기
            dismiss();
        });
        return v;
    }
}