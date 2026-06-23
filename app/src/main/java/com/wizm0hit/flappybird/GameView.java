package com.wizm0hit.flappybird;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.media.AudioAttributes;
import android.media.SoundPool;
import android.os.Handler;
import android.os.Looper;
import android.view.MotionEvent;
import android.view.View;

import androidx.annotation.NonNull;

import java.util.ArrayList;

public class GameView extends View {

    // Game Loop Variables
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable loopTick = new Runnable() {
        @Override
        public void run() {
            if (!gameOver) {
                invalidate();
                handler.postDelayed(this, UPDATE_MILLIS);
            }
        }
    };
    private static final int UPDATE_MILLIS = 20;

    // Graphic Assets
    private Bitmap scaledBg;
    private Bitmap gameOverBitmap;
    private Bitmap[] scoreDigits = new Bitmap[10];

    private final Paint fallbackPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    // Game State Variables
    private Bird bird;
    private ArrayList<Pipe> pipes = new ArrayList<>();
    private int score = 0;
    private boolean gameOver = false;
    private boolean layoutReady = false;
    private int screenW, screenH;

    // Audio Engine Variables
    private SoundPool soundPool;
    private int soundWing, soundPoint, soundHit, soundDie, soundSwoosh;
    private boolean audioReady = false;
    private int loadedSoundsCount = 0;

    // SharedPreferences
    private final SharedPreferences prefs;

    public GameView(Context context, String birdType, boolean isDark) {
        super(context);

        prefs = context.getSharedPreferences("FlappyBirdPrefs", Context.MODE_PRIVATE);

        int bgRes = isDark ? R.drawable.background_night : R.drawable.background_day;
        scaledBg = BitmapFactory.decodeResource(getResources(), bgRes);

        gameOverBitmap = BitmapFactory.decodeResource(getResources(), R.drawable.gameover);

        for (int i = 0; i < 10; i++) {
            int resId = getResources().getIdentifier(
                    "num" + i, "drawable", context.getPackageName());
            if (resId != 0) {
                Bitmap raw = BitmapFactory.decodeResource(getResources(), resId);
                if (raw != null) {
                    scoreDigits[i] = Bitmap.createScaledBitmap(raw, 60, 80, false);
                }
            }
        }

        bird = new Bird(context, birdType, 200, 500);

        // Setup sound system
        initializeAudioEngine(context);
    }

    private void initializeAudioEngine(Context context) {
        AudioAttributes audioAttributes = new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build();

        soundPool = new SoundPool.Builder()
                .setMaxStreams(5)
                .setAudioAttributes(audioAttributes)
                .build();

        soundPool.setOnLoadCompleteListener((poolInstance, sampleId, status) -> {
            if (status == 0) {
                loadedSoundsCount++;
                if (loadedSoundsCount >= 5) {
                    audioReady = true;
                }
            }
        });

        // Load uncompressed audio vectors asynchronously
        soundWing   = soundPool.load(context, R.raw.wing, 1);
        soundPoint  = soundPool.load(context, R.raw.point, 1);
        soundHit    = soundPool.load(context, R.raw.hit, 1);
        soundDie    = soundPool.load(context, R.raw.die, 1);
        soundSwoosh = soundPool.load(context, R.raw.swoosh, 1);
    }

