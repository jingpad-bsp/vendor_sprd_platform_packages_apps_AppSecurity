package com.sprd.launchapp;

import com.sprd.applock.R;

import android.annotation.SuppressLint;
import android.app.ActivityManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;

@SuppressLint("NewApi")
public class LaunchAppinfo {

    private final Context mContext;
    private final PackageManager mPackageManager;

    public ComponentName componentName;
    private Intent intent;
    private String title;
    private Bitmap icon;
    private Drawable mAppDrawable;
    private int mIconDpi;

    //private int mIconWidth = 0;
    //private int mIconHeight = 0;

    private ResolveInfo mInfo;
    private String mPackageName;

    // add by wells,recycle the icons
    public void recycleBitmaps() {
        if (icon != null && !icon.isRecycled()) {
            icon.recycle();
            icon = null;
        }
        if (mAppDrawable != null) {
            mAppDrawable = null;
        }
    }
    // add by wells end
    @SuppressLint("NewApi")
    public LaunchAppinfo(Context context, ResolveInfo info) {
        this(context, info, false);
    }

    @SuppressLint("NewApi")
    public LaunchAppinfo(Context context, ResolveInfo info, boolean create) {
        ActivityManager activityManager = (ActivityManager) context
                .getSystemService(Context.ACTIVITY_SERVICE);

        mIconDpi = activityManager.getLauncherLargeIconDensity();

        this.mContext = context;
        mPackageManager = context.getPackageManager();
        final String packageName = info.activityInfo.applicationInfo.packageName;
        mPackageName = packageName;
        this.componentName = new ComponentName(packageName,
                info.activityInfo.name);
        this.setActivity(componentName, Intent.FLAG_ACTIVITY_NEW_TASK
                | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);

        mInfo = info;
        title = info.loadLabel(mPackageManager).toString();
        //mIconHeight = context.getResources().getDimensionPixelSize(
        //        R.dimen.applaunch_cell_image_height);
        //mIconWidth = context.getResources().getDimensionPixelSize(
        //        R.dimen.applaunch_cell_image_width);
        mInfo = info;
        if (create) {
            createShowIcon();
        }
    }

    public void createShowIcon(){
        mAppDrawable = getFullResIcon(mInfo);
        icon = makeBitmapIcon();
    }

    private Bitmap makeBitmapIcon() {
        Bitmap b = Bitmap.createBitmap(
                Math.max(mAppDrawable.getIntrinsicWidth(), 1),
                Math.max(mAppDrawable.getIntrinsicHeight(), 1),
                Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        mAppDrawable.setBounds(0, 0, b.getWidth(), b.getHeight());
        mAppDrawable.setAlpha(255);
        mAppDrawable.draw(c);
        c.setBitmap(null);
        return b;
    }

    final void setActivity(ComponentName className, int launchFlags) {
        intent = new Intent(Intent.ACTION_MAIN);
        intent.addCategory(Intent.CATEGORY_LAUNCHER);
        intent.setComponent(className);
        intent.setFlags(launchFlags);
    }

    public Drawable getFullResIcon(ResolveInfo info) {
        return getFullResIcon(info.activityInfo);
    }

    public Drawable getFullResIcon(ActivityInfo info) {
        Resources resources;
        try {
            resources = mPackageManager
                    .getResourcesForApplication(info.applicationInfo);
        } catch (PackageManager.NameNotFoundException e) {
            resources = null;
        }
        if (resources != null) {
            int iconId = info.getIconResource();
            if (iconId != 0) {
                return getFullResIcon(resources, iconId);
            }
        }
        return getFullResDefaultActivityIcon();
    }

    public Drawable getFullResIcon(Resources resources, int iconId) {
        Drawable d;
        try {
            d = resources.getDrawableForDensity(iconId, mIconDpi);
        } catch (Resources.NotFoundException e) {
            d = null;
        }
        return (d != null) ? d : getFullResDefaultActivityIcon();
    }

    public Drawable getFullResDefaultActivityIcon() {
        return getFullResIcon(Resources.getSystem(),
                android.R.mipmap.sym_def_app_icon);
    }

    public String getTitle() {
        return title;
    }
    public String getPackageName() {
        return mPackageName;
    }

    public void updateTitle(){
        title = mInfo.loadLabel(mPackageManager).toString();
    }

    public Bitmap getAppIcon() {
        return icon;
    }

}
