// also requires some modifications to the manifest (for the file provider) and a corresponding xml file. see https://developer.android.com/training/camera/photobasics.html
// and the layout needs a button to call dispatchTakePictureIntent

package com.timothychia.faces;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Environment;
import android.os.Handler;
import android.provider.MediaStore;
import android.support.v4.content.FileProvider;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.text.method.ScrollingMovementMethod;
import android.util.Base64;
import android.util.Log;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;

import com.android.volley.AuthFailureError;
import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.JsonObjectRequest;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.ByteBuffer;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static android.graphics.Bitmap.createScaledBitmap;

public class MainActivity extends AppCompatActivity {
    public static final String EXTRA_MESSAGE = "com.example.myfirstapp.MESSAGE";
    private static final String LOG_TAG = "Faces_Main_DT";
    private static final String TAG_WRISTBAND =  "Wristband Tag";

    /* Strings used to communicate with the other modules (Android sends)*/
    private final String MATCH_FOUND = "Match Found\n";
    private final String NO_MATCH = "No Match\n";
    private final String TAKE_PHOTO = "Take Photo\n";

    /* Strings used to communicate with the other modules (Android receives)*/
    private final String RECOGNIZE_REQUEST = "Recognize Request"; // no newline because using readline


    private ImageView mImageView;
    private TextView mTextView;
    private EditText mEditText_newID;
    static final int REQUEST_IMAGE_CAPTURE = 1;
    static final int REQUEST_ENABLE_BT = 2;


    private String app_id = "a1731ed8";
    private String app_key = "d3a579a339de2805b54e53dcd72ee40c";
    final String gallery_name = "People";

    private final String  url_enroll = "https://api.kairos.com/enroll";
    private final String url_recognize = "https://api.kairos.com/recognize";
    private final String  url_gallery_list = "https://api.kairos.com/gallery/list_all";
    private final String  url_gallery_view = "https://api.kairos.com/gallery/view";
    private final String  url_view_subject = "https://api.kairos.com/gallery/view_subject";
    private final String  url_gallery_remove = "https://api.kairos.com/gallery/remove";
    private final String noPhoto = "No Photo";

    private RequestQueue mRequestQueue;

    private BluetoothAdapter mBluetoothAdapter;
    private BluetoothConnectThread mWristbandConnectThread;
    private BluetoothConnectThread mCameraConnectThread;

    private BluetoothConnectedThread mWristbandThread;
    private BluetoothConnectedThread mCameraThread;


    private Handler mWristbandHandler;
    private Handler mCameraHandler;

    //SPP UUID. Should be the UUID for HC 05. Corresponds to what services are available on it.
    static final UUID mUUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");

    String mCurrentPhotoPath;

    /* Used to tune the API performance. */
    private double mThreshold = 0.50;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mImageView = (ImageView) findViewById(R.id.imageView);
        mTextView = (TextView) findViewById(R.id.textView2);
        mTextView.setMovementMethod(new ScrollingMovementMethod());

        mEditText_newID = (EditText) findViewById(R.id.editText_newID);

