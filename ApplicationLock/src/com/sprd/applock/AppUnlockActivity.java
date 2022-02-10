
package com.sprd.applock;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;

import com.android.internal.widget.LockPatternChecker;
import com.android.internal.widget.LockPatternView;
import com.android.internal.widget.LockPatternView.Cell;
import com.android.internal.widget.LockPatternUtils.RequestThrottledException;
import android.app.Activity;
import android.app.ActivityManager;
import android.app.KeyguardManager;
import android.app.admin.DevicePolicyManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.hardware.fingerprint.Fingerprint;
import android.hardware.fingerprint.FingerprintManager;
import android.hardware.fingerprint.FingerprintManager.AuthenticationCallback;
import android.hardware.fingerprint.FingerprintManager.AuthenticationResult;

import android.os.AsyncTask;
import android.os.Bundle;
import android.os.CancellationSignal;
import android.os.CountDownTimer;
import android.os.Handler;
import android.os.PowerManager;
import android.os.SystemClock;
import android.os.Build.VERSION;
import android.os.UserHandle;
import android.provider.Settings;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.text.InputType;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.WindowManager;
import android.view.KeyEvent;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Toast;
import android.widget.TextView;
import com.sprd.applock.ApplicationsState.AppEntry;
import com.android.internal.widget.LockPatternUtils;

import com.sprd.applock.CredentialCheckResultTracker.Listener;

/**
 * An example full-screen activity that shows and hides the system UI (i.e.
 * status bar and navigation/system bar) with user interaction.
 */
