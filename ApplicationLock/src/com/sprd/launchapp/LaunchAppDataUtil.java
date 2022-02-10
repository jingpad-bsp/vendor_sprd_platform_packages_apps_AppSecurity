package com.sprd.launchapp;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.Map.Entry;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlSerializer;
import org.xmlpull.v1.XmlPullParserException;

import android.app.Activity;
import android.app.ActivityManager;
import android.app.Service;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.hardware.display.DisplayManager;
import android.os.Environment;
import android.text.TextUtils;
import android.util.Log;
import android.util.Xml;

public class LaunchAppDataUtil {

    /** multiple user the User XML file need customize */
    public static final boolean USE_LOCAL_XML = true;
    private static final String TAG = "LaunchAppDataUtil";

    public static final int DEFAULT_VISIBLE_ITEM = 30;
    
    /**
     * the APP that not need to display on launchapp activity
     */
    public static final String PACKAGE_SETTINGS = "com.android.settings";
    public static final String PACKAGE_DOWNLOAD = "com.android.providers.downloads.ui";
    public static final String PACKAGE_DIALER = "com.android.dialer";
    //add for bug 596267 begin
    public static final String PACKAGE_CONTACTS = "com.android.contacts";
    //add for bug 596267 end

    public static final String PACKAGE_CAMERA = "com.android.camera2";
    public static final String PACKAGE_DESKCLOCK = "com.android.deskclock";
    private static final String PATH_USER_INFO_DIRECTORY = "/data/sprd/";
    /** preference for saving the selected APP info for corresponding finger */
    public static final String PREFERENCE_APP_LAUNCH = "appLaunchForFinger.xml";
    /**
     * preference for saving the switch state of the APP for launch
     */
    public static final String PREFERENCE_APP_LAUNCH_SWITCH = "appLaunchSwitchStatus.xml";

    private static final String XML_DOC_TAG_MAP = "map";
    private static final String XML_DOC_TAG_INT = "int";
    private static final String XML_DOC_TAG_STRING = "string";
    private static final String XML_DOC_TAG_ATTRIBUTE_NAME = "name";
    private static final String XML_DOC_TAG_ATTRIBUTE_VALUE = "value";
    
    private static final String FP_DATA_DIR = "launchappdata";
    
    private static HashMap<String, String> appLaunchForFingerMap = null;
    private static HashMap<String, Integer> appLaunchSwitchStatusMap = null;
    private static Context mContext;
    private static File appLaunchfile;
    private static File appLaunchswitchfile;

    //public static void init(Context context) 
    static {
        //mContext = context;
        if (appLaunchSwitchStatusMap == null) {
            appLaunchSwitchStatusMap = new HashMap<String, Integer>();
        }
        if (appLaunchForFingerMap == null) {
            appLaunchForFingerMap = new HashMap<String, String>();
        }
        appLaunchForFingerMap.clear();
        appLaunchSwitchStatusMap.clear();

       int userId = ActivityManager.getCurrentUser();
        //final File systemDir = Environment.getUserSystemDirectory(userId);
        appLaunchfile = new File(PATH_USER_INFO_DIRECTORY, FP_DATA_DIR+ "/" + userId +"/"+PREFERENCE_APP_LAUNCH);
        appLaunchswitchfile = new File(PATH_USER_INFO_DIRECTORY, FP_DATA_DIR+ "/" + userId + "/"+PREFERENCE_APP_LAUNCH_SWITCH);
        boolean appLaunchFileInitResult = initDataFile(appLaunchfile);
        boolean appLaunchswitchFileInitResult = initDataFile(appLaunchswitchfile);
        log(" init, selAppFileInitResult: " + appLaunchFileInitResult
                + ", switchStateFileInitResult: " + appLaunchswitchFileInitResult);

        try {
            parseSwitchStatusXmlFromLocal(appLaunchSwitchStatusMap, appLaunchswitchfile);
            parseStringTagXmlFromLocal(appLaunchForFingerMap, appLaunchfile);
        } catch (Exception e) {
            // e.printStackTrace();
            Log.e(TAG, " init Info error, " + e);
        }
    }

