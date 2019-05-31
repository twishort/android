package com.likeitstudio.twishort;

import android.app.ActionBar;
import android.app.Application;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.database.ContentObserver;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.provider.Settings;
import android.support.v7.app.ActionBarActivity;
import android.util.Base64;
import android.util.Log;
import android.util.TypedValue;
import android.view.Display;
import android.view.Surface;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.Toast;

import com.likeitstudio.helper.Image;
import com.likeitstudio.helper.Wait;
import com.likeitstudio.helper.social.Twishort;

import org.scribe.model.Token;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.nio.channels.FileChannel;


/**
 * Created on 18.02.14.
 */
public class DefaultApplication extends Application {

    public static final int REQUEST_CODE_IMAGE = 1;
    public static final int REQUEST_CODE_PHOTO_IMAGE = 2;
    public static final int REQUEST_CODE_PHOTO_VIDEO = 8;
    public static final int REQUEST_CODE_BROWSER = 3;
    public static final int REQUEST_CODE_SIGNIN = 4;
    public static final int REQUEST_CODE_SEND = 5;
    public static final int REQUEST_CODE_GEO = 6;
    public static final int REQUEST_CODE_GEO_SETTINGS = 7;
    public static final int REQUEST_CODE_PERMISSION_PHOTO = 100;
    public static final int REQUEST_CODE_PERMISSION_GEO = 101;

    public static final int IMAGE_SIZE = 800;

    private static Context context;
    private static ActionBarActivity activity;

    private static final String TOKEN = "token";
    private static final String USERNAME = "username";
    private static final String AVATAR = "avatar";
    private static final String TEXT = "text";
    private static final String TITLE = "title";

    public static Twishort twishort;

    public void onCreate() {
        super.onCreate();
        context = getApplicationContext();

        if (twishort == null) {
            twishort = new Twishort(context, getToken());
        }

        getContentResolver().registerContentObserver(
                Settings.System.getUriFor(Settings.System.ACCELEROMETER_ROTATION), true, new ContentObserver(new Handler()) {
                    @Override
                    public void onChange(boolean selfChange) {
                        if (! isWaiting()) {
                            if (isAutorotation()) {
                                unlockScreenOrientation();
                            } else {
                                lockScreenOrientation();
                            }
                        }
                    }
                });
    }

    public static Context getContext() {
        return DefaultApplication.context;
    }

    public static ActionBarActivity getCurrentActivity() {
        return activity;
    }

    public static void setActivity(ActionBarActivity act) {
        activity = act;
    }

    public static SharedPreferences getSharedPreferences() {
        return DefaultApplication.context.getSharedPreferences("com.tmh.twishort_prefernces", context.MODE_PRIVATE);
    }

    // Save & Restore

    public static Token getToken() {
        String tokenString = DefaultApplication.getSharedPreferences().getString(TOKEN, null);
        if (tokenString != null) {
            String[] tokenSplit = tokenString.split(";");
            Token token = new Token(tokenSplit[0], tokenSplit[1]);
            return token;
        }
        return null;
    }

    public static void setToken(Token token) {
        SharedPreferences.Editor editor = DefaultApplication.getSharedPreferences().edit();
        if (token == null) {
            editor.remove(TOKEN);
        } else {
            editor.putString(TOKEN, token.getToken() + ";" + token.getSecret());
        }
        editor.commit();
    }

    public static String getUsername() {
        return DefaultApplication.getSharedPreferences().getString(USERNAME, null);
    }

    public static void setUsername(String username) {
        SharedPreferences.Editor editor = DefaultApplication.getSharedPreferences().edit();
        if (username == null) {
            editor.remove(USERNAME);
        } else {
            editor.putString(USERNAME, username);
        }
        editor.commit();
    }

    public static Bitmap getAvatar() {

        String avatarString = DefaultApplication.getSharedPreferences().getString(AVATAR, null);
        Bitmap avatar = null;

        if (avatarString != null) {
            byte[] b = Base64.decode(avatarString, Base64.DEFAULT);
            InputStream is = new ByteArrayInputStream(b);
            avatar = BitmapFactory.decodeStream(is);
        }
        return avatar;
    }

    public static void setAvatar(Bitmap avatar) {
        SharedPreferences.Editor editor = DefaultApplication.getSharedPreferences().edit();

        if (avatar == null) {
            editor.remove(AVATAR);
        } else {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            avatar.compress(Bitmap.CompressFormat.JPEG, 100, baos);
            byte[] b = baos.toByteArray();
            editor.putString(AVATAR, Base64.encodeToString(b, Base64.DEFAULT));
        }
        editor.commit();
    }

