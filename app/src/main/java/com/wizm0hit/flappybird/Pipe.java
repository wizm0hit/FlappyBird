package com.wizm0hit.flappybird;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import java.util.Random;

public class Pipe {
    public int x, topY, bottomY;
    public int speed = 12;
    public int gap = 420;
    public Bitmap topPipeBitmap, bottomPipeBitmap;
    private Context context;
    private int screenHeight;
    private Random random;

    private static final int MAX_VERTICAL_SHIFT = 250;

    public Pipe(Context context, int startX, int screenHeight) {
        this.context = context;
        this.screenHeight = screenHeight;
        this.x = startX;
        this.random = new Random();

        selectRandomPipeAsset();
        resetPosition(screenHeight, -1);
    }

    public void update() {
        x -= speed;
    }

    public void selectRandomPipeAsset() {
        int pipeImageRes = (random.nextInt(2) == 0)
                ? R.drawable.pipe_green
                : R.drawable.pipe_red;

        Bitmap rawPipe = BitmapFactory.decodeResource(context.getResources(), pipeImageRes);
        if (rawPipe == null) {
            return;
        }

        bottomPipeBitmap = Bitmap.createScaledBitmap(rawPipe, 200, screenHeight, false);

        Matrix matrix = new Matrix();
        matrix.postScale(1, -1);

        topPipeBitmap = Bitmap.createBitmap(
                bottomPipeBitmap,
                0,
                0,
                bottomPipeBitmap.getWidth(),
                bottomPipeBitmap.getHeight(),
                matrix,
                true
        );
    }

    public void resetPosition(int screenHeight, int previousOpeningY) {
        int minPipeHeight = 200;
        int maxPipeHeight = screenHeight - gap - minPipeHeight;

        int targetMin = minPipeHeight;
        int targetMax = maxPipeHeight;

        if (previousOpeningY != -1) {
            targetMin = Math.max(minPipeHeight, previousOpeningY - MAX_VERTICAL_SHIFT);
            targetMax = Math.min(maxPipeHeight, previousOpeningY + MAX_VERTICAL_SHIFT);
        }

        int targetRange = targetMax - targetMin;
        int openingY = (targetRange > 0)
                ? random.nextInt(targetRange) + targetMin
                : minPipeHeight;

        topY = openingY - topPipeBitmap.getHeight();
        bottomY = openingY + gap;
    }

    public int getWidth() {
        return bottomPipeBitmap != null ? bottomPipeBitmap.getWidth() : 200;
    }

    public int getOpeningY() {
        return topY + (topPipeBitmap != null ? topPipeBitmap.getHeight() : 0);
    }
}