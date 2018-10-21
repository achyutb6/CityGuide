package com.twincoders.tourism;

import android.app.ProgressDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Point;
import android.graphics.Rect;
import android.net.Uri;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.annotation.NonNull;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.SwitchCompat;
import android.util.Log;
import android.view.View;
import android.widget.CompoundButton;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.Toast;

import com.ashokvarma.bottomnavigation.BottomNavigationBar;
import com.ashokvarma.bottomnavigation.BottomNavigationItem;
import com.elconfidencial.bubbleshowcase.BubbleShowCaseBuilder;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.FirebaseApp;
import com.google.firebase.ml.vision.FirebaseVision;
import com.google.firebase.ml.vision.cloud.FirebaseVisionCloudDetectorOptions;
import com.google.firebase.ml.vision.cloud.landmark.FirebaseVisionCloudLandmark;
import com.google.firebase.ml.vision.cloud.landmark.FirebaseVisionCloudLandmarkDetector;
import com.google.firebase.ml.vision.common.FirebaseVisionImage;
import com.google.firebase.ml.vision.common.FirebaseVisionLatLng;
import com.google.firebase.ml.vision.text.FirebaseVisionText;
import com.google.firebase.ml.vision.text.FirebaseVisionTextRecognizer;
import com.google.firebase.ml.vision.text.RecognizedLanguage;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.List;

import io.fotoapparat.Fotoapparat;
import io.fotoapparat.configuration.CameraConfiguration;
import io.fotoapparat.configuration.UpdateConfiguration;
import io.fotoapparat.error.CameraErrorListener;
import io.fotoapparat.exception.camera.CameraException;
import io.fotoapparat.parameter.ScaleType;
import io.fotoapparat.preview.Frame;
import io.fotoapparat.preview.FrameProcessor;
import io.fotoapparat.result.BitmapPhoto;
import io.fotoapparat.result.PhotoResult;
import io.fotoapparat.result.WhenDoneListener;
import io.fotoapparat.view.CameraView;
import io.fotoapparat.view.FocusView;

import static io.fotoapparat.log.LoggersKt.fileLogger;
import static io.fotoapparat.log.LoggersKt.logcat;
import static io.fotoapparat.log.LoggersKt.loggers;
import static io.fotoapparat.result.transformer.ResolutionTransformersKt.scaled;
import static io.fotoapparat.selector.AspectRatioSelectorsKt.standardRatio;
import static io.fotoapparat.selector.FlashSelectorsKt.autoFlash;
import static io.fotoapparat.selector.FlashSelectorsKt.autoRedEye;
import static io.fotoapparat.selector.FlashSelectorsKt.off;
import static io.fotoapparat.selector.FlashSelectorsKt.torch;
import static io.fotoapparat.selector.FocusModeSelectorsKt.autoFocus;
import static io.fotoapparat.selector.FocusModeSelectorsKt.continuousFocusPicture;
import static io.fotoapparat.selector.FocusModeSelectorsKt.fixed;
import static io.fotoapparat.selector.LensPositionSelectorsKt.back;
import static io.fotoapparat.selector.LensPositionSelectorsKt.front;
import static io.fotoapparat.selector.PreviewFpsRangeSelectorsKt.highestFps;
import static io.fotoapparat.selector.ResolutionSelectorsKt.highestResolution;
import static io.fotoapparat.selector.SelectorsKt.firstAvailable;
import static io.fotoapparat.selector.SensorSensitivitySelectorsKt.highestSensorSensitivity;

public class MainActivity extends AppCompatActivity {

    private static final String LOGGING_TAG = "Fotoapparat Example";

    private static final String API_KEY = "AIzaSyDGKkN82rmw7iiRcC_3vocOxGsFfrF0Kik";

    private final PermissionsDelegate permissionsDelegate = new PermissionsDelegate(this);
    private boolean hasCameraPermission;
    private CameraView cameraView;
    private FocusView focusView;
    private View capture;

    private Fotoapparat fotoapparat;

    boolean activeCameraBack = true;

    private CameraConfiguration cameraConfiguration = CameraConfiguration
            .builder()
            .photoResolution(standardRatio(
                    highestResolution()
            ))
            .focusMode(firstAvailable(
                    continuousFocusPicture(),
                    autoFocus(),
                    fixed()
            ))
            .flash(firstAvailable(
                    autoRedEye(),
                    autoFlash(),
                    torch(),
                    off()
            ))
            .previewFpsRange(highestFps())
            .sensorSensitivity(highestSensorSensitivity())
            .frameProcessor(new SampleFrameProcessor())
            .build();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
                //  Initialize SharedPreferences

        setContentView(R.layout.activity_main);
        SharedPreferences getPrefs = PreferenceManager
                .getDefaultSharedPreferences(getBaseContext());

        //  Create a new boolean and preference and set it to true
        boolean isFirstStart = getPrefs.getBoolean("firstStart", true);