        /**
     * create the file parentDirectory and file
     * @param filePath the file path which need to create
     * @return
     */
    public static boolean initDataFile(File file) {
        //File file = new File(filePath);
        if (!file.exists()) {
            try {
                File parentDirectory = file.getParentFile();
                if (parentDirectory != null && !parentDirectory.exists()) {
                    parentDirectory.mkdirs();
                    //parentDirectory.setReadable(true, false);
                    //parentDirectory.setWritable(true, false);
                    //parentDirectory.setExecutable(true, false);
                }
                boolean result = file.createNewFile();
                //result = file.setReadable(true, false);
                //result = file.setWritable(true, false);
                //result = file.setExecutable(true, false);
                return result;
            } catch (IOException e) {
                // e.printStackTrace();
                Log.e(TAG, " initDataFile, "
                        + "File create failed, " + e);
                return false;
            }
            //return false;
        }
        return true;
    }

    private static HashMap<String, Integer> parseSwitchStatusXmlFromLocal(
            HashMap<String, Integer> map, File file) throws Exception {
       // File file = new File(filePath);
        if (!file.exists()) {
            Log.e(TAG," parseSwitchStatusXmlFromLocal file not exists");
            return null;
        }
        FileInputStream fis = null;
        try{
            fis = new FileInputStream(file);
            XmlPullParser parser = Xml.newPullParser();
            parser.setInput(fis, "UTF-8");
            int eventType = parser.getEventType();
            while (eventType != XmlPullParser.END_DOCUMENT) {
                String tagName = parser.getName();
                switch (eventType) {
                case XmlPullParser.START_TAG:
                    if (XML_DOC_TAG_MAP.equals(tagName)) {
                        // userList = new ArrayList<UserInfo>();
                    } else if (XML_DOC_TAG_INT.equals(tagName)) {
                        String name = parser.getAttributeValue(null,
                                XML_DOC_TAG_ATTRIBUTE_NAME);
                        String valueStr = parser.getAttributeValue(null,
                                XML_DOC_TAG_ATTRIBUTE_VALUE);
                        int value = 0;
                        if (TextUtils.isDigitsOnly(valueStr)) {
                            value = Integer.valueOf(valueStr);
                        }
                        map.put(name, value);
                    }
                    break;
                case XmlPullParser.END_TAG:
                    if (XML_DOC_TAG_STRING.equals(tagName)) {
                        Log.e(TAG, " parseSwitchStatusXmlFromLocal"
                                + " end one tag.");
                    }
                    break;
                default:
                    break;
                }
                eventType = parser.next();
            }
        } catch (IOException e) {
            Log.e(TAG, "Error in fetching: " + e);
        } catch (XmlPullParserException e) {
            Log.e(TAG, "Error in parsing: " + e);
        } finally {
            if (fis != null) {
                try {
                    fis.close();
                } catch (IOException e) {
                    Log.e(TAG,  "Error in close file: " + e);
                }
            }
        }
        return map;
    }

