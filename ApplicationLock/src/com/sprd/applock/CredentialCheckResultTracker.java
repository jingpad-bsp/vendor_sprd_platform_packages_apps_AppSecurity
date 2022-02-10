package com.sprd.applock;

import android.content.Intent;

public class CredentialCheckResultTracker {
    private Listener mListener;
    private boolean mHasResult = false;
    private boolean mResultMatched;
    private Intent mResultData;
    private int mResultTimeoutMs;
    private int mResultEffectiveUserId;

    public void setListener(Listener listener) {
        if (mListener == listener) {
            return;
        }
        mListener = listener;
        if (mListener != null && mHasResult) {
            mListener.onCredentialChecked(mResultMatched, mResultData, mResultTimeoutMs,
                    mResultEffectiveUserId, false /* newResult */);
        }
    }

    public void setResult(boolean matched, Intent intent, int timeoutMs, int effectiveUserId) {
        mResultMatched = matched;
        mResultData = intent;
        mResultTimeoutMs = timeoutMs;
        mResultEffectiveUserId = effectiveUserId;
        mHasResult = true;
        if (mListener != null) {
            mListener.onCredentialChecked(mResultMatched, mResultData, mResultTimeoutMs,
                    mResultEffectiveUserId, true /* newResult */);
            mHasResult = false;
        }
    }

    public void clearResult() {
        mHasResult = false;
        mResultMatched = false;
        mResultData = null;
        mResultTimeoutMs = 0;
        mResultEffectiveUserId = 0;
    }

    interface Listener {
        public void onCredentialChecked(boolean matched, Intent intent, int timeoutMs,
                int effectiveUserId, boolean newResult);
    }
}
