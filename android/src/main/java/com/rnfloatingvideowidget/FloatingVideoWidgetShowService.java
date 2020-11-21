package com.rnfloatingvideowidget;

import android.app.Service;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup.LayoutParams;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.RelativeLayout;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.view.Display;
import android.widget.Toast;
import android.os.Handler;

import androidx.annotation.Nullable;

import android.view.GestureDetector;
import android.view.GestureDetector.SimpleOnGestureListener;

import com.devbrackets.android.exomedia.listener.OnVideoSizeChangedListener;
import com.devbrackets.android.exomedia.ui.widget.VideoView;
import com.devbrackets.android.exomedia.listener.OnErrorListener;
import com.facebook.react.ReactApplication;
import com.facebook.react.ReactInstanceManager;
import com.facebook.react.ReactNativeHost;
import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.ReactContext;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.bridge.ReadableMap;
import com.facebook.react.bridge.WritableMap;
import com.facebook.react.bridge.ReadableArray;
import com.facebook.react.bridge.WritableArray;
import com.facebook.react.modules.core.DeviceEventManagerModule;
import com.rnfloatingvideowidget.R;

import java.util.Date;
import java.util.Map;
import java.util.Random;

import android.graphics.Point;

public class FloatingVideoWidgetShowService extends Service {



    private Handler timeoutHandler = new Handler();
    private static ReadableMap playingVideo = null; // The video currently playing
    private static ReadableArray videoPlaylist = null; // List of videos
    private static int index = 0; // Index of playing video in videoPlaylist
    private static int skipSize = 10; // Index of playing video in videoPlaylist
    private static ReadableMap initData = null;
    private GestureDetector gestureDetector;
    private int videoWidth = 250; // Default width of floating video player
    private int videoHeight = 140; // Default Height of floating video player

    WindowManager windowManager;
    View floatingWindow, floatingView, playerWrapper, overlayView;
    FrameLayout videoContainer;
    VideoView videoView;
    ImageButton increaseSize, decreaseSize, playVideo, pauseVideo;
    WindowManager.LayoutParams params;
    ReactContext reactContext = null;
    
