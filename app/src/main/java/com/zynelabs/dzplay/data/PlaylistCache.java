package com.zynelabs.dzplay.data;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * On-disk cache of a fully parsed+tagged playlist, per account.
 *
 * <p>The user's #1 complaint: leaving the app re-scans the whole ~97k-entry
 * playlist from scratch every time. After a successful load we persist the
 * channel lists here; the home screen renders from cache instantly and then
 * refreshes quietly in the background.</p>
 *
 * <p>Custom compact binary format (fast to read, no reflection):</p>
 * <pre>
 *   "ZPLC" magic, int schema, long savedAt,
 *   int liveCount, int vodCount, int seriesCount,
 *   then each channel: kind, key, name, logo, group, url,
 *     server, user, pass, streamId, container, archive,
 *     stalkerPortal, stalkerMac, stalkerCmd, smartCat, country
 * </pre>
 * Pure java.io — no Android dependency, so the round-trip is unit-testable
 * on a plain JVM.
 */
public class PlaylistCache {

    /** Bump when Channel's serialized fields change; old caches are ignored. */
    public static final int SCHEMA = 1;

    private static final int MAGIC = 0x5A504C43; // "ZPLC"

    public static class Data {
        public final List<Channel> live;
        public final List<Channel> vod;
        public final List<Channel> series;
        public final long savedAt;
        public Data(List<Channel> l, List<Channel> v, List<Channel> s, long t) {
            live = l; vod = v; series = s; savedAt = t;
        }
    }

    public static class Counts {
        public final int live;
        public final int vod;
        public final int series;
        public final long savedAt;
        public Counts(int l, int v, int s, long t) {
            live = l; vod = v; series = s; savedAt = t;
        }
    }

    private static File fileFor(File dir, String accountId) {
        String safe = accountId == null ? "x"
                : accountId.replaceAll("[^A-Za-z0-9]", "_");
        return new File(dir, "plcache_" + safe + ".bin");
    }

    /** Header-only read: cheap counts for the provider list. Null if none. */
    public static Counts readCounts(File dir, String accountId) {
        File f = fileFor(dir, accountId);
        if (!f.exists()) return null;
        DataInputStream in = null;
        try {
            in = new DataInputStream(new BufferedInputStream(
                    new FileInputStream(f)));
            if (in.readInt() != MAGIC) return null;
            if (in.readInt() != SCHEMA) return null;
            long savedAt = in.readLong();
            int l = in.readInt(), v = in.readInt(), s = in.readInt();
            return new Counts(l, v, s, savedAt);
        } catch (IOException e) {
            return null;
        } finally {
            closeQuiet(in);
        }
    }

    /** Full read. Returns null when missing, corrupt, or wrong schema. */
    public static Data load(File dir, String accountId) {
        File f = fileFor(dir, accountId);
        if (!f.exists()) return null;
        DataInputStream in = null;
        try {
            in = new DataInputStream(new BufferedInputStream(
                    new FileInputStream(f), 65536));
            if (in.readInt() != MAGIC) return null;
            if (in.readInt() != SCHEMA) return null;
            long savedAt = in.readLong();
            int nl = in.readInt(), nv = in.readInt(), ns = in.readInt();
            List<Channel> live = readList(in, nl);
            List<Channel> vod = readList(in, nv);
            List<Channel> series = readList(in, ns);
            return new Data(live, vod, series, savedAt);
        } catch (IOException | RuntimeException e) {
            return null;
        } finally {
            closeQuiet(in);
        }
    }

    private static List<Channel> readList(DataInputStream in, int n)
            throws IOException {
        List<Channel> out = new ArrayList<>(Math.max(n, 16));
        for (int i = 0; i < n; i++) out.add(readChannel(in));
        return out;
    }

    private static Channel readChannel(DataInputStream in) throws IOException {
        Channel c = new Channel();
        c.kind = in.readInt();
        c.key = in.readUTF();
        c.name = in.readUTF();
        c.logo = in.readUTF();
        c.group = in.readUTF();
        c.url = in.readUTF();
        c.server = in.readUTF();
        c.user = in.readUTF();
        c.pass = in.readUTF();
        c.streamId = in.readInt();
        c.container = in.readUTF();
        c.archive = in.readBoolean();
        c.stalkerPortal = in.readUTF();
        c.stalkerMac = in.readUTF();
        c.stalkerCmd = in.readUTF();
        String sc = in.readUTF();
        c.smartCat = sc.isEmpty() ? null : sc; // lazy recompute on demand
        String co = in.readUTF();
        c.country = co.isEmpty() ? null : co;
        return c;
    }