    private void playSound(int soundId, int priority) {
        if (audioReady && soundPool != null && soundId != 0) {
            soundPool.play(soundId, 1.0f, 1.0f, priority, 0, 1.0f);
        }
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldW, int oldH) {
        super.onSizeChanged(w, h, oldW, oldH);
        if (w <= 0 || h <= 0) return;

        screenW = w;
        screenH = h;

        if (scaledBg != null) {
            scaledBg = Bitmap.createScaledBitmap(scaledBg, w, h, false);
        }

        if (gameOverBitmap != null) {
            int goW = (int) (w * 0.55f);
            int goH = Math.max(1, (int) (goW * 0.23f));
            gameOverBitmap = Bitmap.createScaledBitmap(gameOverBitmap, goW, goH, false);
        }

        bird.y = h / 2;

        // Spawn first cycle of pipes
        if (pipes.isEmpty()) {
            pipes.add(new Pipe(getContext(), w, h));
            pipes.add(new Pipe(getContext(), w + (w / 2) + 250, h));
        }

        layoutReady = true;

        handler.removeCallbacks(loopTick);
        handler.postDelayed(loopTick, UPDATE_MILLIS);
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        super.onDraw(canvas);

        if (!layoutReady) return;

        if (scaledBg != null) {
            canvas.drawBitmap(scaledBg, 0, 0, null);
        } else {
            canvas.drawColor(Color.parseColor("#70C5CE"));
        }

        if (!gameOver) {
            bird.update();

            boolean hitFloor   = bird.y > screenH - bird.getHeight();
            boolean hitCeiling = bird.y < 0;
            if (hitFloor || hitCeiling) {
                playSound(soundHit, 2);
                playSound(soundDie, 2);
                triggerGameOver();
                return;
            }

            canvas.drawBitmap(bird.getCurrentBitmap(), bird.x, bird.y, null);

            for (Pipe pipe : pipes) {
                pipe.update();

                if (pipe.topPipeBitmap == null || pipe.bottomPipeBitmap == null)
                    continue;

                canvas.drawBitmap(pipe.topPipeBitmap,    pipe.x, pipe.topY,    null);
                canvas.drawBitmap(pipe.bottomPipeBitmap, pipe.x, pipe.bottomY, null);

                // Recycle off-screen pipes
                if (pipe.x + pipe.getWidth() < 0) {
                    pipe.x = screenW;
                    pipe.selectRandomPipeAsset();

                    // Track position of the other pipe on screen to keep ranges fair
                    int activePipeOpeningY = -1;
                    for (Pipe otherPipe : pipes) {
                        if (otherPipe != pipe) {
                            activePipeOpeningY = otherPipe.getOpeningY();
                            break;
                        }
                    }

                    pipe.resetPosition(screenH, activePipeOpeningY);

                    score++;
                    playSound(soundPoint, 1);
                }

                // Collisions
                Rect birdRect = new Rect(
                        bird.x + 10, bird.y + 10,
                        bird.x + bird.getWidth()  - 10,
                        bird.y + bird.getHeight() - 10);

                Rect topRect = new Rect(
                        pipe.x, pipe.topY,
                        pipe.x + pipe.getWidth(),
                        pipe.topY + pipe.topPipeBitmap.getHeight());

                Rect botRect = new Rect(
                        pipe.x, pipe.bottomY,
                        pipe.x + pipe.getWidth(),
                        pipe.bottomY + pipe.bottomPipeBitmap.getHeight());

                if (Rect.intersects(birdRect, topRect) || Rect.intersects(birdRect, botRect)) {
                    playSound(soundHit, 2);
                    playSound(soundDie, 2);
                    triggerGameOver();
                    return;
                }
            }

        } else {
            if (gameOverBitmap != null) {
                float bx = (screenW - gameOverBitmap.getWidth())  / 2f;
                float by = screenH * 0.40f;
                canvas.drawBitmap(gameOverBitmap, bx, by, null);
            } else {
                fallbackPaint.setColor(Color.WHITE);
                fallbackPaint.setTextSize(96f);
                fallbackPaint.setTextAlign(Paint.Align.CENTER);
                canvas.drawText("GAME OVER", screenW / 2f, screenH * 0.45f, fallbackPaint);
            }

            fallbackPaint.setColor(Color.WHITE);
            fallbackPaint.setTextSize(42f);
            fallbackPaint.setTextAlign(Paint.Align.CENTER);
            canvas.drawText("tap to return", screenW / 2f, screenH * 0.60f, fallbackPaint);
        }

        drawScore(canvas);
    }

    private void drawScore(Canvas canvas) {
        String s = String.valueOf(score);
        int digitW = 65;
        int startX = (screenW - s.length() * digitW) / 2;

        for (int i = 0; i < s.length(); i++) {
            int digit = s.charAt(i) - '0';
            Bitmap sprite = (digit >= 0 && digit < 10) ? scoreDigits[digit] : null;

            if (sprite != null) {
                canvas.drawBitmap(sprite, startX + i * digitW, 150, null);
            } else {
                fallbackPaint.setColor(Color.WHITE);
                fallbackPaint.setTextSize(80f);
                fallbackPaint.setTextAlign(Paint.Align.LEFT);
                canvas.drawText(String.valueOf(digit), startX + i * digitW, 220, fallbackPaint);
            }
        }
    }

    private void triggerGameOver() {
        if (gameOver) return;
        gameOver = true;

        handler.removeCallbacks(loopTick);

        int best = prefs.getInt("highScore", 0);
        if (score > best) {
            prefs.edit().putInt("highScore", score).apply();
        }

        invalidate();
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (event.getAction() == MotionEvent.ACTION_DOWN) {
            if (!gameOver) {
                bird.jump();
                playSound(soundWing, 1);
            } else {
                playSound(soundSwoosh, 1);
                ((Activity) getContext()).finish();
            }
        }
        return true;
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        handler.removeCallbacks(loopTick);

        if (soundPool != null) {
            soundPool.release();
            soundPool = null;
        }
    }
}