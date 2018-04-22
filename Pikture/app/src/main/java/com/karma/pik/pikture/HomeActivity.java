package com.karma.pik.pikture;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.support.design.widget.NavigationView;
import android.support.v4.view.GravityCompat;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.ActionBarDrawerToggle;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ImageView;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;

/**
 * Opens up the default Home activity with tjhe profile image
 */
public class HomeActivity extends AppCompatActivity
        implements NavigationView.OnNavigationItemSelectedListener {

    private static int ACTIVITY_CODE_HOME_LOAD_GAL = 0;
    private static String PROFILE_URL_KEY = "profile_image";

    /**
     * On load of the activity
     *
     * @param savedInstanceState
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_home);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        DrawerLayout drawer = (DrawerLayout) findViewById(R.id.drawer_layout);
        ActionBarDrawerToggle toggle = new ActionBarDrawerToggle(
                this, drawer, toolbar, R.string.navigation_drawer_open, R.string.navigation_drawer_close);
        drawer.addDrawerListener(toggle);
        toggle.syncState();

        NavigationView navigationView = (NavigationView) findViewById(R.id.nav_view);
        navigationView.setNavigationItemSelectedListener(this);

        // Load the image on load of the activity
        String profileUrl = readSharedPreferences(PROFILE_URL_KEY);
        String profile = readFile(PROFILE_URL_KEY);
        if(!profile.isEmpty()){
            Log.w("Profile Share Pref Url:", profileUrl);
            Log.w("Profile Data File Url :", profile);
            try
            {
                ImageView imgView = (ImageView) findViewById(R.id.image_view_home);
                // Set the Image in ImageView after decoding the String
                imgView.setImageBitmap(BitmapFactory.decodeFile(profile));
                Log.w("Loading Saved Img: ", profile.toString());
            }
            catch (Exception e){
               Log.w("Error Image Load: "+e.getMessage(), e.toString());
            }
        }
    }

    @Override
    public void onBackPressed() {
        DrawerLayout drawer = (DrawerLayout) findViewById(R.id.drawer_layout);
        if (drawer.isDrawerOpen(GravityCompat.START)) {
            drawer.closeDrawer(GravityCompat.START);
        } else {
            super.onBackPressed();
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.home, menu);
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

    @SuppressWarnings("StatementWithEmptyBody")
    @Override
    public boolean onNavigationItemSelected(MenuItem item) {
        // Handle navigation view item clicks here.
        int id = item.getItemId();

        if (id == R.id.nav_camera) {
            // Handle the camera action
        } else if (id == R.id.nav_gallery) {

        } else if (id == R.id.nav_slideshow) {

        } else if (id == R.id.nav_manage) {

        } else if (id == R.id.nav_share) {

        } else if (id == R.id.nav_send) {

        }

        DrawerLayout drawer = (DrawerLayout) findViewById(R.id.drawer_layout);
        drawer.closeDrawer(GravityCompat.START);
        return true;
    }



    /**
     * Handle the click event of Images and open the Gallery Activity
     * @param view
     */
    public void onClick(View view) {
        openGalleryIntent();
    }
    public void openGalleryIntent(){
        startActivityForResult(new Intent(this, GalleryActivity.class), ACTIVITY_CODE_HOME_LOAD_GAL);
    }

    /**
     * When the Gallery activity finished this event is raised
     *
     * @param requestCode
     * @param resultCode
     * @param data
     */
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        Log.w("Home Result: ",""+resultCode);
        if (requestCode == ACTIVITY_CODE_HOME_LOAD_GAL) {
            if(resultCode == Activity.RESULT_OK){
                if(data.hasExtra(PROFILE_URL_KEY)) {
                    // Get the Image from data
                    String realpath = data.getStringExtra(PROFILE_URL_KEY);
                    ImageView imgView = (ImageView) findViewById(R.id.image_view_home);
                    // Set the Image in ImageView after decoding the String
                    Log.w("Image path: ", realpath);
                    imgView.setImageBitmap(BitmapFactory.decodeFile(realpath));
                    writeSharedPreferences(PROFILE_URL_KEY, realpath.toString());
                    saveFile(PROFILE_URL_KEY, realpath);
                }
            }
            if (resultCode == Activity.RESULT_CANCELED) {
                //Write your code if there's no result
                Log.w("Home Result Cancelled: ","Error");
            }
        }
    }

    /**
     * Save the path of the file
     * for loading image on startup
     *
     * @param key
     * @param content
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

    /**
     * Save information in the shared preferences
     *
     * @param key
     * @param value
     */
    protected void writeSharedPreferences(String key, String value){
        Log.w("Saving Pref: ",key+" : "+value);
        SharedPreferences sharedPref = this.getPreferences(Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPref.edit();
        editor.putString(key, value);
        editor.commit();
    }

    protected String readSharedPreferences(String key){
        SharedPreferences sharedPref = this.getPreferences(Context.MODE_PRIVATE);
        return sharedPref.getString(key, "");
    }

}
