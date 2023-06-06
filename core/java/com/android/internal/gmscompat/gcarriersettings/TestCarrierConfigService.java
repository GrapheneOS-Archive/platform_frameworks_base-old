package com.android.internal.gmscompat.gcarriersettings;

import android.annotation.Nullable;
import android.app.Service;
import android.content.ComponentName;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Binder;
import android.os.Bundle;
import android.os.IBinder;
import android.os.PersistableBundle;
import android.os.RemoteException;
import android.os.ResultReceiver;
import android.service.carrier.CarrierIdentifier;
import android.service.carrier.CarrierService;
import android.service.carrier.CarrierService.ICarrierServiceWrapper;
import android.service.carrier.IApnSourceService;
import android.service.carrier.ICarrierService;
import android.util.Log;

import com.android.internal.util.Preconditions;

import java.util.ArrayList;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

// This service is only used for testing the CarrierConfig2 app.
// It allows CarrierConfig2 to get carrier configs from Google's CarrierSettings app for arbitrary
// CarrierIds.
public class TestCarrierConfigService extends Service {

    private final Executor bgExecutor = Executors.newSingleThreadExecutor();

    private Future<IBinder> carrierServiceF;
    private Future<IBinder> apnServiceF;

    private final ArrayList<ServiceConnection> serviceConnections = new ArrayList<>();

    public static final String KEY_CARRIER_SERVICE_RESULT = "carrier_service_result";
    public static final String KEY_APN_SERVICE_RESULT = "apn_service_result";

    private final Binder binder = new ICarrierConfigsLoader.Stub() {
        private ICarrierService carrierService;
        private IApnSourceService apnService;

        private void maybeWaitForServices() {
            synchronized (this) {
                if (carrierService == null) {
                    try {
                        carrierService = ICarrierService.Stub.asInterface(carrierServiceF.get());
                        apnService = IApnSourceService.Stub.asInterface(apnServiceF.get());
                    } catch (ExecutionException|InterruptedException e) {
                        throw new IllegalStateException(e);
                    }
                }
            }
        }

        @Override
        public Bundle getConfigs(CarrierIdentifier carrierId) throws RemoteException {
            maybeWaitForServices();

            var carrierServiceResultF = new CompletableFuture<Bundle>();
            var resultReceiver = new ResultReceiver(null) {
                @Override
                protected void onReceiveResult(int resultCode, Bundle resultData) {
                    Preconditions.checkArgument(resultCode == ICarrierServiceWrapper.RESULT_OK);
                    carrierServiceResultF.complete(resultData);
                }
            };
            carrierService.getCarrierConfig(GCarrierSettingsApp.SUB_ID_FOR_CARRIER_SERVICE_CALL, carrierId, resultReceiver);

            GCarrierSettingsApp.carrierIdOverride.set(carrierId);
            ContentValues[] apns = apnService.getApns(GCarrierSettingsApp.SUB_ID_FOR_CARRIER_ID_OVERRIDE);

            var result = new Bundle();
            result.putParcelableArray(KEY_APN_SERVICE_RESULT, apns);

            try {
                Bundle b = carrierServiceResultF.get();
                PersistableBundle pb = b.getParcelable(ICarrierServiceWrapper.KEY_CONFIG_BUNDLE);
                result.putParcelable(KEY_CARRIER_SERVICE_RESULT, pb);
            } catch (InterruptedException|ExecutionException e) {
                throw new IllegalStateException(e);
            }

            return result;
        }
    };

    @Override
    public void onCreate() {
        var csIntent = new Intent(CarrierService.CARRIER_SERVICE_INTERFACE);
        csIntent.setPackage(GCarrierSettingsApp.PKG_NAME);
        carrierServiceF = bind(csIntent);

        var apnServiceIntent = new Intent();
        apnServiceIntent.setComponent(ComponentName.createRelative(GCarrierSettingsApp.PKG_NAME, ".ApnSourceService"));
        apnServiceF = bind(apnServiceIntent);
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    CompletableFuture<IBinder> bind(Intent intent) {
        String TAG = "TestCConfigService.bind";

        var future = new CompletableFuture<IBinder>();

        var sc = new ServiceConnection() {
            @Override
            public void onServiceConnected(ComponentName name, IBinder service) {
                Log.d(TAG, "onServiceConnected " + name);
                future.complete(service);
            }

            @Override
            public void onServiceDisconnected(ComponentName name) {
                Log.d(TAG, "onServiceDisconnected " + name);
            }

            @Override
            public void onBindingDied(ComponentName name) {
                throw new IllegalStateException("onBindingDied " + name);
            }

            @Override
            public void onNullBinding(ComponentName name) {
                throw new IllegalStateException("onNullBinding " + name);
            }
        };

        int bindFlags = Context.BIND_AUTO_CREATE | Context.BIND_ABOVE_CLIENT;
        boolean res = bindService(intent, bindFlags, bgExecutor, sc);
        serviceConnections.add(sc);

        if (!res) {
            throw new IllegalStateException("unable to bind to " + intent);
        }

        return future;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        for (ServiceConnection c : serviceConnections) {
            unbindService(c);
        }
    }
}
