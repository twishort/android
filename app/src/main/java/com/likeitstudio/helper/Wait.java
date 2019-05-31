package com.likeitstudio.helper;

import android.app.ProgressDialog;
import com.likeitstudio.twishort.DefaultApplication;
import com.likeitstudio.twishort.R;

/**
 * Created on 14.06.17.
 */

public class Wait {

    public static ProgressDialog waitDialog;
    public static int waitingStringId;
    public static boolean isWaiting = false;

    public static void show(int stringId) {
        waitingStringId = stringId;
        isWaiting = true;

        DefaultApplication.hideKeyboard();

        if (waitDialog == null) {
            waitDialog = ProgressDialog.show(DefaultApplication.getCurrentActivity(),
                    DefaultApplication.getContext().getResources().getString(waitingStringId),
                    DefaultApplication.getContext().getResources().getString(R.string.wait), true);
        }
    }

    public static void hide() {
        isWaiting = false;

        if (waitDialog != null) {
            waitDialog.dismiss();
            waitDialog = null;
        }
    }

}
