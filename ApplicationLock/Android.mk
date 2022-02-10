LOCAL_PATH:= $(call my-dir)
include $(CLEAR_VARS)

LOCAL_SRC_FILES := $(call all-java-files-under, src)
LOCAL_INIT_RC := init.ApplicationLock.rc
LOCAL_PACKAGE_NAME := ApplicationLock
LOCAL_PRIVATE_PLATFORM_APIS := true
LOCAL_CERTIFICATE := platform
LOCAL_PROGUARD_ENABLED:= disabled
LOCAL_RESOURCE_DIR := \
        $(LOCAL_PATH)/res
include $(BUILD_PACKAGE)
include $(call all-makefiles-under,$(LOCAL_PATH))
