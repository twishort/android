package com.likeitstudio.helper.social;

import android.content.Context;
import android.graphics.Bitmap;
import android.util.Log;

import com.likeitstudio.helper.Connection;

import org.json.JSONArray;
import org.json.JSONObject;
import org.scribe.model.Token;
import org.scribe.model.Verb;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.concurrent.Exchanger;


/**
 * Created on 07.02.14.
 */
public class Twishort extends Twitter {

    public final static String URL_HOME = "http://twishort.com";
    public final static String URL_PROFILE = "http://twishort.com/user/%s";
    public final static String URL_AUTH = "https://twishort.com/auth?return=%s";
    public final static String SUBPATH_USER = "/user/%s";
    private final String API_KEY = "";
    private final String URL_POST = "http://api.twishort.com/1.1/post.json";
    private final String URL_UPDATE = "http://api.twishort.com/1.1/update_ids.json";

    private final String HEADER_SERVICE_PROVIDER = "X-Auth-Service-Provider";
    private final String HEADER_CREDENTIALS = "X-Verify-Credentials-Authorization";

    private final String PARAM_API_KEY = "api_key";
    private final String PARAM_TEXT = "text";
    private final String PARAM_TITLE = "title";
    private final String PARAM_ID = "id";
    private final String PARAM_TWEET_ID = "tweet_id";
    private final String PARAM_MEDIA = "media";
    private final String PARAM_PLACE = "place";
    private final String RESPONSE_ID_STR = "id_str";
    private final String RESPONSE_TEXT = "text_to_tweet";
    private final String RESPONSE_ERROR = "errors";
    private final String RESPONSE_CODE = "code";

    public static final int ERROR_INTERNET = 21;
    public static final int ERROR_SIGN = 22;
    public static final int ERROR_SEND_TWISHORT = 23;
    public static final int ERROR_SEND_TWISHORT_DECODE = 24;
    public static final int ERROR_SEND_TWITTER = 25;
    public static final int ERROR_SEND_TWITTER_ID = 26;
    public static final int ERROR_UPDATE = 28;
    public static final int ERROR_SEND_TWITTER_LIMIT = 152;
    public static final int ERROR_SEND_TWITTER_DUPLICATE = 187;

    String signature;
    private String lastTwitterId;

    static public class Success {
        public void done() {}
    }

    static public class Error {
        public void done(int statusCode) {}
    }

    public Twishort(Context context, Token accessToken) {
        super(context, accessToken);
    }

    protected int getResponseCode(JSONObject jsonResponse) {
        try {
            JSONArray errors = jsonResponse.getJSONArray(RESPONSE_ERROR);
            if (errors != null
                    && errors.length() > 0) {
                int code = (int) ((JSONObject)errors.getJSONObject(0)).getInt(RESPONSE_CODE);
                if (code > 0) {
                    return code;
                }
            }
        } catch (Exception e) {}

        return -1;
    }

    public String getLastTwitterId() {
        return lastTwitterId;
    }

    public void send(final String text, final String title, final double latitude, final double longitude,
                     final String placeId, final ArrayList<Bitmap> images, final File video,
                     final Success success, final Error error) {
        Connection con = new Connection(context);

        if (!con.isConnected()) {
            if (error != null) {
                error.done(ERROR_INTERNET);
            }
            return;
        }
        sendTwishort(text, title, latitude, longitude, placeId, images, video, success, error);
    }

    // 1 Twishort

