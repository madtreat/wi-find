/*
 * Copyright 2014 Google Inc. All Rights Reserved.
 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.vrtoolkit.cardboard.samples.treasurehunt;

import com.google.vrtoolkit.cardboard.CardboardActivity;
import com.google.vrtoolkit.cardboard.CardboardView;
import com.google.vrtoolkit.cardboard.Eye;
import com.google.vrtoolkit.cardboard.HeadTransform;
import com.google.vrtoolkit.cardboard.sensors.HeadTracker;
import com.google.vrtoolkit.cardboard.Viewport;


import android.content.Context;
import android.graphics.SurfaceTexture;
import android.graphics.SurfaceTexture.OnFrameAvailableListener;
import android.media.MediaPlayer;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiManager;
import android.net.wifi.WifiConfiguration;
import android.opengl.GLES20;
import android.opengl.Matrix;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.Vibrator;
import android.util.Log;
import android.hardware.Camera;
import android.graphics.SurfaceTexture;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.List;
import java.util.ArrayList;
import java.util.Timer;
import java.util.TimerTask;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

/**
 * A Cardboard sample application.
 */
public class MainActivity extends CardboardActivity implements CardboardView.StereoRenderer, OnFrameAvailableListener {

    private static final String COUNT = "Count";
    private static int timertest = 0;

    private CardboardView cardboardView;

    private WifiManager wifiManager;
    private static List<ScanResult> wifiSignals;
    private static int trackLevel = 0;
    private static String bssidTrack = ""; // what are we tracking
    private static String ssidTrack = ""; // the name of what are we tracking

    private static Boolean connected = false;
    private MediaPlayer fasthb;
    private MediaPlayer slowhb;
    private MediaPlayer hb;

    private static ArrayList<String> trackedSSIDs;
    private static ArrayList<String> untrackedSSIDs;


    private HeadTracker headTracker;
    private static float[] headMatrix;

    private static final int GL_TEXTURE_EXTERNAL_OES = 0x8D65;
    private int mProgram;
    private SurfaceTexture surface;
    private Camera droid_cam;
    private int texture;

    private static final String TAG = "MainActivity";

    private static final float Z_NEAR = 0.1f;
    private static final float Z_FAR = 100.0f;

    private static final float CAMERA_Z = 0.01f;
    private static final float TIME_DELTA = 0.3f;

    private static final float YAW_LIMIT = 0.12f;
    private static final float PITCH_LIMIT = 0.12f;

    private static final int COORDS_PER_VERTEX = 3;

    // We keep the light always position just above the user.
    private static final float[] LIGHT_POS_IN_WORLD_SPACE = new float[]{0.0f, 2.0f, 0.0f, 1.0f};

    private final float[] lightPosInEyeSpace = new float[4];

    private FloatBuffer floorVertices;
    private FloatBuffer floorColors;
    private FloatBuffer floorNormals;

    private FloatBuffer cubeVertices;
    private FloatBuffer cubeColors;
    private FloatBuffer cubeFoundColors;
    private FloatBuffer cubeNormals;

    private int cubeProgram;
    private int floorProgram;

    private int cubePositionParam;
    private int cubeNormalParam;
    private int cubeColorParam;
    private int cubeModelParam;
    private int cubeModelViewParam;
    private int cubeModelViewProjectionParam;
    private int cubeLightPosParam;

    private int floorPositionParam;
    private int floorNormalParam;
    private int floorColorParam;
    private int floorModelParam;
    private int floorModelViewParam;
    private int floorModelViewProjectionParam;
    private int floorLightPosParam;

    private float[] modelCube;
    private float[] camera;
    private float[] view;
    private float[] headView;
    private float[] modelViewProjection;
    private float[] modelView;
    private float[] modelFloor;

    private int score = 0;
    private float objectDistance = 12f;
    private float floorDepth = 20f;

    private Vibrator vibrator;
    private CardboardOverlayView overlayView;
    private CardboardOverlayViewTop overlayViewTop;

