package com.android.server.ext;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Handler;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

public class PendingActionReceiver extends BroadcastReceiver {
    private final SystemServerExt sse;
    private final Consumer<Intent> action;
    private final Handler handler;

    private String intentAction;
    private static final AtomicLong idSrc = new AtomicLong();

    public PendingActionReceiver(Consumer<Intent> action) {
        this(action, SystemServerExt.get().bgHandler);
    }

    public PendingActionReceiver(Consumer<Intent> action, Handler handler) {
        sse = SystemServerExt.get();
        this.action = action;
        this.handler = handler;
    }

    public Intent getIntentTemplate() {
        synchronized (this) {
            if (intentAction == null) {
                intentAction = PendingActionReceiver.class.getName() + '.' + idSrc.getAndIncrement();
                var filter = new IntentFilter(intentAction);
                sse.registerReceiver(this, filter, handler);
            }
        }

        var i = new Intent(intentAction);
        i.setIdentifier(UUID.randomUUID().toString());
        i.setPackage(sse.context.getPackageName());
        return i;
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        action.accept(intent);
    }
}
