package com.zynelabs.dzplay.data;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

/** Loads all channels for an account off the UI thread. Shared by the home screens. */
public class ChannelRepo {

    public interface Callback {
        void onResult(List<Channel> live, List<Channel> vod, List<Channel> series, String err);
    }

    /** Fresh-load progress phases, posted on the main thread. May be null. */
    public interface Progress {
        void onProgress(String phase);
    }

    public static void load(final Context ctx, final PlAccount acc, final Callback cb) {
        load(ctx, acc, null, cb);
    }

    public static void load(final Context ctx, final PlAccount acc,
                            final Progress prog, final Callback cb) {
        final Handler main = new Handler(Looper.getMainLooper());
        new Thread(new Runnable() {
            @Override public void run() {
                List<Channel> live = new ArrayList<>();
                List<Channel> vod = new ArrayList<>();
                List<Channel> series = new ArrayList<>();
                String err = null;
                // Catch Throwable, not just Exception: an OutOfMemoryError
                // while parsing a giant playlist used to kill this thread
                // silently, leaving the UI stuck on "Loading…" forever.
                try {
                    if ("m3u_url".equals(acc.type)) {
                        // Streaming path: download to a temp file, then parse
                        // line-by-line. The playlist is NEVER held in memory
                        // as one giant String (that was the OOM).
                        phase("Downloading playlist…");
                        final java.io.File tmp = java.io.File.createTempFile(
                                "pl", ".m3u", ctx.getCacheDir());
                        try {
                            XtreamClient.httpDownload(acc.url, tmp);
                            phase("Parsing channels…");
                            java.io.BufferedReader br = new java.io.BufferedReader(
                                    new java.io.InputStreamReader(
                                            new java.io.FileInputStream(tmp), "UTF-8"));
                            try {
                                splitM3u(M3uParser.parse(br, countListener()),
                                        live, vod);
                            } finally {
                                try { br.close(); } catch (Exception ignored) {}
                            }
                        } finally {
                            tmp.delete();
                        }
                    } else if ("m3u_file".equals(acc.type)) {
                        // Stream straight from the stored file — no
                        // read-it-all-into-a-String step.
                        phase("Reading playlist file…");
                        java.io.BufferedReader br = new java.io.BufferedReader(
                                new java.io.InputStreamReader(
                                        ctx.openFileInput(acc.file), "UTF-8"));
                        try {
                            phase("Parsing channels…");
                            splitM3u(M3uParser.parse(br, countListener()),
                                    live, vod);
                        } finally {
                            try { br.close(); } catch (Exception ignored) {}
                        }
                    } else if ("stalker".equals(acc.type)) {
                        phase("Loading portal channels…");
                        live = StalkerClient.channels(acc.url, acc.mac);
                    } else {
                        phase("Loading live channels…");
                        live = XtreamClient.live(acc.server, acc.user, acc.pass);
                        phase("Loading movies…");
                        vod = XtreamClient.vod(acc.server, acc.user, acc.pass);
                        phase("Loading series…");
                        series = XtreamClient.series(acc.server, acc.user, acc.pass);
                        // Refresh the subscription expiry on every background
                        // load so it stays current (not just at login time).
                        // user_info is one cheap call; login() returns null on
                        // auth failure, in which case we keep the old value.
                        try {
                            JSONObject ui = XtreamClient.login(
                                    acc.server, acc.user, acc.pass);
                            long exp = XtreamClient.parseExpDate(ui);
                            if (exp > 0) acc.expDate = exp;
                        } catch (Exception ignored) {}
                    }
                } catch (Throwable t) {
                    String m = t.getMessage();
                    err = (m == null || m.isEmpty()) ? String.valueOf(t) : m;
                }
                // Tag smart categories here, off the UI thread: 97k channels of
                // keyword matching on the main thread causes an ANR.
                if (err == null) {
                    phase("Sorting categories…");
                    try {
                        Cats.tagAll(live);
                        Cats.tagAll(vod);
                        Cats.tagAll(series);
                    } catch (Throwable t) {
                        String m = t.getMessage();
                        err = (m == null || m.isEmpty()) ? String.valueOf(t) : m;
                    }
                }
                final List<Channel> fl = live, fv = vod, fs = series;
                final String ferr = err;
                main.post(new Runnable() {
                    @Override public void run() { cb.onResult(fl, fv, fs, ferr); }
                });
            }

            private void phase(final String p) {
                if (prog == null) return;
                main.post(new Runnable() {
                    @Override public void run() { prog.onProgress(p); }
                });
            }

            private M3uParser.CountListener countListener() {
                return new M3uParser.CountListener() {
                    @Override public void onCount(final int n) {
                        phase("Parsing " + n + " channels…");
                    }
                };
            }
        }).start();
    }

    /** M3U playlists mix live channels and movie files: split by detected kind. */
    private static void splitM3u(List<Channel> all, List<Channel> live, List<Channel> vod) {
        for (Channel c : all) {
            if (c.kind == Channel.VOD) vod.add(c);
            else live.add(c);
        }
    }
}
