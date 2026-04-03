package com.gech.antisleepdetector;

import android.Manifest;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.graphics.PointF;
import android.media.MediaPlayer;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.util.Log;
import android.util.Size;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.mediapipe.formats.proto.LandmarkProto;
import com.google.mediapipe.solutioncore.ResultListener;
import com.google.mediapipe.solutions.facemesh.FaceMesh;
import com.google.mediapipe.solutions.facemesh.FaceMeshOptions;
import com.google.mediapipe.solutions.facemesh.FaceMeshResult;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Camera-based sleep detection using MediaPipe Face Mesh.
 */
public class CameraFragment extends Fragment {

    private static final String TAG = "CameraFragment";

    private static final float EAR_THRESHOLD = 0.21f;
    private static final int SLEEP_FRAME_THRESHOLD = 15;

    // Eye indices
    private static final int RIGHT_EYE_TOP_1 = 159;
    private static final int RIGHT_EYE_BOTTOM_1 = 145;
    private static final int RIGHT_EYE_TOP_2 = 158;
    private static final int RIGHT_EYE_BOTTOM_2 = 153;
    private static final int RIGHT_EYE_LEFT = 33;
    private static final int RIGHT_EYE_RIGHT = 133;

    private static final int LEFT_EYE_TOP_1 = 386;
    private static final int LEFT_EYE_BOTTOM_1 = 374;
    private static final int LEFT_EYE_TOP_2 = 385;
    private static final int LEFT_EYE_BOTTOM_2 = 380;
    private static final int LEFT_EYE_LEFT = 362;
    private static final int LEFT_EYE_RIGHT = 263;

    private PreviewView previewView;
    private FaceMeshOverlayView faceMeshOverlay;
    private TextView statusText;
    private TextView earValueText;
    private View statusIndicator;
    private LinearLayout sleepWarningOverlay;
    private LinearLayout noPermissionView;
    private Button grantPermissionBtn;

    private ExecutorService cameraExecutor;
    private ProcessCameraProvider cameraProvider;
    private FaceMesh faceMesh;

    private int closedEyeFrameCount = 0;
    private boolean isSleeping = false;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private MediaPlayer alertPlayer;
    private Vibrator vibrator;
    private ObjectAnimator warningAnimator;

