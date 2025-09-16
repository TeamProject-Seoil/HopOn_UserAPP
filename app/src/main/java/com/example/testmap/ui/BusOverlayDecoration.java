// com/example/testmap/ui/BusOverlayDecoration.java
package com.example.testmap.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.view.View;
import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.example.testmap.R;
import com.example.testmap.adapter.BusRouteAdapter;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

// BusOverlayDecoration.java
// BusOverlayDecoration.java (핵심 onDrawOver 교체/추가)

public class BusOverlayDecoration extends RecyclerView.ItemDecoration {

    private final Drawable busDot;
    private final Paint boxPaint, textPaint, congPaint, smallPaint, shadowPaint;
    private final float radius, padH, padV, gapX, lineSpacing, iconSize;
    private final BusRouteAdapter adapter;
    private final RecyclerView rv;
    private final int congFree, congMed, congBusy;

    public BusOverlayDecoration(Context ctx, RecyclerView rv, BusRouteAdapter adapter) {
        this.adapter = adapter; this.rv = rv;

        iconSize = dp(ctx,16);
        radius = dp(ctx,10); padH = dp(ctx,8); padV = dp(ctx,5);
        gapX = dp(ctx,10); lineSpacing = dp(ctx,2);

        busDot = ContextCompat.getDrawable(ctx, R.drawable.ic_buslocation);

        boxPaint = new Paint(Paint.ANTI_ALIAS_FLAG); boxPaint.setColor(Color.WHITE);
        shadowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        shadowPaint.setShadowLayer(dp(ctx,3), 0, dp(ctx,1.5f), 0x22000000);

        textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        textPaint.setColor(0xFF333333); textPaint.setTextSize(dp(ctx,12));

        congPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        congPaint.setTextSize(dp(ctx,12)); // 색은 혼잡도별로 줄 것

        smallPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        smallPaint.setColor(0xFF666666); smallPaint.setTextSize(dp(ctx,11));
        smallPaint.setTextAlign(Paint.Align.CENTER);

        congFree = ContextCompat.getColor(ctx, R.color.cong_free_green);
        congMed  = ContextCompat.getColor(ctx, R.color.cong_medium_yellow);
        congBusy = ContextCompat.getColor(ctx, R.color.cong_busy_red);

        rv.setLayerType(View.LAYER_TYPE_SOFTWARE, null); // 그림자용
    }

    @Override public void onDrawOver(@NonNull Canvas c, @NonNull RecyclerView parent, @NonNull RecyclerView.State state) {
        List<BusRouteAdapter.BusMark> marks = adapter.getBusMarks();
        if (marks.isEmpty()) return;

        LinearLayoutManager lm = (LinearLayoutManager) parent.getLayoutManager();
        if (lm == null) return;

        // 같은 구간 겹침 약간씩 아래로
        Map<Integer,Integer> stack = new HashMap<>();

        for (BusRouteAdapter.BusMark m : marks) {
            Integer pos = adapter.findPosBySeq(m.nextSeq);
            if (pos == null) continue;
            View child = lm.findViewByPosition(pos);
            if (child == null) continue;

            // y 위치(아이템 내부 t)
            float top = child.getTop(), h = child.getHeight();
            float y = top + h * m.t;

            int idx = stack.getOrDefault(m.nextSeq, 0);
            stack.put(m.nextSeq, idx+1);
            y += dp(parent.getContext(), 14) * idx;

            // 타임라인 중심 x 구하기
            View timeline = child.findViewById(R.id.timeline);
            float lineCx = (timeline != null)
                    ? child.getLeft() + timeline.getLeft() + (timeline.getWidth()/2f)
                    : child.getLeft() + dp(parent.getContext(), 40) + dp(parent.getContext(), 14);

            // ── 1) 아이콘
            int l = (int)(lineCx - iconSize/2f);
            int t = (int)(y - iconSize/2f);
            busDot.setBounds(l, t, (int)(l+iconSize), (int)(t+iconSize));
            busDot.draw(c);

            // ── 2) 왼쪽 말풍선 박스 텍스트 구성
            String num = m.number == null ? "" : m.number;
            String cong = m.congestion == null ? "" : m.congestion;
            congPaint.setColor(colorForCong(cong));

            // 첫 줄 폭(번호 + 공백 + 혼잡도(색))
            float firstW = textPaint.measureText(num);
            if (!num.isEmpty() && !cong.isEmpty()) firstW += textPaint.measureText(" ");
            firstW += congPaint.measureText(cong);

            // 둘째 줄(저상) 여부
            boolean hasLow = m.lowFloor;
            float secondW = hasLow ? smallPaint.measureText(" 저상") : 0f;

            // 전체 상자 폭/높이
            Paint.FontMetrics fm1 = textPaint.getFontMetrics();
            float h1 = -fm1.ascent + fm1.descent;
            Paint.FontMetrics fm2 = smallPaint.getFontMetrics();
            float h2 = hasLow ? (-fm2.ascent + fm2.descent) : 0f;
            float contentW = Math.max(firstW, secondW);
            float contentH = hasLow ? (h1 + lineSpacing + h2) : h1;

            // 박스를 아이콘 '왼쪽'에
            float boxRight = l - gapX;
            float boxLeft  = boxRight - (contentW + padH*2);
            float boxTop   = y - contentH/2f - padV;
            float boxBot   = y + contentH/2f + padV;

            // 박스 + 그림자
            RectF r = new RectF(boxLeft, boxTop, boxRight, boxBot);
            c.drawRoundRect(r, radius, radius, shadowPaint);
            c.drawRoundRect(r, radius, radius, boxPaint);

            // 첫 줄 베이스라인
            float base1 = boxTop + padV + (-fm1.ascent);
            float x = boxLeft + padH;

            // 번호
            if (!num.isEmpty()) {
                c.drawText(num, x, base1, textPaint);
                x += textPaint.measureText(num);
                if (!cong.isEmpty()) { c.drawText(" ", x, base1, textPaint); x += textPaint.measureText(" "); }
            }
            // 혼잡도(색상)
            if (!cong.isEmpty()) c.drawText(cong, x, base1, congPaint);

            // 둘째 줄: 저상 (가운데 정렬)
            if (hasLow) {
                String lowText = "저상";
                // 두 번째 줄의 베이스라인을 정확히 계산
                float base2 = boxTop + padV + h1 + lineSpacing - fm2.ascent;
                // 박스의 가로 중앙에 텍스트 그리기
                c.drawText(lowText, r.centerX(), base2, smallPaint);
            }

        }
    }

    private int colorForCong(String k) {
        if (k == null) return 0xFF999999;
        switch (k) {
            case "여유": return congFree;
            case "보통": return congMed;
            case "혼잡":
            case "매우혼잡": return congBusy;
            default: return 0xFF999999;
        }
    }

    private static float dp(Context c, float v){
        return v * c.getResources().getDisplayMetrics().density;
    }
}

