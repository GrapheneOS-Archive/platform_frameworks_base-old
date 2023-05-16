package com.android.internal.app;

import android.annotation.Nullable;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.ContentObserver;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.os.UserHandle;
import android.util.Log;
import android.util.Pair;

import java.util.ArrayList;
import java.util.Collections;

public class ContentProviderRedirector {

    private static volatile boolean isEnabled;

    public static void enable() {
        isEnabled = true;
    }

    public static String translateContentProviderAuthority(String auth) {
        if (!isEnabled) {
            return auth;
        }

        String t = null;

        return t != null ? t : auth;
    }

    // registering a ContentObserver requires having the read permission for the underlying
    // ContentProvider
    public static boolean shouldSkipRegisterContentObserver(Uri uri, boolean notifyForDescendants,
                                                            ContentObserver observer, int userId) {
        if (!isEnabled) {
            return false;
        }

        return false;
    }

    public static boolean shouldSkipUnregisterContentObserver(ContentObserver observer) {
        if (!isEnabled) {
            return false;
        }

        return false;
    }

    public static boolean shouldSkipNotifyChange(Uri uri, @Nullable ContentObserver observer,
                                                 @ContentResolver.NotifyFlags int flags) {
        if (!isEnabled) {
            return false;
        }

        return false;
    }

    // a helper class for keeping track of skipped ContentObservers and for delivering content
    // change notifications to them manually (see shouldSkipRegisterContentObserver())
    public static class ContentObserverList {
        private final ArrayList<Pair<ContentObserver, ArrayList<Uri>>> list = new ArrayList<>();

        public void add(ContentObserver observer, Uri uri) {
            synchronized (list) {
                for (var pair : list) {
                    if (pair.first == observer) {
                        pair.second.add(uri);
                        return;
                    }
                }
                var uris = new ArrayList<Uri>();
                uris.add(uri);
                list.add(Pair.create(observer, uris));
            }
        }

        @Nullable
        public boolean remove(ContentObserver observer) {
            synchronized (list) {
                for (int i = 0, m = list.size(); i < m; ++i) {
                    var pair = list.get(i);
                    if (pair.first == observer) {
                        list.remove(i);
                        return true;
                    }
                }

                return false;
            }
        }

        public void notifyAllObservers() {
            final Pair<ContentObserver, ArrayList<Uri>>[] arr;
            synchronized (list) {
                arr = list.toArray(new Pair[0]);
            }

            new Handler(Looper.getMainLooper()).post(() -> {
                int myUserId = UserHandle.myUserId();
                for (var pair : arr) {
                    var uris = Collections.unmodifiableList(pair.second);
                    pair.first.dispatchChange(false, uris, 0, myUserId);
                }
            });
        }

        class Receiver extends BroadcastReceiver {
            @Override
            public void onReceive(Context context, Intent intent) {
                Log.d("ContentChangeReceiver", intent.toString());
                notifyAllObservers();
            }
        }

        public void registerNotificationReceiver(Context ctx, String intentAction, String permission) {
            var receiver = new Receiver();
            var filter = new IntentFilter(intentAction);
            ctx.registerReceiver(receiver, filter, permission, null, Context.RECEIVER_EXPORTED);
        }
    }
}
