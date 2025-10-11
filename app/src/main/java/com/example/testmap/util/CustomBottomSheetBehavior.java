package com.example.testmap.util;

import android.content.Context;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import androidx.annotation.NonNull;
import androidx.coordinatorlayout.widget.CoordinatorLayout;
import com.google.android.material.bottomsheet.BottomSheetBehavior;

public class CustomBottomSheetBehavior<V extends View> extends BottomSheetBehavior<V> {

    // 이 생성자들은 그대로 유지해야 합니다.
    public CustomBottomSheetBehavior() {
        super();
    }

    public CustomBottomSheetBehavior(@NonNull Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    public boolean onInterceptTouchEvent(@NonNull CoordinatorLayout parent, @NonNull V child, @NonNull MotionEvent event) {
        // 숨김/비가시면 절대 가로채지 않음
        if (getState() == STATE_HIDDEN || child.getVisibility() != View.VISIBLE) return false;

        // raw 좌표로 시트 영역 판정
        int[] loc = new int[2];
        child.getLocationOnScreen(loc);
        float rx = event.getRawX(), ry = event.getRawY();
        boolean inside = rx >= loc[0] && rx < loc[0] + child.getWidth()
                && ry >= loc[1] && ry < loc[1] + child.getHeight();

        if (!inside) return false; // 시트 밖 → 지도/배경으로 터치 통과

        // 시트 안 → 기본 동작
        return super.onInterceptTouchEvent(parent, child, event);
    }


    // 터치된 좌표(x, y)가 뷰(view)의 범위 안에 있는지 확인하는 헬퍼 메서드
    private boolean isPointInChildBounds(View view, int x, int y) {
        // 뷰의 화면상 좌표를 가져옵니다.
        int[] location = new int[2];
        view.getLocationOnScreen(location);

        // 뷰의 사각형 영역을 구합니다.
        int viewX = location[0];
        int viewY = location[1];
        int viewRight = viewX + view.getWidth();
        int viewBottom = viewY + view.getHeight();

        // 현재 터치된 좌표가 뷰의 영역 내에 있는지 확인합니다.
        // 참고: MotionEvent의 좌표는 부모 뷰 기준이므로, 실제 화면 좌표로 비교하는 것이 더 정확할 수 있습니다.
        // 하지만 이 경우 CoordinatorLayout 전체를 기준으로 하므로 getX, getY로도 대부분 잘 동작합니다.
        // 더 정확하게 하려면 event.getRawX(), event.getRawY()와 비교해야 합니다.
        return (x >= view.getLeft() && x < view.getRight() && y >= view.getTop() && y < view.getBottom());
    }
}