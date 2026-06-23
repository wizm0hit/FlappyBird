package com.wizm0hit.flappybird;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.Switch;
import android.widget.TextView;

public class MainActivity extends Activity {

    private int[] birdPreviews = {
            R.drawable.yellowbird_midflap,
            R.drawable.bluebird_midflap,
            R.drawable.redbird_midflap
    };
    private String[] birdTypes = {"yellow", "blue", "red"};
    private int currentBirdIndex = 0;

    private RelativeLayout mainMenuLayout;
    private ImageView imgBirdPreview;
    private TextView tvHighScore;
    private Switch switchTheme;
    private SharedPreferences prefs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        prefs = getSharedPreferences("FlappyBirdPrefs", MODE_PRIVATE);

        mainMenuLayout = findViewById(R.id.mainMenuLayout);
        imgBirdPreview = findViewById(R.id.imgBirdPreview);
        tvHighScore = findViewById(R.id.tvHighScore);
        switchTheme = findViewById(R.id.switchTheme);
        Button btnPrevBird = findViewById(R.id.btnPrevBird);
        Button btnNextBird = findViewById(R.id.btnNextBird);
        Button btnPlay = findViewById(R.id.btnPlay);

        boolean isDark = prefs.getBoolean("isDarkTheme", false);
        switchTheme.setChecked(isDark);
        updateMenuTheme(isDark);

        switchTheme.setOnCheckedChangeListener((buttonView, isChecked) -> {
            prefs.edit().putBoolean("isDarkTheme", isChecked).apply();
            updateMenuTheme(isChecked);
        });

        btnNextBird.setOnClickListener(v -> {
            currentBirdIndex = (currentBirdIndex + 1) % birdPreviews.length;
            imgBirdPreview.setImageResource(birdPreviews[currentBirdIndex]);
        });

        btnPrevBird.setOnClickListener(v -> {
            currentBirdIndex = (currentBirdIndex - 1 + birdPreviews.length) % birdPreviews.length;
            imgBirdPreview.setImageResource(birdPreviews[currentBirdIndex]);
        });

        btnPlay.setOnClickListener(v -> {
            Intent intent = new Intent(MainActivity.this, GameActivity.class);
            intent.putExtra("BIRD_TYPE", birdTypes[currentBirdIndex]);
            intent.putExtra("IS_DARK", switchTheme.isChecked());
            startActivity(intent);
        });
    }

    private void updateMenuTheme(boolean isDark) {
        if (isDark) {
            mainMenuLayout.setBackgroundResource(R.drawable.background_night);
        } else {
            mainMenuLayout.setBackgroundResource(R.drawable.background_day);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        int highScore = prefs.getInt("highScore", 0);
        tvHighScore.setText("HIGHEST SCORE: " + highScore);
    }
}