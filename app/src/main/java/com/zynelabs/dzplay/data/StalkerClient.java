package com.zynelabs.dzplay.data;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Stalker Middleware (MAG portal) client: portal URL + MAC address,
 * the same "MAC Portal" style OTT Navigator offers.
 *
 * Flow: handshake (mac cookie) -> token -> get_genres / get_all_channels ->
 * create_link per channel at play time to get a fresh stream URL.
 */
public class StalkerClient {

    private static final String UA =
            "Mozilla/5.0 (QtEmbedded; U; Linux; C) AppleWebKit/533.3 (KHTML, like Gecko) "
                    + "MAG200 stbapp ver: 2 rev: 250 Safari/533.3";

    /** API entry points tried in order; portals expose different ones. */
    private static final String[] API_PATHS = {
            "/portal.php",
            "/stalker_portal/server/load.php",
            "/server/load.php",
            "/c/server/load.php"
    };

    public static String normPortal(String s) {
        s = s.trim();
        if (!s.startsWith("http://") && !s.startsWith("https://")) s = "http://" + s;
        while (s.endsWith("/")) s = s.substring(0, s.length() - 1);
        return s;
    }

    public static boolean validMac(String mac) {
        return mac != null && mac.trim().toUpperCase().matches("^([0-9A-F]{2}:){5}[0-9A-F]{2}$");
    }

    public static class Session {
        public String apiBase; // portal + api path, e.g. http://host/c/portal.php
        public String mac;     // upper-case MAC
        public String token;
    }

    private static final Map<String, Session> cache = new HashMap<>();

    /** Handshake (cached per portal+mac). Throws on failure. */
    public static synchronized Session session(String portal, String mac) throws Exception {
        portal = normPortal(portal);
        mac = mac.trim().toUpperCase();
        if (!validMac(mac)) throw new Exception("Bad MAC format (00:1A:79:…)");
        String key = portal + "|" + mac;
        Session s = cache.get(key);
        if (s != null) return s;
        s = connect(portal, mac);
        cache.put(key, s);
        return s;
    }

    private static Session connect(String portal, String mac) throws Exception {
        for (String path : API_PATHS) {
            String base = portal + path;
            try {
                String body = get(base + "?type=stb&action=handshake", mac, null);
                JSONObject js = new JSONObject(body).optJSONObject("js");
                if (js != null && js.has("token")) {
                    Session s = new Session();
                    s.apiBase = base;
                    s.mac = mac;
                    s.token = js.optString("token");
                    if (!s.token.isEmpty()) return s;
                }
            } catch (Exception ignored) {}
        }
        throw new Exception("Portal handshake failed — check URL & MAC");
    }

    private static String get(String urlStr, String mac, String token) throws Exception {
        HttpURLConnection con = null;
        try {
            URL url = new URL(urlStr);
            con = (HttpURLConnection) url.openConnection();
            con.setConnectTimeout(15000);
            con.setReadTimeout(30000);
            con.setRequestProperty("User-Agent", UA);
            con.setRequestProperty("X-User-Agent", "Model: MAG200; Link: WiFi");
            con.setRequestProperty("Cookie",
                    "mac=" + mac + "; stb_lang=en; timezone=Asia/Kuala_Lumpur");
            if (token != null) con.setRequestProperty("Authorization", "Bearer " + token);
            InputStream in = con.getInputStream();
            BufferedReader br = new BufferedReader(new InputStreamReader(in, "UTF-8"));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) sb.append(line).append('\n');
            br.close();
            return sb.toString();
        } finally {
            if (con != null) con.disconnect();
        }
    }

    private static String enc(String s) {
        try { return URLEncoder.encode(s, "UTF-8"); } catch (Exception e) { return s; }
    }

    /** All live channels of the portal (genres -> group names). */
    public static List<Channel> channels(String portal, String mac) throws Exception {
        Session s = session(portal, mac);

        Map<String, String> genres = new HashMap<>();
        try {
            JSONObject o = new JSONObject(
                    get(s.apiBase + "?type=itv&action=get_genres", s.mac, s.token));
            Object js = o.opt("js");
            JSONArray arr = null;
            if (js instanceof JSONArray) arr = (JSONArray) js;
            else if (js instanceof JSONObject) arr = ((JSONObject) js).optJSONArray("data");
            if (arr != null) {
                for (int i = 0; i < arr.length(); i++) {
                    JSONObject g = arr.optJSONObject(i);
                    if (g != null) genres.put(g.optString("id"), g.optString("title", ""));
                }
            }
        } catch (Exception ignored) {}

        List<Channel> out = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (int page = 1; page <= 40; page++) {
            String body = get(s.apiBase + "?type=itv&action=get_all_channels&p=" + page,
                    s.mac, s.token);
            JSONObject js = new JSONObject(body).optJSONObject("js");
            if (js == null) break;
            JSONArray data = js.optJSONArray("data");
            if (data == null || data.length() == 0) break;
            int before = out.size();
            for (int i = 0; i < data.length(); i++) {
                JSONObject ch = data.optJSONObject(i);
                if (ch == null) continue;
                int id = ch.optInt("id");
                String key = "stalker_" + id;
                if (!seen.add(key)) continue;
                Channel c = new Channel();
                c.kind = Channel.LIVE;
                c.key = key;
                c.name = ch.optString("name", "Channel " + id);
                c.logo = ch.optString("logo", "");
                String gid = ch.optString("tv_genre_id", "");
                String g = genres.get(gid);
                c.group = (g == null) ? "" : g;
                c.stalkerPortal = normPortal(portal);
                c.stalkerMac = s.mac;
                c.stalkerCmd = ch.optString("cmd", "");
                c.streamId = id;
                out.add(c);
            }
            if (out.size() == before) break; // page added nothing new -> done
            int total = js.optInt("total_items", -1);
            if (total > 0 && out.size() >= total) break;
            int maxPage = js.optInt("max_page_items", 0);
            if (maxPage > 0 && data.length() < maxPage) break; // last page
        }
        return out;
    }

    /** Resolve a fresh playable stream URL for a channel (create_link). */
    public static String resolve(Channel c) throws Exception {
        if (c.stalkerPortal == null || c.stalkerPortal.isEmpty()
                || c.stalkerCmd == null || c.stalkerCmd.isEmpty())
            throw new Exception("No portal link");
        Session s = session(c.stalkerPortal, c.stalkerMac);
        String url = s.apiBase + "?type=itv&action=create_link"
                + "&cmd=" + enc(c.stalkerCmd) + "&ch_id=" + c.streamId;
        String body = get(url, s.mac, s.token);
        JSONObject js = new JSONObject(body).optJSONObject("js");
        if (js == null) throw new Exception("Portal refused the link");
        String u = urlFromCmd(js.optString("cmd", ""));
        if (u == null) {
            // some portals answer with the URL directly
            u = urlFromCmd(body);
        }
        if (u == null) throw new Exception("Bad stream link");
        return u;
    }

    /** "ffmpeg http://host:port/..." -> "http://host:port/..." */
    public static String urlFromCmd(String cmd) {
        if (cmd == null) return null;
        for (String tok : cmd.split("\\s+")) {
            if (tok.contains("://")) {
                // strip any trailing quote/comma artefacts
                return tok.replaceAll("[\"',]+$", "");
            }
        }
        return null;
    }
}