        //adding click listener to button. Triggers the built in camera activity.
        findViewById(R.id.button_list).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                recognize();
            }
        });

        //adding click listener to button. Should enroll the photo at mCurrPhotoPath
        findViewById(R.id.button_remove).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Bitmap bitmap = getCurrentBitmap();
                if(bitmap == null) {
                    Log.d(LOG_TAG, "Error enrolling. Bitmap is null.");
                    return;
            }
                String newID = mEditText_newID.getText().toString();
                enroll(bitmap, newID);
            }
        });

        //adding click listener to button. Launches the database activity.
        findViewById(R.id.button_database).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                manage_database();
            }
        });



        // for now, using noPhoto as a way to maintain information about whether an image exists to be recognized or enrolled
        mCurrentPhotoPath = noPhoto;

        // Initialize request queue in a long lasting way.
        mRequestQueue =  RequestQueueSingleton.getInstance(this.getApplicationContext()).getRequestQueue();

        // Connect other modules via bluetooth.
        connectDevices();
    }

    /* For returning to this activity from other activities. */
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQUEST_IMAGE_CAPTURE && resultCode == RESULT_OK) {
            Bitmap bitmap = getCurrentBitmap();
            if(bitmap != null) {
                mImageView.setImageBitmap(bitmap);
                uploadBitmap(bitmap);
            }
        }

        if (requestCode == REQUEST_ENABLE_BT ) {
            if( resultCode == RESULT_OK)
                Log.d(LOG_TAG,"Bluetooth enable success.");
            else
                Log.d(LOG_TAG,"Bluetooth enable failed..");
            connectDevices(); // return to the testing function that should have started the intent to begin wtih
        }

    }


    /* Launch a separate activity to let the user view the database. */
    private void manage_database(){
        Intent intent = new Intent(this, DatabaseManagementActivity.class);
        startActivity(intent);
    }

    /* Connect the wristband and camera modules, and start their threads and handlers. */
    private void connectDevices(){
        String wristbandName = "HC-05";
        String cameraName = "FACES_CAMERA";


        /* Get a valid bluetooth adapter. */
        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (mBluetoothAdapter == null) {
            // Device doesn't support Bluetooth
        }
        if (!mBluetoothAdapter.isEnabled()) {
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
        }
        /* Initialize handlers on UI thread. */
        mWristbandHandler = new Handler();
        mCameraHandler = new Handler();

        /* Get the wristband and camera BluetoothDevice.*/
        BluetoothDevice wristbandDevice = null;
        BluetoothDevice cameraDevice = null;
        Set<BluetoothDevice> pairedDevices = mBluetoothAdapter.getBondedDevices();
        if (pairedDevices.size() > 0) {
            // There are paired devices. Get the name and address of each paired device.
            for (BluetoothDevice device : pairedDevices) {
                String deviceName = device.getName();
                Log.d(LOG_TAG,deviceName);
                if(deviceName.equals(wristbandName))
                    wristbandDevice = device;
                if(deviceName.equals(cameraName))
                    cameraDevice = device;
            }
        }
        else{
            Log.d(LOG_TAG,"No paired devices found!");
        }
//
//        /* Start the device connection threads. Override run to launch the device management threads after.*/
//        if(wristbandDevice != null){
//            mWristbandConnectThread = new BluetoothConnectThread(wristbandDevice,mUUID){
//                @Override
//                public void run() {
//                    super.run();
//                    startWristbandThread(getMmSocket());
//
//                }
//            };
//            mWristbandConnectThread.start();
//        }
//        else Log.d(LOG_TAG,"wristBandDevice null! Thread not launched.");

        if(cameraDevice != null){
            mCameraConnectThread = new BluetoothConnectThread(cameraDevice,mUUID){
                @Override
                public void run() {
                    super.run();
                    startCameraThread(getMmSocket());
                }
            };
            mCameraConnectThread.start();
        }
        else Log.d(LOG_TAG,"cameraDevice null! Thread not launched.");

    }

    /* Starts a thread to manage the wristband. */
    void startWristbandThread(BluetoothSocket mmSocket ){


        mWristbandThread = new BluetoothConnectedThread(  mmSocket , mWristbandHandler ){
            @Override
            public void run() {
                String fromWristband; String tmp;
                BufferedReader in = new BufferedReader(new InputStreamReader(mmInStream),200000);
//             Keep listening to the InputStream until an exception occurs.
                    while (true) {
                        try {
                            Log.d(TAG_WRISTBAND, "Attempting to read");
                            fromWristband = in.readLine();
                            Log.d(TAG_WRISTBAND, fromWristband);

                            // Have the UI thread respond to whatever the wristband just sent.
                            if(fromWristband.equals( RECOGNIZE_REQUEST )){
                                mHandler.post(new Runnable() {
                                    @Override
                                    public void run() {
                                        recognize(); // currently the start of the recognition chain
                                    }
                                });
                            }
                        } catch (IOException e) {
                            Log.d(TAG_WRISTBAND, "Input stream was disconnected", e);
                            break;
                        }
                    }


            }
        };
        mWristbandThread.start();

    }

    void startCameraThread(BluetoothSocket mmSocket ){


        mCameraThread = new BluetoothConnectedThread(  mmSocket , mCameraHandler ){
            @Override
            public void run() {
                String fromWristband; String tmp;
//                BufferedReader in = new BufferedReader(new InputStreamReader(mmInStream),200000);
                BufferedInputStream inBuff = new BufferedInputStream(mmInStream,20000);
                final byte[] imageBytes = new byte[5800]; // needs to be bigger?
                byte[] garbageBytes = new byte[100];
                int numBytes = 0;
                int bytesRead = 0;
                int bytesLeft = 0;
//             Keep listening to the InputStream until an exception occurs.
                while (true) {
                    try {
                        Log.d(TAG_WRISTBAND, "Attempting to read");
//                        fromWristband = in.readLine();

                        /* Read *RDY* tag */
                        while(numBytes <5){
                            bytesRead = inBuff.read(imageBytes,numBytes,5 - numBytes);
                            if(bytesRead>= 0)
                                numBytes = numBytes + bytesRead;
                            Log.d(TAG_WRISTBAND,"numBytes = " + numBytes);
                        }
                        Log.d(TAG_WRISTBAND, "5 bytes read:  "+String.valueOf(imageBytes[0]) + String.valueOf(imageBytes[1]) + String.valueOf(imageBytes[2]) + String.valueOf(imageBytes[3]) +String.valueOf(imageBytes[4])   );
                        /* Read image */
                        numBytes = 0;
                        bytesRead = 0;
                        while(numBytes < 4800){
                            bytesRead = inBuff.read(imageBytes,numBytes,4800 - numBytes);
                            if(bytesRead>= 0)
                                numBytes = numBytes + bytesRead;
                            Log.d(TAG_WRISTBAND,"numBytes = " + numBytes);
                        }
                        Log.d(TAG_WRISTBAND, "Received this many bytes: " + numBytes);
                        /* Read the garbage. I don't know why, but this section does stop garbage from showing up instead of the *RDY tag. Even though it never finds any garbage. */
                        bytesRead = 0;
                        numBytes = 0;
                        bytesLeft = inBuff.available(); // next read call is guaranteed to not block if we use this in the call
                        while(bytesLeft > 0){
                            bytesRead = inBuff.read(garbageBytes,numBytes, bytesRead);
                            if(bytesRead>= 0)
                                numBytes = numBytes + bytesRead;
                            Log.d(TAG_WRISTBAND,"garbageBytes = " + numBytes);
                            bytesLeft = inBuff.available();
                        }



                        // Have the UI thread respond
                            mHandler.post(new Runnable() {
                                @Override
                                public void run() {
                                    // this will run in the main thread
                                    testCBB(imageBytes);
                                }
                            });

                    } catch (IOException e) {
                        Log.d(TAG_WRISTBAND, "Input stream was disconnected", e);
                        break;
                    }
                }


            }
        };
        mCameraThread.start();

    }


    private void testCBB(byte[] imageBytes){
        byte[] imageRGBA = new byte [imageBytes.length*4];
        int i;
        for(i = 0;i<imageBytes.length;i++){
            imageRGBA[4*i] = imageBytes[i];
            imageRGBA[4*i + 1] = imageBytes[i];
            imageRGBA[4*i + 2] = imageBytes[i];
            imageRGBA[4*i + 3] = (byte) 0xff;
        }
        int   width  = 80;
        int   height = 60;
        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        bitmap.copyPixelsFromBuffer(ByteBuffer.wrap(imageRGBA));
        mImageView.setImageBitmap(bitmap);
        uploadBitmap(bitmap);
    }

    public void testRunnable(){
        mTextView.setText("Runnable executed!");
    }

    // connected to a UI button. attempts to write, using the thread.
    public void testWrite(View view){
        Log.d("debug_write", "Attempting to write");
        //don't forget the newline my arduino code is expecting!
        mWristbandThread.write("Testing\n".toString().getBytes());
    }


    /* Functions to send strings to the other modules.
     * Caller is responsible for appending newline.
     * Does a null check in case the app is being debugged without the other modules connected. */
    private void send_wristband(String string){
        if(mWristbandThread == null){
            Log.d(LOG_TAG,"No wristband thread! Skipping write.");
        }
        else{
            Log.d("debug_write", "Attempting to write this to wristband.");
            mWristbandThread.write(string.toString().getBytes());
        }
    }
    private void send_camera(String string){
        if(mCameraThread == null){
            Log.d(LOG_TAG,"No camera thread! Skipping write.");
        }
        else{
            Log.d("debug_write", "Attempting to write this to camera.");
            mCameraThread.write(string.toString().getBytes());
        }
    }

    /* Uses some camera to get a photo, then makes the API call. */
    private void recognize(){
        /* A version without the camera module. */
//        dispatchTakePictureIntent();

        /* A version using the camera module. */
        send_camera(TAKE_PHOTO);
    }



    /* Returns the bitmap stored at the file located by mCurrentPhotoPath
    *  currently returns null if something goes wrong
    *  should maybe have it throw IO exceptions etc. */
    private Bitmap getCurrentBitmap(){
        if(mCurrentPhotoPath == noPhoto)
            return null;

        File photoFile = new File(mCurrentPhotoPath);
        // Code to get a content URI using the path we saved when first creating the most recent photo file.
        Uri photoURI = FileProvider.getUriForFile(this,
                "com.example.android.fileprovider",
                photoFile);
        try {
            Bitmap bitmap = MediaStore.Images.Media.getBitmap(this.getContentResolver(), photoURI);
            bitmap = createScaledBitmap(bitmap , 320,240, false); //scale it down to the same size as OV7670 pictures.

            return bitmap;
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }

    // Launch the system's own camera, providing the activity with a place to save the file.
    // Do add the file provider manifest and xml as detailed in https://developer.android.com/training/camera/photobasics.html
    private void dispatchTakePictureIntent() {
        Intent takePictureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        // Ensure that there's a camera activity to handle the intent
        if (takePictureIntent.resolveActivity(getPackageManager()) != null) {
            // Create the File where the photo should go
            File photoFile = null;
            try {
                photoFile = createImageFile();
            } catch (IOException ex) {
                // Error occurred while creating the File
            }
            // Continue only if the File was successfully created
            if (photoFile != null) {
                Uri photoURI = FileProvider.getUriForFile(this,
                        "com.example.android.fileprovider",
                        photoFile);
                takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, photoURI);
                startActivityForResult(takePictureIntent, REQUEST_IMAGE_CAPTURE);
            }
        }
    }

    // Helper function that creates a file for the camera activity to save data into.
    private File createImageFile() throws IOException {
        // Create an image file name
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
        String imageFileName = "JPEG_" + timeStamp + "_";
        File storageDir = getExternalFilesDir(Environment.DIRECTORY_PICTURES);
        File image = File.createTempFile(
                imageFileName,  /* prefix */
                ".jpg",         /* suffix */
                storageDir      /* directory */
        );

        // Save a file: path for use with ACTION_VIEW intents
        mCurrentPhotoPath = image.getAbsolutePath();
        return image;
    }

    /*
     * The method is taking Bitmap as an argument
     * then it will return the byte[] array for the given bitmap
     * and we will send this array to the server
     * here we are using PNG Compression with 80% quality
     * you can give quality between 0 to 100
     * 0 means worse quality
     * 100 means best quality
     * */
    public byte[] getFileDataFromDrawable(Bitmap bitmap) {
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.PNG, 80, byteArrayOutputStream);
        return byteArrayOutputStream.toByteArray();
    }

    /*
     * Enroll the bitmap under newID.
     * newID = a user inputted string which contains a face's name. Additional information here too e.g. mutual friend.
     * */
    // function to add a face to the database
    private void enroll(final Bitmap bitmap,String newID){
        //maybe check for an empty string?
        Log.d(LOG_TAG,"newID is "+ newID);

        // build json parameters
        JSONObject param = new JSONObject();
        try {
            param.put("gallery_name",gallery_name);
            param.put("subject_id",newID);
            param.put("image",Base64.encodeToString(getFileDataFromDrawable(bitmap), Base64.DEFAULT) );
        } catch (JSONException e) {
            e.printStackTrace();
        }

        // build the POST request to enroll
        JsonObjectRequest jReq = new JsonObjectRequest(Request.Method.POST, url_enroll,param,
                new Response.Listener() {
                    // online tutorial doesn't use "Object response", but android studio won't recognize it as an override otherwise
                    public void onResponse(Object response) {
                        // Display the first 500 characters of the response string.
                        mTextView.setText(mCurrentPhotoPath +"Enrolled with Response: "+ response.toString());
                    }
                }, new Response.ErrorListener() {
            @Override
            public void onErrorResponse(VolleyError error) {
                mTextView.setText(mCurrentPhotoPath +"That didn't work!");
            }
        }){
            @Override
            // online tutorial uses some strange syntax in the angular brackets. Changed it to this instead.
            public Map<String, String> getHeaders() throws AuthFailureError {
                HashMap<String, String> headers = new HashMap<String, String>();
                headers.put("Content-Type", "application/json");
                headers.put("app_id",app_id);
                headers.put("app_key", app_key);
                return headers;
            }
        };

        // Add the request to the RequestQueue.
        mRequestQueue.add(jReq);
    }


    // modifying this to do a Kairos recognize API call
    private void uploadBitmap(final Bitmap bitmap) {
        String recognize_url = "https://api.kairos.com/recognize";
        final String gallery_name = "Office";

        Log.d("time", "start:"+String.valueOf(System.nanoTime()));

        // For some reason, the JSONObject.put method has to be wrapped in try/catch before android studio will take it
        JSONObject recognize_param = new JSONObject();
        try {
            recognize_param.put("gallery_name","People");
            recognize_param.put("threshold","0"); // use 0 here to force a result if a face existed
            recognize_param.put("image",Base64.encodeToString(getFileDataFromDrawable(bitmap), Base64.DEFAULT) );
        } catch (JSONException e) {
            e.printStackTrace();
        }

        JsonObjectRequest jReq = new JsonObjectRequest(Request.Method.POST, recognize_url,recognize_param,
                new Response.Listener() {
                    // online tutorial doesn't use "Object response", but android studio won't recognize it as an override otherwise
                    public void onResponse(Object response) {

                        Log.d("time", "end:"+String.valueOf(System.nanoTime()));


                        // Print the entire response to the debugging log.
                        Log.d(LOG_TAG, "Response received to Recognize request:" + response.toString());

                        // cast the response and attempt to parse through it, using try catch and opt statements for errors/exceptions
                        JSONObject response_json = (JSONObject) response;
                        JSONObject transaction = new JSONObject();

                        /* An error has occurred, such as no faces in image.*/
                        if(response_json.optJSONArray("Errors") != null ){
                            String error = "Unknown error.";
                            try{
                                error = response_json.getJSONArray("Errors").getJSONObject(0).getString("Message");
                            } catch (JSONException e) {
                                error = "Unknown error. JSON Exception.";
                                e.printStackTrace();
                            }finally {
                                mTextView.setText("Error recognizing: " + error);
                                Log.d(LOG_TAG,"Recognize response is an error."+error);
                            send_wristband(NO_MATCH);
                                send_wristband(error + "\n");
                                return;
                            }
                        }
                        /* Since no error occurred, attempt everything else in a try/catch. */
                        try {
                            transaction = response_json.getJSONArray("images").getJSONObject(0).getJSONObject("transaction");
                            String status = transaction.getString("status");
                            double confidence = Double.parseDouble((transaction.getString("confidence")));
                            String best_candidate =  transaction.getString("subject_id");
                            /* If face is recognized */
                            if(confidence >= mThreshold ){
                                mTextView.setText( "Face is: "+ best_candidate + " with confidence: " + confidence);
                            send_wristband(MATCH_FOUND);
                            send_wristband(best_candidate+"\n");
                            }
                            /* If face is not recognized. */
                            else{
                              mTextView.setText("A new face!");
                            send_wristband(MATCH_FOUND);
                            send_wristband("A new face!" + "\n");
                            }
                        }
                        /* Only occurs if API returns a previously unseen type of non-error response. */
                        catch (JSONException e) {
                            e.printStackTrace();
                            Log.d(LOG_TAG,"JSON exception.");
                            mTextView.setText("JSON exception.");
                             send_wristband(NO_MATCH);
                             send_wristband("JSON error occurred." + "\n");
                        }
                    }
                }, new Response.ErrorListener() {
            @Override
            public void onErrorResponse(
                VolleyError error) {
                mTextView.setText(mCurrentPhotoPath +"That didn't work!");
            }
        }){

            /** Passing some request headers* */
            @Override
            // online tutorial uses some strange syntax in the angular brackets. Changed it to this instead.
            public Map<String, String> getHeaders() throws AuthFailureError {
                HashMap<String, String> headers = new HashMap<String, String>();
                headers.put("Content-Type", "application/json");
                headers.put("app_id","a1731ed8");
                headers.put("app_key", "d3a579a339de2805b54e53dcd72ee40c");
                return headers;
            }
        };


    // Add the request to the RequestQueue.
        mRequestQueue.add(jReq);
        Log.d(LOG_TAG, "Sent a Recognize Request!");

    }
}
