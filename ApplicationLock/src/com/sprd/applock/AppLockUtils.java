
package com.sprd.applock;

import android.app.ActivityManager;
import android.app.ActivityManager.RunningTaskInfo;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.text.TextUtils;
import android.util.Log;
import android.util.Xml;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlSerializer;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map.Entry;
import java.util.Set;

public class AppLockUtils {

    public static final String TAG_LOG = "ApplicationLock";
    public static final boolean DEBUG = false;
    private static final String TAG = "Utils";
    private static final byte[] lock = new byte[0];
    private static HashMap<String, String> lockAppMap = null;
    private static HashMap<String, Integer> hasUnlockedAppMap = null;
    private static String filePath = "";
    private static String unlockFilePath = "";
    private static final String PREFERENCE_LOCKED_APP = "LockApp";
    private static final String PREFERENCE_ALREADY_UNLOCKED_APP = "hasUnlockedApp";
    private static final String PATH_USER_INFO_DIRECTORY = "/data/sprd/applock/";
    private static final String XML_DOC_TAG_MAP = "map";
    private static final String XML_DOC_TAG_INT = "int";
    private static final String XML_DOC_TAG_STRING = "string";
    private static final String XML_DOC_TAG_ATTRIBUTE_NAME = "name";
    private static final String XML_DOC_TAG_ATTRIBUTE_VALUE = "value";
    private static final boolean USE_LOCAL_XML = true;
    private static final int FLAG_APP_UNLOCKED = 1;
    private static final int FLAG_APP_NOT_UNLOCKED = 0;

    public static final String PACKAGE_INPUT = "com.android.inputmethod.latin";
    public static final String PACKAGE_LAUNCHER = "com.android.launcher3";
    public static final String PACKAGE_SPRDLAUNCHER = "com.android.sprdlauncher3";
    public static final String PACKAGE_SETTINGS = "com.android.settings";
    public static final String PACKAGE_DOWNLOAD = "com.android.providers.downloads.ui";
    public static final String PACKAGE_DIALER = "com.android.dialer";
    public static final String PACKAGE_CONTACTS = "com.android.contacts";
    public static final String PACKAGE_CAMERA = "com.android.camera2";
    public static final String PACKAGE_DESKCLOCK = "com.android.deskclock";
    public static final String PACKAGE_MUSIC = "com.android.music";
    public static final String PACKAGE_ASPIRE = "com.aspire.popular";
    public static final String PACKAGE_ASPIRE_SIMILAR = "com.aspire.tmmhelper.ui";
    public static final String PACKAGE_STEREO_CAM_CAL = "com.example.stereo_cam_calibration";
    public static final String PACKAGE_SPREADST_VALIDDATE = "com.spreadst.validdate";
    public static final String PACKAGE_SPRD_VALIDATIONTOOLS = "com.sprd.validationtools";
    public static final String PACKAGE_SPRD_AUTOSLT = "com.sprd.autoslt";
    public static final String PACKAGE_OMA_SMARTCARD_SERVICE = "org.simalliance.openmobileapi.service";
    public static final String PACKAGE_OMA_UICC_TERMINAL = "org.simalliance.openmobileapi.uiccterminal";
    public static final String PACKAGE_INSTALLER = "com.android.packageinstaller";
    public static final String PACKAGE_MYSELF = "com.sprd.applock";
    public static final String PACKAGE_GMS = "com.google.android.gms";
    public static final String EXTRA_KEY_TYPE = "crypt_type";
    public static final String EXTRA_KEY_PASSWORD = "password";

