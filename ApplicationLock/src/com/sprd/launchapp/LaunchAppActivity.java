package com.sprd.launchapp;

import java.util.ArrayList;
import java.util.List;
import java.util.HashMap;

import android.app.ActionBar;
import android.app.FragmentManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.ComponentName;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.hardware.fingerprint.Fingerprint;
import android.hardware.fingerprint.FingerprintManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.preference.EditTextPreference;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceCategory;
import android.preference.PreferenceScreen;
import android.preference.SwitchPreference;
import android.text.InputFilter;
import android.text.TextUtils;
import android.util.Log;
import android.view.MenuItem;
import android.widget.EditText;
import android.widget.Toast;

import com.sprd.applock.R;

public class LaunchAppActivity extends PreferenceActivity implements
        Preference.OnPreferenceChangeListener {

    private final String TAG = "LaunchAppActivity";
    private final int MSG_ENABLE_PREFERENCE_CLICK = 1;
    private final int MSG_ENABLE_APP_LAUNCH_SWITCH_CLICK = 2;
    public static final String KEY_FPINFO = "fingerprint";
    
    private final String PRE_KEY_SWITCH_APP_LAUNCH = "pref_key_enable_app_launch";
    private final String PRE_KEY_APP_LAUNCH_INFO = "pref_key_app_launch_info";
    private final String PRE_KEY_CATEGORY_APP_LAUNCH = "pref_key_category_app_launch";
    
    private final static int DELAY_TIME_PREFERENCE_CLICK = 500;
    private Context mContext;
    private SwitchPreference mAppLaunchSwitch;
    private Preference mAppInfoPreference;
    //private PreferenceCategory appLaunchOptions;

    private boolean mAppInfoPreClickEnable = true;
    private boolean mSwitchPreClickEnable = true;
    private Fingerprint mCurrentFpInfo;
    private int mCurrentFpIndex;
    private ArrayList<LaunchAppinfo> mAppList;
    private RefreshUICaller refreshUICaller;
    private AppLoadModel mAppLoadModel;
    private launchAppDialogFragment mDialogFrgt;

    private ActionBar mActionBar;
    private Handler mHandler = new Handler() {
        public void handleMessage(Message msg) {
            switch (msg.what) {
            case MSG_ENABLE_PREFERENCE_CLICK:
                mAppInfoPreClickEnable = true;
                break;
            case MSG_ENABLE_APP_LAUNCH_SWITCH_CLICK:
                mSwitchPreClickEnable = true;
                break;
            }
        }
    };
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.launchapp_activity_preference);
        mContext = this;
        Intent intent = this.getIntent();
        mCurrentFpInfo = intent.getParcelableExtra(KEY_FPINFO);
        mActionBar = getActionBar();
        if (mActionBar != null) {
            mActionBar.setDisplayHomeAsUpEnabled(true);
            mActionBar.setHomeButtonEnabled(true);
        }
        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_PACKAGE_ADDED);
        filter.addAction(Intent.ACTION_PACKAGE_REMOVED);
        filter.addAction(Intent.ACTION_PACKAGE_CHANGED);
        filter.addAction(Intent.ACTION_PACKAGE_REPLACED);
        filter.addDataScheme("package");
        registerReceiver(mBroadcastReceiver, filter);
        String name = "";
        int fingerId = 0;
        if (mCurrentFpInfo == null) {
            Log.e(TAG, " onCreate failed, fpInfo is null");
            Toast.makeText(mContext, R.string.msg_finger_info_is_error,
                    Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        name = mCurrentFpInfo.getName().toString();
        fingerId = mCurrentFpInfo.getFingerId();
        setTitle(name);
        mCurrentFpIndex = fingerId;
        mAppLoadModel = AppLoadModel.getInstance(mContext);
        mAppLoadModel.loadAllAppsByPM();//update applist when first enter the activity
        mAppLoadModel.updateAppsListTitle();
        mAppList = mAppLoadModel.getAppsList();
        //mAppList = (ArrayList<LaunchAppinfo>)appList.clone();
        Log.d(TAG, "LaunchAppActivity onCreate(), size = " + mAppList.size());
        initView();
        mDialogFrgt = launchAppDialogFragment.getInstance(
                mContext, fingerId, refreshUICaller, mAppList);
    }

    @Override
    protected void onResume() {
        super.onResume();
        mAppInfoPreClickEnable = true;
        mSwitchPreClickEnable = true;
        Log.d(TAG, TAG + " onResume START!");
        mAppLoadModel.updateAppsListTitle();
        mAppList = mAppLoadModel.getAppsList();
        Log.d(TAG, "LaunchAppActivity onResume(), size = " + mAppList.size());
        initSwitchState();
    }
    
    private void initView() {
        mAppLaunchSwitch = (SwitchPreference) findPreference(PRE_KEY_SWITCH_APP_LAUNCH);
        mAppInfoPreference = (Preference) findPreference(PRE_KEY_APP_LAUNCH_INFO);
        //appLaunchOptions = (PreferenceCategory) findPreference(PRE_KEY_CATEGORY_APP_LAUNCH);
        mAppLaunchSwitch.setOnPreferenceChangeListener(this);

        refreshUICaller = new RefreshUICaller();
        refreshUICaller.setRefreshUICaller(new RefreshStateInterface() {
            @Override
            public void refreshState() {
                
            }

            @Override
            public void refreshSelectApp(int index) {
                refreshSelectedApp(index);
            }
        });
    }
    
    private void initSwitchState() {
        boolean functionKeyOn = false;
        functionKeyOn = LaunchAppDataUtil.getAppLaunchSwitchState(mContext,mCurrentFpIndex);
        if(mAppLaunchSwitch != null){
            mAppLaunchSwitch.setChecked(functionKeyOn);
        }
        //refreshAppInfoPreState(state);
        refreshSelectedApp(mCurrentFpIndex);
    }

    @Override
    public boolean onPreferenceTreeClick(PreferenceScreen preferenceScreen,
            Preference preference) {
        Log.i(TAG, " onPreferenceTreeClick preKey: "
                + preference.getKey());
        if (preference == mAppLaunchSwitch) {
            
        } else if (preference == mAppInfoPreference) {
            if (!mAppInfoPreClickEnable) {
                Log.e(TAG," onPreferenceTreeClick mAppInfoPreClickEnable"
                        + " is false, AppInfoPre can't click immediately.");
                return false;
            }
            mAppInfoPreClickEnable = false;
            Message msg = mHandler.obtainMessage();
            msg.what = MSG_ENABLE_PREFERENCE_CLICK;
            mHandler.sendMessageDelayed(msg, DELAY_TIME_PREFERENCE_CLICK);
            showSelectAppListDialog(mCurrentFpIndex);
        } 
        return super.onPreferenceTreeClick(preferenceScreen, preference);
    }
    
    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        String key = preference.getKey();
        Log.i(TAG, " onPreferenceChange preKey: " + key);
        if (PRE_KEY_SWITCH_APP_LAUNCH.equals(key)) {
            if (!mSwitchPreClickEnable) {
                Log.e( TAG, " onPreferenceChange mSwitchPreClickEnable"
                        + " is false, AppLaunchSwitch can't click immediately.");
                return false;
            }
            mSwitchPreClickEnable = false;
            Message msg = mHandler.obtainMessage();
            msg.what = MSG_ENABLE_APP_LAUNCH_SWITCH_CLICK;
            mHandler.sendMessageDelayed(msg, DELAY_TIME_PREFERENCE_CLICK);
            boolean state = (Boolean) newValue;
            boolean isChecked = mAppLaunchSwitch.isChecked();
            Log.i(TAG, " onPreferenceChange switchAppLaunch: "
                    + isChecked + " -- state: " + state);
            String appName = LaunchAppDataUtil.getSelectAppInfo(mContext, mCurrentFpIndex);
            /** SPRD: Modified for bug 619304 @{ */
            String appTitleName = getAppPkgTitleName(appName);
            if (TextUtils.isEmpty(appTitleName)) {
            /** @} */
                Toast.makeText(mContext, R.string.msg_select_app_first,
                        Toast.LENGTH_SHORT).show();
                Log.e(TAG," onPreferenceChange mAppLaunchSwitch clickable"
                        + " is false, not select app.");
                return false;
            }
            LaunchAppDataUtil.setAppLaunchSwitchState(mContext,mCurrentFpIndex, state);
            //refreshAppInfoPreState(state);
        } 
        return true;
    }

    private void refreshAppInfoPreState(boolean state) {
        if (state) {
            mAppInfoPreference.setEnabled(true);
        } else {
            mAppInfoPreference.setEnabled(false);
        }
        refreshSelectedApp(mCurrentFpIndex);
    }

    private void showSelectAppListDialog(int fingerIndex) {
        if(null == mDialogFrgt){
            mDialogFrgt = launchAppDialogFragment.getInstance(
                    mContext, fingerIndex, refreshUICaller, mAppList);
        }

        FragmentManager manager = this.getFragmentManager();
        boolean isAdded = mDialogFrgt.isAdded();
        boolean isVisible = mDialogFrgt.isVisible();
        boolean isRemoving = mDialogFrgt.isRemoving();
        Log.i(TAG, " showSelectAppListDialog isAdded: " + isAdded
                + " -- isVisible: " + isVisible + " -- isRemoving: " + isRemoving);
        // avoid the dialogFrgt added repeat lead to throw exception
        if (!isAdded && !isVisible
                && !isRemoving) {
            mDialogFrgt.show(manager.beginTransaction(), "launchAppDialogFragment");
        }
    }

    private void refreshSelectedApp(int fpInfoIndex) {
        String appName = LaunchAppDataUtil.getSelectAppInfo(mContext, fpInfoIndex);
        /*SPRD: modify to show app's title. @{ */
        /*
        String summary = mContext.getString(R.string.pref_summary_launch_app_name)
                + appName;
        */
        String appTitleName = getAppPkgTitleName(appName);
        Log.d(TAG, " appTitleName ="+appTitleName+";  appName ="+appName);
        if(null == appTitleName || TextUtils.isEmpty(appTitleName)){
            appTitleName = "";
        }
        String summary = mContext.getString(R.string.pref_summary_launch_app_name)
                + appTitleName;
        /* @} */
        if(mAppInfoPreference != null){
            mAppInfoPreference.setSummary(summary);
        }
        /** SPRD: Added for bug 619304 @{ */
        if (TextUtils.isEmpty(appTitleName)) {
            if (null != mAppLaunchSwitch && mAppLaunchSwitch.isChecked()) {
                mAppLaunchSwitch.setChecked(false);
            }
        }
        /** @} */
    }

    private String getAppPkgName(String clsName) {
        if(mAppList == null){
            return null;
        }
        int size = mAppList.size();
        for (int i = 0; i < size; i++) {
            LaunchAppinfo app = mAppList.get(i);
            String className = app.componentName.getClassName();
            String packageName = app.componentName.getPackageName();
            if (className.equals(clsName)) {
                return packageName;
            }
        }
        return null;
    }
    /*SPRD: modify to show app's title. @{ */
    private String getAppPkgTitleName(String clsName) {
        if(mAppList == null){
            return null;
        }
        int size = mAppList.size();
        for (int i = 0; i < size; i++) {
            LaunchAppinfo app = mAppList.get(i);
            String className = app.componentName.getClassName();
            String packageName = app.componentName.getPackageName();
            if (className.equals(clsName)) {
                return app.getTitle();
            }
        }
        return null;
    }
    /* @} */

    @Override
    protected void onDestroy() {
        super.onDestroy();
        Log.d(TAG, " onDestroy execute");
        unregisterReceiver(mBroadcastReceiver);
    }


    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();
        if (id == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    BroadcastReceiver mBroadcastReceiver = new BroadcastReceiver() {
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            Log.d(TAG, " onReceive  action = " + action);
            if (action == null) {
                Log.e(TAG, " onReceive action is null ");
                return;
            }
            String packageName = intent.getData().getSchemeSpecificPart();
            AppLoadModel loadModel = AppLoadModel.getInstance(context);
            ArrayList<LaunchAppinfo> appInfoList = loadModel.getAppsList();
            FingerprintManager fingerprintManager = (FingerprintManager) mContext.getSystemService(
                    Context.FINGERPRINT_SERVICE);
            final List<Fingerprint> items = fingerprintManager.getEnrolledFingerprints();
            if (appInfoList != null
                    && action.equals(Intent.ACTION_PACKAGE_REMOVED)) {
                for (int i = 0; i< appInfoList.size(); i++) {
                    LaunchAppinfo info = appInfoList.get(i);
                    ComponentName componentName = info.componentName;
                    if (componentName.getPackageName().equals(packageName)){

                        //reset the launch application function
                        for (int id = 0; id < items.size(); id++) {
                            int fingerIndex;
                            fingerIndex = items.get(id).getFingerId();
                            if (LaunchAppDataUtil.getSelectAppInfo(mContext, fingerIndex).
                                    equals(componentName.getClassName())){
                                LaunchAppDataUtil.deleteSelectAppInfo(mContext, fingerIndex);
                            }
                        }
                    }
                }
            }
            loadModel.updateAppList(action,packageName);
            LaunchAppListAdapter listAdapter = mDialogFrgt.getListItemAdapter();
            if(listAdapter != null){
                listAdapter.notifyDataSetChanged();
            }

        }
    };
}
