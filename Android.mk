LOCAL_PATH:= $(call my-dir)
include $(CLEAR_VARS)

LOCAL_JAVA_LIBRARIES := bouncycastle
LOCAL_JAVA_LIBRARIES += com.qrd.plugin.feature_query

LOCAL_JAVA_LIBRARIES += qcnvitems
LOCAL_JAVA_LIBRARIES += qcrilhook
                         
LOCAL_STATIC_JAVA_LIBRARIES := guava android-support-v4

LOCAL_MODULE_TAGS := optional

LOCAL_SRC_FILES := $(call all-java-files-under, src)  \
  src/com/qualcomm/cabl/ICABLService.aidl


LOCAL_PACKAGE_NAME := Settings
LOCAL_CERTIFICATE := platform

LOCAL_PROGUARD_FLAG_FILES := proguard.flags

include $(BUILD_PACKAGE)

# Use the folloing include to make our test apk.
include $(call all-makefiles-under,$(LOCAL_PATH))
