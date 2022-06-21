package com.android.internal.batteryShare;

import android.compat.annotation.UnsupportedAppUsage;
import android.os.IHwBinder;
import android.os.RemoteException;
import android.util.Log;

import java.util.NoSuchElementException;

public class ReverseWirelessCharger implements
        IHwBinder.DeathRecipient {
    private static final String TAG = "ReverseWirelessCharger";
    private static ReverseWirelessCharger sReverseWirelessCharger;
    private IWirelessCharger mWirelessCharger;

    private ReverseWirelessCharger() {
    }

    @UnsupportedAppUsage
    public static ReverseWirelessCharger getInstance() {
        if (sReverseWirelessCharger == null) {
            sReverseWirelessCharger = new ReverseWirelessCharger();
        }
        return sReverseWirelessCharger;
    }

    public void serviceDied(long j) {
        Log.i(TAG, "serviceDied");
        this.mWirelessCharger = null;
    }

    private void initHALInterface() {
        if (this.mWirelessCharger == null) {
            try {
                IWirelessCharger service = IWirelessCharger.getService();
                this.mWirelessCharger = service;
                service.linkToDeath(this, 0L);
            } catch (Exception e) {
                Log.i(TAG, "" + e.getMessage(), e);
                this.mWirelessCharger = null;
            }
        }
    }

    @UnsupportedAppUsage
    public boolean isRtxSupported() {
        initHALInterface();
        IWirelessCharger iWirelessCharger = this.mWirelessCharger;
        if (iWirelessCharger != null) {
            try {
                return iWirelessCharger.isRtxSupported();
            } catch (NoSuchElementException | RemoteException e) {
                Log.i(TAG, "isRtxSupported error : ", e);
            }
        }
        return false;
    }

    @UnsupportedAppUsage
    public void setRtxMode(boolean z) {
        initHALInterface();
        IWirelessCharger iWirelessCharger = this.mWirelessCharger;
        if (iWirelessCharger != null) {
            try {
                iWirelessCharger.setRtxMode(z);
            } catch (NoSuchElementException | RemoteException e) {
                Log.i(TAG, "setRtxMode error : ", e);
            }
        }
    }

    @UnsupportedAppUsage
    public boolean isRtxModeOn() {
        initHALInterface();
        IWirelessCharger iWirelessCharger = this.mWirelessCharger;
        if (iWirelessCharger != null) {
            try {
                return iWirelessCharger.isRtxModeOn();
            } catch (NoSuchElementException | RemoteException e) {
                Log.i(TAG, "isRtxModeOn error : ", e);
            }
        }
        return false;
    }

}