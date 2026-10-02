package com.zynelabs.dzplay.data;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** App-local storage: playlist accounts + favorites. Lives on the device only. */
public class Store {
    private static final String PREF = "dzplay_prefs";
    private final SharedPreferences sp;

    public Store(Context c) {
        sp = c.getSharedPreferences(PREF, Context.MODE_PRIVATE);
    }

    // ---- accounts ----
    public List<PlAccount> accounts() {
        List<PlAccount> out = new ArrayList<>();
        try {
            JSONArray arr = new JSONArray(sp.getString("accounts", "[]"));
            for (int i = 0; i < arr.length(); i++) out.add(PlAccount.fromJson(arr.getJSONObject(i)));
        } catch (Exception ignored) {}
        return out;
    }

    public void saveAccounts(List<PlAccount> list) {
        JSONArray arr = new JSONArray();
        for (PlAccount a : list) arr.put(a.toJson());
        sp.edit().putString("accounts", arr.toString()).apply();
    }

    public void addAccount(PlAccount a) {
        if (a.id == null || a.id.isEmpty()) a.id = UUID.randomUUID().toString();
        List<PlAccount> l = accounts();
        l.add(a);
        saveAccounts(l);
    }

    public void updateAccount(PlAccount a) {
        List<PlAccount> l = accounts();
        for (int i = 0; i < l.size(); i++) if (l.get(i).id.equals(a.id)) l.set(i, a);
        saveAccounts(l);
    }

    public void removeAccount(String id) {
        List<PlAccount> l = accounts();
        for (int i = l.size() - 1; i >= 0; i--) if (l.get(i).id.equals(id)) l.remove(i);
        saveAccounts(l);
        sp.edit().remove("fav_" + id).apply();
    }

    public void setAccountEnabled(String id, boolean enabled) {
        List<PlAccount> l = accounts();
        for (PlAccount a : l) if (a.id.equals(id)) a.enabled = enabled;
        saveAccounts(l);
    }

    /** Only providers that are not disabled (OTT-style). */
    public List<PlAccount> enabledAccounts() {
        List<PlAccount> out = new ArrayList<>();
        for (PlAccount a : accounts()) if (a.enabled) out.add(a);
        return out;
    }

    public PlAccount account(String id) {
        for (PlAccount a : accounts()) if (a.id.equals(id)) return a;
        return null;
    }

    /** The account the home screen loads. Empty = first account with cache. */
    public String activeAccountId() { return sp.getString("active_account_id", ""); }
    public void setActiveAccountId(String id) {
        sp.edit().putString("active_account_id", id == null ? "" : id).apply();
    }

    /**
     * Provider switcher selection: "" = active account, "all" = all providers
     * combined, otherwise an account id.
     */
    public String switchSel() { return sp.getString("switch_sel", ""); }
    public void setSwitchSel(String s) {
        sp.edit().putString("switch_sel", s == null ? "" : s).apply();
    }

    /** Accent color key: "purple" | "cyan" | "blend". */
    public String accent() { return sp.getString("accent", "blend"); }
    public void setAccent(String a) {
        sp.edit().putString("accent", a == null ? "blend" : a).apply();
    }

    /** Custom background image filename (internal storage). Empty = default. */
    public String bgImagePath() { return sp.getString("bg_image", ""); }
    public void setBgImagePath(String p) {
        sp.edit().putString("bg_image", p == null ? "" : p).apply();
    }

    // ---- favorites (per account) ----
    public Set<String> favorites(String accountId) {
        return new HashSet<>(sp.getStringSet("fav_" + accountId, new HashSet<String>()));
    }

    public boolean isFav(String accountId, String key) {
        return favorites(accountId).contains(key);
    }

    public void toggleFav(String accountId, String key) {
        Set<String> s = favorites(accountId);
        if (s.contains(key)) s.remove(key); else s.add(key);
        sp.edit().putStringSet("fav_" + accountId, s).apply();
    }

    public void clearFavorites(String accountId) {
        sp.edit().remove("fav_" + accountId).apply();
    }

    /** Bulk-restore favorites (used by backup import). */
    public void setFavorites(String accountId, java.util.Set<String> favs) {
        sp.edit().putStringSet("fav_" + accountId, favs).apply();
    }

    /** Bulk-restore hidden channels (used by backup import). */
    public void setHiddenChannels(String accId, java.util.Set<String> hidden) {
        sp.edit().putStringSet("hidden_" + accId, hidden).apply();
    }

