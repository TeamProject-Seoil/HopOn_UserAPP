package com.example.testmap.activity;

import android.os.Bundle;
import android.view.View;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;

import androidx.appcompat.app.AppCompatActivity;

import com.example.testmap.R;

public class NoticeActivity extends AppCompatActivity {

    private boolean isExpanded = false;
    private LinearLayout noticeContent;
    private ImageView expandArrow;
    private ImageButton noticeBackButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_notice); // XML 파일 이름과 반드시 일치

        // View 연결
        noticeContent = findViewById(R.id.notice_content);
        expandArrow = findViewById(R.id.expand_arrow);
        noticeBackButton = findViewById(R.id.notice_back_button);

        // 공지 항목 클릭 시 펼치기/접기
        findViewById(R.id.notice_header).setOnClickListener(v -> toggleNotice());

        // 뒤로가기 버튼 클릭 시 Activity 종료
        noticeBackButton.setOnClickListener(v -> finish());
    }

    // 공지 펼치기/접기 토글
    private void toggleNotice() {
        isExpanded = !isExpanded;
        if (isExpanded) {
            expandView(noticeContent);
            expandArrow.setRotation(180f);
        } else {
            collapseView(noticeContent);
            expandArrow.setRotation(0f);
        }
    }

    // 페이드 인 애니메이션
    private void expandView(View view) {
        view.setAlpha(0f);
        view.setVisibility(View.VISIBLE);
        view.animate()
                .alpha(1f)
                .setDuration(200)
                .start();
    }

    // 페이드 아웃 애니메이션
    private void collapseView(View view) {
        view.animate()
                .alpha(0f)
                .setDuration(200)
                .withEndAction(() -> view.setVisibility(View.GONE))
                .start();
    }
}
