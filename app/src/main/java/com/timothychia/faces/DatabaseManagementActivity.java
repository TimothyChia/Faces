package com.timothychia.faces;

import android.content.Context;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import com.android.volley.AuthFailureError;
import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.JsonObjectRequest;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.Map;

public class DatabaseManagementActivity extends AppCompatActivity {

    private static final String LOG_TAG =  "DATABASE_TAG";

    private RequestQueue mRequestQueue;
    private TextView mTextView;
    private EditText mEditText_removeID;


    private String app_id = "a1731ed8";
    private String app_key = "d3a579a339de2805b54e53dcd72ee40c";
    final String gallery_name = "People";


    final String  url_gallery_list = "https://api.kairos.com/gallery/list_all";
    final String  url_gallery_view = "https://api.kairos.com/gallery/view";
    final String  url_view_subject = "https://api.kairos.com/gallery/view_subject";
    final String  url_gallery_remove = "https://api.kairos.com/gallery/remove";
    final String  url_gallery_remove_subject = "https://api.kairos.com/gallery/remove_subject";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_database_management);

        mTextView = findViewById(R.id.textView2);
        mTextView.setMovementMethod(new ScrollingMovementMethod());
        mEditText_removeID = findViewById(R.id.editText_removeID);


        //adding click listener to button. Triggers the built in camera activity.
        findViewById(R.id.button_remove).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                remove_subject();
            }
        });

        //adding click listener to button. Triggers the built in camera activity.
        findViewById(R.id.button_list).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                gallery_list();
            }
        });


        mRequestQueue =  RequestQueueSingleton.getInstance(this.getApplicationContext()).getRequestQueue();



        gallery_list();
    }


    private void gallery_list(){
        // build json parameters
        JSONObject param = new JSONObject();
        try {
            param.put("gallery_name",gallery_name);
        } catch (JSONException e) {
            e.printStackTrace();
        }

        // build the POST request to enroll
        JsonObjectRequest jReq = new JsonObjectRequest(Request.Method.POST, url_gallery_view,param,
                new Response.Listener() {
                    // online tutorial doesn't use "Object response", but android studio won't recognize it as an override otherwise
                    public void onResponse(Object response) {
                        // Display the first 500 characters of the response string.
//                        mTextView.setText(mCurrentPhotoPath +"Enrolled with Response: "+ response.toString());
                        JSONObject response_json = (JSONObject) response;
                        try {
                            JSONArray subject_ids_json = response_json.getJSONArray("subject_ids");
                            String subject_ids_string =  subject_ids_json.join("\n");
                            mTextView.setText(subject_ids_string);
//                            Log.d(LOG_TAG, subject_ids_string);
                       } catch (JSONException e) {
                            mTextView.setText("An error occurred.");
                            Log.d(LOG_TAG, "Error in gallery_list().");
                            Log.d(LOG_TAG, "Response received to Recognize request:" + response.toString());
                            e.printStackTrace();
                        }
                    }
                }, new Response.ErrorListener() {
            @Override
            public void onErrorResponse(VolleyError error) {
//                mTextView.setText(mCurrentPhotoPath +"That didn't work!");
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

    // remove the subject in the gallery with a subject_id generated by...um...
    private void remove_subject(){

        String removeID = mEditText_removeID.getText().toString();
        if(removeID == null){
            print_toast("Please enter a name first!");
            return;
        }


        // build json parameters
        JSONObject param = new JSONObject();
        try {
            param.put("gallery_name",gallery_name);
            param.put("subject_id",removeID);
        } catch (JSONException e) {
            e.printStackTrace();
        }

        // build the POST request to enroll
        JsonObjectRequest jReq = new JsonObjectRequest(Request.Method.POST, url_gallery_remove_subject,param,
                new Response.Listener() {
                    // online tutorial doesn't use "Object response", but android studio won't recognize it as an override otherwise
                    public void onResponse(Object response) {
                        JSONObject response_json = (JSONObject) response;

                        /* An error has occurred, such as no faces in image.*/
                        if(response_json.optJSONArray("Errors") != null ){
                            String error = "Unknown error.";
                            try{
                                error = response_json.getJSONArray("Errors").getJSONObject(0).getString("Message");
                            } catch (JSONException e) {
                                error = "Unknown error. JSON Exception.";
                                e.printStackTrace();
                            }finally {
                                Log.d(LOG_TAG,"Remove subject response is an error. " + error);
                                print_toast("Remove subject response is an error. " + error);
                                return;
                            }
                        }
                        else
                        {
                            print_toast("Removed!");

                        }
                    }
                }, new Response.ErrorListener() {
            @Override
            public void onErrorResponse(VolleyError error) {
//                mTextView.setText(mCurrentPhotoPath +"That didn't work!");
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

    private void print_toast(String msg){
        Context context = getApplicationContext();
        CharSequence text = msg;
        int duration = Toast.LENGTH_SHORT;

        Toast toast = Toast.makeText(context, text, duration);
        toast.show();
    }
}
