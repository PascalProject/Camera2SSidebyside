package puf.com.camera2gvr;

import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.SurfaceTexture;
import android.graphics.drawable.Drawable;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.support.v4.content.ContextCompat;
import android.util.Log;
import android.util.Size;
import android.view.ContextMenu;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.Surface;

import com.google.vr.sdk.base.FieldOfView;
import com.google.vr.sdk.base.GvrActivity;
import com.google.vr.sdk.base.GvrView;
import com.google.vr.sdk.base.HeadTransform;
import com.google.vr.sdk.base.ScreenParams;

import java.util.Arrays;

import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.RelativeLayout;
import android.widget.Toast;
import android.view.MotionEvent;
import android.view.View;
import android.widget.ImageView;


/**
 *
 */
public class MainActivity extends GvrActivity implements GvrViewStereoRendererService.GvrRendererEvents {

    private static final String TAG = "MainActivity";
    private static final int REQUEST_CAMERA_PERMISSION_RESULT = 0;

    private GvrView cameraView;
    private GvrViewStereoRendererService gvrViewStereoRendererService;

    private CameraDevice camera;
    private CameraManager cameraManager;
    private CaptureRequest.Builder captureBuilder;
    private CameraCaptureSession cameraSession;
    private SurfaceTexture surfaceTexture;
    private StreamConfigurationMap configs;
    private Size previewSize;

    /*Touch Screen*/
    private RelativeLayout relativeLayout;

    /*Insert Object*/
    private OverlayView overlayView;

    /*360 degree*/
    private ScreenParams screenParams;
    private FieldOfView fov;
    private float pixelsPerDegree;
    private Drawable icon;

    /**
     * A callback objects for receiving updates about the state of a camera device.
     * A callback instance must be provided to the openCamera below
     * openCamera(String, CameraDevice.StateCallback, Handler) method to open a camera device.
     */
    private CameraDevice.StateCallback stateCallback = new CameraDevice.StateCallback() {

        /**
         * The method called when a camera device has finished opening.
         * @param cameraDevice
         */
        @Override
        public void onOpened(CameraDevice cameraDevice) {
            camera = cameraDevice ;
            startPreview();
        }

        /**
         * The method called when a camera device has encountered a serious error.
         * @param cameraDevice
         */
        @Override
        public void onDisconnected(CameraDevice cameraDevice) {
            //Do nothing - this application never close camera :)
        }

        @Override
        public void onError(CameraDevice cameraDevice, int i) {
            //Do nothing - we don't want camera error :)
        }
    };


