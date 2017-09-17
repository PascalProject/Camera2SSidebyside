package puf.com.camera2gvr;


import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.google.vr.sdk.base.GvrView;
import com.google.vr.sdk.base.ScreenParams;

/**
 * Sai gon 31/08/2017.
 */

public class OverlayView extends LinearLayout {

    /*Separate 2 screen*/
    private final OverlayEye leftEye;
    private final OverlayEye rightEye;

    /*360 degree*/
    int headOffset;
    private float pixelsPerRadian;
    int ckdn;


    public OverlayView(Context context, AttributeSet attributeSet){

        super(context, attributeSet);
        setOrientation(HORIZONTAL);



        /**
        * Test with Text View
        * @Output : Hello Virtual Word! on the screen
        *==============================================
        * TextView textView = new TextView(context, attributeSet);
        * addView(textView);
        * textView.setTextColor(Color.rgb(150,250,180));
        * textView.setText("Hello Virtual Word!");
        * setVisibility(View.VISIBLE);
         * ============================================
        */

        

        LayoutParams params = new LayoutParams(
                LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT, 1.0f);
        params.setMargins(0, 0, 0, 0);

        leftEye = new OverlayEye(context, attributeSet);
        leftEye.setLayoutParams(params);
        addView(leftEye);

        rightEye = new OverlayEye(context, attributeSet);
        rightEye.setLayoutParams(params);
        addView(rightEye);

          /*Show only 1 Overlay View*/
//        OverlayEye eye = new OverlayEye(context, attributeSet);
//        eye.setLayoutParams(params);
//        addView(eye);
//        eye.setColor(Color.rgb(150,250,180));
//        eye.addContent("Hello Virtual World!");

          /*Separte Overlay View*/
        setDepthOffset(0.01f);
        setColor(Color.rgb(150, 255, 180));
        setVisibility(View.VISIBLE);
        //We call set addContent in Main
        //addContent("Insert Object");

    }

    public void addContent(String s, Drawable icon) {
        leftEye.addContent(s, icon);
        rightEye.addContent(s, icon);
    }

    public void setDepthOffset(float offset) {
        leftEye.setDepthOffset(offset);
        rightEye.setDepthOffset(-offset);

    }

    public void setColor(int rgb) {
        leftEye.setColor(rgb);
        rightEye.setColor(rgb);
    }

    public void calcVirtualWidth(GvrView carboard){
        int screenWidth = carboard.getHeadMountedDisplay().getScreenParams().getWidth()/2;
        float fov = carboard.getGvrViewerParams().getLeftEyeMaxFov().getLeft();
        float pixelsPerDegree = screenWidth / fov;
        pixelsPerDegree = (float)(pixelsPerDegree*180/Math.PI);
        ckdn = (int)(pixelsPerDegree * 360.0);
    }

    public void setHeadYaw(float angle) {
        headOffset = (int) (angle * pixelsPerRadian);
        leftEye.setHeadOffset(headOffset);
        rightEye.setHeadOffset(headOffset);
    }

    private class OverlayEye extends ViewGroup{

        private Context context;
        private AttributeSet attributeSet;
        private TextView textView;
        private int textColor;
        private int viewWidth;
        private float depth;
        private ImageView imageView;

        public OverlayEye(Context context, AttributeSet attrs){
            super(context,attrs);
            this.context = context;
            this.attributeSet = attrs;
        }

        public void setColor(int color){
            this.textColor = color;
        }

        public void addContent(String text, Drawable icon){
            textView = new TextView(context,attributeSet);
            textView.setGravity(Gravity.CENTER);
            textView.setX(depth);
            textView.setTextColor(textColor);
            textView.setText(text);
            addView(textView);

            imageView = new ImageView(context,attributeSet);
            imageView.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
            imageView.setAdjustViewBounds(true);
            imageView.setImageDrawable(icon);
            addView(imageView);
        }

        public void setDepthOffset(float offset){
            depth = (int)(offset*viewWidth);
        }

        @Override
        protected void onLayout(boolean	changed, int left, int top,	int	right, int bottom) {
            final int width	= right	- left;
            final int height = bottom - top;
            final float verticalTextPos	= 0.52f;
            float topMargin	= height * verticalTextPos;

            //defaut size
            final float imageSize = 0.3f;

            //position
            final float verticalImageOffset = -0.07f;

            float imageMargin	=(1.0f - imageSize)/2.0f;
            topMargin = (height	* (imageMargin	+ verticalImageOffset));
            float botMargin	= topMargin	+ (height * imageSize);
            imageView.layout(0, (int) topMargin, width, (int) botMargin);

            textView.layout(0, (int) topMargin,	width, bottom);
            viewWidth = width;
        }

        public void setHeadOffset(int headOffset) {
            textView.setX(headOffset + depth);
            imageView.setX(headOffset + depth);
        }
    }
}