    private ActivityResultLauncher<String> permissionLauncher;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        permissionLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestPermission(),
                isGranted -> {
                    if (isGranted) {
                        showCameraUI();
                        startCamera();
                    } else {
                        showNoPermissionUI();
                    }
                }
        );
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_camera, container, false);

        previewView = view.findViewById(R.id.camera_preview);
        faceMeshOverlay = view.findViewById(R.id.face_mesh_overlay);
        statusText = view.findViewById(R.id.tv_status);
        earValueText = view.findViewById(R.id.tv_ear_value);
        statusIndicator = view.findViewById(R.id.status_indicator);
        sleepWarningOverlay = view.findViewById(R.id.sleep_warning_overlay);
        noPermissionView = view.findViewById(R.id.no_permission_view);
        grantPermissionBtn = view.findViewById(R.id.btn_grant_permission);

        cameraExecutor = Executors.newSingleThreadExecutor();
        vibrator = (Vibrator) requireContext().getSystemService(Context.VIBRATOR_SERVICE);

        grantPermissionBtn.setOnClickListener(v -> requestCameraPermission());

        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED) {
            showCameraUI();
            startCamera();
        } else {
            requestCameraPermission();
        }

        return view;
    }

    private void requestCameraPermission() {
        permissionLauncher.launch(Manifest.permission.CAMERA);
    }

    private void showCameraUI() {
        previewView.setVisibility(View.VISIBLE);
        noPermissionView.setVisibility(View.GONE);
    }

    private void showNoPermissionUI() {
        previewView.setVisibility(View.GONE);
        noPermissionView.setVisibility(View.VISIBLE);
        statusText.setText("Permission denied");
    }

    private void startCamera() {
        ListenableFuture<ProcessCameraProvider> cameraProviderFuture =
                ProcessCameraProvider.getInstance(requireContext());

        cameraProviderFuture.addListener(() -> {
            try {
                cameraProvider = cameraProviderFuture.get();
                bindCameraUseCases();
            } catch (Exception e) {
                Log.e(TAG, "Camera initialization failed", e);
            }
        }, ContextCompat.getMainExecutor(requireContext()));
    }

    private void bindCameraUseCases() {
        if (cameraProvider == null) return;

        Preview preview = new Preview.Builder().build();
        preview.setSurfaceProvider(previewView.getSurfaceProvider());

        ImageAnalysis imageAnalysis = new ImageAnalysis.Builder()
                .setTargetResolution(new Size(640, 480))
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build();

        initFaceMesh();
        imageAnalysis.setAnalyzer(cameraExecutor, this::analyzeImage);

        CameraSelector cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA;

        try {
            cameraProvider.unbindAll();
            cameraProvider.bindToLifecycle(getViewLifecycleOwner(), cameraSelector, preview, imageAnalysis);
            faceMeshOverlay.setFrontCamera(true);
            statusText.setText("Detecting...");
        } catch (Exception e) {
            Log.e(TAG, "Camera binding failed", e);
        }
    }

    private void initFaceMesh() {
        FaceMeshOptions options = FaceMeshOptions.builder()
                .setStaticImageMode(false)
                .setRefineLandmarks(true)
                .setMaxNumFaces(1)
                .setRunOnGpu(false)
                .build();

        faceMesh = new FaceMesh(requireContext(), options);
        faceMesh.setResultListener(this::processResult);
        faceMesh.setErrorListener((message, e) -> Log.e(TAG, "MediaPipe error: " + message, e));
    }

    private void analyzeImage(@NonNull ImageProxy imageProxy) {
        if (faceMesh == null) {
            imageProxy.close();
            return;
        }

        try {
            Bitmap bitmap = imageProxy.toBitmap();
            int rotationDegrees = imageProxy.getImageInfo().getRotationDegrees();
            
            if (rotationDegrees != 0) {
                Matrix matrix = new Matrix();
                matrix.postRotate(rotationDegrees);
                Bitmap rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);
                if (rotated != bitmap) bitmap.recycle();
                bitmap = rotated;
            }

            // Update overlay dimensions based on the processed bitmap
            final int width = bitmap.getWidth();
            final int height = bitmap.getHeight();
            mainHandler.post(() -> faceMeshOverlay.setSourceDimensions(width, height));

            faceMesh.send(bitmap, imageProxy.getImageInfo().getTimestamp() / 1000);
            bitmap.recycle();
        } catch (Exception e) {
            Log.e(TAG, "Error processing image", e);
        } finally {
            imageProxy.close();
        }
    }

    private void processResult(FaceMeshResult result) {
        if (result == null || result.multiFaceLandmarks() == null || result.multiFaceLandmarks().isEmpty()) {
            mainHandler.post(() -> {
                statusText.setText("No face detected");
                earValueText.setText("EAR: --");
                faceMeshOverlay.clearLandmarks();
                closedEyeFrameCount = 0;
                if (isSleeping) {
                    isSleeping = false;
                    hideSleepWarning();
                }
            });
            return;
        }

        List<LandmarkProto.NormalizedLandmark> faceLandmarks = result.multiFaceLandmarks().get(0).getLandmarkList();
        if (faceLandmarks.size() < 468) return;

        float rightEAR = calculateEAR(faceLandmarks, RIGHT_EYE_TOP_1, RIGHT_EYE_BOTTOM_1, RIGHT_EYE_TOP_2, RIGHT_EYE_BOTTOM_2, RIGHT_EYE_LEFT, RIGHT_EYE_RIGHT);
        float leftEAR = calculateEAR(faceLandmarks, LEFT_EYE_TOP_1, LEFT_EYE_BOTTOM_1, LEFT_EYE_TOP_2, LEFT_EYE_BOTTOM_2, LEFT_EYE_LEFT, LEFT_EYE_RIGHT);
        float avgEAR = (rightEAR + leftEAR) / 2.0f;

        List<PointF> points = new ArrayList<>();
        for (LandmarkProto.NormalizedLandmark lm : faceLandmarks) {
            points.add(new PointF(lm.getX(), lm.getY()));
        }

        boolean eyesClosed = avgEAR < EAR_THRESHOLD;
        if (eyesClosed) {
            closedEyeFrameCount++;
        } else {
            closedEyeFrameCount = 0;
        }

        boolean shouldSleep = closedEyeFrameCount >= SLEEP_FRAME_THRESHOLD;
        mainHandler.post(() -> {
            earValueText.setText(String.format("EAR: %.3f", avgEAR));
            faceMeshOverlay.updateLandmarks(points, shouldSleep);

            if (shouldSleep && !isSleeping) {
                isSleeping = true;
                showSleepWarning();
            } else if (!shouldSleep && isSleeping) {
                isSleeping = false;
                hideSleepWarning();
            }

            if (shouldSleep) {
                statusText.setText("😴 SLEEPING!");
                statusIndicator.setBackgroundResource(R.drawable.status_dot_sleep);
            } else if (eyesClosed) {
                statusText.setText("Eyes closing...");
                statusIndicator.setBackgroundResource(R.drawable.status_dot_sleep);
            } else {
                statusText.setText("✓ Awake");
                statusIndicator.setBackgroundResource(R.drawable.status_dot_awake);
            }
        });
    }

    private float calculateEAR(List<LandmarkProto.NormalizedLandmark> landmarks, int t1, int b1, int t2, int b2, int l, int r) {
        float v1 = distance(landmarks.get(t1), landmarks.get(b1));
        float v2 = distance(landmarks.get(t2), landmarks.get(b2));
        float h = distance(landmarks.get(l), landmarks.get(r));
        return (h < 0.0001f) ? 0 : (v1 + v2) / (2.0f * h);
    }

    private float distance(LandmarkProto.NormalizedLandmark a, LandmarkProto.NormalizedLandmark b) {
        float dx = a.getX() - b.getX();
        float dy = a.getY() - b.getY();
        return (float) Math.sqrt(dx * dx + dy * dy);
    }

    private void showSleepWarning() {
        if (getContext() == null) return;
        sleepWarningOverlay.setVisibility(View.VISIBLE);
        warningAnimator = ObjectAnimator.ofFloat(sleepWarningOverlay, "alpha", 0.7f, 1.0f);
        warningAnimator.setDuration(500);
        warningAnimator.setRepeatMode(ValueAnimator.REVERSE);
        warningAnimator.setRepeatCount(ValueAnimator.INFINITE);
        warningAnimator.start();

        playAlertSound();
        if (vibrator != null && vibrator.hasVibrator()) {
            vibrator.vibrate(VibrationEffect.createWaveform(new long[]{0, 500, 200}, 0));
        }
    }

    private void hideSleepWarning() {
        sleepWarningOverlay.setVisibility(View.GONE);
        if (warningAnimator != null) warningAnimator.cancel();
        stopAlertSound();
        if (vibrator != null) vibrator.cancel();
    }

    private void playAlertSound() {
        try {
            if (alertPlayer == null && getContext() != null) {
                Uri alarmUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM);
                if (alarmUri == null) alarmUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
                alertPlayer = MediaPlayer.create(requireContext(), alarmUri);
                if (alertPlayer != null) {
                    alertPlayer.setLooping(true);
                    alertPlayer.start();
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Sound error", e);
        }
    }

    private void stopAlertSound() {
        if (alertPlayer != null) {
            try { alertPlayer.stop(); alertPlayer.release(); } catch (Exception ignored) {}
            alertPlayer = null;
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        hideSleepWarning();
        if (faceMesh != null) faceMesh.close();
        if (cameraProvider != null) cameraProvider.unbindAll();
        if (cameraExecutor != null) cameraExecutor.shutdown();
    }
}
