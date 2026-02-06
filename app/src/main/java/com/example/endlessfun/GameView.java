package com.example.endlessfun;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.media.SoundPool;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.Random;

public class GameView extends View implements Runnable {

    // Thread & state
    private Thread gameThread;
    private boolean isPlaying = true;
    private boolean isGameOver = false;

    // Player
    private float playerX, playerY;
    private float velocityY = 0;
    private final float gravity = 1.5f;
    private final float jumpForce = -20;

    // Bitmaps
    private Bitmap playerBitmap;
    private Bitmap obstacleBitmap;

    // Difficulty
    private float obstacleSpeed = 5f;
    private int spawnDelay = 90;
    private int gapHeight = 300;
    private int lastDifficultyScore = 0;

    // Obstacles
    private class ObstaclePair {
        RectF topRect;
        RectF bottomRect;
        boolean passed = false;

        ObstaclePair(float x, float gapTop) {
            float width = getWidth() / 8f;  // 1/8 of screen width
            topRect = new RectF(x, 0, x + width, gapTop);
            bottomRect = new RectF(x, gapTop + gapHeight, x + width, getHeight());
        }

        void update() {
            topRect.offset(-obstacleSpeed, 0);
            bottomRect.offset(-obstacleSpeed, 0);
        }
    }



    private final ArrayList<ObstaclePair> obstacles = new ArrayList<>();
    private final Random random = new Random();
    private int spawnTimer = 0;

    // Score
    private int score = 0;
    private int highScore = 0;

    // DB
    private AppDatabase db;

    // Sound
    private SoundPool soundPool;
    private int jumpSound, hitSound, scoreSound;

    // Paint
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

    public GameView(Context context, AttributeSet attrs) {
        super(context, attrs);

        init(context);

    }

    private void init(Context context) {
        // Sounds
        soundPool = new SoundPool.Builder().setMaxStreams(3).build();
        jumpSound = soundPool.load(context, R.raw.jump, 1);
        hitSound = soundPool.load(context, R.raw.hit, 1);
        scoreSound = soundPool.load(context, R.raw.score, 1);

        // Screen size
        int screenWidth = Resources.getSystem().getDisplayMetrics().widthPixels;
        int screenHeight = Resources.getSystem().getDisplayMetrics().heightPixels;

        // Player bitmap
        playerBitmap = BitmapFactory.decodeResource(getResources(), R.drawable.player);
        playerBitmap = Bitmap.createScaledBitmap(playerBitmap,
                screenWidth / 8,
                screenHeight / 18,
                true);

        // Gap height based on player
        gapHeight = playerBitmap.getHeight() * 3;

        // Obstacle bitmap (if using image, otherwise ignore)
        obstacleBitmap = BitmapFactory.decodeResource(getResources(), R.drawable.obstacle);
        obstacleBitmap = Bitmap.createScaledBitmap(obstacleBitmap,
                screenWidth / 6,
                screenHeight / 2,
                true);
    }

    public void startGame() {
        // Load high score from DB (only once if needed)
        if (db == null) {
            db = AppDatabase.getInstance(getContext());
            new Thread(() -> {
                Integer saved = db.scoreDao().getHighScore();
                highScore = saved != null ? saved : 0;
            }).start();
        }

        // Start the game thread if not already running
        if (gameThread == null || !gameThread.isAlive()) {
            isPlaying = true;
            isGameOver = false;
            obstacles.clear();
            score = 0;
            velocityY = 0;
            obstacleSpeed = 10f;
            spawnDelay = 90;
            lastDifficultyScore = 0;
            gapHeight = playerBitmap.getHeight() * 3;

            gameThread = new Thread(this);
            gameThread.start();
        }
    }


    private void resetGame() {
        obstacles.clear();
        score = 0;
        velocityY = 0;
        obstacleSpeed = 10f;
        spawnDelay = 120;
        lastDifficultyScore = 0;
        isGameOver = false;
        playerY = getHeight() / 2f;

        // recalc gap height based on player size
        gapHeight = playerBitmap.getHeight() * 3;
    }


