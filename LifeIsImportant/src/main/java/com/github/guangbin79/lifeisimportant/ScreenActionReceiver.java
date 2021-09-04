package com.github.guangbin79.lifeisimportant;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;

public class ScreenActionReceiver extends BroadcastReceiver {
    private Context context = null;
    private Listener listener = null;

    interface Listener {
        void onLockscreen();
        void onUnlockscreen();
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        if (action.equals(Intent.ACTION_SCREEN_ON)) {
            if (listener != null) {
                listener.onUnlockscreen();
            }
        } else if (action.equals(Intent.ACTION_SCREEN_OFF)) {
            if (listener != null) {
                listener.onLockscreen();
            }
        }
    }

    public void registerScreenActionReceiver(Context context, Listener listener) {
        if (this.listener == null) {
            this.listener = listener;
            this.context = context;

            IntentFilter filter = new IntentFilter();
            filter.addAction(Intent.ACTION_SCREEN_OFF);
            filter.addAction(Intent.ACTION_SCREEN_ON);
            context.registerReceiver(ScreenActionReceiver.this, filter);
        }
    }

    public void unRegisterScreenActionReceiver() {
        if (listener != null) {
            context.unregisterReceiver(ScreenActionReceiver.this);
            context = null;
            listener = null;
        }
    }
}