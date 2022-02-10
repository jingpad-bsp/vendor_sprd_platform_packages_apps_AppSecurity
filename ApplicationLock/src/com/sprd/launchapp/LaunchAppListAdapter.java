package com.sprd.launchapp;

import java.util.ArrayList;

import android.content.Context;
import android.graphics.Bitmap;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.CheckedTextView;
import android.widget.ImageView;

import com.sprd.applock.R;

public class LaunchAppListAdapter extends BaseAdapter {

    private final static String TAG = "LaunchAppListAdapter";
    private Context mContext;
    private LayoutInflater mInflater;
    private ArrayList<LaunchAppinfo> mApps;
    private String preAppClassName;

    public LaunchAppListAdapter(Context context) {
        mContext = context;
        mInflater = LayoutInflater.from(mContext);
    }

    @Override
    public int getCount() {
        return mApps.size();
    }

    @Override
    public Object getItem(int pos) {
        return mApps.get(pos);
    }

    @Override
    public long getItemId(int pos) {
        return pos;
    }

    @Override
    public View getView(int pos, View convertView, ViewGroup parent) {
        ViewHolder holder = null;
        if (convertView == null) {
            holder = new ViewHolder();
            convertView = mInflater.inflate(R.layout.launchapp_list_item_app, null);
            holder.iv = (ImageView) convertView.findViewById(R.id.imageView);
            holder.checkedTv = (CheckedTextView) convertView.findViewById(R.id.checkedTextView);
            convertView.setTag(holder);
        } else {
            holder = (ViewHolder) convertView.getTag();
        }
        if (mApps == null) {
            Log.e(TAG, "LaunchAppListAdapter getView applist is null");
            return null;
        }
        synchronized(AppLoadModel.class){
            if(pos >= mApps.size()){
                holder.checkedTv.setVisibility(View.GONE);
                holder.iv.setVisibility(View.GONE);
                return convertView;
            }else{
                holder.checkedTv.setVisibility(View.VISIBLE);
                holder.iv.setVisibility(View.VISIBLE);
            }
            LaunchAppinfo app = mApps.get(pos);
            String name = app.getTitle();
            Bitmap drawable = app.getAppIcon();
            Log.d(TAG, "LaunchAppListAdapter getView app_name = "
                    + name + " -- position: " + pos);
            if (!TextUtils.isEmpty(preAppClassName)
                    && preAppClassName.equals(app.componentName.getClassName())) {
                holder.checkedTv.setChecked(true);
            } else {
                holder.checkedTv.setChecked(false);
            }
            holder.checkedTv.setText(name);
            holder.iv.setImageBitmap(drawable);
        }
        return convertView;
    }

    private static class ViewHolder {
        ImageView iv;
        CheckedTextView checkedTv;
        //LaunchAdapterOnClickListener listener;
    }

    public void getFpInfoIndex(int index) {
        synchronized(AppLoadModel.class){
            preAppClassName = LaunchAppDataUtil.getSelectAppInfo(mContext, index);
        }
    }

    public void setAppsList(ArrayList<LaunchAppinfo> apps) {
        /*SPRD: copy the LaunchAppinfo to mAppList. @{ */
        mApps = apps;
        //mApps = (ArrayList<LaunchAppinfo>)apps.clone();
        if(mApps != null){
            Log.d(TAG, "setAppsList() mApps.size"+mApps.size());
        }
        /* @} */
    }

    private class  LaunchAdapterOnClickListener implements OnClickListener{
        
        @Override
        public void onClick(View v) {
            ((CheckedTextView)v.findViewById(R.id.checkedTextView)).setChecked(true);
        }
    };
    
}
