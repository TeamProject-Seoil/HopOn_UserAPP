package com.example.testmap.adapter;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.PorterDuff;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.ColorRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable; // ← 추가
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.example.testmap.R;
import com.example.testmap.dto.ArrivalDto;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** 정류장 도착 리스트 어댑터 (Fragment와 분리) */
public class ArrivalAdapter extends RecyclerView.Adapter<ArrivalAdapter.ArrivalVH> {

    /** 아이템 클릭 콜백 */
    public interface OnItemClick {
        void onClick(ArrivalDto item);
    }

    private final List<ArrivalDto> data;
    @Nullable private final OnItemClick onItemClick;

    // 기존과 호환되는 생성자 (콜백 없음)
    public ArrivalAdapter(List<ArrivalDto> d) {
        this(d, null);
    }

    // 콜백 포함 생성자
    public ArrivalAdapter(List<ArrivalDto> d, @Nullable OnItemClick onItemClick) {
        this.data = d;
        this.onItemClick = onItemClick;
    }

    static class ArrivalVH extends RecyclerView.ViewHolder {
        ImageView ivBus;
        TextView tvRoute, tvDir, tvArr1, tvArr2, tvCong1, tvCong2;
        ArrivalVH(@NonNull View itemView) {
            super(itemView);
            ivBus  = itemView.findViewById(R.id.ivBus);
            tvRoute= itemView.findViewById(R.id.tvRoute);
            tvDir  = itemView.findViewById(R.id.tvDir);
            tvArr1 = itemView.findViewById(R.id.tvArr1);
            tvArr2 = itemView.findViewById(R.id.tvArr2);
            tvCong1= itemView.findViewById(R.id.tvCong1);
            tvCong2= itemView.findViewById(R.id.tvCong2);
        }
    }

    @NonNull @Override
    public ArrivalVH onCreateViewHolder(@NonNull ViewGroup p, int vt) {
        View v = LayoutInflater.from(p.getContext()).inflate(R.layout.item_arrival, p, false);
        return new ArrivalVH(v);
    }


    @Override
    public void onBindViewHolder(@NonNull ArrivalVH h, int pos) {
        ArrivalDto a = data.get(pos);

        h.tvRoute.setText(a.rtNm);
        h.tvDir.setText(a.adirection + " 방면");

        // ✅ SharedPreferences에서 선택된 색상 불러오기
        SharedPreferences prefs = h.itemView.getContext().getSharedPreferences("settings", Context.MODE_PRIVATE);
        int colorValue = prefs.getInt("arrival_text_color", android.graphics.Color.BLACK);

        // ✅ "xx분 후"에만 색상 적용
        h.tvArr1.setText(formatArrMsgColored(h.tvArr1, a.arrmsg1, colorValue));
        h.tvArr2.setText(formatArrMsgColored(h.tvArr2, a.arrmsg2, colorValue));

        // 버스 아이콘 색상 (기존)
        int colorBus = ContextCompat.getColor(h.itemView.getContext(),
                com.example.testmap.util.BusColors.forRouteType(a.routeType));
        h.ivBus.setImageResource(R.drawable.vector);
        h.ivBus.setColorFilter(colorBus, PorterDuff.Mode.SRC_IN);

        h.tvCong1.setVisibility(View.GONE);
        h.tvCong2.setVisibility(View.GONE);

        bindStatus(h.tvCong1, a.congestion1, a.rerdieDiv1, a.arrmsg1);
        bindStatus(h.tvCong2, a.congestion2, a.rerdieDiv2, a.arrmsg2);

        if (onItemClick != null) {
            h.itemView.setOnClickListener(v -> onItemClick.onClick(a));
        } else {
            h.itemView.setOnClickListener(null);
        }
    }



    @Override public int getItemCount(){ return data.size(); }

    private CharSequence formatArrMsgColored(View h, String s, int colorValue) {
        if (s == null) return "";

        // 기본 문자열 정리
        String t = s.replace('\n',' ').replace('\r',' ');
        t = t.replaceAll("\\s+", " ").trim();

        SpannableString span = new SpannableString(t);

        // [로 시작하는 괄호 이후는 제외하고 "xx분"만 색 적용
        int bracketIndex = t.indexOf('[');
        int colorEnd = (bracketIndex == -1) ? t.length() : bracketIndex;

        Pattern p = Pattern.compile("(\\d+\\s*분\\s*후)");

        Matcher m = p.matcher(t);

        while (m.find()) {
            if (m.start() < colorEnd) { // 괄호 안쪽은 제외
                span.setSpan(
                        new ForegroundColorSpan(colorValue),
                        m.start(), m.end(),
                        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                );
            }
        }

        return span;
    }