    static {
        if (hasUnlockedAppMap == null) {
            hasUnlockedAppMap = new HashMap<String, Integer>();
        }
        hasUnlockedAppMap.clear();
        if (lockAppMap == null) {
            lockAppMap = new HashMap<String, String>();
        }
        lockAppMap.clear();
        int userId = ActivityManager.getCurrentUser();
        String pathPrefix = PATH_USER_INFO_DIRECTORY + userId + "/";
        Log.d(TAG_LOG, TAG + "init, pathPrefix: " + pathPrefix);
        filePath = pathPrefix + PREFERENCE_LOCKED_APP;
        unlockFilePath = pathPrefix + PREFERENCE_ALREADY_UNLOCKED_APP;
        boolean unlockFileInitResult = initDataFile(unlockFilePath);
        boolean lockAppFileInitResult = initDataFile(filePath);
        Log.d(TAG_LOG, TAG + "init and lockAppFileInitResult: " + lockAppFileInitResult
                + " and unlockFileInitResult: " + unlockFileInitResult);
        try {
            parseLockAppXmlFromLocal(lockAppMap, filePath);
            parseUnlockedAppXmlFromLocal(hasUnlockedAppMap, unlockFilePath);
        } catch (Exception e) {
            Log.e(TAG_LOG, TAG + " init lock app info error, " + e);
        }
    }

    public static boolean isAppNeedtoFilter(String packageName) {
        boolean ret = false;
        if (PACKAGE_SETTINGS.equals(packageName) || PACKAGE_INPUT.equals(packageName)
                || PACKAGE_DOWNLOAD.equals(packageName) || PACKAGE_LAUNCHER.equals(packageName)
                || PACKAGE_SPRDLAUNCHER.equals(packageName)
                || PACKAGE_OMA_SMARTCARD_SERVICE.equals(packageName)
                || PACKAGE_SPREADST_VALIDDATE.equals(packageName)
                || PACKAGE_OMA_UICC_TERMINAL.equals(packageName)
                || PACKAGE_STEREO_CAM_CAL.equals(packageName)
                || PACKAGE_SPRD_VALIDATIONTOOLS.equals(packageName)
                || PACKAGE_SPRD_AUTOSLT.equals(packageName)
                || PACKAGE_CAMERA.equals(packageName)
                || PACKAGE_GMS.equals(packageName)
                || PACKAGE_MYSELF.equals(packageName)
                || PACKAGE_DIALER.equals(packageName)
                || PACKAGE_DESKCLOCK.equals(packageName)) {
            Log.d(TAG_LOG, TAG + " this APP " + packageName
                    + " filter out, so don't need show locked activity!");
            ret = true;
        }
        return ret;
    }

    private static HashMap<String, Integer> parseUnlockedAppXmlFromLocal(
            HashMap<String, Integer> map, String path) throws Exception {
        File file = new File(path);
        if (!file.exists()) {
            Log.e(TAG_LOG, TAG + " parseUnlockedAppXmlFromLocal file not exists");
            return null;
        }
        FileInputStream fis = new FileInputStream(file);
        XmlPullParser parser = Xml.newPullParser();
        parser.setInput(fis, "UTF-8");
        int eventType = parser.getEventType();
        while (eventType != XmlPullParser.END_DOCUMENT) {
            String tagName = parser.getName();
            switch (eventType) {
                case XmlPullParser.START_TAG:
                    if (XML_DOC_TAG_MAP.equals(tagName)) {

                    } else if (XML_DOC_TAG_INT.equals(tagName)) {
                        String name = parser.getAttributeValue(null, XML_DOC_TAG_ATTRIBUTE_NAME);
                        String valueStr = parser.getAttributeValue(null,
                                XML_DOC_TAG_ATTRIBUTE_VALUE);
                        int value = 0;
                        if (!TextUtils.isEmpty(valueStr)) {
                            value = Integer.valueOf(valueStr);
                        }
                        map.put(name, value);
                    }
                    break;
                case XmlPullParser.END_TAG:
                    if (XML_DOC_TAG_INT.equals(tagName)) {
                        Log.e(TAG_LOG, TAG + " parseUnlockedAppXmlFromLocal" + " end one tag.");
                    }
                    break;
                default:
                    break;
            }
            eventType = parser.next();
        }
        fis.close();
        return map;
    }

