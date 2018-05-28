/*
 * Copyright 2018 Google Inc. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.ar.core.examples.java.cloudanchor;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.os.Build;
import android.os.Bundle;
import android.support.annotation.GuardedBy;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.ar.core.Anchor;
import com.google.ar.core.Anchor.CloudAnchorState;
import com.google.ar.core.ArCoreApk;
import com.google.ar.core.Camera;
import com.google.ar.core.Config;
import com.google.ar.core.Config.CloudAnchorMode;
import com.google.ar.core.Frame;
import com.google.ar.core.HitResult;
import com.google.ar.core.Plane;
import com.google.ar.core.Point;
import com.google.ar.core.Point.OrientationMode;
import com.google.ar.core.PointCloud;
import com.google.ar.core.Session;
import com.google.ar.core.Trackable;
import com.google.ar.core.TrackingState;
import com.google.ar.core.examples.java.common.helpers.AppPermissionHelper;
import com.google.ar.core.examples.java.common.helpers.DisplayRotationHelper;
import com.google.ar.core.examples.java.common.helpers.FullScreenHelper;
import com.google.ar.core.examples.java.common.helpers.SnackbarHelper;
import com.google.ar.core.examples.java.common.messaging.MyFirebaseMessagingService;
import com.google.ar.core.examples.java.common.rendering.BackgroundRenderer;
import com.google.ar.core.examples.java.common.rendering.ObjectRenderer;
import com.google.ar.core.examples.java.common.rendering.ObjectRenderer.BlendMode;
import com.google.ar.core.examples.java.common.rendering.PlaneRenderer;
import com.google.ar.core.examples.java.common.rendering.PointCloudRenderer;
import com.google.ar.core.exceptions.CameraNotAvailableException;
import com.google.ar.core.exceptions.UnavailableApkTooOldException;
import com.google.ar.core.exceptions.UnavailableArcoreNotInstalledException;
import com.google.ar.core.exceptions.UnavailableSdkTooOldException;
import com.google.common.base.Preconditions;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.messaging.FirebaseMessaging;



import java.io.FileInputStream;
import java.io.IOException;
import java.util.Collection;
import java.util.UUID;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

/**
 * Main Activity for the Cloud Anchor Example
 *
 * <p>This is a simple example that shows how to host and resolve anchors using ARCore Cloud Anchors
 * API calls. This app only has at most one anchor at a time, to focus more on the cloud aspect of
 * anchors.
 */
public class CloudAnchorActivity extends AppCompatActivity implements GLSurfaceView.Renderer {
    private static final String TAG = CloudAnchorActivity.class.getSimpleName();

    private enum HostResolveMode {
        NONE,
        HOSTING,
        RESOLVING,
    }

    // Rendering. The Renderers are created here, and initialized when the GL surface is created.
    private GLSurfaceView surfaceView;
    private final BackgroundRenderer backgroundRenderer = new BackgroundRenderer();
    private final ObjectRenderer virtualObject = new ObjectRenderer();
    private final ObjectRenderer virtualObjectShadow = new ObjectRenderer();
    private final PlaneRenderer planeRenderer = new PlaneRenderer();
    private final PointCloudRenderer pointCloudRenderer = new PointCloudRenderer();

    private boolean installRequested;

    // Temporary matrices allocated here to reduce number of allocations for each frame.
    private final float[] anchorMatrix = new float[16];
    private final float[] viewMatrix = new float[16];
    private final float[] projectionMatrix = new float[16];

    // Locks needed for synchronization
    private final Object singleTapLock = new Object();
    private final Object anchorLock = new Object();

    // Tap handling and UI.
    private GestureDetector gestureDetector;
    private final SnackbarHelper snackbarHelper = new SnackbarHelper();
    private DisplayRotationHelper displayRotationHelper;
    private Button hostButton;
    private Button resolveButton;
    private TextView roomCodeText;

    @GuardedBy("singleTapLock")
    private MotionEvent queuedSingleTap;

    private Session session;

    @GuardedBy("anchorLock")
    private Anchor anchor;

