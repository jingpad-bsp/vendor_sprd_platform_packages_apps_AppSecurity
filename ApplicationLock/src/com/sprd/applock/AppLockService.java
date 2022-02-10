package com.sprd.applock;

import android.app.ActivityManager;
import android.app.ActivityManagerNative;
import android.app.KeyguardManager;
import android.app.ActivityManager.RunningAppProcessInfo;
import android.app.IActivityController;
import android.app.IActivityManager;
import android.app.IProcessObserver;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
//import android.content.pm.AppCloneUserInfo;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Handler;
import android.os.IBinder;
import android.os.RemoteException;
import android.os.UserHandle;
import android.os.UserManager;
import android.text.TextUtils;
import android.util.Log;
import android.widget.Toast;

import java.util.List;
import java.util.ArrayList;
import java.util.Iterator;

public class AppLockService extends Service {

    private static final String TAG = "Service";
    private static final String ACTION_REMOVE_ALL_FINGERPRINT = "com.sprd.fp.Settings.DISABLE_FINGERPRINT";
    private Context mContext;
    private String[] mForegroundPkgs;
    private int mCurrentPid = -1;
    private boolean isClone = false;
    private static final byte[] lock = new byte[0];
    private static final int UNMATCH_DIALOG_NORMAL_TIMEOUT = 1500;
    private static final int SHOW_PASSWORD_ACTIVITY_DELAY = 150;
    private final Handler mHandler = new Handler();
    private ActivityManager mActivityManager;
    private KeyguardManager mKeyguardManager;

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(AppLockUtils.TAG_LOG, TAG + "$$$ onCreate...");
        mContext = this.getApplicationContext();
        mActivityManager = (ActivityManager) mContext.getSystemService(Context.ACTIVITY_SERVICE);
        mKeyguardManager = (KeyguardManager) mContext.getSystemService(Context.KEYGUARD_SERVICE);
        IntentFilter filter = new IntentFilter();
        filter.setPriority(IntentFilter.SYSTEM_HIGH_PRIORITY);
        filter.addAction(Intent.ACTION_SCREEN_OFF);
        filter.addAction(Intent.ACTION_SCREEN_ON);
        filter.addAction(Intent.ACTION_USER_PRESENT);
        mContext.registerReceiver(mReceiver, filter);
        IntentFilter packageFilter = new IntentFilter();
        packageFilter.addAction(Intent.ACTION_PACKAGE_ADDED);
        packageFilter.addAction(Intent.ACTION_PACKAGE_CHANGED);
        packageFilter.addAction(Intent.ACTION_PACKAGE_REMOVED);
        packageFilter.addAction(Intent.ACTION_PACKAGE_REPLACED);
        packageFilter.addDataScheme("package");
        mContext.registerReceiver(mReceiver, packageFilter);
        IntentFilter fpRemoveFilter = new IntentFilter();
        fpRemoveFilter.addAction(ACTION_REMOVE_ALL_FINGERPRINT);
        mContext.registerReceiver(mReceiver, fpRemoveFilter);
        try {
            ActivityManagerNative.getDefault().registerProcessObserver(mProcessObserver);
        } catch (RemoteException e) {
            Log.e(AppLockUtils.TAG_LOG, TAG + "$$$ RemoteException... e:" + e);
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(AppLockUtils.TAG_LOG, TAG + "$$$ onStartCommand...");
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.d(AppLockUtils.TAG_LOG, TAG + "$$$ onDestroy...");
        if(mContext != null && mReceiver != null){
            mContext.unregisterReceiver(mReceiver);
        }
    }

    @Override
    public boolean onUnbind(Intent intent) {
        Log.d(AppLockUtils.TAG_LOG, TAG + "$$$ onUnbind...");
        return super.onUnbind(intent);
    }

    @Override
    public IBinder onBind(Intent arg0) {
        Log.d(AppLockUtils.TAG_LOG, TAG + "$$$ onBind...");
        return null;
    }

