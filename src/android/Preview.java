package com.cordovaplugincamerapreview;

import android.app.Activity;
import android.content.Context;
import android.graphics.ImageFormat;
import android.graphics.Point;
import android.graphics.Rect;
import android.graphics.YuvImage;
import android.hardware.Camera;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Display;
import android.view.WindowManager;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.View;
import android.widget.RelativeLayout;
import org.apache.cordova.LOG;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;

class Preview extends RelativeLayout implements SurfaceHolder.Callback {
  private final String TAG = "Preview";

  CustomSurfaceView mSurfaceView;
  SurfaceHolder mHolder;
  Camera.Size mPreviewSize;
  List<Camera.Size> mSupportedPreviewSizes;
  Camera mCamera;
  int cameraId;
  int displayOrientation;
  int facing = Camera.CameraInfo.CAMERA_FACING_BACK;
  int viewWidth;
  int viewHeight;

  Context ctx_;
  Preview(Context context) {
    super(context);

    ctx_=context;
    mSurfaceView = new CustomSurfaceView(context);
    addView(mSurfaceView);

    requestLayout();

    // Install a SurfaceHolder.Callback so we get notified when the
    // underlying surface is created and destroyed.
    mHolder = mSurfaceView.getHolder();
    mHolder.addCallback(this);
    mHolder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);
  }

  public void setCamera(Camera camera, int cameraId) {
    mCamera = camera;
    this.cameraId = cameraId;

    if (camera != null) {
      mSupportedPreviewSizes = mCamera.getParameters().getSupportedPreviewSizes();
      setCameraDisplayOrientation();

      List<String> mFocusModes = mCamera.getParameters().getSupportedFocusModes();

      Camera.Parameters params = mCamera.getParameters();
      if (mFocusModes.contains("continuous-picture")) {
        params.setFocusMode(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE);
      } else if (mFocusModes.contains("continuous-video")){
        params.setFocusMode(Camera.Parameters.FOCUS_MODE_CONTINUOUS_VIDEO);
      } else if (mFocusModes.contains("auto")){
        params.setFocusMode(Camera.Parameters.FOCUS_MODE_AUTO);
      }
      mCamera.setParameters(params);
    }
  }

  public int getDisplayOrientation() {
    return displayOrientation;
  }
  public int getCameraFacing() {
    return facing;
  }

  public void printPreviewSize(String from) {
    Log.d(TAG, "printPreviewSize from " + from + ": > width: " + mPreviewSize.width + " height: " + mPreviewSize.height);
  }
  public void setCameraPreviewSize() {
    if (mCamera != null) {
      Camera.Parameters parameters = mCamera.getParameters();
      parameters.setPreviewSize(mPreviewSize.width, mPreviewSize.height);
      mCamera.setParameters(parameters);
    }
  }
  private void setCameraDisplayOrientation() {
    Camera.CameraInfo info = new Camera.CameraInfo();
    int rotation = ((Activity) getContext()).getWindowManager().getDefaultDisplay().getRotation();
    int degrees = 0;
    DisplayMetrics dm = new DisplayMetrics();

    Camera.getCameraInfo(cameraId, info);
    ((Activity) getContext()).getWindowManager().getDefaultDisplay().getMetrics(dm);

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
    facing = info.facing;
    if (info.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
      displayOrientation = (info.orientation + degrees) % 360;
      displayOrientation = (360 - displayOrientation) % 360;
    } else {
      displayOrientation = (info.orientation - degrees + 360) % 360;
    }

    Log.d(TAG, "screen is rotated " + degrees + "deg from natural");
    Log.d(TAG, (info.facing == Camera.CameraInfo.CAMERA_FACING_FRONT ? "front" : "back") + " camera is oriented -" + info.orientation + "deg from natural");
    Log.d(TAG, "need to rotate preview " + displayOrientation + "deg");
    mCamera.setDisplayOrientation(displayOrientation);
  }

  public void switchCamera(Camera camera, int cameraId) {
    try {
      setCamera(camera, cameraId);

      Log.d("CameraPreview", "before set camera");

      camera.setPreviewDisplay(mHolder);

      Log.d("CameraPreview", "before getParameters");

      Camera.Parameters parameters = camera.getParameters();

      Log.d("CameraPreview", "before setPreviewSize");

      mSupportedPreviewSizes = parameters.getSupportedPreviewSizes();
      //mPreviewSize = getOptimalPreviewSize(mSupportedPreviewSizes, mSurfaceView.getWidth(), mSurfaceView.getHeight());
      mPreviewSize = getOptimalPreviewSize(mSupportedPreviewSizes);
      parameters.setPreviewSize(mPreviewSize.width, mPreviewSize.height);
      Log.d(TAG, mPreviewSize.width + " " + mPreviewSize.height);

      camera.setParameters(parameters);
    } catch (IOException exception) {
      Log.e(TAG, exception.getMessage());
    }
  }

  @Override
  protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
    // We purposely disregard child measurements because act as a
    // wrapper to a SurfaceView that centers the camera preview instead
    // of stretching it.
    final int width = resolveSize(getSuggestedMinimumWidth(), widthMeasureSpec);
    final int height = resolveSize(getSuggestedMinimumHeight(), heightMeasureSpec);
    setMeasuredDimension(width, height);

    if (mSupportedPreviewSizes != null) {
      //mPreviewSize = getOptimalPreviewSize(mSupportedPreviewSizes, width, height);
      mPreviewSize = getOptimalPreviewSize(mSupportedPreviewSizes);
    }
  }
  

  @Override
  protected void onLayout(boolean changed, int l, int t, int r, int b) {

    if (changed && getChildCount() > 0) {
      final View child = getChildAt(0);

      int width = r - l;
      int height = b - t;

      int previewWidth = width;
      int previewHeight = height;

      if (mPreviewSize != null) {
        previewWidth = mPreviewSize.width;
        previewHeight = mPreviewSize.height;

        if(displayOrientation == 90 || displayOrientation == 270) {
          previewWidth = mPreviewSize.height;
          previewHeight = mPreviewSize.width;
        }

        LOG.d(TAG, "previewWidth:" + previewWidth + " previewHeight:" + previewHeight);
      }

      int nW;
      int nH;
      int top;
      int left;

      float scale = 1f;

      // Center the child SurfaceView within the parent.
      //Jason 19May2021 alter the preview size to fit the optimal screen zie
      //instead of fit it horizontally
      //bcz user complaint abt they see in the preview diff from the one captured
      if (width * previewHeight < height * previewWidth) {
        Log.d(TAG, "center horizontally");
        int scaledChildWidth = (int)((previewWidth * height / previewHeight) * scale);
        nW = width;//(width + scaledChildWidth) / 2;
        nH = (int)(height * scale);
        top = 0;
        left =0;// (width - scaledChildWidth) / 2;
      } else {
        Log.d(TAG, "center vertically");
        int scaledChildHeight = (int) ((previewHeight * width / previewWidth) * scale);
        nW = (int) (width * scale);
        nH = height;//(height + scaledChildHeight) / 2;
        top = 0;//(height - scaledChildHeight) / 2;
        left = 0;
      }
      child.layout(left, top, nW, nH);

      Log.d("layout", "left:" + left);
      Log.d("layout", "top:" + top);
      Log.d("layout", "right:" + nW);
      Log.d("layout", "bottom:" + nH);
    }
  }

  public void surfaceCreated(SurfaceHolder holder) {
    // The Surface has been created, acquire the camera and tell it where
    // to draw.
    try {
      if (mCamera != null) {
        mSurfaceView.setWillNotDraw(false);
        mCamera.setPreviewDisplay(holder);
      }
    } catch (Exception exception) {
      Log.e(TAG, "Exception caused by setPreviewDisplay()", exception);
    }
  }

  public void surfaceDestroyed(SurfaceHolder holder) {
    // Surface will be destroyed when we return, so stop the preview.
    try {
      if (mCamera != null) {
        mCamera.stopPreview();
      }
    } catch (Exception exception) {
      Log.e(TAG, "Exception caused by surfaceDestroyed()", exception);
    }
  }
 


  public Camera.Size getOptimalPreviewSize(List<Camera.Size> sizes)
    {
        final double ASPECT_TOLERANCE = 0.05;
        if( sizes == null )
            return null;

        Camera.Size optimalSize = null;
        double minDiff = Double.MAX_VALUE;

        try{

          //Jason 19May2021 alter the preview size to fit the optimal screen zie
          //this is important to keep the view not zoom in that much!
          Point display_size = new Point();
          Activity activity = (Activity)this.getContext();
          {   
              Display display = activity.getWindowManager().getDefaultDisplay();
              display.getSize(display_size);

              Log.d(TAG, "display_size: " + display_size.x + " x " + display_size.y);
          }
          double targetRatio = calculateTargetRatioForPreview(display_size);
          int targetHeight = Math.min(display_size.y, display_size.x);
          if( targetHeight <= 0 ) {
              targetHeight = display_size.y;
          }
          // Try to find the size which matches the aspect ratio, and is closest match to display height
          for(Camera.Size size : sizes)
          {
              Log.d(TAG, "    supported preview size: " + size.width + ", " + size.height);
              double ratio = (double)size.width / size.height;
              if( Math.abs(ratio - targetRatio) > ASPECT_TOLERANCE )
                  continue;
              if( Math.abs(size.height - targetHeight) < minDiff ) {
                  optimalSize = size;
                  minDiff = Math.abs(size.height - targetHeight);
              }
          }

     

         // Cannot find the one match the aspect ratio, ignore the requirement
         if (optimalSize == null) {
           minDiff = Double.MAX_VALUE;
           for (Camera.Size size : sizes) {
             if (Math.abs(size.height - targetHeight) < minDiff) {
               optimalSize = size;
               minDiff = Math.abs(size.height - targetHeight);
             }
           }
         }


            Log.d(TAG, "chose optimalSize: " + optimalSize.width + " x " + optimalSize.height);
            Log.d(TAG, "optimalSize ratio: " + ((double)optimalSize.width / optimalSize.height));
      }catch (Exception exception) {
        Log.e(TAG, "Exception getOptimalPreviewSize activity get previewsize from screen:", exception);
      }
  
      return optimalSize;
    }


    public boolean isVideo=false;
    public static double targetRatio_global=0.0f;
    private double calculateTargetRatioForPreview(Point display_size)
    {
        double targetRatio = 0.0f;

      try {
        if (mCamera != null) {
          if( isVideo)
          {

          }
          else
          {
            targetRatio = ((double)mCamera.getParameters().getPictureSize().width) / (double)mCamera.getParameters().getPictureSize().height;
            //targetRatio = ((double)display_size.x) / (double)display_size.y;
            targetRatio_global=targetRatio;
          }
        }else{
          if(targetRatio_global!=0){
            targetRatio=targetRatio_global;
          }
        }
      } catch (Exception exception) {
        Log.e(TAG, "Exception caused by setPreviewDisplay()", exception);
        if(targetRatio_global!=0){
          targetRatio=targetRatio_global;
        }
      }

      return targetRatio;
    }

  private Camera.Size getOptimalPreviewSize(List<Camera.Size> sizes, int w, int h) {
    final double ASPECT_TOLERANCE = 0.1;
    double targetRatio = (double) w / h;
    if (displayOrientation == 90 || displayOrientation == 270) {
      targetRatio = (double) h / w;
    }
    Log.d(TAG, "getOptimalPreviewSize optimal preview Camera.Size size ori: w:" + w + " h:" + h);
    if(sizes == null){
      return null;
    }

    Camera.Size optimalSize = null;
    double minDiff = Double.MAX_VALUE;
 
    int targetHeight = h;

    Log.d(TAG, "getOptimalPreviewSize optimal preview Camera.Size minDiff:" + minDiff + " ");
    // Try to find an size match aspect ratio and size
    for (Camera.Size size : sizes) {
      Log.d(TAG, "getOptimalPreviewSize optimal preview Camera.Size size: w:" + size.width + " h:" + size.height);
      double ratio = (double) size.width / size.height;
      Log.d(TAG, "getOptimalPreviewSize optimal preview Camera.Size size: ratio:" + ratio + " " );
      Log.d(TAG, "getOptimalPreviewSize optimal preview Camera.Size size: targetRatio:" + targetRatio + " " );
      Log.d(TAG, "getOptimalPreviewSize optimal preview Camera.Size size: Math.abs(ratio - targetRatio):" + Math.abs(ratio - targetRatio) + " " );
      if (Math.abs(ratio - targetRatio) > ASPECT_TOLERANCE) continue;
      if (Math.abs(size.height - targetHeight) < minDiff) {
        optimalSize = size;
        minDiff = Math.abs(size.height - targetHeight);
      }
    }

    // Cannot find the one match the aspect ratio, ignore the requirement
    if (optimalSize == null) {
      minDiff = Double.MAX_VALUE;
      for (Camera.Size size : sizes) {
        if (Math.abs(size.height - targetHeight) < minDiff) {
          optimalSize = size;
          minDiff = Math.abs(size.height - targetHeight);
        }
      }
    }

    Log.d(TAG, "optimal preview size: w: " + optimalSize.width + " h: " + optimalSize.height);
    return optimalSize;
  }

  public void surfaceChanged(SurfaceHolder holder, int format, int w, int h) {
    if(mCamera != null) {
      try {
        // Now that the size is known, set up the camera parameters and begin
        // the preview.
        mSupportedPreviewSizes = mCamera.getParameters().getSupportedPreviewSizes();
        if (mSupportedPreviewSizes != null) {
          //mPreviewSize = getOptimalPreviewSize(mSupportedPreviewSizes, w, h);
          mPreviewSize = getOptimalPreviewSize(mSupportedPreviewSizes);
        }

        // Context ctx=ctx_;

        // if(mSurfaceView!=null){
        //   ctx=mSurfaceView.getContext();
        // }
        //Jason  19May2021 added for autorotate start
        Camera.Parameters parameters = mCamera.getParameters();
        // Display display = ((WindowManager)ctx.getSystemService(Context.WINDOW_SERVICE)).getDefaultDisplay();
    
        // if(display.getRotation() == Surface.ROTATION_0) {  
        //   parameters.setPreviewSize( mPreviewSize.height,mPreviewSize.width);                           
        //   mCamera.setDisplayOrientation(90);
        // }
    
        // if(display.getRotation() == Surface.ROTATION_90) { 
        //   parameters.setPreviewSize(mPreviewSize.width, mPreviewSize.height);                        
        // }
    
        // if(display.getRotation() == Surface.ROTATION_180) { 
        //   parameters.setPreviewSize( mPreviewSize.height,mPreviewSize.width);            
        // }
    
        // if(display.getRotation() == Surface.ROTATION_270) { 
        //   parameters.setPreviewSize(mPreviewSize.width, mPreviewSize.height);
        //   mCamera.setDisplayOrientation(180);
        // }
        //Jason  19May2021 added for autorotate end

 
        parameters.setPreviewSize(mPreviewSize.width, mPreviewSize.height);
        requestLayout();
        //mCamera.setDisplayOrientation(90);
        mCamera.setParameters(parameters);
        mCamera.startPreview();
      } catch (Exception exception) {
        Log.e(TAG, "Exception caused by surfaceChanged()", exception);

        try{
          Camera.Parameters parameters = mCamera.getParameters();
          parameters.setPreviewSize(mPreviewSize.width, mPreviewSize.height);
          requestLayout();
          //mCamera.setDisplayOrientation(90);
          mCamera.setParameters(parameters);
          mCamera.startPreview();
        }  catch (Exception excc) {
          Log.e(TAG, "Exception caused by inner surfaceChanged()", excc);
        }
      
      }
    }
  }
 

  public void setOneShotPreviewCallback(Camera.PreviewCallback callback) {
    if(mCamera != null) {
      mCamera.setOneShotPreviewCallback(callback);
    }
  }
}