    /**
     * Converts a raw text file, saved as a resource, into an OpenGL ES shader.
     *
     * @param type  The type of shader we will be creating.
     * @param resId The resource ID of the raw text file about to be turned into a shader.
     * @return The shader object handler.
     */
    private int loadGLShader(int type, int resId) {
        String code = readRawTextFile(resId);
        int shader = GLES20.glCreateShader(type);
        GLES20.glShaderSource(shader, code);
        GLES20.glCompileShader(shader);

        // Get the compilation status.
        final int[] compileStatus = new int[1];
        GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, compileStatus, 0);

        // If the compilation failed, delete the shader.
        if (compileStatus[0] == 0) {
            Log.e(TAG, "Error compiling shader: " + GLES20.glGetShaderInfoLog(shader));
            GLES20.glDeleteShader(shader);
            shader = 0;
        }

        if (shader == 0) {
            throw new RuntimeException("Error creating shader.");
        }

        return shader;
    }

    /**
     * Checks if we've had an error inside of OpenGL ES, and if so what that error is.
     *
     * @param label Label to report in case of error.
     */
    private static void checkGLError(String label) {
        int error;
        while ((error = GLES20.glGetError()) != GLES20.GL_NO_ERROR) {
            Log.e(TAG, label + ": glError " + error);
            throw new RuntimeException(label + ": glError " + error);
        }
    }

    public TimerTask mTimerTask = new TimerTask() {
        @Override
        public void run() {
            timertest += 1;

            // scan for wifi signals
            wifiManager.startScan();
            wifiSignals = wifiManager.getScanResults();

//            headTracker.getLastHeadView(headMatrix, 0);

            // get the bssid's signal strength
            trackLevel = 0;
            for (int i = 0; i < wifiSignals.size(); i++) {
                if (bssidTrack.equals("")) {
                    Log.d(COUNT, "Blank bssidTrack...");
                    if (trackedSSIDs.contains(wifiSignals.get(i).SSID)) {
                        continue;
                    }
                    bssidTrack = wifiSignals.get(i).BSSID;
                    ssidTrack = wifiSignals.get(i).SSID;
                    trackLevel = -wifiSignals.get(i).level;
                    trackedSSIDs.add(ssidTrack);
                    if (untrackedSSIDs.contains(wifiSignals.get(i).SSID)) {
                        untrackedSSIDs.remove(ssidTrack);
                    }

                    // attempt to connect
                    String caps = wifiSignals.get(i).capabilities;
                    if (caps.contains("WEP") || caps.contains("WPA") || caps.contains("WPA2")) {
                        connected = false;
                        mHandlerClosed.obtainMessage(1).sendToTarget();
                    }
                    else {
                        WifiConfiguration wifiConfig = new WifiConfiguration();
                        wifiConfig.SSID = String.format("\"%s\"", ssidTrack);
//                    wifiConfig.preSharedKey = String.format("\"%s\"", key);

                        //remember id
                        int netId = wifiManager.addNetwork(wifiConfig);
                        wifiManager.disconnect();
                        wifiManager.enableNetwork(netId, true);
                        wifiManager.reconnect();
                        connected = true;
                        mHandlerConnected.obtainMessage(1).sendToTarget();
                    }
                }
                else {
                    untrackedSSIDs.add(wifiSignals.get(i).SSID);
                }

                if (bssidTrack.equals("")) {
                    break;
                }

                if (wifiSignals.get(i).BSSID.equals(bssidTrack)) {
                    trackLevel = -wifiSignals.get(i).level;
                }
            }

            // change background colors or something to play a hot-cold game with this SSID
            float colorLevel = 0;
            for (int i = 0; i < WorldLayoutData.FLOOR_COLORS.length; i += 4) {
                colorLevel = (float) (trackLevel - 45) / 30;
                if (!connected) {
                    WorldLayoutData.FLOOR_COLORS[i] = colorLevel;//red
                    WorldLayoutData.FLOOR_COLORS[i + 1] = 0.0f;//green
                }
                else {
                    WorldLayoutData.FLOOR_COLORS[i] = 0.0f;//red
                    WorldLayoutData.FLOOR_COLORS[i + 1] = colorLevel;//green
                }
                WorldLayoutData.FLOOR_COLORS[i + 2] = 0.0f;//blue
                WorldLayoutData.FLOOR_COLORS[i + 3] = 1.0f;//alpha
            }
            ByteBuffer bbFloorColors = ByteBuffer.allocateDirect(WorldLayoutData.FLOOR_COLORS.length * 4);
            bbFloorColors.order(ByteOrder.nativeOrder());
            floorColors = bbFloorColors.asFloatBuffer();
            floorColors.put(WorldLayoutData.FLOOR_COLORS);
            floorColors.position(0);
            drawFloor();

            Log.d(COUNT, String.valueOf(wifiSignals));
            Log.d(COUNT, bssidTrack + ": " + String.valueOf(trackLevel) + " => " + String.valueOf(colorLevel));
//            overlayView.show3DToast(bssidTrack + ": " + String.valueOf(trackLevel) + " => " + String.valueOf(redLevel));
            mHandlerTop.obtainMessage(2).sendToTarget();

            if (false) {//timertest > 10) {
//            hideObject();
//            mHandler.obtainMessage(1).sendToTarget();
                timertest = 0;

                // get the max-strength wifi signal
                int maxLevel = 0;
                int maxIndex = 0;
                String maxBSSID = "";
                for (int i = 0; i < wifiSignals.size(); i++) {
                    if (wifiSignals.get(i).level > maxLevel) {
                        maxLevel = wifiSignals.get(i).level;
                        maxIndex = i;
                        maxBSSID = wifiSignals.get(i).BSSID;
                    }
                }
                bssidTrack = maxBSSID;
            }
        }
    };

    private Handler mHandler = new Handler() {
        public void handleMessage(Message msg) {
            overlayView.show3DToast(String.valueOf(msg));//"You ran out of time, look again!");
        }
    };

    private Handler mHandlerTop = new Handler() {
        public void handleMessage(Message msg) {
            overlayViewTop.show3DToast("Tracking SSID: " + ssidTrack + "/ str=" + String.valueOf(trackLevel));
        }
    };

    private Handler mHandlerConnected = new Handler() {
        public void handleMessage(Message msg) {
            overlayView.show3DToast("Connected to " + ssidTrack);
        }
    };

    private Handler mHandlerClosed = new Handler() {
        public void handleMessage(Message msg) {
            overlayView.show3DToast(ssidTrack + " is a closed network");
        }
    };

    /**
     * Sets the view to our CardboardView and initializes the transformation matrices we will use
     * to render our scene.
     */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        wifiManager = (WifiManager) getSystemService(Context.WIFI_SERVICE);

        fasthb = MediaPlayer.create(this, R.raw.fast_hb);
        slowhb = MediaPlayer.create(this, R.raw.slow_hb);
        hb = MediaPlayer.create(this, R.raw.hb);

        Timer mTimer = new Timer();
        mTimer.scheduleAtFixedRate(mTimerTask, 1000, 1000);

        setContentView(R.layout.common_ui);
        cardboardView = (CardboardView) findViewById(R.id.cardboard_view);
        cardboardView.setRestoreGLStateEnabled(false);
        cardboardView.setRenderer(this);
        setCardboardView(cardboardView);

        trackedSSIDs = new ArrayList<String>();
        untrackedSSIDs = new ArrayList<String>();

