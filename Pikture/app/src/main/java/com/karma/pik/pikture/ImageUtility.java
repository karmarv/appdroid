package com.karma.pik.pikture;

import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.MediaStore;
import android.util.Log;

import java.io.FileDescriptor;
import java.io.IOException;


public class ImageUtility {

    public static String getRealPathFromURI(Context context, Uri contentUri) {
        Cursor cursor = null;
        String path = "";
        try {
            Log.w("Get real path for : ", contentUri.getPath());
            String[] proj = { MediaStore.Images.Media.DATA };
            cursor = context.getContentResolver().query(contentUri,  proj, null, null, null);
            int column_index = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA);
            cursor.moveToFirst();
            path = cursor.getString(column_index);
        }catch (Exception e){
            Log.w("Error: ", e.getMessage());
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
        return path;
    }

}
