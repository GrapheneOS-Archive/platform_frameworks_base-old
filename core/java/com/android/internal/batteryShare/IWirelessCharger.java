package com.android.internal.batteryShare;

import android.os.HidlSupport;
import android.os.HwBinder;
import android.os.HwParcel;
import android.os.IHwBinder;
import android.os.IHwInterface;
import android.os.RemoteException;

import java.util.ArrayList;

public interface IWirelessCharger extends IHwInterface {
    ArrayList<String> interfaceChain() throws RemoteException;

    boolean isRtxModeOn() throws RemoteException;

    boolean isRtxSupported() throws RemoteException;

    boolean linkToDeath(IHwBinder.DeathRecipient deathRecipient, long j) throws RemoteException;

    byte setRtxMode(boolean z) throws RemoteException;

    static IWirelessCharger asInterface(IHwBinder iHwBinder) {
        if (iHwBinder == null) return null;
        IWirelessCharger queryLocalInterface = (IWirelessCharger) iHwBinder.queryLocalInterface("vendor.google.wireless_charger@1.2::IWirelessCharger");
        if (queryLocalInterface != null) return queryLocalInterface;

        Proxy proxy = new Proxy(iHwBinder);
        try {
            for (String interfaceChain : proxy.interfaceChain()) {
                if (interfaceChain.equals("vendor.google.wireless_charger@1.2::IWirelessCharger")) {
                    return proxy;
                }
            }
        } catch (RemoteException exception) {
            exception.rethrowFromSystemServer();
        }
        return null;
    }

    static IWirelessCharger getService() throws RemoteException {
        return asInterface(HwBinder.getService("vendor.google.wireless_charger@1.2::IWirelessCharger", "default"));
    }

    final class Proxy implements IWirelessCharger {
        private final IHwBinder mRemote;

        public Proxy(IHwBinder iHwBinder) {
            this.mRemote = iHwBinder;
        }

        public IHwBinder asBinder() {
            return this.mRemote;
        }

        public boolean equals(Object obj) {
            return HidlSupport.interfacesEqual(this, obj);
        }

        public int hashCode() {
            return asBinder().hashCode();
        }

        @Override
        public boolean isRtxSupported() throws RemoteException {
            HwParcel hwParcel = wirelessChargerHwParcel();
            HwParcel resultParcel = new HwParcel();
            try {
                this.mRemote.transact(17, hwParcel, resultParcel, 0);
                resultParcel.verifySuccess();
                hwParcel.releaseTemporaryStorage();
                return resultParcel.readBool();
            } finally {
                resultParcel.release();
            }
        }

        private HwParcel wirelessChargerHwParcel() {
            HwParcel hwParcel = new HwParcel();
            hwParcel.writeInterfaceToken("vendor.google.wireless_charger@1.2::IWirelessCharger");
            return hwParcel;
        }

        @Override
        public boolean isRtxModeOn() throws RemoteException {
            HwParcel hwParcel = wirelessChargerHwParcel();
            HwParcel resultParcel = new HwParcel();
            try {
                this.mRemote.transact(18, hwParcel, resultParcel, 0);
                resultParcel.verifySuccess();
                hwParcel.releaseTemporaryStorage();
                return resultParcel.readBool();
            } finally {
                resultParcel.release();
            }
        }

        @Override
        public byte setRtxMode(boolean z) throws RemoteException {
            HwParcel hwParcel = wirelessChargerHwParcel();
            hwParcel.writeBool(z);
            HwParcel resultParcel = new HwParcel();
            try {
                this.mRemote.transact(20, hwParcel, resultParcel, 0);
                resultParcel.verifySuccess();
                hwParcel.releaseTemporaryStorage();
                return resultParcel.readInt8();
            } finally {
                resultParcel.release();
            }
        }

        @Override
        public ArrayList<String> interfaceChain() throws RemoteException {
            HwParcel hwParcel = new HwParcel();
            hwParcel.writeInterfaceToken("android.hidl.base@1.0::IBase");
            HwParcel hwParcel2 = new HwParcel();
            try {
                this.mRemote.transact(256067662, hwParcel, hwParcel2, 0);
                hwParcel2.verifySuccess();
                hwParcel.releaseTemporaryStorage();
                return hwParcel2.readStringVector();
            } finally {
                hwParcel2.release();
            }
        }

        @Override
        public boolean linkToDeath(IHwBinder.DeathRecipient deathRecipient, long j) throws RemoteException {
            return this.mRemote.linkToDeath(deathRecipient, j);
        }
    }
}
