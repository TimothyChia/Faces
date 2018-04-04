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
    private ImageView mImageView;
    private TextView mTextView;
    static final int REQUEST_IMAGE_CAPTURE = 1;

    private String app_id_string = "a1731ed8";
    private String app_key_string = "d3a579a339de2805b54e53dcd72ee40c";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mImageView = (ImageView) findViewById(R.id.imageView);
        mTextView = (TextView) findViewById(R.id.textView2);
        mTextView.setMovementMethod(new ScrollingMovementMethod());

        //adding click listener to button. Triggers the built in camera activity.
        findViewById(R.id.button_recognize).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                dispatchTakePictureIntent();
            }
        });

    }



    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQUEST_IMAGE_CAPTURE && resultCode == RESULT_OK) {

            // Code to get a content URI using the path we saved when first creating the most recent photo file.
            File photoFile = new File(mCurrentPhotoPath);
            //getting the image Uri
            Uri photoURI = FileProvider.getUriForFile(this,
                    "com.example.android.fileprovider",
                    photoFile);

            try {
                //getting bitmap object from uri
                Bitmap bitmap = MediaStore.Images.Media.getBitmap(this.getContentResolver(), photoURI);

                //displaying selected image to imageview
                mImageView.setImageBitmap(bitmap);

                //calling the method uploadBitmap to upload image
                uploadBitmap(bitmap);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
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

    // modifying this to do a Kairos recognize API call
    private void uploadBitmap(final Bitmap bitmap) {
        String recognize_url = "https://api.kairos.com/recognize";
        final String gallery_name = "Office";

        // A more complicated queue instantiation may be needed to make this safe from even orientation changes
        RequestQueue queue = Volley.newRequestQueue(this);


        // For some reason, the JSONObject.put method has to be wrapped in try/catch before android studio will take it
        JSONObject recognize_param = new JSONObject();
        try {
            recognize_param.put("gallery_name","People");
            recognize_param.put("threshold","0");
            recognize_param.put("image",Base64.encodeToString(getFileDataFromDrawable(bitmap), Base64.DEFAULT) );
        } catch (JSONException e) {
            e.printStackTrace();
        }

        JsonObjectRequest jReq = new JsonObjectRequest(Request.Method.POST, recognize_url,recognize_param,
                new Response.Listener() {
                    // online tutorial doesn't use "Object response", but android studio won't recognize it as an override otherwise
                    public void onResponse(Object response) {
                        // Display the first 500 characters of the response string.
                        mTextView.setText(mCurrentPhotoPath +"Response is: "+ response.toString());
                    }
                }, new Response.ErrorListener() {
            @Override
            public void onErrorResponse(
                    /**
                     * JsonObjectRequest takes in five paramaters
                     * Request Type - This specifies the type of the request eg: GET,POST
                     * URL          - This String param specifies the Request URL
                     * JSONObject   - This parameter takes in the POST parameters."null" in
                     *                  case of GET request.
                     * Listener     -This parameter takes in a implementation of Response.Listener()
                     *                 interface which is invoked if the request is successful
                     * Listener     -This parameter takes in a implementation of Error.Listener()
                     *               interface which is invoked if any error is encountered while processing
                     *               the request
                     **/VolleyError error) {
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
        queue.add(jReq);


    }
}
