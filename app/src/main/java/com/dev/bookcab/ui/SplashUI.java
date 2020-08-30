package com.dev.bookcab.ui;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.dev.bookcab.R;

public class SplashUI extends AppCompatActivity {

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.splash);

        /**
         * starting Sign up ui after 5 sec of delay to show splash
         */
        new Handler().postDelayed((Runnable) () -> {
            startActivity(new Intent(this, SingupUI.class));
            finish();
        }, 1000 * 5);
    }
}