    // Cloud Anchor Components.
    private FirebaseManager firebaseManager;
    private final CloudAnchorManager cloudManager = new CloudAnchorManager();
    private HostResolveMode currentMode;
    private RoomCodeAndCloudAnchorIdListener hostListener;

    // Firebase Messaging
    private String fireToken;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        // Initialize the surface
        surfaceView = findViewById(R.id.surfaceview);
        displayRotationHelper = new DisplayRotationHelper(this);
        /**
         *  -------------------------------------ARCORE---------------------------------------------
         */
        // Set up tap listener.
        gestureDetector =
                new GestureDetector(
                        this,
                        new GestureDetector.SimpleOnGestureListener() {
                            @Override
                            public boolean onSingleTapUp(MotionEvent e) {
                                synchronized (singleTapLock) {
                                    if (currentMode == HostResolveMode.HOSTING) {
                                        queuedSingleTap = e;
                                    }
                                }
                                return true;
                            }

                            @Override
                            public boolean onDown(MotionEvent e) {
                                return true;
                            }
                        });
        surfaceView.setOnTouchListener((v, event) -> gestureDetector.onTouchEvent(event));

        // Set up renderer.
        surfaceView.setPreserveEGLContextOnPause(true);
        surfaceView.setEGLContextClientVersion(2);
        surfaceView.setEGLConfigChooser(8, 8, 8, 8, 16, 0); // Alpha used for plane blending.
        surfaceView.setRenderer(this);
        surfaceView.setRenderMode(GLSurfaceView.RENDERMODE_CONTINUOUSLY);
        installRequested = false;

        // Initialize UI components.
        hostButton = findViewById(R.id.host_button);
        hostButton.setOnClickListener((view) -> onHostButtonPress());
        resolveButton = findViewById(R.id.resolve_button);
        resolveButton.setOnClickListener((view) -> onResolveButtonPress());
        roomCodeText = findViewById(R.id.room_code_text);

        // Initialize Cloud Anchor variables.
        firebaseManager = new FirebaseManager(this);
        currentMode = HostResolveMode.NONE;

