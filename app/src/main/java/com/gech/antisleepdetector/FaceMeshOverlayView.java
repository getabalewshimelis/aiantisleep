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
 * Exact-aligned face mesh overlay with perfect eye position tracking
 */
public class FaceMeshOverlayView extends View {

    private final Paint eyeOpenPaint;
    private final Paint eyeClosedPaint;
    private final Paint debugPaint;

    private List<PointF> landmarks = new ArrayList<>();
    private boolean isSleeping = false;
    private int sourceWidth = 640;
    private int sourceHeight = 480;
    private boolean isFrontCamera = true;
    private boolean debugMode = false;

    // Exact eye contour indices from MediaPipe Face Mesh
    private static final int[] RIGHT_EYE = {33, 7, 163, 144, 145, 153, 154, 155, 133, 173, 157, 158, 159, 160, 161, 246};
    private static final int[] LEFT_EYE = {362, 382, 381, 380, 374, 373, 390, 249, 263, 466, 388, 387, 386, 385, 384, 398};

    // Transformation cache
    private float cachedScale = 1f;
    private float cachedOffsetX = 0f;
    private float cachedOffsetY = 0f;

    public FaceMeshOverlayView(Context context, AttributeSet attrs) {
        super(context, attrs);

        eyeOpenPaint = new Paint();
        eyeOpenPaint.setColor(Color.parseColor("#66E0A3"));
        eyeOpenPaint.setStyle(Paint.Style.STROKE);
        eyeOpenPaint.setStrokeWidth(6f);
        eyeOpenPaint.setAntiAlias(true);
        eyeOpenPaint.setStrokeCap(Paint.Cap.ROUND);
        eyeOpenPaint.setStrokeJoin(Paint.Join.ROUND);

        eyeClosedPaint = new Paint();
        eyeClosedPaint.setColor(Color.parseColor("#FF4444"));
        eyeClosedPaint.setStyle(Paint.Style.STROKE);
        eyeClosedPaint.setStrokeWidth(8f);
        eyeClosedPaint.setAntiAlias(true);
        eyeClosedPaint.setStrokeCap(Paint.Cap.ROUND);
        eyeClosedPaint.setStrokeJoin(Paint.Join.ROUND);

        debugPaint = new Paint();
        debugPaint.setColor(Color.YELLOW);
        debugPaint.setStyle(Paint.Style.FILL);
        debugPaint.setAntiAlias(true);
        debugPaint.setTextSize(24f);

        // Enable hardware acceleration
        setLayerType(LAYER_TYPE_HARDWARE, null);
    }

    public void setSourceDimensions(int width, int height) {
        if (this.sourceWidth != width || this.sourceHeight != height) {
            this.sourceWidth = width;
            this.sourceHeight = height;
            invalidate();
        }
    }

    public void setFrontCamera(boolean frontCamera) {
        this.isFrontCamera = frontCamera;
    }

    public void setDebugMode(boolean debug) {
        this.debugMode = debug;
        invalidate();
    }

    public void updateLandmarks(List<PointF> newLandmarks, boolean sleeping) {
        this.landmarks = newLandmarks != null ? new ArrayList<>(newLandmarks) : new ArrayList<>();
        this.isSleeping = sleeping;
        invalidate();
    }

    public void clearLandmarks() {
        this.landmarks.clear();
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        if (landmarks == null || landmarks.isEmpty()) return;

        // Recalculate transformation
        calculateTransformation();

        // Debug mode: show all landmarks
        if (debugMode) {
            drawDebugLandmarks(canvas);
        }

        // Draw eye contours
        Paint eyePaint = isSleeping ? eyeClosedPaint : eyeOpenPaint;
        drawEyeContour(canvas, RIGHT_EYE, eyePaint);
        drawEyeContour(canvas, LEFT_EYE, eyePaint);
    }

    private void calculateTransformation() {
        float viewWidth = getWidth();
        float viewHeight = getHeight();

        if (viewWidth == 0 || viewHeight == 0) return;

        // Match CameraX PreviewView FILL_CENTER scaling
        float scaleX = viewWidth / (float) sourceWidth;
        float scaleY = viewHeight / (float) sourceHeight;

        // Use max to fill the view (crop if needed)
        cachedScale = Math.max(scaleX, scaleY);

        // Center the scaled image
        float scaledWidth = sourceWidth * cachedScale;
        float scaledHeight = sourceHeight * cachedScale;

        cachedOffsetX = (viewWidth - scaledWidth) / 2f;
        cachedOffsetY = (viewHeight - scaledHeight) / 2f;
    }

    private void drawDebugLandmarks(Canvas canvas) {
        Paint dotPaint = new Paint();
        dotPaint.setColor(Color.CYAN);
        dotPaint.setStyle(Paint.Style.FILL);
        dotPaint.setAntiAlias(true);

        Paint textPaint = new Paint();
        textPaint.setColor(Color.WHITE);
        textPaint.setTextSize(20f);
        textPaint.setAntiAlias(true);
        textPaint.setShadowLayer(4f, 0f, 0f, Color.BLACK);

        // Draw all landmarks
        for (int i = 0; i < landmarks.size(); i++) {
            PointF pt = transformPoint(landmarks.get(i));
            canvas.drawCircle(pt.x, pt.y, 4f, dotPaint);
        }

        // Highlight key eye points
        int[] keyPoints = {33, 133, 159, 145, 158, 153, 362, 263, 386, 374, 385, 380};
        Paint keyDotPaint = new Paint();
        keyDotPaint.setColor(Color.RED);
        keyDotPaint.setStyle(Paint.Style.FILL);
        keyDotPaint.setAntiAlias(true);

        for (int idx : keyPoints) {
            if (idx < landmarks.size()) {
                PointF pt = transformPoint(landmarks.get(idx));
                canvas.drawCircle(pt.x, pt.y, 8f, keyDotPaint);
                canvas.drawText(String.valueOf(idx), pt.x + 10, pt.y - 10, textPaint);
            }
        }
    }

    private void drawEyeContour(Canvas canvas, int[] eyeIndices, Paint paint) {
        for (int i = 0; i < eyeIndices.length; i++) {
            int currentIdx = eyeIndices[i];
            int nextIdx = eyeIndices[(i + 1) % eyeIndices.length];

            if (currentIdx >= landmarks.size() || nextIdx >= landmarks.size()) continue;

            PointF start = transformPoint(landmarks.get(currentIdx));
            PointF end = transformPoint(landmarks.get(nextIdx));

            canvas.drawLine(start.x, start.y, end.x, end.y, paint);
        }
    }

    private PointF transformPoint(PointF point) {
        // MediaPipe gives normalized coordinates (0-1)
        // Transform to screen coordinates
        float x = point.x * sourceWidth * cachedScale + cachedOffsetX;
        float y = point.y * sourceHeight * cachedScale + cachedOffsetY;

        // Mirror horizontally for front camera
        if (isFrontCamera) {
            x = getWidth() - x;
        }

        return new PointF(x, y);
    }
}