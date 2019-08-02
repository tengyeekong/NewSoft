package com.solution.it.newsoft;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;

import javax.inject.Inject;

import androidx.databinding.DataBindingUtil;
import dagger.android.support.DaggerAppCompatActivity;

public class MainActivity extends DaggerAppCompatActivity {

    @Inject
    SharedPreferences prefs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        DataBindingUtil.setContentView(this, R.layout.activity_main);

        new Handler().postDelayed(() -> {
            if (!prefs.getString(ViewModel.USERNAME, "").isEmpty()) {
                Intent intent = new Intent(MainActivity.this, ListingActivity.class);
                startActivity(intent);
            } else {
                Intent intent = new Intent(MainActivity.this, LoginActivity.class);
                startActivity(intent);
            }
        }, 2000);
    }
}
