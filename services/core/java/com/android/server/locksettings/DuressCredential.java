package com.android.server.locksettings;

import com.android.internal.widget.LockPatternUtils;
import com.android.internal.widget.LockscreenCredential;
import com.android.internal.widget.PasswordValidationError;
import com.android.server.locksettings.SyntheticPasswordManager.PasswordData;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;

class DuressCredential {
    private final PasswordData salt;
    private final byte[] hashedCredential;

    DuressCredential(PasswordData salt, byte[] hashedCredential) {
        this.salt = salt;
        this.hashedCredential = hashedCredential;
    }

    static DuressCredential create(LockscreenCredential credential, SyntheticPasswordManager spm) {
        PasswordData salt = PasswordData.create(credential.getType(),
                // no need to leak PIN length here, it's used only for PIN auto-confirm
                LockPatternUtils.PIN_LENGTH_UNAVAILABLE);
        byte[] hashedCredential = spm.stretchLskf(credential, salt);
        return new DuressCredential(salt, hashedCredential);
    }

    boolean verify(SyntheticPasswordManager spm, LockscreenCredential credential) {
        return Arrays.equals(hashedCredential, spm.stretchLskf(credential, salt));
    }

    int getType() {
        return salt.credentialType;
    }

    void serialize(DataOutputStream s) throws IOException {
        byte[] saltBytes = salt.toBytes();
        s.writeInt(saltBytes.length);
        s.write(saltBytes);
        s.writeInt(hashedCredential.length);
        s.write(hashedCredential);
    }

    static DuressCredential deserialize(DataInputStream s) throws IOException {
        PasswordData salt = PasswordData.fromBytes(s.readNBytes(s.readInt()));
        byte[] hashedCredential = s.readNBytes(s.readInt());
        return new DuressCredential(salt, hashedCredential);
    }

    static void validate(LockscreenCredential credential, int expectedType) {
        int type = credential.getType();
        if (type != expectedType) {
            throw new IllegalArgumentException("type mismatch: expected " + expectedType + ", got " + type);
        }

        List<PasswordValidationError> validationErrors =
                LockPatternUtils.validateDuressCredential(credential);
        if (!validationErrors.isEmpty()) {
            throw new IllegalArgumentException("validation failed: " +
                    Arrays.toString(validationErrors.toArray()));
        }
    }
}
