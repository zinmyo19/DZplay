package com.zynelabs.dzplay.data;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.Looper;
import android.util.LruCache;
import android.widget.ImageView;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Tiny async image loader with memory cache. No dependencies. */
public class ImageLoader {
    private static final LruCache<String, Bitmap> cache;
    private static final ExecutorService pool = Executors.newFixedThreadPool(4);
    private static final Handler main = new Handler(Looper.getMainLooper());

    static {
        int memKb = (int) (Runtime.getRuntime().maxMemory() / 1024);
        cache = new LruCache<String, Bitmap>(memKb / 8) {
            @Override protected int sizeOf(String k, Bitmap b) {
                return b.getByteCount() / 1024;
            }
        };
    }

    public static void clear() {
        cache.evictAll();
    }

    public static void load(final String url, final ImageView iv, final int placeholder) {
        load(url, iv, placeholder == 0 ? null
                : iv.getResources().getDrawable(placeholder, null));
    }

    /** Same, with a Drawable placeholder (e.g. per-category fallback art). */
    public static void load(final String url, final ImageView iv,
                            final android.graphics.drawable.Drawable placeholder) {
        if (url == null || url.isEmpty()) {
            iv.setImageDrawable(placeholder);
            iv.setTag(null);
            return;
        }
        Bitmap hit = cache.get(url);
        iv.setTag(url);
        if (hit != null) {
            iv.setImageBitmap(hit);
            return;
        }
        iv.setImageDrawable(placeholder);
        pool.execute(new Runnable() {
            @Override public void run() {
                final Bitmap bmp = fetch(url);
                if (bmp != null) cache.put(url, bmp);
                main.post(new Runnable() {
                    @Override public void run() {
                        if (url.equals(iv.getTag())) {
                            if (bmp != null) iv.setImageBitmap(bmp);
                            else iv.setImageDrawable(placeholder);
                        }
                    }
                });
            }
        });
    }

    private static Bitmap fetch(String urlStr) {
        HttpURLConnection con = null;
        try {
            URL url = new URL(urlStr);
            con = (HttpURLConnection) url.openConnection();
            con.setConnectTimeout(10000);
            con.setReadTimeout(15000);
            con.setRequestProperty("User-Agent", "ZyneLabsIPTV/1.0");
            InputStream in = con.getInputStream();
            // downsample to ~192px to save memory
            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            // need a fresh stream for real decode; read fully once
            byte[] data = readAll(in);
            in.close();
            BitmapFactory.decodeByteArray(data, 0, data.length, bounds);
            int sample = 1;
            int w = bounds.outWidth;
            while (w / (sample * 2) >= 192) sample *= 2;
            BitmapFactory.Options opts = new BitmapFactory.Options();
            opts.inSampleSize = sample;
            return BitmapFactory.decodeByteArray(data, 0, data.length, opts);
        } catch (Exception e) {
            return null;
        } finally {
            if (con != null) con.disconnect();
        }
    }

    private static byte[] readAll(InputStream in) throws Exception {
        java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) > 0) bos.write(buf, 0, n);
        return bos.toByteArray();
    }
}
