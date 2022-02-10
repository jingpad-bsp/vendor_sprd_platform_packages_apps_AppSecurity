/*
 * Copyright (C) 2016 Created by SPRD
 */

package com.sprd.applock;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.view.View;
import android.widget.TextView;

import java.lang.ref.WeakReference;

/***
 * Manages a number of views inside of the given layout. See below for a list of widgets.
 */
public class AppLockMessageArea extends TextView implements AppLockMessageDisplay {
    /** Handler token posted with accessibility announcement runnables. */
    private static final Object ANNOUNCE_TOKEN = new Object();

    /**
     * Delay before speaking an accessibility announcement. Used to prevent
     * lift-to-type from interrupting itself.
     */
    private static final long ANNOUNCEMENT_DELAY = 250;

    public static final int SECURITY_MESSAGE_DURATION = 2000;

    private final Handler mHandler;

    // Timeout before we reset the message to show charging/owner info
    long mTimeout = SECURITY_MESSAGE_DURATION;
    CharSequence mMessage;

    private CharSequence mSeparator;

    private final Runnable mClearMessageRunnable = new Runnable() {
        @Override
        public void run() {
            mMessage = null;
            update();
        }
    };

    public AppLockMessageArea(Context context) {
        this(context, null);
    }

    public AppLockMessageArea(Context context, AttributeSet attrs) {
        super(context, attrs);
        setLayerType(LAYER_TYPE_HARDWARE, null); // work around nested unclipped
                                                 // SaveLayer bug
        mHandler = new Handler(Looper.myLooper());

        mSeparator = getResources().getString(
                com.android.internal.R.string.kg_text_message_separator);

        update();
    }

    @Override
    public void setMessage(CharSequence msg, boolean important) {
        if (!TextUtils.isEmpty(msg) && important) {
            securityMessageChanged(msg);
        } else {
            clearMessage();
        }
    }

    @Override
    public void setMessage(int resId, boolean important) {
        if (resId != 0 && important) {
            CharSequence message = getContext().getResources().getText(resId);
            securityMessageChanged(message);
        } else {
            clearMessage();
        }
    }

    @Override
    public void setMessage(int resId, boolean important, Object... formatArgs) {
        if (resId != 0 && important) {
            String message = getContext().getString(resId, formatArgs);
            securityMessageChanged(message);
        } else {
            clearMessage();
        }
    }

    @Override
    public void setTimeout(int timeoutMs) {
        mTimeout = timeoutMs;
    }

    @Override
    protected void onFinishInflate() {
        // boolean shouldMarquee =
        // KeyguardUpdateMonitor.getInstance(mContext).isDeviceInteractive();
        // setSelected(shouldMarquee); // This is required to ensure marquee
        // works
    }

    private void securityMessageChanged(CharSequence message) {
        mMessage = message;
        update();
        mHandler.removeCallbacks(mClearMessageRunnable);
        if (mTimeout > 0) {
            mHandler.postDelayed(mClearMessageRunnable, mTimeout);
        }
        mHandler.removeCallbacksAndMessages(ANNOUNCE_TOKEN);
        mHandler.postAtTime(new AnnounceRunnable(this, getText()), ANNOUNCE_TOKEN,
                (SystemClock.uptimeMillis() + ANNOUNCEMENT_DELAY));
    }

    private void clearMessage() {
        mHandler.removeCallbacks(mClearMessageRunnable);
        mHandler.post(mClearMessageRunnable);
    }

    private void update() {
        CharSequence status = mMessage;
        setVisibility(TextUtils.isEmpty(status) ? INVISIBLE : VISIBLE);
        setText(status);
    }

    /**
     * Runnable used to delay accessibility announcements.
     */
    private static class AnnounceRunnable implements Runnable {
        private final WeakReference<View> mHost;
        private final CharSequence mTextToAnnounce;

        AnnounceRunnable(View host, CharSequence textToAnnounce) {
            mHost = new WeakReference<View>(host);
            mTextToAnnounce = textToAnnounce;
        }

        @Override
        public void run() {
            final View host = mHost.get();
            if (host != null) {
                host.announceForAccessibility(mTextToAnnounce);
            }
        }
    }
}
