package edu.ucsb.ece.ece150.maskme;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.support.v4.content.res.ResourcesCompat;
import android.util.Log;
import android.util.SparseArray;

import com.google.android.gms.vision.face.Face;
import com.google.android.gms.vision.face.Landmark;

public class MaskedImageView extends android.support.v7.widget.AppCompatImageView {

    private enum MaskType {
        NOMASK, FIRST, SECOND
    }

    private SparseArray<Face> faces = null;
    private MaskType maskType = MaskType.NOMASK;
    Paint mPaint = new Paint();
    private Bitmap mBitmap;
    private Bitmap mask1Bitmap;

    private Matrix transform;

    public MaskedImageView(Context context) {
        super(context);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        // 1. Main background image that is captured
        mBitmap = ((BitmapDrawable) getDrawable()).getBitmap();
        if(mBitmap == null){
            return;
        }
        double viewWidth = canvas.getWidth();
        double viewHeight = canvas.getHeight();
        double imageWidth = mBitmap.getWidth();
        double imageHeight = mBitmap.getHeight();
        double scale = Math.min(viewWidth / imageWidth, viewHeight / imageHeight);

        drawBitmap(canvas, scale);

        // 2. Load Mask image as a bitmap
        Drawable mask1Drawable = ResourcesCompat.getDrawable(getContext().getResources(), R.drawable.icon, null);
        mask1Bitmap = ((BitmapDrawable) mask1Drawable).getBitmap();
        // Create a matrix to do rotation
        transform = new Matrix();

        switch (maskType){
            case FIRST:
                drawFirstMaskOnCanvas(canvas, scale);
                break;
            case SECOND:
                drawSecondMaskOnCanvas(canvas, scale);
                break;
        }
    }

    protected void drawFirstMask(SparseArray<Face> faces){
        this.faces = faces;
        this.maskType = MaskType.FIRST;
        this.invalidate();
    }

    protected void drawSecondMask(SparseArray<Face> faces){
        this.faces = faces;
        this.maskType = MaskType.SECOND;
        this.invalidate();
    }

    private void drawBitmap(Canvas canvas, double scale) {
        double imageWidth = mBitmap.getWidth();
        double imageHeight = mBitmap.getHeight();

        Rect destBounds = new Rect(0, 0, (int)(imageWidth * scale), (int)(imageHeight * scale));
        canvas.drawBitmap(mBitmap, null, destBounds, null);
    }

    private void drawFirstMaskOnCanvas(Canvas canvas, double scale) {

        // TODO: Draw first type of mask on the static photo
        // 1. set properties of mPaint
        mPaint.setColor(Color.GREEN);
        mPaint.setStyle(Paint.Style.STROKE);
        mPaint.setStrokeWidth(5);

        // 2. get positions of faces and draw masks on faces.
        // 2. get positions of faces and draw masks on faces.
        Log.i("MaskImgView: ","Faces: "+ faces.size());
        for (int i = 0; i < faces.size(); ++i) {
            Face face = faces.valueAt(i);
            int elx=0, ely=0, erx=0, ery=0;
            for (Landmark landmark : face.getLandmarks()) {
                if(landmark.getType() == 10) {
                    erx = (int) (landmark.getPosition().x * scale);
                    ery = (int) (landmark.getPosition().y * scale);
                    Log.i("MaskImgView: ", "Draw at (" + erx + ", " + ery + "), LandmarkType:" + landmark.getType());
                    canvas.drawCircle(erx, ery, 10, mPaint);
                }else if(landmark.getType() == 4){
                    elx = (int) (landmark.getPosition().x * scale);
                    ely = (int) (landmark.getPosition().y * scale);
                    Log.i("MaskImgView: ", "Draw at (" + elx + ", " + ely + "), LandmarkType:" + landmark.getType());
                    canvas.drawCircle(elx, ely, 10, mPaint);
                }
            }
            drawMask(canvas,elx, ely, erx, ery, MaskType.FIRST, scale, face.getEulerZ());
        }
    }


    private void drawMask(Canvas canvas, int elx , int ely, int erx , int ery, MaskType maskType, double scale, float faceAngle){
        Log.i("MaskImgView: ", " Draw "+maskType.name());
        Matrix tranform = new Matrix();
        RectF destBounds = new RectF(elx+30 , ely-60 , erx-30, ery+60);
        Log.i("MaskImgView: ", "Before Draw "+destBounds.toString());
        tranform.setRotate(faceAngle, destBounds.centerX(), destBounds.centerY());
        tranform.mapRect(destBounds);
        Log.i("MaskImgView: ", "after Draw "+destBounds.toString());
        canvas.drawBitmap( mask1Bitmap, null, destBounds, null );
    }



    private void drawEyeMask(Canvas canvas, Landmark eyeLMark, Landmark eyeRMark, float angle, double scale){
        Log.i("MaskImgView: ", "Face Angle: "+ angle);
        Log.i("MaskImgView: ", "Eye Left(" + eyeLMark.getPosition().x + ", " + eyeLMark.getPosition().y + "), LandmarkType:" + eyeLMark.getType());
        Log.i("MaskImgView: ", "Eye Righ(" + eyeRMark.getPosition().x + ", " + eyeRMark.getPosition().y + "), LandmarkType:" + eyeRMark.getType());
        float mwid = 10;
        RectF dst = new RectF((eyeLMark.getPosition().x-mwid),
                              (eyeLMark.getPosition().y+mwid),
                              (eyeRMark.getPosition().x+mwid),
                              (eyeRMark.getPosition().y-mwid));
        Log.i("MaskImgView: ", "Before:"+dst.toString());
        // This is to rotate about the Rectangles center
        /*
        transform.setRotate(angle, ( eyeLMark.getPosition().x + eyeRMark.getPosition().x)/2,
                                   ( eyeLMark.getPosition().y + eyeRMark.getPosition().y)/2);
        transform.mapRect(dst);
        */
        Log.i("MaskImgView: ", "After :"+dst.toString());
        canvas.drawBitmap( mask1Bitmap, null, dst, null );
    }

    private void drawMask(Canvas canvas, int elx , int ely, int erx , int ery, MaskType maskType, double scale){
        Rect destBounds = new Rect(elx , ely+((Math.abs(elx-erx))/4) , erx, ery+((Math.abs(elx-erx))/4));
        canvas.drawBitmap( mask1Bitmap, null, destBounds, null );
    }

    private void drawSecondMaskOnCanvas( Canvas canvas, double scale ) {
        // TODO: Draw second type of mask on the static photo
        // 1. set properties of mPaint
        // 2. get positions of faces and draw masks on faces.

    }



    public void noFaces() {
        faces = null;
    }

    public void reset() {
        faces = null;
        setImageBitmap(null);
    }
}