    /** Write (atomically via temp file + rename). Best-effort: never throws. */
    public static void save(File dir, String accountId,
                            List<Channel> live, List<Channel> vod,
                            List<Channel> series) {
        File tmp = new File(dir, "plcache_tmp.bin");
        File dst = fileFor(dir, accountId);
        DataOutputStream out = null;
        try {
            out = new DataOutputStream(new BufferedOutputStream(
                    new FileOutputStream(tmp), 65536));
            out.writeInt(MAGIC);
            out.writeInt(SCHEMA);
            out.writeLong(System.currentTimeMillis());
            out.writeInt(live.size());
            out.writeInt(vod.size());
            out.writeInt(series.size());
            writeList(out, live);
            writeList(out, vod);
            writeList(out, series);
            out.flush();
            out.close();
            out = null;
            if (dst.exists()) dst.delete();
            tmp.renameTo(dst);
        } catch (IOException | RuntimeException ignored) {
            try { tmp.delete(); } catch (Exception e2) { /* ignore */ }
        } finally {
            closeQuiet(out);
        }
    }

    private static void writeList(DataOutputStream out, List<Channel> l)
            throws IOException {
        for (Channel c : l) writeChannel(out, c);
    }

    private static void writeChannel(DataOutputStream out, Channel c)
            throws IOException {
        out.writeInt(c.kind);
        out.writeUTF(nn(c.key));
        out.writeUTF(nn(c.name));
        out.writeUTF(nn(c.logo));
        out.writeUTF(nn(c.group));
        out.writeUTF(nn(c.url));
        out.writeUTF(nn(c.server));
        out.writeUTF(nn(c.user));
        out.writeUTF(nn(c.pass));
        out.writeInt(c.streamId);
        out.writeUTF(nn(c.container));
        out.writeBoolean(c.archive);
        out.writeUTF(nn(c.stalkerPortal));
        out.writeUTF(nn(c.stalkerMac));
        out.writeUTF(nn(c.stalkerCmd));
        out.writeUTF(c.smartCat == null ? "" : c.smartCat);
        out.writeUTF(c.country == null ? "" : c.country);
    }

    private static String nn(String s) { return s == null ? "" : s; }

    public static void clear(File dir, String accountId) {
        try { fileFor(dir, accountId).delete(); } catch (Exception ignored) {}
    }

    private static void closeQuiet(java.io.Closeable c) {
        try { if (c != null) c.close(); } catch (Exception ignored) {}
    }

    /** Human "updated … ago" label for the home screen. */
    public static String ago(long savedAt) {
        long d = System.currentTimeMillis() - savedAt;
        if (d < 60_000) return "just now";
        long m = d / 60_000;
        if (m < 60) return m + "m ago";
        long h = m / 60;
        if (h < 24) return h + "h ago";
        return (h / 24) + "d ago";
    }

    // ---------------- JVM smoke test ----------------
    // javac: Channel.java Cats.java PlaylistCache.java ; java com.zynelabs.dzplay.data.PlaylistCache
    public static void main(String[] args) throws Exception {
        File dir = new File(args.length > 0 ? args[0]
                : System.getProperty("java.io.tmpdir"));
        List<Channel> live = new ArrayList<>();
        for (int i = 0; i < 500; i++) {
            Channel c = new Channel();
            c.kind = Channel.LIVE;
            c.key = "m3u_" + i;
            c.name = "Test Channel " + i + " ⚽ Sports HD";
            c.logo = i % 3 == 0 ? "http://example.com/logo" + i + ".png" : "";
            c.group = i % 2 == 0 ? "UK | SPORTS" : "Premium Movies";
            c.url = "http://example.com/stream" + i;
            c.smartCat = "⚽ Sports";
            live.add(c);
        }
        List<Channel> vod = new ArrayList<>();
        Channel m = new Channel();
        m.kind = Channel.VOD; m.key = "vod_1"; m.name = "Movie (2024)";
        m.url = "http://example.com/movie.mp4"; m.container = "mp4";
        vod.add(m);
        long t0 = System.currentTimeMillis();
        save(dir, "test-acc", live, vod, new ArrayList<Channel>());
        long t1 = System.currentTimeMillis();
        Counts co = readCounts(dir, "test-acc");
        Data d = load(dir, "test-acc");
        long t2 = System.currentTimeMillis();
        if (co == null || d == null) throw new RuntimeException("cache miss");
        if (co.live != 500 || co.vod != 1) throw new RuntimeException("counts wrong");
        if (d.live.size() != 500 || d.vod.size() != 1) throw new RuntimeException("size wrong");
        Channel r = d.live.get(499);
        if (!"Test Channel 499 ⚽ Sports HD".equals(r.name)
                || !"Premium Movies".equals(r.group) // 499 is odd
                || !"⚽ Sports".equals(r.smartCat)
                || r.kind != Channel.LIVE) throw new RuntimeException("field mismatch");
        Channel r2 = d.live.get(498);
        if (!"UK | SPORTS".equals(r2.group)) throw new RuntimeException("group mismatch");
        if (!"mp4".equals(d.vod.get(0).container)) throw new RuntimeException("vod field mismatch");
        System.out.println("PlaylistCache OK: save " + (t1 - t0) + "ms, load "
                + (t2 - t1) + "ms, 501 channels, counts=" + co.live + "/"
                + co.vod + "/" + co.series);
        clear(dir, "test-acc");
    }
}
