package com.zynelabs.dzplay.data;

import java.io.BufferedReader;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;

/** Minimal M3U / M3U8 playlist parser (EXTINF). */
public class M3uParser {

    private static String attr(String line, String name) {
        // indexOf-based: Pattern.compile per line was a major slowdown
        // (100k+ compiles on giant playlists) and churned memory.
        String key = name + "=\"";
        int i = line.indexOf(key);
        if (i < 0) return "";
        int s = i + key.length();
        int e = line.indexOf('"', s);
        return e < 0 ? "" : line.substring(s, e);
    }

    private static final String[] VOD_EXTS = {
            ".mp4", ".mkv", ".avi", ".mov", ".wmv", ".flv", ".webm",
            ".m4v", ".mpg", ".mpeg", ".3gp", ".tsv"
    };

    /** True when the URL points at a movie file (not a live stream). */
    public static boolean isVodUrl(String url) {
        if (url == null) return false;
        String u = url.toLowerCase();
        int q = u.indexOf('?');
        if (q >= 0) u = u.substring(0, q);
        int h = u.indexOf('#');
        if (h >= 0) u = u.substring(0, h);
        for (String e : VOD_EXTS) {
            if (u.endsWith(e)) return true;
        }
        return false;
    }

    public static List<Channel> parse(String text) {
        return parse(text, null);
    }

    /** Parses from a String (kept for small inputs); giant playlists should
     * use parse(BufferedReader, …) so the whole file is never one String. */
    public static List<Channel> parse(String text, CountListener cl) {
        BufferedReader br = new BufferedReader(new StringReader(text));
        try {
            List<Channel> out = parse(br, cl);
            br.close();
            return out;
        } catch (Exception e) {
            return new ArrayList<Channel>();
        }
    }

    /**
     * Streaming parse: reads line-by-line from the reader, so a 100k-entry
     * playlist never sits in memory as one giant String. Callers must close
     * the reader.
     */
    /** Progress callback for huge playlists (called every 2000 channels). */
    public interface CountListener {
        void onCount(int n);
    }

    public static List<Channel> parse(BufferedReader br, CountListener cl) {
        List<Channel> out = new ArrayList<>();
        try {
            String line;
            String pendingName = null, pendingLogo = "", pendingGroup = "";
            int n = 0;
            while ((line = br.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) continue;
                if (line.startsWith("#EXTINF")) {
                    pendingLogo = attr(line, "tvg-logo");
                    pendingGroup = attr(line, "group-title");
                    int comma = line.lastIndexOf(',');
                    pendingName = comma >= 0 && comma + 1 < line.length()
                            ? line.substring(comma + 1).trim() : ("Channel " + (++n));
                } else if (line.startsWith("#")) {
                    // skip other tags (EXTVLCOPT, EXTGRP, ...)
                } else if (pendingName != null && (line.startsWith("http://") || line.startsWith("https://") || line.startsWith("rtsp://") || line.startsWith("rtmp://"))) {
                    Channel c = new Channel();
                    // VOD detection: movie files have video extensions.
                    // (HLS .m3u8 stays LIVE — it can be either, and the live
                    // player handles it; live TV groups are never affected.)
                    c.kind = isVodUrl(line) ? Channel.VOD : Channel.LIVE;
                    c.key = "m3u_" + out.size();
                    c.name = pendingName;
                    c.logo = pendingLogo;
                    c.group = pendingGroup;
                    c.url = line;
                    out.add(c);
                    pendingName = null;
                    if (cl != null && out.size() % 2000 == 0) cl.onCount(out.size());
                }
            }
            br.close();
        } catch (Exception ignored) {}
        return out;
    }
}
