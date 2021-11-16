package com.example.myapp;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.os.Bundle;
import android.util.Log;
import android.util.Size;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.view.PreviewView;
import androidx.core.app.ActivityCompat;
import androidx.core.app.ActivityCompat.OnRequestPermissionsResultCallback;
import androidx.core.content.ContextCompat;

import com.google.mlkit.common.model.LocalModel;
import com.google.mlkit.vision.camera.CameraSourceConfig;
import com.google.mlkit.vision.camera.CameraXSource;
import com.google.mlkit.vision.camera.DetectionTaskCallback;
import com.google.mlkit.vision.objects.DetectedObject;
import com.google.mlkit.vision.objects.ObjectDetection;
import com.google.mlkit.vision.objects.ObjectDetector;
import com.google.mlkit.vision.objects.custom.CustomObjectDetectorOptions;

import java.util.ArrayList;
import java.util.List;

public final class MainActivity extends AppCompatActivity
        implements OnRequestPermissionsResultCallback {
    private static final String TAG = "MainActivity";
    private static final int PERMISSION_REQUESTS = 1;

    private static final LocalModel localModel =
            new LocalModel.Builder()
                    .setAssetFilePath("custom_models/model.tflite").build();

    private final int lensFacing = CameraSourceConfig.CAMERA_FACING_BACK;
    private PreviewView previewView;
    private GraphicOverlay graphicOverlay;
    private boolean needUpdateGraphicOverlayImageSourceInfo;
    private DetectionTaskCallback<List<DetectedObject>> detectionTaskCallback;
    private CameraXSource cameraXSource;
    private CustomObjectDetectorOptions customObjectDetectorOptions;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d(TAG, "onCreate");

        setContentView(R.layout.activity_vision_cameraxsource_demo);
        previewView = findViewById(R.id.preview_view);
        if (previewView == null) {
            Log.d(TAG, "previewView is null");
        }
        graphicOverlay = findViewById(R.id.graphic_overlay);
        if (graphicOverlay == null) {
            Log.d(TAG, "graphicOverlay is null");
        }

        detectionTaskCallback =
                detectionTask -> detectionTask
                        .addOnSuccessListener(MainActivity.this::onDetectionTaskSuccess)
                        .addOnFailureListener(MainActivity.this::onDetectionTaskFailure);

        if (!allPermissionsGranted()) {
            getRuntimePermissions();
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        createThenStartCameraXSource();
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (cameraXSource != null) {
            cameraXSource.stop();
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (cameraXSource != null) {
            cameraXSource.close();
        }
    }

    @Override
    public void onRequestPermissionsResult(
            int requestCode, String[] permissions, int[] grantResults) {
        Log.i(TAG, "Permission granted!");
        createThenStartCameraXSource();
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
    }

    private void createThenStartCameraXSource() {
        if (cameraXSource != null) {
            cameraXSource.close();
        }

        customObjectDetectorOptions = getCustomObjectDetectorOptionsForLivePreview(localModel);


        ObjectDetector objectDetector = ObjectDetection.getClient(customObjectDetectorOptions);

        CameraSourceConfig.Builder builder =
                new CameraSourceConfig.Builder(
                        getApplicationContext(), objectDetector, detectionTaskCallback)
                        .setFacing(lensFacing);

        cameraXSource = new CameraXSource(builder.build(), previewView);
        needUpdateGraphicOverlayImageSourceInfo = true;
        cameraXSource.start();
    }

    public CustomObjectDetectorOptions getCustomObjectDetectorOptionsForLivePreview(LocalModel localModel) {
        return new CustomObjectDetectorOptions.Builder(localModel)
                .setDetectorMode(CustomObjectDetectorOptions.STREAM_MODE)
                .enableMultipleObjects()
                .enableClassification()
                .setMaxPerObjectLabelCount(1)
                .build();
    }

    private void onDetectionTaskSuccess(List<DetectedObject> results) {
        graphicOverlay.clear();
        if (needUpdateGraphicOverlayImageSourceInfo) {
            Size size = cameraXSource.getPreviewSize();
            if (size != null) {
                Log.d(TAG, "preview width: " + size.getWidth());
                Log.d(TAG, "preview height: " + size.getHeight());
                boolean isImageFlipped =
                        cameraXSource.getCameraFacing() == CameraSourceConfig.CAMERA_FACING_FRONT;
                if (isPortraitMode()) {
                    // Swap width and height sizes when in portrait, since it will be rotated by
                    // 90 degrees. The camera preview and the image being processed have the same size.
                    graphicOverlay.setImageSourceInfo(size.getHeight(), size.getWidth(), isImageFlipped);
                } else {
                    graphicOverlay.setImageSourceInfo(size.getWidth(), size.getHeight(), isImageFlipped);
                }
                needUpdateGraphicOverlayImageSourceInfo = false;
            } else {
                Log.d(TAG, "previewsize is null");
            }
        }
//        Log.v(TAG, "Number of object been detected: " + results.size());
        for (DetectedObject object : results) {
            if (object.getLabels().get(0).getConfidence() * 100 > 70 && object.getBoundingBox().width() < 630) {
//              Log.d(TAG+"_Golemina na Rect",object.getBoundingBox().width() + "      "+object.getBoundingBox().height());
                graphicOverlay.add(new ObjectGraphic(graphicOverlay, object));
            }
        }

        graphicOverlay.postInvalidate();
    }

    private void onDetectionTaskFailure(Exception e) {
        graphicOverlay.clear();
        graphicOverlay.postInvalidate();
        String error = "Failed to process. Error: " + e.getLocalizedMessage();
        Toast.makeText(
                graphicOverlay.getContext(), error + "\nCause: " + e.getCause(), Toast.LENGTH_SHORT)
                .show();
        Log.d(TAG, error);
    }

    private boolean isPortraitMode() {
        return getApplicationContext().getResources().getConfiguration().orientation
                != Configuration.ORIENTATION_LANDSCAPE;
    }

    private boolean isPermissionGranted(Context context, String permission) {
        if (ContextCompat.checkSelfPermission(context, permission)
                == PackageManager.PERMISSION_GRANTED) {
            Log.i(TAG, "Permission granted: " + permission);
            return true;
        }
        Log.i(TAG, "Permission NOT granted: " + permission);
        return false;
    }

    private boolean allPermissionsGranted() {
        for (String permission : getRequiredPermissions()) {
            if (!isPermissionGranted(this, permission)) {
                return false;
            }
        }
        return true;
    }

    private String[] getRequiredPermissions() {
        try {
            PackageInfo info =
                    this.getPackageManager()
                            .getPackageInfo(this.getPackageName(), PackageManager.GET_PERMISSIONS);
            String[] ps = info.requestedPermissions;
            if (ps != null && ps.length > 0) {
                return ps;
            } else {
                return new String[0];
            }
        } catch (Exception e) {
            return new String[0];
        }
    }

    private void getRuntimePermissions() {
        List<String> allNeededPermissions = new ArrayList<>();
        for (String permission : getRequiredPermissions()) {
            if (!isPermissionGranted(this, permission)) {
                allNeededPermissions.add(permission);
            }
        }

        if (!allNeededPermissions.isEmpty()) {
            ActivityCompat.requestPermissions(
                    this, allNeededPermissions.toArray(new String[0]), PERMISSION_REQUESTS);
        }
    }
}