    // ---- recent watched (per account, newest first, max 20) ----
    public List<String> recent(String accountId) {
        List<String> out = new ArrayList<>();
        try {
            JSONArray arr = new JSONArray(sp.getString("recent_" + accountId, "[]"));
            for (int i = 0; i < arr.length(); i++) out.add(arr.getString(i));
        } catch (Exception ignored) {}
        return out;
    }

    public void addRecent(String accountId, String key) {
        List<String> r = recent(accountId);
        r.remove(key);
        r.add(0, key);
        while (r.size() > 20) r.remove(r.size() - 1);
        JSONArray arr = new JSONArray();
        for (String k : r) arr.put(k);
        sp.edit().putString("recent_" + accountId, arr.toString()).apply();
    }

    // ---- prefs ----
    public String viewMode() { return sp.getString("view_mode", "list"); }
    public void setViewMode(String m) { sp.edit().putString("view_mode", m).apply(); }

    public String sortMode() { return sp.getString("sort_mode", "default"); }
    public void setSortMode(String m) { sp.edit().putString("sort_mode", m).apply(); }

    // app theme (unified palette shared across Dominic's apps)
    public String theme() {
        String t = sp.getString("theme", "vault");
        // One-time v4 migration: the old default was "calm"; users who never
        // picked a theme get the new Neon Vault look, deliberate choices stay.
        if (!sp.getBoolean("theme_v4_seen", false)) {
            sp.edit().putBoolean("theme_v4_seen", true).apply();
            if ("calm".equals(t)) {
                t = "vault";
                sp.edit().putString("theme", t).apply();
            }
        }
        return t;
    }
    public void setTheme(String t) { sp.edit().putString("theme", t).apply(); }

    public static final String DEFAULT_GROUP_LINK = "https://t.me/+7I8oPYJp-fE3ZTY1";

    public String groupLink() { return sp.getString("group_link", DEFAULT_GROUP_LINK); }
    public void setGroupLink(String u) { sp.edit().putString("group_link", u).apply(); }

    // player buffer size in seconds (user-adjustable, like OTT Navigator)
    public int bufferSecs() { return sp.getInt("buffer_secs", 90); }
    public void setBufferSecs(int s) { sp.edit().putInt("buffer_secs", s).apply(); }

    // video scale mode (OTT-style zoom 70%–140%, user-adjustable)
    public float videoScale() { return sp.getFloat("video_scale", 1.0f); }
    public void setVideoScale(float s) { sp.edit().putFloat("video_scale", s).apply(); }

    // VOD resume position ("continue watching", like OTT Navigator)
    private String resumeKey(String accId, String chanKey) {
        return "rz_" + (accId + "|" + chanKey).hashCode();
    }
    public long resumePos(String accId, String chanKey) {
        return sp.getLong(resumeKey(accId, chanKey), 0);
    }
    public void setResumePos(String accId, String chanKey, long ms) {
        setResumePos(accId, chanKey, ms, 0);
    }
    public void setResumePos(String accId, String chanKey, long ms, long durMs) {
        sp.edit().putLong(resumeKey(accId, chanKey), ms)
                .putLong(resumeKey(accId, chanKey) + "_d", durMs).apply();
    }
    public long resumeDur(String accId, String chanKey) {
        return sp.getLong(resumeKey(accId, chanKey) + "_d", 0);
    }
    public void clearResumePos(String accId, String chanKey) {
        sp.edit().remove(resumeKey(accId, chanKey)).apply();
    }

    // ---- home dashboard shelves (StreamVault-style) ----
    public static final String[] SHELF_IDS =
            {"continue", "fav", "fresh_movies", "fresh_series"};
    public static final String[] SHELF_TITLES =
            {"Continue Watching", "Favorite Channels", "Fresh Movies", "Fresh Series"};
    /** Ordered visible shelf ids; empty = default (all four, in order). */
    public List<String> shelfOrder() {
        List<String> out = new ArrayList<>();
        String s = sp.getString("home_shelves", "");
        if (s == null || s.isEmpty()) {
            for (String id : SHELF_IDS) out.add(id);
            return out;
        }
        for (String p : s.split(",")) {
            p = p.trim();
            boolean known = false;
            for (String id : SHELF_IDS) if (id.equals(p)) { known = true; break; }
            if (known && !out.contains(p)) out.add(p);
        }
        return out;
    }
    public void setShelfOrder(List<String> ids) {
        StringBuilder sb = new StringBuilder();
        for (String id : ids) {
            if (sb.length() > 0) sb.append(',');
            sb.append(id);
        }
        sp.edit().putString("home_shelves", sb.toString()).apply();
    }