    private void startPreview() {

        //check camera error
        if (camera == null) {
            Log.e(TAG, "preview failed");
            return;
        }

        Surface surface = new Surface(surfaceTexture);

        try {
            //use CaptureRequest.Builder to change intent Camera Preview
            captureBuilder = camera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
        }

        catch (CameraAccessException e) {
            e.printStackTrace();
        }

        /**
         * Add a surface to the list of targets for this request
         * The Surface added must be one of the surfaces included in the most recent call to
         * createCaptureSession(List, CameraCaptureSession.StateCallback, Handler)
         * when the request is given to the camera device.
         *
         * @Link : https://developer.android.com/reference/android/hardware/camera2/CaptureRequest.Builder.html
         */
        captureBuilder.addTarget(surface);

        try {
            camera.createCaptureSession(Arrays.asList(surface),
                    //A callback object for receiving updates about the state of a camera capture session.
                    new CameraCaptureSession.StateCallback() {
                        @Override
                        public void onConfigured(CameraCaptureSession session) {
                            cameraSession = session;
                            updatePreview();
                        }

                        @Override
                        public void onConfigureFailed(CameraCaptureSession cameraCaptureSession) {
                            Log.e(TAG, "onConfigureFailed");
                        }
                    }, null);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    private void updatePreview() {
        if (camera == null) {
            Log.e(TAG, "updatePreivew error, return");
        }

        /**
         * Use CaptureRequest.Builder to:
         * auto-exposure, auto-white-balance, auto-focus
         */
        captureBuilder.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO);

        /**
         * Create a loop for preview camera
         * Thanks to HandlerThread the application alway rendering the camera preview
         * And the application can't break
         */
        HandlerThread thread = new HandlerThread("CameraPreview");
        thread.start();
        Handler backgroundHandler = new Handler(thread.getLooper());

        try {
            cameraSession.setRepeatingRequest(captureBuilder.build(), null, backgroundHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    //int permissionCheck = ContextCompat.checkSelfPermission(this, android.Manifest.permission.CAMERA);
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        loadActivity(0, icon);
    }

    private void loadActivity(int blinding, Drawable icon){
        setContentView(R.layout.activity_main);
        cameraView = (GvrView) findViewById(R.id.camera_view);

        //Rendering the the GvrView To Application
        setGvrView(cameraView);

        //this class we create for rendering the texture from camera yo GvrView
        gvrViewStereoRendererService = new GvrViewStereoRendererService(cameraView, this, blinding);


        /**
         * A system service manager for detecting, characterizing, and connecting to CameraDevices.
         * @Link : https://developer.android.com/reference/android/hardware/camera2/CameraManager.html
         */
        cameraManager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);

        /**
         * Using Overlay View
         * In this view, we cant input a Text or an object.
         */
        overlayView = (OverlayView)findViewById(R.id.overlay);
        //icon = getResources().getDrawable(R.drawable.cube,null);
        this.icon = icon;
        overlayView.addContent("", this.icon);

        /**
         * Feature 360 degree
         */
        screenParams = cameraView.getHeadMountedDisplay().getScreenParams();
        fov = cameraView.getHeadMountedDisplay().getGvrViewerParams().getLeftEyeMaxFov();
        overlayView.calcVirtualWidth(cameraView);
    }

    /*
    * We Override all of activity of Main Activity
    * Because we have to import the function handle the Camera 2 Api in this Activity
    */
    @Override
    protected void onResume() {
        super.onResume();
        cameraView.onResume(); //here
    }

    @Override
    protected void onPause() {
        super.onPause();
        cameraView.onPause(); //here
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        //here not ~^0_0^~
    }

    @Override
    public void onSurfaceTextureCreated(SurfaceTexture surfaceTexture) {
        this.surfaceTexture = surfaceTexture;

        /*
        * The request permission is very important
        * If you don't have that the Camera 2 api cannot open the Camera
        * You can't not use function openCamera() below without the request
        * And the output is a Gvr View empty
        */
        requestPermissions(new String[] {android.Manifest.permission.CAMERA}, REQUEST_CAMERA_PERMISSION_RESULT);

        openCamera();
    }

    /**
     * After we allow the Permission the application try to open camera thanks to this
     * @Output: Camera open.
     * @Error: Cannot connect camera
     *        |Cannot have permission
     *        |Device don't have camera
     *        |...
     */
    private void openCamera() {
        try {

            /*The ID camera
            * if ID = 0 => back camera
            * if ID = 1 => front camera
            */
            String cameraId = cameraManager.getCameraIdList()[0];

            /**Get camera preview stream
            * The doc below have detail about CameraCharacteristics and StreamConfigurationMap
            * @Doc : https://developer.android.com/reference/android/hardware/camera2/params/StreamConfigurationMap
            */
            CameraCharacteristics characteristics = cameraManager.getCameraCharacteristics(cameraId);
            configs = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
            previewSize = configs.getOutputSizes(SurfaceTexture.class)[0];

            /*
            * /!\ Be careful if we don't check permission we cannot open the camera
            * Because in android 6.0 + we have to check permission if we want use the device function
            */
            if(ContextCompat.checkSelfPermission(this, android.Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
                /*Open the camera
                * the camera preview generate thanks to CameraCharacteristics and StreamConfigurationMap
                */
                cameraManager.openCamera(cameraId, stateCallback, cameraView.getHandler());
        }

        /*
        * CameraAccessException is thrown if a camera device could not be queried or opened by the CameraManager
        * In Camera 2 Api the camera trust only onpen by the CameraManager
        * If the connection to an opened CameraDevice is no longer valid it will thrown on this exception
        */
        catch (CameraAccessException e) {
            e.printStackTrace();
        }

        /*
        * If the camera do not the permission then this exception will thrown the cause
        */
        catch (SecurityException e) {
            e.printStackTrace();
        }
    }

    /*Load Menu*/

    @Override
    public boolean onTouchEvent(MotionEvent event) {

        if(event.getAction() == MotionEvent.ACTION_UP) {
            Toast.makeText(getApplicationContext(), "Blinding Protanomaly", Toast.LENGTH_SHORT).show();
            // TODO Auto-generated method stub
            relativeLayout = (RelativeLayout) findViewById(R.id.main_layout);

            //this.registerForContextMenu(cameraView);
            registerForContextMenu(relativeLayout);
            relativeLayout.showContextMenu();
        }
        return super.onTouchEvent(event);
    }

    @Override
    public void onCreateContextMenu(ContextMenu menu, View v, ContextMenu.ContextMenuInfo menuInfo) {
        super.onCreateContextMenu(menu, v, menuInfo);
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.menu, menu);
    }
    // Hàm sử lý sự kiện khi click vào mỗi item
    @Override
    public boolean onContextItemSelected(MenuItem item) {

        overlayView = (OverlayView)findViewById(R.id.overlay);
        icon = null;

        AdapterView.AdapterContextMenuInfo info = (AdapterView.AdapterContextMenuInfo) item.getMenuInfo();
        switch (item.getItemId()) {
            case R.id.mnSideBySide:
                Toast.makeText(getApplicationContext(),"Side By Side", Toast.LENGTH_SHORT).show();
                loadActivity(0,null);

                return true;

            case R.id.mnInsertObject:
                Toast.makeText(getApplicationContext(),"Insert Object", Toast.LENGTH_SHORT).show();
                loadActivity(0,getResources().getDrawable(R.drawable.cube,null));

                return true;
            case R.id.mnHandleBlindingDeuteranomaly:
                Toast.makeText(getApplicationContext(),"Blinding Deuteranomaly", Toast.LENGTH_SHORT).show();
                loadActivity(1,null);
                return true;

            case R.id.mnHandleBlindingProtanomaly:
                Toast.makeText(getApplicationContext(),"Blinding Protanomaly", Toast.LENGTH_SHORT).show();
                loadActivity(2,null);

                return true;
            default:
                return super.onContextItemSelected(item);
        }

    }

    /*360 degree feature*/
    public void onNewFrame(HeadTransform headTransform){
        final float[] angles = new float[3];
        headTransform.getEulerAngles(angles,0);
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                overlayView.setHeadYaw(angles[1]);
            }
        });
    }
}
