package com.android.systemui.qs.tiles;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.BatteryManager;
import android.os.Handler;
import android.os.Looper;
import android.service.quicksettings.Tile;
import android.view.View;
import android.widget.Switch;

import androidx.annotation.Nullable;

import com.android.internal.batteryShare.ReverseWirelessCharger;
import com.android.internal.logging.MetricsLogger;
import com.android.internal.logging.nano.MetricsProto.MetricsEvent;
import com.android.systemui.R;
import com.android.systemui.dagger.qualifiers.Background;
import com.android.systemui.dagger.qualifiers.Main;
import com.android.systemui.plugins.ActivityStarter;
import com.android.systemui.plugins.FalsingManager;
import com.android.systemui.plugins.qs.QSTile.BooleanState;
import com.android.systemui.plugins.statusbar.StatusBarStateController;
import com.android.systemui.qs.QSHost;
import com.android.systemui.qs.logging.QSLogger;
import com.android.systemui.statusbar.policy.KeyguardStateController;

import javax.inject.Inject;

public class BatteryShareTile extends SecureQSTile<BooleanState> {

    public static final String TILE_SPEC = "batteryShare";

    private final Icon mIcon = ResourceIcon.get(R.drawable.ic_battery_share);
    private final ReverseWirelessCharger mWirelessCharger;
    private final Context mContext;

    private final BroadcastReceiver mBroadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            refreshState();
        }
    };

    @Inject
    public BatteryShareTile(
            QSHost host,
            @Background Looper backgroundLooper,
            @Main Handler mainHandler,
            FalsingManager falsingManager,
            MetricsLogger metricsLogger,
            StatusBarStateController statusBarStateController,
            ActivityStarter activityStarter,
            QSLogger qsLogger,
            KeyguardStateController keyguardStateController
    ) {
        super(host, backgroundLooper, mainHandler, falsingManager, metricsLogger,
                statusBarStateController, activityStarter, qsLogger, keyguardStateController);
        mWirelessCharger = ReverseWirelessCharger.getInstance();
        mContext = host.getContext();
        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_BATTERY_CHANGED);
        mContext.registerReceiver(mBroadcastReceiver, filter);
    }

    @Override
    public BooleanState newTileState() {
        return new BooleanState();
    }

    @Override
    protected void handleDestroy() {
        mContext.unregisterReceiver(mBroadcastReceiver);
        super.handleDestroy();
    }

    @Override
    protected void handleUserSwitch(int newUserId) {
    }

    @Override
    public int getMetricsCategory() {
        return MetricsEvent.QS_BATTERY_TILE;
    }

    @Override
    public void handleSetListening(boolean listening) {
        super.handleSetListening(listening);
    }

    @Override
    public boolean isAvailable() {
        return isSupported();
    }

    @Override
    public Intent getLongClickIntent() {
        return new Intent(Intent.ACTION_POWER_USAGE_SUMMARY);
    }

    @Override
    protected void handleClick(@Nullable View view, boolean keyguardShowing) {
        if (checkKeyguard(view, keyguardShowing)) {
            return;
        }
        if (getState().state == Tile.STATE_UNAVAILABLE) {
            refreshState();
            return;
        }
        mWirelessCharger.setRtxMode(!isReverseWirelessChargingOn());
        refreshState();
    }

    private boolean isReverseWirelessChargingOn() {
        return mWirelessCharger.isRtxModeOn();
    }

    private boolean isSupported() {
        return mWirelessCharger.isRtxSupported();
    }

    @Override
    public CharSequence getTileLabel() {
        return mContext.getString(R.string.battery_share_switch_title);
    }

    private int getAvailableStatus() {
        return isPlugged() || !mWirelessCharger.isRtxSupported() ? Tile.STATE_UNAVAILABLE
                : isReverseWirelessChargingOn() ? Tile.STATE_ACTIVE : Tile.STATE_INACTIVE;
    }

    public boolean isPlugged() {
        Intent intent = mContext.registerReceiver(null,
                new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        int plugged = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1);
        return plugged == BatteryManager.BATTERY_PLUGGED_WIRELESS;
    }

    @Override
    protected void handleUpdateState(BooleanState state, Object arg) {
        state.icon = mIcon;
        state.label = mContext.getString(R.string.battery_share_switch_title);
        state.contentDescription = state.label;
        state.expandedAccessibilityClassName = Switch.class.getName();
        state.showRippleEffect = isSupported();
        state.value = isReverseWirelessChargingOn();
        state.secondaryLabel = "";
        state.state = getAvailableStatus();
    }
}
