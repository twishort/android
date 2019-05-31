package com.likeitstudio.twishort;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ContentResolver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Environment;
import android.provider.MediaStore;
import android.provider.Settings;
import android.support.v7.app.ActionBarActivity;
import android.os.Bundle;
import android.util.Base64;
import android.util.Log;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.Toast;

import com.likeitstudio.belt.GeoBelt;
import com.likeitstudio.belt.PhotoBelt;
import com.likeitstudio.belt.TextBelt;
import com.likeitstudio.belt.TitleBelt;
import com.likeitstudio.belt.ToolbarBelt;
import com.likeitstudio.helper.Connection;
import com.likeitstudio.helper.MediaPath;
import com.likeitstudio.helper.social.Twishort;
import com.likeitstudio.helper.social.Twitter;

import org.scribe.model.Token;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;


public class MainActivity extends ActionBarActivity {

    private GeoBelt geoBelt;
    private TextBelt textBelt;
    private TitleBelt titleBelt;
    private PhotoBelt photoBelt;
    private ToolbarBelt toolbarBelt;

    private AlertDialog.Builder alertDialog;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        DefaultApplication.setActivity(this);

        setContentView(R.layout.main);
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);

        geoBelt = new GeoBelt();
        textBelt = new TextBelt();
        titleBelt = new TitleBelt();
        photoBelt = new PhotoBelt();
        toolbarBelt = new ToolbarBelt();
        textBelt.setOnKeyListener(onTextEditKey);
        textBelt.setOnFocusChangeListener(onTextEditFocus);
        titleBelt.setOnKeyListener(onTextEditKey);
        titleBelt.setOnFocusChangeListener(onTextEditFocus);
        geoBelt.setAuthorizeMethod(new Runnable() {
            @Override
            public void run() {
                DefaultApplication.showWait(R.string.auth_dialog);
                toolbarBelt.signin(DefaultApplication.REQUEST_CODE_GEO);
            }
        });

        shareWithTwishort();
    }

    @Override
    protected void onResume() {
        DefaultApplication.setActivity(this);
        super.onResume();
    }

    @Override
    protected void onDestroy() {
        textBelt.save();
        titleBelt.save();
        super.onDestroy();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if ((grantResults.length > 0)
                && (grantResults[0] == PackageManager.PERMISSION_GRANTED)) {
            switch (requestCode) {
                case DefaultApplication.REQUEST_CODE_PERMISSION_PHOTO:
                    photoBelt.show();
                    break;
                case DefaultApplication.REQUEST_CODE_PERMISSION_GEO:
                    geoBelt.show();
                    break;
            }
        }
    }

    // Lock screen touch on waiting

    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        return DefaultApplication.isWaiting() ? false : super.dispatchTouchEvent(ev);
    }

    // Save & Restore

    @Override
    public void onSaveInstanceState(Bundle savedInstanceState) {
        DefaultApplication.hideKeyboard();

        textBelt.onSaveInstanceState(savedInstanceState);
        titleBelt.onSaveInstanceState(savedInstanceState);
        geoBelt.onSaveInstanceState(savedInstanceState);
        photoBelt.onSaveInstanceState(savedInstanceState);
        if (DefaultApplication.isWaiting()) {
            savedInstanceState.putBoolean("isWaiting", true);
            DefaultApplication.hideWait();
        }
        super.onSaveInstanceState(savedInstanceState);
    }

    @Override
    protected void onRestoreInstanceState(Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);

        textBelt.onRestoreInstanceState(savedInstanceState);
        titleBelt.onRestoreInstanceState(savedInstanceState);
        geoBelt.onRestoreInstanceState(savedInstanceState);
        photoBelt.onRestoreInstanceState(savedInstanceState);

        if (savedInstanceState.getBoolean("isWaiting")) {
            DefaultApplication.showWait(R.string.app_name);
        }
    }

    // Menu

    @Override
    public boolean onCreateOptionsMenu(final Menu menu) {
        getMenuInflater().inflate(R.menu.main, menu);
        toolbarBelt.createMenu(menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_logout:
                toolbarBelt.signout();
                break;
            case R.id.action_login:
                if (Connection.isConnected(this)) {
                    DefaultApplication.showWait(R.string.auth_dialog);
                    toolbarBelt.signin(DefaultApplication.REQUEST_CODE_SIGNIN);
                } else {
                    DefaultApplication.toast(R.string.error_internet);
                }
                break;
            case R.id.action_send:
                if (Connection.isConnected(this)) {
                    send();
                } else {
                    DefaultApplication.toast(R.string.error_internet);
                }
                break;
            case R.id.action_camera:
            case R.id.action_add_camera:
                photoBelt.toggle();
                break;
            case R.id.action_place:
            case R.id.action_add_place:
                if (geoBelt.getPlaceId() != null) {
                    geoBelt.showSelectPlace();
                } else {
                    geoBelt.toggle();
                }
                break;
            case R.id.action_title:
            case R.id.action_add_title:
                titleBelt.toggle();
                break;
            case R.id.action_twitter:
                openUrl(toolbarBelt.username == null ? Twitter.URL_HOME : String.format(Twitter.URL_PROFILE, toolbarBelt.username));
                break;
            case R.id.action_twishort:
                if (toolbarBelt.username == null) {
                    openUrl(Twishort.URL_HOME);
                } else {
                    try {
                        byte[] bytes = String.format(Twishort.SUBPATH_USER, toolbarBelt.username).getBytes("UTF-8");
                        openUrl(String.format(Twishort.URL_AUTH, Base64.encodeToString(bytes, Base64.DEFAULT)));
                    } catch (Exception e) {
                        openUrl(String.format(Twishort.URL_PROFILE, toolbarBelt.username));
                    }
                }
                break;
            case R.id.action_support:
                openUrl(getResources().getString(R.string.url_support));
                break;
            default:
                return super.onOptionsItemSelected(item);
        }
        return true;
    }

    // Back

    @Override
    public void onBackPressed() {
        backButtonHandler();
    }

    // Clear

    private void clear() {
        textBelt.clear();
        titleBelt.hide();
        photoBelt.hide();
        geoBelt.hide();
    }

    // Send

    private void send() {

        String text = textBelt.getText();

        if (text.length() == 0) {
            return;
        }

        DefaultApplication.showWait(R.string.send_dialog);

        if (! DefaultApplication.twishort.isAuthorized()) {
            toolbarBelt.signin(DefaultApplication.REQUEST_CODE_SEND);
            return;
        }

        DefaultApplication.twishort.send(
                text,
                titleBelt.getText(),
                geoBelt.getLatitude(),
                geoBelt.getLongitude(),
                geoBelt.getPlaceId(),
                photoBelt.getImages(),
                photoBelt.getVideo(),
                new Twishort.Success() {
                    @Override
                    public void done() {
                        DefaultApplication.hideWait();
                        showSuccessDialog(String.format(Twitter.URL_TWEET, toolbarBelt.username, DefaultApplication.twishort.getLastTwitterId()));
                        clear();
                    }
                }, new Twishort.Error() {
                    @Override
                    public void done(int statusCode) {
                        DefaultApplication.hideWait();

                        switch (statusCode) {
                            case Twishort.ERROR_INTERNET:
                                DefaultApplication.toast(R.string.error_internet);
                                break;
                            case Twishort.ERROR_SIGN:
                                DefaultApplication.toast(R.string.error_sign);
                                toolbarBelt.signout();
                                break;
                            case Twishort.ERROR_SEND_TWISHORT:
                                DefaultApplication.toast(R.string.error_twishort);
                                break;
                            case Twishort.ERROR_SEND_TWISHORT_DECODE:
                                DefaultApplication.toast(R.string.error_twishort_decode);
                                break;
                            case Twishort.ERROR_SEND_TWITTER:
                                DefaultApplication.toast(R.string.error_twitter);
                                break;
                            case Twishort.ERROR_SEND_TWITTER_ID:
                                DefaultApplication.toast(R.string.error_twitter_id);
                                break;
                            case Twishort.ERROR_UPDATE:
                                DefaultApplication.toast(R.string.success);
                                clear();
                                break;
                            case Twishort.ERROR_SEND_TWITTER_LIMIT:
                                DefaultApplication.toast(R.string.error_limit);
                                break;
                            case Twishort.ERROR_SEND_TWITTER_DUPLICATE:
                                DefaultApplication.toast(R.string.error_duplicate);
                                break;
                            default:
                                DefaultApplication.toast(getResources().getString(R.string.error_send) + " (" + statusCode + ")");
                        }
                    }
                });
    }

    // Share

    private String getFilePathFromContentUri(Uri selectedVideoUri) {
        String filePath;
        String[] filePathColumn = {MediaStore.MediaColumns.DATA};

        Cursor cursor = getContentResolver().query(selectedVideoUri, filePathColumn, null, null, null);
        cursor.moveToFirst();

        int columnIndex = cursor.getColumnIndex(filePathColumn[0]);
        filePath = cursor.getString(columnIndex);
        cursor.close();
        return filePath;
    }

    public void shareWithTwishort() {
        Intent intent = getIntent();
        String action = intent.getAction();
        String type = intent.getType();

        if (action == null
            || type == null) {
            return;
        }
        try {
            if (Intent.ACTION_SEND.equals(action)) {

                clear();

                if ("text/plain".equals(type)) {
                    String sharedText = intent.getStringExtra(Intent.EXTRA_TEXT);
                    textBelt.setText(sharedText);
                } else {
                    Uri uri = intent.getParcelableExtra(Intent.EXTRA_STREAM);
                    if (uri != null) {
                        String path = MediaPath.getFilePath(this, uri);
                        File file = new File(path);
                        //DefaultApplication.toast(file.getAbsolutePath());
                        //Log.e("!", file.getAbsolutePath());
                        if (file != null) {
                            photoBelt.addExtraMediaList(file);
                            photoBelt.show();
                        }
                    }
                }
            } else if (Intent.ACTION_SEND_MULTIPLE.equals(action)) {

            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // Request activity

    @Override
    protected void onActivityResult(final int requestCode, int resultCode, Intent data) {
        switch (requestCode) {
            case DefaultApplication.REQUEST_CODE_PHOTO_IMAGE:
            case DefaultApplication.REQUEST_CODE_PHOTO_VIDEO:
                if (resultCode == Activity.RESULT_OK) {
                    photoBelt.saveMedia(requestCode, data);
                    photoBelt.refresh();
                }
                break;
            case DefaultApplication.REQUEST_CODE_SIGNIN:
            case DefaultApplication.REQUEST_CODE_SEND:
            case DefaultApplication.REQUEST_CODE_GEO:
                if (resultCode == Activity.RESULT_OK) {
                    DefaultApplication.twishort.getAccessToken(data.getDataString(), new Twitter.AccessTokenSuccess() {
                        @Override
                        public void done(Token accessToken) {
                            DefaultApplication.setToken(accessToken);
                            toolbarBelt.loadUserInfo();
                            if (requestCode == DefaultApplication.REQUEST_CODE_SEND) {
                                send();
                            } else {
                                if (requestCode == DefaultApplication.REQUEST_CODE_GEO) {
                                    geoBelt.show();
                                }
                                DefaultApplication.hideWait();
                            }
                        }
                    });
                } else {
                    DefaultApplication.hideWait();
                    if (requestCode == DefaultApplication.REQUEST_CODE_GEO) {
                        geoBelt.hide();
                    }
                }
                break;
            case DefaultApplication.REQUEST_CODE_GEO_SETTINGS:
                if (geoBelt.isLocationEnabled()) {
                    geoBelt.show();
                }
                break;
        }
    }

    // Back

    public void backButtonHandler() {
        View view = getCurrentFocus();
        if (view != null) {
            InputMethodManager imm = (InputMethodManager)getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm.isAcceptingText()) {
                imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
                view.clearFocus();
                return;
            }
        }
        if (alertDialog != null) {
            return;
        }
        if (textBelt.canClose()
                && titleBelt.canClose()
                && !DefaultApplication.isWaiting()) {
            finish();
            return;
        }
        showExitDialog();
    }

    // Dialog

    private void showExitDialog() {
        alertDialog = new AlertDialog.Builder(MainActivity.this);
        alertDialog.setMessage(R.string.ask_quit);
        alertDialog.setTitle(R.string.app_name);
        alertDialog.setIcon(R.mipmap.ic_launcher);
        alertDialog.setPositiveButton(R.string.save_close,
                new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        //saveData();
                        finish();
                    }
                });
        alertDialog.setNegativeButton(R.string.cancel,
                new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.cancel();
                        alertDialog = null;
                    }
                });
        alertDialog.show();
    }

    private void showSuccessDialog(final String url) {
        alertDialog = new AlertDialog.Builder(MainActivity.this);
        alertDialog.setMessage(R.string.success);
        alertDialog.setIcon(R.mipmap.ic_launcher);
        alertDialog.setTitle(R.string.app_name);
        alertDialog.setPositiveButton(R.string.ok,
                new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.cancel();
                        alertDialog = null;
                    }
                });
        alertDialog.setNegativeButton(R.string.show_tweet,
                new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        openUrl(url);
                        dialog.cancel();
                        alertDialog = null;
                    }
                });
        alertDialog.show();
    }

    // Url

    private void openUrl(String url) {
        if (Connection.isConnected(this)) {
            Intent i = new Intent(this, WebActivity.class);
            i.putExtra(WebActivity.URL, url);
            startActivityForResult(i, DefaultApplication.REQUEST_CODE_BROWSER);
        } else {
            DefaultApplication.toast(R.string.error_internet);
        }
    }

    // Edit Text

    View.OnKeyListener onTextEditKey = new View.OnKeyListener() {
        @Override
        public boolean onKey(View view, int i, KeyEvent keyEvent) {
            if (i == KeyEvent.KEYCODE_BACK && keyEvent.getRepeatCount() == 0) {
                backButtonHandler();
                return true;
            }
            return false;
        }
    };

    View.OnFocusChangeListener onTextEditFocus = new View.OnFocusChangeListener() {
        @Override
        public void onFocusChange(View view, boolean b) {
            ((EditText)view).setCursorVisible(b);
            if (b) {
                if (!titleBelt.getShowState()) {
                    titleBelt.hideForInput();
                }
                geoBelt.hideForInput();
                photoBelt.hideForInput();

                // Show keyboard
                InputMethodManager imm = (InputMethodManager)DefaultApplication.getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
                if (! imm.isAcceptingText()) {
                    imm.toggleSoftInput(InputMethodManager.SHOW_FORCED, 0);
                }
            } else {
                ((EditText)view).setText(((EditText)view).getText().toString().trim());
                titleBelt.showIfNeeded();
                geoBelt.showIfNeeded();
                photoBelt.showIfNeeded();
            }
        }
    };

}
