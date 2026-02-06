package com.example.endlessfun;

import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.Rect;
import android.graphics.RectF;
import android.media.SoundPool;
import android.util.AttributeSet;
import android.graphics.drawable.Drawable;
import android.view.MotionEvent;
import android.view.View;

import androidx.core.content.ContextCompat;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.Random;

public class GameView extends View implements Runnable {

    // Thread & state
    private Thread gameThread;
    private boolean isPlaying = true;
    private boolean isGameOver = false;

    private boolean isPaused = false;


    // Player
    private float playerX, playerY;
    private float velocityY = 0;
    private final float gravity = 2.0f;
    private final float jumpForce = -30;

    // Bitmaps
    private Bitmap playerBitmap;
    private Bitmap obstacleBitmap;

    // Difficulty (ramps every DIFFICULTY_INTERVAL points: faster pipes, smaller gap, more frequent spawns)
    private static final int DIFFICULTY_INTERVAL = 5;
    private float obstacleSpeed = 5f;
    private int spawnDelay = 90;
    private int gapHeight = 300;
    private int lastDifficultyScore = 0;

    // Medal thresholds (score >= value)
    private static final int MEDAL_BRONZE = 10;
    private static final int MEDAL_SILVER = 25;
    private static final int MEDAL_GOLD = 50;
    private static final int MEDAL_PLATINUM = 100;

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
    private int initialHighScore = 0; // high score at start of this run (for "New record!")

    // DB & prefs
    private AppDatabase db;
    private GamePrefs gamePrefs;

    // Bird tint colors (index 0 = no filter)
    private static final int[] BIRD_TINT_COLORS = {
        0,           // 0 = no tint (use 0 to skip filter)
        Color.parseColor("#E53935"), // 1 red
        Color.parseColor("#1E88E5"), // 2 blue
        Color.parseColor("#FDD835"), // 3 yellow
        Color.parseColor("#43A047"), // 4 green
    };

    private static final int[] BACKGROUND_COLOR_IDS = {
        R.color.bg_cream,
        R.color.bg_sky,
        R.color.bg_grass,
        R.color.bg_sunset,
        R.color.bg_night,
    };

    // Sound
    private SoundPool soundPool;
    private int jumpSound, hitSound, scoreSound, winSound;

    // Paint
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

    // Game over "Change background" button bounds (set in onDraw when game over)
    private final RectF changeBackgroundBounds = new RectF();

    public GameView(Context context, AttributeSet attrs) {
        super(context, attrs);

        init(context);

    }

    private void init(Context context) {
        // Sounds
        soundPool = new SoundPool.Builder().setMaxStreams(4).build();
        jumpSound = soundPool.load(context, R.raw.jump, 1);
        hitSound = soundPool.load(context, R.raw.hit, 1);
        scoreSound = soundPool.load(context, R.raw.score, 1);
        winSound = soundPool.load(context, R.raw.win, 1);

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

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (gamePrefs == null) gamePrefs = new GamePrefs(getContext());
        // Load high score early so it's ready when user taps Play
        if (db == null) {
            db = AppDatabase.getInstance(getContext());
            new Thread(() -> {
                Integer saved = db.scoreDao().getHighScore();
                highScore = saved != null ? saved : 0;
            }).start();
        }
    }

