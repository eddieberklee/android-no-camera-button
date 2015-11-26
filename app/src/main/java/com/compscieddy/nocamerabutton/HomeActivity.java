package com.compscieddy.nocamerabutton;

import android.content.Context;
import android.content.Intent;
import android.hardware.Camera;
import android.media.AudioManager;
import android.speech.RecognitionListener;
import android.speech.SpeechRecognizer;
import android.support.v7.app.ActionBarActivity;
import android.os.Bundle;
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


public class HomeActivity extends ActionBarActivity implements SurfaceHolder.Callback {

  private static final String TAG = HomeActivity.class.getSimpleName();

  Camera mCamera;
  SurfaceView mSurfaceView;
  SurfaceHolder mSurfaceHolder;
  Camera.PictureCallback rawCallback;
  Camera.ShutterCallback shutterCallback;
  Camera.PictureCallback jpegCallback;

  Button mStartButton, mStopButton, mCaptureButton;
  Button mStartSpeechButton, mStopSpeechButton;

  SpeechRecognizer mSpeechRecognizer;
  AudioManager mAudioManager;

  TextView mDisplayText1, mDisplayText2;
  boolean mIsPreviewRunning;

  // App Name : Kimchi Camera?

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_home);

    mStartButton = (Button)findViewById(R.id.start_button);
    mStartButton.setOnClickListener(new Button.OnClickListener() {
      public void onClick(View v) {
        startCamera();
      }
    });
    mStopButton = (Button)findViewById(R.id.stop_button);
    mCaptureButton = (Button) findViewById(R.id.capture_button);
    mStopButton.setOnClickListener(new Button.OnClickListener() {
      public void onClick(View v) {
        stopCamera();
      }
    });
    mCaptureButton.setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View v) {
        captureImage();
      }
    });

    mSpeechRecognizer = SpeechRecognizer.createSpeechRecognizer(this);
    if (SpeechRecognizer.isRecognitionAvailable(this)) {
      Log.d(TAG, "Speech Recognition detected as being available");
      mSpeechRecognizer.setRecognitionListener(mRecognitionListener);
    } else {
      Toast.makeText(this, "Speech Recognition Not Available", Toast.LENGTH_SHORT).show();
    }
    mStartSpeechButton = (Button) findViewById(R.id.start_speech);
    mStopSpeechButton = (Button) findViewById(R.id.stop_speech);
    mStartSpeechButton.setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View v) {
        startListening();
      }
    });
    mStopSpeechButton.setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View v) {
        mSpeechRecognizer.stopListening();
      }
    });

    mAudioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
    // Immediately start listening as soon as the app is launched
    startListening();

    mDisplayText1 = (TextView) findViewById(R.id.display_text1);
    mDisplayText2 = (TextView) findViewById(R.id.display_text2);

    mSurfaceView = (SurfaceView)findViewById(R.id.surface_view);
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

  }

  private void captureImage() {
    mCamera.takePicture(shutterCallback, rawCallback, jpegCallback);
  }

  private void startCamera()
  {
    try{
      mCamera = Camera.open();
    }catch(RuntimeException e){
      Log.e(TAG, "init_camera: " + e);
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
      Log.e(TAG, "init_camera: " + e);
      return;
    }
  }

  private void stopCamera()
  {
    mCamera.stopPreview();
    mCamera.release();
  }

  public void previewCamera() {
    try {
      mCamera.setPreviewDisplay(mSurfaceHolder);
      mCamera.startPreview();
      mIsPreviewRunning = true;
    } catch (Exception e) {
      Log.e(TAG, "Cannot start preview");
    }
  }

  public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
    if (mIsPreviewRunning) {
      mCamera.stopPreview();
    }

    Display display = ((WindowManager)getSystemService(WINDOW_SERVICE)).getDefaultDisplay();

    if(display.getRotation() == Surface.ROTATION_0) {
      mCamera.setDisplayOrientation(90);
    }

    if(display.getRotation() == Surface.ROTATION_90) {
    }

    if(display.getRotation() == Surface.ROTATION_180) {
    }

    if(display.getRotation() == Surface.ROTATION_270) {
      mCamera.setDisplayOrientation(180);
    }

    startCamera();
  }

  public void surfaceCreated(SurfaceHolder holder) {
    startCamera();
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

    }

    @Override
    public void onBeginningOfSpeech() {

    }

    @Override
    public void onRmsChanged(float rmsdB) {
      mDisplayText2.setText(""+rmsdB);
    }

    @Override
    public void onBufferReceived(byte[] buffer) {

    }

    @Override
    public void onEndOfSpeech() {
      startListening();
    }

    @Override
    public void onError(int error) {
      Toast.makeText(HomeActivity.this, "Speech Recognition Error", Toast.LENGTH_SHORT).show();
      startListening();
    }

    @Override
    public void onResults(Bundle results) {
      ArrayList<String> resultsArray = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
      StringBuilder sb = new StringBuilder();
      for (int i = 0; i < resultsArray.size(); i++) {
        String word = resultsArray.get(i);
        sb.append(word);
        if (i != resultsArray.size() - 1) sb.append(", ");
        if (TextUtils.equals(word.toLowerCase(), "cheese")) captureImage();
      }
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
    mAudioManager.setStreamMute(AudioManager.STREAM_SYSTEM, true);
    mAudioManager.setStreamMute(AudioManager.STREAM_NOTIFICATION, true);
    mAudioManager.setStreamMute(AudioManager.STREAM_ALARM, true);
    mAudioManager.setStreamMute(AudioManager.STREAM_MUSIC, true);
    mAudioManager.setStreamMute(AudioManager.STREAM_RING, true);
    mAudioManager.setStreamMute(AudioManager.STREAM_SYSTEM, true);

    mSpeechRecognizer.startListening(new Intent());
  }

}