    public void sendTwishort(final String text, final String title, final double latitude, final double longitude,
                             final String placeId, final ArrayList<Bitmap> images, final File video,
                             final Success success, final Error error) {

        signature = signature(Verb.GET, URL_VERIFY_CREDENTIALS);

        if (signature == null || signature == "") {
            if (error != null) {
                error.done(ERROR_SIGN);
            }
            return;
        }

        Connection con = new Connection(context);

        HashMap<String, String> params = new HashMap<String,String>();
        params.put(PARAM_API_KEY, API_KEY);
        params.put(PARAM_TEXT, text);
        params.put(PARAM_MEDIA, (images.size() > 0  || video != null ? "1" : "0"));
        if (title.length() > 0) {
            params.put(PARAM_TITLE, title);
        }

        HashMap<String, String> headers = new HashMap<String, String>();
        headers.put(HEADER_SERVICE_PROVIDER, URL_VERIFY_CREDENTIALS);
        headers.put(HEADER_CREDENTIALS, signature);

        con.Post(URL_POST, params, headers, new Connection.Success() {
                    @Override
                    public void done(String response, JSONObject jsonResponse, int statusCode) {
                        try {
                            String twishortText = jsonResponse.getString(RESPONSE_TEXT);
                            String twishortId = jsonResponse.getString(PARAM_ID);
                            sendTwitter(twishortId, twishortText, latitude, longitude, placeId, images, video, success, error);
                        } catch (Exception e) {
                            if (error != null) {
                                error.done(ERROR_SEND_TWISHORT_DECODE);
                            }
                        }
                    }
                }, new Connection.Error() {
                    @Override
                    public void done(String response, JSONObject jsonResponse, int statusCode) {
                        if (error != null) {
                            int code = getResponseCode(jsonResponse) == 152 ? ERROR_SIGN : ERROR_SEND_TWISHORT;
                            error.done(code);
                        }
                    }
                });
    }

    // 2 Twitter

    private void sendTwitter(final String twishortId, final String twishortText, final double latitude, final double longitude,
                             final String placeId, final ArrayList<Bitmap> images, final File video,
                             final Success success, final Error error) {
        share(twishortText, latitude, longitude, placeId, images, video, new Twitter.Success() {
                    @Override
                    public void done(JSONObject response) {
                        try {
                            String twitterId = response.getString(RESPONSE_ID_STR);
                            lastTwitterId = twitterId;

                            String media = null;
                            try {
                                JSONArray mediaArray = response.has("extended_entities") ? (JSONArray) ((JSONObject) response.get("extended_entities")).get("media") : null;
                                media = mediaArray != null && mediaArray.length() > 0 ? mediaArray.toString() : null;
                            } catch (Exception e) {}

                            String place = null;
                            try {
                                JSONObject placeObject = response.has("place") ? (JSONObject) response.get("place") : null;
                                place = placeObject != null ? placeObject.toString() : null;
                            } catch (Exception e) {}

                            updateTwishort(twishortId, twitterId, media, place, success, error);
                        } catch (Exception e) {
                            if (error != null) {
                                error.done(ERROR_SEND_TWITTER_ID);
                            }
                        }
                    }
                }, new Twitter.Error() {
                    @Override
                    public void done(JSONObject response, int statusCode) {
                        if (error != null) {
                            error.done(statusCode);
                        }
                    }
                });
    }

    // 3 Twishort

    private void updateTwishort(String twishortId, String twitterId, String media, String place, final Success success, final Error error) {

        Connection con = new Connection();

        HashMap<String, String> params = new HashMap<String,String>();
        params.put(PARAM_API_KEY, API_KEY);
        params.put(PARAM_ID, twishortId);
        params.put(PARAM_TWEET_ID, twitterId);
        if (media != null) {
            params.put(PARAM_MEDIA, media);
        }
        if (place != null) {
            params.put(PARAM_PLACE, place);
        }

        HashMap<String, String> headers = new HashMap<String, String>();
        headers.put(HEADER_SERVICE_PROVIDER, URL_VERIFY_CREDENTIALS);
        headers.put(HEADER_CREDENTIALS, signature);

        con.Post(URL_UPDATE, params, headers, new Connection.Success() {
                    @Override
                    public void done(String response, JSONObject jsonResponse, int statusCode) {
                        if (success != null) {
                            success.done();
                        }
                    }
                }, new Connection.Error() {
                    @Override
                    public void done(String response, JSONObject jsonResponse, int statusCode) {
                        if (error != null) {
                            error.done(ERROR_UPDATE);
                        }
                    }
                });
    }

}
