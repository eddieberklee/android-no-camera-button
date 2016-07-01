package com.compscieddy.shoutake;

import android.Manifest;
import android.annotation.TargetApi;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.graphics.Point;
import android.graphics.RectF;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.CaptureResult;
import android.hardware.camera2.TotalCaptureResult;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.AudioManager;
import android.media.Image;
import android.media.ImageReader;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.provider.MediaStore;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v4.view.animation.FastOutSlowInInterpolator;
import android.support.v7.app.ActionBarActivity;
import android.text.TextUtils;
import android.util.Size;
import android.util.SparseIntArray;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.compscieddy.eddie_utils.Etils;
import com.facebook.rebound.SimpleSpringListener;
import com.facebook.rebound.Spring;
import com.facebook.rebound.SpringConfig;
import com.facebook.rebound.SpringSystem;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

import butterknife.Bind;
import butterknife.ButterKnife;

/**
 * A lot of this code to setup Camera2 is from https://github.com/googlesamples/android-Camera2Basic
 */
public class CameraActivity extends ActionBarActivity implements ActivityCompat.OnRequestPermissionsResultCallback,
    View.OnClickListener {

  private static final Lawg lawg = Lawg.newInstance(CameraActivity.class.getSimpleName());

  // Camera 2 API
  CameraManager mCamera2Manager;
  private CameraDevice.StateCallback mCamera2StateCallback;
  private final static int CAMERA_2_API_LIMIT = Build.VERSION_CODES.LOLLIPOP;
  CameraDevice mCamera2Device;
  CaptureRequest.Builder mCamera2CaptureRequestBuilder; // for the camera preview
  CameraCaptureSession mCamera2CaptureSession;

  public static final int BASE_REQUEST_CODE = 100;
  public static final int CAMERA_PERMISSIONS_REQUEST = BASE_REQUEST_CODE + 1;
  public static final int RECORD_AUDIO_PERMISSIONS_REQUEST = BASE_REQUEST_CODE + 2;
  public static final int WRITE_EXTERNAL_STORAGE_PERMISSIONS_REQUEST = BASE_REQUEST_CODE + 3;

  @Bind(R.id.start_camera_button) View mStartCameraButton;
  @Bind(R.id.stop_camera_button) View mStopCameraButton;
  @Bind(R.id.capture_camera_button) View mCaptureCameraButton;
  @Bind(R.id.start_speech_button) View mStartSpeechButton;
  @Bind(R.id.stop_speech_button) View mStopSpeechButton;
  @Bind(R.id.display_text1) TextView mDisplayText1;
  @Bind(R.id.display_text2) TextView mDisplayText2;
  @Bind(R.id.texture) AutoFitTextureView mTextureView;
  @Bind(R.id.flash_capture_animation) View mFlashCaptureAnimationView;
  @Bind(R.id.preview_capture) ImageView mPreviewCapture;

  private static final FastOutSlowInInterpolator FAST_OUT_SLOW_IN_INTERPOLATOR = new FastOutSlowInInterpolator();
  SpeechRecognizer mSpeechRecognizer;
  AudioManager mAudioManager;
  private CaptureRequest mCamera2CaptureRequest;
  private CameraCaptureSession.CaptureCallback mCapture2Callback;
  private static final SparseIntArray ORIENTATIONS = new SparseIntArray();

  static {
    ORIENTATIONS.append(Surface.ROTATION_0, 90);
    ORIENTATIONS.append(Surface.ROTATION_90, 0);
    ORIENTATIONS.append(Surface.ROTATION_180, 270);
    ORIENTATIONS.append(Surface.ROTATION_270, 180);
  }

  private boolean mIsFrontCamera = true;

  /**
   * An additional thread for running tasks that shouldn't block the UI.
   */
  private HandlerThread mBackgroundThread;

  /**
   * Camera state: Showing camera preview.
   */
  private static final int STATE_PREVIEW = 0;

  /**
   * Camera state: Waiting for the focus to be locked.
   */
  private static final int STATE_WAITING_LOCK = 1;

  /**
   * Camera state: Waiting for the exposure to be precapture state.
   */
  private static final int STATE_WAITING_PRECAPTURE = 2;

  /**
   * Camera state: Waiting for the exposure state to be something other than precapture.
   */
  private static final int STATE_WAITING_NON_PRECAPTURE = 3;

  /**
   * Camera state: Picture was taken.
   */
  private static final int STATE_PICTURE_TAKEN = 4;

  private static final String FRAGMENT_DIALOG = "dialog";
  private int mState = STATE_PREVIEW;

  private Size mPreviewSize;

  /**
   * This is the output file for our picture.
   */
  private File mFile;
  /**
   * ID of the current {@link CameraDevice}.
   */
  private String mCameraId;
  /**
   * Whether the current camera device supports Flash or not.
   */
  private boolean mFlashSupported;
  /**
   * Max preview width that is guaranteed by Camera2 API
   */
  private static final int MAX_PREVIEW_WIDTH = 1920;
  /**
   * Max preview height that is guaranteed by Camera2 API
   */
  private static final int MAX_PREVIEW_HEIGHT = 1080;

  private Handler mBackgroundHandler;
  /**
   * Orientation of the camera sensor
   */
  private int mSensorOrientation;
  /**
   * An {@link ImageReader} that handles still image capture.
   */
  private ImageReader mImageReader;

  /**
   * A {@link Semaphore} to prevent the app from exiting before closing the camera.
   */
  private Semaphore mCameraOpenCloseLock = new Semaphore(1);

  /**
   * This a callback object for the {@link ImageReader}. "onImageAvailable" will be called when a
   * still image is ready to be saved.
   */
  private ImageReader.OnImageAvailableListener mOnImageAvailableListener;

  private ViewGroup mRootView;
  private LayoutInflater mLayoutInflater;
  private Handler mHandler;
  private SpringSystem mSpringSystem;
  private Spring mFlashCaptureSpring;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    mHandler = new Handler(Looper.getMainLooper());
    mLayoutInflater = (LayoutInflater) getSystemService(LAYOUT_INFLATER_SERVICE);
    mRootView = (ViewGroup) mLayoutInflater.inflate(R.layout.activity_camera, null);
    setContentView(mRootView);
    ButterKnife.bind(this);

    if (Build.VERSION.SDK_INT >= CAMERA_2_API_LIMIT) {
      initOnImageAvailableListener();
      initCaptureCallback();
    }

    checkPermissions();
    setListeners();
    initCamera2();
    initReboundSpring();

  }

  int[] captureColors = new int[] {
      R.color.flatui_red_1,
      R.color.flatui_orange_1,
      R.color.flatui_yellow_2,
      R.color.flatui_yellow_1,
      R.color.flatui_green_1,
      R.color.flatui_teal_1,
      R.color.flatui_teal_2,
      R.color.flatui_blue_1,
  };

  private void initReboundSpring() {
    mSpringSystem = SpringSystem.create();
    mFlashCaptureSpring = mSpringSystem.createSpring();
    mFlashCaptureSpring.setSpringConfig(new SpringConfig(40, 10));
    mFlashCaptureSpring.setOvershootClampingEnabled(false);
    mFlashCaptureSpring.addListener(new SimpleSpringListener() {
      @Override
      public void onSpringUpdate(Spring spring) {
        float value = (float) spring.getCurrentValue();
        spring.getCurrentDisplacementDistance();
        spring.getEndValue();
        float springProgress = (float) ((1 - spring.getCurrentDisplacementDistance()) / spring.getEndValue());
        float colorValue = value * (captureColors.length - 1);
        float alphaValue = Etils.mapValue(value, 0, 1f, 0, 0.7f);
        int colorIndex = (int) colorValue;
        lawg.d("[" + colorIndex + "] value: " + value + " springProgress: " + springProgress + " colorValue: " + colorValue);
        if (colorIndex > captureColors.length - 2) {
          colorIndex = captureColors.length - 2;
        }
        float colorProgress = colorValue - colorIndex;
        if (false) lawg.d("colorProgress: " + colorProgress + " colorIndex: " + colorIndex);
        int intermediateColor = Etils.getIntermediateColor(
            getResources().getColor(captureColors[colorIndex]),
            getResources().getColor(captureColors[colorIndex + 1]),
            colorProgress);
        mFlashCaptureAnimationView.setBackgroundColor(intermediateColor);
        mFlashCaptureAnimationView.setAlpha(alphaValue);
      }
      @Override
      public void onSpringAtRest(Spring spring) {
        if (false) lawg.d("onSpringAtRest");
        mFlashCaptureAnimationView.setAlpha(0);
        mFlashCaptureAnimationView.setVisibility(View.GONE);
      }
      @Override
      public void onSpringActivate(Spring spring) {
        if (false) lawg.d("onSpringActivate");
      }
      @Override
      public void onSpringEndStateChange(Spring spring) {
        if (false) lawg.d("onSpringEndStateChange");
      }
    });
  }

  @TargetApi(CAMERA_2_API_LIMIT)
  private void initOnImageAvailableListener() {
    mOnImageAvailableListener = new ImageReader.OnImageAvailableListener() {
      @Override
      public void onImageAvailable(ImageReader reader) {
        lawg.d("onImageAvailable()");
        mBackgroundHandler.post(new ImageSaver(CameraActivity.this, reader.acquireNextImage(), mFile));
      }
    };
  }

  @TargetApi(CAMERA_2_API_LIMIT)
  private void initCaptureCallback() {
    lawg.d("initCaptureCallback()");
    mCapture2Callback = new CameraCaptureSession.CaptureCallback() {
      private void process(CaptureResult result) {
        switch (mState) {
          case STATE_PREVIEW: {
            // We have nothing to do when the camera preview is working normally.
            break;
          }
          case STATE_WAITING_LOCK: {
            Integer afState = result.get(CaptureResult.CONTROL_AF_STATE);
            if (afState == null) {
              captureStillPicture();
            } else if (CaptureResult.CONTROL_AF_STATE_FOCUSED_LOCKED == afState ||
                CaptureResult.CONTROL_AF_STATE_NOT_FOCUSED_LOCKED == afState) {
              // CONTROL_AE_STATE can be null on some devices
              Integer aeState = result.get(CaptureResult.CONTROL_AE_STATE);
              if (aeState == null ||
                  aeState == CaptureResult.CONTROL_AE_STATE_CONVERGED) {
                mState = STATE_PICTURE_TAKEN;
                captureStillPicture();
              } else {
                runPrecaptureSequence();
              }
            }
            break;
          }
          case STATE_WAITING_PRECAPTURE: {
            // CONTROL_AE_STATE can be null on some devices
            Integer aeState = result.get(CaptureResult.CONTROL_AE_STATE);
            if (aeState == null ||
                aeState == CaptureResult.CONTROL_AE_STATE_PRECAPTURE ||
                aeState == CaptureRequest.CONTROL_AE_STATE_FLASH_REQUIRED) {
              mState = STATE_WAITING_NON_PRECAPTURE;
            }
            break;
          }
          case STATE_WAITING_NON_PRECAPTURE: {
            // CONTROL_AE_STATE can be null on some devices
            Integer aeState = result.get(CaptureResult.CONTROL_AE_STATE);
            if (aeState == null || aeState != CaptureResult.CONTROL_AE_STATE_PRECAPTURE) {
              mState = STATE_PICTURE_TAKEN;
              captureStillPicture();
            }
            break;
          }
        }
      }

      @Override
      public void onCaptureProgressed(@NonNull CameraCaptureSession session,
                                      @NonNull CaptureRequest request,
                                      @NonNull CaptureResult partialResult) {
        process(partialResult);
      }

      @Override
      public void onCaptureCompleted(@NonNull CameraCaptureSession session,
                                     @NonNull CaptureRequest request,
                                     @NonNull TotalCaptureResult result) {
        process(result);
      }

    };
  }

  private void startSpeechRecognizer() {
    lawg.d("startSpeechRecognizer()");
    if (SpeechRecognizer.isRecognitionAvailable(this)
        && hasPermission(Manifest.permission.RECORD_AUDIO)) {
      lawg.d("Speech Recognition detected as being available");
      startListening();
    } else {
      Toast.makeText(this, "Speech Recognition Not Available", Toast.LENGTH_SHORT).show();
    }
  }

  private void stopSpeechRecognizer() {
    mAudioManager.setStreamMute(AudioManager.STREAM_RING, false);
    if (mSpeechRecognizer != null) {
      mSpeechRecognizer.destroy();
    }
    mSpeechRecognizer.stopListening();
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
          lawg.d("Has camera permission? " + hasPermission(Manifest.permission.CAMERA));
          if (hasPermission(Manifest.permission.CAMERA)) {
            startCamera2(mTextureView.getWidth(), mTextureView.getHeight());
          } else {
            ActivityCompat.requestPermissions(
                CameraActivity.this,
                new String[]{Manifest.permission.CAMERA},
                CAMERA_PERMISSIONS_REQUEST);
          }
        }
        break;
      case R.id.stop_camera_button:
        stopCamera2();
        break;
      case R.id.capture_camera_button:
        captureStillPicture();
        break;
    }
  }

  /**
   * {@link TextureView.SurfaceTextureListener} handles several lifecycle events on a
   * {@link TextureView}.
   */
  private final TextureView.SurfaceTextureListener mSurfaceTextureListener
      = new TextureView.SurfaceTextureListener() {

    @Override
    public void onSurfaceTextureAvailable(SurfaceTexture texture, int width, int height) {
      lawg.d("Has camera permission? " + hasPermission(Manifest.permission.CAMERA));
      if (hasPermission(Manifest.permission.CAMERA)) {
        startCamera2(width, height);
      } else {
        ActivityCompat.requestPermissions(
            CameraActivity.this,
            new String[]{Manifest.permission.CAMERA},
            CAMERA_PERMISSIONS_REQUEST);
      }
    }

    @Override
    public void onSurfaceTextureSizeChanged(SurfaceTexture texture, int width, int height) {
      configureTransform(width, height);
    }

    @Override
    public boolean onSurfaceTextureDestroyed(SurfaceTexture texture) {
      return true;
    }

    @Override
    public void onSurfaceTextureUpdated(SurfaceTexture texture) {
    }

  };

  @Override
  protected void onResume() {
    super.onResume();
    startBackgroundThread();
    startSpeechRecognizer();

    // When the screen is turned off and turned back on, the SurfaceTexture is already
    // available, and "onSurfaceTextureAvailable" will not be called. In that case, we can open
    // a camera and start preview from here (otherwise, we wait until the surface is ready in
    // the SurfaceTextureListener).
    if (mTextureView.isAvailable()) {
      lawg.d("Has camera permission? " + hasPermission(Manifest.permission.CAMERA));
      if (hasPermission(Manifest.permission.CAMERA)) {
        startCamera2(mTextureView.getWidth(), mTextureView.getHeight());
      } else {
        ActivityCompat.requestPermissions(
            CameraActivity.this,
            new String[] { Manifest.permission.CAMERA },
            CAMERA_PERMISSIONS_REQUEST);
      }
    } else {
      mTextureView.setSurfaceTextureListener(mSurfaceTextureListener);
    }
  }

  /**
   * Starts a background thread and its {@link Handler}.
   */
  private void startBackgroundThread() {
    mBackgroundThread = new HandlerThread("CameraBackground");
    mBackgroundThread.start();
    mBackgroundHandler = new Handler(mBackgroundThread.getLooper());
  }

  /**
   * Stops the background thread and its {@link Handler}.
   */
  @TargetApi(CAMERA_2_API_LIMIT)
  private void stopBackgroundThread() {
    mBackgroundThread.quitSafely();
    try {
      mBackgroundThread.join();
      mBackgroundThread = null;
      mBackgroundHandler = null;
    } catch (InterruptedException e) {
      e.printStackTrace();
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
      ActivityCompat.requestPermissions(
          CameraActivity.this,
          new String[] { Manifest.permission.CAMERA },
          CAMERA_PERMISSIONS_REQUEST);
    }
    if (!hasPermission(Manifest.permission.RECORD_AUDIO)) {
      lawg.d("No record audio permissions granted, requesting now -");
      ActivityCompat.requestPermissions(
          CameraActivity.this,
          new String[] { Manifest.permission.RECORD_AUDIO },
          RECORD_AUDIO_PERMISSIONS_REQUEST);
    }
    if (!hasPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
      lawg.d("No write external files permissions granted, requesting now -");
      ActivityCompat.requestPermissions(
          CameraActivity.this,
          new String[] { Manifest.permission.WRITE_EXTERNAL_STORAGE },
          WRITE_EXTERNAL_STORAGE_PERMISSIONS_REQUEST);
    }
  }

  @TargetApi(CAMERA_2_API_LIMIT)
  static class CompareSizesByArea implements Comparator<Size> {
    @Override
    public int compare(Size lhs, Size rhs) {
      // We cast here to ensure the multiplications won't overflow
      return Long.signum((long) lhs.getWidth() * lhs.getHeight() -
          (long) rhs.getWidth() * rhs.getHeight());
    }
  }

  @TargetApi(CAMERA_2_API_LIMIT)
  private static Size chooseOptimalSize(Size[] choices, int textureViewWidth,
                                        int textureViewHeight, int maxWidth, int maxHeight, Size aspectRatio) {

    // Collect the supported resolutions that are at least as big as the preview Surface
    List<Size> bigEnough = new ArrayList<>();
    // Collect the supported resolutions that are smaller than the preview Surface
    List<Size> notBigEnough = new ArrayList<>();
    int w = aspectRatio.getWidth();
    int h = aspectRatio.getHeight();
    for (Size option : choices) {
      if (option.getWidth() <= maxWidth && option.getHeight() <= maxHeight &&
          option.getHeight() == option.getWidth() * h / w) {
        if (option.getWidth() >= textureViewWidth &&
            option.getHeight() >= textureViewHeight) {
          bigEnough.add(option);
        } else {
          notBigEnough.add(option);
        }
      }
    }

    // Pick the smallest of those big enough. If there is no one big enough, pick the
    // largest of those not big enough.
    if (bigEnough.size() > 0) {
      return Collections.min(bigEnough, new CompareSizesByArea());
    } else if (notBigEnough.size() > 0) {
      return Collections.max(notBigEnough, new CompareSizesByArea());
    } else {
      lawg.e("Couldn't find any suitable preview size");
      return choices[0];
    }
  }

  @TargetApi(CAMERA_2_API_LIMIT)
  private void setUpCameraOutputs(int width, int height) {
    Activity activity = this;
    CameraManager manager = (CameraManager) activity.getSystemService(Context.CAMERA_SERVICE);
    try {
      for (String cameraId : manager.getCameraIdList()) {
        CameraCharacteristics characteristics
            = manager.getCameraCharacteristics(cameraId);

        // We don't use a front facing camera in this sample.
        Integer facing = characteristics.get(CameraCharacteristics.LENS_FACING);
        if (mIsFrontCamera) {
          if (facing != null && facing == CameraCharacteristics.LENS_FACING_BACK) {
            continue;
          }
        } else {
          if (facing != null && facing == CameraCharacteristics.LENS_FACING_FRONT) {
            continue;
          }
        }

        StreamConfigurationMap map = characteristics.get(
            CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
        if (map == null) {
          continue;
        }

        // For still image captures, we use the largest available size.
        Size largest = Collections.max(
            Arrays.asList(map.getOutputSizes(ImageFormat.JPEG)),
            new CompareSizesByArea());
        mImageReader = ImageReader.newInstance(largest.getWidth(), largest.getHeight(),
            ImageFormat.JPEG, /*maxImages*/2);
        mImageReader.setOnImageAvailableListener(
            mOnImageAvailableListener, mBackgroundHandler);

        // Find out if we need to swap dimension to get the preview size relative to sensor
        // coordinate.
        int displayRotation = activity.getWindowManager().getDefaultDisplay().getRotation();
        //noinspection ConstantConditions
        mSensorOrientation = characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION);
        boolean swappedDimensions = false;
        switch (displayRotation) {
          case Surface.ROTATION_0:
          case Surface.ROTATION_180:
            if (mSensorOrientation == 90 || mSensorOrientation == 270) {
              swappedDimensions = true;
            }
            break;
          case Surface.ROTATION_90:
          case Surface.ROTATION_270:
            if (mSensorOrientation == 0 || mSensorOrientation == 180) {
              swappedDimensions = true;
            }
            break;
          default:
            lawg.e("Display rotation is invalid: " + displayRotation);
        }

        Point displaySize = new Point();
        activity.getWindowManager().getDefaultDisplay().getSize(displaySize);
        int rotatedPreviewWidth = width;
        int rotatedPreviewHeight = height;
        int maxPreviewWidth = displaySize.x;
        int maxPreviewHeight = displaySize.y;

        if (swappedDimensions) {
          rotatedPreviewWidth = height;
          rotatedPreviewHeight = width;
          maxPreviewWidth = displaySize.y;
          maxPreviewHeight = displaySize.x;
        }

        if (maxPreviewWidth > MAX_PREVIEW_WIDTH) {
          maxPreviewWidth = MAX_PREVIEW_WIDTH;
        }

        if (maxPreviewHeight > MAX_PREVIEW_HEIGHT) {
          maxPreviewHeight = MAX_PREVIEW_HEIGHT;
        }

        // Danger, W.R.! Attempting to use too large a preview size could  exceed the camera
        // bus' bandwidth limitation, resulting in gorgeous previews but the storage of
        // garbage capture data.
        mPreviewSize = chooseOptimalSize(map.getOutputSizes(SurfaceTexture.class),
            rotatedPreviewWidth, rotatedPreviewHeight, maxPreviewWidth,
            maxPreviewHeight, largest);

        // We fit the aspect ratio of TextureView to the size of preview we picked.
        int orientation = getResources().getConfiguration().orientation;
        if (orientation == Configuration.ORIENTATION_LANDSCAPE) {
          mTextureView.setAspectRatio(
              mPreviewSize.getWidth(), mPreviewSize.getHeight());
        } else {
          mTextureView.setAspectRatio(
              mPreviewSize.getHeight(), mPreviewSize.getWidth());
        }

        // Check if the flash is supported.
        Boolean available = characteristics.get(CameraCharacteristics.FLASH_INFO_AVAILABLE);
        mFlashSupported = available == null ? false : available;

        mCameraId = cameraId;
        return;
      }
    } catch (CameraAccessException e) {
      e.printStackTrace();
    } catch (NullPointerException e) {
      // Currently an NPE is thrown when the Camera2API is used but not supported on the
      // device this code runs.
      ErrorDialog.newInstance(getString(R.string.camera_error))
          .show(getFragmentManager(), FRAGMENT_DIALOG);
    }
  }

  @TargetApi(CAMERA_2_API_LIMIT)
  private void setAutoFlash(CaptureRequest.Builder requestBuilder) {
    if (mFlashSupported) {
      requestBuilder.set(CaptureRequest.CONTROL_AE_MODE,
          CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH);
    }
  }

  @TargetApi(CAMERA_2_API_LIMIT)
  private void initCamera2() {
    mCamera2StateCallback = new CameraDevice.StateCallback() {
      @Override
      public void onOpened(CameraDevice camera) {
        lawg.d("CameraStateCallback PREVIEW STARTED");
        mCameraOpenCloseLock.release();
        mCamera2Device = camera;
        createCameraPreviewSession();
      }

      @Override
      public void onDisconnected(CameraDevice camera) {
        lawg.d("CameraStateCallback onDisconnected()");
        mCameraOpenCloseLock.release();
        camera.close();
        mCamera2Device = null;
      }

      @Override
      public void onError(CameraDevice camera, int error) {
        lawg.d("CameraStateCallback onError() errorCode: " + error);
        mCameraOpenCloseLock.release();
        camera.close();
        mCamera2Device = null;
        Activity activity = CameraActivity.this;
        if (null != activity) {
          activity.finish();
        }
      }
    };

  }

  /**
   * Creates a new {@link CameraCaptureSession} for camera preview.
   */
  @TargetApi(CAMERA_2_API_LIMIT)
  private void createCameraPreviewSession() {
    try {
      SurfaceTexture texture = mTextureView.getSurfaceTexture();
      assert texture != null;

      // We configure the size of default buffer to be the size of camera preview we want.
      texture.setDefaultBufferSize(mPreviewSize.getWidth(), mPreviewSize.getHeight());

      // This is the output Surface we need to start preview.
      Surface surface = new Surface(texture);

      // We set up a CaptureRequest.Builder with the output Surface.
      mCamera2CaptureRequestBuilder = mCamera2Device.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
      mCamera2CaptureRequestBuilder.addTarget(surface);

      // Here, we create a CameraCaptureSession for camera preview.
      mCamera2Device.createCaptureSession(Arrays.asList(surface, mImageReader.getSurface()),
          new CameraCaptureSession.StateCallback() {

            @Override
            public void onConfigured(@NonNull CameraCaptureSession cameraCaptureSession) {
              // The camera is already closed
              if (null == mCamera2Device) {
                return;
              }

              // When the session is ready, we start displaying the preview.
              mCamera2CaptureSession = cameraCaptureSession;
              try {
                // Auto focus should be continuous for camera preview.
                mCamera2CaptureRequestBuilder.set(CaptureRequest.CONTROL_AF_MODE,
                    CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
                // Flash is automatically enabled when necessary.
                setAutoFlash(mCamera2CaptureRequestBuilder);

                // Finally, we start displaying the camera preview.
                mCamera2CaptureRequest = mCamera2CaptureRequestBuilder.build();
                mCamera2CaptureSession.setRepeatingRequest(mCamera2CaptureRequest,
                    mCapture2Callback, mBackgroundHandler);
              } catch (CameraAccessException e) {
                e.printStackTrace();
              }
            }

            @Override
            public void onConfigureFailed(
                @NonNull CameraCaptureSession cameraCaptureSession) {
              Etils.showToast(CameraActivity.this, "Failed");
            }
          }, null
      );
    } catch (CameraAccessException e) {
      e.printStackTrace();
    }
  }

  /**
   * Capture a still picture. This method should be called when we get a response in
   */
  @TargetApi(CAMERA_2_API_LIMIT)
  private void captureStillPicture() {
    try {
      final Activity activity = CameraActivity.this;
      if (activity == null || mCamera2Device == null) {
        return;
      }

      mFlashCaptureAnimationView.setVisibility(View.VISIBLE);
      mFlashCaptureSpring.setCurrentValue(0);
      mFlashCaptureSpring.setEndValue(1.0f);

      mTextureView.setDrawingCacheEnabled(true);
      Bitmap cameraPreview = mTextureView.getBitmap();
      if (cameraPreview == null) {
        Etils.showToast(CameraActivity.this, "Camera preview bitmap null");
      }
      mPreviewCapture.setImageBitmap(cameraPreview);
      mPreviewCapture.animate()
          .alpha(1.0f)
          .setInterpolator(FAST_OUT_SLOW_IN_INTERPOLATOR)
          .setDuration(1500)
          .withEndAction(new Runnable() {
            @Override
            public void run() {
              mPreviewCapture.animate()
                  .alpha(0.0f)
                  .setInterpolator(FAST_OUT_SLOW_IN_INTERPOLATOR)
                  .scaleX(0.3f)
                  .scaleY(0.3f)
                  .translationX(Etils.dpToPx(300))
                  .translationY(Etils.dpToPx(500))
                  .withEndAction(new Runnable() {
                    @Override
                    public void run() {
                      mPreviewCapture.setScaleX(1.0f);
                      mPreviewCapture.setScaleY(1.0f);
                      mPreviewCapture.setTranslationX(1.0f);
                      mPreviewCapture.setTranslationY(1.0f);
                    }
                  });
            }
          });

      // This is the CaptureRequest.Builder that we use to take a picture.
      final CaptureRequest.Builder captureBuilder =
          mCamera2Device.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
      captureBuilder.addTarget(mImageReader.getSurface());

      // Use the same AE and AF modes as the preview.
      captureBuilder.set(CaptureRequest.CONTROL_AF_MODE,
          CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
      setAutoFlash(captureBuilder);

      // Orientation
      int rotation = activity.getWindowManager().getDefaultDisplay().getRotation();
      captureBuilder.set(CaptureRequest.JPEG_ORIENTATION, getOrientation(rotation));

      Date date = new Date();
      SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH.mm.ss");
      final String timestampFilename = dateFormat.format(date);
      mFile = new File(getExternalFilesDir(null), timestampFilename + ".jpg");
      CameraCaptureSession.CaptureCallback CaptureCallback = new CameraCaptureSession.CaptureCallback() {
        @Override
        public void onCaptureCompleted(@NonNull CameraCaptureSession session,
                                       @NonNull CaptureRequest request,
                                       @NonNull TotalCaptureResult result) {
          lawg.d("Saved file: " + mFile.toString());
          unlockFocus();
        }
      };

      mCamera2CaptureSession.stopRepeating();
      mCamera2CaptureSession.capture(captureBuilder.build(), CaptureCallback, mBackgroundHandler);

    } catch (CameraAccessException e) {
      e.printStackTrace();
    }
  }

  @TargetApi(CAMERA_2_API_LIMIT)
  private void startCamera2(int width, int height) {
    lawg.d("startCamera2()");

    setUpCameraOutputs(width, height);
    configureTransform(width, height);
    mCamera2Manager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
    try {
      if (!mCameraOpenCloseLock.tryAcquire(2500, TimeUnit.MILLISECONDS)) {
        throw new RuntimeException("Time out waiting to lock camera opening.");
      }
      mCamera2Manager.openCamera(mCameraId, mCamera2StateCallback, mBackgroundHandler);
    } catch (CameraAccessException e) {
      e.printStackTrace();
    } catch (InterruptedException e) {
      throw new RuntimeException("Interrupted while trying to lock camera opening.", e);
    } catch (SecurityException se) {
      lawg.e("SecurityException " + se);
      se.printStackTrace();
    }

  }

  /**
   * Configures the necessary {@link android.graphics.Matrix} transformation to `mTextureView`.
   * This method should be called after the camera preview size is determined in
   * setUpCameraOutputs and also the size of `mTextureView` is fixed.
   *
   * @param viewWidth  The width of `mTextureView`
   * @param viewHeight The height of `mTextureView`
   */
  @TargetApi(CAMERA_2_API_LIMIT)
  private void configureTransform(int viewWidth, int viewHeight) {
    Activity activity = CameraActivity.this;
    if (null == mTextureView || null == mPreviewSize || null == activity) {
      return;
    }
    int rotation = activity.getWindowManager().getDefaultDisplay().getRotation();
    Matrix matrix = new Matrix();
    RectF viewRect = new RectF(0, 0, viewWidth, viewHeight);
    RectF bufferRect = new RectF(0, 0, mPreviewSize.getHeight(), mPreviewSize.getWidth());
    float centerX = viewRect.centerX();
    float centerY = viewRect.centerY();
    if (Surface.ROTATION_90 == rotation || Surface.ROTATION_270 == rotation) {
      bufferRect.offset(centerX - bufferRect.centerX(), centerY - bufferRect.centerY());
      matrix.setRectToRect(viewRect, bufferRect, Matrix.ScaleToFit.FILL);
      float scale = Math.max(
          (float) viewHeight / mPreviewSize.getHeight(),
          (float) viewWidth / mPreviewSize.getWidth());
      matrix.postScale(scale, scale, centerX, centerY);
      matrix.postRotate(90 * (rotation - 2), centerX, centerY);
    } else if (Surface.ROTATION_180 == rotation) {
      matrix.postRotate(180, centerX, centerY);
    }
    mTextureView.setTransform(matrix);
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

  @TargetApi(CAMERA_2_API_LIMIT)
  private void stopCamera2() {
    try {
      mCameraOpenCloseLock.acquire();
      if (null != mCamera2CaptureSession) {
        mCamera2CaptureSession.close();
        mCamera2CaptureSession = null;
      }
      if (null != mCamera2Device) {
        mCamera2Device.close();
        mCamera2Device = null;
      }
      if (null != mImageReader) {
        mImageReader.close();
        mImageReader = null;
      }
    } catch (InterruptedException e) {
      throw new RuntimeException("Interrupted while trying to lock camera closing.", e);
    } finally {
      mCameraOpenCloseLock.release();
    }
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

  private static final boolean DEBUG_RECOGNITION = false;
  RecognitionListener mRecognitionListener = new RecognitionListener() {
    @Override
    public void onReadyForSpeech(Bundle params) {
      if (DEBUG_RECOGNITION) lawg.d("[RecognitionListener] onReadyForSpeech()");
    }

    @Override
    public void onBeginningOfSpeech() {
      if (DEBUG_RECOGNITION) lawg.d("[RecognitionListener] onBeginningOfSpeech()");
    }

    @Override
    public void onRmsChanged(float rmsdB) {
      mDisplayText2.setText("" + rmsdB);
    }

    @Override
    public void onBufferReceived(byte[] buffer) {
      if (DEBUG_RECOGNITION) lawg.d("onBufferReceived()");
    }

    @Override
    public void onEndOfSpeech() {
      if (DEBUG_RECOGNITION) lawg.d("[RecognitionListener] onEndOfSpeech()");
//      startListening();
    }

    @Override
    public void onError(int error) {
      if (DEBUG_RECOGNITION) lawg.d("[RecognitionListener] onError() errorCode: " + error);
      Toast.makeText(CameraActivity.this, "Speech Recognition Error", Toast.LENGTH_SHORT).show();
      startListening();
    }

    @Override
    public void onResults(Bundle results) {
      lawg.d("[RecognitionListener] onResults()");
      ArrayList<String> resultsArray = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
      float[] confidenceScores = results.getFloatArray(SpeechRecognizer.CONFIDENCE_SCORES);
      StringBuilder sb = new StringBuilder();
      for (int i = 0; i < resultsArray.size(); i++) {
        String word = resultsArray.get(i);
        sb.append(word);
        if (i != resultsArray.size() - 1) sb.append(", ");
        if (TextUtils.equals(word.toLowerCase(), "cheese")) {
          captureStillPicture();
        }
      }
      lawg.d("Words detected: \n" + sb.toString());
      mDisplayText1.setText(sb.toString());

      for (int i = 0; i < resultsArray.size(); i++) {
        String word = resultsArray.get(i);
        addFloatingWord(word, confidenceScores[i]);
      }

      // repeat listen
      startListening();
    }

    @Override
    public void onPartialResults(Bundle partialResults) {
      ArrayList<String> resultsArray = partialResults.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
      StringBuilder sb = new StringBuilder();
      for (int i = 0; i < resultsArray.size(); i++) {
        String word = resultsArray.get(i);
        sb.append(word);
        if (i != resultsArray.size() - 1) sb.append(", ");
        if (TextUtils.equals(word.toLowerCase(), "cheese")) {
          captureStillPicture();
        }
      }
      if (sb.length() > 0) {
        lawg.d("onPartialResults() Words detected: \n" + sb.toString());
      }
    }

    @Override
    public void onEvent(int eventType, Bundle params) {
      if (DEBUG_RECOGNITION) lawg.d("onEvent()");
    }
  };

  public void startListening() {
    lawg.d("startListening()");
//    mAudioManager.setStreamMute(AudioManager.STREAM_SYSTEM, true);
//    mAudioManager.setStreamMute(AudioManager.STREAM_NOTIFICATION, true);
//    mAudioManager.setStreamMute(AudioManager.STREAM_ALARM, true);
//    mAudioManager.setStreamMute(AudioManager.STREAM_MUSIC, true);
    if (mSpeechRecognizer != null) {
      mSpeechRecognizer.destroy();
    }
    mAudioManager.setStreamMute(AudioManager.STREAM_MUSIC, true);
    mAudioManager.setStreamMute(AudioManager.STREAM_ALARM, true);
    mAudioManager.setStreamMute(AudioManager.STREAM_NOTIFICATION, true);
    mAudioManager.setStreamMute(AudioManager.STREAM_SYSTEM, true);
    mAudioManager.setStreamMute(AudioManager.STREAM_RING, true);
    mSpeechRecognizer = SpeechRecognizer.createSpeechRecognizer(this);
    mSpeechRecognizer.setRecognitionListener(mRecognitionListener);
    Intent recognizerIntent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
    recognizerIntent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);
//    recognizerIntent.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 1000 * 2);
//    recognizerIntent.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 1000 * 2);
//    recognizerIntent.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 1000 * 2);
    mSpeechRecognizer.startListening(recognizerIntent);
  }

  Map<String, View> wordViews = new HashMap<>();
  long lastWordAddedMillis = -1;
  final long SPACING_GAP_MILLIS = 300;
  int[] randomColors = new int[] {
      R.color.flatui_red_1, R.color.flatui_red_2,
      R.color.flatui_orange_1, R.color.flatui_orange_2,
      R.color.flatui_yellow_1, R.color.flatui_yellow_2,
      R.color.flatui_green_1, R.color.flatui_green_2,
      R.color.flatui_teal_1, R.color.flatui_teal_2,
      R.color.flatui_blue_1, R.color.flatui_blue_2,
      R.color.flatui_purple_1, R.color.flatui_purple_2,
      R.color.flatui_midnightblue_1, R.color.flatui_midnightblue_2,
  };

  private void addFloatingWord(final String word, final float confidence) {

    long currentTimeMillis = System.currentTimeMillis();
    if (lastWordAddedMillis < currentTimeMillis) {
      lastWordAddedMillis = currentTimeMillis;
    }
    lastWordAddedMillis += SPACING_GAP_MILLIS;

    mHandler.postDelayed(new Runnable() {
      @Override
      public void run() {
        final View wordView = mLayoutInflater.inflate(R.layout.view_word, mRootView, false);
        int randomColor = Etils.getRandomNumberInRange(0, randomColors.length - 1);
        Etils.applyColorFilter(wordView.getBackground(), getResources().getColor(randomColors[randomColor]), true);
        TextView wordTextView = ButterKnife.findById(wordView, R.id.word);
        wordTextView.setText(word);

        float scale = Etils.mapValue(confidence, 0, 1, 1, 2.5f);
        wordTextView.setTextSize(TypedValue.COMPLEX_UNIT_PX, wordTextView.getTextSize() * scale);

        int xVariance = Etils.getRandomNumberInRange(Etils.dpToPx(-100), Etils.dpToPx(100));
        int yVariance = Etils.getRandomNumberInRange(Etils.dpToPx(-300), Etils.dpToPx(300));
        wordView.setX(Etils.dpToPx(200) + xVariance);
        wordView.setY(Etils.dpToPx(400) + yVariance);
        wordViews.put(word, wordView);
        mRootView.addView(wordView);

        int translationY = Etils.getRandomNumberInRange(Etils.dpToPx(10), Etils.dpToPx(40));

        wordView.animate()
            .translationY(translationY)
            .alpha(0f).setDuration(5000).withEndAction(new Runnable() {
          @Override
          public void run() {
            wordViews.remove(word);
            mRootView.removeView(wordView);
          }
        });
      }
    }, lastWordAddedMillis - currentTimeMillis);
  }

  @Override
  protected void onPause() {
    super.onPause();
    stopCamera2();
    stopSpeechRecognizer();
    stopBackgroundThread();
  }

  /**
   * Shows an error message dialog.
   */
  public static class ErrorDialog extends DialogFragment {

    private static final String ARG_MESSAGE = "message";

    public static ErrorDialog newInstance(String message) {
      ErrorDialog dialog = new ErrorDialog();
      Bundle args = new Bundle();
      args.putString(ARG_MESSAGE, message);
      dialog.setArguments(args);
      return dialog;
    }

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
      final Activity activity = getActivity();
      return new AlertDialog.Builder(activity)
          .setMessage(getArguments().getString(ARG_MESSAGE))
          .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialogInterface, int i) {
              activity.finish();
            }
          })
          .create();
    }

  }

  /**
   * Retrieves the JPEG orientation from the specified screen rotation.
   *
   * @param rotation The screen rotation.
   * @return The JPEG orientation (one of 0, 90, 270, and 360)
   */
  private int getOrientation(int rotation) {
    // Sensor orientation is 90 for most devices, or 270 for some devices (eg. Nexus 5X)
    // We have to take that into account and rotate JPEG properly.
    // For devices with orientation of 90, we simply return our mapping from ORIENTATIONS.
    // For devices with orientation of 270, we need to rotate the JPEG 180 degrees.
    return (ORIENTATIONS.get(rotation) + mSensorOrientation + 270) % 360;
  }

  /**
   * Unlock the focus. This method should be called when still image capture sequence is
   * finished.
   */
  @TargetApi(CAMERA_2_API_LIMIT)
  private void unlockFocus() {
    try {
      // Reset the auto-focus trigger
      mCamera2CaptureRequestBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER,
          CameraMetadata.CONTROL_AF_TRIGGER_CANCEL);
      setAutoFlash(mCamera2CaptureRequestBuilder);
      mCamera2CaptureSession.capture(mCamera2CaptureRequestBuilder.build(), mCapture2Callback,
          mBackgroundHandler);
      // After this, the camera will go back to the normal state of preview.
      mState = STATE_PREVIEW;
      mCamera2CaptureSession.setRepeatingRequest(mCamera2CaptureRequest, mCapture2Callback,
          mBackgroundHandler);
    } catch (CameraAccessException e) {
      e.printStackTrace();
    }
  }

  /**
   * Run the precapture sequence for capturing a still image. This method should be called when
   */
  @TargetApi(CAMERA_2_API_LIMIT)
  private void runPrecaptureSequence() {
    try {
      // This is how to tell the camera to trigger.
      mCamera2CaptureRequestBuilder.set(CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER,
          CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER_START);
      // Tell #mCaptureCallback to wait for the precapture sequence to be set.
      mState = STATE_WAITING_PRECAPTURE;
      mCamera2CaptureSession.capture(mCamera2CaptureRequestBuilder.build(), mCapture2Callback,
          mBackgroundHandler);
    } catch (CameraAccessException e) {
      e.printStackTrace();
    }
  }

  /**
   * Saves a JPEG {@link Image} into the specified {@link File}.
   */
  private static class ImageSaver implements Runnable {

    /**
     * The JPEG image
     */
    private final Image mImage;
    /**
     * The file we save the image into.
     */
    private final File mFile;
    private Activity mActivity;

    public ImageSaver(Activity activity, Image image, File file) {
      mActivity = activity;
      mImage = image;
      mFile = file;
    }

    @TargetApi(CAMERA_2_API_LIMIT)
    @Override
    public void run() {
      ByteBuffer buffer = mImage.getPlanes()[0].getBuffer();
      byte[] bytes = new byte[buffer.remaining()];
      buffer.get(bytes);
      FileOutputStream output = null;
      try {
        output = new FileOutputStream(mFile);
        output.write(bytes);

        Bitmap bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
        Date date = new Date();
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH.mm.ss");
        final String timestampFilename = dateFormat.format(date);
        // http://stackoverflow.com/a/8722494/4326052
        String imagePath = new File(mActivity.getExternalFilesDir(null), timestampFilename + ".jpg").getAbsolutePath();
        MediaStore.Images.Media.insertImage(mActivity.getContentResolver(), bitmap, timestampFilename, "Picture taken by shoutake");

      } catch (IOException e) {
        e.printStackTrace();
      } finally {
        mImage.close();
        if (null != output) {
          try {
            output.close();
          } catch (IOException e) {
            e.printStackTrace();
          }
        }
      }
    }
  }

}
