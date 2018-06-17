package edu.ucsb.ece.ece150.maskme;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.hardware.Camera;
import android.os.Bundle;
import android.support.design.widget.Snackbar;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.res.ResourcesCompat;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.util.SparseArray;
import android.view.Display;
import android.view.Surface;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.Toast;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;
import edu.ucsb.ece.ece150.maskme.camera.CameraSourcePreview;
import edu.ucsb.ece.ece150.maskme.camera.GraphicOverlay;
import com.google.android.gms.vision.CameraSource;
import com.google.android.gms.vision.Frame;
import com.google.android.gms.vision.MultiProcessor;
import com.google.android.gms.vision.Tracker;
import com.google.android.gms.vision.face.Face;
import com.google.android.gms.vision.face.FaceDetector;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;

/**
 * Activity for the face tracker app.  This app detects faces with the rear facing camera, and draws
 * overlay graphics to indicate the position, size, and ID of each face.
 */
public final class FaceTrackerActivity extends AppCompatActivity {
    private static final String TAG = "FaceTracker";

    private CameraSource mCameraSource = null;

    private CameraSourcePreview mPreview;
    private GraphicOverlay mGraphicOverlay;

    private static final int RC_HANDLE_GMS = 9001;
    // permission request codes need to be < 256
    private static final int RC_HANDLE_CAMERA_PERM = 2;

    private enum ButtonsMode {
        PREVIEW_TAKEPICTURE_INVISIBLE, BACK_MASK_LUCKY
    }

    SparseArray<Face> mFaces = new SparseArray<>();

    private ButtonsMode buttonsMode = ButtonsMode.PREVIEW_TAKEPICTURE_INVISIBLE;
    private MaskedImageView mImageView;
    private Button mCameraButton;
    private Bitmap mCapturedImage;
    private Button mLeftButton;
    private Button mRightButton;
	private boolean isPortrait = false;

    private FaceDetector mStaticFaceDetector;


    //==============================================================================================
    // Activity Methods
    //==============================================================================================