    private static HashMap<String, String> parseLockAppXmlFromLocal(HashMap<String, String> map,
            String path) throws Exception {
        File file = new File(path);
        if (!file.exists()) {
            Log.e(TAG_LOG, TAG + " parseLockAppXmlFromLocal file not exists");
            return null;
        }
        FileInputStream fis = new FileInputStream(file);
        XmlPullParser parser = Xml.newPullParser();
        parser.setInput(fis, "UTF-8");
        int eventType = parser.getEventType();
        while (eventType != XmlPullParser.END_DOCUMENT) {
            String tagName = parser.getName();
            switch (eventType) {
                case XmlPullParser.START_TAG:
                    if (XML_DOC_TAG_MAP.equals(tagName)) {
                        // userList = new ArrayList<UserInfo>();
                    } else if (XML_DOC_TAG_STRING.equals(tagName)) {
                        String name = parser.getAttributeValue(null, XML_DOC_TAG_ATTRIBUTE_NAME);
                        String text = parser.nextText();
                        map.put(name, text);
                    }
                    break;
                case XmlPullParser.END_TAG:
                    if (XML_DOC_TAG_STRING.equals(tagName)) {
                        Log.e(TAG_LOG, TAG + " parseLockAppXmlFromLocal end one tag.");
                    }
                    break;
                default:
                    break;
            }
            eventType = parser.next();
        }
        fis.close();
        return map;
    }

    private static boolean initDataFile(String string) {
        File file = new File(filePath);
        if (!file.exists()) {
            try {
                File parentDirectory = file.getParentFile();
                if (parentDirectory != null && !parentDirectory.exists()) {
                    parentDirectory.mkdirs();
                }
                boolean result = file.createNewFile();
                return result;
            } catch (IOException e) {
                Log.e(TAG_LOG, TAG + " initDataFile, " + "File create failed, " + e);
                return false;
            }

        }
        return true;
    }

    public static boolean setAPPLockedFlag(Context context, ApplicationInfo info, boolean flag) {
        synchronized (lock) {
            boolean result = false;
            String packageName = info.packageName;
            // int uid = info.uid;
            if (USE_LOCAL_XML) {
                if (flag) {
                    lockAppMap.put(packageName, packageName);
                    // lockAppMap.put(uid, uid);
                    if (PACKAGE_CONTACTS.equals(packageName)) {
                        lockAppMap.put(PACKAGE_DIALER, PACKAGE_DIALER);
                    }
                } else {
                    lockAppMap.put(packageName, "");
                    if (PACKAGE_CONTACTS.equals(packageName)) {
                        lockAppMap.put(PACKAGE_DIALER, "");
                    }
                    AppLockUtils.clearAppAlreadyUnlockedFlag(context, packageName);
                }
                result = saveLockedAppMapToXml(context, lockAppMap);
            } else {
                SharedPreferences mLockAppPreferences = context.getSharedPreferences(
                        PREFERENCE_LOCKED_APP, Context.MODE_PRIVATE);
                SharedPreferences.Editor editor = mLockAppPreferences.edit();
                if (flag) {
                    editor.putString(packageName, packageName);
                    if (PACKAGE_CONTACTS.equals(packageName)) {
                        editor.putString(PACKAGE_DIALER, PACKAGE_DIALER);
                    }
                } else {
                    editor.putString(packageName, "");
                    if (PACKAGE_CONTACTS.equals(packageName)) {
                        editor.putString(PACKAGE_DIALER, "");
                    }
                    AppLockUtils.clearAppAlreadyUnlockedFlag(context, packageName);
                }
                result = editor.commit();
            }
            if (DEBUG) {
                Log.d(TAG_LOG, TAG + " setAPPLockedFlag app packageName: " + packageName
                        + " -- flag: " + flag + " -- result: " + result);
            }
            return result;
        }
    }

