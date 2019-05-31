package com.likeitstudio.helper;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.AsyncTask;
import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLEncoder;
import java.util.HashMap;

/**
 * Created with IntelliJ IDEA.
 * Date: 29.09.13
 * Time: 17:36
 * To change this template use File | Settings | File Templates.
 */

public class Connection {

    public static final String METHOD_GET = "GET";
    public static final String METHOD_POST = "POST";
    public static final String METHOD_DELETE = "DELETE";
    public static final String METHOD_PUT = "PUT";

    public static final int TYPE_JSON = 0;
    public static final int TYPE_IMAGE = 1;
    public static final int TYPE_TEXT = 2;

    private static final String TAG = "Connection";

    final int timeout = 30000;

    protected Context context;

    public int type = TYPE_JSON;

    Success onSuccess;
    Error onError;



    public interface Done {
        public void done(String response, JSONObject jsonResponse, int statusCode);
    }

    static public class Success implements Done {
        public void done(String response, JSONObject jsonResponse, int statusCode) {}
        public void done(Bitmap bitmap, int statusCode) {}
    }

    static public class Error implements Done {
        public void done(String response, JSONObject jsonResponse, int statusCode) {}
    }


    public Connection(Context context) {
        this.context = context;
    }

    public Connection() {}

    public boolean isConnected() {
        return Connection.isConnected(context);
    }

    static public boolean isConnected(Context context) {
        ConnectivityManager connMgr = (ConnectivityManager)
                context.getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo networkInfo = connMgr.getActiveNetworkInfo();

        return networkInfo != null
                && networkInfo.isConnected();
    }

    public void Get(String url, Success onSuccess, Error onError) {
        Request(METHOD_GET, url, null, null, onSuccess, onError);
    }

    public void Post(String url, HashMap<String, String> params, HashMap<String, String> headers, Success onSuccess, Error onError) {
        Request(METHOD_POST, url, params, headers, onSuccess, onError);
    }

    public void Request(final String method, final String urlString, HashMap<String, String> params, HashMap<String, String> headers, final Success onSuccess, final Error onError) {
        this.onSuccess = onSuccess;
        this.onError = onError;

        RequestTask requestTask = new RequestTask();
        requestTask.execute(method, urlString, params, headers);
    }

    private class RequestTask extends AsyncTask<Object, Integer, Void>{

        int statusCode;
        String response;
        JSONObject jsonResponse;
        Bitmap bitmap;

        @Override
        protected void onPreExecute() {}

        @Override
        protected Void doInBackground(Object... args) {
            String method = (String)args[0];
            String urlString = (String)args[1];
            HashMap<String, String> params = (HashMap<String, String>)args[2];
            HashMap<String, String> headers = (HashMap<String, String>)args[3];

            try {

                Log.d(TAG, "Request : " + method + " " + urlString);

                InputStream in;

                HttpURLConnection http = OpenHttpConnection(method, urlString, params, headers);
                statusCode = http.getResponseCode();
                in = statusCode < 400 ? http.getInputStream() : http.getErrorStream();

                switch (type) {
                    case TYPE_JSON:
                        response = decodeText(in);
                        jsonResponse = parseJSON(response);
                        break;
                    case TYPE_IMAGE:
                        bitmap = decodeImage(in);
                        break;
                    default:
                        response = decodeText(in);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
            return null;
        }

        @Override
        protected void onProgressUpdate(Integer... progress) {}

        @Override
        protected void onPostExecute(Void result) {
            if (statusCode == 200) {
                if (onSuccess != null) {
                    if (type != TYPE_IMAGE) {
                        onSuccess.done(response, jsonResponse, statusCode);
                    } else {
                        onSuccess.done(bitmap, statusCode);
                    }
                }
            } else {
                if (onError != null) {
                    onError.done(response, jsonResponse, statusCode);
                }
            }
        }

        @Override
        protected void onCancelled() {}
    }

    private HttpURLConnection OpenHttpConnection(String method, String urlString, HashMap<String, String> params, HashMap<String, String> headers) throws IOException {

        URL url = new URL(urlString);
        URLConnection conn = url.openConnection();

        if (! (conn instanceof HttpURLConnection)) {
            throw new IOException("Not an HTTP connection");
        }
        try {
            HttpURLConnection httpConn = (HttpURLConnection)conn;
            httpConn.setAllowUserInteraction(false);
            httpConn.setInstanceFollowRedirects(true);
            httpConn.setRequestMethod(method);
            httpConn.setConnectTimeout(timeout);
            httpConn.setReadTimeout(timeout);

            if (headers != null
                    && headers.size() > 0) {
                for (HashMap.Entry<String, String> entity: headers.entrySet()) {
                    httpConn.setRequestProperty(entity.getKey(), entity.getValue());
                }
            }
            if (params != null
                && params.size() > 0) {
                StringBuilder postData = new StringBuilder();
                for (HashMap.Entry<String, String> param: params.entrySet()) {
                    if (postData.length() != 0) {
                        postData.append('&');
                    }
                    postData.append(URLEncoder.encode(param.getKey(), "UTF-8"));
                    postData.append('=');
                    postData.append(URLEncoder.encode(String.valueOf(param.getValue()), "UTF-8"));
                }
                byte[] postDataBytes = postData.toString().getBytes("UTF-8");

                httpConn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
                httpConn.setRequestProperty("Content-Length", String.valueOf(postDataBytes.length));
                httpConn.setDoOutput(true);

                httpConn.getOutputStream().write(postDataBytes);
            } else {
                httpConn.connect();
            }
            return httpConn;
        } catch (Exception e) {
            Log.d(TAG, e.getLocalizedMessage());
            throw new IOException("Can't connect");
        }
    }

    private String decodeText(InputStream in) {

//        String str = "";
//        try {
//            BufferedReader reader = new BufferedReader(new InputStreamReader(in, "UTF-8"));
//            for (int c; (c = reader.read()) >= 0; str += (char)c);
//            in.close();
//        } catch (Exception e) {
//            Log.d(TAG, e.getLocalizedMessage());
//        }
//        return str;

        int BUFFER_SIZE = 2000;

        InputStreamReader isr = new InputStreamReader(in);
        int charRead;
        String str = "";
        char[] inputBuffer = new char[BUFFER_SIZE];
        try {
            while ((charRead = isr.read(inputBuffer)) > 0) {
                String readString = String.copyValueOf(inputBuffer, 0, charRead);
                str += readString;
                inputBuffer = new char[BUFFER_SIZE];
            }
            in.close();
        } catch (IOException e) {
            Log.d(TAG, e.getLocalizedMessage());
            return "";
        }
        return str;
    }

    private Bitmap decodeImage(InputStream in) {
        Bitmap bitmap = null;
        try {
            bitmap = BitmapFactory.decodeStream(in);
        } catch (Exception e) {
            Log.d(TAG, e.getLocalizedMessage());
        }
        return bitmap;
    }

    public static JSONObject parseJSON(String jsonString) {
        try {
            Log.d(TAG, jsonString);
            if (jsonString.length() == 0) {
                jsonString = "{}";
            }
            JSONObject jsonObject = new JSONObject(jsonString);
            return jsonObject;
        } catch (JSONException e) {
            Log.e(TAG, "Can't parse JSON");
            e.printStackTrace();
            return null;
        }
    }

}
