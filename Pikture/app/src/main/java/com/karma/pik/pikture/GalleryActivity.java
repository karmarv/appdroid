package com.karma.pik.pikture;

import android.Manifest;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.database.Cursor;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.support.annotation.AnyRes;
import android.support.annotation.NonNull;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;

/**
 * Shows the Gallery with a grid of images
 * Also provides a folder selector for images in the file system
 *
 */
public class GalleryActivity extends AppCompatActivity {

    private static final String GALLERY_PARAM_KEY = "param";
    private String imgDecodableString = "";

    private static int RESULT_LOAD_IMG = 1;
    private static int MY_PERMISSIONS_REQUEST_WRITE_STORAGE = 1;
    private static Uri selectedImage;

    /**
     * On load of the activity
     *
     * @param savedInstanceState
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        imgDecodableString = "";
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_gallery);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        if(!checkIfAlreadyhavePermission()){
            requestpermission();
        }else {
            // Image Adapter to populate the Grid
            final ImageAdapter imageAdapter = new ImageAdapter(this);
            GridView gridview = (GridView) findViewById(R.id.gridview);
            gridview.setAdapter(imageAdapter);

            gridview.setOnItemClickListener(new AdapterView.OnItemClickListener() {
                public void onItemClick(AdapterView<?> parent, View v,
                                        int position, long id) {
                    //Toast.makeText(this, position, Toast.LENGTH_LONG).show();
                    File f = (File) imageAdapter.getItem(position);

                    Log.w("Selected File ", "" + f.getAbsolutePath());

                    // Set the result for the calling intent
                    Intent intent = new Intent();
                    intent.putExtra("profile_image", f.getAbsolutePath());
                    setResult(RESULT_OK, intent);

                    Log.w("Prev item ", "" + selectedImage);
                    finish();
                }
            });
        }
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
    }

    public String getURLForResource (int resourceId) {
        return Uri.parse("android.resource://"+R.class.getPackage().getName()+"/drawable/" +resourceId).toString();
    }


    public void onBrowseClick(View view) {
        Snackbar.make(view, "Opening Gallery", Snackbar.LENGTH_LONG)
                .setAction("Action", null).show();
        TextView textView = (TextView) findViewById(R.id.text_view_gallery);
        loadImageFromGallery(view);
        textView.setTextColor(Color.GREEN);
        textView.setText("Go !! Set profile now.");
    }

    /**
     * When the Files activity finished this event is raised
     *
     * @param requestCode
     * @param resultCode
     * @param data
     */
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        try {
            // When an Image is picked
            if (requestCode == RESULT_LOAD_IMG && resultCode == RESULT_OK && null != data) {
                // Get the Image from data
                selectedImage = data.getData();
                String[] filePathColumn = { MediaStore.Images.Media.DATA };
                TextView textView = (TextView) findViewById(R.id.text_view_gallery);
                textView.setTextColor(Color.BLUE);
                textView.setText("Img Uri:"+selectedImage.getLastPathSegment().toString());

                // Get the cursor and query the image
                Cursor cursor = getContentResolver().query(selectedImage,
                        filePathColumn, null, null, null);
                // Move to first row
                cursor.moveToFirst();
                int columnIndex = cursor.getColumnIndex(filePathColumn[0]);
                imgDecodableString = cursor.getString(columnIndex);
                cursor.close();

                // Set the result for the calling intent
                String realpath = ImageUtility.getRealPathFromURI(this,selectedImage);
                Intent intent = new Intent();
                intent.putExtra("profile_image", realpath);
                setResult(RESULT_OK, intent);
                finish();
            } else {
                Toast.makeText(this, "You haven't picked Image",
                        Toast.LENGTH_LONG).show();
            }
        } catch (Exception e) {
            Toast.makeText(this, "Something went wrong", Toast.LENGTH_LONG)
                    .show();
        }

    }

    /**
     * Load image gallery intent
     */
    public void loadImageFromGallery(View view) {
        if(!checkIfAlreadyhavePermission()){
            requestpermission();
        }else {
            if(view != null)
                Snackbar.make(view, "Permissions Available", Snackbar.LENGTH_LONG)
                        .setAction("Action", null).show();
            // Create intent to Open Image applications like Gallery, Google Photos
            Intent galleryIntent = new Intent(Intent.ACTION_PICK,
                    android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
            // Start the Intent
            startActivityForResult(galleryIntent, RESULT_LOAD_IMG);
        }
    }


    // Permissions
    private boolean checkIfAlreadyhavePermission() {
        int result = ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE);
        Log.w("My App Permissions: ", ""+result);
        return result == PackageManager.PERMISSION_GRANTED;
    }

    private void requestpermission(){
        // No explanation needed; request the permission
        ActivityCompat.requestPermissions(this,
                new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},
                MY_PERMISSIONS_REQUEST_WRITE_STORAGE);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String permissions[], int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        switch (requestCode) {
            case 1: {
                if (grantResults.length > 0
                        && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    loadImageFromGallery(null);
                } else {
                    Toast.makeText(this, "Please give your permission.", Toast.LENGTH_LONG).show();
                }
                break;
            }
        }
    }

    public class SingleMediaScanner implements MediaScannerConnection.MediaScannerConnectionClient {

        private MediaScannerConnection mMs;
        private File mFile;

        public SingleMediaScanner(Context context, File f) {
            mFile = f;
            mMs = new MediaScannerConnection(context, this);
            mMs.connect();
        }

        public void onMediaScannerConnected() {
            mMs.scanFile(mFile.getAbsolutePath(), null);
        }

        public void onScanCompleted(String path, Uri uri) {
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setData(uri);
            startActivity(intent);
            mMs.disconnect();
        }
    }
}
