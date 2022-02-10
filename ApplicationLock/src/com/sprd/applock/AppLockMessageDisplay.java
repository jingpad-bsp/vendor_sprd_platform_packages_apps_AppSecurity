/*
 * Copyright (C) 2016 Created by SPRD.
 */

package com.sprd.applock;

public interface AppLockMessageDisplay {
    public void setMessage(CharSequence msg, boolean important);

    public void setMessage(int resId, boolean important);

    public void setMessage(int resId, boolean important, Object... formatArgs);

    public void setTimeout(int timeout_ms);
}
