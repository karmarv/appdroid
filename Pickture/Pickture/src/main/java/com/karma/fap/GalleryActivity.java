package com.karma.fap;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

public class GalleryActivity extends AppCompatActivity {

    private static final String GALLERY_PARAM_KEY = "param";

    private static int RESULT_LOAD_IMG = 1;
    private static int MY_PERMISSIONS_REQUEST_WRITE_STORAGE = 2;
    String imgDecodableString;

    public static void start(Context context, String param) {
        Intent intent = new Intent(context, GalleryActivity.class);
        intent.putExtra(GALLERY_PARAM_KEY, param);
        context.startActivity(intent);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_gallery);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        // Get the parameter
        Intent intent = this.getIntent();
        String paramValue = intent.getStringExtra(GALLERY_PARAM_KEY);

        FloatingActionButton fab = (FloatingActionButton) findViewById(R.id.fab);
        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Snackbar.make(view, "Replace with your own action", Snackbar.LENGTH_LONG)
                        .setAction("Action", null).show();
            }
        });
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
    }



    public void onClick(View view) {
        TextView textView = (TextView) findViewById(R.id.text_view_gallery);
        // Toggle logic
        if (textView.getCurrentTextColor() == Color.RED) {
            textView.setTextColor(Color.GREEN);
            textView.setText("Lets Go");
            Snackbar.make(view, "Opening Gallery", Snackbar.LENGTH_LONG)
                    .setAction("Action", null).show();
            loadImageFromGallery(view);
        } else {
            textView.setTextColor(Color.RED);
            textView.setText("Sure !!!");
        }
    }


    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        try {
            // When an Image is picked
            if (requestCode == RESULT_LOAD_IMG && resultCode == RESULT_OK && null != data) {
                // Get the Image from data
                Uri selectedImage = data.getData();
                String[] filePathColumn = { MediaStore.Images.Media.DATA };

                TextView textView = (TextView) findViewById(R.id.text_view_gallery);
                textView.setTextColor(Color.BLUE);
                textView.setText("Img Uri:"+selectedImage.getLastPathSegment().toString());

                // Get the cursor
                Cursor cursor = getContentResolver().query(selectedImage,
                        filePathColumn, null, null, null);
                // Move to first row
                cursor.moveToFirst();
                int columnIndex = cursor.getColumnIndex(filePathColumn[0]);
                imgDecodableString = cursor.getString(columnIndex);
                cursor.close();
                ImageView imgView = (ImageView) findViewById(R.id.image_view_gallery);
                // Set the Image in ImageView after decoding the String
                imgView.setImageBitmap(BitmapFactory.decodeFile(imgDecodableString));

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

}
