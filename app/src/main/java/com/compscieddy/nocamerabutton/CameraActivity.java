package com.compscieddy.nocamerabutton;

import android.Manifest;
import android.annotation.TargetApi;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.hardware.Camera;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.CaptureRequest;
import android.media.AudioManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.speech.RecognitionListener;
import android.speech.SpeechRecognizer;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.ActionBarActivity;
import android.text.TextUtils;
import android.util.Log;
import android.view.Display;
import android.view.Menu;
import android.view.MenuItem;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;

import butterknife.Bind;
import butterknife.ButterKnife;


public class CameraActivity extends ActionBarActivity implements SurfaceHolder.Callback, ActivityCompat.OnRequestPermissionsResultCallback,
    View.OnClickListener {

  private static final Lawg lawg = Lawg.newInstance(CameraActivity.class.getSimpleName());

  // OG Camera API
  Camera mCamera;
  Camera.PictureCallback rawCallback;
  Camera.ShutterCallback shutterCallback;
  Camera.PictureCallback jpegCallback;

  // Camera 2 API
  CameraManager mCamera2Manager;
  private CameraDevice.StateCallback mCamera2StateCallback;
  private final static int CAMERA_2_API_LIMIT = Build.VERSION_CODES.LOLLIPOP;
  CameraDevice mCamera2Device;
  CaptureRequest.Builder mCamera2CaptureRequestBuilder; // for the camera preview
  Surface mCamera2Surface;
  CameraCaptureSession mCamera2CaptureSession;

  public static final int BASE_REQUEST_CODE = 100;
  public static final int CAMERA_PERMISSIONS_REQUEST = BASE_REQUEST_CODE + 1;
  public static final int RECORD_AUDIO_PERMISSIONS_REQUEST = BASE_REQUEST_CODE + 2;

  SurfaceHolder mSurfaceHolder;

  @Bind(R.id.start_camera_button) Button mStartCameraButton;
  @Bind(R.id.stop_camera_button) Button mStopCameraButton;
  @Bind(R.id.capture_camera_button) Button mCaptureCameraButton;
  @Bind(R.id.start_speech_button) Button mStartSpeechButton;
  @Bind(R.id.stop_speech_button) Button mStopSpeechButton;
  @Bind(R.id.display_text1) TextView mDisplayText1;
  @Bind(R.id.display_text2) TextView mDisplayText2;
  @Bind(R.id.surface_view) SurfaceView mSurfaceView;

  SpeechRecognizer mSpeechRecognizer;
  AudioManager mAudioManager;

  boolean mIsPreviewRunning;
  boolean mCamera2SurfaceCreated = false;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_camera);
    ButterKnife.bind(this);

    checkPermissions();
    setListeners();
    initCamera2();
    if (hasPermission(Manifest.permission.RECORD_AUDIO)) {
      startSpeechRecognizer();
    }

  }

  private void startSpeechRecognizer() {
    lawg.d("startSpeechRecognizer()");
    mSpeechRecognizer = SpeechRecognizer.createSpeechRecognizer(this);
    if (SpeechRecognizer.isRecognitionAvailable(this)
        && hasPermission(Manifest.permission.RECORD_AUDIO)) {
      lawg.d("Speech Recognition detected as being available");
      mSpeechRecognizer.setRecognitionListener(mRecognitionListener);
    } else {
      Toast.makeText(this, "Speech Recognition Not Available", Toast.LENGTH_SHORT).show();
    }
  }

  private boolean hasPermission(String permissionName) {
    int permissionCheck = ContextCompat.checkSelfPermission(CameraActivity.this, permissionName);
    return (permissionCheck == PackageManager.PERMISSION_GRANTED);
  }

  @Override
  public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
    boolean granted = grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED;
    switch (requestCode) {
      case CAMERA_PERMISSIONS_REQUEST:
        lawg.d("CAMERA permission response " + grantResults
            + ((grantResults.length > 0) ? grantResults[0] : ""));
        break;
      case RECORD_AUDIO_PERMISSIONS_REQUEST:
        lawg.d("RECORD_AUDIO permission response " + grantResults
            + ((grantResults.length > 0) ? grantResults[0] : ""));
        startSpeechRecognizer();
        break;
    }
  }

  @Override
  public void onClick(View v) {
    switch (v.getId()) {
      case R.id.start_speech_button:
        startListening();
        break;
      case R.id.stop_speech_button:
        mSpeechRecognizer.stopListening();
        break;
      case R.id.start_camera_button:
        if (Build.VERSION.SDK_INT >= CAMERA_2_API_LIMIT) {
          startCamera2();
        } else {
          startCamera();
        }
        break;
      case R.id.stop_camera_button:
        stopCamera();
        break;
      case R.id.capture_camera_button:
        captureImage();
        break;
    }
  }

  private void setListeners() {

    mStartSpeechButton.setOnClickListener(this);
    mStopSpeechButton.setOnClickListener(this);
    mStartCameraButton.setOnClickListener(this);
    mStopCameraButton.setOnClickListener(this);
    mCaptureCameraButton.setOnClickListener(this);

    mAudioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
  }

  private void checkPermissions() {
    lawg.d("checkPermissions()");
    if (!hasPermission(Manifest.permission.CAMERA)) {
      lawg.d("No camera permissions granted, requesting now -");
      ActivityCompat.requestPermissions(CameraActivity.this,
          new String[] { Manifest.permission.CAMERA },
          CAMERA_PERMISSIONS_REQUEST);
    } if (!hasPermission(Manifest.permission.RECORD_AUDIO)) {
      lawg.d("No record audio permissions granted, requesting now -");
      ActivityCompat.requestPermissions(CameraActivity.this,
          new String[] { Manifest.permission.RECORD_AUDIO },
          RECORD_AUDIO_PERMISSIONS_REQUEST);
    }
  }

  @TargetApi(CAMERA_2_API_LIMIT)
  private void initCamera2() {
    mSurfaceHolder = mSurfaceView.getHolder();
    mSurfaceHolder.addCallback(this);
    mSurfaceHolder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);
    rawCallback = new Camera.PictureCallback() {
      public void onPictureTaken(byte[] data, Camera camera) {
        Log.d("Log", "onPictureTaken - raw");
      }
    };
    /** Handles data for jpeg picture */
    shutterCallback = new Camera.ShutterCallback() {
      public void onShutter() {
        Log.i("Log", "onShutter'd");
      }
    };
    jpegCallback = new Camera.PictureCallback() {
      public void onPictureTaken(byte[] data, Camera camera) {
        FileOutputStream outStream = null;
        try {
          outStream = new FileOutputStream(String.format(
              "/sdcard/%d.jpg", System.currentTimeMillis()));
          outStream.write(data);
          outStream.close();
          Log.d("Log", "onPictureTaken - wrote bytes: " + data.length);
        } catch (FileNotFoundException e) {
          e.printStackTrace();
        } catch (IOException e) {
          e.printStackTrace();
        } finally {
        }
        Log.d("Log", "onPictureTaken - jpeg");
      }
    };

    if (Build.VERSION.SDK_INT >= CAMERA_2_API_LIMIT) {
      mCamera2StateCallback = new CameraDevice.StateCallback() {
        @Override
        public void onOpened(CameraDevice camera) {
          mCamera2Device = camera;
          lawg.d("CameraStateCallback PREVIEW STARTED");
          startCamera2Preview();
        }

        @Override
        public void onDisconnected(CameraDevice camera) {
          lawg.d("CameraStateCallback onDisconnected()");
        }

        @Override
        public void onError(CameraDevice camera, int error) {
          lawg.d("CameraStateCallback onError() errorCode: " + error);
        }
      };
    }

  }

  private void captureImage() {
    mCamera.takePicture(shutterCallback, rawCallback, jpegCallback);
  }

  private void startCamera() {
    try {
      mCamera = Camera.open(); // TODO: let's try out the Camera 2 API - this method doesn't seem to work with M devices cause I'm getting a "E/Camera: Error 2" in my logs
      mCamera.setErrorCallback(new Camera.ErrorCallback() {
        @Override
        public void onError(int error, Camera camera) {
          if (error == Camera.CAMERA_ERROR_UNKNOWN) {
            lawg.e("Camera Error: Unknown - what a great error huh...");
          } else if (error == Camera.CAMERA_ERROR_SERVER_DIED) {
            lawg.e("Camera Error: Server Died - am I supposed to revive it at this point?");
          } else {
            lawg.e("Some unknown Camera error occurred"); // should never be seen but being cautious
          }
        }
      });
    } catch (RuntimeException e) {
      lawg.e("init_camera: " + e);
      return;
    }
    Camera.Parameters param;
    param = mCamera.getParameters();
    //modify parameter
    param.setPreviewFrameRate(20);
//    param.setPreviewSize(264, 216);
    param.setPreviewSize(176, 144);
    mCamera.setParameters(param);
    try {
      mCamera.setPreviewDisplay(mSurfaceHolder);
      mCamera.startPreview();
      //mCamera.takePicture(shutter, raw, jpeg)
    } catch (Exception e) {
      lawg.e("init_camera: " + e);
      return;
    }
  }

  @TargetApi(CAMERA_2_API_LIMIT)
  private void startCamera2() {
    lawg.d("startCamera2()");

    mCamera2Manager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
    try {
      String cameraId = mCamera2Manager.getCameraIdList()[0]; // first one should be the back camera
      CameraCharacteristics characteristics = mCamera2Manager.getCameraCharacteristics(cameraId);
      try {
//        if (mCameraPermissionGranted)
        mCamera2Manager.openCamera(cameraId, mCamera2StateCallback, null);
      } catch (SecurityException e) {
        lawg.e("Error in startCamera2 " + e);
        e.printStackTrace();
      }
    } catch (CameraAccessException e) {
      e.printStackTrace();
    }

  }

  @TargetApi(CAMERA_2_API_LIMIT)
  private void updateCamera2Preview() {
    mCamera2CaptureRequestBuilder.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO);
    HandlerThread thread = new HandlerThread("CameraPreview");
    thread.start();
    Handler backgroundHandler = new Handler(thread.getLooper());

    try {
      mCamera2CaptureSession.setRepeatingRequest(mCamera2CaptureRequestBuilder.build(), null, backgroundHandler);
    } catch (CameraAccessException e) {
      e.printStackTrace();
    }
  }

  private void stopCamera() {
    mCamera.stopPreview();
    mCamera.release();
  }

  public void previewCamera() {
    try {
      mCamera.setPreviewDisplay(mSurfaceHolder);
      mCamera.startPreview();
      mIsPreviewRunning = true;
    } catch (Exception e) {
      lawg.e("Cannot start preview");
    }
  }

  public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
    if (!(Build.VERSION.SDK_INT >= CAMERA_2_API_LIMIT)) {
      if (mIsPreviewRunning) {
        mCamera.stopPreview();
      }

      Display display = ((WindowManager) getSystemService(WINDOW_SERVICE)).getDefaultDisplay();

      if (display.getRotation() == Surface.ROTATION_0) {
        mCamera.setDisplayOrientation(90);
      }

      if (display.getRotation() == Surface.ROTATION_90) {
      }

      if (display.getRotation() == Surface.ROTATION_180) {
      }

      if (display.getRotation() == Surface.ROTATION_270) {
        mCamera.setDisplayOrientation(180);
      }

      if (Build.VERSION.SDK_INT >= CAMERA_2_API_LIMIT) {
        lawg.d("surfaceChanged()");
        startCamera2();
      } else {
        startCamera();
      }
    }
  }

  public void surfaceCreated(SurfaceHolder holder) {
    mCamera2Surface = holder.getSurface();
    mCamera2SurfaceCreated = true;
    lawg.d("surfaceCreated() holder: " + holder + " surface: " + mCamera2Surface);
    if (Build.VERSION.SDK_INT >= CAMERA_2_API_LIMIT) {
      lawg.d("surfaceCreated()");
      startCamera2();
    } else {
      startCamera();
    }
  }

  @TargetApi(CAMERA_2_API_LIMIT)
  private void startCamera2Preview() {
    try {
      mCamera2CaptureRequestBuilder = mCamera2Device.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
    } catch (CameraAccessException e) {
      e.printStackTrace();
    }

    mCamera2CaptureRequestBuilder.addTarget(mCamera2Surface);

    try {
      mCamera2Device.createCaptureSession(Arrays.asList(mCamera2Surface), new CameraCaptureSession.StateCallback() {
        @Override
        public void onConfigured(CameraCaptureSession session) {
          mCamera2CaptureSession = session;
          updateCamera2Preview();
        }

        @Override
        public void onConfigureFailed(CameraCaptureSession session) {
          Toast.makeText(CameraActivity.this, "onConfigureFailed", Toast.LENGTH_SHORT).show();
        }
      }, null);
    } catch (CameraAccessException e) {
      e.printStackTrace();
    }
  }

  public void surfaceDestroyed(SurfaceHolder holder) {
    // TODO Auto-generated method stub
  }

  @Override
  public boolean onCreateOptionsMenu(Menu menu) {
    // Inflate the menu; this adds items to the action bar if it is present.
    getMenuInflater().inflate(R.menu.menu_home, menu);
    return true;
  }

  @Override
  public boolean onOptionsItemSelected(MenuItem item) {
    // Handle action bar item clicks here. The action bar will
    // automatically handle clicks on the Home/Up button, so long
    // as you specify a parent activity in AndroidManifest.xml.
    int id = item.getItemId();

    //noinspection SimplifiableIfStatement
    if (id == R.id.action_settings) {
      return true;
    }

    return super.onOptionsItemSelected(item);
  }

  RecognitionListener mRecognitionListener = new RecognitionListener() {
    @Override
    public void onReadyForSpeech(Bundle params) {
      lawg.d("[RecognitionListener] onReadyForSpeech()");
    }

    @Override
    public void onBeginningOfSpeech() {
      lawg.d("[RecognitionListener] onBeginningOfSpeech()");
    }

    @Override
    public void onRmsChanged(float rmsdB) {
      mDisplayText2.setText("" + rmsdB);
    }

    @Override
    public void onBufferReceived(byte[] buffer) {

    }

    @Override
    public void onEndOfSpeech() {
      lawg.d("[RecognitionListener] onEndOfSpeech()");
      startListening();
    }

    @Override
    public void onError(int error) {
      lawg.d("[RecognitionListener] onError() errorCode: " + error);
      Toast.makeText(CameraActivity.this, "Speech Recognition Error", Toast.LENGTH_SHORT).show();
      mSpeechRecognizer.cancel();
    }

    @Override
    public void onResults(Bundle results) {
      lawg.d("[RecognitionListener] onResults()");
      ArrayList<String> resultsArray = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
      StringBuilder sb = new StringBuilder();
      for (int i = 0; i < resultsArray.size(); i++) {
        String word = resultsArray.get(i);
        sb.append(word);
        if (i != resultsArray.size() - 1) sb.append(", ");
        if (TextUtils.equals(word.toLowerCase(), "cheese")) captureImage();
      }
      lawg.d("Words detected: \n" + sb.toString());
      mDisplayText1.setText(sb.toString());

      // repeat listen
      startListening();
    }

    @Override
    public void onPartialResults(Bundle partialResults) {

    }

    @Override
    public void onEvent(int eventType, Bundle params) {

    }
  };

  public void startListening() {
//    mAudioManager.setStreamMute(AudioManager.STREAM_SYSTEM, true);
//    mAudioManager.setStreamMute(AudioManager.STREAM_NOTIFICATION, true);
//    mAudioManager.setStreamMute(AudioManager.STREAM_ALARM, true);
//    mAudioManager.setStreamMute(AudioManager.STREAM_MUSIC, true);
//    mAudioManager.setStreamMute(AudioManager.STREAM_RING, true);
//    mAudioManager.setStreamMute(AudioManager.STREAM_SYSTEM, true);

    mSpeechRecognizer.startListening(new Intent());
  }

}