        //  If the activity has never started before...
        if (isFirstStart) {

            //  Launch app intro
//            Intent i = new Intent(MainActivity.this, IntroActivity.class);
//            startActivity(i);
//            finish();

            //  Make a new preferences editor
            SharedPreferences.Editor e = getPrefs.edit();

            //  Edit preference to make it false because we don't want this to run again
            e.putBoolean("firstStart", false);

            //  Apply changes
            e.apply();
        }
        BottomNavigationBar bottomNavigationBar = (BottomNavigationBar) findViewById(R.id.bottom_navigation_bar);

        bottomNavigationBar
                .addItem(new BottomNavigationItem(R.drawable.ic_home_white_24dp, "Landmark detection").setActiveColorResource(R.color.colorAccent))
                .addItem(new BottomNavigationItem(R.drawable.ic_book_white_24dp, "Bus route").setActiveColorResource(R.color.colorAccent))
                .initialise();

        cameraView = findViewById(R.id.cameraView);
        focusView = findViewById(R.id.focusView);
        capture = findViewById(R.id.capture);
        hasCameraPermission = permissionsDelegate.hasCameraPermission();

        if (hasCameraPermission) {
            cameraView.setVisibility(View.VISIBLE);
        } else {
            permissionsDelegate.requestCameraPermission();
        }
        new BubbleShowCaseBuilder(this) //Activity instance
                .title("Select The feature you want to use") //Any title for the bubble view
                .targetView(bottomNavigationBar)
                .show(); //Display the ShowCase
        FirebaseApp.initializeApp(this);
        FirebaseVisionCloudDetectorOptions options =
                new FirebaseVisionCloudDetectorOptions.Builder()
                        .setModelType(FirebaseVisionCloudDetectorOptions.LATEST_MODEL)
                        .setMaxResults(15)
                        .build();
        fotoapparat = createFotoapparat();
        bottomNavigationBar.setTabSelectedListener(new BottomNavigationBar.OnTabSelectedListener(){
            @Override
            public void onTabSelected(int position) {
                takePictureOnClick(position);
            }
            @Override
            public void onTabUnselected(int position) {
            }
            @Override
            public void onTabReselected(int position) {
            }
        });

