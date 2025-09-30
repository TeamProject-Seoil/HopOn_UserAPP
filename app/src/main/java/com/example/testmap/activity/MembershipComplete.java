package com.example.testmap.activity;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;

import androidx.appcompat.app.AppCompatActivity;

import com.example.testmap.R;

public class MembershipComplete extends AppCompatActivity{

    Button loginMove_button;

    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_membership_complete);
        loginMove_button = findViewById(R.id.loginMove);
        loginMove_button.setOnClickListener(v -> {
            Intent intent = new Intent(MembershipComplete.this, LoginActivity.class);
            startActivity(intent);
            finish();
        });
    }
}
