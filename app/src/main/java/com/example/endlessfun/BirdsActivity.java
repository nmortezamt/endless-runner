package com.example.endlessfun;

import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.drawable.DrawableCompat;

public class BirdsActivity extends AppCompatActivity {

    private GamePrefs prefs;
    private LinearLayout birdsContainer;
    private LinearLayout backgroundsContainer;

    private static final int[] BIRD_NAME_IDS = {
        R.string.bird_default,
        R.string.bird_red,
        R.string.bird_blue,
        R.string.bird_yellow,
        R.string.bird_green,
    };

    private static final int[] BIRD_TINT_COLORS = {
        0,
        0xFFE53935,
        0xFF1E88E5,
        0xFFFDD835,
        0xFF43A047,
    };

    private static final int[] BACKGROUND_COLOR_IDS = {
        R.color.bg_cream,
        R.color.bg_sky,
        R.color.bg_grass,
        R.color.bg_sunset,
        R.color.bg_night,
    };

    private static final int[] BACKGROUND_NAME_IDS = {
        R.string.bg_cream,
        R.string.bg_sky,
        R.string.bg_grass,
        R.string.bg_sunset,
        R.string.bg_night,
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_birds);

        prefs = new GamePrefs(this);
        birdsContainer = findViewById(R.id.birdsContainer);
        backgroundsContainer = findViewById(R.id.backgroundsContainer);

        findViewById(R.id.backButton).setOnClickListener(v -> finish());

        buildBirdRows();
        buildBackgroundRows();

        // Sync unlocks with current high score (in case it was set before opening this page)
        new Thread(() -> {
            Integer high = AppDatabase.getInstance(this).scoreDao().getHighScore();
            int highScore = high != null ? high : 0;
            runOnUiThread(() -> {
                prefs.updateUnlocksForHighScore(highScore);
                buildBirdRows();
                buildBackgroundRows();
            });
        }).start();
    }

    private void buildBirdRows() {
        birdsContainer.removeAllViews();
        int selected = prefs.getSelectedBird();

        for (int i = 0; i < BirdUnlock.BIRD_COUNT; i++) {
            View row = LayoutInflater.from(this).inflate(R.layout.item_bird, birdsContainer, false);
            ImageView icon = row.findViewById(R.id.birdIcon);
            TextView name = row.findViewById(R.id.birdName);
            TextView unlockText = row.findViewById(R.id.birdUnlock);
            ImageView check = row.findViewById(R.id.birdCheck);

            name.setText(getString(BIRD_NAME_IDS[i]));
            boolean unlocked = prefs.isBirdUnlocked(i);
            if (unlocked) {
                unlockText.setText(i == selected ? getString(R.string.selected) : "");
                check.setVisibility(i == selected ? View.VISIBLE : View.GONE);
            } else {
                int score = BirdUnlock.getUnlockScoreForBird(i);
                unlockText.setText(getString(R.string.unlock_at, score));
                check.setVisibility(View.GONE);
            }

            Drawable d = ContextCompat.getDrawable(this, R.drawable.player);
            if (d != null) {
                d = d.mutate();
                if (i > 0 && i < BIRD_TINT_COLORS.length) {
                    DrawableCompat.setTint(d, BIRD_TINT_COLORS[i]);
                }
                icon.setImageDrawable(d);
            }

            final int index = i;
            row.setOnClickListener(v -> {
                if (!prefs.isBirdUnlocked(index)) {
                    Toast.makeText(this, getString(R.string.unlock_at, BirdUnlock.getUnlockScoreForBird(index)), Toast.LENGTH_SHORT).show();
                    return;
                }
                prefs.setSelectedBird(index);
                buildBirdRows();
            });

            birdsContainer.addView(row);
        }
    }

    private void buildBackgroundRows() {
        backgroundsContainer.removeAllViews();
        int selected = prefs.getSelectedBackground();

        for (int i = 0; i < BACKGROUND_COLOR_IDS.length; i++) {
            View row = LayoutInflater.from(this).inflate(R.layout.item_background, backgroundsContainer, false);
            View swatch = row.findViewById(R.id.backgroundSwatch);
            TextView name = row.findViewById(R.id.backgroundName);
            ImageView check = row.findViewById(R.id.backgroundCheck);

            swatch.setBackgroundColor(ContextCompat.getColor(this, BACKGROUND_COLOR_IDS[i]));
            name.setText(getString(BACKGROUND_NAME_IDS[i]));
            check.setVisibility(i == selected ? View.VISIBLE : View.GONE);

            final int index = i;
            row.setOnClickListener(v -> {
                prefs.setSelectedBackground(index);
                buildBackgroundRows();
            });

            backgroundsContainer.addView(row);
        }
    }
}
