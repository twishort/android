package com.likeitstudio.twishort;

import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.support.v4.view.MenuItemCompat;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.ProgressBar;

import com.likeitstudio.helper.Connection;


/**
 * Created on 14.02.14.
 */
public class WebActivity extends AppCompatActivity {

    public static final String URL = "url";
    public static final String REQUEST_CODE = "requestCode";

    private WebView wv;
    private Menu menu;
    private int requestCode;

    private void customizeInterface() {
        ActionBar bar = getSupportActionBar();
        bar.setBackgroundDrawable(new ColorDrawable(getResources().getColor(R.color.action_bar)));
        bar.setDisplayHomeAsUpEnabled(true);
        wv.setScrollBarStyle(View.SCROLLBARS_INSIDE_OVERLAY);
        wv.getSettings().setAllowFileAccess(false);
    }

    private void defineVariables() {
        DefaultApplication.setActivity(this);
        requestCode = getIntent().getIntExtra(REQUEST_CODE, -1);

        wv = (WebView)findViewById(R.id.web);
        WebSettings webSettings = wv.getSettings();
        webSettings.setJavaScriptEnabled(true);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.web);

        defineVariables();
        customizeInterface();

        if (Connection.isConnected(this)) {
            String url = getIntent().getStringExtra(URL);
            if (url != null) {
                wv.loadUrl(url);
                wv.setWebViewClient(webViewClient);
            }
        } else {
            Intent intent = new Intent();
            setResult(RESULT_CANCELED, intent);
            finish();
        }
    }

    // Keycode

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (event.getAction() == KeyEvent.ACTION_DOWN) {
            switch(keyCode) {
                case KeyEvent.KEYCODE_BACK:
                    if (wv.canGoBack() == true) {
                        wv.goBack();
                    } else {
                        finish();
                    }
                    return true;
            }
        }
        return super.onKeyDown(keyCode, event);
    }

    // Menu

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.web, menu);
        this.menu = menu;

        final MenuItem refreshItem = menu.findItem(R.id.web_refresh);
        final LayoutInflater factory = getLayoutInflater();
        final View entryView = factory.inflate(R.layout.actionbar_progress, null);
        ProgressBar spinner = (ProgressBar)entryView.findViewById(R.id.refresh);
        spinner.getIndeterminateDrawable().setColorFilter(Color.WHITE, android.graphics.PorterDuff.Mode.MULTIPLY);
        MenuItemCompat.setActionView(refreshItem, spinner);

        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
        } else {
            switch (item.getItemId()) {
                case R.id.backward:
                    back();
                    break;
                case R.id.forward:
                    forward();
                    break;
            }
        }
        return true;
    }

    // Action

    private void back() {
        if (wv.canGoBack() == true) {
            wv.goBack();
        }
    }

    private void forward() {
        if (wv.canGoForward() == true) {
            wv.goForward();
        }
    }

    public void setRefreshActionButtonState(final boolean refreshing) {
        if (menu == null) {
            return;
        }
        menu.findItem(R.id.web_refresh).setVisible(refreshing);
    }

    private void showWait() {
        setRefreshActionButtonState(true);
    }

    private void hideWait() {
        setRefreshActionButtonState(false);
    }

    // WebView

    WebViewClient webViewClient = new WebViewClient() {

        @Override
        public void onPageStarted(WebView view, String url, android.graphics.Bitmap favicon) {
            if (requestCode == DefaultApplication.REQUEST_CODE_SIGNIN
                    || requestCode == DefaultApplication.REQUEST_CODE_SEND
                    || requestCode == DefaultApplication.REQUEST_CODE_GEO) {
                Uri uri = Uri.parse(url);
                String verifier = uri.getQueryParameter("oauth_verifier");
                Intent i = new Intent();

                if (verifier != null) {
                    i.setData(Uri.parse(verifier));
                    setResult(RESULT_OK, i);
                    finish();
                    return;
                }
            }
            showWait();
        }

        @Override
        public void onPageFinished(WebView view, String url) {
            hideWait();
        }
    };
}
