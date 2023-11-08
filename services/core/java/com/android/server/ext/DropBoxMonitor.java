package com.android.server.ext;

import android.annotation.CurrentTimeMillisLong;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.ext.ErrorReportUi;
import android.os.DropBoxManager;
import android.os.Environment;
import android.os.Handler;
import android.text.TextUtils;
import android.util.ArrayMap;
import android.util.AtomicFile;
import android.util.Slog;

import com.android.internal.R;
import com.android.internal.os.BackgroundThread;
import com.android.server.BootReceiver;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.function.Consumer;

import static java.util.concurrent.TimeUnit.MINUTES;

// See DropboxManager docs for more info
public class DropBoxMonitor {
    static final String TAG = DropBoxMonitor.class.getSimpleName();

    final Context context;
    final DropBoxManager dropBoxManager;
    // tag name -> tag
    final ArrayMap<String, Tag> tags = new ArrayMap(7);

    @CurrentTimeMillisLong
    final long initialTimestamp = System.currentTimeMillis();

    private volatile boolean historyCheckCompleted;

    // When a critical system process crashes, device might reboot or freeze before the user is
    // able to read the error report. The error report is persisted to storage in most such cases
    // as a tombstone or as a DropBox entry.
    //
    // To handle showing such error reports, timestamp of most recent entry for each tag is recorded
    // to stateFile in a delayed way, and each tag is checked for historical entries after each boot.
    final AtomicFile stateFile = new AtomicFile(new File(Environment.getDataSystemDirectory(), "dropbox_monitor"));
    private static final long STATE_PERSISTENCE_DELAY = MINUTES.toMillis(3);

    public static void init(Context ctx) {
        new DropBoxMonitor(ctx);
    }

    private DropBoxMonitor(Context context) {
        this.context = context;
        dropBoxManager = context.getSystemService(DropBoxManager.class);

        watchTag("SYSTEM_TOMBSTONE_PROTO_WITH_HEADERS", this::handleTombstone);
        watchTag("system_server_crash", this::handleSystemServerCrash);
        watchTag("SYSTEM_LAST_KMSG", this::handleKernelCrash);
        watchTag("SYSTEM_FSCK", this::handleFsckEntry);

        try {
            restoreState();
        } catch (Throwable t) {
            if (!(t instanceof FileNotFoundException)) {
                Slog.e(TAG, "unable to restore state", t);
            }
        }

        // check for historical unreported entries from previous boot(s)
        int entryCnt = 0;
        for (Tag t : tags.values()) {
            entryCnt += checkTag(t);
        }

        historyCheckCompleted = true;

        if (entryCnt > 0) {
            scheduleSaveState();
        }

        var filter = new IntentFilter(DropBoxManager.ACTION_DROPBOX_ENTRY_ADDED);
        context.registerReceiver(new EntryAddedReceiver(), filter, null, BackgroundThread.getHandler());
    }

    static class Tag {
        final String name;
        final Consumer<DropBoxManager.Entry> handler;
        @CurrentTimeMillisLong volatile long lastSeen;

        Tag(String name, Consumer<DropBoxManager.Entry> handler) {
            this.name = name;
            this.handler = handler;
        }
    }

