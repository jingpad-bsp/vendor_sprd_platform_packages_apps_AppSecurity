package com.sprd.launchapp;

import java.util.ArrayList;

import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.AbsListView;
import android.widget.AdapterView;
import android.widget.AbsListView.OnScrollListener;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.CheckedTextView;
import android.widget.ListView;

import com.sprd.applock.R;
import android.graphics.Bitmap;

import com.sprd.launchapp.RefreshUICaller;
import com.sprd.launchapp.LaunchAppinfo;
import com.sprd.launchapp.LaunchAppListAdapter;
import com.sprd.launchapp.AppLoadModel;
import com.sprd.launchapp.LaunchAppDataUtil;

public class launchAppDialogFragment extends DialogFragment
        implements DialogInterface.OnClickListener, ListView.OnScrollListener {

    private final static String TAG = "launchAppDialogFragment";
    private static Context mContext;
    private AppListItemOnItemClick itemClickListener;
    private static int fpInfoIndex;
    private static RefreshUICaller mListener;
    private static launchAppDialogFragment mDialogFgt;
    private static ArrayList<LaunchAppinfo> mApps;
    private static LaunchAppListAdapter listItemAdapter;
    private ListView mListView;

    /*
    private launchAppDialogFragment(Context context) {
        mContext = context;
        listItemAdapter = new LaunchAppListAdapter(mContext);
        itemClickListener = new AppListItemOnItemClick();
    }*/
    public static synchronized launchAppDialogFragment getInstance(Context context,
            int pos, RefreshUICaller refreshUIState, ArrayList<LaunchAppinfo> appList) {
        mContext = context;
        fpInfoIndex = pos;
        mListener = refreshUIState;
        if (mDialogFgt == null) {
            mDialogFgt = new launchAppDialogFragment();
        }
        synchronized(AppLoadModel.class){
            mApps = appList;
        }
        Log.d("launchAppDialogFragment", "launchAppDialogFragment getInstance(), size = " + mApps.size());
        return mDialogFgt;
    }
    
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        Log.i(TAG, "launchAppDialogFragment onCreateDialog() " );
        listItemAdapter = new LaunchAppListAdapter(mContext);
        itemClickListener = new AppListItemOnItemClick();
        listItemAdapter.getFpInfoIndex(fpInfoIndex);
        listItemAdapter.setAppsList(mApps);
        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
        LayoutInflater inflater = LayoutInflater.from(getActivity());
        View v = inflater.inflate(R.layout.launchapp_fragment_dialog, null);
        mListView = (ListView) v.findViewById(R.id.listViewApp);
        mListView.setAdapter(listItemAdapter);
        mListView.setOnItemClickListener(itemClickListener);
        mListView.setOnScrollListener(this);
        builder.setTitle(R.string.select_application_launchapp);
        builder.setView(v);
        //builder.setSingleChoiceItems(listItemAdapter, 0, this);
        //builder.setPositiveButton("OK", this);
        builder.setNegativeButton(android.R.string.cancel, this);
        Dialog dialog = builder.create();
        dialog.setCanceledOnTouchOutside(false);
        return dialog;
    }
    /*SPRD: Add OnResume function ot reload applist. @{ */
    public void onResume(){
        Log.d(TAG, "launchAppDialogFragment onResume() " );
        AppLoadModel mLoadModel = AppLoadModel.getInstance(mContext);
        mLoadModel.updateAppsListTitle();
        //ArrayList<LaunchAppinfo> appList = mfp.getAppsList();
        mApps = mLoadModel.getAppsList();
        synchronized(AppLoadModel.class){
            Log.d(TAG, "launchAppDialogFragment onResume() mApps.size = "+mApps.size());
            for(int i=0; i<mApps.size(); i++){
                LaunchAppinfo app = mApps.get(i);
                String name = app.getTitle();
                Bitmap drawable = app.getAppIcon();
                Log.d(TAG, "launchAppDialogFragment onResume() app_name = "+ name + " -- i: " + i);

            }
            listItemAdapter.getFpInfoIndex(fpInfoIndex);
            listItemAdapter.setAppsList(mApps);
            listItemAdapter.notifyDataSetChanged();
        }
        super.onResume();
    }
    /* @} */

    @Override
    public void onClick(DialogInterface dialog, int which) {
        Log.i(TAG, "launchAppDialogFragment onClick item which: " + which);
        if (which == AlertDialog.BUTTON_NEGATIVE) {
            dismissAllowingStateLoss();
        } else if (which == AlertDialog.BUTTON_POSITIVE) {
            
        } else {

        }
    }

    public LaunchAppListAdapter getListItemAdapter() {
        return listItemAdapter;
    }

    private void saveSelectLaunchAppInfo(int pos) {
        ComponentName componentName = mApps.get(pos).componentName;
        String className = componentName.getClassName();
        Log.i(TAG, "launchAppDialogFragment saveAppInfo className: " + className);
        LaunchAppDataUtil.setSelectAppInfo(mContext, fpInfoIndex, className);
        mListener.refreshSelectApp(fpInfoIndex);
    }
    
    class AppListItemOnItemClick implements OnItemClickListener {
        @Override
        public void onItemClick(AdapterView<?> list, View v, int pos, long id) {
            Log.i(TAG, "launchAppDialogFragment OnItemClickListener item position: " + pos);
            ((CheckedTextView)v.findViewById(R.id.checkedTextView)).setChecked(true);
            saveSelectLaunchAppInfo(pos);
            dismissAllowingStateLoss();
        }
    }

    @Override
    public void onScrollStateChanged(AbsListView view, int scrollState) {
        Log.e(TAG, "venus onScrollStateChanged, scrollState = " + scrollState);
        switch(scrollState){
        case OnScrollListener.SCROLL_STATE_IDLE:
            reloadBitmap(mListView.getFirstVisiblePosition(),
                    mListView.getChildCount(),
                    mListView.getCount());
            break;
        default:
            break;
        }
    }

    private void reloadBitmap(int firstVisibleItem, int visibleItemCount, int totalItemCount){
        Log.e(TAG, "onScrollStateChanged, firstVisibleItem = " + firstVisibleItem + " visibleItemCount = "
             + visibleItemCount + " totalItemCount = " + totalItemCount);
        synchronized(AppLoadModel.class){
            for (int position = firstVisibleItem; position < firstVisibleItem + visibleItemCount; position++) {
                if (mApps.size() > position && mApps.get(position).getAppIcon() == null) {
                    mApps.get(position).createShowIcon();
                }
            }
        }

        synchronized(AppLoadModel.class){
            for (int pos = 0; pos < firstVisibleItem; pos++) {
                //ensure the first page can display when start this
                //ensure the icon's amount greater than the item
                //ensure the icon have been created
                if (pos >= LaunchAppDataUtil.DEFAULT_VISIBLE_ITEM
                        && mApps.size() > pos
                        && mApps.get(pos).getAppIcon() != null) {
                    mApps.get(pos).recycleBitmaps();
                }
            }
            for (int pos = firstVisibleItem + visibleItemCount; pos < totalItemCount; pos++) {
                //ensure the first page can display when start this
                //ensure the icon's amount greater than the item
                //ensure the icon have been created
                if (pos >= LaunchAppDataUtil.DEFAULT_VISIBLE_ITEM
                        && mApps.size() > pos
                        && mApps.get(pos).getAppIcon() != null) {
                    mApps.get(pos).recycleBitmaps();
                }
            }
            listItemAdapter.notifyDataSetChanged();
        }
    }

    @Override
    public void onScroll(AbsListView view, int firstVisibleItem,
            int visibleItemCount, int totalItemCount) {
    }
}
