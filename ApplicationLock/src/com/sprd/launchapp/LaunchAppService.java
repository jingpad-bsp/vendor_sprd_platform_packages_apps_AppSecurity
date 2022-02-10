package com.sprd.launchapp;

import java.util.ArrayList;
import java.util.List;

import android.app.ActivityManager;
import android.app.ActivityManager.RunningTaskInfo;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.fingerprint.Fingerprint;
import android.hardware.fingerprint.FingerprintManager;
import android.os.Bundle;
import android.os.IBinder;
import android.os.SystemProperties;
import android.os.UserHandle;
import android.text.TextUtils;
import android.util.Log;
import android.net.Uri;

import com.sprd.applock.AppLockUtils;
import com.sprd.applock.R;

public class LaunchAppService extends Service {

    private static final String TAG = "LaunchAppService";
    private final static String KEY_FINGERPRINT = "fingerprint";
    private final static String LAUNCH_FROM = "launch_from";
    private final static String ACTION_KEYGUARD_FP_AUTHENTICATE_SUCCESS = "com.sprd.intent.action.keyguard_fp_authenticate_success";
    private final int MSG_LAUNCH_APP = 1;
    private Context mContext;

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "$$$ onCreate...");
        mContext = this.getApplicationContext();

        IntentFilter fpAuthFilter = new IntentFilter();
        fpAuthFilter.addAction(ACTION_KEYGUARD_FP_AUTHENTICATE_SUCCESS);
        mContext.registerReceiver(mReceiver, fpAuthFilter);

        IntentFilter packageIntentFilter = new IntentFilter();
        packageIntentFilter.addAction(Intent.ACTION_PACKAGE_REMOVED);
        packageIntentFilter.addDataScheme("package");
        mContext.registerReceiver(mReceiver, packageIntentFilter);
        
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "$$$ onStartCommand...");
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        Log.d(TAG, "$$$ onDestroy...");
        super.onDestroy();
        if(mContext != null && mReceiver != null){
            mContext.unregisterReceiver(mReceiver);
        }
    }

    @Override
    public boolean onUnbind(Intent intent) {
        Log.d(TAG, "$$$ onUnbind...");
        return super.onUnbind(intent);
    }

    @Override
    public IBinder onBind(Intent arg0) {
        Log.d(TAG, "$$$ onBind...");
        return null;
    }

    private BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            Log.d(TAG, "$$ onReceive action: " + action);
            if (action == null) {
                Log.e(TAG, " onReceive action is null ");
                return;
            }
            if(!SystemProperties.getBoolean("persist.vendor.sprd.fp.launchapp", false)){
                Log.d(TAG, " onReceive  fp launchapp feature is off ");
                return;
            }
            int userId = UserHandle.myUserId();
            boolean mIsOwner = (userId == UserHandle.USER_OWNER);
            if (!mIsOwner) {
                Log.d(TAG, " onReceive  fp launchapp not in OWNER user... ");
                return;
            }
            if (action.equals(Intent.ACTION_BOOT_COMPLETED)) {
                //LaunchAppDataUtil.init(context);
            }else if(action.equals(ACTION_KEYGUARD_FP_AUTHENTICATE_SUCCESS)){
                Fingerprint fpInfo = intent.getParcelableExtra(KEY_FINGERPRINT);
                int fingerId = 0;
                if (fpInfo == null) {
                    Log.e(TAG, " onCreate failed, fpInfo is null");
                    return;
                }
                fingerId = fpInfo.getFingerId();
                boolean launchAppSwitch = LaunchAppDataUtil.getAppLaunchSwitchState(context,fingerId);
                Log.d(TAG, " onReceive  fingerId = "+fingerId + " launchAppSwitch = "+launchAppSwitch);
                if(launchAppSwitch){
                    String appClassName = LaunchAppDataUtil.getSelectAppInfo(context, fingerId);
                    if (TextUtils.isEmpty(appClassName)) {
                        Log.e(TAG, " launchApp failed, app classInfo is null");
                        return;
                    }
                    AppLoadModel mfp = AppLoadModel.getInstance(context);
                    ArrayList<LaunchAppinfo> appList = mfp.getAppsList();
                    if (appList != null) {
                        int size = appList.size();
                        for (int i=0; i< size; i++) {
                            LaunchAppinfo appInfo = appList.get(i);
                            String className = appInfo.componentName.getClassName();
                            String packageName = appInfo.componentName.getPackageName();
                            if (appClassName.equals(className)) {
                                Log.i(TAG, " begin to launchApp! packageName: "
                                        + packageName);
                                AppLockUtils.setAppAlreadyUnlockedFlag(context,packageName);
                                System.setProperty("finger.launch.app", packageName);
                                Log.e(TAG,  " LaunchAppService   finger.launch.app = "+System.getProperty("finger.launch.app"));
                                String topPackage = getTopActivityPackage();
                                if (topPackage.equals(packageName)) {
                                    Log.i(TAG, " The app has already launched");
                                    return ;
                                }
                                // start app
                                Intent launchIntent = new Intent(Intent.ACTION_MAIN);
                                launchIntent.addCategory(Intent.CATEGORY_LAUNCHER);
                                launchIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
                                //launchIntent.setPackage(packageName);
                                launchIntent.setClassName(packageName, className);
                                launchIntent.putExtra(LAUNCH_FROM,"launchFromSprdFingerprint");
                                context.startActivityAsUser(launchIntent, UserHandle.CURRENT_OR_SELF);
                            }
                        }
                    } else {
                        Log.e(TAG, " launchApp failed, appList is null");
                    }
                }
            } else if (action.equals(Intent.ACTION_PACKAGE_REMOVED)) {
                String packageName = intent.getData().getSchemeSpecificPart();
                FingerprintManager fingerprintManager = (FingerprintManager) mContext.getSystemService(
                        Context.FINGERPRINT_SERVICE);
                final List<Fingerprint> items = fingerprintManager.getEnrolledFingerprints();
                //reset the launch application function
                for (int id = 0; id < items.size(); id++) {
                    int fingerIndex;
                    fingerIndex = items.get(id).getFingerId();
                    if (LaunchAppDataUtil.getSelectAppInfo(mContext, fingerIndex).
                            startsWith(packageName)){
                        LaunchAppDataUtil.deleteSelectAppInfo(mContext, fingerIndex);
                    }
                }
            } 

        }

    };

    private  String getTopActivityPackage() {
        String runningTopPackage = "";
        ActivityManager am = (ActivityManager) mContext
                .getSystemService(Context.ACTIVITY_SERVICE);
        List<RunningTaskInfo> runningTaskList = am.getRunningTasks(1);
        if (runningTaskList != null) {
            RunningTaskInfo runningTaskInfo = runningTaskList.get(0);
            if (runningTaskInfo != null) {
                runningTopPackage = runningTaskInfo.topActivity
                        .getPackageName();
            }
        }
        return runningTopPackage;
    }

}
