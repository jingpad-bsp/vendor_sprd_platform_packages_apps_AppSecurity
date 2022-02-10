package com.sprd.applock;

import android.app.Activity;
import android.app.ActionBar;
import android.content.Context;
//import android.content.pm.AppCloneUserInfo;
import android.content.pm.ApplicationInfo;
import android.content.res.Resources;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.ImageButton;
import android.widget.ListView;
import android.widget.Switch;
import android.widget.TextView;
import com.sprd.applock.ApplicationsState.AppEntry;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;

public class AppLockListActivity extends Activity {

    private static final String TAG = "Activity-List";
    private AppsAdapter mAdapter;
    private ActionBar mActionBar;
    private ApplicationsState mApplicationsState;
    private ViewGroup mRelative;
    private Switch mSelectAllBox;
    private TextView mSelectText;
    private View mRootView;
    private View mListContainer;
    private ListView mListView;
    private Context mContext;
    private boolean ignoreChange = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.applock_multi_select);
        mContext = this.getBaseContext();
        mActionBar = getActionBar();
        if (mActionBar != null) {
            mActionBar.setDisplayHomeAsUpEnabled(true);
            mActionBar.setHomeButtonEnabled(true);
        }
        mApplicationsState = ApplicationsState.getInstance(getApplication());
        mAdapter = new AppsAdapter(mApplicationsState);
    }

    private void initialViews() {
        mRelative = (ViewGroup) findViewById(R.id.select);
        mSelectAllBox = (Switch) findViewById(R.id.select_all);
        mSelectText = (TextView) findViewById(R.id.select_text);
        mListContainer = (View) findViewById(R.id.list_container);
        mSelectAllBox.setVisibility(View.VISIBLE);

        if (mListContainer != null) {
            View emptyView = mListContainer.findViewById(com.android.internal.R.id.empty);
            ListView lv = (ListView) mListContainer.findViewById(android.R.id.list);
            if (emptyView != null) {
                lv.setEmptyView(emptyView);
            }
            lv.setSaveEnabled(true);
            lv.setItemsCanFocus(true);
            lv.setTextFilterEnabled(true);
            mListView = lv;
            mListView.setAdapter(mAdapter);
            mListView.setRecyclerListener(mAdapter);
            mListView.setOnItemClickListener(new OnItemClickListener() {
                @Override
                public void onItemClick(AdapterView<?> arg0, View view, int position, long arg3) {
                    AppLockViewHolder holder = (AppLockViewHolder) view.getTag();
                    holder.checkBox.setChecked(!holder.checkBox.isChecked());
                    mAdapter.setChecked(position, holder.checkBox.isChecked());
                    updateViews();
                }
            });
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        initialViews();
        if (mAdapter != null) {
            mAdapter.resume();
        }
        updateViews();
    }

    private void updateViews() {
        Log.d(AppLockUtils.TAG_LOG, TAG + " updateViews()...");
        if (mAdapter.mEntries != null && mAdapter.mEntries.size() == 0) {
            mRelative.setVisibility(View.GONE);
        } else {
            mRelative.setVisibility(View.VISIBLE);
        }

        int selectNum = mAdapter.getCheckedCount();
        int allNum = mAdapter.getCount();
        Log.d(AppLockUtils.TAG_LOG, TAG + " selectNum =" + selectNum + " allNum = " + allNum);
        if (selectNum == allNum && allNum != 0) {
            ignoreChange = true;
            mSelectAllBox.setChecked(true);
            mSelectText.setText(R.string.cancle_select_all);
            ignoreChange = false;
        } else {
            ignoreChange = true;
            mSelectAllBox.setChecked(false);
            mSelectText.setText(R.string.select_all);
            ignoreChange = false;
        }

        mSelectAllBox.setOnCheckedChangeListener(mOnCheckedChangeListener);
        mAdapter.notifyDataSetChanged();
    }

    @Override
    public void onPause() {
        super.onPause();
        if (mAdapter != null) {
            mAdapter.pause();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mAdapter != null) {
            mAdapter.release();
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    // The class AppsAdapter
    class AppsAdapter extends BaseAdapter implements ApplicationsState.Callbacks,
            AbsListView.RecyclerListener {
        private static final String TAG = "Adapter";
        private final ApplicationsState mState;
        private final ApplicationsState.Session mSession;
        private final ArrayList<View> mActive = new ArrayList<View>();
        private ArrayList<AppEntry> mEntries;
        private boolean mResumed;
        private LayoutInflater mInflater;
        private AppLockViewHolder holder;

        public AppsAdapter(ApplicationsState state) {
            mState = state;
            mSession = state.newSession(this);
            mInflater = LayoutInflater.from(mContext);
        }

        private HashMap<Long, Long> mCheckMap = new HashMap<Long, Long>();

        public void setChecked(int position, boolean checked) {
            if (AppLockUtils.DEBUG) {
                Log.d(AppLockUtils.TAG_LOG, TAG + " setChecked!  position=" + position);
                Log.d(AppLockUtils.TAG_LOG, TAG + " setChecked!  checked=" + checked);
            }
            long id = getItemId(position);
            if (checked) {
                mCheckMap.put(id, id);
            } else {
                mCheckMap.remove(id);
            }
            // set lock status for app
            AppLockUtils.setAPPLockedFlag(mContext, mEntries.get(position).info, checked);
        }

        public boolean hasCheckedItem() {
            return mCheckMap.size() > 0;
        }

        public boolean isChecked(int position) {
            long id = getItemId(position);
            return mCheckMap.containsValue(id);
        }

        public int getCheckedCount() {
            return mCheckMap.size();
        }

        public void updateCheckedMap() {
            if (mCheckMap.size() == 0) {
                return;
            }
            List<Long> delList = new ArrayList<Long>();
            for (Long id : mCheckMap.keySet()) {
                boolean isChecked = false;
                for (int j = 0; j < mEntries.size(); j++) {
                    if (mEntries.get(j).id == id) {
                        isChecked = true;
                        break;
                    }
                }
                if (isChecked == false) {
                    delList.add(id);
                }

            }
            for (int i = 0; i < delList.size(); i++) {
                mCheckMap.remove(delList.get(i));
            }
        }

        public ArrayList<AppEntry> getCheckedAppInfo() {
            ArrayList<AppEntry> entry = new ArrayList<AppEntry>(mCheckMap.size());
            int pos = 0;
            AppEntry appInfo;
            for (int i = 0; i < getCount(); i++) {
                if (isChecked(i)) {
                    appInfo = mEntries.get(i);
                    entry.add(appInfo);
                }
            }
            return entry;
        }

        public void resume() {
            Log.d(AppLockUtils.TAG_LOG, TAG + " Resume!  mResumed=" + mResumed);
            if (!mResumed) {
                mResumed = true;
                mSession.resume();
                rebuildList(true);
            }
        }

        public void pause() {
            if (mResumed) {
                mResumed = false;
                mSession.pause();
            }
        }

        public void release() {
            mSession.release();
        }

        public void rebuildList(boolean eraseold) {
            Log.d(AppLockUtils.TAG_LOG, TAG + " Rebuilding app list...");
            if (mEntries != null) {
                mCheckMap.clear();
                Log.d(AppLockUtils.TAG_LOG, TAG + " Rebuilding app list clear mCheckMap");
            }
            ApplicationsState.AppFilter filterObj = ApplicationsState.FILTER_DOWNLOADED_AND_LAUNCHER;
            Comparator<AppEntry> comparatorObj = ApplicationsState.ALPHA_COMPARATOR;
            ArrayList<AppEntry> entries = mSession.rebuild(filterObj, comparatorObj);
            Log.d(AppLockUtils.TAG_LOG, TAG + " Rebuilding entries==" + entries);
            if (entries == null && !eraseold) {
                return;
            }
            if (entries != null) {
                Log.e(AppLockUtils.TAG_LOG, TAG + " entries.size()==" + entries.size());
                Log.e(AppLockUtils.TAG_LOG, TAG + " entries==" + entries);
                mEntries = entries;
            } else {
                mEntries = null;
            }
            if (mEntries != null) {
                Iterator<AppEntry> it = mEntries.iterator();
                while (it.hasNext()) {
                    AppEntry entry = it.next();
                    // for remove special APP. Such as Setting...
                    Log.d(AppLockUtils.TAG_LOG, TAG + " entry.info.flags==" + entry.info.flags);
                    Log.d(AppLockUtils.TAG_LOG, TAG + " entry.info.packageName=="
                            + entry.info.packageName);
                    if (AppLockUtils.isAppNeedtoFilter(entry.info.packageName)) {
                        Log.d(AppLockUtils.TAG_LOG, TAG + " Remove the APP: "
                                + entry.info.packageName);
                        it.remove();
                    }
                    if ((entry.info.flags & ApplicationInfo.FLAG_INSTALLED) == 0) {
                        Log.d(AppLockUtils.TAG_LOG, TAG + " Remove FLAG_INSTALLED/entry.info.packageName== "
                                + entry.info.packageName);
                        it.remove();
                    }
                }
            }
            for (int i = 0; i < getCount(); i++) {
                Log.d(AppLockUtils.TAG_LOG,
                        TAG + " begin checkSwitchFromFile ==uid: " + mEntries.get(i).info.uid
                                + " packageName: " + mEntries.get(i).info.packageName);
                boolean enabled = AppLockUtils.getAppLockState(mContext, mEntries.get(i).info);
                Log.d(AppLockUtils.TAG_LOG, TAG + " enabled=" + enabled);
                setChecked(i, enabled);
            }
            updateViews();
            notifyDataSetChanged();

            if (entries == null) {
                mListContainer.setVisibility(View.INVISIBLE);
            } else {
                mListContainer.setVisibility(View.VISIBLE);
            }
        }

        @Override
        public void onRunningStateChanged(boolean running) {
            // Log.i(AppLockUtils.TAG_LOG, TAG + " onRunningStateChanged...running " + running);
        }

        @Override
        public void onRebuildComplete(ArrayList<AppEntry> apps) {
            Log.i(AppLockUtils.TAG_LOG, TAG + " onRebuildComplete...");
            mListContainer.setVisibility(View.VISIBLE);
            mEntries = apps;
            if (mEntries != null) {
                Iterator<AppEntry> it = mEntries.iterator();
                while (it.hasNext()) {
                    AppEntry entry = it.next();
                    // for remove special APP. Such as Setting...
                    Log.d(AppLockUtils.TAG_LOG, TAG + " entry.info.packageName: "
                            + entry.info.packageName);
                    if (AppLockUtils.isAppNeedtoFilter(entry.info.packageName)) {
                        Log.d(AppLockUtils.TAG_LOG, TAG + " Remove the APP: "
                                + entry.info.packageName);
                        it.remove();
                    }
                    //if (AppCloneUserInfo.isAppCloneUid(entry.info.uid)) {
                    //    Log.d(AppLockUtils.TAG_LOG, TAG + " Remove the cloned APP: "
                    //            + entry.info.packageName);
                    //    it.remove();
                    //}
                }
            }
            Log.i(AppLockUtils.TAG_LOG, TAG + " onRebuildComplete and APPS count = " + getCount());
            for (int i = 0; i < getCount(); i++) {
                boolean enabled = AppLockUtils.getAppLockState(mContext, mEntries.get(i).info);
                setChecked(i, enabled);
            }

            updateViews();
            notifyDataSetChanged();
        }

        @Override
        public void onPackageListChanged() {
            Log.i(AppLockUtils.TAG_LOG, TAG + " onPackageListChanged...");
            rebuildList(false);
        }

        @Override
        public void onPackageIconChanged() {
            // We ensure icons are loaded when their item is displayed, so
            // don't care about icons loaded in the background.
            // Log.i(AppLockUtils.TAG_LOG, TAG + " onPackageIconChanged...");
        }

        @Override
        public void onPackageSizeChanged(String packageName) {
            // Log.i(AppLockUtils.TAG_LOG, TAG + " onPackageSizeChanged...packageName:" + packageName);
        }

        @Override
        public void onAllSizesComputed() {
            Log.i(AppLockUtils.TAG_LOG, TAG + " onAllSizesComputed...");

        }

        public int getCount() {
            return mEntries != null ? mEntries.size() : 0;
        }

        public Object getItem(int position) {
            return mEntries.get(position);
        }

        public AppEntry getAppEntry(int position) {
            return mEntries.get(position);
        }

        public long getItemId(int position) {
            return mEntries.get(position).id;
        }

        @Override
        public void onMovedToScrapHeap(View view) {
            mActive.remove(view);
        }

        @Override
        public void onLauncherInfoChanged() {
            Log.i(AppLockUtils.TAG_LOG, TAG + " onLauncherInfoChanged...");
            rebuildList(false);
        }

        @Override
        public void onLoadEntriesCompleted() {
            // TODO Auto-generated method stub
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            if (AppLockUtils.DEBUG) {
                Log.i(AppLockUtils.TAG_LOG, TAG + " getView...position= " + position);
            }
            holder = AppLockViewHolder.createOrRecycle(mInflater, convertView);
            convertView = holder.rootView;
            AppEntry entry = mEntries.get(position);
            synchronized (entry) {
                holder.entry = entry;
                String label  = entry.info.loadLabel(mContext.getPackageManager()).toString();
                if (!TextUtils.isEmpty(label)) {
                    holder.appName.setText(label);
                }
                else if (entry.label != null) {
                    holder.appName.setText(entry.label);
                }
                mState.ensureIcon(entry);
                if (entry.icon != null) {
                    holder.appIcon.setImageDrawable(entry.icon);
                }
                holder.checkBox.setChecked(isChecked(position));
            }
            mActive.remove(convertView);
            mActive.add(convertView);
            return convertView;
        }

    }

    private Switch.OnCheckedChangeListener mOnCheckedChangeListener = new Switch.OnCheckedChangeListener() {
        @Override
        public void onCheckedChanged(CompoundButton view, boolean isChecked) {
            if (AppLockUtils.DEBUG) {
                Log.d(AppLockUtils.TAG_LOG, TAG + " onCheckedChanged/isChecked =" + isChecked);
                Log.d(AppLockUtils.TAG_LOG, TAG + " onCheckedChanged/ignoreChange =" + ignoreChange);
            }
            if (!ignoreChange) {
                switch (view.getId()) {
                    case R.id.select_all:
                        int allNum = mAdapter.getCount();
                        for (int i = 0; i < allNum; i++) {
                            mAdapter.setChecked(i, isChecked);
                        }
                        if (isChecked) {
                            mSelectText.setText(R.string.cancle_select_all);
                        } else {
                            mSelectText.setText(R.string.select_all);
                        }
                        mAdapter.notifyDataSetChanged();
                        break;
                }
            }
        }
    };

}