    public FloatingVideoWidgetShowService() {}

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && intent.getAction() != null) {
            switch (intent.getAction()) {

                case "ACTION_CLOSE_WIDGET": {
                    long seek = videoView.getCurrentPosition();
                    videoView.setKeepScreenOn(false);
                    stopSelf();
                    WritableMap args = new Arguments().createMap();
                    args.putInt("index", index);
                    args.putInt("seek", (int) seek);
                    args.putString("type", "close");
                    sendEvent(reactContext, "onClose", args);
                    onDestroy();
                    break;
                }
                case "ACTION_PLAY": {
                    onResume(floatingWindow);
                    break;
                }
                case "ACTION_PAUSE": {
                    onPause(floatingWindow);
                    break;
                }
                case "ACTION_PREV": {
                    onPrev(floatingWindow);
                    break;
                }
                case "ACTION_NEXT": {
                    onNext(floatingWindow);
                    break;
                }
                case "ACTION_SET_VIDEO": {
                    ReadableMap data = Arguments.fromBundle(intent.getBundleExtra("DATA"));
                    initData = data;
                    playingVideo = data.getMap("video");
                    videoPlaylist = data.getArray("videos");
                    index = data.getInt("index");
                    skipSize = data.getInt("skipSize");
                    int Seek = data.getInt("seek");
                    Uri myUri = Uri.parse(playingVideo.getString("url"));
                    videoView.setVideoURI(myUri);
                    videoView.seekTo(Seek);
                    videoView.start();
                    videoView.setKeepScreenOn(true);

                    WritableMap args = Arguments.createMap();
                    args.putString("state", "isOpened");
                    args.putString("url", playingVideo.getString("url"));
                    sendEvent(reactContext, "onOpen", args);
                    break;
                }
            }
        }
        return START_STICKY;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        

        final ReactInstanceManager reactInstanceManager = getReactNativeHost().getReactInstanceManager();
        ReactContext getReactContext = reactInstanceManager.getCurrentReactContext();
        gestureDetector = new GestureDetector(this, new SingleTapConfirm());
        reactContext = getReactContext;
        floatingWindow = LayoutInflater.from(this).inflate(R.layout.floating_widget_layout, null);

        // Define the layout flag according to android version.

        int LAYOUT_FLAG;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            LAYOUT_FLAG = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;
        } else {
            LAYOUT_FLAG = WindowManager.LayoutParams.TYPE_PHONE;
        }
        // Setting layout params for floating video

        params = new WindowManager.LayoutParams(WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT, LAYOUT_FLAG,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE, PixelFormat.TRANSLUCENT);


        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);

        assert windowManager != null;
        windowManager.addView(floatingWindow, params);

        // Define all the video view and its components

        floatingView = floatingWindow.findViewById(R.id.Layout_Expended);
        playerWrapper = floatingWindow.findViewById(R.id.view_wrapper);
        overlayView = floatingWindow.findViewById(R.id.overlay_view);
        videoView = (VideoView) floatingWindow.findViewById(R.id.videoView);
        increaseSize = (ImageButton) floatingWindow.findViewById(R.id.increase_size);
        decreaseSize = (ImageButton) floatingWindow.findViewById(R.id.decrease_size);
        playVideo = (ImageButton) floatingWindow.findViewById(R.id.app_video_play);
        pauseVideo = (ImageButton) floatingWindow.findViewById(R.id.app_video_pause);

        // Setting the on error Listener

        videoView.setOnErrorListener(new OnErrorListener() {
            @Override
            public boolean onError(Exception e) {
                long seek = videoView.getCurrentPosition();
                WritableMap args = new Arguments().createMap();
                args.putInt("index", index);
                args.putInt("seek", (int) seek);
                args.putString("url", playingVideo.getString("url"));
                args.putString("type", "error");

                sendEvent(reactContext, "onError", args);

                Toast.makeText(reactContext, "An Error occured, please try again", Toast.LENGTH_LONG).show();
                return false;
            }
        });

        // Changes the video size when new video is loaded

        videoView.setOnVideoSizedChangedListener(new OnVideoSizeChangedListener() {
            @Override
            public void onVideoSizeChanged(int intrinsicWidth, int intrinsicHeight, float pixelWidthHeightRatio) {
                final float scale = reactContext.getResources().getDisplayMetrics().density;

                videoWidth = intrinsicWidth;
                videoHeight = intrinsicHeight;

                RelativeLayout relativeLayout = (RelativeLayout) floatingWindow.findViewById(R.id.view_wrapper);
                double aspectRatio = (double) videoWidth / (double) videoHeight;

                if (videoHeight > videoWidth) {
                    int height = (int) (200 * scale + 0.5f);
                    double width = height * aspectRatio;

                    relativeLayout.getLayoutParams().width = (int) width;
                    relativeLayout.getLayoutParams().height = height;

                } else {
                    int width = (int) (250 * scale + 0.5f);
                    double height = width / aspectRatio;
                    relativeLayout.getLayoutParams().width = width;
                    relativeLayout.getLayoutParams().height = (int) height;

                }


            }
        });

        floatingWindow.findViewById(R.id.app_video_crop).setOnClickListener(new View.OnClickListener() {


            @Override
            public void onClick(View view) {
                long seek = videoView.getCurrentPosition();
                videoView.setKeepScreenOn(false);
                stopSelf();
                WritableMap args = new Arguments().createMap();
                args.putInt("index", index);
                args.putInt("seek", (int) seek);
                args.putString("url", playingVideo.getString("url"));
                args.putString("type", "close");
                sendEvent(reactContext, "onClose", args);
                onDestroy();
            }
        });



        floatingWindow.findViewById(R.id.Layout_Expended).setOnTouchListener(new View.OnTouchListener() {
            int X_Axis, Y_Axis;
            float TouchX, TouchY;

            @Override
            public boolean onTouch(View v, MotionEvent event) {


                if (gestureDetector.onTouchEvent(event)) {

                    if (overlayView.getVisibility() == View.VISIBLE) {

                        overlayView.setVisibility(View.GONE);
                        timeoutHandler.removeCallbacksAndMessages(null);

                    } else {
                        overlayView.setVisibility(View.VISIBLE);

                        timeoutHandler.postDelayed(new Runnable() {
                            public void run() {

                                overlayView.setVisibility(View.GONE);
                            }
                        }, 5000);
                    }

                } else {

                    int touches = event.getPointerCount();

                    if (touches > 1) {
                    }

                    switch (event.getAction()) {

                        case MotionEvent.ACTION_DOWN:

                            X_Axis = params.x;
                            Y_Axis = params.y;
                            TouchX = event.getRawX();
                            TouchY = event.getRawY();
                            return true;

                        case MotionEvent.ACTION_UP:

                            floatingView.setVisibility(View.VISIBLE);
                            return true;

                        case MotionEvent.ACTION_MOVE:

                            params.x = X_Axis + (int) (event.getRawX() - TouchX);
                            params.y = Y_Axis + (int) (event.getRawY() - TouchY);
                            windowManager.updateViewLayout(floatingWindow, params);
                            return true;
                    }

                }

                return false;

            }
        });
    }

    private class SingleTapConfirm extends SimpleOnGestureListener {

        @Override
        public boolean onSingleTapUp(MotionEvent event) {
            return true;
        }
    }


    private void sendEvent(ReactContext reactContext, String eventName, @Nullable WritableMap params) {
        reactContext.getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter.class).emit(eventName, params);
    }



    public void increaseWindowSize(View view) {
        final float scale = reactContext.getResources().getDisplayMetrics().density;
        RelativeLayout relativeLayout = (RelativeLayout) floatingWindow.findViewById(R.id.view_wrapper);
        Display display = windowManager.getDefaultDisplay();
        Point size = new Point();
        display.getSize(size);
        int densityX = size.x; // default height width of screen

        double aspectRatio = (double) videoWidth / (double) videoHeight;

        if (videoHeight > videoWidth) {
            int height = (int) (400 * scale + 0.5f);
            double width = height * aspectRatio;

            relativeLayout.getLayoutParams().width = (int) width;
            relativeLayout.getLayoutParams().height = height;

        } else {
            int width = densityX;
            double height = width / aspectRatio;
            relativeLayout.getLayoutParams().width = densityX;
            relativeLayout.getLayoutParams().height = (int) height;

        }
        increaseSize.setVisibility(View.GONE);
        decreaseSize.setVisibility(View.VISIBLE);

    }

    // public void returnToApp(View view) {
    //     long seek = videoView.getCurrentPosition();
    //     Intent intent = getPackageManager().getLaunchIntentForPackage(reactContext.getPackageName());
    //     intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
    //     startActivity(intent);
    //     videoView.setKeepScreenOn(false);
    //     stopSelf();
    //     WritableMap args = new Arguments().createMap();
    //     args.putInt("index", index);
    //     args.putInt("seek", (int) seek);
    //     args.putString("type", "close");
    //     args.putString("url", playingVideo.getString("url"));

    //     sendEvent(reactContext, "backToApp", args);
    //     onDestroy();
    // }

    public void decreaseWindowSize(View view) {
        final float scale = reactContext.getResources().getDisplayMetrics().density;
        RelativeLayout relativeLayout = (RelativeLayout) floatingWindow.findViewById(R.id.view_wrapper);

        double aspectRatio = (double) videoWidth / (double) videoHeight;

        if (videoHeight > videoWidth) {
            int height = (int) (200 * scale + 0.5f);
            double width = height * aspectRatio;

            relativeLayout.getLayoutParams().width = (int) width;
            relativeLayout.getLayoutParams().height = height;

        } else {
            int width = (int) (250 * scale + 0.5f);
            double height = width / aspectRatio;
            relativeLayout.getLayoutParams().width = width;
            relativeLayout.getLayoutParams().height = (int) height;

        }

        increaseSize.setVisibility(View.VISIBLE);
        decreaseSize.setVisibility(View.GONE);

    }

    public void onPause(View view) {
        long seek = videoView.getCurrentPosition();
        playVideo.setVisibility(ImageButton.VISIBLE);
        pauseVideo.setVisibility(ImageButton.GONE);
        videoView.pause();
        WritableMap args = Arguments.createMap();
        args.putInt("index", index);
        args.putInt("seek", (int) seek);
        args.putString("type", "paused");
        args.putString("url", playingVideo.getString("url"));
        sendEvent(reactContext, "onPause", args);

    }

    public void onResume(View view) {
        long seek = videoView.getCurrentPosition();
        playVideo.setVisibility(ImageButton.GONE);
        pauseVideo.setVisibility(ImageButton.VISIBLE);
        videoView.start();
        WritableMap args = Arguments.createMap();
        args.putInt("index", index);
        args.putInt("seek", (int) seek);
        args.putString("type", "resume");
        args.putString("url", playingVideo.getString("url"));
        sendEvent(reactContext, "onPlay", args);
    }

    public void onNext(View view) {
        long seek = videoView.getCurrentPosition();
        videoView.seekTo(seek + skipSize);
        playVideo.setVisibility(ImageButton.GONE);
        pauseVideo.setVisibility(ImageButton.VISIBLE);
        WritableMap args = Arguments.createMap();
        args.putInt("index", index);
        args.putString("type", "next");
        args.putInt("seek", (int) seek + skipSize);
        sendEvent(reactContext, "onNext", args);
    }

    public void onPrev(View view) {
        long seek = videoView.getCurrentPosition();
        videoView.seekTo(seek - skipSize);
        playVideo.setVisibility(ImageButton.GONE);
        pauseVideo.setVisibility(ImageButton.VISIBLE);
        WritableMap args = Arguments.createMap();
        args.putInt("index", index);
        args.putString("type", "prev");
        args.putInt("seek", (int) seek - skipSize);
        sendEvent(reactContext, "onPrev", args);
    }


    @Override
    public void onDestroy() {
        super.onDestroy();
        long seek = videoView.getCurrentPosition();
        WritableMap args = Arguments.createMap();
        args.putInt("index", index);
        args.putString("type", "progress");
        args.putInt("seek", (int) seek );
        sendEvent(reactContext, "onProgress", args);
        if (floatingWindow != null)
            windowManager.removeView(floatingWindow);
    }

    protected ReactNativeHost getReactNativeHost() {
        return ((ReactApplication) getApplication()).getReactNativeHost();
    }

}
