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

    // v1.1.0 Death Sequence States
    private boolean isDying = false;
    private int deathPauseFramesCount = 0;
    private static final int DEATH_PAUSE_DURATION = 15;

    // v1.1.0 Variables
    private float scoreScale = 1.0f;
    private int shakeDuration = 0;
    private final java.util.Random shakeRandom = new java.util.Random();

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

        android.graphics.Typeface pixelFont = androidx.core.content.res.ResourcesCompat.getFont(context, R.font.pixeloperatorbold);
        if (pixelFont != null) {
            fallbackPaint.setTypeface(pixelFont);
        }

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

        // --- SCREEN SHAKE RENDERING ENGINE ---
        canvas.save();
        if (shakeDuration > 0) {
            float intensity = (float) shakeDuration / 15f;
            float offsetX = ((shakeRandom.nextFloat() * 30) - 15) * intensity;
            float offsetY = ((shakeRandom.nextFloat() * 30) - 15) * intensity;

            canvas.translate(offsetX, offsetY);
            shakeDuration--;
        }

        if (scaledBg != null) {
            canvas.drawBitmap(scaledBg, 0, 0, null);
        } else {
            canvas.drawColor(Color.parseColor("#70C5CE"));
        }

        // --- SCORE SCALE ANIMATION TICKER ---
        if (scoreScale > 1.0f) {
            scoreScale -= 0.05f; // Smoothly shrink back down frame-by-frame
            if (scoreScale < 1.0f) scoreScale = 1.0f;
        }

        // --- GAMEPLAY / PROGRESSION LOOP ---
        if (!gameOver) {
            if (!isDying) {
                bird.update(false);

                // Floor/Ceiling crash check
                boolean hitFloor   = bird.y > screenH - bird.getHeight();
                boolean hitCeiling = bird.y < 0;
                if (hitFloor || hitCeiling) {
                    playSound(soundHit, 2);
                    playSound(soundDie, 2);
                    triggerGameOver();
                    return;
                }

                // Update and recycle pipes
                for (Pipe pipe : pipes) {
                    pipe.update();

                    if (pipe.topPipeBitmap == null || pipe.bottomPipeBitmap == null)
                        continue;

                    // Recycle off-screen pipes
                    if (pipe.x + pipe.getWidth() < 0) {
                        pipe.x = screenW;
                        pipe.selectRandomPipeAsset();

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
                        scoreScale = 1.4f; // Pop score scale when passing pipe
                    }

                    // Check boundaries for pipe collisions
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
                        isDying = true; // Enter dramatic pause
                        deathPauseFramesCount = 0;

                        // Trigger screen shake duration (15 frames)
                        shakeDuration = 15;

                        // Trigger phone vibration hardware haptics
                        android.os.Vibrator vibrator = (android.os.Vibrator) getContext().getSystemService(Context.VIBRATOR_SERVICE);
                        if (vibrator != null && vibrator.hasVibrator()) {
                            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                                vibrator.vibrate(android.os.VibrationEffect.createOneShot(200, android.os.VibrationEffect.DEFAULT_AMPLITUDE));
                            } else {
                                vibrator.vibrate(200);
                            }
                        }
                    }
                }
            } else {
                // --- DEATH SEQUENCE ---
                if (deathPauseFramesCount < DEATH_PAUSE_DURATION) {
                    deathPauseFramesCount++;
                } else {
                    if (deathPauseFramesCount == DEATH_PAUSE_DURATION) {
                        playSound(soundDie, 2);
                        deathPauseFramesCount++;
                        bird.velocity = 0; // Pure drop vertical force setup
                    }
                    bird.update(true);
                }

                // Hard ground collision stop
                if (bird.y > screenH - bird.getHeight()) {
                    triggerGameOver();
                    return;
                }
            }

            // --- RENDER PIPES ---
            for (Pipe pipe : pipes) {
                if (pipe.topPipeBitmap != null && pipe.bottomPipeBitmap != null) {
                    canvas.drawBitmap(pipe.topPipeBitmap, pipe.x, pipe.topY, null);
                    canvas.drawBitmap(pipe.bottomPipeBitmap, pipe.x, pipe.bottomY, null);
                }
            }

            // --- RENDER BIRD WITH CANVAS ROTATION ---
            canvas.save();
            float pivotX = bird.x + (bird.getWidth() / 2f);
            float pivotY = bird.y + (bird.getHeight() / 2f);
            canvas.rotate(bird.angle, pivotX, pivotY);
            canvas.drawBitmap(bird.getCurrentBitmap(), bird.x, bird.y, null);
            canvas.restore();

        } else {
            // Game Over Banner state
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

            // --- TWEAK AND CRISPEN THESE LINES BELOW ---
            fallbackPaint.setColor(Color.WHITE);
            fallbackPaint.setTextSize(54f); // Bumped up slightly from 42f for retro look
            fallbackPaint.setTextAlign(Paint.Align.CENTER);

            // Optional: Adds a subtle retro black outline shadow
            fallbackPaint.setShadowLayer(4f, 2f, 2f, Color.BLACK);

            canvas.drawText("tap to return", screenW / 2f, screenH * 0.62f, fallbackPaint);

            // Clear shadow layer so it doesn't leak into fallback score drawing text
            fallbackPaint.clearShadowLayer();
        }

        drawScore(canvas);
        canvas.restore(); // Closes screen shake engine save wrapper
    }

    private void drawScore(Canvas canvas) {
        String s = String.valueOf(score);
        int digitW = 65;
        int startX = (screenW - s.length() * digitW) / 2;
        int startY = 150;

        for (int i = 0; i < s.length(); i++) {
            int digit = s.charAt(i) - '0';
            Bitmap sprite = (digit >= 0 && digit < 10) ? scoreDigits[digit] : null;

            if (sprite != null) {
                float centerX = startX + (i * digitW) + (sprite.getWidth() / 2f);
                float centerY = startY + (sprite.getHeight() / 2f);

                canvas.save();
                // Scale relative to the exact center of this specific digit
                canvas.scale(scoreScale, scoreScale, centerX, centerY);
                canvas.drawBitmap(sprite, startX + i * digitW, startY, null);
                canvas.restore(); // MUST restore immediately after the specific save
            } else {
                fallbackPaint.setColor(Color.WHITE);
                fallbackPaint.setTextSize(80f * scoreScale);
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
                if (!isDying) {
                    bird.jump();
                    playSound(soundWing, 1);
                }
            } else {
                playSound(soundSwoosh, 1);
                isDying = false;
                deathPauseFramesCount = 0;
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