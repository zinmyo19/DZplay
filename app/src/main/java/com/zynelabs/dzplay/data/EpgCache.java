package com.zynelabs.dzplay.data;

import android.app.Activity;
import android.content.Context;
import android.util.Xml;

import org.xmlpull.v1.XmlPullParser;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Shared EPG programme cache (StreamVault-style "now/next" data everywhere).
 * Fetches Xtream xmltv.php once per account per process and keeps programmes
 * for the account's live channels; the guide, channel list and player all
 * read from here so the data is consistent and fetched only once.
 */
public class EpgCache {

    public static class Prog {
        public long start, stop;
        public String title = "";
        public String desc = "";
    }

    public interface Done { void run(); }

    private static final Map<String, Map<String, List<Prog>>> cache = new HashMap<>();
    private static final Set<String> loading = new HashSet<>();

    /** streamId key used everywhere (matches Channel.streamId). */
    public static String key(Channel c) {
        return String.valueOf(c.streamId);
    }

    /**
     * Ensure programmes are loaded for this account. Fetches in the
     * background once; done() runs on the UI thread (immediately if cached
     * or if this provider has no programme data).
     */
    public static void ensure(final Context ctx, final PlAccount acc,
                              final List<Channel> live, final Done done) {
        if (acc == null || !acc.isXtream()) {
            if (done != null) done.run();
            return;
        }
        synchronized (cache) {
            if (cache.containsKey(acc.id)) {
                if (done != null) done.run();
                return;
            }
            if (!loading.add(acc.id)) return; // fetch already in flight
        }
        new Thread(new Runnable() {
            @Override public void run() {
                final Map<String, List<Prog>> g = fetch(acc, live);
                synchronized (cache) {
                    cache.put(acc.id, g);
                    loading.remove(acc.id);
                }
                if (done != null) {
                    if (ctx instanceof Activity) {
                        ((Activity) ctx).runOnUiThread(new Runnable() {
                            @Override public void run() { done.run(); }
                        });
                    } else {
                        done.run();
                    }
                }
            }
        }).start();
    }

    /** Put an externally-fetched guide (used by EpgActivity's own loader). */
    public static void put(String accId, Map<String, List<Prog>> g) {
        synchronized (cache) {
            cache.put(accId, g);
        }
    }

    public static Map<String, List<Prog>> get(String accId) {
        synchronized (cache) {
            Map<String, List<Prog>> g = cache.get(accId);
            return g != null ? g : new HashMap<String, List<Prog>>();
        }
    }

    /** Programme airing right now on this channel, or null. */
    public static Prog now(String accId, String streamKey) {
        long now = System.currentTimeMillis();
        List<Prog> list = get(accId).get(streamKey);
        if (list == null) return null;
        for (Prog p : list)
            if (p.start <= now && now < p.stop) return p;
        return null;
    }

    /** Next upcoming programme on this channel, or null. */
    public static Prog next(String accId, String streamKey) {
        long now = System.currentTimeMillis();
        List<Prog> list = get(accId).get(streamKey);
        if (list == null) return null;
        Prog best = null;
        for (Prog p : list)
            if (p.start > now && (best == null || p.start < best.start)) best = p;
        return best;
    }

    // ---------------- fetch (moved from EpgActivity) ----------------

    /** Stream-parse xmltv.php, keep programmes for our live channels only. */
    private static Map<String, List<Prog>> fetch(PlAccount acc, List<Channel> live) {
        Map<String, List<Prog>> guide = new HashMap<>();
        Map<String, Boolean> want = new HashMap<>();
        for (Channel c : live) want.put(String.valueOf(c.streamId), true);
        HttpURLConnection con = null;
        try {
            String urlStr = XtreamClient.normServer(acc.server) + "/xmltv.php?username="
                    + enc(acc.user) + "&password=" + enc(acc.pass);
            con = (HttpURLConnection) new URL(urlStr).openConnection();
            con.setConnectTimeout(20000);
            con.setReadTimeout(120000);
            con.setRequestProperty("User-Agent", "ZyneLabsIPTV/4.0");
            InputStream in = con.getInputStream();
            XmlPullParser x = Xml.newPullParser();
            x.setInput(in, "UTF-8");
            Prog cur = null;
            String curChan = null;
            String curTag = null;
            int ev = x.getEventType();
            while (ev != XmlPullParser.END_DOCUMENT) {
                if (ev == XmlPullParser.START_TAG) {
                    String name = x.getName();
                    if ("programme".equals(name)) {
                        curChan = x.getAttributeValue(null, "channel");
                        if (curChan != null && want.containsKey(curChan)) {
                            cur = new Prog();
                            cur.start = parseTime(x.getAttributeValue(null, "start"));
                            cur.stop = parseTime(x.getAttributeValue(null, "stop"));
                        } else {
                            cur = null;
                        }
                    } else if (cur != null && ("title".equals(name) || "desc".equals(name))) {
                        curTag = name;
                    }
                } else if (ev == XmlPullParser.TEXT) {
                    if (cur != null && curTag != null) {
                        String t = x.getText();
                        if ("title".equals(curTag)) cur.title += t;
                        else cur.desc += t;
                    }
                } else if (ev == XmlPullParser.END_TAG) {
                    String name = x.getName();
                    if ("programme".equals(name)) {
                        if (cur != null) {
                            // Some providers base64-encode xmltv titles/descs
                            // just like the Xtream JSON API does.
                            cur.title = XtreamClient.decodeMaybe(cur.title);
                            cur.desc = XtreamClient.decodeMaybe(cur.desc);
                        }
                        if (cur != null && cur.start > 0 && !cur.title.isEmpty()) {
                            List<Prog> list = guide.get(curChan);
                            if (list == null) {
                                list = new ArrayList<>();
                                guide.put(curChan, list);
                            }
                            list.add(cur);
                        }
                        cur = null;
                        curChan = null;
                    }
                    curTag = null;
                }
                ev = x.next();
            }
            in.close();
        } catch (Exception ignored) {
        } finally {
            if (con != null) con.disconnect();
        }
        return guide;
    }

    private static String enc(String s) {
        try { return URLEncoder.encode(s, "UTF-8"); } catch (Exception e) { return s; }
    }

    private static long parseTime(String s) {
        if (s == null || s.length() < 14) return 0;
        try {
            String core = s.substring(0, 14);
            String zone = s.length() > 15 ? s.substring(15).trim() : "+0000";
            SimpleDateFormat f = new SimpleDateFormat("yyyyMMddHHmmss Z", Locale.US);
            return f.parse(core + " " + zone).getTime();
        } catch (Exception e) {
            return 0;
        }
    }
}
