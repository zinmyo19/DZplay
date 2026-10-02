package com.zynelabs.dzplay.data;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import java.io.File;
import java.util.List;

/**
 * Cache-first playlist loading shared by the home/category screens.
 *
 * <p>Flow: read the disk cache on a background thread and hand it to the UI
 * immediately (cold start shows channels in milliseconds), then refresh from
 * the source in the background and deliver the fresh lists (the caller swaps
 * them in and we rewrite the cache). The caller's onError decides what to do
 * when there is no cached data vs. a failed background refresh.</p>
 */
public class PlaylistLoader {

    public interface Listener {
        /** Cached data is available — render it now. May not be called. */
        void onCached(List<Channel> live, List<Channel> vod,
                      List<Channel> series, long savedAt);
        /** Fresh-load progress phase (only while no data is shown yet). */
        void onProgress(String phase);
        /** Fresh data arrived — swap it in. Cache was rewritten. */
        void onFresh(List<Channel> live, List<Channel> vod,
                     List<Channel> series);
        /** Fresh load failed. */
        void onError(String err);
    }

    public static void start(final Context ctx, final PlAccount acc,
                             final Listener l) {
        final Handler main = new Handler(Looper.getMainLooper());
        final File dir = ctx.getFilesDir();
        final String accId = acc.id;
        new Thread(new Runnable() {
            @Override public void run() {
                final PlaylistCache.Data cached =
                        PlaylistCache.load(dir, accId);
                if (cached != null) {
                    main.post(new Runnable() {
                        @Override public void run() {
                            l.onCached(cached.live, cached.vod,
                                    cached.series, cached.savedAt);
                        }
                    });
                }
                ChannelRepo.load(ctx, acc, new ChannelRepo.Progress() {
                    @Override public void onProgress(final String phase) {
                        main.post(new Runnable() {
                            @Override public void run() { l.onProgress(phase); }
                        });
                    }
                }, new ChannelRepo.Callback() {
                    @Override public void onResult(final List<Channel> live,
                                                   final List<Channel> vod,
                                                   final List<Channel> series,
                                                   final String err) {
                        if (err != null) {
                            l.onError(err);
                            return;
                        }
                        new Thread(new Runnable() {
                            @Override public void run() {
                                PlaylistCache.save(dir, accId, live, vod, series);
                                main.post(new Runnable() {
                                    @Override public void run() {
                                        l.onFresh(live, vod, series);
                                    }
                                });
                            }
                        }).start();
                    }
                });
            }
        }).start();
    }
}
