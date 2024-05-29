package com.android.internal.telephony.euicc;

import android.content.Context;
import android.telephony.TelephonyManager;
import android.telephony.UiccSlotInfo;
import android.telephony.euicc.EuiccCardManager;
import android.util.Log;

import java.util.ArrayList;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

public class EuiccWipe {
    static final String TAG = EuiccWipe.class.getSimpleName();

    public static boolean run(Context ctx, int flags) {
        Log.d(TAG, "start");

        var telephonyManager = ctx.getSystemService(TelephonyManager.class);
        if (telephonyManager == null) {
            Log.d(TAG, "TelephonyManager is null");
            return false;
        }

        var euiccCardManager = ctx.getSystemService(EuiccCardManager.class);
        if (euiccCardManager == null) {
            Log.d(TAG, "EuiccCardManager is null");
            return false;
        }

        UiccSlotInfo[] usiArr = telephonyManager.getUiccSlotsInfo();
        if (usiArr == null) {
            Log.d(TAG, "getUiccSlotsInfo is null");
            return false;
        }

        Executor callbackExecutor = Executors.newSingleThreadExecutor();
        ArrayList<CompletableFuture<Integer>> futures = new ArrayList<>();

        for (UiccSlotInfo usi : usiArr) {
            Log.d(TAG, "processing " + usi);
            if (usi == null || !usi.getIsEuicc()) {
                continue;
            }

            String cardId = usi.getCardId();

            var future = new CompletableFuture<Integer>();

            var cb = new EuiccCardManager.ResultCallback<Void>() {
                @Override
                public void onComplete(int resultCode, Void result) {
                    Log.d(TAG, "onComplete: cardId " + cardId + " resultCode " + resultCode);
                    future.complete(resultCode);
                }
            };

            int opts = EuiccCardManager.RESET_OPTION_DELETE_OPERATIONAL_PROFILES
                | EuiccCardManager.RESET_OPTION_DELETE_FIELD_LOADED_TEST_PROFILES;

            euiccCardManager.resetMemory(cardId, opts, callbackExecutor, cb, flags);
            futures.add(future);
        }

        CompletableFuture<Void> allFutures = CompletableFuture.allOf(
                futures.toArray(new CompletableFuture[0]));

        // await completion
        try {
            allFutures.get();
        } catch (InterruptedException | ExecutionException e) {
            Log.e(TAG, "", e);
            return false;
        }

        for (CompletableFuture<Integer> future : futures) {
            Integer res = future.getNow(null);
            Objects.requireNonNull(res);
            if (res.intValue() != EuiccCardManager.RESULT_OK) {
                return false;
            }
        }

        Log.d(TAG, "end");
        return true;
    }
}