    private static HashMap<String, String> parseStringTagXmlFromLocal(
            HashMap<String, String> map, File file) throws Exception {
        //File file = new File(filePath);
        if (!file.exists()) {
            Log.e(TAG," parseStringTagXmlFromLocal file not exists ");
            return null;
        }
        FileInputStream fis = null;
        try{
            fis = new FileInputStream(file);
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
                        String name = parser.getAttributeValue(null,
                                XML_DOC_TAG_ATTRIBUTE_NAME);
                        String text = parser.nextText();
                        map.put(name, text);
                    }
                    break;
                case XmlPullParser.END_TAG:
                    if (XML_DOC_TAG_STRING.equals(tagName)) {
                        Log.e(TAG, " parseStringTagXmlFromLocal"
                                + " end one tag.");
                    }
                    break;
                default:
                    break;
                }
                eventType = parser.next();
            }
        } catch (IOException e) {
            Log.e(TAG, "Error in fetching: " + e);
        } catch (XmlPullParserException e) {
            Log.e(TAG, "Error in parsing: " + e);
        } finally {
            if (fis != null) {
                try {
                    fis.close();
                } catch (IOException e) {
                    Log.e(TAG, "Error in close file: " + e);
                }
            }
        }
        return map;
    }

    private static boolean saveStringTagMapToXml(Context context,
            HashMap<String, String> map, File file) {
        log(" saveStringTagMapToXml, ");
        //File file = new File(filePath);
        if (!file.exists()) {
            try {
                file.getParentFile().mkdirs();
                file.createNewFile();
            } catch (IOException e) {
                // e.printStackTrace();
                Log.e(TAG, " saveStringTagMapToXml, "
                        + "File create failed, " + e);
                return false;
            }
        }
        OutputStream out = null;
        try {
            out = new FileOutputStream(file);
            return writeDataToStringTagXml(map, out);
        } catch (FileNotFoundException e) {
            // e.printStackTrace();
            Log.e(TAG, " saveStringTagMapToXml, "
                    + "FileNotFoundException, " + e);
            return false;
        } catch (Exception e) {
            // e.printStackTrace();
            Log.e(TAG, " saveStringTagMapToXml, "
                    + "occurs error, " + e);
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


    
    private static boolean writeDataToStringTagXml(HashMap<String, String> map,
            OutputStream out) {
        if (map == null || map.size() == 0) {
            Log.e(TAG, " writeDataToStringTagXml hashMap is empty");
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
            serializer.text(enter);
            Set<Entry<String, String>> entryset = map.entrySet();
            Iterator<Entry<String, String>> iter = entryset.iterator();
            while (iter.hasNext()) {
                Entry<String, String> iter_entry = iter.next();
                serializer.startTag(null, XML_DOC_TAG_STRING);
                serializer.attribute(null, XML_DOC_TAG_ATTRIBUTE_NAME, iter_entry.getKey());
                serializer.text(iter_entry.getValue());
                serializer.endTag(null, XML_DOC_TAG_STRING);
                serializer.text(enter);
            }
            serializer.endTag(null, XML_DOC_TAG_MAP);
            serializer.endDocument();
            out.flush();
            out.close();
            return true;
        } catch (Exception e) {
            Log.e(TAG, " writeDataToStringTagXml occurs exception, " + e);
            return false;
        }
    }

    private static boolean saveIntTagMapToXml(Context context,
            HashMap<String, Integer> map, File file) {
        log(" saveIntTagMapToXml, ");
        if (!file.exists()) {
            try {
                file.getParentFile().mkdirs();
                file.createNewFile();
            } catch (IOException e) {
                // e.printStackTrace();
                Log.e(TAG, " saveIntTagMapToXml, "
                        + "File create failed, " + e);
                return false;
            }
        }
        OutputStream out = null;
        try {
            out = new FileOutputStream(file);
            return writeDataToIntTagXml(map, out);
        } catch (FileNotFoundException e) {
            // e.printStackTrace();
            Log.e(TAG, " saveIntTagMapToXml, "
                    + "FileNotFoundException, " + e);
            return false;
        } catch (Exception e) {
            // e.printStackTrace();
            Log.e(TAG, " saveIntTagMapToXml, "
                    + "occurs error, " + e);
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
        
    private static boolean writeDataToIntTagXml(HashMap<String, Integer> map,
            OutputStream out) {
        if (map == null || map.size() == 0) {
            Log.e(TAG, " writeDataToIntTagXml hashMap is empty");
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
            serializer.text(enter);
            Set<Entry<String, Integer>> entryset = map.entrySet();
            Iterator<Entry<String, Integer>> iter = entryset.iterator();
            while (iter.hasNext()) {
                Entry<String, Integer> iter_entry = iter.next();
                serializer.startTag(null, XML_DOC_TAG_INT);
                serializer.attribute(null, XML_DOC_TAG_ATTRIBUTE_NAME, iter_entry.getKey());
                serializer.attribute(null, XML_DOC_TAG_ATTRIBUTE_VALUE, iter_entry.getValue() + "");
                serializer.endTag(null, XML_DOC_TAG_INT);
                serializer.text(enter);
            }
            serializer.endTag(null, XML_DOC_TAG_MAP);
            serializer.endDocument();
            out.flush();
            out.close();
            return true;
        } catch (Exception e) {
            Log.e(TAG, " writeDataToIntTagXml occurs exception, " + e);
            return false;
        }
    }

    private static void log(String msg) {
        //if (FpApplicationConfig.DEBUG) {
            Log.d(TAG, " " + msg);
        //}
    }

    /**
     * get the select APP info
     * 
     * @param context
     * @param fingerIndex
     *            the Index of the finger Info
     * @return the class name that saved before, otherwise return null
     */
    public static String getSelectAppInfo(Context context, int fingerIndex) {
        String result = "";
        if (USE_LOCAL_XML) {
            if (appLaunchForFingerMap.containsKey(fingerIndex + "")) {
                result = appLaunchForFingerMap.get(fingerIndex + "");
            }
        } else {
            SharedPreferences sp = context.getSharedPreferences(
                    LaunchAppDataUtil.PREFERENCE_APP_LAUNCH, Activity.MODE_PRIVATE);
            result = sp.getString(fingerIndex + "", "");
        }
        return result;
    }

    /**
     * delete the select APP info
     * 
     * @param context
     * @param fingerIndex
     *            which fingerIndex selected APP info want to delete
     * @return true, means delete success, otherwise delete failed
     */
    public static boolean deleteSelectAppInfo(Context context, int fingerIndex) {
        boolean result = false;
        if (USE_LOCAL_XML) {
            appLaunchForFingerMap.put(fingerIndex + "", "");
            result = saveStringTagMapToXml(context, appLaunchForFingerMap,
                    appLaunchfile);
        } else {
            SharedPreferences sp = context.getSharedPreferences(
                    LaunchAppDataUtil.PREFERENCE_APP_LAUNCH, Activity.MODE_PRIVATE);
            SharedPreferences.Editor editor = sp.edit();
            editor.putString(fingerIndex + "", "");
            result = editor.commit();
        }
        log(" deleteSelectAppInfo fpInfoIndex: " + fingerIndex
                + ", --remove result: " + result);
        return result;
    }

    /**
     * save the selected APP info with fingerIndex
     * 
     * @param context
     * @param fingerInfoIndex
     *            which fingerIndex selected APP info want to save
     * @param className
     * @return true, means save success, otherwise save failed
     */
    public static boolean setSelectAppInfo(Context context,
            int fingerInfoIndex, String className) {
        boolean result = false;
        if (USE_LOCAL_XML) {
            appLaunchForFingerMap.put(fingerInfoIndex + "", className);
            result = saveStringTagMapToXml(context, appLaunchForFingerMap,
                    appLaunchfile);
        } else {
            SharedPreferences sp = context.getSharedPreferences(
                    LaunchAppDataUtil.PREFERENCE_APP_LAUNCH, Activity.MODE_PRIVATE);
            SharedPreferences.Editor editor = sp.edit();
            editor.putString(fingerInfoIndex + "", className);
            result = editor.commit();
        }
        return result;
    }

    /**
     * @param context
     * @param fingerIndex
     *            the corresponding finger index
     * @return the state of launchAPP switch, true or 1 means the switch is ON,
     *         otherwise is OFF
     */
    public static boolean getAppLaunchSwitchState(Context context,
            int fingerIndex) {
        boolean state = false;
        if (USE_LOCAL_XML) {
            if (appLaunchSwitchStatusMap.containsKey(fingerIndex + "")) {
                int swi = appLaunchSwitchStatusMap.get(fingerIndex + "");
                    if(swi > 0){
                        state = true;
                    }
            }
        } else {
            SharedPreferences sp = context.getSharedPreferences(
                LaunchAppDataUtil.PREFERENCE_APP_LAUNCH_SWITCH, Activity.MODE_PRIVATE);
            state = sp.getBoolean(fingerIndex + "", false);
        }
        return state;
    }

    /**
     * @param context
     * @param fingerIndex
     *            the corresponding finger index
     * @return true means delete the corresponding finger launchAPP switch state
     *         success, otherwise delete failed
     */
    public static boolean deleteAppLaunchSwitchState(Context context,
            int fingerIndex) {
        boolean result = false;
        if (USE_LOCAL_XML) {
            appLaunchSwitchStatusMap.put(fingerIndex + "", 0);
            result = saveIntTagMapToXml(context, appLaunchSwitchStatusMap,
                    appLaunchswitchfile);
        } else {
            SharedPreferences sp = context.getSharedPreferences(
                LaunchAppDataUtil.PREFERENCE_APP_LAUNCH_SWITCH, Activity.MODE_PRIVATE);
            SharedPreferences.Editor editor = sp.edit();
            editor.putBoolean(fingerIndex + "", false);
            result = editor.commit();
        }
        log(" deleteAppLaunchSwitchState fpIndex: " + fingerIndex
                + ", --result: " + result);
        return result;
    }

    public static void setAppLaunchSwitchState(Context context, int fingerIndex, boolean state) {
        Log.i(TAG, " saveSwitchState fpIndex: " + fingerIndex
                + " --state: " + state);
        boolean result = false;
        if (USE_LOCAL_XML) {
            int on = 0;
            if (state){
                on = 1;
            }
            appLaunchSwitchStatusMap.put(fingerIndex + "", on);
            result = saveIntTagMapToXml(context, appLaunchSwitchStatusMap,
                    appLaunchswitchfile);
        } else {
            SharedPreferences sp = context.getSharedPreferences(
                    LaunchAppDataUtil.PREFERENCE_APP_LAUNCH_SWITCH,  Activity.MODE_PRIVATE);
            SharedPreferences.Editor editor = sp.edit();
            editor.putBoolean(fingerIndex + "", state);
            editor.commit();
        }
    }

}