        switchCameraOnClick();
        toggleTorchOnSwitch();
    }

    private Fotoapparat createFotoapparat() {
        return Fotoapparat
                .with(this)
                .into(cameraView)
                .focusView(focusView)
                .previewScaleType(ScaleType.CenterCrop)
                .lensPosition(back())
                .frameProcessor(new SampleFrameProcessor())
                .logger(loggers(
                        logcat(),
                        fileLogger(this)
                ))
                .cameraErrorCallback(new CameraErrorListener() {
                    @Override
                    public void onError(@NotNull CameraException e) {
                        Toast.makeText(MainActivity.this, e.toString(), Toast.LENGTH_LONG).show();
                    }
                })
                .build();

    }



    private void switchCameraOnClick() {
        View switchCameraButton = findViewById(R.id.switchCamera);

        boolean hasFrontCamera = fotoapparat.isAvailable(front());

        switchCameraButton.setVisibility(
                hasFrontCamera ? View.VISIBLE : View.GONE
        );

        if (hasFrontCamera) {
            switchCameraOnClick(switchCameraButton);
        }
    }

    private void toggleTorchOnSwitch() {
        SwitchCompat torchSwitch = findViewById(R.id.torchSwitch);

        torchSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                fotoapparat.updateConfiguration(
                        UpdateConfiguration.builder()
                                .flash(
                                        isChecked ? torch() : off()
                                )
                                .build()
                );
            }
        });
    }

    private void switchCameraOnClick(View view) {
        view.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                activeCameraBack = !activeCameraBack;
                fotoapparat.switchTo(
                        activeCameraBack ? back() : front(),
                        cameraConfiguration
                );
            }
        });
    }

    private void takePictureOnClick(final int position) {
        capture.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                takePicture(position);
            }
        });
    }

    private void takePicture(final int position) {
        PhotoResult photoResult = fotoapparat.takePicture();

        photoResult.saveToFile(new File(
                getExternalFilesDir("photos"),
                "photo.jpg"
        ));
        final ProgressDialog progress = new ProgressDialog(this);
        progress.setCancelable(true);
        progress.show();
        progress.setMessage("Loading");
        photoResult
                .toBitmap(scaled(0.25f))
                .whenDone(new WhenDoneListener<BitmapPhoto>() {
                    @Override
                    public void whenDone(@Nullable BitmapPhoto bitmapPhoto) {
                        if (bitmapPhoto == null) {
                            Log.e(LOGGING_TAG, "Couldn't capture photo.");
                            return;
                        }
                        ImageView imageView = findViewById(R.id.result);


                        imageView.setImageBitmap(bitmapPhoto.bitmap);
                        imageView.setRotation(-bitmapPhoto.rotationDegrees);
                        FirebaseVisionImage image = FirebaseVisionImage.fromBitmap(bitmapPhoto.bitmap);
                        if(position == 0) {
                            FirebaseVisionCloudLandmarkDetector detector = FirebaseVision.getInstance()
                                    .getVisionCloudLandmarkDetector();

                            Task<List<FirebaseVisionCloudLandmark>> result = detector.detectInImage(image)
                                    .addOnSuccessListener(new OnSuccessListener<List<FirebaseVisionCloudLandmark>>() {
                                        @Override
                                        public void onSuccess(List<FirebaseVisionCloudLandmark> firebaseVisionCloudLandmarks) {
                                            Log.e("MainActivity", "Success" + firebaseVisionCloudLandmarks.size());
                                            for (FirebaseVisionCloudLandmark landmark : firebaseVisionCloudLandmarks) {
                                                Rect bounds = landmark.getBoundingBox();
                                                String landmarkName = landmark.getLandmark();
                                                String entityId = landmark.getEntityId();
                                                float confidence = landmark.getConfidence();


                                                Log.e("MainActivity", landmarkName + ":" + confidence + ":" + landmark.getLocations().size());
                                                progress.dismiss();
                                                Uri gmmIntentUri = Uri.parse("geo:0,0?q=" + landmarkName);
                                                Intent mapIntent = new Intent(Intent.ACTION_VIEW, gmmIntentUri);
                                                mapIntent.setPackage("com.google.android.apps.maps");
                                                startActivity(mapIntent);
                                                // Multiple locations are possible, e.g., the location of the depicted
                                                // landmark and the location the picture was taken.
                                                for (FirebaseVisionLatLng loc : landmark.getLocations()) {
                                                    double latitude = loc.getLatitude();
                                                    double longitude = loc.getLongitude();
                                                }
                                            }
                                        }
                                    })
                                    .addOnFailureListener(new OnFailureListener() {
                                        @Override
                                        public void onFailure(@NonNull Exception e) {
                                            Log.e("MainActivity", e.toString());
                                            progress.dismiss();
                                        }
                                    });
                        }else if(position == 1){
                        FirebaseVisionTextRecognizer textRecognizer = FirebaseVision.getInstance()
                                .getCloudTextRecognizer();

                        textRecognizer.processImage(image)
                                .addOnSuccessListener(new OnSuccessListener<FirebaseVisionText>() {
                                    @Override
                                    public void onSuccess(FirebaseVisionText result) {
                                        Log.e("MainActivity","Success");
                                        String resultText = result.getText();
                                        for (FirebaseVisionText.TextBlock block: result.getTextBlocks()) {
                                            String blockText = block.getText();
                                            Float blockConfidence = block.getConfidence();
                                            List<RecognizedLanguage> blockLanguages = block.getRecognizedLanguages();
                                            Point[] blockCornerPoints = block.getCornerPoints();
                                            Rect blockFrame = block.getBoundingBox();
                                            for (FirebaseVisionText.Line line: block.getLines()) {
                                                String lineText = line.getText();
                                                Log.e("MainActivity","Result:   "+lineText);
                                                if(lineText.contains("DART")){
                                                    for (FirebaseVisionText.Element element: line.getElements()) {
                                                        String elementText = element.getText();
                                                        if(elementText.matches("(?<!\\d)\\d{5}(?!\\d)")){
                                                            progress.dismiss();
                                                            String stopName = "Coit @ Frankford - N - FS";
                                                            if(elementText.contains("31576")){
                                                                stopName = "Coit @ Frankford - N - FS";
                                                            }else{
                                                                stopName = "Campbell @ University - W - FS";
                                                            }
                                                            Log.e("MainActivity","STOPID:   "+elementText);
                                                            Uri gmmIntentUri = Uri.parse("geo:0,0?q="+stopName);
                                                            Intent mapIntent = new Intent(Intent.ACTION_VIEW, gmmIntentUri);
                                                            mapIntent.setPackage("com.google.android.apps.maps");
                                                            startActivity(mapIntent);
                                                            break;
                                                        }
                                                        else
                                                            Log.e("MainActivity","ELSE");
                                                    }
                                                }
                                                Float lineConfidence = line.getConfidence();
                                                List<RecognizedLanguage> lineLanguages = line.getRecognizedLanguages();
                                                Point[] lineCornerPoints = line.getCornerPoints();
                                                Rect lineFrame = line.getBoundingBox();
                                            }
                                        }
                                    }
                                })
                                .addOnFailureListener(
                                        new OnFailureListener() {
                                            @Override
                                            public void onFailure(@NonNull Exception e) {
                                                Log.e("MainActivity",e.toString());
                                                progress.dismiss();
                                            }
                                        });
                        }
                    }
                });
    }



    @Override
    protected void onStart() {
        super.onStart();
        if (hasCameraPermission) {
            fotoapparat.start();
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (hasCameraPermission) {
            fotoapparat.stop();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (permissionsDelegate.resultGranted(requestCode, permissions, grantResults)) {
            hasCameraPermission = true;
            fotoapparat.start();
            cameraView.setVisibility(View.VISIBLE);
        }
    }

    private class SampleFrameProcessor implements FrameProcessor {
        @Override
        public void process(@NotNull Frame frame) {
            // Perform frame processing, if needed
        }
    }

}