    /**
     * Initializes the UI and initiates the creation of a face detector.
     */
    @Override
    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);
        setContentView(R.layout.facetracker_main);
		isPortrait = getResources().getConfiguration().orientation == Configuration.ORIENTATION_PORTRAIT;

        mPreview = (CameraSourcePreview) findViewById(R.id.preview);
        mGraphicOverlay = (GraphicOverlay) findViewById(R.id.faceOverlay);

        mCameraButton = (Button) findViewById(R.id.camera_button);
        mCameraButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if(buttonsMode == ButtonsMode.PREVIEW_TAKEPICTURE_INVISIBLE) {
                    mLeftButton.setVisibility(View.VISIBLE);
                    if(mCameraSource != null){
                        mCameraSource.takePicture(null, new CameraSource.PictureCallback() {
                            @Override
                            public void onPictureTaken(byte[] data) {
                                Log.i("FaceTrakAct: onPicT:",""+data.length);
                                int degrees = getDisplayRotation();
                                mCapturedImage = BitmapFactory.decodeByteArray(data, 0, data.length);
								//mCapturedImage = rotateImage(BitmapFactory.decodeByteArray(data, 0, data.length),degrees);
                                //mImageView.setImageBitmap(mCapturedImage);
                                Bitmap bm = null;
                                try {
                                    // COnverting ByteArray to Bitmap - >Rotate and Convert back to Data
                                    int CameraEyeValue = 0;
                                    if (data != null) {
                                        int screenWidth = getResources().getDisplayMetrics().widthPixels;
                                        int screenHeight = getResources().getDisplayMetrics().heightPixels;
                                        //Bitmap bm = BitmapFactory.decodeByteArray(data, 0, (data != null) ? data.length : 0);
                                        if (getResources().getConfiguration().orientation == Configuration.ORIENTATION_PORTRAIT) {
                                            // Notice that width and height are reversed
                                            Bitmap scaled = Bitmap.createScaledBitmap(mCapturedImage, screenHeight, screenWidth, true);
                                            int w = scaled.getWidth();
                                            int h = scaled.getHeight();
                                            // Setting post rotate to 90
                                            Matrix mtx = new Matrix();
                                            boolean cameraFront = Boolean.TRUE;
                                            CameraEyeValue = setPhotoOrientation(FaceTrackerActivity.this, cameraFront==true ? 1:0); // CameraID = 1 : front 0:back
                                            if(cameraFront) { // As Front camera is Mirrored so Fliping the Orientation
                                                if (CameraEyeValue == 270) {
                                                    mtx.postRotate(90);
                                                } else if (CameraEyeValue == 90) {
                                                    mtx.postRotate(270);
                                                }
                                            }else{
                                                mtx.postRotate(CameraEyeValue); // CameraEyeValue is default to Display Rotation
                                            }

                                            bm = Bitmap.createBitmap(scaled, 0, 0, w, h, mtx, true);
                                        }else{// LANDSCAPE MODE
                                            //No need to reverse width and height
                                            Bitmap scaled = Bitmap.createScaledBitmap(bm, screenWidth, screenHeight, true);
                                            bm=scaled;
                                        }
                                    }
                                    // Converting the Die photo to Bitmap
                                    Toast toast = Toast.makeText(getApplicationContext(), "Picture: "+CameraEyeValue , Toast.LENGTH_LONG);
                                    toast.show();

                                } catch (Exception e) {

                                }
                                mImageView.setImageBitmap(bm);
                                }
                        });
                    }
                }
                else{ // BACK_MASK_LUCKY Mode
                    if(mFaces.size() == 0) {
                        detectStaticFaces(mCapturedImage);
                    }
                    mImageView.drawFirstMask(mFaces);
                }
            }
        });

        mLeftButton = (Button) findViewById(R.id.left_button);
        mRightButton = (Button) findViewById(R.id.right_button);

        mLeftButton.setVisibility(View.GONE);
        mRightButton.setVisibility(View.GONE);

        mLeftButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if(buttonsMode == ButtonsMode.PREVIEW_TAKEPICTURE_INVISIBLE) {
                    mImageView.setImageBitmap(mCapturedImage);
                    mPreview.addView(mImageView);
                    mPreview.bringChildToFront(mImageView);
                    mLeftButton.setText("Back");
                    mCameraButton.setText("Mask!");
                    mRightButton.setVisibility(View.VISIBLE);
                    buttonsMode = ButtonsMode.BACK_MASK_LUCKY;
                }
                else{
                    mPreview.removeView(mImageView);
                    mLeftButton.setText("Preview");
                    mCameraButton.setText("Take Picture!");
                    mRightButton.setVisibility(View.GONE);
                    mFaces.clear();
                    buttonsMode = ButtonsMode.PREVIEW_TAKEPICTURE_INVISIBLE;
                }
            }
        });

        mRightButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if(mFaces.size() == 0) {
                    detectStaticFaces(mCapturedImage);
                }
                mImageView.drawSecondMask(mFaces);
            }
        });

        mImageView = new MaskedImageView(getApplicationContext());
        mImageView.setScaleType(ImageView.ScaleType.FIT_XY);

        // Check for the camera permission before accessing the camera.  If the
        // permission is not granted yet, request permission.
        int permissionGranted = ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA);
        if (permissionGranted == PackageManager.PERMISSION_GRANTED) {
            createCameraSource();
        } else {
            requestCameraPermission();
        }
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
    }

    public int setPhotoOrientation(Activity activity, int cameraId) {
        android.hardware.Camera.CameraInfo info = new android.hardware.Camera.CameraInfo();
        android.hardware.Camera.getCameraInfo(cameraId, info);
        int rotation = activity.getWindowManager().getDefaultDisplay().getRotation();
        int degrees = 0;
        switch (rotation) {
            case Surface.ROTATION_0:
                degrees = 0;
                break;
            case Surface.ROTATION_90:
                degrees = 90;
                break;
            case Surface.ROTATION_180:
                degrees = 180;
                break;
            case Surface.ROTATION_270:
                degrees = 270;
                break;
        }

        int result;
        // do something for phones running an SDK before lollipop
        if (info.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
            result = (info.orientation + degrees) % 360;
            result = (360 - result) % 360; // compensate the mirror
        } else { // back-facing
            result = (info.orientation - degrees + 360) % 360;
        }

        return result;
    }

    public int getDisplayRotation(){
        Display display = ((WindowManager) getApplicationContext()
                .getSystemService(Context.WINDOW_SERVICE)).getDefaultDisplay();
        int rotation = display.getRotation();
        int degrees = 0;
        switch (rotation) {
            case Surface.ROTATION_0:
                degrees = 0;
                break;
            case Surface.ROTATION_90:
                degrees = 90;
                break;
            case Surface.ROTATION_180:
                degrees = 180;
                break;
            case Surface.ROTATION_270:
                degrees = 270;
                break;
        }
        Log.i("FaceTrakAct: onRot:","Display rotated by angle => "+degrees);
        return degrees;
    }

    public static Bitmap rotateImage(Bitmap source, float angle) {
        Log.v("FaceTrakAct: rot:", "Rotating bitmap " + angle + " degrees");
        Matrix matrix = new Matrix();
        matrix.postRotate(angle);
        return Bitmap.createBitmap(source, 0, 0, source.getWidth(), source.getHeight(),
                matrix, true);
    }
    private void detectStaticFaces(Bitmap inputImage){
        if(inputImage == null){
            return;
        }
        Log.i("FaceTrakAct: detect:","");
        Frame frame = new Frame.Builder().setBitmap(inputImage).build();
        Log.i("FaceTrakAct: frame:",""+ frame.getBitmap().getWidth());
        mFaces = mStaticFaceDetector.detect(frame);
        Log.i("FaceTrakAct: NumFaces:", String.valueOf(mFaces.size()));
    }

    /**
     * Handles the requesting of the camera permission.  This includes
     * showing a "Snackbar" message of why the permission is needed then
     * sending the request.
     */
    private void requestCameraPermission() {
        Log.w(TAG, "Camera permission is not granted. Requesting permission");

        final String[] permissions = new String[]{Manifest.permission.CAMERA};

        if (!ActivityCompat.shouldShowRequestPermissionRationale(this,
                Manifest.permission.CAMERA)) {
            ActivityCompat.requestPermissions(this, permissions, RC_HANDLE_CAMERA_PERM);
            return;
        }

        final Activity thisActivity = this;

        View.OnClickListener listener = new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                ActivityCompat.requestPermissions(thisActivity, permissions,
                        RC_HANDLE_CAMERA_PERM);
            }
        };

        Snackbar.make(mGraphicOverlay, R.string.permission_camera_rationale,
                Snackbar.LENGTH_INDEFINITE)
                .setAction(R.string.ok, listener)
                .show();
    }

    /**
     * Creates and starts the camera.  Note that this uses a higher resolution in comparison
     * to other detection examples to enable the barcode detector to detect small barcodes
     * at long distances.
     */
    private void createCameraSource() {

        // TODO: Create a face detector for real time face detection
        // 1. Get the application's context
        Context context = getApplicationContext();
        // 2. Create a FaceDetector object for real time detection
        //    Ref: https://developers.google.com/vision/android/face-tracker-tutorial
        FaceDetector detector = new FaceDetector.Builder(context)
                .setClassificationType(FaceDetector.ALL_CLASSIFICATIONS)
                .build();

        // 3. Create a FaceDetector object for detecting faces on a static photo
        mStaticFaceDetector = new FaceDetector.Builder(context)
                .setClassificationType(FaceDetector.ALL_CLASSIFICATIONS)
                .build();

        // 4. Create a GraphicFaceTrackerFactory
        // 5. Pass the GraphicFaceTrackerFactory to
        //    a MultiProcessor.Builder to create a MultiProcessor
        // 6. Associate the MultiProcessor with the real time detector
        detector.setProcessor(
                new MultiProcessor.Builder<>(new GraphicFaceTrackerFactory())
                        .build());
        // 7. Check if the real time detector is operational
        if (detector.isOperational()) {
            // Note: The first time that an app using face API is installed on a device, GMS will
            // download a native library to the device in order to do detection.  Usually this
            // completes before the app is run for the first time.  But if that download has not yet
            // completed, then the above call will not detect any faces.
            //
            // isOperational() can be used to check if the required native library is currently
            // available.  The detector will automatically become operational once the library
            // download completes on device.
            Log.w(TAG, "Face detector dependencies are not yet available.");
	
	        // 8. Create a camera source to capture video images from the camera,
	        //    and continuously stream those images into the detector and
	        //    its associated MultiProcessor
	        mCameraSource = new CameraSource.Builder(context, detector)
	                .setRequestedPreviewSize(isPortrait?1400:1000, isPortrait? 1000:800)
	                .setFacing(CameraSource.CAMERA_FACING_BACK)
					.setAutoFocusEnabled(true)
	                .setRequestedFps(30.0f)
	                .build();
        }
    }

    /**
     * Restarts the camera.
     */
    @Override
    protected void onResume() {
        super.onResume();

        startCameraSource();
    }

    /**
     * Stops the camera.
     */
    @Override
    protected void onPause() {
        super.onPause();
        mPreview.stop();
    }

    /**
     * Releases the resources associated with the camera source, the associated detector, and the
     * rest of the processing pipeline.
     */
    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mCameraSource != null) {
            mCameraSource.release();
        }
    }

    /**
     * Callback for the result from requesting permissions. This method
     * is invoked for every call on {@link #requestPermissions(String[], int)}.
     * <p>
     * <strong>Note:</strong> It is possible that the permissions request interaction
     * with the user is interrupted. In this case you will receive empty permissions
     * and results arrays which should be treated as a cancellation.
     * </p>
     *
     * @param requestCode  The request code passed in {@link #requestPermissions(String[], int)}.
     * @param permissions  The requested permissions. Never null.
     * @param grantResults The grant results for the corresponding permissions
     *                     which is either {@link PackageManager#PERMISSION_GRANTED}
     *                     or {@link PackageManager#PERMISSION_DENIED}. Never null.
     * @see #requestPermissions(String[], int)
     */
    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        if (requestCode != RC_HANDLE_CAMERA_PERM) {
            Log.d(TAG, "Got unexpected permission result: " + requestCode);
            super.onRequestPermissionsResult(requestCode, permissions, grantResults);
            return;
        }

        if (grantResults.length != 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            Log.d(TAG, "Camera permission granted - initialize the camera source");
            // we have permission, so create the camerasource
            createCameraSource();
            return;
        }

        Log.e(TAG, "Permission not granted: results len = " + grantResults.length +
                " Result code = " + (grantResults.length > 0 ? grantResults[0] : "(empty)"));

        DialogInterface.OnClickListener listener = new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int id) {
                finish();
            }
        };

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Face Tracker sample")
                .setMessage(R.string.no_camera_permission)
                .setPositiveButton(R.string.ok, listener)
                .show();
    }

    //==============================================================================================
    // Camera Source Preview
    //==============================================================================================

    /**
     * Starts or restarts the camera source, if it exists.  If the camera source doesn't exist yet
     * (e.g., because onResume was called before the camera source was created), this will be called
     * again when the camera source is created.
     */
    private void startCameraSource() {

        // check that the device has play services available.
        int code = GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(
                getApplicationContext());
        if (code != ConnectionResult.SUCCESS) {
            Dialog dlg =
                    GoogleApiAvailability.getInstance().getErrorDialog(this, code, RC_HANDLE_GMS);
            dlg.show();
        }

        if (mCameraSource != null) {
            try {
                mPreview.start(mCameraSource, mGraphicOverlay);
            } catch (IOException e) {
                Log.e(TAG, "Unable to start camera source.", e);
                mCameraSource.release();
                mCameraSource = null;
            }
        }
    }

    //==============================================================================================
    // Graphic Face Tracker
    //==============================================================================================

    /**
     * Factory for creating a face tracker to be associated with a new face.  The multiprocessor
     * uses this factory to create face trackers as needed -- one for each individual.
     */
    private class GraphicFaceTrackerFactory implements MultiProcessor.Factory<Face> {
        @Override
        public Tracker<Face> create(Face face) {
            return new GraphicFaceTracker(mGraphicOverlay);
        }
    }

    /**
     * Face tracker for each detected individual. This maintains a face graphic within the app's
     * associated face overlay.
     */
    private class GraphicFaceTracker extends Tracker<Face> {
        private GraphicOverlay mOverlay;
        private FaceGraphic mFaceGraphic;

        GraphicFaceTracker(GraphicOverlay overlay) {
            mOverlay = overlay;
            Bitmap bitmap = BitmapFactory.decodeResource(getResources(),R.drawable.smiles);
            Bitmap mBitmap = Bitmap.createScaledBitmap(bitmap, 50,50, false);
            mFaceGraphic = new FaceGraphic(overlay, mBitmap);
        }

        /**
         * Start tracking the detected face instance within the face overlay.
         */
        @Override
        public void onNewItem(int faceId, Face item) {
            mFaceGraphic.setId(faceId);
        }

        /**
         * Update the position/characteristics of the face within the overlay.
         */
        @Override
        public void onUpdate(FaceDetector.Detections<Face> detectionResults, Face face) {
            mOverlay.add(mFaceGraphic);
            mFaceGraphic.updateFace(face);
        }

        /**
         * Hide the graphic when the corresponding face was not detected.  This can happen for
         * intermediate frames temporarily (e.g., if the face was momentarily blocked from
         * view).
         */
        @Override
        public void onMissing(FaceDetector.Detections<Face> detectionResults) {
            mOverlay.remove(mFaceGraphic);
        }

        /**
         * Called when the face is assumed to be gone for good. Remove the graphic annotation from
         * the overlay.
         */
        @Override
        public void onDone() {
            mOverlay.remove(mFaceGraphic);
        }
    }


    /* -------------------------------------------------------------------------------------
     *
     * -------------------------------------------------------------------------------------
     */
    protected void saveFile(String key, String content){
        try {
            FileOutputStream fileout=openFileOutput(key, MODE_PRIVATE);
            OutputStreamWriter outputWriter=new OutputStreamWriter(fileout);
            outputWriter.write(content.toString());
            outputWriter.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    protected void saveBitmap(Bitmap bmp){
        FileOutputStream out = null;
        String filename = "/Download/savedMasked.png";
        try {
            Log.i("Save Image: ", filename);
            out = new FileOutputStream(filename);
            bmp.compress(Bitmap.CompressFormat.PNG, 100, out); // bmp is your Bitmap instance
            // PNG is a lossless format, the compression factor (100) is ignored
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            try {
                if (out != null) {
                    out.close();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    protected String readFile(String filename){
        String s="";
        //reading text from file
        try {
            FileInputStream fileIn=openFileInput(filename);
            InputStreamReader InputRead= new InputStreamReader(fileIn);
            char[] inputBuffer= new char[100];
            int charRead;
            while ((charRead=InputRead.read(inputBuffer))>0) {
                // char to string conversion
                String readstring=String.copyValueOf(inputBuffer,0,charRead);
                s +=readstring;
            }
            InputRead.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return s;
    }

}