    private BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (AppLockUtils.DEBUG) {
                Log.d(AppLockUtils.TAG_LOG, TAG + "$$ onReceive action: " + action);
            }
            if (action == null) {
                Log.e(AppLockUtils.TAG_LOG, TAG + " onReceive action is null ");
                return;
            }
            if (Intent.ACTION_SCREEN_OFF.equals(action)) {
                AppLockUtils.clearAllAlreadyUnlockedFlag(context);
                String fingerLaunchApp = System.getProperty("finger.launch.app");
                if (!TextUtils.isEmpty(fingerLaunchApp)){
                    AppLockUtils.setAppAlreadyUnlockedFlag(context,fingerLaunchApp);
                }
                System.clearProperty("finger.launch.app");
            } else if (action.equals(Intent.ACTION_USER_PRESENT)) {
                String runningTopPackage = AppLockUtils.getTopActivityPackage(mContext);
                System.clearProperty("finger.launch.app");
                if (!TextUtils.isEmpty(runningTopPackage)
                        && AppLockUtils.isAppNeedLock(context, runningTopPackage)) {
                    showLockActivity(runningTopPackage);
                }
            } else if (action.equals(Intent.ACTION_SCREEN_ON)) {
                String runningTopPackage = AppLockUtils.getTopActivityPackage(mContext);
                System.clearProperty("finger.launch.app");
                if (!TextUtils.isEmpty(runningTopPackage)
                        && AppLockUtils.isAppNeedLock(context, runningTopPackage)) {
                    showLockActivity(runningTopPackage);
                }
            } else if (action.equals(Intent.ACTION_PACKAGE_ADDED)
                    || action.equals(Intent.ACTION_PACKAGE_CHANGED)
                    || action.equals(Intent.ACTION_PACKAGE_REMOVED)
                    || action.equals(Intent.ACTION_PACKAGE_REPLACED)) {
                UserManager userManager = (UserManager) context
                        .getSystemService(Context.USER_SERVICE);
                int uid = intent.getIntExtra(Intent.EXTRA_UID, -1);
                UserHandle userHandle = UserHandle.getUserHandleForUid(uid);
                //AppCloneUserInfo appCloneUserInfo = new AppCloneUserInfo(
                //        userManager.getUserInfo(userHandle.getIdentifier()));
                boolean cloneApp = false;//appCloneUserInfo.isAppClone();
                boolean replace = intent.getBooleanExtra(Intent.EXTRA_REPLACING, false);
                if (replace || cloneApp) {
                    Log.d(AppLockUtils.TAG_LOG, TAG + " do nothing...");
                    return;
                }
                String packageName = intent.getData().getSchemeSpecificPart();
                if (TextUtils.isEmpty(packageName)) {
                    return;
                }
                Log.d(AppLockUtils.TAG_LOG, TAG + " ACTION_PACKAGE_...packageName:" + packageName);
                if (packageName.startsWith("com.android.stk")) {
                    Log.d(AppLockUtils.TAG_LOG, TAG + " ignore the package...");
                    return;
                }
                if (action.equals(Intent.ACTION_PACKAGE_CHANGED)
                        && AppLockUtils.isSystemApp(context, packageName)) {
                    Log.d(AppLockUtils.TAG_LOG, TAG + " package is changed and is SystemApp...");
                    return;
                }
                ApplicationInfo info = new ApplicationInfo();
                info.packageName = packageName;
                AppLockUtils.setAPPLockedFlag(context, info, false);
            } else if (action.equals(ACTION_REMOVE_ALL_FINGERPRINT)) {
                AppLockUtils.clearAllLockedFlag(context);
            }

        }

    };

    private ArrayList<String> getAppNameByPID(Context context, int PID) {
        ArrayList<String> list = new ArrayList<String>();
        List<RunningAppProcessInfo> l = mActivityManager.getRunningAppProcesses();
        Iterator<RunningAppProcessInfo> it = l.iterator();
        while (it.hasNext()) {
            RunningAppProcessInfo info = (RunningAppProcessInfo) (it.next());
            try {
                if (info.pid == PID) {
                    Log.d(AppLockUtils.TAG_LOG, TAG + " getAppNameByPID, PID = " + PID
                            + ", processName = " + info.processName);
                    list.add(info.processName);
                }
            } catch (Exception e) {
                Log.e(AppLockUtils.TAG_LOG, TAG + " getAppNameByPID occurs exception, ", e);
            }
        }
        return list;
    }

    private boolean showLockActivity(String packageName) {
        boolean result = false;
        if (packageName == null || AppLockUtils.isAppNeedtoFilter(packageName)
                && !AppLockUtils.PACKAGE_DIALER.equals(packageName)) {
            Log.d(AppLockUtils.TAG_LOG, TAG + " Don't show LockActivity...");
            return false;
        } else {
            if (mKeyguardManager != null && mKeyguardManager.inKeyguardRestrictedInputMode()) {
                Log.d(AppLockUtils.TAG_LOG, TAG + " Don't show LockActivity, keyguard is showing,"
                        + " The App [" + packageName + "] not need to lock!");
                return false;
            }
        }

        if (isAppNeedLock(packageName)) {
            Intent intent = new Intent(AppLockService.this, AppUnlockActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            intent.putExtra("package_name", packageName);
            intent.putExtra("is_cloned", isClone);
            int userId = UserHandle.myUserId();
            Log.d(AppLockUtils.TAG_LOG, TAG + " showLockActivity" + ", packageName: " + packageName);
            startActivityAsUser(intent, new UserHandle(userId));
            result = true;
        }
        return result;
    }

    private boolean isAppNeedLock(String pkg) {
        boolean needLock = AppLockUtils.isAppNeedLock(this, pkg);
        boolean alreadyUnlocked = AppLockUtils.isAppAlreadyUnlocked(this, pkg);
        Log.d(AppLockUtils.TAG_LOG, TAG + " isAppNeedLock pkg = " + pkg + " needLock = "
                + needLock + " alreadyUnlock = " + alreadyUnlocked);
        return needLock && !alreadyUnlocked;
    }

    private Runnable mShowPwdRunnable = new Runnable() {
        @Override
        public void run() {
            synchronized (lock) {
                if (mForegroundPkgs == null) {
                    return;
                }
                int len = mForegroundPkgs.length;
                Log.d(AppLockUtils.TAG_LOG, TAG + " mForegroundPkgs.length = " + len);
                if (len > 0) {
                    ArrayList<String> list = getAppNameByPID(mContext, mCurrentPid);
                    for (String topPackageName : list) {
                        Log.d(AppLockUtils.TAG_LOG, TAG + " ForegroundPackageName: "
                                + topPackageName);
                        boolean result = showLockActivity(topPackageName);
                        if (result) {
                            break;
                        }
                    }
                }
            }
        }
    };

    private Runnable mShowPwdDelayRunnable = new Runnable() {
        public void run() {
            synchronized (lock) {
                String runningTopPackage = AppLockUtils.getTopActivityPackage(mContext);
                Log.d(AppLockUtils.TAG_LOG, TAG + " mShowPwdDelayRunnable runningTopPackage = "
                        + runningTopPackage);
                showLockActivity(runningTopPackage);
            }
        }
    };

    final IProcessObserver mProcessObserver = new IProcessObserver.Stub() {
        @Override
        public void onForegroundActivitiesChanged(int pid, int uid, boolean foregroundActivities) {
            synchronized (lock) {
                if (AppLockUtils.DEBUG) {
                    Log.d(AppLockUtils.TAG_LOG, TAG + " onForegroundActivitiesChanged pid = " + pid
                            + " --uid: " + uid + " isForegroundAct: " + foregroundActivities);
                }
                if (foregroundActivities) {
                    PackageManager pm = getPackageManager();
                    mForegroundPkgs = pm.getPackagesForUid(uid);
                    mCurrentPid = pid;
                    //isClone = AppCloneUserInfo.isAppCloneUid(uid);
                    mHandler.removeCallbacks(mShowPwdDelayRunnable);
                    mHandler.removeCallbacks(mShowPwdRunnable);
                    mHandler.postDelayed(mShowPwdRunnable, SHOW_PASSWORD_ACTIVITY_DELAY);
                } else {
                    mHandler.removeCallbacks(mShowPwdRunnable);
                    mHandler.removeCallbacks(mShowPwdDelayRunnable);
                    mHandler.postDelayed(mShowPwdDelayRunnable, SHOW_PASSWORD_ACTIVITY_DELAY);
                }
            }
        }



        @Override
        public void onProcessDied(int pid, int uid) {
            if (AppLockUtils.DEBUG) {
                Log.d(AppLockUtils.TAG_LOG, TAG + " onProcessDied pid = " + pid + " --uid: " + uid);
            }
        }

    };
}
