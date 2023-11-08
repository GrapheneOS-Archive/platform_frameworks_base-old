package com.android.server.logcat;

import android.content.Context;
import android.util.Slog;

import static java.nio.charset.StandardCharsets.UTF_8;

public class LogdNotableMessage {
    static final String TAG = LogdNotableMessage.class.getSimpleName();

    static void onNotableMessage(Context ctx, int type, int uid, int pid, byte[] msgBytes) {
        Slog.d(TAG, "uid " + uid + ", pid " + pid + ", msg " + new String(msgBytes, UTF_8));

        switch (type) {
        }
    }
}
