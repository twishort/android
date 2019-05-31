package com.likeitstudio.helper.social;

import android.content.Context;
import android.graphics.Bitmap;
import android.os.AsyncTask;
import android.text.TextUtils;
import android.util.Log;

import org.apache.http.entity.mime.HttpMultipartMode;
import org.apache.http.entity.mime.MultipartEntity;
import org.apache.http.entity.mime.content.ByteArrayBody;
import org.apache.http.entity.mime.content.StringBody;
import org.apache.http.protocol.HTTP;
import org.json.JSONArray;
import org.json.JSONObject;
import org.scribe.builder.ServiceBuilder;
import org.scribe.builder.api.TwitterApi;
import org.scribe.model.OAuthRequest;
import org.scribe.model.Response;
import org.scribe.model.Token;
import org.scribe.model.Verb;
import org.scribe.model.Verifier;
import org.scribe.oauth.OAuthService;


import com.likeitstudio.helper.Connection;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Locale;
import java.util.Map;
import java.util.zip.Deflater;

/**
 * Created on 07.02.14.
 */
public class Twitter {

    private final String API_KEY = "";
    private final String API_SECRET = "";

    public final static String URL_HOME = "https://twitter.com";
    public final static String URL_PROFILE = "https://twitter.com/%s";
    public final static String URL_TWEET = "https://twitter.com/%s/status/%s";
    protected final String URL_VERIFY_CREDENTIALS = "https://api.twitter.com/1.1/account/verify_credentials.json";
    protected final String URL_STATUS_UPDATE = "https://api.twitter.com/1.1/statuses/update.json";
    protected final String URL_STATUS_UPLOAD_MEDIA = "https://upload.twitter.com//1.1/media/upload.json";
    protected final String URL_GEO_SEARCH = "https://api.twitter.com/1.1/geo/search.json";

    public static final int ERROR_ACCESS_TOKEN = 10;

    protected final int CHUNK_BYTES = 2097152;

    protected Context context;
    protected OAuthService service;
    protected Token requestToken;
    protected Token accessToken;
    protected JSONObject userInfo;
    protected Bitmap avatar;

    protected AuthorizationUrlSuccess authorizationUrlSuccess;
    protected UserInfoSuccess userInfoSuccess;
    protected AccessTokenSuccess accessTokenSuccess;

    static public class AuthorizationUrlSuccess {
        public void done(String url) {}
    }

    static public class UserInfoSuccess {
        public void done(JSONObject userInfo) {}
    }

    static public class AccessTokenSuccess {
        public void done(Token accessToken) {}
    }

    static public class AvatarSuccess {
        public void done(Bitmap avatar) {}
    }

    static public class Success {
        public void done(JSONObject response) {}
    }

    static public class SuccessString {
        public void done(String string) {}
    }

    static public class SuccessList {
        public void done(ArrayList list) {}
    }

    static public class SuccessArray {
        public void done(JSONArray response) {}
    }

    static public class Error {
        public void done(JSONObject response, int statusCode) {}
    }


    public Twitter(Context context, Token accessToken) {
        this.context = context;

        service = new ServiceBuilder()
                .provider(TwitterApi.class)
                .apiKey(API_KEY)
                .apiSecret(API_SECRET)
                .callback(URL_HOME)
                .build();

        setAccessToken(accessToken);
    }

    public void setAccessToken(Token accessToken) {
        this.accessToken = accessToken;
    }

    public boolean isAuthorized() {
        return accessToken != null;
    }

    public void getAuthorizationUrl(AuthorizationUrlSuccess authorizationUrlSuccess) {
        this.authorizationUrlSuccess = authorizationUrlSuccess;
        new AuthorizationUrlTask().execute();
    }

    public void getAccessToken(String verify, AccessTokenSuccess accessTokenSuccess) {
        if (accessToken != null) {
            accessTokenSuccess.done(accessToken);
        } else {
            this.accessTokenSuccess = accessTokenSuccess;
            new AccessTokenTask().execute(verify);
        }
    }

    public void getUserinfo(UserInfoSuccess userInfoSuccess) {
        if (userInfo != null) {
            userInfoSuccess.done(userInfo);
        } else {
            this.userInfoSuccess = userInfoSuccess;
            new UserInfoTask().execute();
        }
    }

    public void getPlaces(double latitude, double longitude, int count, SuccessArray success, Error error) {
        new PlacesTask().execute(latitude, longitude, count, success, error);
    }