//        headTracker = (HeadTracker) getSystemService(Context.);
//        headTracker.setNeckModelEnabled(true);
//        headTracker.setGyroBiasEstimationEnabled(true);
//        headTracker.startTracking();

        modelCube = new float[16];
        camera = new float[16];
        view = new float[16];
        modelViewProjection = new float[16];
        modelView = new float[16];
        modelFloor = new float[16];
        headView = new float[16];
        vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);


        overlayView = (CardboardOverlayView) findViewById(R.id.overlay);
        overlayView.show3DToast("Welcome To our app. \nMore Red means more Wi-Fi");

        overlayViewTop = (CardboardOverlayViewTop) findViewById(R.id.overlayTop);
//        overlayViewTop.show3DToast();
    }

    @Override
    public void onRendererShutdown() {
        Log.i(TAG, "onRendererShutdown");
    }

    @Override
    public void onSurfaceChanged(int width, int height) {
        Log.i(TAG, "onSurfaceChanged");
    }

    /**
     * Creates the buffers we use to store information about the 3D world.
     * <p/>
     * <p>OpenGL doesn't use Java arrays, but rather needs data in a format it can understand.
     * Hence we use ByteBuffers.
     *
     * @param config The EGL configuration used when creating the surface.
     */
    @Override
    public void onSurfaceCreated(EGLConfig config) {
        Log.i(TAG, "onSurfaceCreated");
        GLES20.glClearColor(0.1f, 0.1f, 0.1f, 0.5f); // Dark background so text shows up well.

        ByteBuffer bbVertices = ByteBuffer.allocateDirect(WorldLayoutData.CUBE_COORDS.length * 4);
        bbVertices.order(ByteOrder.nativeOrder());
        cubeVertices = bbVertices.asFloatBuffer();
        cubeVertices.put(WorldLayoutData.CUBE_COORDS);
        cubeVertices.position(0);

        ByteBuffer bbColors = ByteBuffer.allocateDirect(WorldLayoutData.CUBE_COLORS.length * 4);
        bbColors.order(ByteOrder.nativeOrder());
        cubeColors = bbColors.asFloatBuffer();
        cubeColors.put(WorldLayoutData.CUBE_COLORS);
        cubeColors.position(0);

        ByteBuffer bbFoundColors = ByteBuffer.allocateDirect(
                WorldLayoutData.CUBE_FOUND_COLORS.length * 4);
        bbFoundColors.order(ByteOrder.nativeOrder());
        cubeFoundColors = bbFoundColors.asFloatBuffer();
        cubeFoundColors.put(WorldLayoutData.CUBE_FOUND_COLORS);
        cubeFoundColors.position(0);

        ByteBuffer bbNormals = ByteBuffer.allocateDirect(WorldLayoutData.CUBE_NORMALS.length * 4);
        bbNormals.order(ByteOrder.nativeOrder());
        cubeNormals = bbNormals.asFloatBuffer();
        cubeNormals.put(WorldLayoutData.CUBE_NORMALS);
        cubeNormals.position(0);

// make a floor
        ByteBuffer bbFloorVertices = ByteBuffer.allocateDirect(WorldLayoutData.FLOOR_COORDS.length * 4);
        bbFloorVertices.order(ByteOrder.nativeOrder());
        floorVertices = bbFloorVertices.asFloatBuffer();
        floorVertices.put(WorldLayoutData.FLOOR_COORDS);
        floorVertices.position(0);

        ByteBuffer bbFloorNormals = ByteBuffer.allocateDirect(WorldLayoutData.FLOOR_NORMALS.length * 4);
        bbFloorNormals.order(ByteOrder.nativeOrder());
        floorNormals = bbFloorNormals.asFloatBuffer();
        floorNormals.put(WorldLayoutData.FLOOR_NORMALS);
        floorNormals.position(0);

        ByteBuffer bbFloorColors = ByteBuffer.allocateDirect(WorldLayoutData.FLOOR_COLORS.length * 4);
        bbFloorColors.order(ByteOrder.nativeOrder());
        floorColors = bbFloorColors.asFloatBuffer();
        floorColors.put(WorldLayoutData.FLOOR_COLORS);
        floorColors.position(0);

        int vertexShader = loadGLShader(GLES20.GL_VERTEX_SHADER, R.raw.light_vertex);
        int gridShader = loadGLShader(GLES20.GL_FRAGMENT_SHADER, R.raw.grid_fragment);
        int passthroughShader = loadGLShader(GLES20.GL_FRAGMENT_SHADER, R.raw.passthrough_fragment);

        cubeProgram = GLES20.glCreateProgram();
        GLES20.glAttachShader(cubeProgram, vertexShader);
        GLES20.glAttachShader(cubeProgram, passthroughShader);
        GLES20.glLinkProgram(cubeProgram);
        GLES20.glUseProgram(cubeProgram);

        checkGLError("Cube program");

        cubePositionParam = GLES20.glGetAttribLocation(cubeProgram, "a_Position");
        cubeNormalParam = GLES20.glGetAttribLocation(cubeProgram, "a_Normal");
        cubeColorParam = GLES20.glGetAttribLocation(cubeProgram, "a_Color");

        cubeModelParam = GLES20.glGetUniformLocation(cubeProgram, "u_Model");
        cubeModelViewParam = GLES20.glGetUniformLocation(cubeProgram, "u_MVMatrix");
        cubeModelViewProjectionParam = GLES20.glGetUniformLocation(cubeProgram, "u_MVP");
        cubeLightPosParam = GLES20.glGetUniformLocation(cubeProgram, "u_LightPos");

        GLES20.glEnableVertexAttribArray(cubePositionParam);
        GLES20.glEnableVertexAttribArray(cubeNormalParam);
        GLES20.glEnableVertexAttribArray(cubeColorParam);

        checkGLError("Cube program params");

        floorProgram = GLES20.glCreateProgram();
        GLES20.glAttachShader(floorProgram, vertexShader);
        GLES20.glAttachShader(floorProgram, gridShader);
        GLES20.glLinkProgram(floorProgram);
        GLES20.glUseProgram(floorProgram);

        checkGLError("Floor program");

        floorModelParam = GLES20.glGetUniformLocation(floorProgram, "u_Model");
        floorModelViewParam = GLES20.glGetUniformLocation(floorProgram, "u_MVMatrix");
        floorModelViewProjectionParam = GLES20.glGetUniformLocation(floorProgram, "u_MVP");
        floorLightPosParam = GLES20.glGetUniformLocation(floorProgram, "u_LightPos");

        floorPositionParam = GLES20.glGetAttribLocation(floorProgram, "a_Position");
        floorNormalParam = GLES20.glGetAttribLocation(floorProgram, "a_Normal");
        floorColorParam = GLES20.glGetAttribLocation(floorProgram, "a_Color");

        GLES20.glEnableVertexAttribArray(floorPositionParam);
        GLES20.glEnableVertexAttribArray(floorNormalParam);
        GLES20.glEnableVertexAttribArray(floorColorParam);

        checkGLError("Floor program params");

// Object first appears directly in front of user.
        Matrix.setIdentityM(modelCube, 0);
        Matrix.translateM(modelCube, 0, 0, 0, -objectDistance);

        Matrix.setIdentityM(modelFloor, 0);
        Matrix.translateM(modelFloor, 0, 0, -floorDepth, 0); // Floor appears below user.

        checkGLError("onSurfaceCreated");

//        mProgram = GLES20.glCreateProgram();             // create empty OpenGL ES Program
//        GLES20.glAttachShader(mProgram, vertexShader);   // add the vertex shader to program
//        GLES20.glAttachShader(mProgram, fragmentShader); // add the fragment shader to program
//        GLES20.glLinkProgram(mProgram);

        texture = createTexture();
        startCamera(texture);
    }

    @Override
    public void onFrameAvailable(SurfaceTexture arg0) {
        cardboardView.requestRender();
    }

    /**
     * Converts a raw text file into a string.
     *
     * @param resId The resource ID of the raw text file about to be turned into a shader.
     * @return The context of the text file, or null in case of error.
     */
    private String readRawTextFile(int resId) {
        InputStream inputStream = getResources().openRawResource(resId);
        try {
            BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append("\n");
            }
            reader.close();
            return sb.toString();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }

    /**
     * Prepares OpenGL ES before we draw a frame.
     *
     * @param headTransform The head transformation in the new frame.
     */
    @Override
    public void onNewFrame(HeadTransform headTransform) {
// Build the Model part of the ModelView matrix.
        Matrix.rotateM(modelCube, 0, TIME_DELTA, 0.5f, 0.5f, 1.0f);

// Build the camera matrix and apply it to the ModelView.
        Matrix.setLookAtM(camera, 0, 0.0f, 0.0f, CAMERA_Z, 0.0f, 0.0f, 0.0f, 0.0f, 1.0f, 0.0f);

        headTransform.getHeadView(headView, 0);

        checkGLError("onReadyToDraw");
    }

    /**
     * Draws a frame for an eye.
     *
     * @param eye The eye to render. Includes all required transformations.
     */
    @Override
    public void onDrawEye(Eye eye) {
        GLES20.glEnable(GLES20.GL_DEPTH_TEST);
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT | GLES20.GL_DEPTH_BUFFER_BIT);

        checkGLError("colorParam");

