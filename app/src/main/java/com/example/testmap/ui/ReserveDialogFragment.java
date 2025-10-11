package com.example.testmap.ui;

import android.app.Dialog;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.DialogFragment;

import com.example.testmap.R;

public class ReserveDialogFragment extends DialogFragment {

    private static final String ARG_DEPARTURE_NAME = "departure_name";
    private static final String ARG_ARRIVAL_NAME   = "arrival_name";
    private static final String ARG_ROUTE_NAME     = "route_name";

    public interface OnReserveListener {
        void onReserveComplete(String departureName, String arrivalName, String routeName,
                               boolean boardingAlarm, boolean dropOffAlarm);
    }

    private OnReserveListener listener;

    public void setOnReserveListener(OnReserveListener listener) {
        this.listener = listener;
    }

    public static ReserveDialogFragment newInstance(String departureName, String arrivalName, String routeName) {
        ReserveDialogFragment f = new ReserveDialogFragment();
        Bundle args = new Bundle();
        args.putString(ARG_DEPARTURE_NAME, departureName);
        args.putString(ARG_ARRIVAL_NAME, arrivalName);
        args.putString(ARG_ROUTE_NAME, routeName);
        f.setArguments(args);
        return f;
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        LayoutInflater inflater = requireActivity().getLayoutInflater();
        View view = inflater.inflate(R.layout.reserve_screen, null);

        String departureName = getArguments() != null ? getArguments().getString(ARG_DEPARTURE_NAME) : "출발역";
        String arrivalName   = getArguments() != null ? getArguments().getString(ARG_ARRIVAL_NAME)   : "도착역";
        String routeName     = getArguments() != null ? getArguments().getString(ARG_ROUTE_NAME)     : "버스";

        TextView tvBusNumber = view.findViewById(R.id.tvBusNumber);
        TextView tvRidingStation = view.findViewById(R.id.riging_station);
        TextView tvOutStation = view.findViewById(R.id.out_station);
        CheckBox cbBoarding = view.findViewById(R.id.checkBoardingAlarm);
        CheckBox cbDropOff = view.findViewById(R.id.checkDropOffAlarm);
        Button btnCancel = view.findViewById(R.id.btnCancel);
        Button btnReserve = view.findViewById(R.id.btnReserve);

        tvBusNumber.setText(routeName);
        tvRidingStation.setText(departureName);
        tvOutStation.setText(arrivalName);

        cbBoarding.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked) cbDropOff.setChecked(false);
        });
        cbDropOff.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked) cbBoarding.setChecked(false);
        });

        btnCancel.setOnClickListener(v -> dismiss());

        btnReserve.setOnClickListener(v -> {
            boolean boardingAlarm = cbBoarding.isChecked();
            boolean dropOffAlarm = cbDropOff.isChecked();
            if (listener != null) {
                listener.onReserveComplete(departureName, arrivalName, routeName, boardingAlarm, dropOffAlarm);
            }
            dismiss();
        });

        return new AlertDialog.Builder(requireContext())
                .setView(view)
                .create();
    }
}

