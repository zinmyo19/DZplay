package com.zynelabs.dzplay.data;

import org.json.JSONObject;

/** A playlist account: M3U link, M3U file, Xtream Codes login, or Stalker portal. */
public class PlAccount {
    public String id;
    public String name;
    public String type;   // "m3u_url" | "m3u_file" | "xtream" | "stalker"
    public String url;    // m3u link or stalker portal url
    public String file;   // internal filename for m3u file
    public String server, user, pass; // xtream
    public String mac;    // stalker portal MAC
    public long expDate;  // subscription expiry, epoch seconds; 0 = unknown/unlimited
    public boolean enabled = true; // OTT-style disable toggle

    public PlAccount() {}

    public static PlAccount fromJson(JSONObject o) {
        PlAccount a = new PlAccount();
        a.id = o.optString("id");
        a.name = o.optString("name");
        a.type = o.optString("type");
        a.url = o.optString("url", "");
        a.file = o.optString("file", "");
        a.server = o.optString("server", "");
        a.user = o.optString("user", "");
        a.pass = o.optString("pass", "");
        a.mac = o.optString("mac", "");
        a.expDate = o.optLong("expDate", 0);
        a.enabled = o.optBoolean("enabled", true);
        return a;
    }

    public JSONObject toJson() {
        JSONObject o = new JSONObject();
        try {
            o.put("id", id); o.put("name", name); o.put("type", type);
            o.put("url", url); o.put("file", file);
            o.put("server", server); o.put("user", user); o.put("pass", pass);
            o.put("mac", mac); o.put("expDate", expDate); o.put("enabled", enabled);
        } catch (Exception ignored) {}
        return o;
    }

    public boolean isXtream() { return "xtream".equals(type); }

    public boolean isStalker() { return "stalker".equals(type); }
}
