package com.wizm0hit.flappybird;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

public class Bird {
    public int x, y;
    public int velocity = 0;
    public final int GRAVITY = 2;
    public final int JUMP_STRENGTH = -24;

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

    public void update() {
        velocity += GRAVITY;
        y += velocity;

        // Dynamic Flapping Animation Cycle Logic
        frameDelayCount++;
        if (frameDelayCount > 4) { // Change wing frame every 4 updates
            currentFrame = (currentFrame + 1) % 3;
            frameDelayCount = 0;
        }
    }

    public void jump() {
        velocity = JUMP_STRENGTH;
    }

    public Bitmap getCurrentBitmap() {
        return flapFrames[currentFrame];
    }

    public int getWidth() { return flapFrames[0].getWidth(); }
    public int getHeight() { return flapFrames[0].getHeight(); }
}