        // Initialize Firebase messaging
        initializeFirebaseMessagingOnCreate();
    }

    /**
     * -------------------------------NOTIFICATION------------------------------------------------
     */
    protected void initializeFirebaseMessagingOnCreate() {

        // Firebase service account
        // firebase-adminsdk-uw4ag@huntar-88a42.iam.gserviceaccount.com

        /*
        try {
            FileInputStream serviceAccount = new FileInputStream("huntar-88a42-firebase-adminsdk-uw4ag-243c9cde06.json");
            FirebaseOptions options = new FirebaseOptions.Builder()
                    .setCredentials(GoogleCredentials.fromStream(serviceAccount))
                    //.setCredentials(GoogleCredentials.getApplicationDefault())
                    .setDatabaseUrl("https://huntar-88a42.firebaseio.com") //https://huntar-88a42.firebaseio.com/
                    .build();
            FirebaseApp.initializeApp(options);
        } catch (Exception e) {
            Log.e(TAG, e.getMessage());
        }
        */

        subscribeNotifications();

        // If a notification message is tapped, any data accompanying the notification
        // message is available in the intent extras. In this sample the launcher
        // intent is fired when the notification is tapped, so any accompanying data would
        // be handled here. If you want a different intent fired, set the click_action
        // field of the notification message to the desired intent. The launcher intent
        // is used when no click_action is specified.
        //
        // Handle possible data accompanying notification message.
        // [START handle_data_extras]
        if (getIntent().getExtras() != null) {
            for (String key : getIntent().getExtras().keySet()) {
                Object value = getIntent().getExtras().get(key);
                Log.d(TAG, "Key: " + key + " Value: " + value);
            }
        }
        // [END handle_data_extras]

        Button logTokenButton = findViewById(R.id.logTokenButton);
        logTokenButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // Get token
                fireToken = ""; // FirebaseInstanceId.getInstance().getToken();
                // fireToken = "fiDmOSQIya4:APA91bEpYwqJ1xgxTwFhP2mDUqIJhP3strLdoW3SGgKjr4j5pF64IbPIRvlZk9T1ozVo91uZpMDHskl3U2dqi4PIgo4O4ocl2PGdlt8BYuvHKWe43IBoNIHKHucXBpepddv3KJKAWVGW";

                String msg = getString(R.string.msg_token_fmt, fireToken);
                Log.d(TAG, msg);
                Toast.makeText(CloudAnchorActivity.this, msg, Toast.LENGTH_SHORT).show();
            }
        });
    }


    private String getUniquePhoneId() {
        final TelephonyManager tm = (TelephonyManager) getBaseContext().getSystemService(Context.TELEPHONY_SERVICE);

        final String tmDevice, tmSerial, androidId;
        if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                    this, new String[] {android.Manifest.permission.READ_PHONE_STATE}, 2);
        }
        tmDevice = "" + tm.getDeviceId();
        tmSerial = "" + tm.getSimSerialNumber();
        androidId = "" + android.provider.Settings.Secure.getString(getContentResolver(), android.provider.Settings.Secure.ANDROID_ID);
        UUID deviceUuid = new UUID(androidId.hashCode(), ((long)tmDevice.hashCode() << 32) | tmSerial.hashCode());
        String deviceId = deviceUuid.toString();
        return deviceId;
  }

  private void sendUpstreamMessage(){
        /* Ref: https://firebase.google.com/docs/cloud-messaging/android/topic-messaging

        POST https://fcm.googleapis.com/v1/projects/myproject-b5ae1/messages:send HTTP/1.1
        Content-Type: application/json
        Authorization: Bearer ya29.ElqKBGN2Ri_Uz...HnS_uNreA
        {
          "message":{
            "topic" : "foo-bar",
            "notification" : {
              "body" : "This is a Firebase Cloud Messaging Topic Message!",
              "title" : "FCM Message",
              }
           }
        }
         */

      String channelName = getString(R.string.default_notification_channel_name);
      // See documentation on defining a message payload.
      // RemoteMessage.Builder bui = new RemoteMessage.Builder("");
      /*
      Message message = Message.builder()
              //.setNotification()
              .putData("score", "850")
              .putData("time", "2:45")
                . setTopic(channelName)
              .build();
      // Send a message to the device corresponding to the provided registration token.
      String response = null;
      try {
          response = FirebaseMessaging.getInstance().send(message);
          // Response is a message ID string.
          Log.d(TAG,"Successfully sent message: " + response);
      } catch (FirebaseMessagingException e) {
          Log.e(TAG,e.toString());
      }
      */
  }

  private void subscribeNotifications(){
      String channelId  =  getString(R.string.default_notification_channel_id);
      String channelName = getString(R.string.default_notification_channel_name);
      // Register the notification handler
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
          Log.i(TAG,"Initialized notification handler");
          // Create channel to show notifications.
          NotificationManager notificationManager = getSystemService(NotificationManager.class);
          // Create the notification message content
          NotificationChannel mChannel = new NotificationChannel(channelId, channelName, NotificationManager.IMPORTANCE_LOW);
          mChannel.setDescription("Treasure local ");
          mChannel.enableLights(true);
          mChannel.setLightColor(Color.RED);
          mChannel.enableVibration(true);
          mChannel.setVibrationPattern(new long[]{100, 200, 300, 400, 500, 400, 300, 200, 400});
          notificationManager.createNotificationChannel(mChannel);
      }

      Log.d(TAG, "Subscribing to news topic");
      // [START subscribe_topics]
      FirebaseMessaging.getInstance().subscribeToTopic(channelName)
              .addOnCompleteListener(new OnCompleteListener<Void>() {
                  @Override
                  public void onComplete(@NonNull Task<Void> task) {
                      String msg = getString(R.string.msg_subscribed);
                      if (!task.isSuccessful()) {
                          msg = getString(R.string.msg_subscribe_failed);
                      }
                      Log.d(TAG, "Topic: "+channelName+", Message:"+msg);
                      Toast.makeText(CloudAnchorActivity.this, msg, Toast.LENGTH_SHORT).show();
                  }
              });
      // [END subscribe_topics]
  }


  @Override
  protected void onResume() {
    super.onResume();

    if (session == null) {
      Exception exception = null;
      int messageId = -1;
      try {
        switch (ArCoreApk.getInstance().requestInstall(this, !installRequested)) {
          case INSTALL_REQUESTED:
            installRequested = true;
            return;
          case INSTALLED:
            break;
        }

        // ARCore requires camera permissions to operate. If we did not yet obtain runtime
        // permission on Android M and above, now is a good time to ask the user for it.
        if (!AppPermissionHelper.hasCameraPermission(this)) {
            AppPermissionHelper.requestCameraPermission(this);
          return;
        }
        session = new Session(this);
      } catch (UnavailableArcoreNotInstalledException e) {
        messageId = R.string.snackbar_arcore_unavailable;
        exception = e;
      } catch (UnavailableApkTooOldException e) {
        messageId = R.string.snackbar_arcore_too_old;
        exception = e;
      } catch (UnavailableSdkTooOldException e) {
        messageId = R.string.snackbar_arcore_sdk_too_old;
        exception = e;
      } catch (Exception e) {
        messageId = R.string.snackbar_arcore_exception;
        exception = e;
      }

      if (exception != null) {
        snackbarHelper.showError(this, getString(messageId));
        Log.e(TAG, "Exception creating session", exception);
        return;
      }

      // Create default config and check if supported.
      Config config = new Config(session);
      config.setCloudAnchorMode(CloudAnchorMode.ENABLED);
      session.configure(config);

      // Setting the session in the HostManager.
      cloudManager.setSession(session);
      // Show the inital message only in the first resume.
      snackbarHelper.showMessage(this, getString(R.string.snackbar_initial_message));
    }

    // Note that order matters - see the note in onPause(), the reverse applies here.
    try {
      session.resume();
    } catch (CameraNotAvailableException e) {
      // In some cases (such as another camera app launching) the camera may be given to
      // a different app instead. Handle this properly by showing a message and recreate the
      // session at the next iteration.
      snackbarHelper.showError(this, getString(R.string.snackbar_camera_unavailable));
      session = null;
      return;
    }
    surfaceView.onResume();
    displayRotationHelper.onResume();
  }

  @Override
  public void onPause() {
    super.onPause();
    if (session != null) {
      // Note that the order matters - GLSurfaceView is paused first so that it does not try
      // to query the session. If Session is paused before GLSurfaceView, GLSurfaceView may
      // still call session.update() and get a SessionPausedException.
      displayRotationHelper.onPause();
      surfaceView.onPause();
      session.pause();
    }
  }

  @Override
  public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] results) {
    if (!AppPermissionHelper.hasCameraPermission(this)) {
      Toast.makeText(this, "Camera permission is needed to run this application", Toast.LENGTH_LONG)
          .show();
      if (!AppPermissionHelper.shouldShowRequestPermissionRationale(this)) {
        // Permission denied with checking "Do not ask again".
          AppPermissionHelper.launchPermissionSettings(this);
      }
      finish();
    }
  }

  @Override
  public void onWindowFocusChanged(boolean hasFocus) {
    super.onWindowFocusChanged(hasFocus);
    FullScreenHelper.setFullScreenOnWindowFocusChanged(this, hasFocus);
  }

  /**
   * Handles the most recent user tap.
   *
   * <p>We only ever handle one tap at a time, since this app only allows for a single anchor.
   *
   * @param frame the current AR frame
   * @param cameraTrackingState the current camera tracking state
   */
  private void handleTap(Frame frame, TrackingState cameraTrackingState) {
    // Handle taps. Handling only one tap per frame, as taps are usually low frequency
    // compared to frame rate.
    synchronized (singleTapLock) {
      synchronized (anchorLock) {
        // Only handle a tap if the anchor is currently null, the queued tap is non-null and the
        // camera is currently tracking.
        if (anchor == null
			&& queuedSingleTap != null
            && cameraTrackingState == TrackingState.TRACKING) {
          Preconditions.checkState(
              currentMode == HostResolveMode.HOSTING,
              "We should only be creating an anchor in hosting mode.");
          for (HitResult hit : frame.hitTest(queuedSingleTap)) {
            if (shouldCreateAnchorWithHit(hit)) {
              Anchor newAnchor = hit.createAnchor();
              //Log.i(TAG,"Create an anchor: "+ newAnchor);
              Preconditions.checkNotNull(hostListener, "The host listener cannot be null.");
              cloudManager.hostCloudAnchor(newAnchor, hostListener);
              setNewAnchor(newAnchor);
              snackbarHelper.showMessage(this, getString(R.string.snackbar_anchor_placed));
              break; // Only handle the first valid hit.
            }else{
                //Log.e(TAG,"Unable to create an anchor ");
            }
          }
        }
      }
      queuedSingleTap = null;
    }
  }

  /** Returns {@code true} if and only if the hit can be used to create an Anchor reliably. */
  private static boolean shouldCreateAnchorWithHit(HitResult hit) {
    Trackable trackable = hit.getTrackable();
    if (trackable instanceof Plane) {
      // Check if the hit was within the plane's polygon.
      return ((Plane) trackable).isPoseInPolygon(hit.getHitPose());
    } else if (trackable instanceof Point) {
      // Check if the hit was against an oriented point.
      return ((Point) trackable).getOrientationMode() == OrientationMode.ESTIMATED_SURFACE_NORMAL;
    }
    return false;
  }

  @Override
  public void onSurfaceCreated(GL10 gl, EGLConfig config) {
    GLES20.glClearColor(0.1f, 0.1f, 0.1f, 1.0f);

    // Prepare the rendering objects. This involves reading shaders, so may throw an IOException.
    try {
      // Create the texture and pass it to ARCore session to be filled during update().
      backgroundRenderer.createOnGlThread(this);
      planeRenderer.createOnGlThread(this, "models/trigrid.png");
      pointCloudRenderer.createOnGlThread(this);

      virtualObject.createOnGlThread(this, "models/andy.obj", "models/andy.png");
      virtualObject.setMaterialProperties(0.0f, 2.0f, 0.5f, 6.0f);

      virtualObjectShadow.createOnGlThread(
          this, "models/andy_shadow.obj", "models/andy_shadow.png");
      virtualObjectShadow.setBlendMode(BlendMode.Shadow);
      virtualObjectShadow.setMaterialProperties(1.0f, 0.0f, 0.0f, 1.0f);
    } catch (IOException ex) {
      Log.e(TAG, "Failed to read an asset file", ex);
    }
  }

  @Override
  public void onSurfaceChanged(GL10 gl, int width, int height) {
    displayRotationHelper.onSurfaceChanged(width, height);
    GLES20.glViewport(0, 0, width, height);
  }

  @Override
  public void onDrawFrame(GL10 gl) {
    // Clear screen to notify driver it should not load any pixels from previous frame.
    GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT | GLES20.GL_DEPTH_BUFFER_BIT);

    if (session == null) {
      return;
    }
    // Notify ARCore session that the view size changed so that the perspective matrix and
    // the video background can be properly adjusted.
    displayRotationHelper.updateSessionIfNeeded(session);

    try {
      session.setCameraTextureName(backgroundRenderer.getTextureId());

      // Obtain the current frame from ARSession. When the configuration is set to
      // UpdateMode.BLOCKING (it is by default), this will throttle the rendering to the
      // camera framerate.
      Frame frame = session.update();
      Camera camera = frame.getCamera();
      Collection<Anchor> updatedAnchors = frame.getUpdatedAnchors();
      TrackingState cameraTrackingState = camera.getTrackingState();

      // Notify the cloudManager of all the updates.
      cloudManager.onUpdate(updatedAnchors);

      // Handle user input.
      handleTap(frame, cameraTrackingState);

      // Draw background.
      backgroundRenderer.draw(frame);

      // If not tracking, don't draw 3d objects.
      if (cameraTrackingState == TrackingState.PAUSED) {
        return;
      }

      // Get camera and projection matrices.
      camera.getViewMatrix(viewMatrix, 0);
      camera.getProjectionMatrix(projectionMatrix, 0, 0.1f, 100.0f);

      // Visualize tracked points.
      PointCloud pointCloud = frame.acquirePointCloud();
      pointCloudRenderer.update(pointCloud);
      pointCloudRenderer.draw(viewMatrix, projectionMatrix);

      // Application is responsible for releasing the point cloud resources after using it.
      pointCloud.release();

      // Visualize planes.
      planeRenderer.drawPlanes(
          session.getAllTrackables(Plane.class), camera.getDisplayOrientedPose(), projectionMatrix);

      // Check if the anchor can be visualized or not, and get its pose if it can be.
      boolean shouldDrawAnchor = false;
      synchronized (anchorLock) {
        if (anchor != null && anchor.getTrackingState() == TrackingState.TRACKING) {
          // Get the current pose of an Anchor in world space. The Anchor pose is updated
          // during calls to session.update() as ARCore refines its estimate of the world.
          anchor.getPose().toMatrix(anchorMatrix, 0);
          shouldDrawAnchor = true;
        }
      }

      // Visualize anchor.
      if (shouldDrawAnchor) {
        float[] colorCorrectionRgba = new float[4];
        frame.getLightEstimate().getColorCorrection(colorCorrectionRgba, 0);

        // Update and draw the model and its shadow.
        float scaleFactor = 1.0f;
        virtualObject.updateModelMatrix(anchorMatrix, scaleFactor);
        virtualObjectShadow.updateModelMatrix(anchorMatrix, scaleFactor);
        virtualObject.draw(viewMatrix, projectionMatrix, colorCorrectionRgba);
        virtualObjectShadow.draw(viewMatrix, projectionMatrix, colorCorrectionRgba);
      }
    } catch (Throwable t) {
      // Avoid crashing the application due to unhandled exceptions.
      Log.e(TAG, "Exception on the OpenGL thread", t);
    }
  }

  /** Sets the new value of the current anchor. Detaches the old anchor, if it was non-null. */
  private void setNewAnchor(Anchor newAnchor) {
    synchronized (anchorLock) {
      if (anchor != null) {
        anchor.detach();
      }
      anchor = newAnchor;
    }
  }

  /** Callback function invoked when the Host Button is pressed. */
  private void onHostButtonPress() {
    if (currentMode == HostResolveMode.HOSTING) {
      resetMode();
      return;
    }

    if (hostListener != null) {
      return;
    }
    resolveButton.setEnabled(false);
    hostButton.setText(R.string.cancel);
    snackbarHelper.showMessageWithDismiss(this, getString(R.string.snackbar_on_host));
    Log.i(TAG, "Obtain a new room code and start hosting ");
    hostListener = new RoomCodeAndCloudAnchorIdListener();
    firebaseManager.getNewRoomCode(hostListener);
  }

  /** Callback function invoked when the Resolve Button is pressed. */
  private void onResolveButtonPress() {
    if (currentMode == HostResolveMode.RESOLVING) {
      resetMode();
      return;
    }
    ResolveDialogFragment dialogFragment = new ResolveDialogFragment();
    dialogFragment.setOkListener(this::onRoomCodeEntered);
    dialogFragment.show(getSupportFragmentManager(), "ResolveDialog");
  }

  /** Resets the mode of the app to its initial state and removes the anchors. */
  private void resetMode() {
    hostButton.setText(R.string.host_button_text);
    hostButton.setEnabled(true);
    resolveButton.setText(R.string.resolve_button_text);
    resolveButton.setEnabled(true);
    roomCodeText.setText(R.string.initial_room_code);
    currentMode = HostResolveMode.NONE;
    firebaseManager.clearRoomListener();
    hostListener = null;
    setNewAnchor(null);
    snackbarHelper.hide(this);
    cloudManager.clearListeners();
  }

  /** Callback function invoked when the user presses the OK button in the Resolve Dialog. */
  private void onRoomCodeEntered(Long roomCode) {
    currentMode = HostResolveMode.RESOLVING;
    hostButton.setEnabled(false);
    resolveButton.setText(R.string.cancel);
    roomCodeText.setText(String.valueOf(roomCode));
    snackbarHelper.showMessageWithDismiss(this, getString(R.string.snackbar_on_resolve));

    // Register a new listener for the given room.
    firebaseManager.registerNewListenerForRoom(
        roomCode,
        (cloudAnchorId) -> {
          // When the cloud anchor ID is available from Firebase.
          cloudManager.resolveCloudAnchor(
              cloudAnchorId,
              (anchor) -> {
                // When the anchor has been resolved, or had a final error state.
                CloudAnchorState cloudState = anchor.getCloudAnchorState();
                if (cloudState.isError()) {
                  Log.w(
                      TAG,
                      "The anchor in room "
                          + roomCode
                          + " could not be resolved. The error state was "
                          + cloudState);
                  snackbarHelper.showMessageWithDismiss(
                      CloudAnchorActivity.this,
                      getString(R.string.snackbar_resolve_error, cloudState));
                  return;
                }
                snackbarHelper.showMessageWithDismiss(
                    CloudAnchorActivity.this, getString(R.string.snackbar_resolve_success));
                setNewAnchor(anchor);
              });
        });
  }

  /**
   * Listens for both a new room code and an anchor ID, and shares the anchor ID in Firebase with
   * the room code when both are available.
   */
  private final class RoomCodeAndCloudAnchorIdListener
      implements CloudAnchorManager.CloudAnchorListener, FirebaseManager.RoomCodeListener, MyFirebaseMessagingService.FireNotificationListener {

    private Long roomCode;
    private String cloudAnchorId;

    @Override
    public void onNewRoomCode(Long newRoomCode) {
      Preconditions.checkState(roomCode == null, "The room code cannot have been set before.");
      roomCode = newRoomCode;
      roomCodeText.setText(String.valueOf(roomCode));
      snackbarHelper.showMessageWithDismiss(
          CloudAnchorActivity.this, getString(R.string.snackbar_room_code_available));
      checkAndMaybeShare();
      synchronized (singleTapLock) {
        // Change currentMode to HOSTING after receiving the room code (not when the 'Host' button
        // is tapped), to prevent an anchor being placed before we know the room code and able to
        // share the anchor ID.
        currentMode = HostResolveMode.HOSTING;
      }
    }

    @Override
    public void onError(DatabaseError error) {
      Log.w(TAG, "A Firebase database error happened.", error.toException());
      snackbarHelper.showError(
          CloudAnchorActivity.this, getString(R.string.snackbar_firebase_error));
    }

    @Override
    public void onCloudTaskComplete(Anchor anchor) {
      CloudAnchorState cloudState = anchor.getCloudAnchorState();
      if (cloudState.isError()) {
        Log.e(TAG, "Error hosting a cloud anchor, state " + cloudState);
        snackbarHelper.showMessageWithDismiss(
            CloudAnchorActivity.this, getString(R.string.snackbar_host_error, cloudState));
        return;
      }else{
          Log.i(TAG, "Cloud Anchor is set now");
      }
      Preconditions.checkState(
          cloudAnchorId == null, "The cloud anchor ID cannot have been set before.");
      cloudAnchorId = anchor.getCloudAnchorId();
      setNewAnchor(anchor);
      checkAndMaybeShare();
    }

    private void checkAndMaybeShare() {
      if (roomCode == null || cloudAnchorId == null) {
        return;
      }
      Log.i(TAG, "Store anchor id");
      firebaseManager.storeAnchorIdInRoom(roomCode, cloudAnchorId);
      Log.i(TAG, "Stored anchor id "+ cloudAnchorId + " in room "+roomCode);
      snackbarHelper.showMessageWithDismiss(
          CloudAnchorActivity.this, getString(R.string.snackbar_cloud_id_shared));
    }

    /**
     * Notification from cloud messaging service
     *
     * @param notification
     */
    @Override
    public void onFireNotification(String notification) {
      Log.i(TAG, "From Cloud: "+notification);
      snackbarHelper.showMessageWithDismiss(
              CloudAnchorActivity.this, "From Cloud: "+notification);
    }
  }
}