public class AppUnlockActivity extends Activity implements Listener,
        TextView.OnEditorActionListener {

    private final String TAG = "Activity-Unlock";
    private final String ACTION_LOCKOUT_RESET = "com.android.server.fingerprint.ACTION_LOCKOUT_RESET";
    private final int UNMATCH_DIALOG_NORMAL_TIMEOUT = 1500;
    private EditText mText;
    private Button mConfirmButton;
    private Button mCancelButton;
    private Button mOnlyCancelButton;
    private ImageView fingerprintIconIV;
    private LinearLayout bottomBtnLayout;
    private LinearLayout oneBtnLayout;
    private LockPatternView mLockPatternView;
    private AsyncTask<?, ?, ?> mPendingLockCheck;
    private CredentialCheckResultTracker mCredentialCheckResultTracker;
    private int mPasswordMinLength = LockPatternUtils.MIN_LOCK_PASSWORD_SIZE;
    private int mPasswordMaxLength = 16;

    private enum Stage {
        NeedToUnlock, NeedToUnlockWrong, LockedOut
    }

    private int mEffectiveUserId;
    private int mNumWrongConfirmAttempts = 0;
    private CountDownTimer mCountdownTimer;
    private boolean mIsAlpha;
    private FingerprintManager mFingerprintManager;

    private KeyguardManager mKeyguardManager;
    private PowerManager mPowerManager;
    private int mMode = 0; // 0: check mode; 1: first input; 2: input confirm;
    private String mFirstPwd;
    private String mTempPwd;
    private LockPatternUtils mLockPatternUtils;
    private String mCheckedPackage;
    private boolean mIsClone = false;
    private CancellationSignal mCancelSignal;
    private Handler mHandler = new Handler();
    private boolean mNeedRestore = false;

    private AppLockMessageArea mSecurityMessageDisplay;
    int mThrottleTimeout = 0;
    int attempts = 0;
    public static final int FAILED_ATTEMPTS_BEFORE_TIMEOUT = 5;
    public static final int THROTTLED_TIMEOUT = 12345;
    public static final int CRYPT_TYPE_PASSWORD = 0;
    public static final int CRYPT_TYPE_PIN = 3;
    private static int mActiveQuality = DevicePolicyManager.PASSWORD_QUALITY_ALPHABETIC;
    private int mTaskId = -1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        log(" onCreate");
        setContentView(R.layout.appunlock_main);
        mLockPatternUtils = new LockPatternUtils(this);
        mKeyguardManager = (KeyguardManager) getSystemService(Context.KEYGUARD_SERVICE);
        mPowerManager = (PowerManager) getSystemService(Context.POWER_SERVICE);
        mFingerprintManager = (FingerprintManager) getSystemService(Context.FINGERPRINT_SERVICE);
        mCancelSignal = new CancellationSignal();
        processExtraDate();

        ViewOnClickListener clickListener = new ViewOnClickListener();
        mText = (EditText) findViewById(R.id.password_txt);
        mText.setOnEditorActionListener(this);
        mText.addTextChangedListener(mTextWatcher);
        mConfirmButton = (Button) findViewById(R.id.button_ok);
        mCancelButton = (Button) findViewById(R.id.button_cancel);
        mOnlyCancelButton = (Button) findViewById(R.id.button_cancel_only);
        mConfirmButton.setOnClickListener(clickListener);
        mCancelButton.setOnClickListener(clickListener);
        mOnlyCancelButton.setOnClickListener(clickListener);
        fingerprintIconIV = (ImageView) findViewById(R.id.iv_fingerprintIcon);
        bottomBtnLayout = (LinearLayout) findViewById(R.id.fullscreen_content_controls);
        oneBtnLayout = (LinearLayout) findViewById(R.id.onebuttonlayout);
        mLockPatternView = (LockPatternView) findViewById(R.id.lockPattern);
        mSecurityMessageDisplay = (AppLockMessageArea) findViewById(R.id.keyguard_message_area);
        mEffectiveUserId = UserHandle.myUserId();
        mLockPatternView.setTactileFeedbackEnabled(isTactileFeedbackEnabled());
        mLockPatternView.setInStealthMode(!isVisiblePatternEnabled(mEffectiveUserId));
        mLockPatternView.setOnPatternListener(mConfirmExistingLockPatternListener);
        registerReceiver(mLockoutReceiver, new IntentFilter(ACTION_LOCKOUT_RESET));
        if (mCredentialCheckResultTracker == null) {
            mCredentialCheckResultTracker = new CredentialCheckResultTracker();
        }
        mTempPwd = null;
    }

    @Override
    public void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        log(" onNewIntent");
        setIntent(intent);
        processExtraDate();
        if (!TextUtils.isEmpty(mTempPwd)) {
            mTempPwd = null;
        }
    }

    private void processExtraDate() {
        Intent intent = getIntent();
        String title = "";
        if (intent != null) {
            mCheckedPackage = intent.getStringExtra("package_name");
            mIsClone = intent.getBooleanExtra("is_cloned", false);
            title = AppLockUtils.getAppNameByPackageName(this, mCheckedPackage);
            if (mIsClone) {
                title = getString(R.string.app_clone_name, title);
            }
            mTaskId = AppLockUtils.getRunningTaskInfo(this, mCheckedPackage);
        }
        log(" processExtraDate, title =" + title + ", mCheckedPackage = " + mCheckedPackage);
        changeTitle(title);
    }

    private final BroadcastReceiver mLockoutReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if ("com.android.server.fingerprint.ACTION_LOCKOUT_RESET".equals(intent.getAction())) {
                log(" receive ACTION_LOCKOUT_RESET");
                MyToast(null);
                retryFingerprint();
            }
        }
    };

    private void retryFingerprint() {
        log(" retryFingerprint");
        if (null == mCancelSignal) {
            mCancelSignal = new CancellationSignal();
        }
        mFingerprintManager.authenticate(null, mCancelSignal, 0, mFingerprintAuthCallback, null);
    }

    @Override
    public void onResume() {
        super.onResume();
        log("$$$$ onResume...");
        if (null == mCancelSignal) {
            mCancelSignal = new CancellationSignal();
        }
        mFingerprintManager.authenticate(null, mCancelSignal, 0, mFingerprintAuthCallback, null);
        mActiveQuality = getActivePasswordQuality(mEffectiveUserId);
        if (mActiveQuality == DevicePolicyManager.PASSWORD_QUALITY_SOMETHING) {
            mLockPatternView.setVisibility(View.VISIBLE);
            mText.setVisibility(View.GONE);
            bottomBtnLayout.setVisibility(View.GONE);
            oneBtnLayout.setVisibility(View.VISIBLE);
            //mConfirmButton.setVisibility(View.GONE);
            //mCancelButton.setVisibility(View.VISIBLE);
        } else {
            mLockPatternView.setVisibility(View.GONE);
            mText.setVisibility(View.VISIBLE);
            bottomBtnLayout.setVisibility(View.VISIBLE);
            oneBtnLayout.setVisibility(View.GONE);
            //mConfirmButton.setVisibility(View.VISIBLE);
            //mCancelButton.setVisibility(View.VISIBLE);
        }

        int currentType = mText.getInputType();
        mIsAlpha = mActiveQuality != DevicePolicyManager.PASSWORD_QUALITY_NUMERIC;
        mText.setInputType(mIsAlpha ? (InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD)
                : (InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_VARIATION_PASSWORD));

        if (View.VISIBLE == mText.getVisibility()) {
            if (mTempPwd != null) {
                mText.setText(mTempPwd);
                mText.setSelection(mTempPwd.length());
            } else {
                mText.setText("");
            }
        }
        if (!mLockPatternView.isEnabled()) {
            // The deadline has passed, but the timer was cancelled. Or the
            // pending lock
            // check was cancelled. Need to clean up.
            mNumWrongConfirmAttempts = 0;
            updateStage(Stage.NeedToUnlock);
        }
        /** SPRD: added for bug 617839 @{ */
        long deadline = getLockoutAttemptDeadline(mEffectiveUserId);
        int timeOut = Settings.System.getInt(getContentResolver(), "PIN_INPUT_TIMEOUT", 0);
        log("$$$$ onResume... and deadline:" + deadline + " timeOut:" + timeOut);
        if (timeOut == 1 && deadline > 0) {
            mCredentialCheckResultTracker.clearResult();
            handleAttemptLockout(deadline);
        }
        /** @} */
        mCredentialCheckResultTracker.setListener(this);
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        log(" onSaveInstanceState...");
        outState.putString("now_pwd", mText.getText().toString());
        super.onSaveInstanceState(outState);
    }

    @Override
    public void onRestoreInstanceState(Bundle savedInstanceState) {
        mTempPwd = savedInstanceState.getString("now_pwd");
        log(" onRestoreInstanceState...");
        super.onRestoreInstanceState(savedInstanceState);
    }

    @Override
    public void onPause() {
        super.onPause();
        log(" onPause...");
        cancelOperation();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        log(" onDestroy...");
        mNumWrongConfirmAttempts = 0;
        unregisterReceiver(mLockoutReceiver);
    }

    @Override
    public void onBackPressed() {
        backToHome();
    }

    private boolean checkPwd(String pwd) {
        if (mLockPatternUtils == null) {
            Log.e(TAG, "mLockPatternUtils == null");
            return false;
        }
        boolean result = false;
        Class[] params = new Class[] {
                String.class, int.class
        };
        try {
            Method method = mLockPatternUtils.getClass().getMethod("checkPassword", params);
            if (method != null) {
                try {
                    result = (boolean) method.invoke(mLockPatternUtils, pwd,
                            ActivityManager.getCurrentUser());
                    mThrottleTimeout = 0;
                } catch (Exception e) {
                    Log.e(TAG, " method.invoke, ", e);
                    Throwable cause = e.getCause();
                    if (cause instanceof RequestThrottledException) {
                        mThrottleTimeout = ((RequestThrottledException) cause).getTimeoutMs();
                        Log.e(TAG, " mThrottleTimeout = " + mThrottleTimeout);
                    }
                }
            }
        } catch (NoSuchMethodException e) {
            Log.e(TAG, "NoSuchMethodException, ", e);
        }
        return result;
    }

    private void cancelOperation() {
        log(" cancelOperation...");
        if (mCancelSignal != null) {
            mCancelSignal.cancel();
            mCancelSignal = null;
        }
    }

    private boolean hasFingerEnabled() {
        boolean hasEnabled = false;
        hasEnabled = mFingerprintManager.hasEnrolledFingerprints();
        log(" hasFingerEnabled, hasEnabled: " + hasEnabled);
        return hasEnabled;
    }

    private boolean unLock() {
        log(" unLock and setAlreadyUnlockedFlag...");
        AppLockUtils.setAppAlreadyUnlockedFlag(this, mCheckedPackage);
        return true;
    }

    private boolean isPasswordCorrect(String pwd) {
        SharedPreferences sp = getSharedPreferences("data", Activity.MODE_PRIVATE);
        String password = sp.getString("pwd", "");
        if (pwd.equals(password)) {
            return true;
        }
        return false;
    }

    private void changeTitle(String name) {
        if (TextUtils.isEmpty(name)) {
            this.setTitle(getString(R.string.msg_password));
        } else {
            this.setTitle(getString(R.string.msg_app_locked, name));
        }
    }

    private void backToHome() {
        Log.d(AppLockUtils.TAG_LOG, TAG + " backToHome  sleep 200");
        Intent home = new Intent(Intent.ACTION_MAIN);
        home.addCategory(Intent.CATEGORY_HOME);
        startActivity(home);
        try {
            Thread.sleep(200);
        } catch (Exception e) {
            Log.e(AppLockUtils.TAG_LOG, TAG + " backToHome exception, " + e);
        } finally {
            finish();
        }
    }

    private void MyToast(String str) {
        Log.d(AppLockUtils.TAG_LOG, TAG + " MyToast str = " + str);
        mSecurityMessageDisplay.setMessage(str, true);
    }

    private void log(String msg) {
        if (AppLockUtils.DEBUG) {
            Log.d(AppLockUtils.TAG_LOG, TAG + " " + msg);
        } else {
            // nothing to do...
        }
    }

    private void startCheckPassword(final String pin, final Intent intent) {
        Log.d(AppLockUtils.TAG_LOG, TAG + " 7.0 startCheckPassword and mMode:" + mMode);
        final int userId = mEffectiveUserId;
        mPendingLockCheck = LockPatternChecker.checkPassword(
                mLockPatternUtils,
                pin,
                userId,
                new LockPatternChecker.OnCheckCallback() {
                    @Override
                    public void onChecked(boolean matched, int timeoutMs) {
                        mPendingLockCheck = null;
                        if (matched) {
                            intent.putExtra(AppLockUtils.EXTRA_KEY_TYPE,
                                    mIsAlpha ? CRYPT_TYPE_PASSWORD : CRYPT_TYPE_PIN);
                            intent.putExtra(AppLockUtils.EXTRA_KEY_PASSWORD, pin);
                        } else {
                            if (mIsAlpha) {
                                MyToast(getString(R.string.msg_password_is_wrong));
                            } else {
                                MyToast(getString(R.string.msg_wrong_pin));
                            }
                            mText.setText("");
                        }
                        mCredentialCheckResultTracker.setResult(matched, intent, timeoutMs, userId);
                    }
                });
    }

    private class ViewOnClickListener implements View.OnClickListener {
        @Override
        public void onClick(View view) {
            if (view == mConfirmButton) {
                handleNext();
            } else if (view == mCancelButton) {
                backToHome();
            } else if (view == mOnlyCancelButton) {
                backToHome();
            }
        }
    }

    private boolean isTactileFeedbackEnabled() {
        if (mLockPatternUtils == null) {
            Log.e(TAG, "mLockPatternUtils == null");
            return false;
        }
        boolean result = false;
        try {
            Method method = mLockPatternUtils.getClass().getMethod("isTactileFeedbackEnabled");
            if (method != null) {
                try {
                    result = (boolean) method.invoke(mLockPatternUtils);
                } catch (Exception e) {
                    Log.e(TAG, " isTactileFeedbackEnabled method.invoke, ", e);
                }
            }
        } catch (NoSuchMethodException e) {
            Log.e(TAG, " isTactileFeedbackEnabled NoSuchMethodException, ", e);
        }
        return result;
    }

    private boolean isVisiblePatternEnabled(int userId) {
        if (mLockPatternUtils == null) {
            Log.e(TAG, "mLockPatternUtils == null");
            return false;
        }
        boolean result = false;
        Class[] params = new Class[] {
            int.class
        };
        try {
            Method method = mLockPatternUtils.getClass().getMethod("isVisiblePatternEnabled",
                    params);
            if (method != null) {
                try {
                    result = (boolean) method.invoke(mLockPatternUtils, userId);
                } catch (Exception e) {
                    Log.e(TAG, " method.invoke, ", e);
                }
            }
        } catch (NoSuchMethodException e) {
            Log.e(TAG, " NoSuchMethodException, ", e);
        }
        return result;
    }

    private int getActivePasswordQuality(int userId) {
        int activePasswordQuality = DevicePolicyManager.PASSWORD_QUALITY_UNSPECIFIED;
        if (mLockPatternUtils == null) {
            Log.e(TAG, "mLockPatternUtils == null");
            return activePasswordQuality;
        }
        Class[] params = new Class[] {
            int.class
        };
        try {
            Method method = mLockPatternUtils.getClass().getMethod("getActivePasswordQuality",
                    params);
            if (method != null) {
                try {
                    activePasswordQuality = (int) method.invoke(mLockPatternUtils, userId);
                } catch (Exception e) {
                    Log.e(TAG, " method.invoke, ", e);
                }
            }
        } catch (NoSuchMethodException e) {
            Log.e(TAG, " NoSuchMethodException, ", e);
        }
        return activePasswordQuality;
    }

    private boolean checkPattern(List<LockPatternView.Cell> pattern, int userId) {
        if (mLockPatternUtils == null) {
            Log.e(AppLockUtils.TAG_LOG, TAG + " mLockPatternUtils == null");
            return false;
        }
        boolean result = false;
        Method[] methods = mLockPatternUtils.getClass().getMethods();
        if (methods != null) {
            for (Method m : methods) {
                String methodName = m.getName();
                if ("checkPattern".equals(methodName)) {
                    try {
                        result = (boolean) m.invoke(mLockPatternUtils, pattern, userId);
                        log(" invoke...result:" + result);
                        mThrottleTimeout = 0;
                    } catch (Exception e) {
                        Log.e(AppLockUtils.TAG_LOG, TAG + " method.invoke, ", e);
                        Throwable cause = e.getCause();
                        if (cause instanceof RequestThrottledException) {
                            mThrottleTimeout = ((RequestThrottledException) cause).getTimeoutMs();
                            Log.e(AppLockUtils.TAG_LOG, TAG + " mThrottleTimeout = "
                                    + mThrottleTimeout);
                        }
                    }
                }
            }
        }
        return result;
    }

    // Add by frank begin {
    private long setLockoutAttemptDeadline(int userId, int time) {
        if (mLockPatternUtils == null) {
            Log.e(TAG, "mLockPatternUtils == null, return");
            return 0;
        }
        long result = 0;
        Method[] methods = mLockPatternUtils.getClass().getMethods();
        if (methods != null) {
            for (Method m : methods) {
                String methodName = m.getName();
                if ("setLockoutAttemptDeadline".equals(methodName)) {
                    try {
                        result = (long) m.invoke(mLockPatternUtils, userId, time);
                    } catch (Exception e) {
                        Log.e(TAG, " method.invoke, ", e);
                    }
                }
            }
        }
        return result;
    }

    // Add by frank end }

    /** SPRD: added for bug 617839 @{ */
    private long getLockoutAttemptDeadline(int userId) {
        if (mLockPatternUtils == null) {
            Log.e(TAG, "mLockPatternUtils == null");
            return 0;
        }
        long result = 0;
        Method[] methods = mLockPatternUtils.getClass().getMethods();
        if (methods != null) {
            for (Method m : methods) {
                String methodName = m.getName();
                if ("getLockoutAttemptDeadline".equals(methodName)) {
                    try {
                        result = (long) m.invoke(mLockPatternUtils, userId);
                    } catch (Exception e) {
                        Log.e(TAG, " method.invoke, ", e);
                    }
                }
            }
        }
        return result;
    }

    /** @} */

    private void updateStage(Stage stage) {
        switch (stage) {
            case NeedToUnlock:
                mLockPatternView.setEnabled(true);
                mLockPatternView.enableInput();
                mLockPatternView.clearPattern();
                break;
            case NeedToUnlockWrong:
                mLockPatternView.setDisplayMode(LockPatternView.DisplayMode.Wrong);
                mLockPatternView.setEnabled(true);
                mLockPatternView.enableInput();
                break;
            case LockedOut:
                mLockPatternView.clearPattern();
                // enabled = false means: disable input, and have the
                // appearance of being disabled.
                mLockPatternView.setEnabled(false);
                break;
        }
    }

    private Runnable mClearPatternRunnable = new Runnable() {
        public void run() {
            mLockPatternView.clearPattern();
        }
    };

    // clear the wrong pattern unless they have started a new one
    // already
    private void postClearPatternRunnable() {
        mLockPatternView.removeCallbacks(mClearPatternRunnable);
        mLockPatternView.postDelayed(mClearPatternRunnable, UNMATCH_DIALOG_NORMAL_TIMEOUT);
    }

    private void moveToCaller() {
        AppLockUtils.backToCaller(this, mCheckedPackage, mTaskId);
    }

    private void startDisappearAnimation(Intent intent) {
        unLock();
        moveToCaller();
        finish();
    }

    /**
     * The pattern listener that responds according to a user confirming an
     * existing lock pattern.
     */
    private LockPatternView.OnPatternListener mConfirmExistingLockPatternListener = new LockPatternView.OnPatternListener() {

        public void onPatternStart() {
            mLockPatternView.removeCallbacks(mClearPatternRunnable);
        }

        public void onPatternCleared() {
            mLockPatternView.removeCallbacks(mClearPatternRunnable);
        }

        public void onPatternCellAdded(List<Cell> pattern) {
        }

        public void onPatternDetected(List<LockPatternView.Cell> pattern) {
            mLockPatternView.setEnabled(false);
            Intent intent = new Intent();
            // startCheckPattern(pattern);//for 6.0
            startCheckPattern(pattern, intent);
        }

        private void startCheckPattern(final List<LockPatternView.Cell> pattern, final Intent intent) {
            log(" startCheckPattern...new way for 7.0");
            if (pattern.size() < LockPatternUtils.MIN_PATTERN_REGISTER_FAIL) {
                // Pattern size is less than the minimum, do not count it as an
                // fail attempt.
                log(" startCheckPattern...and pattern.size() < 4");
                onPatternChecked(false, intent, 0, mEffectiveUserId, false);
                return;
            }
            final int userId = mEffectiveUserId;
            // boolean checkResult = checkPattern(pattern, userId);
            // log(" checkResult = " + checkResult);
            // onPatternChecked(checkResult);
            mPendingLockCheck = LockPatternChecker.checkPattern(mLockPatternUtils, pattern, userId,
                    new LockPatternChecker.OnCheckCallback() {
                        @Override
                        public void onChecked(boolean matched, int timeoutMs) {
                            mPendingLockCheck = null;
                            mCredentialCheckResultTracker.setResult(matched, intent, timeoutMs,
                                    userId);
                        }
                    });
        }
        /*
         * private void startCheckPattern(final List<LockPatternView.Cell>
         * pattern) { log(" startCheckPattern..."); if (pattern.size() <
         * LockPatternUtils.MIN_PATTERN_REGISTER_FAIL ) {
         * log(" startCheckPattern...and pattern.size() < 4");
         * onPatternChecked(false); return; } int userId = mEffectiveUserId;
         * boolean checkResult = checkPattern(pattern, userId);
         * log(" checkResult = " + checkResult); onPatternChecked(checkResult);
         * } private void onPatternChecked(boolean matched, Intent intent, int
         * timeoutMs, int effectiveUserId, boolean newResult) {
         * mLockPatternView.setEnabled(true); if (matched) { if (newResult) {
         * //reportSuccessfullAttempt(); } startDisappearAnimation(intent);
         * //checkForPendingIntent(); } else { if (timeoutMs > 0) {
         * //refreshLockScreen(); long deadline =
         * mLockPatternUtils.setLockoutAttemptDeadline(effectiveUserId,
         * timeoutMs); handleAttemptLockout(deadline); } else {
         * updateStage(Stage.NeedToUnlockWrong); postClearPatternRunnable(); }
         * if (newResult) { //reportFailedAttempt(); } } } private void
         * onPatternChecked(boolean matched) {
         * mLockPatternView.setEnabled(true); if (matched) {
         * startDisappearAnimation(); // mLockPatternView.clearPattern(); } else
         * { updateStage(Stage.NeedToUnlockWrong); postClearPatternRunnable();
         * if (mThrottleTimeout > 0) { long deadline =
         * setLockoutAttemptDeadline(mEffectiveUserId, mThrottleTimeout);
         * log(" mThrottleTimeout = " + mThrottleTimeout + ", deadline" +
         * deadline); handleAttemptLockout(deadline); } else if
         * (mThrottleTimeout == 0) {
         * MyToast(getString(R.string.msg_password_is_wrong)); } } }
         */
    };

    private void handleAttemptLockout(long elapsedRealtimeDeadline) {
        /** SPRD: Modified for bug 617839 @{ */
        if (mActiveQuality == DevicePolicyManager.PASSWORD_QUALITY_SOMETHING) {
            mLockPatternView.clearPattern();
            mLockPatternView.setEnabled(false);
        } else {
            mText.setEnabled(false);
            mConfirmButton.setEnabled(false);
        }
        /** @} */

        final long elapsedRealtime = SystemClock.elapsedRealtime();

        mCountdownTimer = new CountDownTimer(elapsedRealtimeDeadline - elapsedRealtime, 1000) {
            @Override
            public void onTick(long millisUntilFinished) {
                final int secondsRemaining = (int) (millisUntilFinished / 1000);
                log(" secondsRemaining = " + secondsRemaining);
                mSecurityMessageDisplay.setMessage(R.string.fp_too_many_failed_attempts_countdown,
                        true, secondsRemaining);
            }

            @Override
            public void onFinish() {
                Settings.System.putInt(getContentResolver(), "PIN_INPUT_TIMEOUT", 0);
                /** SPRD: Modified for bug 617839 @{ */
                if (mActiveQuality == DevicePolicyManager.PASSWORD_QUALITY_SOMETHING) {
                    mLockPatternView.setEnabled(true);
                } else {
                    mText.setEnabled(true);
                    mConfirmButton.setEnabled(true);
                }
                /** @} */
                // displayDefaultSecurityMessage();
            }
        }.start();
    }

    private void showError(CharSequence error) {
        fingerprintIconIV.setImageResource(R.drawable.ic_fingerprint_error);
        // mText.setText(error);
        mText.removeCallbacks(mResetErrorTextRunnable);
        mText.postDelayed(mResetErrorTextRunnable, UNMATCH_DIALOG_NORMAL_TIMEOUT);
    }

    private Runnable mResetErrorTextRunnable = new Runnable() {
        @Override
        public void run() {
            // mText.setText("");
            fingerprintIconIV.setImageResource(R.drawable.ic_fingerprint);
        }
    };

    private FingerprintManager.AuthenticationCallback mFingerprintAuthCallback = new FingerprintManager.AuthenticationCallback() {
        @Override
        public void onAuthenticationFailed() {
            // handleFingerprintAuthFailed();
            mNumWrongConfirmAttempts++;
            log(" onAuthenticationFailed, failedTimes: " + mNumWrongConfirmAttempts);
            String msg = getString(R.string.msg_finger_not_match);
            MyToast(msg);
            showError(msg);
        }

        @Override
        public void onAuthenticationSucceeded(AuthenticationResult result) {
            // handleFingerprintAuthenticated();
            fingerprintIconIV.setImageResource(R.drawable.ic_fingerprint_success);
            log(" onAuthenticationSucceeded, result = " + result);
            boolean isScreenOn = mPowerManager.isScreenOn();
            log(" onAuthenticationSucceeded, isScreenOn = " + isScreenOn);
            if (isScreenOn) {
                mNumWrongConfirmAttempts = 0;
                unLock();
                moveToCaller();
                finish();
            }
        }

        @Override
        public void onAuthenticationHelp(int helpMsgId, CharSequence helpString) {
            // handleFingerprintHelp(helpMsgId, helpString.toString());
            log(" onAuthenticationHelp");
        }

        @Override
        public void onAuthenticationError(int errMsgId, CharSequence errString) {
            // handleFingerprintError(errMsgId, errString.toString());
            if (errMsgId != FingerprintManager.FINGERPRINT_ERROR_CANCELED) {
                showError(errString);
            }
            log(" onAuthenticationError, errMsgId = " + errMsgId + ", errString = " + errString);
            // because on Android M the max fingerprint try times is: 5
            if (errMsgId == FingerprintManager.FINGERPRINT_ERROR_LOCKOUT) {
                mSecurityMessageDisplay.setTimeout(0);
                MyToast(errString.toString());
                mSecurityMessageDisplay.setTimeout(AppLockMessageArea.SECURITY_MESSAGE_DURATION);
            }
        }

        @Override
        public void onAuthenticationAcquired(int acquireInfo) {
            // handleFingerprintAcquired(acquireInfo);
            log(" onAuthenticationAcquired, acquireInfo = " + acquireInfo);
        }
    };

    @Override
    public void onCredentialChecked(boolean matched, Intent intent, int timeoutMs,
            int effectiveUserId, boolean newResult) {
        log(" onCredentialChecked...");
        onPatternChecked(matched, intent, timeoutMs, effectiveUserId, newResult);

    }

    private void onPatternChecked(boolean matched, Intent intent, int timeoutMs,
            int effectiveUserId, boolean newResult) {
        log(" onPatternChecked, matched = " + matched);
        log(" onPatternChecked, effectiveUserId = " + effectiveUserId);
        log(" onPatternChecked, newResult = " + newResult);
        log(" onPatternChecked, timeoutMs = " + timeoutMs);
        mLockPatternView.setEnabled(true);
        if (matched) {
            Settings.System.putInt(getContentResolver(), "PIN_INPUT_TIMEOUT", 0);
            if (newResult) {
                // reportSuccessfullAttempt();
            }
            startDisappearAnimation(intent);
            // checkForPendingIntent();
        } else {
            if (timeoutMs > 0) {
                // refreshLockScreen();
                long deadline = mLockPatternUtils.setLockoutAttemptDeadline(effectiveUserId,
                        timeoutMs);
                Settings.System.putInt(getContentResolver(), "PIN_INPUT_TIMEOUT", 1);
                handleAttemptLockout(deadline);
            } else {
                if (mActiveQuality == DevicePolicyManager.PASSWORD_QUALITY_SOMETHING) {
                    MyToast(getString(R.string.msg_wrong_pattern));
                }
                updateStage(Stage.NeedToUnlockWrong);
                postClearPatternRunnable();
            }
            if (newResult) {
                reportFailedAttempt();
            }
        }
    }

    protected void reportFailedAttempt() {
        mLockPatternUtils.reportFailedPasswordAttempt(mEffectiveUserId);
    }

    private void handleNext() {
        final String pwd = mText.getText().toString();
        if (pwd.length() < 4) {
            MyToast(getString(R.string.msg_pwd_length_invalid));
            mText.setText("");
            return;
        }
        final Intent intent = new Intent();
        startCheckPassword(pwd, intent);
    }

    public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
        if (EditorInfo.IME_ACTION_NEXT == actionId) {
            handleNext();
            return true;
        }
        return false;
    }

    private String validatePassword(String password) {
        if (password.length() > mPasswordMaxLength) {
            return getString(mIsAlpha ? R.string.msg_password_too_long
                    : R.string.msg_pin_too_long, mPasswordMaxLength + 1);
        }
        return null;
    }

    private void updateUI() {
        String password = mText.getText().toString();
        String error = validatePassword(password);
        if (error != null) {
            MyToast(error);
            mConfirmButton.setEnabled(false);
        } else {
            mConfirmButton.setEnabled(true);
        }
    }

    final TextWatcher mTextWatcher = new TextWatcher() {
        public void beforeTextChanged(CharSequence s, int start, int count, int after) {
        }

        public void onTextChanged(CharSequence s, int start, int before, int count) {
        }

        public void afterTextChanged(Editable s) {
            log(" afterTextChanged and updateUI... ");
            updateUI();
        }
    };
}
