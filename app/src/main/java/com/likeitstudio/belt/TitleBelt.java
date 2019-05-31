package com.likeitstudio.belt;

import android.os.Bundle;
import android.os.Handler;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.style.RelativeSizeSpan;
import android.util.Log;
import android.view.View;
import android.widget.EditText;

import com.likeitstudio.twishort.DefaultApplication;
import com.likeitstudio.twishort.R;

import java.util.Timer;
import java.util.TimerTask;

/**
 * Created on 11.06.17.
 */

public class TitleBelt {

    private EditText etTitle;
    private SpannableString hint;
    private View.OnFocusChangeListener onTextEditFocusListener;

    public TitleBelt() {
        etTitle = (EditText)DefaultApplication.getCurrentActivity().findViewById(R.id.title);
        etTitle.setOnFocusChangeListener(onTextEditFocus);
        etTitle.setText(DefaultApplication.getTitle());

        String hintString = DefaultApplication.getContext().getResources().getString(R.string.title_hint);
        hint = new SpannableString(hintString);
        hint.setSpan(new RelativeSizeSpan(0.8f), 0, hintString.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        if (getText().length() > 0) {
            //etTitle.setHint(null);
            showWithoutFocus();
        } else {
            etTitle.setHint(hint);
        }
    }

    public void save() {
        DefaultApplication.setTitle(getText());
    }

    public void clear() {
        DefaultApplication.hideKeyboard();
        DefaultApplication.setTitle(null);
        etTitle.setText(null);
        etTitle.setHint(hint);
    }

    public boolean canClose() {
        return etTitle.getText().length() == 0;
    }

    public void clearFocus() {
        if (etTitle.hasFocus()) {
            etTitle.clearFocus();
        }
    }

    public void toggle() {
        if (etTitle.getVisibility() == View.GONE) {
            show();
        } else {
            hide();
        }
    }

    public void show() {
        etTitle.setTag(1);
        etTitle.setVisibility(View.VISIBLE);
        etTitle.requestFocus();
    }

    public void showWithoutFocus() {
        etTitle.setTag(1);
        etTitle.setVisibility(View.VISIBLE);
    }

    public void showIfNeeded() {
        if (getShowState()) {
            etTitle.setVisibility(View.VISIBLE);
        }
    }

    public void hide() {
        etTitle.setTag(null);
        etTitle.setVisibility(View.GONE);
        clear();
    }

    public void hideForInput() {
        if (etTitle.getResources().getDisplayMetrics().heightPixels < 600) {
            etTitle.setVisibility(View.GONE);
        }
    }

    public String getText() {
        return etTitle.getText().toString().trim();
    }

    public void setText(String text) {
        etTitle.setText(text);
        etTitle.setHint(text != null && text.length() > 0 ? null : hint);
    }

    public void setShowState(boolean show) {
        etTitle.setTag(show ? 1 : null);
    }

    public boolean getShowState() {
        return etTitle.getTag() != null ? true : false;
    }

    public void setOnKeyListener(View.OnKeyListener onKeyListener) {
        etTitle.setOnKeyListener(onKeyListener);
    }

    public void setOnFocusChangeListener(View.OnFocusChangeListener onFocusChangeListener) {
        onTextEditFocusListener = onFocusChangeListener;
    }

    public void onSaveInstanceState(Bundle savedInstanceState) {
        DefaultApplication.setTitle(getText());
        if (getShowState()) {
            savedInstanceState.putBoolean(etTitle.isFocused() ? "showTitleWithFocus" : "showTitle", true);
        }
    }

    public void onRestoreInstanceState(Bundle savedInstanceState) {
        setText(DefaultApplication.getTitle());
        if (savedInstanceState.getBoolean("showTitleWithFocus")) {
            showWithoutFocus(); //show();
        } else if (savedInstanceState.getBoolean("showTitle")) {
            showWithoutFocus();
        }
    }

    // EditText

    View.OnFocusChangeListener onTextEditFocus = new View.OnFocusChangeListener() {
        @Override
        public void onFocusChange(View view, boolean b) {
            onTextEditFocusListener.onFocusChange(view, b);
            //etTitle.setHint(b ? null : hint);
            if (!b && getText().length() == 0) {
                hide();
            }
        }
    };
}
