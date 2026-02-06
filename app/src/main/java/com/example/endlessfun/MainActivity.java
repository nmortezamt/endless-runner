package com.example.endlessfun;

import android.os.Bundle;
import android.view.View;
import android.widget.Button;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);

        GameView gameView = findViewById(R.id.gameView);
        Button startButton = findViewById(R.id.startButton);
        Button pauseButton = findViewById(R.id.pauseButton);

        startButton.setOnClickListener(v -> {
            gameView.startGame();        // start the game
            startButton.setVisibility(View.GONE);
            // hide button
        });


        pauseButton.setOnClickListener(v -> {
            if (gameView.isPaused()) {
                gameView.resumeGame();
                pauseButton.setText("Pause");
            } else {
                gameView.pauseGame();
                pauseButton.setText("Resume");
            }
        });
    }
}