//        GLES20.glUseProgram(mProgram);
//        GLES20.glActiveTexture(GL_TEXTURE_EXTERNAL_OES); // glerror 1280
//        checkGLError("cam-texture-a");
//        GLES20.glBindTexture(GL_TEXTURE_EXTERNAL_OES, texture);
//        checkGLError("cam-texture-b");

// Apply the eye transformation to the camera.
        Matrix.multiplyMM(view, 0, eye.getEyeView(), 0, camera, 0);

// Set the position of the light
        Matrix.multiplyMV(lightPosInEyeSpace, 0, view, 0, LIGHT_POS_IN_WORLD_SPACE, 0);

// Build the ModelView and ModelViewProjection matrices
// for calculating cube position and light.
        float[] perspective = eye.getPerspective(Z_NEAR, Z_FAR);
        Matrix.multiplyMM(modelView, 0, view, 0, modelCube, 0);
        Matrix.multiplyMM(modelViewProjection, 0, perspective, 0, modelView, 0);
//        drawCube();

// Set modelView for the floor, so we draw floor in the correct location
        Matrix.multiplyMM(modelView, 0, view, 0, modelFloor, 0);
        Matrix.multiplyMM(modelViewProjection, 0, perspective, 0,
                modelView, 0);
        drawFloor();
    }

    @Override
    public void onFinishFrame(Viewport viewport) {
    }

    /**
     * Draw the cube.
     * <p/>
     * <p>We've set all of our transformation matrices. Now we simply pass them into the shader.
     */

    public void drawCube() {
        GLES20.glUseProgram(cubeProgram);

        GLES20.glUniform3fv(cubeLightPosParam, 1, lightPosInEyeSpace, 0);

// Set the Model in the shader, used to calculate lighting
        GLES20.glUniformMatrix4fv(cubeModelParam, 1, false, modelCube, 0);

// Set the ModelView in the shader, used to calculate lighting
        GLES20.glUniformMatrix4fv(cubeModelViewParam, 1, false, modelView, 0);

// Set the position of the cube
        GLES20.glVertexAttribPointer(cubePositionParam, COORDS_PER_VERTEX, GLES20.GL_FLOAT,
                false, 0, cubeVertices);

// Set the ModelViewProjection matrix in the shader.
        GLES20.glUniformMatrix4fv(cubeModelViewProjectionParam, 1, false, modelViewProjection, 0);

// Set the normal positions of the cube, again for shading
        GLES20.glVertexAttribPointer(cubeNormalParam, 3, GLES20.GL_FLOAT, false, 0, cubeNormals);
        GLES20.glVertexAttribPointer(cubeColorParam, 4, GLES20.GL_FLOAT, false, 0,
                isLookingAtObject() ? cubeFoundColors : cubeColors);

//        GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, 36);
        checkGLError("Drawing cube");
    }

    /**
     * Draw the floor.
     * <p/>
     * <p>This feeds in data for the floor into the shader. Note that this doesn't feed in data about
     * position of the light, so if we rewrite our code to draw the floor first, the lighting might
     * look strange.
     */
    public void drawFloor() {
        GLES20.glUseProgram(floorProgram);

// Set ModelView, MVP, position, normals, and color.
        GLES20.glUniform3fv(floorLightPosParam, 1, lightPosInEyeSpace, 0);
        GLES20.glUniformMatrix4fv(floorModelParam, 1, false, modelFloor, 0);
        GLES20.glUniformMatrix4fv(floorModelViewParam, 1, false, modelView, 0);
        GLES20.glUniformMatrix4fv(floorModelViewProjectionParam, 1, false,
                modelViewProjection, 0);
        GLES20.glVertexAttribPointer(floorPositionParam, COORDS_PER_VERTEX, GLES20.GL_FLOAT,
                false, 0, floorVertices);
        GLES20.glVertexAttribPointer(floorNormalParam, 3, GLES20.GL_FLOAT, false, 0,
                floorNormals);
        GLES20.glVertexAttribPointer(floorColorParam, 4, GLES20.GL_FLOAT, false, 0, floorColors);

        GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, 6);

        checkGLError("drawing floor");
    }

    /**
     * Called when the Cardboard trigger is pulled.
     */
    @Override
    public void onCardboardTrigger() {
        Log.i(TAG, "onCardboardTrigger");

        overlayView.show3DToast("Switching SSID...");
        bssidTrack = "";
        ssidTrack = "";
//        if (isLookingAtObject()) {
//            score++;
//            timertest = 0;
////            overlayView.show3DToast("Found it! Look around for another one.\nScore = " + score);
//            hideObject();
//        } else {
//
//        }

// Always give user feedback.
        vibrator.vibrate(50);
    }

    /**
     * Find a new random position for the object.
     * <p/>
     * <p>We'll rotate it around the Y-axis so it's out of sight, and then up or down by a little bit.
     */
    private void hideObject() {
        float[] rotationMatrix = new float[16];
        float[] posVec = new float[4];

// First rotate in XZ plane, between 90 and 270 deg away, and scale so that we vary
// the object's distance from the user.
        float angleXZ = (float) Math.random() * 180 + 90;
        Matrix.setRotateM(rotationMatrix, 0, angleXZ, 0f, 1f, 0f);
        float oldObjectDistance = objectDistance;
        objectDistance = (float) Math.random() * 15 + 5;
        float objectScalingFactor = objectDistance / oldObjectDistance;
        Matrix.scaleM(rotationMatrix, 0, objectScalingFactor, objectScalingFactor,
                objectScalingFactor);
        Matrix.multiplyMV(posVec, 0, rotationMatrix, 0, modelCube, 12);

// Now get the up or down angle, between -20 and 20 degrees.
        float angleY = (float) Math.random() * 80 - 40; // Angle in Y plane, between -40 and 40.
        angleY = (float) Math.toRadians(angleY);
        float newY = (float) Math.tan(angleY) * objectDistance;

        Matrix.setIdentityM(modelCube, 0);
        Matrix.translateM(modelCube, 0, posVec[0], newY, posVec[2]);
    }

    /**
     * Check if user is looking at object by calculating where the object is in eye-space.
     *
     * @return true if the user is looking at the object.
     */
    private boolean isLookingAtObject() {
        float[] initVec = {0, 0, 0, 1.0f};
        float[] objPositionVec = new float[4];

// Convert object space to camera space. Use the headView from onNewFrame.
        Matrix.multiplyMM(modelView, 0, headView, 0, modelCube, 0);
        Matrix.multiplyMV(objPositionVec, 0, modelView, 0, initVec, 0);

        float pitch = (float) Math.atan2(objPositionVec[1], -objPositionVec[2]);
        float yaw = (float) Math.atan2(objPositionVec[0], -objPositionVec[2]);

        return Math.abs(pitch) < PITCH_LIMIT && Math.abs(yaw) < YAW_LIMIT;
    }


    public void startCamera(int texture)
    {
        surface = new SurfaceTexture(texture);
        surface.setOnFrameAvailableListener(this);

        droid_cam = Camera.open();

        try
        {
            droid_cam.setPreviewTexture(surface);
            droid_cam.startPreview();
        }
        catch (IOException ioe)
        {
            Log.w("MainActivity","CAM LAUNCH FAILED");
        }
    }

    static private int createTexture()
    {
        int[] texture = new int[1];

        GLES20.glGenTextures(1,texture, 0);
        GLES20.glBindTexture(GL_TEXTURE_EXTERNAL_OES, texture[0]);
        GLES20.glTexParameterf(GL_TEXTURE_EXTERNAL_OES,
                GL10.GL_TEXTURE_MIN_FILTER,GL10.GL_LINEAR);
        GLES20.glTexParameterf(GL_TEXTURE_EXTERNAL_OES,
                GL10.GL_TEXTURE_MAG_FILTER, GL10.GL_LINEAR);
        GLES20.glTexParameteri(GL_TEXTURE_EXTERNAL_OES,
                GL10.GL_TEXTURE_WRAP_S, GL10.GL_CLAMP_TO_EDGE);
        GLES20.glTexParameteri(GL_TEXTURE_EXTERNAL_OES,
                GL10.GL_TEXTURE_WRAP_T, GL10.GL_CLAMP_TO_EDGE);

        return texture[0];
    }
}
