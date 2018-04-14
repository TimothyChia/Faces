// also requires some modifications to the manifest (for the file provider) and a corresponding xml file. see https://developer.android.com/training/camera/photobasics.html
// and the layout needs a button to call dispatchTakePictureIntent

package com.timothychia.faces;

import android.content.Intent;
import android.graphics.Bitmap;
import android.media.Image;
import android.net.Uri;
import android.os.Environment;
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
import android.widget.Toast;

import com.android.volley.AuthFailureError;
import com.android.volley.NetworkResponse;
import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.Volley;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

public class MainActivity extends AppCompatActivity {
    public static final String EXTRA_MESSAGE = "com.example.myfirstapp.MESSAGE";
    private static final String LOG_TAG =  MainActivity.class.getSimpleName();
    private ImageView mImageView;
    private TextView mTextView;
    private EditText mEditText_newID;
    static final int REQUEST_IMAGE_CAPTURE = 1;

    private String app_id = "a1731ed8";
    private String app_key = "d3a579a339de2805b54e53dcd72ee40c";
    final String gallery_name = "People";

    final String  url_enroll = "https://api.kairos.com/enroll";
    final String url_recognize = "https://api.kairos.com/recognize";
    final String  url_gallery_list = "https://api.kairos.com/gallery/list_all";
    final String  url_gallery_view = "https://api.kairos.com/gallery/view";
    final String  url_view_subject = "https://api.kairos.com/gallery/view_subject";
    final String  url_gallery_remove = "https://api.kairos.com/gallery/remove";
    final String noPhoto = "No Photo";

    private RequestQueue mRequestQueue;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mImageView = (ImageView) findViewById(R.id.imageView);
        mTextView = (TextView) findViewById(R.id.textView2);
        mTextView.setMovementMethod(new ScrollingMovementMethod());

        mEditText_newID = (EditText) findViewById(R.id.editText_newID);

        //adding click listener to button. Triggers the built in camera activity.
        findViewById(R.id.button_recognize).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                dispatchTakePictureIntent();
            }
        });

        //adding click listener to button. Should enroll the photo at mCurrPhotoPath
        findViewById(R.id.button_enroll).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Bitmap bitmap = getCurrentBitmap();
                if(bitmap == null) {
                    Log.d(LOG_TAG, "Error enrolling. Bitmap is null.");
                    return;
            }
                enroll(bitmap);
            }
        });

        //adding click listener to button. Should enroll the photo at mCurrPhotoPath
        findViewById(R.id.button_database).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                manage_database();
            }
        });


        // for now, using noPhoto as a way to maintain information about whether an image exists to be recognized or enrolled
        mCurrentPhotoPath = noPhoto;

        // A more complicated queue instantiation may be needed to make this safe from even orientation changes
//        mRequestQueue = Volley.newRequestQueue(this);

        // attempting the more complicated version.
        mRequestQueue =  RequestQueueSingleton.getInstance(this.getApplicationContext()).getRequestQueue();

        // temp to test bluetooth
        test_bluetooth();
    }

private void manage_database(){
    Intent intent = new Intent(this, DatabaseManagementActivity.class);
    startActivity(intent);
}

    private void test_bluetooth(){
        Intent intent = new Intent(this, BluetoothTest.class);
        startActivity(intent);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQUEST_IMAGE_CAPTURE && resultCode == RESULT_OK) {
                Bitmap bitmap = getCurrentBitmap();
                if(bitmap != null) {
                    mImageView.setImageBitmap(bitmap);
                    uploadBitmap(bitmap);
                }
            }

    }

    // returns the bitmap stored at the file located by mCurrentPhotoPath
    // currently returns null if something goes wrong
    // should maybe have it throw IO exceptions etc.
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



    String mCurrentPhotoPath;
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
     * Using the photo currently stored in the file at mCurrentPhotoPath, enroll
     * */
    private String newID; // a user inputted string which contains a face's name. Additional information here too e.g. mutual friend.
    // function to add a face to the database
    private void enroll(final Bitmap bitmap){
        newID = mEditText_newID.getText().toString();
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
                        // cast the response and attempt to parse through it, using try catch and opt statements for errors/exceptions

                        JSONObject response_json = (JSONObject) response;
                        JSONObject transaction = new JSONObject();

                        try {
                            transaction = response_json.getJSONArray("images").getJSONObject(0).getJSONObject("transaction");
                        } catch (JSONException e) {
                            // should only happen when there's no faces in the image, or Kairos detected other errors
                            e.printStackTrace();
                            Log.d(LOG_TAG,"JSON exception");
                        }
                        String status = transaction.optString("status","error getting status");
                        Log.d(LOG_TAG, "status:" + status);


                        if( status.equals("error getting status"))
                            mTextView.setText("An error occured..");
                        else if( status.equals( "failure") )
                            mTextView.setText("No face found");
                        else {
                            String best_candidate =  transaction.optString("subject_id","failed to find subject_id in json");
                            mTextView.setText(mCurrentPhotoPath +"Face is: "+ best_candidate);
                        }

                        // Print the entire response to the debugging log.
                        Log.d(LOG_TAG, "Response received to Recognize request:" + response.toString());
                        Log.d(LOG_TAG, "status:" + status);
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