    class EntryAddedReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            String tagName = intent.getStringExtra(DropBoxManager.EXTRA_TAG);
            Tag tag = tags.get(tagName);
            if (tag != null) {
                try {
                    int entryCnt = checkTag(tag);
                    if (entryCnt > 0) {
                        scheduleSaveState();
                    }
                } catch (Exception e) {
                    Slog.e(TAG, "", e);
                }
            }
        }
    }

    private void watchTag(String name, Consumer<DropBoxManager.Entry> handler) {
        var t = new Tag(name, handler);
        // in case this tag is not present in persisted state
        t.lastSeen = Math.max(0L, initialTimestamp - STATE_PERSISTENCE_DELAY);
        tags.put(name, t);
    }

    int checkTag(Tag tag) {
        int entryCnt = 0;

        for (;;) {
            try (DropBoxManager.Entry e = dropBoxManager.getNextEntry(tag.name, tag.lastSeen)) {
                if (e == null) {
                    return entryCnt;
                }

                tag.lastSeen = e.getTimeMillis();

                try {
                    tag.handler.accept(e);
                } catch (Exception ex) {
                    Slog.e(TAG, "handler for " + tag.name + " tag failed", ex);
                }

                ++entryCnt;
            } catch (Exception ex) {
                Slog.e(TAG, "checkTag() failed for " + tag.name);
                return entryCnt;
            }
        }
    }

    void scheduleSaveState() {
        byte[] state = serializeState();

        Handler h = BackgroundThread.getHandler();
        // DropBox is rate-limited, which limits memory usage of pending saveState() calls
        h.postDelayed(() -> saveState(state), STATE_PERSISTENCE_DELAY);
    }

    private void saveState(byte[] state) {
        FileOutputStream out = null;
        try {
            out = stateFile.startWrite();
            out.write(state);
            stateFile.finishWrite(out);
        } catch (IOException t) {
            stateFile.failWrite(out);
            Slog.e(TAG, "", t);
        }
    }

    private static final int FILE_VERSION = 0;

    byte[] serializeState()  {
        var bos = new ByteArrayOutputStream(500);
        var s = new DataOutputStream(bos);
        Collection<Tag> l = tags.values();

        try {
            s.write(FILE_VERSION);
            s.writeInt(l.size());

            for (Tag t : l) {
                s.writeUTF(t.name);
                s.writeLong(t.lastSeen);
            }
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }

        return bos.toByteArray();
    }

    void restoreState() throws IOException {
        var s = new DataInputStream(new ByteArrayInputStream(stateFile.readFully()));

        int ver = s.readByte();
        if (ver != FILE_VERSION) {
            Slog.e(TAG, "unknown file version: " + ver);
            return;
        }

        int cnt = s.readInt();
        for (int i = 0; i < cnt; ++i) {
            String tagName = s.readUTF();
            long lastSeen = s.readLong();
            Tag t = tags.get(tagName);
            if (t != null) {
                t.lastSeen = lastSeen;
            } else {
                Slog.e(TAG, "restoreState: unknown tag: " + tagName);
            }
        }
    }

    void handleTombstone(DropBoxManager.Entry entry) {
        if (historyCheckCompleted) {
            // subsequent entries are handled directly in TombstoneHandler
            return;
        }

        TombstoneHandler.handleDropBoxEntry(this, entry);
    }

    void handleKernelCrash(DropBoxManager.Entry e) {
        String text = readEntryText(e, "kernel_crash");
        if (text != null) {
            for (String line : text.split("\n")) {
                String prefix = BootReceiver.KMSG_BOOT_REASON_PREFIX;
                if (line.startsWith(prefix)) {
                    String bootReason = line.substring(prefix.length());

                    switch (bootReason) {
                        case "reboot", "PowerKey", "normal", "recovery" -> {
                            Slog.d(TAG, "skipping last_kmsg, its boot reason is " + bootReason);
                            return;
                        }
                    }

                    break;
                }
            }

            showCrashNotif(e, "Kernel", text);
        }
    }

    void handleSystemServerCrash(DropBoxManager.Entry e) {
        String text = readEntryText(e, "sserver");
        if (text != null) {
            showCrashNotif(e, "system_server", text);
        }
    }

    void handleFsckEntry(DropBoxManager.Entry e) {
        String text = readEntryText(e, "fsck");
        if (text != null) {
            var i = ErrorReportUi.createBaseIntent(ErrorReportUi.ACTION_CUSTOM_REPORT, text);
            i.putExtra(Intent.EXTRA_TITLE, "File system check error");
            i.putExtra(ErrorReportUi.EXTRA_TYPE, "fsck_error");
            i.putExtra(ErrorReportUi.EXTRA_SHOW_REPORT_BUTTON, true);

            SystemJournalNotif.showGeneric(context, e.getTimeMillis(), context.getString(R.string.fsck_error_notif_title), i);
        }
    }

    void showCrashNotif(DropBoxManager.Entry entry, String progName, String errorReport) {
        SystemJournalNotif.showCrash(context, progName, errorReport, entry.getTimeMillis());
    }

    private static String readEntryText(DropBoxManager.Entry entry, String logTagSuffix) {
        String TAG = "DropBox_" + logTagSuffix;
        String text = null;
        try (InputStream s = entry.getInputStream()) {
            if (s == null) {
                Slog.d(TAG, "entry.getInputStream() is null");
                return null;
            }

            text = new String(s.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            Slog.d(TAG, "", e);
        }

        if (text == null) {
            Slog.d(TAG, "unable to read entry text");
            return null;
        }

        if (TextUtils.isEmpty(text)) {
            return null;
        }

        return text;
    }
}
