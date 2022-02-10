package com.sprd.launchapp;

public class RefreshUICaller {
    RefreshStateInterface refreshCaller;

    public void refreshState() {
        refreshCaller.refreshState();
    }

    public void refreshSelectApp(int index) {
        refreshCaller.refreshSelectApp(index);
    }

    public void setRefreshUICaller(RefreshStateInterface caller) {
        refreshCaller = caller;
    }
}
