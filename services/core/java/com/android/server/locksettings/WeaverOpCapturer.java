package com.android.server.locksettings;

import android.annotation.Nullable;
import android.hardware.weaver.WeaverReadResponse;
import android.os.Build;
import android.util.Log;
import android.util.Slog;

import java.util.ArrayList;
import java.util.List;

import libcore.util.HexEncoding;

public class WeaverOpCapturer {
    static final String TAG = WeaverOpCapturer.class.getSimpleName();
    static final boolean ENABLE_OP_LOGGING = Build.IS_DEBUGGABLE && Log.isLoggable(TAG, Log.VERBOSE);

    private static final ThreadLocal<Session> activeSession = new ThreadLocal<>();

    enum ValueType {
        NON_ZERO,
        ZERO,
        NULL,
        ERROR,
    }

    public record WeaverRead(int slot, int responseStatus,
                             ValueType valueType, int valueLength,
                             Exception ex) implements WeaverOp {}

    public record WeaverWrite(int slot, ValueType valueType, int valueLength,
                              Exception ex) implements WeaverOp {}

    public interface WeaverOp {}

    public static class Session implements AutoCloseable {
        private ArrayList<WeaverOp> capturedOps = new ArrayList<>();

        public Session() {
            if (activeSession.get() != null) {
                throw new IllegalStateException();
            }
            activeSession.set(this);
        }

        public List<WeaverOp> getCapturedOps() {
            return capturedOps;
        }

        @Override
        public void close() {
            if (activeSession.get() != this) {
                throw new IllegalStateException();
            }
            activeSession.set(null);
        }
    }

    public static void onRead(int slot, byte[] key,
                              @Nullable WeaverReadResponse response,
                              @Nullable Exception ex) {
        if (ENABLE_OP_LOGGING) {
            if (response == null) {
                Slog.d(TAG, "read", ex);
            } else {
                Slog.d(TAG, "read; slot " + slot
                        + ", status " + response.status
                        + ", key " + HexEncoding.encodeToString(key)
                        + ", value " + HexEncoding.encodeToString(response.value)
                        + ", timeout " + response.timeout);
            }
        }

        Session s = activeSession.get();
        if (s == null) {
            return;
        }

        byte[] value = response == null ? null : response.value;
        int responseStatus = response == null ? -1 : response.status;
        int valueLength = value == null ? -1 : value.length;
        var wr = new WeaverRead(slot, responseStatus, getValueType(value, ex), valueLength, ex);
        s.capturedOps.add(wr);
    }

    public static void onWrite(int slot, byte[] key, byte[] value,
                               @Nullable Exception ex) {
        if (ENABLE_OP_LOGGING) {
            if (ex != null) {
                Slog.d(TAG, "write", ex);
            } else {
                Slog.d(TAG, "write; slot " + slot
                        + ", key " + HexEncoding.encodeToString(key)
                        + ", value " + HexEncoding.encodeToString(value));
            }
        }

        Session s = activeSession.get();
        if (s == null) {
            return;
        }

        int valueLength = value == null ? -1 : value.length;
        var ww = new WeaverWrite(slot, getValueType(value, ex), valueLength, ex);
        s.capturedOps.add(ww);
    }

    // needed for preventing leakage of the actual Weaver slot value
    private static ValueType getValueType(byte[] value, Exception ex) {
        if (ex != null) {
            return ValueType.ERROR;
        }
        if (value == null) {
            return ValueType.NULL;
        }
        for (byte b : value) {
            if (b != 0) {
                return ValueType.NON_ZERO;
            }
        }
        return ValueType.ZERO;
    }
}