    public static void setText(String text) {
        SharedPreferences.Editor editor = DefaultApplication.getSharedPreferences().edit();
        if (text == null) {
            editor.remove(TEXT);
        } else {
            editor.putString(TEXT, text.trim());
        }
        editor.commit();
    }

    public static String getText() {
        return DefaultApplication.getSharedPreferences().getString(TEXT, null);
    }

    public static void setTitle(String title) {
        SharedPreferences.Editor editor = DefaultApplication.getSharedPreferences().edit();
        if (title == null) {
            editor.remove(TITLE);
        } else {
            editor.putString(TITLE, title.trim());
        }
        editor.commit();
    }

    public static String getTitle() {
        return DefaultApplication.getSharedPreferences().getString(TITLE, null);
    }

    public static boolean isBackground() {
        File directory = (new ContextWrapper(context)).getDir(null, Context.MODE_PRIVATE);
        File file = new File(directory, "background.jpg");
        return file.exists();
    }

    public static void setBackground(File file) {
        File directory = (new ContextWrapper(context)).getDir(null, Context.MODE_PRIVATE);
        File dest = new File(directory, "background.jpg");
        if (dest.exists()) {
            dest.delete();
        }
        if (file != null) {
            setImage(Image.resampleFile(file, 1000), dest);
        }
    }

    public static Bitmap getBackground() {
        File directory = (new ContextWrapper(context)).getDir(null, Context.MODE_PRIVATE);
        File file = new File(directory, "background.jpg");
        if (file.exists()) {
            return getImage(file);
        }
        return null;
    }

    public static void copyFile(File src, File dst) {
        try {
            FileInputStream inStream = new FileInputStream(src);
            FileOutputStream outStream = new FileOutputStream(dst);
            FileChannel inChannel = inStream.getChannel();
            FileChannel outChannel = outStream.getChannel();
            inChannel.transferTo(0, inChannel.size(), outChannel);
            inStream.close();
            outStream.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void setImage(Bitmap image, File file) {
        try {
            FileOutputStream fos;
            fos = new FileOutputStream(file);
            image.compress(Bitmap.CompressFormat.JPEG, 90, fos);
            fos.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static Bitmap getImage(File file) {
        try {
            if (file.exists()) {
                BitmapFactory.Options options = new BitmapFactory.Options();
                options.inJustDecodeBounds = false;
                options.inPreferredConfig = Bitmap.Config.RGB_565;
                options.inDither = true;
                return BitmapFactory.decodeFile(file.getAbsolutePath(), options);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    // Toast

    public static void toast(int stringId) {
        DefaultApplication.toast(context.getResources().getString(stringId));
    }

    public static void toast(String string) {
        Toast.makeText(context, string, Toast.LENGTH_LONG).show();
    }

    // Helper

    public static boolean isLongActionBar() {
        return context.getResources().getDisplayMetrics().widthPixels > 400;
    }

    public static float dp2px(float dp) {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp, context.getResources().getDisplayMetrics());
    }

    // Keyboard

    public static void hideKeyboard() {
        if (activity == null
                || activity.getCurrentFocus() == null) {
            return;
        }
        InputMethodManager inputManager = (InputMethodManager)
                context.getSystemService(Context.INPUT_METHOD_SERVICE);
        inputManager.hideSoftInputFromWindow(activity.getCurrentFocus().getWindowToken(),
                InputMethodManager.HIDE_NOT_ALWAYS);

        activity.getCurrentFocus().clearFocus();
    }

    // Wait

    public static void showWait(int stringId) {
        lockScreenOrientation();
        Wait.show(stringId);
    }

    public static void hideWait() {
        if (isAutorotation()) {
            unlockScreenOrientation();
        }
        Wait.hide();
    }

    public static boolean isWaiting() {
        return Wait.isWaiting;
    }

    // Orientation

    private static boolean isAutorotation() {
        return android.provider.Settings.System.getInt(activity.getContentResolver(),
                Settings.System.ACCELEROMETER_ROTATION, 0) == 1;
    }

    public static void lockScreenOrientation() {
        if (activity == null) {
            return;
        }
        final int rotation = ((WindowManager) context.getSystemService(Context.WINDOW_SERVICE)).getDefaultDisplay().getOrientation();
        switch (rotation) {
            case Surface.ROTATION_0:
                activity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
                break;
            case Surface.ROTATION_90:
                activity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
                break;
            case Surface.ROTATION_180:
                activity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT);
                break;
            default:
                activity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE);
        }
    }

    public static void unlockScreenOrientation() {
        if (activity == null) {
            return;
        }
        activity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR);
    }
}
