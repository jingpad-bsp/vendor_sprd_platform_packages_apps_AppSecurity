package com.sprd.launchapp;

import java.util.ArrayList;
import java.util.List;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.AsyncTask;
import android.util.Log;
import android.content.Intent;

public class AppLoadModel {

    private static final String  TAG = "AppLoadModel";
    private static AppLoadModel mAppLoadModel;
    private Context mContext;
    private ArrayList<LaunchAppinfo> mApplist;

    private AppLoadModel(Context context) {
        mContext = context;
        mApplist = new ArrayList<LaunchAppinfo>();
        recycleAppIconList();
        loadAllAppsByPM();
    }

    public static synchronized AppLoadModel getInstance(Context context) {
        if (mAppLoadModel == null) {
            mAppLoadModel = new AppLoadModel(context);
        }
        return mAppLoadModel;
    }

    public void loadAllAppsByPM() {
        synchronized(AppLoadModel.class){
            mApplist.clear();
            int N = Integer.MAX_VALUE;
            PackageManager packageManager = mContext.getPackageManager();
            Intent mainIntent = new Intent(Intent.ACTION_MAIN, null);
            mainIntent.addCategory(Intent.CATEGORY_LAUNCHER);
            List<ResolveInfo> apps = null;
            apps = packageManager.queryIntentActivities(mainIntent, 0);
            N = apps.size();

            for (int i = 0; i < N; i++) {
                ResolveInfo ri = apps.get(i);
                String packageName = ri.activityInfo.applicationInfo.packageName;
                //String taskAffinity = ri.activityInfo.taskAffinity;
                if (!LaunchAppDataUtil.PACKAGE_SETTINGS.equals(packageName)
                        && !LaunchAppDataUtil.PACKAGE_DESKCLOCK.equals(packageName)
                        && !LaunchAppDataUtil.PACKAGE_DOWNLOAD.equals(packageName)) {
                    //to ensure the icon in first page can display
                    if (i < LaunchAppDataUtil.DEFAULT_VISIBLE_ITEM) {
                        mApplist.add(new LaunchAppinfo(mContext, apps.get(i), true));
                    } else {
                        mApplist.add(new LaunchAppinfo(mContext, apps.get(i)));
                    }
                }
            }
        }
    }
    public ArrayList<LaunchAppinfo> getAppsList() {
            return mApplist;
    }
    
    public void recycleAppIconList() {
        Log.i(TAG, " recycleAppIconList app icon is released");
        synchronized(AppLoadModel.class){
            if (mApplist != null && mApplist.size() > 0) {
                int size = mApplist.size();
                for (int i = 0; i < size; i++) {
                        mApplist.get(i).recycleBitmaps();
                }
            }
        }
    }

    public void updateAppsListTitle(){
        Log.i(TAG, " updateAppsListTitle");
        synchronized(AppLoadModel.class){
            if (mApplist != null && mApplist.size() > 0) {
                int size = mApplist.size();
                for (int i = 0; i < size; i++) {
                    mApplist.get(i).updateTitle();
                }
            }
        }
    }
    /*SPRD: Only update the list ,not clear and then add. @{ */
    public void updateAppList(String intentaction, String packagename) {
        Log.d( TAG, " updateAppList() intentaction ="+intentaction+"; packagename ="+packagename);
        if (LaunchAppDataUtil.PACKAGE_SETTINGS.equals(packagename)
                || LaunchAppDataUtil.PACKAGE_DESKCLOCK.equals(packagename)
                || LaunchAppDataUtil.PACKAGE_DOWNLOAD.equals(packagename)) {
            return ;
        }
        synchronized(AppLoadModel.class){
            if(intentaction.equals(Intent.ACTION_PACKAGE_ADDED)
                    || intentaction.equals(Intent.ACTION_PACKAGE_CHANGED)
                    || intentaction.equals(Intent.ACTION_PACKAGE_REPLACED)){
                removeAppInfo(packagename);
                addAppInfo(packagename);
            }else if(intentaction.equals(Intent.ACTION_PACKAGE_REMOVED)){
                removeAppInfo(packagename);
            }
        }
    }
    private void addAppInfo(String packagename){
        Log.d(TAG, " addAppInfo() packagename = "+packagename);
        int N = Integer.MAX_VALUE;
        PackageManager packageManager = mContext.getPackageManager();
        Intent mainIntent = new Intent(Intent.ACTION_MAIN, null);
        mainIntent.addCategory(Intent.CATEGORY_LAUNCHER);
        List<ResolveInfo> apps = null;
        apps = packageManager.queryIntentActivities(mainIntent, 0);
        if(apps == null){
            return;
        }
        N = apps.size();
        Log.d( TAG, " addAppInfo() N = "+N);
        for (int i = 0; i < N; i++) {
            ResolveInfo ri = apps.get(i);
            String pn = ri.activityInfo.applicationInfo.packageName;
            if(pn.equals(packagename)){
                if (i < LaunchAppDataUtil.DEFAULT_VISIBLE_ITEM) {
                    mApplist.add(new LaunchAppinfo(mContext, apps.get(i), true));
               } else {
                    mApplist.add(new LaunchAppinfo(mContext, apps.get(i)));
                }
                break;
            }
        }
    }
    private void removeAppInfo(String packagename){
        Log.d( TAG, " removeAppInfo() packagename = "+packagename);
        if (mApplist != null && mApplist.size() > 0) {
            int size = mApplist.size();
            for (int i = 0; i < size; i++) {
                String title = mApplist.get(i).getTitle();
                String pName = mApplist.get(i).getPackageName();
                if(pName.equals(packagename)){
                    mApplist.get(i).recycleBitmaps();
                    mApplist.remove(i);
                    break;
                }
            }
        }
    }
    /* @} */
}

