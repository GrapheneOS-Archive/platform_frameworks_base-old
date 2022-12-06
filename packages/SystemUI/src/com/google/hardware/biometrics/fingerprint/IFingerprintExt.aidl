package com.google.hardware.biometrics.fingerprint;

interface IFingerprintExt {
    void onPointerDown(long pointerId, int x, int y, float minor, float major);

    void onPointerUp(long pointerId);

    void onUiReady();
}
