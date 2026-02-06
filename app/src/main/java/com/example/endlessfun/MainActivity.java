package com.example.endlessfun;

import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

public class MainActivity extends AppCompatActivity {

    private GameView gameView;
    private View menuOverlay;
    private ImageButton pauseButton;
    private TextView bestScoreText;
    private ImageView bestMedalIcon;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);

        gameView = findViewById(R.id.gameView);
        menuOverlay = findViewById(R.id.menuOverlay);
        pauseButton = findViewById(R.id.pauseButton);
        bestScoreText = findViewById(R.id.bestScoreText);
        bestMedalIcon = findViewById(R.id.bestMedalIcon);
        Button startButton = findViewById(R.id.startButton);
        Button birdsButton = findViewById(R.id.birdsButton);

        // Load high score for menu display
        loadBestScoreForMenu();

        startButton.setOnClickListener(v -> {
            menuOverlay.setVisibility(View.GONE);
            pauseButton.setVisibility(View.VISIBLE);
            pauseButton.setImageResource(R.drawable.ic_pause);
            pauseButton.setContentDescription(getString(R.string.pause));
            gameView.startGame();
        });

        birdsButton.setOnClickListener(v -> startActivity(new android.content.Intent(this, BirdsActivity.class)));

        pauseButton.setOnClickListener(v -> {
            if (gameView.isPaused()) {
                gameView.resumeGame();
                pauseButton.setImageResource(R.drawable.ic_pause);
                pauseButton.setContentDescription(getString(R.string.pause));
            } else {
                gameView.pauseGame();
                pauseButton.setImageResource(R.drawable.ic_play);
                pauseButton.setContentDescription(getString(R.string.resume));
            }
        });
    }

    private void loadBestScoreForMenu() {
        new Thread(() -> {
            AppDatabase db = AppDatabase.getInstance(this);
            Integer saved = db.scoreDao().getHighScore();
            int best = saved != null ? saved : 0;
            int medalResId = GameView.getMedalDrawableId(best);
            runOnUiThread(() -> {
                bestScoreText.setText(getString(R.string.best_score_format, best));
                if (medalResId != 0) {
                    bestMedalIcon.setImageResource(medalResId);
                    bestMedalIcon.setVisibility(View.VISIBLE);
                } else {
                    bestMedalIcon.setVisibility(View.GONE);
                }
            });
        }).start();
    }
}
