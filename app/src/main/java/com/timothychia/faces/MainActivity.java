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
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Toast;

import com.android.volley.AuthFailureError;
import com.android.volley.NetworkResponse;
import com.android.volley.Request;
import com.android.volley.Response;
import com.android.volley.VolleyError;
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
    static final int REQUEST_IMAGE_CAPTURE = 1;

    private String app_id_string = "a1731ed8";
    private String app_key_string = "d3a579a339de2805b54e53dcd72ee40c";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mImageView = (ImageView) findViewById(R.id.imageView);

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
        String url_recognize = "https://api.kairos.com/recognize";
        final String gallery_name = "Office";

        //our custom volley request
        VolleyMultipartRequest volleyMultipartRequest = new VolleyMultipartRequest(Request.Method.POST, url_recognize,
                new Response.Listener<NetworkResponse>() {
                    @Override
                    public void onResponse(NetworkResponse response) {
                        try {
                            JSONObject obj = new JSONObject(new String(response.data));
                            // use toast to display the results
                            Toast.makeText(getApplicationContext(), obj.toString(), Toast.LENGTH_SHORT).show();
                        } catch (JSONException e) {
                            e.printStackTrace();
                        }
                    }
                },
                new Response.ErrorListener() {
                    @Override
                    public void onErrorResponse(VolleyError error) {
                        Toast.makeText(getApplicationContext(), error.getMessage(), Toast.LENGTH_SHORT).show();
                    }
                }) {
            // now overriding methods in order to set parameters and header

            // the class does contain mHeaders, but this way should work fine.
            @Override
            public Map<String, String> getHeaders() throws AuthFailureError {
                HashMap<String, String> headers = new HashMap<String, String>();
                headers.put("Content-Type", "application/json");
                headers.put("app_id",app_id_string);
                headers.put("app_key", app_key_string);
                return headers;
            }

            @Override
            protected Map<String, String> getParams() throws AuthFailureError {
                Map<String, String> params = new HashMap<>();
                params.put("gallery_name", gallery_name);
                return params;
            }

            /*
             * Here we are passing image by renaming it with a unique name
             * */
            @Override
            protected Map<String, DataPart> getByteData() {
                Map<String, DataPart> params = new HashMap<>();
                long imagename = System.currentTimeMillis();
                params.put("pic", new DataPart(imagename + ".png", getFileDataFromDrawable(bitmap)));
                return params;
            }
        };

        //adding the request to volley
        Volley.newRequestQueue(this).add(volleyMultipartRequest);
    }
}
