package com.zynelabs.dzplay.data;

/** One playable item: live channel, movie, or series. */
public class Channel {
    public static final int LIVE = 0;
    public static final int VOD = 1;
    public static final int SERIES = 2;

    public int kind = LIVE;
    public String key = "";      // unique within account
    public String name = "";
    public String logo = "";
    public String group = "";
    public String url = "";      // direct play URL (live/vod)

    // Xtream extras for series episode resolution
    public String server = "";
    public String user = "";
    public String pass = "";
    public int streamId = 0;
    public String container = "";

    // Xtream catch-up/archive support (from get_live_streams: tv_archive)
    public boolean archive = false;

    // Stalker portal extras (resolved to a fresh URL at play time)
    public String stalkerPortal = "";
    public String stalkerMac = "";
    public String stalkerCmd = "";

    public String displayGroup() {
        return (group == null || group.isEmpty()) ? "Ungrouped" : group;
    }

    /** Cached smart category (Cats.of), computed once off the UI thread. */
    public String smartCat = null;

    public String getSmartCat() {
        if (smartCat == null) smartCat = Cats.of(this);
        return smartCat;
    }

    /** Cached country label (Cats.countryOf), computed once off the UI thread. */
    public String country = null;

    public String getCountry() {
        if (country == null) country = Cats.countryOf(this);
        return country;
    }
}