    public void getAvatar(final AvatarSuccess avatarSuccess) {
        if (avatar != null) {
            avatarSuccess.done(avatar);
        } else {
            if (userInfo == null) {
                getUserinfo(new UserInfoSuccess() {
                    @Override
                    public void done(JSONObject userInfo) {
                        if (userInfo == null) {
                            avatarSuccess.done(null);
                        } else {
                            getAvatar(avatarSuccess);
                        }
                    }
                });
            } else {
                try {
                    String imageUrl = userInfo.getString("profile_image_url");
                    if (imageUrl != null
                            && imageUrl.length() > 0) {
                        Connection con = new Connection();
                        con.type = Connection.TYPE_IMAGE;
                        con.Get(imageUrl, new Connection.Success() {
                                    @Override
                                    public void done(Bitmap bitmap, int statusCode) {
                                        avatar = bitmap;
                                        avatarSuccess.done(bitmap);
                                    }
                                }, new Connection.Error() {
                                    @Override
                                    public void done(String response, JSONObject jsonResponse, int statusCode) {
                                        avatarSuccess.done(null);
                                    }
                                });
                    }
                } catch (Exception e) {
                    avatarSuccess.done(null);
                }
            }
        }
    }

    public void signout() {
        accessToken = null;
        requestToken = null;
        userInfo = null;
    }

    public void share(final String text, final double latitude, final double longitude, final String placeId,
                      final ArrayList<Bitmap> images, final File video, final Success success, final Error error) {
        if (accessToken == null) {
            if (error != null) {
                error.done(null, ERROR_ACCESS_TOKEN);
            }
        } else {
            final ArrayList<String> mediaIds = new ArrayList<>();

            if (images.size() > 0) {
                uploadImage(images, mediaIds, new SuccessList(){
                    @Override
                    public void done(ArrayList mediaIds) {
                        new ShareTask().execute(text, latitude, longitude, placeId, mediaIds, success, error);
                    }
                }, error);
                return;
            } else if (video != null) {
                uploadVideo(video, new SuccessString() {
                    @Override
                    public void done(String mediaId) {
                        mediaIds.add(mediaId);
                        new ShareTask().execute(text, latitude, longitude, placeId, mediaIds, success, error);
                    }
                }, error);
                return;
            }
            new ShareTask().execute(text, latitude, longitude, placeId, mediaIds, success, error);
        }
    }

    protected void uploadImage(final ArrayList<Bitmap> images, final ArrayList<String> mediaIds, final SuccessList success, final Error error) {
        new UploadImage().execute(images.get(mediaIds.size()), new SuccessString() {
            @Override
            public void done(String mediaId) {
                mediaIds.add(mediaId);
                if (mediaIds.size() < images.size()) {
                    uploadImage(images, mediaIds, success, error);
                } else {
                    success.done(mediaIds);
                }
            }
        }, error);
    }

    protected void uploadVideo(final File video, final SuccessString success, final Error error) {
        uploadVideo(video, null, -1, success, error);
    }

    protected void uploadVideo(final File video, final String mediaId, final int segment, final SuccessString success, final Error error) {
        // Segment -1 Init
        // Segment 0..n Upload chunk
        // Segment * CHUNK_BYTES >= length Finalize

        final long length = video.length();
        new UploadVideo().execute(video, mediaId, segment, new SuccessString() {
            @Override
            public void done(String mediaId) {
                if ((segment + 1) * CHUNK_BYTES < length) {
                    // Upload chunk
                    uploadVideo(video, mediaId, segment + 1, success, error);
                } else {
                    // Finalize
                    new UploadVideo().execute(null, mediaId, 0, new SuccessString() {
                        @Override
                        public void done(String mediaId) {
                            success.done(mediaId);
                        }
                    }, error);
                }
            }
        }, error);
    }


    // Signature

    protected String signature(Verb method, String url) {
        if (accessToken == null) {
            return null;
        }
        OAuthRequest request = new OAuthRequest(method, url);
        service.signRequest(accessToken, request);
        Map<String, String> headers = request.getHeaders();
        String signature = headers.get("Authorization");

        return signature;
    }

    // Task

    private class AuthorizationUrlTask extends AsyncTask<Void, Integer, String> {

        @Override
        protected String doInBackground(Void... params) {
            String url = null;
            try {
                if (requestToken == null) {
                    requestToken = service.getRequestToken();
                }
                url = service.getAuthorizationUrl(requestToken);
            } catch (Exception e) {
                e.printStackTrace();
            }
            return url;
        }

