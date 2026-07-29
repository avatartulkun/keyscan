package com.secureqr.scanner.utils;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

/** Bounds memory use when displaying user-provided or restored images. */
public final class BitmapDecodeHelper {
    private BitmapDecodeHelper() {}

    public static Bitmap decodeFile(String path, int maxDimension) {
        if (path == null || path.trim().isEmpty()) return null;
        int target = Math.max(64, maxDimension);
        try {
            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(path, bounds);
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null;

            int sample = 1;
            while (bounds.outWidth / sample > target * 2 || bounds.outHeight / sample > target * 2) {
                sample *= 2;
            }
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inSampleSize = Math.max(1, sample);
            options.inPreferredConfig = Bitmap.Config.ARGB_8888;
            return BitmapFactory.decodeFile(path, options);
        } catch (OutOfMemoryError | RuntimeException ignored) {
            return null;
        }
    }
}
