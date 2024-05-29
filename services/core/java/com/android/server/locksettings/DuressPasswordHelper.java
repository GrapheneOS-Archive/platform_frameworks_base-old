package com.android.server.locksettings;

import android.annotation.Nullable;
import android.os.HandlerThread;
import android.os.UserHandle;
import android.util.Slog;

import com.android.internal.widget.LockscreenCredential;
import com.android.internal.widget.VerifyCredentialResponse;

import java.util.Objects;
import java.util.UUID;

import static com.android.internal.widget.LockPatternUtils.CREDENTIAL_TYPE_NONE;
import static com.android.internal.widget.LockPatternUtils.CREDENTIAL_TYPE_PASSWORD;
import static com.android.internal.widget.LockPatternUtils.CREDENTIAL_TYPE_PIN;

public class DuressPasswordHelper {
    static final String TAG = DuressPasswordHelper.class.getSimpleName();

    private final LockSettingsService lockSettingsService;
    private final LockSettingsStorage lockSettingsStorage;
    private final SyntheticPasswordManager spManager;
    private final HandlerThread backgroundThread;

    DuressPasswordHelper(LockSettingsService lockSettingsService,
            LockSettingsStorage lockSettingsStorage, SyntheticPasswordManager spManager) {
        var bgThread = new HandlerThread(UUID.randomUUID().toString());
        bgThread.start();
        this.backgroundThread = bgThread;
        this.lockSettingsService = lockSettingsService;
        this.lockSettingsStorage = lockSettingsStorage;
        this.spManager = spManager;
    }

    void onVerifyCredentialResult(@Nullable VerifyCredentialResponse res, @Nullable LockscreenCredential credential) {
        if (res != null && res.getResponseCode() == VerifyCredentialResponse.RESPONSE_OK) {
            return;
        }

        if (credential == null) {
            return;
        }

        // credential verification is slow, don't block the current (usually binder) thread
        backgroundThread.getThreadHandler().post(() -> {
            if (isDuressCredential(credential)) {
                DuressWipe.run(lockSettingsService.getContext());
            }
        });
    }

    void setDuressCredentials(LockscreenCredential ownerCredential,
                                 LockscreenCredential pin, LockscreenCredential password) {
        Objects.requireNonNull(ownerCredential, "ownerCredential");
        Objects.requireNonNull(pin, "pin");
        Objects.requireNonNull(password, "password");

        int userId = UserHandle.USER_SYSTEM;

        if (lockSettingsService.getCredentialType(userId) == CREDENTIAL_TYPE_NONE) {
            if (!ownerCredential.isNone()) {
                throw new IllegalArgumentException("!ownerCredential.isNone()");
            }
        } else if (lockSettingsService.checkCredential(ownerCredential, userId, null)
                .getResponseCode() != VerifyCredentialResponse.RESPONSE_OK) {
            throw new SecurityException("owner credential verification failed");
        }

        if (pin.isNone() && password.isNone()) {
            // exception handling is delegated to the caller
            DuressCredentials.delete(lockSettingsStorage);
            Slog.d(TAG, "deleted duress credentials");
            return;
        }

        DuressCredential.validate(pin, CREDENTIAL_TYPE_PIN);
        DuressCredential.validate(password, CREDENTIAL_TYPE_PASSWORD);

        // exception handling is delegated to the caller
        DuressCredentials.create(spManager, pin, password).save(lockSettingsStorage);
    }

    boolean hasDuressCredentials() {
        return DuressCredentials.maybeGet(lockSettingsStorage) != null;
    }

    private boolean isDuressCredential(LockscreenCredential credential) {
        int credentialType = credential.getType();
        switch (credentialType) {
            case CREDENTIAL_TYPE_PIN:
            case CREDENTIAL_TYPE_PASSWORD:
                DuressCredentials dc = DuressCredentials.maybeGet(lockSettingsStorage);
                return dc != null && dc.get(credentialType).verify(spManager, credential);
            default:
                return false;
        }
    }
}
