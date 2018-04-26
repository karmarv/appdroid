package com.karma.pik.pikture;

import android.content.Context;
import android.graphics.BitmapFactory;
import android.os.Environment;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

public class ImageAdapter extends BaseAdapter {

    String SCAN_PATH = "/Download/"; // Images/
    File[] allFiles ;

    // references to our images
    public static Integer[] mThumbIds = {
            R.drawable.sample_2, R.drawable.sample_3,
            R.drawable.sample_4, R.drawable.sample_5,
            R.drawable.sample_6, R.drawable.sample_7,
            R.drawable.sample_0, R.drawable.sample_1
    };

    private Context mContext;

    public ImageAdapter(Context c) {
        mContext = c;
        File folder = new File(Environment.getExternalStorageDirectory().getPath()+SCAN_PATH);
        allFiles = folder.listFiles();
        int i = 0;
        if(allFiles != null) {
            Log.w("File Count: ", ""+allFiles.length);
            for (File f : allFiles) {
                i++;
                Log.w("File[" + i + "]: ", "" + f.getAbsolutePath());
            }
        }
        //TODO: recursively walk through all images in folders
    }

    public int getCount() {
        return allFiles.length;
    }

    public Object getItem(int position) {
        return allFiles[position];
    }

    public long getItemId(int position) {
        return 0;
    }

    // create a new ImageView for each item referenced by the Adapter
    public View getView(int position, View convertView, ViewGroup parent) {
        ImageView imageView;
        if (convertView == null) {
            // if it's not recycled, initialize some attributes
            imageView = new ImageView(mContext);
            imageView.setLayoutParams(new ViewGroup.LayoutParams(250, 250));
            imageView.setScaleType(ImageView.ScaleType.CENTER_CROP);
            imageView.setPadding(8, 8, 8, 8);
            //columnWidth = (int) ((getScreenWidth() - ((AppConstant.NUM_OF_COLUMNS + 1) * padding)) / AppConstant.NUM_OF_COLUMNS);
        } else {
            imageView = (ImageView) convertView;
        }
        //imageView.setImageResource(mThumbIds[position]);
        imageView.setImageBitmap(BitmapFactory.decodeFile(allFiles[position].getAbsolutePath()));
        return imageView;
    }

}
