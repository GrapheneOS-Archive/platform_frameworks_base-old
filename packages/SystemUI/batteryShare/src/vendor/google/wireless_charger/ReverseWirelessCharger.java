package vendor.google.wireless_charger;

import android.os.IBinder;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.util.Log;

import java.util.NoSuchElementException;

public class ReverseWirelessCharger {
    private static final String TAG = "ReverseWirelessCharger";
    private static ReverseWirelessCharger sReverseWirelessCharger;
    private final IWirelessCharger mWirelessCharger;

    private ReverseWirelessCharger() {
        String serviceName = "vendor.google.wireless_charger.IWirelessCharger/default";
        IBinder service = ServiceManager.getService(serviceName);
        mWirelessCharger = IWirelessCharger.Stub.asInterface(service);
    }

    public static ReverseWirelessCharger getInstance() {
        if (sReverseWirelessCharger == null) {
            sReverseWirelessCharger = new ReverseWirelessCharger();
        }
        return sReverseWirelessCharger;
    }

    public boolean isRtxSupported() {
        if (mWirelessCharger != null) {
            try {
                return mWirelessCharger.isRtxSupported();
            } catch (NoSuchElementException | RemoteException e) {
                Log.e(TAG, "isRtxSupported error : ", e);
            }
        }
        return false;
    }

    public void setRtxMode(boolean isOn) {
        if (mWirelessCharger != null) {
            try {
                mWirelessCharger.setRtxMode(isOn);
            } catch (NoSuchElementException | RemoteException e) {
                Log.e(TAG, "setRtxMode error : ", e);
            }
        }
    }

    public boolean isRtxModeOn() {
        if (mWirelessCharger != null) {
            try {
                return mWirelessCharger.isRtxModeOn();
            } catch (NoSuchElementException | RemoteException e) {
                Log.e(TAG, "isRtxModeOn error : ", e);
            }
        }
        return false;
    }

}