    @Override
    public void run() {
        while (isPlaying) {
            update();
            postInvalidate();
            try { Thread.sleep(20); } catch (InterruptedException ignored) {}
        }
    }

    private void update() {
        if (isGameOver) return;

        // Difficulty scaling
        if (score >= lastDifficultyScore + 5) {
            obstacleSpeed += 0.3f;
            gapHeight = Math.max(200, gapHeight - 10);
            spawnDelay = Math.max(50, spawnDelay - 2);
            lastDifficultyScore = score;
        }

        // Physics
        velocityY += gravity;
        playerY += velocityY;

        if (playerY > getHeight() - playerBitmap.getHeight() / 2f) {
            onGameOver();
        }

        // Spawn pipes
        spawnTimer++;
        if (spawnTimer > spawnDelay) {
            float minTop = 200;
            float maxTop = getHeight() - gapHeight - 200;
            float gapTop = minTop + random.nextFloat() * (maxTop - minTop);
            obstacles.add(new ObstaclePair(getWidth(), gapTop));
            spawnTimer = 0;
        }

        // Update pipes
        Iterator<ObstaclePair> it = obstacles.iterator();
        while (it.hasNext()) {
            ObstaclePair o = it.next();
            o.update();

            if (o.topRect.right < 0) {
                it.remove();
                continue;
            }

            // Score
            if (!o.passed && o.topRect.right < playerX) {
                o.passed = true;
                score++;
                soundPool.play(scoreSound, 1, 1, 1, 0, 1);
            }

            // Collision
            float padding = 10; // 10px buffer to make collisions forgiving
            RectF playerRect = new RectF(
                    playerX - playerBitmap.getWidth() / 2f + padding,
                    playerY - playerBitmap.getHeight() / 2f + padding,
                    playerX + playerBitmap.getWidth() / 2f - padding,
                    playerY + playerBitmap.getHeight() / 2f - padding
            );


            if (RectF.intersects(playerRect, o.topRect) ||
                    RectF.intersects(playerRect, o.bottomRect)) {
                onGameOver();
            }
        }
    }

    private void onGameOver() {
        if (isGameOver) return;
        isGameOver = true;
        soundPool.play(hitSound, 1, 1, 1, 0, 1);

        if (score > highScore) {
            highScore = score;
            new Thread(() -> {
                HighScore hs = new HighScore();
                hs.score = highScore;
                db.scoreDao().saveHighScore(hs);
            }).start();
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        canvas.drawColor(Color.WHITE);

        if (playerX == 0) {
            playerX = getWidth() / 4f;
            playerY = getHeight() / 2f;
        }


        // Pipes
        paint.setColor(Color.GREEN);  // choose color
        for (ObstaclePair o : obstacles) {
            canvas.drawRect(o.topRect, paint);
            canvas.drawRect(o.bottomRect, paint);
        }


        // Player
        canvas.drawBitmap(
                playerBitmap,
                playerX - playerBitmap.getWidth() / 2f,
                playerY - playerBitmap.getHeight() / 2f,
                null
        );

        // HUD
        paint.setColor(Color.BLACK);
        paint.setTextSize(50);
        canvas.drawText("Score: " + score, 40, 120, paint);
        canvas.drawText("High Score: " + highScore, 40, 180, paint);

        if (isGameOver) {
            paint.setTextAlign(Paint.Align.CENTER);
            paint.setTextSize(90);
            canvas.drawText("GAME OVER", getWidth() / 2f, getHeight() / 2f, paint);
            paint.setTextSize(40);
            canvas.drawText("Tap to Restart", getWidth() / 2f, getHeight() / 2f + 80, paint);
            paint.setTextAlign(Paint.Align.LEFT);
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (event.getAction() == MotionEvent.ACTION_DOWN) {
            if (isGameOver) {
                resetGame();
            } else {
                velocityY = jumpForce;
                soundPool.play(jumpSound, 1, 1, 1, 0, 1);
            }
        }
        return true;
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        soundPool.release();
    }
}

