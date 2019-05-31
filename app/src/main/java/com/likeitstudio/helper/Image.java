package com.likeitstudio.helper;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import java.io.File;
import java.io.FileInputStream;

/**
 * Created on 16.06.17.
 */

public class Image {

    public static Bitmap resampleFile(File file, float size) {
        try {
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inJustDecodeBounds = true;

            BitmapFactory.decodeStream(new FileInputStream(file), null, o);

            // Find the correct scale value. It should be the power of 2.
            int scale = 1;
            while (o.outWidth / scale / 2 >= size &&
                    o.outHeight / scale / 2 >= size) {
                scale *= 2;
            }
            // Decode with inSampleSize
            BitmapFactory.Options o2 = new BitmapFactory.Options();
            o2.inSampleSize = scale;
            return BitmapFactory.decodeStream(new FileInputStream(file), null, o2);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    public static Bitmap resample(Bitmap bitmap, float size) {
        try {
            double width = bitmap.getWidth();
            double height = bitmap.getHeight();
            int scale = 1;
            while (width / scale / 2 >= size &&
                    height / scale / 2 >= size) {
                scale *= 2;
            }
            return Bitmap.createScaledBitmap(bitmap, (int) width / scale, (int) height / scale, false);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }
}