    // ---- EPG guide prefs (StreamVault-style) ----
    /** 0=compact, 1=comfortable, 2=cinematic */
    public int epgDensity() { return sp.getInt("epg_density", 1); }
    public void setEpgDensity(int d) { sp.edit().putInt("epg_density", d).apply(); }
    public boolean epgFavOnly() { return sp.getBoolean("epg_favonly", false); }
    public void setEpgFavOnly(boolean b) { sp.edit().putBoolean("epg_favonly", b).apply(); }

    // ---- auto-play + updater ----
    /** Auto-play next item when VOD ends (StreamVault-style). */
    public boolean autoplayNext() { return sp.getBoolean("autoplay_next", true); }
    public void setAutoplayNext(boolean b) { sp.edit().putBoolean("autoplay_next", b).apply(); }
    public long lastUpdateCheck() { return sp.getLong("last_update_check", 0); }
    public void setLastUpdateCheck(long t) { sp.edit().putLong("last_update_check", t).apply(); }

    // ---- programme reminders ----
    public static class Reminder {
        public String id, chKey, chName, title, accId;
        public long start;
    }
    public List<Reminder> reminders() {
        List<Reminder> out = new ArrayList<>();
        try {
            JSONArray arr = new JSONArray(sp.getString("reminders", "[]"));
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                Reminder r = new Reminder();
                r.id = o.optString("id"); r.chKey = o.optString("ch");
                r.chName = o.optString("chName"); r.title = o.optString("title");
                r.accId = o.optString("acc"); r.start = o.optLong("start");
                out.add(r);
            }
        } catch (Exception ignored) {}
        return out;
    }
    public String addReminder(String accId, String chKey, String chName,
                              String title, long start) {
        List<Reminder> rs = reminders();
        String id = UUID.randomUUID().toString();
        try {
            JSONArray arr = new JSONArray(sp.getString("reminders", "[]"));
            JSONObject o = new JSONObject();
            o.put("id", id); o.put("acc", accId); o.put("ch", chKey);
            o.put("chName", chName); o.put("title", title); o.put("start", start);
            arr.put(o);
            sp.edit().putString("reminders", arr.toString()).apply();
        } catch (Exception ignored) {}
        return id;
    }
    public void removeReminder(String id) {
        try {
            JSONArray arr = new JSONArray(sp.getString("reminders", "[]"));
            JSONArray n = new JSONArray();
            for (int i = 0; i < arr.length(); i++)
                if (!id.equals(arr.getJSONObject(i).optString("id"))) n.put(arr.get(i));
            sp.edit().putString("reminders", n.toString()).apply();
        } catch (Exception ignored) {}
    }
    /** Drop reminders whose programme already ended (called on guide open). */
    public void pruneReminders() {
        long now = System.currentTimeMillis();
        try {
            JSONArray arr = new JSONArray(sp.getString("reminders", "[]"));
            JSONArray n = new JSONArray();
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                if (o.optLong("start") + 3600_000L > now) n.put(arr.get(i));
            }
            if (n.length() != arr.length())
                sp.edit().putString("reminders", n.toString()).apply();
        } catch (Exception ignored) {}
    }

    // ---- scheduled recordings (StreamVault-style) ----
    public static class SchedRec {
        public String id = "", accId = "", chKey = "", chName = "", url = "", title = "";
        public String stalkerPortal = "", stalkerMac = "", stalkerCmd = "";
        public int streamId = 0;
        public long start = 0, end = 0;
        public int repeat = 0; // 0=once, 1=daily, 2=weekly
    }
    private static SchedRec schedFromJson(JSONObject o) {
        SchedRec r = new SchedRec();
        r.id = o.optString("id"); r.accId = o.optString("acc");
        r.chKey = o.optString("chKey"); r.chName = o.optString("chName");
        r.url = o.optString("url"); r.title = o.optString("title");
        r.stalkerPortal = o.optString("portal"); r.stalkerMac = o.optString("mac");
        r.stalkerCmd = o.optString("cmd"); r.streamId = o.optInt("sid");
        r.start = o.optLong("start"); r.end = o.optLong("end");
        r.repeat = o.optInt("repeat");
        return r;
    }
    public List<SchedRec> schedRecs() {
        List<SchedRec> out = new ArrayList<>();
        try {
            JSONArray arr = new JSONArray(sp.getString("sched_recs", "[]"));
            for (int i = 0; i < arr.length(); i++) out.add(schedFromJson(arr.getJSONObject(i)));
        } catch (Exception ignored) {}
        return out;
    }
    public SchedRec schedRec(String id) {
        for (SchedRec r : schedRecs()) if (r.id.equals(id)) return r;
        return null;
    }
    /** Adds and returns the assigned id. */
    public String addSchedRec(SchedRec r) {
        r.id = UUID.randomUUID().toString();
        try {
            JSONArray arr = new JSONArray(sp.getString("sched_recs", "[]"));
            JSONObject o = new JSONObject();
            o.put("id", r.id); o.put("acc", r.accId); o.put("chKey", r.chKey);
            o.put("chName", r.chName); o.put("url", r.url); o.put("title", r.title);
            o.put("portal", r.stalkerPortal); o.put("mac", r.stalkerMac);
            o.put("cmd", r.stalkerCmd); o.put("sid", r.streamId);
            o.put("start", r.start); o.put("end", r.end); o.put("repeat", r.repeat);
            arr.put(o);
            sp.edit().putString("sched_recs", arr.toString()).apply();
        } catch (Exception ignored) {}
        return r.id;
    }
    public void removeSchedRec(String id) {
        try {
            JSONArray arr = new JSONArray(sp.getString("sched_recs", "[]"));
            JSONArray n = new JSONArray();
            for (int i = 0; i < arr.length(); i++)
                if (!id.equals(arr.getJSONObject(i).optString("id"))) n.put(arr.get(i));
            sp.edit().putString("sched_recs", n.toString()).apply();
        } catch (Exception ignored) {}
    }
    /** Drop one-shot recordings that already ended. */
    public void pruneSchedRecs() {
        long now = System.currentTimeMillis();
        try {
            JSONArray arr = new JSONArray(sp.getString("sched_recs", "[]"));
            JSONArray n = new JSONArray();
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                if (o.optInt("repeat") != 0 || o.optLong("end") > now) n.put(arr.get(i));
            }
            if (n.length() != arr.length())
                sp.edit().putString("sched_recs", n.toString()).apply();
        } catch (Exception ignored) {}
    }
    /** Overlapping scheduled recordings with [start, end). */
    public List<SchedRec> recConflicts(long start, long end) {
        List<SchedRec> out = new ArrayList<>();
        for (SchedRec r : schedRecs())
            if (start < r.end && end > r.start) out.add(r);
        return out;
    }
    // recording defaults: padding + retention
    public int recPadStart() { return sp.getInt("rec_pad_start", 2); }
    public void setRecPadStart(int m) { sp.edit().putInt("rec_pad_start", m).apply(); }
    public int recPadEnd() { return sp.getInt("rec_pad_end", 5); }
    public void setRecPadEnd(int m) { sp.edit().putInt("rec_pad_end", m).apply(); }
    /** Retention in days; 0 = keep everything. */
    public int recRetention() { return sp.getInt("rec_retention", 0); }
    public void setRecRetention(int d) { sp.edit().putInt("rec_retention", d).apply(); }

    // ---- parental control (OTT-style) ----
    // The PIN is stored as SHA-256(salt + pin) — never plaintext.
    private String pinSalt() {
        String s = sp.getString("pin_salt", "");
        if (s.isEmpty()) {
            s = UUID.randomUUID().toString();
            sp.edit().putString("pin_salt", s).apply();
        }
        return s;
    }
    private static String sha256(String s) {
        try {
            java.security.MessageDigest md =
                    java.security.MessageDigest.getInstance("SHA-256");
            byte[] h = md.digest(s.getBytes("UTF-8"));
            StringBuilder sb = new StringBuilder();
            for (byte b : h) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            return "!" + s;
        }
    }
    /** Stored PIN hash (empty = no PIN). Never the plaintext PIN. */
    public String parentalPin() { return sp.getString("parental_pin_hash", ""); }
    public boolean hasParentalPin() { return !parentalPin().isEmpty(); }
    public void setParentalPin(String pin) {
        if (pin == null || pin.isEmpty()) {
            sp.edit().remove("parental_pin_hash").apply();
            return;
        }
        sp.edit().putString("parental_pin_hash",
                sha256(pinSalt() + "|" + pin)).apply();
    }
    public boolean checkParentalPin(String pin) {
        if (pin == null) return false;
        String stored = parentalPin();
        if (stored.isEmpty()) return false;
        if (stored.equals(sha256(pinSalt() + "|" + pin))) return true;
        // one-time migration: v3.15 stored the PIN as plaintext
        String legacy = sp.getString("parental_pin", "");
        if (!legacy.isEmpty() && legacy.equals(pin)) {
            setParentalPin(pin);
            sp.edit().remove("parental_pin").apply();
            return true;
        }
        return false;
    }
    public boolean hideAdult() { return sp.getBoolean("hide_adult", false); }
    public void setHideAdult(boolean h) { sp.edit().putBoolean("hide_adult", h).apply(); }
    public boolean adultLocked() { return hasParentalPin() && hideAdult(); }

    // ---- radio handling ----
    /** Show radio stations inside the Live TV grid (default: separate section). */
    public boolean showRadioInLive() { return sp.getBoolean("show_radio_live", false); }
    public void setShowRadioInLive(boolean b) {
        sp.edit().putBoolean("show_radio_live", b).apply();
    }

    // ---- tap-to-preview (StreamVault-style: first tap previews, second plays) ----
    public boolean previewOnTap() { return sp.getBoolean("preview_on_tap", true); }
    public void setPreviewOnTap(boolean b) {
        sp.edit().putBoolean("preview_on_tap", b).apply();
    }

    // ---- hidden / renamed channels (OTT-style channel manager) ----
    public java.util.Set<String> hiddenChannels(String accId) {
        return new java.util.HashSet<>(
                sp.getStringSet("hidden_" + accId, new java.util.HashSet<String>()));
    }
    public void setChannelHidden(String accId, String key, boolean hide) {
        setChannelHidden(accId, key, hide, null);
    }
    /** Hide/unhide; when hiding, remember the display name for the manager UI. */
    public void setChannelHidden(String accId, String key, boolean hide, String dispName) {
        java.util.Set<String> s = hiddenChannels(accId);
        if (hide) s.add(key); else s.remove(key);
        sp.edit().putStringSet("hidden_" + accId, s).apply();
        setHiddenName(accId, key, hide ? dispName : null);
    }
    // per-account JSON maps (enumerable, so backup/restore can include them)
    private java.util.Map<String, String> strMap(String prefKey) {
        java.util.Map<String, String> m = new java.util.LinkedHashMap<>();
        try {
            org.json.JSONObject o = new org.json.JSONObject(
                    sp.getString(prefKey, "{}"));
            java.util.Iterator<String> it = o.keys();
            while (it.hasNext()) {
                String k = it.next();
                m.put(k, o.optString(k, ""));
            }
        } catch (Exception ignored) {}
        return m;
    }
    private void saveStrMap(String prefKey, java.util.Map<String, String> m) {
        try {
            org.json.JSONObject o = new org.json.JSONObject();
            for (java.util.Map.Entry<String, String> e : m.entrySet())
                o.put(e.getKey(), e.getValue());
            sp.edit().putString(prefKey, o.toString()).apply();
        } catch (Exception ignored) {}
    }
    public java.util.Map<String, String> hiddenNames(String accId) {
        return strMap("hnms_" + accId);
    }
    public String hiddenName(String accId, String key) {
        String n = hiddenNames(accId).get(key);
        return n == null ? "" : n;
    }
    public java.util.Map<String, String> renames(String accId) {
        return strMap("rnms_" + accId);
    }
    public String customName(String accId, String key) {
        String n = renames(accId).get(key);
        return n == null ? "" : n;
    }
    public void setCustomName(String accId, String key, String name) {
        java.util.Map<String, String> m = renames(accId);
        if (name == null || name.trim().isEmpty()) m.remove(key);
        else m.put(key, name.trim());
        saveStrMap("rnms_" + accId, m);
    }
    private void setHiddenName(String accId, String key, String name) {
        java.util.Map<String, String> m = hiddenNames(accId);
        if (name == null || name.isEmpty()) m.remove(key);
        else m.put(key, name);
        saveStrMap("hnms_" + accId, m);
    }
}
