package com.likeitstudio.belt;

import android.content.Context;
import android.os.Bundle;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.style.RelativeSizeSpan;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;

import com.likeitstudio.twishort.DefaultApplication;
import com.likeitstudio.twishort.R;

/**
 * Created on 11.06.17.
 */

public class TextBelt {

    private EditText etText;
    private SpannableString hint;
    private View.OnFocusChangeListener onTextEditFocusListener;

    public TextBelt() {
        etText = (EditText)DefaultApplication.getCurrentActivity().findViewById(R.id.message);
        etText.setOnFocusChangeListener(onTextEditFocus);
        etText.setText(DefaultApplication.getText());

        String hintString = DefaultApplication.getContext().getResources().getString(R.string.text_hint);
        hint = new SpannableString(hintString);
        hint.setSpan(new RelativeSizeSpan(0.8f), 0, hintString.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        etText.setHint(getText().length() > 0 ? null : hint);
    }

    public void clear() {
        DefaultApplication.hideKeyboard();
        DefaultApplication.setText(null);
        etText.setText(null);
    }

    public void save() {
        DefaultApplication.setText(getText());
    }

    public boolean canClose() {
        return etText.getText().length() == 0;
    }

    public void clearFocus() {
        if (etText.hasFocus()) {
            etText.clearFocus();
        }
    }

    public void onSaveInstanceState(Bundle savedInstanceState) {
        DefaultApplication.setTitle(getText());
    }

    public void onRestoreInstanceState(Bundle savedInstanceState) {
        setText(DefaultApplication.getText());
    }

    public void setOnKeyListener(View.OnKeyListener onKeyListener) {
        etText.setOnKeyListener(onKeyListener);
    }

    public void setOnFocusChangeListener(View.OnFocusChangeListener onFocusChangeListener) {
        onTextEditFocusListener = onFocusChangeListener;
    }

    public String getText() {
        return etText.getText().toString().trim();
    }

    public void setText(String text) {
        etText.setText(text);
        etText.setHint(text != null && text.length() > 0 ? null : hint);
    }

    // EditText

    View.OnFocusChangeListener onTextEditFocus = new View.OnFocusChangeListener() {
        @Override
        public void onFocusChange(View view, boolean b) {
            onTextEditFocusListener.onFocusChange(view, b);
            etText.setHint(b ? null : hint);
        }
    };

}
