package com.wizm0hit.flappybird;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

public class Bird {
    public int x, y;
    public int velocity = 0;
    public final int GRAVITY = 2;
    public final int JUMP_STRENGTH = -24;

    // v1.1.0 Rotation Mechanics
    public float angle = 0f;
    private static final float MAX_UPWARD_ANGLE = -20f;
    private static final float MAX_DOWNWARD_ANGLE = 90f;
    private static final float HANG_TIME_THRESHOLD = 5f;

    private Bitmap[] flapFrames = new Bitmap[3];
    private int currentFrame = 0;
    private int frameDelayCount = 0;

    public Bird(Context context, String type, int startX, int startY) {
        this.x = startX;
        this.y = startY;

        int dRes, mRes, uRes;
        if ("blue".equals(type)) {
            dRes = R.drawable.bluebird_downflap;
            mRes = R.drawable.bluebird_midflap;
            uRes = R.drawable.bluebird_upflap;
        } else if ("red".equals(type)) {
            dRes = R.drawable.redbird_downflap;
            mRes = R.drawable.redbird_midflap;
            uRes = R.drawable.redbird_upflap;
        } else {
            dRes = R.drawable.yellowbird_downflap;
            mRes = R.drawable.yellowbird_midflap;
            uRes = R.drawable.yellowbird_upflap;
        }

        flapFrames[0] = BitmapFactory.decodeResource(context.getResources(), dRes);
        flapFrames[1] = BitmapFactory.decodeResource(context.getResources(), mRes);
        flapFrames[2] = BitmapFactory.decodeResource(context.getResources(), uRes);

        for (int i = 0; i < 3; i++) {
            flapFrames[i] = Bitmap.createScaledBitmap(flapFrames[i], 120, 90, false);
        }
    }

    public void update(boolean isDying) {
        velocity += GRAVITY;
        y += velocity;

        // 1. Dynamic Flapping Animation Cycle Logic
        if (!isDying) {
            frameDelayCount++;
            if (frameDelayCount > 4) {
                currentFrame = (currentFrame + 1) % 3;
                frameDelayCount = 0;
            }
        } else {
            currentFrame = 1;
        }

        // 2. Smoothed Angular Momentum Calculation
        if (velocity < 0) {
            angle = MAX_UPWARD_ANGLE;
        } else if (velocity > HANG_TIME_THRESHOLD) {
            angle += 4.5f;
            if (angle > MAX_DOWNWARD_ANGLE) {
                angle = MAX_DOWNWARD_ANGLE;
            }
        } else {
            angle = 0f;
        }
    }

    public void jump() {
        velocity = JUMP_STRENGTH;
        angle = MAX_UPWARD_ANGLE;
    }

    public Bitmap getCurrentBitmap() {
        return flapFrames[currentFrame];
    }

    public int getWidth() { return flapFrames[0].getWidth(); }
    public int getHeight() { return flapFrames[0].getHeight(); }
}