    public void startGame() {
        // Start the game thread if not already running
        if (gameThread == null || !gameThread.isAlive()) {
            isPlaying = true;
            isGameOver = false;
            obstacles.clear();
            score = 0;
            initialHighScore = highScore;
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
        if (isGameOver || isPaused) return;

        // Difficulty scaling: every DIFFICULTY_INTERVAL points, game gets harder
        if (score >= lastDifficultyScore + DIFFICULTY_INTERVAL) {
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
                // Update high score in real time when reaching or beating record
                if (score >= highScore) {
                    // Play win sound only once per run: when we first beat the record we started with (e.g. 10 → 11)
                    if (initialHighScore > 0 && score == initialHighScore + 1) {
                        soundPool.play(winSound, 1, 1, 1, 0, 1);
                    }
                    highScore = score;
                    if (db != null) {
                        new Thread(() -> {
                            HighScore hs = new HighScore();
                            hs.score = highScore;
                            db.scoreDao().saveHighScore(hs);
                            if (gamePrefs != null) gamePrefs.updateUnlocksForHighScore(highScore);
                        }).start();
                    }
                }
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
        // High score is already updated in real time when passing obstacles
    }

    @Override
    protected void onDraw(Canvas canvas) {
        int bgIndex = gamePrefs != null ? gamePrefs.getSelectedBackground() : 0;
        if (bgIndex < 0 || bgIndex >= BACKGROUND_COLOR_IDS.length) bgIndex = 0;
        canvas.drawColor(getResources().getColor(BACKGROUND_COLOR_IDS[bgIndex], null));

        if (playerX == 0) {
            playerX = getWidth() / 4f;
            playerY = getHeight() / 2f;
        }

        // Pipes
        int pipeColor = getResources().getColor(R.color.pipe_color, null);
        int pipeBorder = getResources().getColor(R.color.pipe_border, null);
        paint.setColor(pipeColor);
        for (ObstaclePair o : obstacles) {
            canvas.drawRect(o.topRect, paint);
            canvas.drawRect(o.bottomRect, paint);
            paint.setColor(pipeBorder);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(4f);
            canvas.drawRect(o.topRect, paint);
            canvas.drawRect(o.bottomRect, paint);
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(pipeColor);
        }

        // Player (with selected bird tint)
        int birdIndex = gamePrefs != null ? gamePrefs.getSelectedBird() : 0;
        if (birdIndex > 0 && birdIndex < BIRD_TINT_COLORS.length) {
            paint.setColorFilter(new PorterDuffColorFilter(BIRD_TINT_COLORS[birdIndex], PorterDuff.Mode.MULTIPLY));
        }
        canvas.drawBitmap(
                playerBitmap,
                playerX - playerBitmap.getWidth() / 2f,
                playerY - playerBitmap.getHeight() / 2f,
                paint
        );
        paint.setColorFilter(null);

        // HUD with background (Score, Best + medal)
        float hudTop = 24;
        float hudLeft = 24;
        paint.setTextSize(44);
        paint.setColor(Color.WHITE);
        String scoreStr = "Score: " + score;
        String highStr = "Best: " + highScore;
        Rect scoreBounds = new Rect();
        Rect highBounds = new Rect();
        paint.getTextBounds(scoreStr, 0, scoreStr.length(), scoreBounds);
        paint.getTextBounds(highStr, 0, highStr.length(), highBounds);
        float pad = 16f;
        float lineH = scoreBounds.height() + 8;
        float medalSize = 36f;
        float boxRight = hudLeft + Math.max(scoreBounds.width(), highBounds.width() + medalSize + 8) + pad * 2;
        float boxBottom = hudTop + lineH * 2 + pad * 2;
        paint.setColor(getResources().getColor(R.color.hud_bg, null));
        canvas.drawRoundRect(hudLeft, hudTop, boxRight, boxBottom, 12, 12, paint);
        paint.setColor(Color.WHITE);
        canvas.drawText(scoreStr, hudLeft + pad, hudTop + pad + scoreBounds.height(), paint);
        canvas.drawText(highStr, hudLeft + pad, hudTop + pad + scoreBounds.height() + lineH, paint);
        // Medal next to Best when current score earns one
        int medalId = getMedalDrawableId(score);
        if (medalId != 0) {
            Drawable medal = ContextCompat.getDrawable(getContext(), medalId);
            if (medal != null) {
                float mx = hudLeft + pad + highBounds.width() + 8;
                float my = hudTop + pad + scoreBounds.height() + lineH - medalSize;
                medal.setBounds((int) mx, (int) my, (int) (mx + medalSize), (int) (my + medalSize));
                medal.draw(canvas);
            }
        }
        paint.setColorFilter(null);

        if (isGameOver) {
            float cx = getWidth() / 2f;
            float cy = getHeight() / 2f;
            paint.setColor(getResources().getColor(R.color.game_over_overlay, null));
            canvas.drawRect(0, 0, getWidth(), getHeight(), paint);
            paint.setColor(Color.WHITE);
            paint.setTextAlign(Paint.Align.CENTER);
            paint.setTextSize(72);
            canvas.drawText("GAME OVER", cx, cy - 40, paint);
            paint.setTextSize(36);
            canvas.drawText("Tap to Restart", cx, cy + 50, paint);
            // "Change background" button
            String changeBgText = getContext().getString(R.string.change_background);
            paint.setTextSize(32);
            Rect changeBgBounds = new Rect();
            paint.getTextBounds(changeBgText, 0, changeBgText.length(), changeBgBounds);
            float changeBgY = getHeight() - 80f;
            canvas.drawText(changeBgText, cx, changeBgY, paint);
            float padz = 40f;
            changeBackgroundBounds.set(cx - changeBgBounds.width() / 2f - padz, changeBgY - changeBgBounds.height() - 8,
                    cx + changeBgBounds.width() / 2f + padz, changeBgY + 8);
            if (score > 0 && score >= initialHighScore) {
                paint.setTextSize(32);
                paint.setColor(0xFFFFFF00);
                canvas.drawText("New record!", cx, cy + 100, paint);
            }
            // Medal / prize for best records
            if (medalId != 0) {
                String medalName = getMedalName(score);
                Drawable medal = ContextCompat.getDrawable(getContext(), medalId);
                if (medal != null) {
                    float mSize = 64f;
                    float my = cy + 140;
                    medal.setBounds((int) (cx - mSize / 2), (int) my, (int) (cx + mSize / 2), (int) (my + mSize));
                    medal.draw(canvas);
                }
                if (medalName != null) {
                    paint.setTextSize(28);
                    paint.setColor(Color.WHITE);
                    canvas.drawText(medalName + " medal!", cx, cy + 220, paint);
                }
            }
            paint.setTextAlign(Paint.Align.LEFT);
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (event.getAction() == MotionEvent.ACTION_DOWN) {
            if (isGameOver) {
                if (changeBackgroundBounds.contains(event.getX(), event.getY())) {
                    getContext().startActivity(new Intent(getContext(), BirdsActivity.class));
                } else {
                    resetGame();
                }
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

    public void pauseGame() {
        isPaused = true;
    }

    public void resumeGame() {
        isPaused = false;
    }

    public boolean isPaused() {
        return isPaused;
    }

    /** Current high score (for menu display). May be 0 until DB load completes. */
    public int getHighScore() {
        return highScore;
    }

    /** Returns drawable id for medal at this score, or 0 if no medal. */
    public static int getMedalDrawableId(int score) {
        if (score >= MEDAL_PLATINUM) return R.drawable.medal_platinum;
        if (score >= MEDAL_GOLD) return R.drawable.medal_gold;
        if (score >= MEDAL_SILVER) return R.drawable.medal_silver;
        if (score >= MEDAL_BRONZE) return R.drawable.medal_bronze;
        return 0;
    }

    /** Returns medal tier name for score, or null. */
    public static String getMedalName(int score) {
        if (score >= MEDAL_PLATINUM) return "Platinum";
        if (score >= MEDAL_GOLD) return "Gold";
        if (score >= MEDAL_SILVER) return "Silver";
        if (score >= MEDAL_BRONZE) return "Bronze";
        return null;
    }

}

