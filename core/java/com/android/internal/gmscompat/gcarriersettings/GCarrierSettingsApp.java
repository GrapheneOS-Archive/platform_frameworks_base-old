package com.android.internal.gmscompat.gcarriersettings;

import android.content.Context;
import android.service.carrier.CarrierIdentifier;
import android.telephony.TelephonyManager;

import java.util.Objects;

// A set of hooks that are needed to obtain output of Google's CarrierSettings app for arbitrary
// CarrierIds. That output is used for testing CarrierConfig2 app.
public class GCarrierSettingsApp {
    public static final String PKG_NAME = "com.google.android.carrier";

    public static final int PHONE_SLOT_IDX_FOR_CARRIER_ID_OVERRIDE = 50;
    public static final int SUB_ID_FOR_CARRIER_ID_OVERRIDE = 90;
    // a separate value is need to prevent caching inside GCarrierSettings from interfering with
    // the results
    public static final int SUB_ID_FOR_CARRIER_SERVICE_CALL = 91;

    private static int isTestingModeEnabled;

    static ThreadLocal<CarrierIdentifier> carrierIdOverride;

    public static void init() {
        carrierIdOverride = new ThreadLocal<>();
    }

    public static int maybeOverrideSlotIndex(int subId) {
        if (subId == SUB_ID_FOR_CARRIER_ID_OVERRIDE) {
            return PHONE_SLOT_IDX_FOR_CARRIER_ID_OVERRIDE;
        }
        return -1;
    }

    public static int[] maybeOverrideSubIds(int slotIndex) {
        if (slotIndex == PHONE_SLOT_IDX_FOR_CARRIER_ID_OVERRIDE) {
            return new int[] { SUB_ID_FOR_CARRIER_ID_OVERRIDE };
        }
        return null;
    }

    public static TelephonyManager maybeOverrideCreateTelephonyManager(Context ctx, int subId) {
        if (subId == SUB_ID_FOR_CARRIER_ID_OVERRIDE) {
            CarrierIdentifier override = carrierIdOverride.get();
            Objects.requireNonNull(override);
            return new GCSTelephonyManager(ctx, SUB_ID_FOR_CARRIER_ID_OVERRIDE, override);
        }

        return null;
    }

    public static class GCSTelephonyManager extends TelephonyManager {
        private final CarrierIdentifier carrierIdOverride;

        public GCSTelephonyManager(Context context, int subId, CarrierIdentifier carrierIdOverride) {
            super(context, subId);
            this.carrierIdOverride = carrierIdOverride;
        }

        @Override public String getSimOperator() {
            return carrierIdOverride.getMcc() + carrierIdOverride.getMnc();
        }

        @Override public String getSimOperatorName() {
            return carrierIdOverride.getSpn();
        }

        @Override public String getSubscriberId() {
            return carrierIdOverride.getImsi();
        }

        @Override public String getGroupIdLevel1() {
            return carrierIdOverride.getGid1();
        }
    }
}