    /** 표시 규칙
     * mode(=congestion): 1 잔여좌석 → "잔여 N"
     *                    2 재차인원 → "인원 N"
     *                    3 만차     → "만차"
     *                    4 혼잡도   → val 3/4/5/6 → "여유/보통/혼잡/매우혼잡"
     */
    private void bindStatus(TextView tv, String mode, String val, String arrmsg) {
        if (tv == null) return;
        if (arrmsg != null && arrmsg.contains("운행종료")) { tv.setVisibility(View.GONE); return; }

        String m = s(mode);
        String v = s(val);
        if (m == null) { tv.setVisibility(View.GONE); return; }

        switch (v) {
            case "1":
                if (isNumber(m)) show(tv, "잔여 " + m, R.color.cong_free_green);
                else tv.setVisibility(View.GONE);
                return;
            case "2":
                if (isNumber(m)) show(tv, "인원 " + m, R.color.cong_medium_yellow);
                else tv.setVisibility(View.GONE);
                return;
            case "3":
                show(tv, "만차", R.color.cong_busy_red);
                return;
            case "4":
                String level = normalizeCongLevel(m);
                if (level == null) { tv.setVisibility(View.GONE); return; }
                switch (level) {
                    case "3": case "LOW": case "여유":
                        show(tv, "여유", R.color.cong_free_green); break;
                    case "4": case "MEDIUM": case "보통":
                        show(tv, "보통", R.color.cong_medium_yellow); break;
                    case "5": case "HIGH": case "혼잡":
                        show(tv, "혼잡", R.color.cong_busy_red); break;
                    case "6": case "VERY_HIGH": case "매우혼잡":
                        show(tv, "매우혼잡", R.color.cong_busy_red); break;
                    default: tv.setVisibility(View.GONE);
                }
                return;
            default:
                tv.setVisibility(View.GONE);
        }
    }
    // ✅ SharedPreferences에 저장된 정렬 기준에 따라 data 정렬
   /* 버스 도착정보 노출 기준
    public void applySort(Context context) {
        SharedPreferences prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE);
        String criteria = prefs.getString("arrival_display_criteria", "노선번호순");

        switch (criteria) {
            case "도착시간순":
                java.util.Collections.sort(data, com.example.testmap.dto.ArrivalComparators.BY_ARRIVAL_TIME);
                break;
            case "버스유형순":
                java.util.Collections.sort(data, com.example.testmap.dto.ArrivalComparators.BY_BUS_TYPE);
                break;
            default:
                java.util.Collections.sort(data, com.example.testmap.dto.ArrivalComparators.BY_ROUTE_NUMBER);
                break;
        }

        notifyDataSetChanged();
    }*/


    private void show(TextView tv, String text, @ColorRes int colorRes) {
        tv.setVisibility(View.VISIBLE);
        tv.setText(text);
        tv.setTextColor(ContextCompat.getColor(tv.getContext(), colorRes));
        tv.setBackground(null);
    }

    private String s(String v) { return v == null ? null : v.trim(); }
    private boolean isNumber(String v) { try { Integer.parseInt(v.trim()); return true; } catch (Exception e) { return false; } }

    /** 혼잡도 정규화: "3/4/5/6" 또는 "LOW/MEDIUM/HIGH/VERY_HIGH" 또는 "여유/보통/혼잡/매우혼잡" */
    private String normalizeCongLevel(String raw) {
        if (raw == null) return null;
        String v = raw.trim();
        for (int i = 0; i < v.length(); i++) {
            char c = v.charAt(i);
            if (c >= '3' && c <= '6') return String.valueOf(c);
        }
        String up = v.toUpperCase();
        if (up.contains("VERY_HIGH") || v.contains("매우혼잡")) return "6";
        if (up.contains("HIGH")      || v.contains("혼잡"))    return "5";
        if (up.contains("MEDIUM")    || v.contains("보통"))    return "4";
        if (up.contains("LOW")       || v.contains("여유"))    return "3";
        return null;
    }
}