        @Override
        protected void onPostExecute(String result) {
            if (authorizationUrlSuccess != null) {
                authorizationUrlSuccess.done(result);
            }
        }
    }

    private class AccessTokenTask extends AsyncTask<String, Integer, Token> {

        @Override
        protected Token doInBackground(String... params) {
            try {
                if (requestToken == null) {
                    requestToken = service.getRequestToken();
                }
                accessToken = service.getAccessToken(requestToken, new Verifier(params[0]));
            } catch (Exception e) {
                e.printStackTrace();
            }
            return accessToken;
        }

        @Override
        protected void onPostExecute(Token token) {
            if (accessTokenSuccess != null) {
                accessTokenSuccess.done(token);
            }
        }
    }

    private class UserInfoTask extends AsyncTask<Void, Integer, JSONObject> {

        @Override
        protected JSONObject doInBackground(Void... params) {
            try {
                OAuthRequest request = new OAuthRequest(Verb.GET, URL_VERIFY_CREDENTIALS);
                service.signRequest(accessToken, request);
                Response response = request.send();
                if (response.getCode() == 200) {
                    userInfo = Connection.parseJSON(response.getBody());
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
            return userInfo;
        }

        @Override
        protected void onPostExecute(JSONObject result) {
            if (userInfoSuccess != null) {
                userInfoSuccess.done(result);
            }
        }
    }

    private class PlacesTask extends AsyncTask<Object, Integer, JSONArray> {

        SuccessArray success;
        Error error;
        int code;

        @Override
        protected JSONArray doInBackground(Object... params) {

            double latitude = (double) params[0];
            double longitude = (double) params[1];
            int count = (int) params[2];
            success = (SuccessArray) params[3];
            error = (Error) params[4];

            JSONArray places = null;
            try {
                String url = String.format(Locale.US, "%s?lat=%.6f&long=%.6f&max_results=%d&accuracy=1000&granularity=poi",
                        URL_GEO_SEARCH, latitude, longitude, count);

                OAuthRequest request = new OAuthRequest(Verb.GET, url);
                service.signRequest(accessToken, request);
                Response response = request.send();
                code = response.getCode();
                JSONObject json = Connection.parseJSON(response.getBody());
                if (code == 200) {
                    if (json.has("result")) {
                        places = (JSONArray) ((JSONObject)json.get("result")).get("places");
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
            return places;
        }

        @Override
        protected void onPostExecute(JSONArray result) {
            if (code == 200
                    && result != null) {
                if (success != null) {
                    success.done(result);
                }
            } else {
                if (error != null) {
                    error.done(null, code);
                }
            }
        }
    }

    private class ShareTask extends AsyncTask<Object, Integer, Void> {

        String text;
        double latitude;
        double longitude;
        String placeId;
        ArrayList<String> mediaIds;
        File video;
        Success success;
        Error error;
        Response response;
        int code;
        JSONObject json;

        @Override
        protected Void doInBackground(Object... params) {
            text = (String) params[0];
            latitude = (double) params[1];
            longitude = (double) params[2];
            placeId = (String) params[3];
            mediaIds = (ArrayList<String>) params[4];
            success = (Success) params[5];
            error = (Error) params[6];

            try {
                OAuthRequest request = new OAuthRequest(Verb.POST, URL_STATUS_UPDATE);
                request.addBodyParameter("status", text);
                if (placeId != null) {
                    request.addBodyParameter("place_id", placeId);
                }
                if (latitude != 0 && longitude != 0) {
                    request.addBodyParameter("lat", String.format(Locale.US, "%.6f", latitude));
                    request.addBodyParameter("long", String.format(Locale.US, "%.6f", longitude));
                    request.addBodyParameter("display_coordinates", "true");
                }
                if (mediaIds.size() > 0) {
                    request.addBodyParameter("media_ids", TextUtils.join(",", mediaIds));
                }
                service.signRequest(accessToken, request);

                response = request.send();

                code = response.getCode();
                json = Connection.parseJSON(response.getBody());

            } catch (Exception e) {
                e.printStackTrace();
            }
            return null;
        }

        @Override
        protected void onPostExecute(Void result) {
            if (code == 200) {
                if (success != null) {
                    success.done(json);
                }
            } else {
                if (error != null) {
                    error.done(json, code);
                }
            }
        }
    }

    private class UploadImage extends AsyncTask<Object, Integer, Void> {

        SuccessString success;
        Error error;
        int code;
        JSONObject json;
        String mediaId;

        @Override
        protected Void doInBackground(Object... params) {
            Bitmap image = (Bitmap) params[0];
            success = (SuccessString) params[1];
            error = (Error) params[2];

            try {
                OAuthRequest request = new OAuthRequest(Verb.POST, URL_STATUS_UPLOAD_MEDIA);
                MultipartEntity entity = new MultipartEntity(HttpMultipartMode.BROWSER_COMPATIBLE);

                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                image.compress(Bitmap.CompressFormat.JPEG, 75, baos);
                byte[] data = baos.toByteArray();

                entity.addPart("media", new ByteArrayBody(data, "image/jpeg", "image.jpg"));

                ByteArrayOutputStream out = new ByteArrayOutputStream();
                entity.writeTo(out);

                request.addPayload(out.toByteArray());
                request.addHeader(entity.getContentType().getName(), entity.getContentType().getValue());

                service.signRequest(accessToken, request);

                Response response = request.send();

                code = response.getCode();
                json = Connection.parseJSON(response.getBody());
                mediaId = json.getString("media_id_string");

            } catch (Exception e) {
                e.printStackTrace();
            }
            return null;
        }

        @Override
        protected void onPostExecute(Void result) {
            if (code == 200
                    && mediaId.length() > 0) {
                if (success != null) {
                    success.done(mediaId);
                }
            } else {
                if (error != null) {
                    error.done(json, code);
                }
            }
        }
    }

    private class UploadVideo extends AsyncTask<Object, Integer, Void> {

        SuccessString success;
        Error error;
        int code;
        JSONObject json;
        String mediaId;

        @Override
        protected Void doInBackground(Object... params) {
            File video = (File) params[0];
            mediaId = (String) params[1];
            int segment = (int) params[2];
            success = (SuccessString) params[3];
            error = (Error) params[4];

            try {
                OAuthRequest request = new OAuthRequest(Verb.POST, URL_STATUS_UPLOAD_MEDIA);
                MultipartEntity entity = new MultipartEntity(HttpMultipartMode.BROWSER_COMPATIBLE);
                boolean isGif = video != null && video.getAbsolutePath().toLowerCase().endsWith(".gif");

                if (mediaId == null) {
                    // Init
                    request.addBodyParameter("command", "INIT");
                    request.addBodyParameter("media_type", isGif ? "image/gif" : "video/mp4");
                    request.addBodyParameter("media_category", isGif ? "tweet_gif" : "tweet_video");
                    request.addBodyParameter("total_bytes", "" + video.length());
                } else if (video == null) {
                    // Finalize
                    request.addBodyParameter("command", "FINALIZE");
                    request.addBodyParameter("media_id", mediaId);
                } else {
                    // Upload chunk
                    int start = segment * CHUNK_BYTES;
                    int length = (int) Math.min(video.length() - start, CHUNK_BYTES);
                    byte[] data = new byte[length];

                    BufferedInputStream bis = new BufferedInputStream(new FileInputStream(video));
                    DataInputStream dis = new DataInputStream(bis);
                    dis.read(data, start, length);

                    entity.addPart("command", new StringBody("APPEND"));
                    entity.addPart("media_id", new StringBody(mediaId));
                    entity.addPart("segment_index", new StringBody("" + segment));

                    entity.addPart("media", new ByteArrayBody(data,
                            isGif ? "image/gif" : "video/mp4", isGif ? "image.gif" : "video.mp4"));

                    ByteArrayOutputStream out = new ByteArrayOutputStream();
                    entity.writeTo(out);

                    request.addPayload(out.toByteArray());
                    request.addHeader(entity.getContentType().getName(), entity.getContentType().getValue());
                }

                service.signRequest(accessToken, request);

                Response response = request.send();

                code = response.getCode();
                json = Connection.parseJSON(response.getBody());

                if (mediaId == null) {
                    // Init
                    mediaId = json.getString("media_id_string");
                }

            } catch (Exception e) {
                e.printStackTrace();
            }
            return null;
        }

        @Override
        protected void onPostExecute(Void result) {
            if (code >= 200 && code <= 299
                    && mediaId.length() > 0) {
                if (success != null) {
                    success.done(mediaId);
                }
            } else {
                if (error != null) {
                    error.done(json, code);
                }
            }
        }
    }

}
