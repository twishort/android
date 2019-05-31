package com.likeitstudio.belt;


import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.ColorDrawable;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.ActionBar;
import android.text.SpannableString;
import android.text.style.ForegroundColorSpan;
import android.util.Base64;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;

import com.likeitstudio.helper.Bitmaps;
import com.likeitstudio.helper.social.Twitter;
import com.likeitstudio.twishort.DefaultApplication;
import com.likeitstudio.twishort.R;
import com.likeitstudio.twishort.WebActivity;

import org.json.JSONObject;

/**
 * Created on 11.06.17.
 */

public class ToolbarBelt {

    public Menu menu;
    public String username;
    public Bitmap avatarBitmap;
    private boolean isLongActionBar;

    public ToolbarBelt() {
        ActionBar bar = DefaultApplication.getCurrentActivity().getSupportActionBar();
        bar.setBackgroundDrawable(new ColorDrawable(DefaultApplication.getContext().getResources().getColor(R.color.action_bar)));
        bar.setDisplayHomeAsUpEnabled(false);

        isLongActionBar = DefaultApplication.isLongActionBar();

        // Logo
        DefaultApplication.getCurrentActivity().getSupportActionBar().setLogo(R.drawable.ic_logo);
        DefaultApplication.getCurrentActivity().getSupportActionBar().setDisplayShowHomeEnabled(isLongActionBar);
        DefaultApplication.getCurrentActivity().getSupportActionBar().setDisplayUseLogoEnabled(isLongActionBar);

        username = DefaultApplication.getUsername();
        avatarBitmap = DefaultApplication.getAvatar();

        if (DefaultApplication.getToken() != null
                && username == null) {
            loadUserInfo();DefaultApplication.getCurrentActivity().getSupportActionBar().setLogo(R.drawable.ic_logo);
            DefaultApplication.getCurrentActivity().getSupportActionBar().setDisplayShowHomeEnabled(isLongActionBar);
            DefaultApplication.getCurrentActivity().getSupportActionBar().setDisplayUseLogoEnabled(isLongActionBar);
        }
    }

    public void createMenu(final Menu menu) {
        this.menu = menu;

        MenuItem addItem = menu.findItem(R.id.action_add);
        MenuItem cameraItem = menu.findItem(R.id.action_camera);
        MenuItem titleItem = menu.findItem(R.id.action_title);
        MenuItem placeItem = menu.findItem(R.id.action_place);
        final MenuItem sendItem = menu.findItem(R.id.action_send);
        addItem.setVisible(!isLongActionBar);
        cameraItem.setVisible(isLongActionBar);
        titleItem.setVisible(isLongActionBar);
        placeItem.setVisible(isLongActionBar);

        sendItem.getActionView().setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                menu.performIdentifierAction(sendItem.getItemId(), 0);
            }
        });

        changeUsername();
        changeAvatar();

        if (username != null
                && avatarBitmap == null) {
            loadAvatar();
        }
    }

    private void changeUsername() {
        MenuItem profileItem = menu.findItem(R.id.action_me);
        MenuItem logoutItem = menu.findItem(R.id.action_logout);
        MenuItem loginItem = menu.findItem(R.id.action_login);
        MenuItem twitterItem = menu.findItem(R.id.action_twitter);
        MenuItem twishortItem = menu.findItem(R.id.action_twishort);

        profileItem.setIcon(R.drawable.ic_action_profile);

        if (username != null) {
            logoutItem.setTitle(DefaultApplication.getContext().getResources().getString(R.string.logout) + " @" + username);
            logoutItem.setVisible(true);
            SpannableString s = new SpannableString(logoutItem.getTitle());
            s.setSpan(new ForegroundColorSpan(ContextCompat.getColor(DefaultApplication.getContext(), R.color.alert)), 0, s.length(), 0);
            logoutItem.setTitle(s);
            loginItem.setVisible(false);
        } else {
            logoutItem.setVisible(false);
            loginItem.setVisible(true);
        }
    }

    private void changeAvatar() {
        MenuItem imageItem = menu.findItem(R.id.action_me);

        if (avatarBitmap != null) {
            float size = DefaultApplication.dp2px(180);
            Bitmap newBitmap = Bitmaps.getResizedAndScaled(avatarBitmap, size, size);
            imageItem.setIcon(new BitmapDrawable(null, newBitmap));
        } else {
            imageItem.setIcon(R.drawable.ic_action_profile);
        }
    }

    public void signin(final int code) {

        DefaultApplication.twishort.getAuthorizationUrl(new Twitter.AuthorizationUrlSuccess() {
            @Override
            public void done(String url) {
                if (url == null
                        || url.length() == 0) {
                    DefaultApplication.hideWait();
                    DefaultApplication.toast(R.string.error_auth);
                    return;
                }
                Intent i = new Intent(DefaultApplication.getContext(), WebActivity.class);
                i.putExtra(WebActivity.URL, url);
                i.putExtra(WebActivity.REQUEST_CODE, code);
                DefaultApplication.getCurrentActivity().startActivityForResult(i, code);
            }
        });
    }

    public void signout() {
        username = null;
        DefaultApplication.setUsername(null);
        changeUsername();

        avatarBitmap = null;
        DefaultApplication.setAvatar(null);
        changeAvatar();

        DefaultApplication.setToken(null);
        DefaultApplication.twishort.signout();
    }

    public void loadUserInfo() {
        DefaultApplication.twishort.getUserinfo(new Twitter.UserInfoSuccess() {
            @Override
            public void done(JSONObject userInfo) {
                try {
                    username = userInfo.getString("screen_name");
                    DefaultApplication.setUsername(username);
                    changeUsername();
                } catch (Exception e) {
                    e.printStackTrace();
                }
                loadAvatar();
            }
        });
    }

    public void loadAvatar() {

        DefaultApplication.twishort.getAvatar(new Twitter.AvatarSuccess() {
            @Override
            public void done(Bitmap avatar) {
                avatarBitmap = avatar;
                DefaultApplication.setAvatar(avatar);
                changeAvatar();
            }
        });
    }

}
