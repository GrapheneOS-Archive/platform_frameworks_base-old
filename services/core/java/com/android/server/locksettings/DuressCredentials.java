package com.android.server.locksettings;

import android.annotation.Nullable;
import android.database.sqlite.SQLiteDiskIOException;
import android.os.UserHandle;

import com.android.internal.widget.LockscreenCredential;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

import libcore.util.HexEncoding;

import static com.android.internal.widget.LockPatternUtils.CREDENTIAL_TYPE_PASSWORD;
import static com.android.internal.widget.LockPatternUtils.CREDENTIAL_TYPE_PIN;

class DuressCredentials {
    final DuressCredential pin;
    final DuressCredential password;

    DuressCredentials(DuressCredential pin, DuressCredential password) {
        if (pin.getType() != CREDENTIAL_TYPE_PIN) {
            throw new IllegalArgumentException();
        }
        this.pin = pin;
        if (password.getType() != CREDENTIAL_TYPE_PASSWORD) {
            throw new IllegalArgumentException();
        }
        this.password = password;
    }

    static DuressCredentials create(SyntheticPasswordManager spm,
                                    LockscreenCredential pin, LockscreenCredential password) {
        return new DuressCredentials(DuressCredential.create(pin, spm),
                DuressCredential.create(password, spm));
    }

    private static final String LOCK_SETTINGS_STORAGE_KEY = "duress_credentials";
    private static final int LOCK_SETTINGS_STORAGE_USER_ID = UserHandle.USER_SYSTEM;

    void save(LockSettingsStorage lss) {
        lss.setString(LOCK_SETTINGS_STORAGE_KEY, serialize(), LOCK_SETTINGS_STORAGE_USER_ID);
    }

    @Nullable
    static DuressCredentials maybeGet(LockSettingsStorage lss) {
        String s = lss.getString(LOCK_SETTINGS_STORAGE_KEY, null, LOCK_SETTINGS_STORAGE_USER_ID);
        if (s == null) {
            return null;
        }
        return deserialize(s);
    }

    static void delete(LockSettingsStorage lss) {
        lss.removeKey(LOCK_SETTINGS_STORAGE_KEY, LOCK_SETTINGS_STORAGE_USER_ID);
    }

    DuressCredential get(int type) {
        return switch (type) {
            case CREDENTIAL_TYPE_PIN -> pin;
            case CREDENTIAL_TYPE_PASSWORD -> password;
            default -> throw new IllegalArgumentException(Integer.toString(type));
        };
    }

    private static final int VERSION = 0;

    private String serialize() {
        var bos = new ByteArrayOutputStream(1000);
        var s = new DataOutputStream(bos);
        try {
            s.writeByte(VERSION);
            pin.serialize(s);
            password.serialize(s);
        } catch (IOException e) {
            // ByteArrayOutputStream never throws IOException
            throw new IllegalStateException(e);
        }

        return HexEncoding.encodeToString(bos.toByteArray());
    }

    private static DuressCredentials deserialize(String str) {
        var s = new DataInputStream(new ByteArrayInputStream(HexEncoding.decode(str)));
        try {
            int version = s.readByte();
            if (version > VERSION) {
                throw new IllegalArgumentException(str);
            }
            var pin = DuressCredential.deserialize(s);
            var password = DuressCredential.deserialize(s);
            if (s.available() != 0) {
                throw new IllegalArgumentException(str);
            }
            return new DuressCredentials(pin, password);
        } catch (IOException e) {
            // ByteArrayInputStream never throws IOException
            throw new IllegalStateException(e);
        }
    }
}
