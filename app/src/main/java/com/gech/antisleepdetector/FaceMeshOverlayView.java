package com.gech.antisleepdetector;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PointF;
import android.util.AttributeSet;
import android.view.View;

import java.util.ArrayList;
import java.util.List;

/**
 * Custom overlay view that draws face mesh landmarks and eye contours
 * on top of the camera preview.
 */
public class FaceMeshOverlayView extends View {

    private final Paint eyeOpenPaint;
    private final Paint eyeClosedPaint;

    private List<PointF> landmarks = new ArrayList<>();
    private boolean isSleeping = false;
    private int sourceWidth = 640;
    private int sourceHeight = 480;
    private boolean isFrontCamera = true;

    // MediaPipe Face Mesh eye landmark indices
    private static final int[] RIGHT_EYE = {33, 7, 163, 144, 145, 153, 154, 155, 133, 173, 157, 158, 159, 160, 161, 246};
    private static final int[] LEFT_EYE = {362, 382, 381, 380, 374, 373, 390, 249, 263, 466, 388, 387, 386, 385, 384, 398};

    public FaceMeshOverlayView(Context context, AttributeSet attrs) {
        super(context, attrs);

        eyeOpenPaint = new Paint();
        eyeOpenPaint.setColor(Color.parseColor("#66E0A3"));
        eyeOpenPaint.setStyle(Paint.Style.STROKE);
        eyeOpenPaint.setStrokeWidth(5f);
        eyeOpenPaint.setAntiAlias(true);

        eyeClosedPaint = new Paint();
        eyeClosedPaint.setColor(Color.parseColor("#FF4444"));
        eyeClosedPaint.setStyle(Paint.Style.STROKE);
        eyeClosedPaint.setStrokeWidth(8f);
        eyeClosedPaint.setAntiAlias(true);
    }

    public void setSourceDimensions(int width, int height) {
        if (this.sourceWidth != width || this.sourceHeight != height) {
            this.sourceWidth = width;
            this.sourceHeight = height;
            postInvalidate();
        }
    }

    public void setFrontCamera(boolean frontCamera) {
        this.isFrontCamera = frontCamera;
    }

    public void updateLandmarks(List<PointF> newLandmarks, boolean sleeping) {
        this.landmarks = newLandmarks != null ? new ArrayList<>(newLandmarks) : new ArrayList<>();
        this.isSleeping = sleeping;
        postInvalidate();
    }

    public void clearLandmarks() {
        this.landmarks.clear();
        postInvalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        if (landmarks == null || landmarks.isEmpty()) return;

        float viewWidth = getWidth();
        float viewHeight = getHeight();

        // Calculate scaling to match PreviewView.ScaleType.FILL_CENTER
        float scaleX = viewWidth / (float) sourceWidth;
        float scaleY = viewHeight / (float) sourceHeight;
        float scale = Math.max(scaleX, scaleY);
        
        float offsetX = (viewWidth - (sourceWidth * scale)) / 2f;
        float offsetY = (viewHeight - (sourceHeight * scale)) / 2f;

        Paint eyePaint = isSleeping ? eyeClosedPaint : eyeOpenPaint;
        drawEyeContour(canvas, RIGHT_EYE, eyePaint, scale, offsetX, offsetY);
        drawEyeContour(canvas, LEFT_EYE, eyePaint, scale, offsetX, offsetY);
    }

    private void drawEyeContour(Canvas canvas, int[] eyeIndices, Paint paint, float scale, float offsetX, float offsetY) {
        for (int i = 0; i < eyeIndices.length; i++) {
            int currentIdx = eyeIndices[i];
            int nextIdx = eyeIndices[(i + 1) % eyeIndices.length];

            if (currentIdx >= landmarks.size() || nextIdx >= landmarks.size()) continue;

            PointF start = transformPoint(landmarks.get(currentIdx), scale, offsetX, offsetY);
            PointF end = transformPoint(landmarks.get(nextIdx), scale, offsetX, offsetY);

            canvas.drawLine(start.x, start.y, end.x, end.y, paint);
        }
    }

    private PointF transformPoint(PointF point, float scale, float offsetX, float offsetY) {
        float x = point.x * sourceWidth * scale + offsetX;
        float y = point.y * sourceHeight * scale + offsetY;

        if (isFrontCamera) {
            x = getWidth() - x; // Mirror final x position
        }

        return new PointF(x, y);
    }
}
