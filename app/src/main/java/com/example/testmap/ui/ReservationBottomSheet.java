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

    private static final String ARG_STOP_NAME = "stop_name";
    private static final String ARG_ROUTE_NAME = "route_name";

    public static ReservationBottomSheet newInstance(String stopName, String routeName) {
        ReservationBottomSheet sheet = new ReservationBottomSheet();
        Bundle args = new Bundle();
        args.putString(ARG_STOP_NAME, stopName);
        args.putString(ARG_ROUTE_NAME, routeName);
        sheet.setArguments(args);
        return sheet;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.reservation_bottomsheet, container, false);

        String stopName = getArguments() != null ? getArguments().getString(ARG_STOP_NAME) : "";
        String routeName = getArguments() != null ? getArguments().getString(ARG_ROUTE_NAME) : "";

        TextView tvBusNumber = view.findViewById(R.id.tvBusNumber);
        TextView tvRidingStation = view.findViewById(R.id.riging_station);
        TextView tvOutStation = view.findViewById(R.id.out_station);

        tvBusNumber.setText(routeName);
        tvRidingStation.setText(stopName);
        tvOutStation.setText(stopName);

        ImageButton exitButton = view.findViewById(R.id.exit_button);
        exitButton.setOnClickListener(v -> dismiss());

        Button btnReserve = view.findViewById(R.id.btnReserve);
        btnReserve.setOnClickListener(v -> dismiss());

        return view;
    }
}
