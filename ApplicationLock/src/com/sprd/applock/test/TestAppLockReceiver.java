package com.sprd.applock.test;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import com.sprd.applock.AppLockListActivity;

public class TestAppLockReceiver extends BroadcastReceiver {
    private static final boolean USE_SECRET_CODE = false;
    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || !USE_SECRET_CODE) {
            return;
        }
        String action = intent.getAction();
        if (action != null && action.equals("android.provider.Telephony.SECRET_CODE")) {
            Intent newIntent = new Intent(context, AppLockListActivity.class);
            newIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(newIntent);
        }
    }
}
