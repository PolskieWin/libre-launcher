LOCAL_PATH := $(call my-dir)

include $(CLEAR_VARS)
LOCAL_MODULE := shaderc_shared
LOCAL_MODULE_FILENAME := libshaderc
LOCAL_SRC_FILES := shaderc_export.cpp shaderc_lwj343_compat.cpp
LOCAL_WHOLE_STATIC_LIBRARIES := shaderc
LOCAL_LDLIBS := -llog
include $(BUILD_SHARED_LIBRARY)

$(call import-module,third_party/shaderc)