    private static boolean saveLockedAppMapToXml(Context context, HashMap<String, String> map) {
        File file = new File(filePath);
        if (!file.exists()) {
            boolean result = initDataFile(filePath);
            if (!result) {
                Log.e(TAG_LOG, TAG + " saveLockedAppMapToXml, " + "File create failed.");
            }
        }
        OutputStream out = null;
        try {
            out = new FileOutputStream(file);
            return writeDataToLockAppXml(map, out);
        } catch (FileNotFoundException e) {
            Log.e(TAG_LOG, TAG + " saveLockedAppMapToXml, " + "FileNotFoundException, " + e);
            return false;
        } catch (Exception e) {
            Log.e(TAG_LOG, TAG + " saveLockedAppMapToXml, " + "occurs error, " + e);
            return false;
        } finally {
            if (out != null) {
                try {
                    out.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    private static boolean writeDataToLockAppXml(HashMap<String, String> map, OutputStream out) {
        boolean writeResult = false;
        if (map == null || map.size() == 0) {
            Log.e(TAG_LOG, TAG + " writeDataToLockAppXml hashMap is empty");
            return false;
        }
        try {
            String enter = System.getProperty("line.separator");
            XmlSerializer serializer = Xml.newSerializer();
            serializer.setOutput(out, "UTF-8");
            serializer.startDocument("UTF-8", true);
            // add the line separator
            serializer.text(enter);
            serializer.startTag(null, XML_DOC_TAG_MAP);
            // add the line separator
            serializer.text(enter);
            Set<Entry<String, String>> entryset = map.entrySet();
            Iterator<Entry<String, String>> iter = entryset.iterator();
            while (iter.hasNext()) {
                Entry<String, String> iter_entry = iter.next();
                serializer.startTag(null, XML_DOC_TAG_STRING);
                serializer.attribute(null, XML_DOC_TAG_ATTRIBUTE_NAME, iter_entry.getKey());
                serializer.text(iter_entry.getValue());
                serializer.endTag(null, XML_DOC_TAG_STRING);
                // add the line separator
                serializer.text(enter);
            }
            serializer.endTag(null, XML_DOC_TAG_MAP);
            serializer.endDocument();
            out.flush();
            out.close();
            writeResult = true;
        } catch (Exception e) {
            Log.e(TAG_LOG, TAG + " writeDataToLockAppXml occurs exception, " + e);
        }
        return writeResult;
    }

    public static boolean getAppLockState(Context context, ApplicationInfo info) {
        synchronized (lock) {
            String pkg = info.packageName;
            if (DEBUG) {
                Log.d(TAG_LOG, TAG + " getAppLockState pkg:" + pkg);
            }
            if (USE_LOCAL_XML) {
                if (lockAppMap == null) {
                    return false;
                }
                String packagename = lockAppMap.get(pkg);
                if (pkg.equals(packagename)) {
                    return true;
                } else {
                    return false;
                }
            } else {
                SharedPreferences mLockAppPreferences = context.getSharedPreferences(
                        PREFERENCE_LOCKED_APP, Context.MODE_PRIVATE);
                String packagename = mLockAppPreferences.getString(pkg, null);
                Log.d(TAG_LOG, TAG + " getAppLockState packagename:" + packagename);
                if (TextUtils.isEmpty(packagename)) {
                    Log.d(TAG_LOG, TAG + "packagename isEmpty");
                    return false;
                }
                if (pkg.equals(packagename)) {
                    return true;
                } else {
                    return false;
                }
            }
        }
    }

    public static boolean isAppAlreadyUnlocked(Context context, String pkg) {
        synchronized (lock) {
            if (USE_LOCAL_XML) {
                if (PACKAGE_ASPIRE_SIMILAR.equals(pkg)) {
                    pkg = PACKAGE_ASPIRE;
                }
                if (!hasUnlockedAppMap.containsKey(pkg)) {
                    Log.e(TAG_LOG, TAG + " isAppAlreadyUnlocked "
                            + "map not contains this pkgName: " + pkg);
                    return false;
                }
                int unlocked = hasUnlockedAppMap.get(pkg);
                Log.d(TAG_LOG, TAG + " isAppAlreadyUnlocked pkg = " + pkg + " -- unlocked: "
                        + unlocked);
                if (unlocked == FLAG_APP_UNLOCKED) {
                    return true;
                }
                return false;
            } else {
                if (PACKAGE_ASPIRE_SIMILAR.equals(pkg)) {
                    pkg = PACKAGE_ASPIRE;
                }
                SharedPreferences sp = context.getSharedPreferences(
                        PREFERENCE_ALREADY_UNLOCKED_APP, Context.MODE_PRIVATE);
                int unlocked = sp.getInt(pkg, 0);
                Log.d(TAG_LOG, TAG + " isAppAlreadyUnlocked pkg = " + pkg + " -- unlocked: "
                        + unlocked);
                if (unlocked == FLAG_APP_UNLOCKED) {
                    return true;
                }
                return false;
            }
        }

    }

    public static boolean isAppNeedLock(Context context, String pkg) {
        Log.d(TAG_LOG, TAG + " isAppNeedLock context = " + context + " --packageName: " + pkg
                + " size = " + lockAppMap.size());
        synchronized (lock) {
            if (USE_LOCAL_XML) {
                if (PACKAGE_ASPIRE_SIMILAR.equals(pkg)) {
                    pkg = PACKAGE_ASPIRE;
                }
                if (lockAppMap == null) {
                    return false;
                }
                String needLockedPackage = lockAppMap.get(pkg);
                Log.d(TAG_LOG, TAG + " isAppNeedLock needLockedPackage = " + needLockedPackage);
                if (needLockedPackage != null && needLockedPackage.equals(pkg)) {
                    return true;
                } else {
                    return false;
                }
            } else {
                if (PACKAGE_ASPIRE_SIMILAR.equals(pkg)) {
                    pkg = PACKAGE_ASPIRE;
                }
                SharedPreferences sp = context.getSharedPreferences(PREFERENCE_LOCKED_APP,
                        Context.MODE_PRIVATE);
                String needLockedPackage = sp.getString(pkg, "");
                Log.d(TAG_LOG, TAG + " isAppNeedLock needLockedPackage = " + needLockedPackage);
                if (needLockedPackage.equals(pkg)) {
                    return true;
                } else {
                    return false;
                }
            }
        }
    }

    public static boolean setAppAlreadyUnlockedFlag(Context context, String pkg) {
        return setAppUnlockedFlag(context, pkg, FLAG_APP_UNLOCKED);
    }

    public static boolean clearAppAlreadyUnlockedFlag(Context context, String pkg) {
        return setAppUnlockedFlag(context, pkg, FLAG_APP_NOT_UNLOCKED);
    }

    private static boolean setAppUnlockedFlag(Context context, String pkg, int flag) {
        Log.d(TAG_LOG, TAG + " setAppUnlockedFlag pkg = " + pkg + " flag = " + flag);

        synchronized (lock) {
            if (USE_LOCAL_XML) {
                if (PACKAGE_ASPIRE_SIMILAR.equals(pkg)) {
                    pkg = PACKAGE_ASPIRE;
                }
                if (PACKAGE_DIALER.equals(pkg) || PACKAGE_CONTACTS.equals(pkg)) {
                    hasUnlockedAppMap.put(PACKAGE_CONTACTS, flag);
                    hasUnlockedAppMap.put(PACKAGE_DIALER, flag);
                } else {
                    hasUnlockedAppMap.put(pkg, flag);
                }
                Log.d(TAG_LOG, TAG + " setAppUnlockedFlag pkg = " + pkg + " unlocked = " + flag);
                return saveUnlockedAppMapToXml(context, hasUnlockedAppMap);
            } else {
                if (PACKAGE_ASPIRE_SIMILAR.equals(pkg)) {
                    pkg = PACKAGE_ASPIRE;
                }
                SharedPreferences sp = context.getSharedPreferences(
                        PREFERENCE_ALREADY_UNLOCKED_APP, Context.MODE_PRIVATE);
                SharedPreferences.Editor editor = sp.edit();
                int unlocked = sp.getInt(pkg, 0);
                Log.d(TAG_LOG, TAG + " setAppUnlockedFlag pkg = " + pkg + " unlocked = " + unlocked);
                if (PACKAGE_DIALER.equals(pkg) || PACKAGE_CONTACTS.equals(pkg)) {
                    int unlocked_dialer = sp.getInt(pkg, 0);
                    int unlocked_contacts = sp.getInt(pkg, 0);
                    if (unlocked_dialer != 0) {
                        editor.remove(PACKAGE_DIALER);
                    }
                    if (unlocked_contacts != 0) {
                        editor.remove(PACKAGE_CONTACTS);
                    }
                    editor.putInt(PACKAGE_DIALER, flag);
                    editor.putInt(PACKAGE_CONTACTS, flag);
                    return editor.commit();
                } else {
                    if (unlocked != 0) {
                        editor.remove(pkg);
                    }
                    editor.putInt(pkg, flag);
                    return editor.commit();
                }
            }
        }

    }

    private static boolean saveUnlockedAppMapToXml(Context context, HashMap<String, Integer> map) {
        File file = new File(unlockFilePath);
        if (!file.exists()) {
            boolean result = initDataFile(unlockFilePath);
            if (!result) {
                Log.e(TAG_LOG, TAG + " saveUnlockedAppMapToXml, File create failed.");
            }
        }
        OutputStream out = null;
        try {
            out = new FileOutputStream(file);
            return writeDataToUnlockedAppXml(map, out);
        } catch (FileNotFoundException e) {
            Log.e(TAG_LOG, TAG + " saveUnlockedAppMapToXml, " + "FileNotFoundException, " + e);
            return false;
        } catch (Exception e) {
            Log.e(TAG_LOG, TAG + " saveUnlockedAppMapToXml, " + "occurs error, " + e);
            return false;
        } finally {
            if (out != null) {
                try {
                    out.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    private static boolean writeDataToUnlockedAppXml(HashMap<String, Integer> map, OutputStream out) {
        boolean writeResult = false;
        if (map == null || map.size() == 0) {
            Log.e(TAG_LOG, TAG + " writeDataToUnlockedAppXml hashMap is empty");
            return false;
        }
        try {
            String enter = System.getProperty("line.separator");
            XmlSerializer serializer = Xml.newSerializer();
            serializer.setOutput(out, "UTF-8");
            serializer.startDocument("UTF-8", true);
            // add the line separator
            serializer.text(enter);
            serializer.startTag(null, XML_DOC_TAG_MAP);
            // add the line separator
            serializer.text(enter);
            Set<Entry<String, Integer>> entryset = map.entrySet();
            Iterator<Entry<String, Integer>> iter = entryset.iterator();
            while (iter.hasNext()) {
                Entry<String, Integer> iter_entry = iter.next();
                serializer.startTag(null, XML_DOC_TAG_INT);
                serializer.attribute(null, XML_DOC_TAG_ATTRIBUTE_NAME, iter_entry.getKey());
                serializer.attribute(null, XML_DOC_TAG_ATTRIBUTE_VALUE, iter_entry.getValue()
                        .toString());
                serializer.endTag(null, XML_DOC_TAG_INT);
                // add the line separator
                serializer.text(enter);
            }
            serializer.endTag(null, XML_DOC_TAG_MAP);
            serializer.endDocument();
            out.flush();
            out.close();
            writeResult = true;
        } catch (Exception e) {
            Log.e(TAG_LOG, TAG + " writeDataToUnlockedAppXml occurs exception, " + e);
        }
        return writeResult;
    }

    public static void clearAllAlreadyUnlockedFlag(Context context) {
        synchronized (lock) {
            if (USE_LOCAL_XML) {
                hasUnlockedAppMap.clear();
                boolean result = deleteXmlFile(unlockFilePath);
            } else {
                context.getSharedPreferences(PREFERENCE_ALREADY_UNLOCKED_APP, Context.MODE_PRIVATE)
                        .edit().clear().commit();
            }
        }
    }

    public static void clearAllLockAppFlag(Context context) {
        synchronized (lock) {
            if (USE_LOCAL_XML) {
                lockAppMap.clear();
                boolean result = deleteXmlFile(filePath);
            } else {
                context.getSharedPreferences(PREFERENCE_LOCKED_APP, Context.MODE_PRIVATE)
                        .edit().clear().commit();
            }
        }
    }

    private static boolean deleteXmlFile(String fileName) {
        File file = new File(fileName);
        if (file.exists()) {
            return file.delete();
        } else {
            return false;
        }
    }

    public static String getTopActivityPackage(Context context) {
        String runningTopPackage = "";
        String baseActivityPackage = "";
        ActivityManager am = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        List<RunningTaskInfo> runningTaskList = am.getRunningTasks(1);
        if (runningTaskList != null && !runningTaskList.isEmpty()) {
            RunningTaskInfo runningTaskInfo = runningTaskList.get(0);
            if (runningTaskInfo != null) {
                runningTopPackage = runningTaskInfo.topActivity.getPackageName();
                baseActivityPackage = runningTaskInfo.baseActivity.getPackageName();
                if (DEBUG) {
                    Log.d(TAG_LOG, TAG + " getTopActivityPackage and runningTopPackage: " + runningTopPackage);
                    Log.d(TAG_LOG, TAG + " getTopActivityPackage and baseActivityPackage: " + baseActivityPackage);
                }
            }
        }
        if (PACKAGE_INSTALLER.equals(runningTopPackage) || PACKAGE_GMS.equals(runningTopPackage)) {
            if (DEBUG) {
                Log.d(TAG_LOG, TAG + " getTopActivityPackage and baseActivityPackage is "
                        + baseActivityPackage);
            }
            return baseActivityPackage;
        } else {
            if (DEBUG) {
                Log.d(TAG_LOG, TAG + " getTopActivityPackage and runningTopPackage is "
                        + runningTopPackage);
            }
            return runningTopPackage;
        }
    }

    public static void backToCaller(Context context, String pkg, int taskId) {
        if (DEBUG) {
            Log.d(TAG_LOG, TAG + " backToCaller.pkg is " + pkg);
            Log.d(TAG_LOG, TAG + " backToCaller.taskId is " + taskId);
        }
        if (taskId >= 0) {
            ActivityManager am = (ActivityManager) context
                    .getSystemService(Context.ACTIVITY_SERVICE);
            am.moveTaskToFront(taskId, ActivityManager.MOVE_TASK_WITH_HOME);
        }
    }

    public static int getRunningTaskInfo(Context context, String pkg) {
        int taskId = -1;
        ActivityManager am = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        List<RunningTaskInfo> runningTaskList = am.getRunningTasks(ActivityManager
                .getDefaultAppRecentsLimitStatic());
        if (DEBUG) {
            Log.d(TAG_LOG, TAG + " runningTaskList.size is " + runningTaskList.size());
        }
        for (RunningTaskInfo runningTaskInfo : runningTaskList) {
            taskId = runningTaskInfo.id;
            String activityPackage = "";
            String baseActivityPackage = runningTaskInfo.baseActivity.getPackageName();
            String topActivityPackage = runningTaskInfo.topActivity.getPackageName();
            if (PACKAGE_INSTALLER.equals(topActivityPackage)
                    || PACKAGE_GMS.equals(topActivityPackage)) {
                activityPackage = baseActivityPackage;
            } else {
                activityPackage = topActivityPackage;
            }
            if (DEBUG) {
                Log.d(TAG_LOG, TAG + " topActivityPackage is " + topActivityPackage);
                Log.d(TAG_LOG, TAG + " baseActivityPackage is " + baseActivityPackage);
                Log.d(TAG_LOG, TAG + " taskId is " + taskId);
            }
            if (pkg != null && pkg.equals(activityPackage)) {
                break;
            }
        }
        return taskId;
    }

    public static void clearAllLockedFlag(Context context) {
        Log.d(TAG_LOG, TAG + " remove all fps and clear all flag...");
        clearAllLockAppFlag(context);
        clearAllAlreadyUnlockedFlag(context);
    }

    public static String getAppNameByPackageName(Context context, String packageName) {
        PackageManager pm = context.getPackageManager();
        String name = null;
        try {
            name = pm.getApplicationLabel(
                    pm.getApplicationInfo(packageName, PackageManager.GET_META_DATA)).toString();
        } catch (NameNotFoundException e) {
            e.printStackTrace();
        }
        return name;
    }

    /**
     * whether the App is system level.
     */
    public static boolean isSystemApp(Context context, String packageName) {
        boolean result = false;
        if (packageName != null) {
            PackageManager pm = context.getPackageManager();
            if (packageName != null) {
                try {
                    ApplicationInfo appInfo = pm.getApplicationInfo(packageName, 0);
                    if (appInfo != null) {
                        if ((appInfo.flags & ApplicationInfo.FLAG_SYSTEM) > 0) {
                            result = true;
                        }
                    }
                } catch (PackageManager.NameNotFoundException e) {
                    Log.e(TAG_LOG, TAG + " Error getting application info:", e);
                }
            }
        }
        return result;
    }
}
