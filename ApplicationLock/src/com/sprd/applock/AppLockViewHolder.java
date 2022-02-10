package com.sprd.applock;

import android.content.Context;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Switch;
import android.widget.ImageView;
import android.widget.TextView;

public class AppLockViewHolder {
    public ApplicationsState.AppEntry entry;
    public View rootView;
    public TextView appName;
    public ImageView appIcon;
    public Switch checkBox;

    static public AppLockViewHolder createOrRecycle(LayoutInflater inflater, View convertView) {
        if (convertView == null) {
            convertView = inflater.inflate(R.layout.applock_list_item, null);
            AppLockViewHolder holder = new AppLockViewHolder();
            holder.rootView = convertView;
            holder.appName = (TextView) convertView.findViewById(R.id.app_name);
            holder.appIcon = (ImageView) convertView.findViewById(R.id.app_icon);
            holder.checkBox = (Switch) convertView.findViewById(R.id.select_checkbox);
            convertView.setTag(holder);
            return holder;
        } else {
            return (AppLockViewHolder) convertView.getTag();
        }
    }
}

