
package com.sprd.applock;

import android.app.Application;
import android.content.Context;
import android.content.Intent;
import android.os.SystemProperties;
import android.os.UserHandle;
import android.util.Log;

import com.sprd.launchapp.LaunchAppService;

public class AppLockApplication extends Application {

    private static final String TAG = "AppLockApplication";
    private static Context mContext;
    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(AppLockUtils.TAG_LOG, TAG + " onCreate...");
        mContext = getApplicationContext();
        if(SystemProperties.getBoolean("persist.vendor.sprd.fp.lockapp", false)){
            mContext.startServiceAsUser(new Intent(mContext, AppLockService.class), new UserHandle(
                    UserHandle.USER_OWNER));
            AppLockUtils.clearAllAlreadyUnlockedFlag(mContext);
        }
        if(SystemProperties.getBoolean("persist.vendor.sprd.fp.launchapp", false)){
            mContext.startServiceAsUser(new Intent(mContext, LaunchAppService.class), new UserHandle(
                    UserHandle.USER_OWNER));
        }
    }

}
