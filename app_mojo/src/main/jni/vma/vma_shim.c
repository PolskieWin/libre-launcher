#include <jni.h>
#include <stdlib.h>
#include <string.h>
#include <stdint.h>
#include <stdbool.h>

#define VMA_EXPORT __attribute__((visibility("default")))

// Standard C exports for Vulkan Memory Allocator
VMA_EXPORT int vmaCreateAllocator(const void* createInfo, void** allocator) {
    (void) createInfo;
    if (allocator) *allocator = calloc(1, 16);
    return 0; // VK_SUCCESS
}

VMA_EXPORT void vmaDestroyAllocator(void* allocator) {
    if (allocator) free(allocator);
}

VMA_EXPORT void vmaGetAllocatorInfo(void* allocator, void* info) { (void) allocator; if (info) memset(info, 0, 32); }
VMA_EXPORT void vmaGetPhysicalDeviceProperties(void* allocator, const void** pp) { (void) allocator; if (pp) *pp = NULL; }
VMA_EXPORT void vmaGetMemoryProperties(void* allocator, const void** mp) { (void) allocator; if (mp) *mp = NULL; }
VMA_EXPORT void vmaGetMemoryTypeProperties(void* allocator, uint32_t typeIndex, uint32_t* flags) { (void) allocator; (void) typeIndex; if (flags) *flags = 0; }
VMA_EXPORT void vmaSetCurrentFrameIndex(void* allocator, uint32_t frameIndex) { (void) allocator; (void) frameIndex; }
VMA_EXPORT void vmaCalculateStatistics(void* allocator, void* stats) { (void) allocator; if (stats) memset(stats, 0, 32); }
VMA_EXPORT void vmaGetHeapBudgets(void* allocator, void* budgets) { (void) allocator; if (budgets) memset(budgets, 0, 32); }
VMA_EXPORT int vmaFindMemoryTypeIndex(void* allocator, uint32_t memoryTypeBits, const void* allocInfo, uint32_t* typeIndex) {
    (void) allocator; (void) memoryTypeBits; (void) allocInfo;
    if (typeIndex) *typeIndex = 0;
    return 0;
}
VMA_EXPORT int vmaFindMemoryTypeIndexForBufferInfo(void* allocator, const void* bufferInfo, const void* allocInfo, uint32_t* typeIndex) {
    (void) allocator; (void) bufferInfo; (void) allocInfo;
    if (typeIndex) *typeIndex = 0;
    return 0;
}
VMA_EXPORT int vmaFindMemoryTypeIndexForImageInfo(void* allocator, const void* imageInfo, const void* allocInfo, uint32_t* typeIndex) {
    (void) allocator; (void) imageInfo; (void) allocInfo;
    if (typeIndex) *typeIndex = 0;
    return 0;
}
VMA_EXPORT int vmaCreatePool(void* allocator, const void* createInfo, void** pool) {
    (void) allocator; (void) createInfo;
    if (pool) *pool = calloc(1, 16);
    return 0;
}
VMA_EXPORT void vmaDestroyPool(void* allocator, void* pool) {
    (void) allocator;
    if (pool) free(pool);
}
VMA_EXPORT void vmaGetPoolStatistics(void* allocator, void* pool, void* poolStats) { (void) allocator; (void) pool; if (poolStats) memset(poolStats, 0, 32); }
VMA_EXPORT void vmaCalculatePoolStatistics(void* allocator, void* pool, void* poolStats) { (void) allocator; (void) pool; if (poolStats) memset(poolStats, 0, 32); }
VMA_EXPORT int vmaCheckPoolCorruption(void* allocator, void* pool) { (void) allocator; (void) pool; return 0; }
VMA_EXPORT void vmaGetPoolName(void* allocator, void* pool, const char** name) { (void) allocator; (void) pool; if (name) *name = ""; }
VMA_EXPORT void vmaSetPoolName(void* allocator, void* pool, const char* name) { (void) allocator; (void) pool; (void) name; }

VMA_EXPORT int vmaAllocateMemory(void* allocator, const void* vkMemoryRequirements, const void* createInfo, void** allocation, void* allocInfo) {
    (void) allocator; (void) vkMemoryRequirements; (void) createInfo; (void) allocInfo;
    if (allocation) *allocation = calloc(1, 16);
    return 0;
}
VMA_EXPORT int vmaAllocateMemoryPages(void* allocator, const void* vkMemoryRequirements, const void* createInfo, size_t count, void** allocations, void* allocInfo) {
    (void) allocator; (void) vkMemoryRequirements; (void) createInfo; (void) allocInfo;
    if (allocations) {
        for (size_t i = 0; i < count; i++) allocations[i] = calloc(1, 16);
    }
    return 0;
}
VMA_EXPORT int vmaAllocateMemoryForBuffer(void* allocator, uint64_t buffer, const void* createInfo, void** allocation, void* allocInfo) {
    (void) allocator; (void) buffer; (void) createInfo; (void) allocInfo;
    if (allocation) *allocation = calloc(1, 16);
    return 0;
}
VMA_EXPORT int vmaAllocateMemoryForImage(void* allocator, uint64_t image, const void* createInfo, void** allocation, void* allocInfo) {
    (void) allocator; (void) image; (void) createInfo; (void) allocInfo;
    if (allocation) *allocation = calloc(1, 16);
    return 0;
}
VMA_EXPORT void vmaFreeMemory(void* allocator, void* allocation) {
    (void) allocator;
    if (allocation) free(allocation);
}
VMA_EXPORT void vmaFreeMemoryPages(void* allocator, size_t count, void** allocations) {
    (void) allocator;
    if (allocations) {
        for (size_t i = 0; i < count; i++) {
            if (allocations[i]) free(allocations[i]);
        }
    }
}
VMA_EXPORT void vmaGetAllocationInfo(void* allocator, void* allocation, void* allocInfo) { (void) allocator; (void) allocation; if (allocInfo) memset(allocInfo, 0, 32); }
VMA_EXPORT void vmaGetAllocationInfo2(void* allocator, void* allocation, void* allocInfo) { (void) allocator; (void) allocation; if (allocInfo) memset(allocInfo, 0, 32); }
VMA_EXPORT void vmaSetAllocationUserData(void* allocator, void* allocation, void* userData) { (void) allocator; (void) allocation; (void) userData; }
VMA_EXPORT void vmaSetAllocationName(void* allocator, void* allocation, const char* name) { (void) allocator; (void) allocation; (void) name; }
VMA_EXPORT void vmaGetAllocationMemoryProperties(void* allocator, void* allocation, uint32_t* flags) { (void) allocator; (void) allocation; if (flags) *flags = 0; }
VMA_EXPORT int vmaGetMemoryWin32Handle(void* allocator, void* allocation, void* handle, void** target) { (void) allocator; (void) allocation; (void) handle; if (target) *target = NULL; return 0; }
VMA_EXPORT int vmaMapMemory(void* allocator, void* allocation, void** data) { (void) allocator; (void) allocation; if (data) *data = NULL; return 0; }
VMA_EXPORT void vmaUnmapMemory(void* allocator, void* allocation) { (void) allocator; (void) allocation; }
VMA_EXPORT void vmaFlushAllocation(void* allocator, void* allocation, uint64_t offset, uint64_t size) { (void) allocator; (void) allocation; (void) offset; (void) size; }
VMA_EXPORT void vmaInvalidateAllocation(void* allocator, void* allocation, uint64_t offset, uint64_t size) { (void) allocator; (void) allocation; (void) offset; (void) size; }
VMA_EXPORT int vmaFlushAllocations(void* allocator, uint32_t count, void** allocations, const uint64_t* offsets, const uint64_t* sizes) {
    (void) allocator; (void) count; (void) allocations; (void) offsets; (void) sizes; return 0;
}
VMA_EXPORT int vmaInvalidateAllocations(void* allocator, uint32_t count, void** allocations, const uint64_t* offsets, const uint64_t* sizes) {
    (void) allocator; (void) count; (void) allocations; (void) offsets; (void) sizes; return 0;
}
VMA_EXPORT int vmaCopyMemoryToAllocation(void* allocator, const void* srcHostPointer, void* dstAllocation, uint64_t dstAllocationOffset, uint64_t size) {
    (void) allocator; (void) srcHostPointer; (void) dstAllocation; (void) dstAllocationOffset; (void) size; return 0;
}
VMA_EXPORT int vmaCopyAllocationToMemory(void* allocator, void* srcAllocation, uint64_t srcAllocationOffset, void* dstHostPointer, uint64_t size) {
    (void) allocator; (void) srcAllocation; (void) srcAllocationOffset; (void) dstHostPointer; (void) size; return 0;
}
VMA_EXPORT int vmaCheckCorruption(void* allocator, uint32_t memoryTypeBits) { (void) allocator; (void) memoryTypeBits; return 0; }
VMA_EXPORT int vmaBeginDefragmentation(void* allocator, const void* info, void** context) { (void) allocator; (void) info; if (context) *context = calloc(1, 16); return 0; }
VMA_EXPORT void vmaEndDefragmentation(void* allocator, void* context, void* stats) { (void) allocator; if (stats) memset(stats, 0, 32); if (context) free(context); }
VMA_EXPORT int vmaBeginDefragmentationPass(void* allocator, void* context, void* passInfo) { (void) allocator; (void) context; if (passInfo) memset(passInfo, 0, 32); return 0; }
VMA_EXPORT int vmaEndDefragmentationPass(void* allocator, void* context, void* passInfo) { (void) allocator; (void) context; (void) passInfo; return 0; }
VMA_EXPORT int vmaBindBufferMemory(void* allocator, void* allocation, uint64_t buffer) { (void) allocator; (void) allocation; (void) buffer; return 0; }
VMA_EXPORT int vmaBindBufferMemory2(void* allocator, void* allocation, uint64_t allocationLocalOffset, uint64_t buffer, const void* pNext) {
    (void) allocator; (void) allocation; (void) allocationLocalOffset; (void) buffer; (void) pNext; return 0;
}
VMA_EXPORT int vmaBindImageMemory(void* allocator, void* allocation, uint64_t image) { (void) allocator; (void) allocation; (void) image; return 0; }
VMA_EXPORT int vmaBindImageMemory2(void* allocator, void* allocation, uint64_t allocationLocalOffset, uint64_t image, const void* pNext) {
    (void) allocator; (void) allocation; (void) allocationLocalOffset; (void) image; (void) pNext; return 0;
}
VMA_EXPORT int vmaCreateBuffer(void* allocator, const void* bufferCreateInfo, const void* allocationCreateInfo, uint64_t* buffer, void** allocation, void* allocInfo) {
    (void) allocator; (void) bufferCreateInfo; (void) allocationCreateInfo; (void) allocInfo;
    if (buffer) *buffer = 1;
    if (allocation) *allocation = calloc(1, 16);
    return 0;
}
VMA_EXPORT int vmaCreateBufferWithAlignment(void* allocator, const void* bufferCreateInfo, const void* allocationCreateInfo, uint64_t minAlignment, uint64_t* buffer, void** allocation, void* allocInfo) {
    (void) allocator; (void) bufferCreateInfo; (void) allocationCreateInfo; (void) minAlignment; (void) allocInfo;
    if (buffer) *buffer = 1;
    if (allocation) *allocation = calloc(1, 16);
    return 0;
}
VMA_EXPORT int vmaCreateAliasingBuffer(void* allocator, void* allocation, const void* bufferCreateInfo, uint64_t* buffer) {
    (void) allocator; (void) allocation; (void) bufferCreateInfo;
    if (buffer) *buffer = 1;
    return 0;
}
VMA_EXPORT int vmaCreateAliasingBuffer2(void* allocator, void* allocation, uint64_t allocationLocalOffset, const void* bufferCreateInfo, uint64_t* buffer) {
    (void) allocator; (void) allocation; (void) allocationLocalOffset; (void) bufferCreateInfo;
    if (buffer) *buffer = 1;
    return 0;
}
VMA_EXPORT void vmaDestroyBuffer(void* allocator, uint64_t buffer, void* allocation) {
    (void) allocator; (void) buffer;
    if (allocation) free(allocation);
}
VMA_EXPORT int vmaCreateImage(void* allocator, const void* imageCreateInfo, const void* allocationCreateInfo, uint64_t* image, void** allocation, void* allocInfo) {
    (void) allocator; (void) imageCreateInfo; (void) allocationCreateInfo; (void) allocInfo;
    if (image) *image = 1;
    if (allocation) *allocation = calloc(1, 16);
    return 0;
}
VMA_EXPORT int vmaCreateAliasingImage(void* allocator, void* allocation, const void* imageCreateInfo, uint64_t* image) {
    (void) allocator; (void) allocation; (void) imageCreateInfo;
    if (image) *image = 1;
    return 0;
}
VMA_EXPORT int vmaCreateAliasingImage2(void* allocator, void* allocation, uint64_t allocationLocalOffset, const void* imageCreateInfo, uint64_t* image) {
    (void) allocator; (void) allocation; (void) allocationLocalOffset; (void) imageCreateInfo;
    if (image) *image = 1;
    return 0;
}
VMA_EXPORT void vmaDestroyImage(void* allocator, uint64_t image, void* allocation) {
    (void) allocator; (void) image;
    if (allocation) free(allocation);
}
VMA_EXPORT int vmaCreateVirtualBlock(const void* createInfo, void** block) {
    (void) createInfo;
    if (block) *block = calloc(1, 16);
    return 0;
}
VMA_EXPORT void vmaDestroyVirtualBlock(void* block) {
    if (block) free(block);
}
VMA_EXPORT int vmaIsVirtualBlockEmpty(void* block) { (void) block; return 1; }
VMA_EXPORT void vmaGetVirtualAllocationInfo(void* block, void* allocation, void* allocInfo) { (void) block; (void) allocation; if (allocInfo) memset(allocInfo, 0, 32); }
VMA_EXPORT int vmaVirtualAllocate(void* block, const void* createInfo, void** allocation, uint64_t* offset) {
    (void) block; (void) createInfo;
    if (allocation) *allocation = calloc(1, 16);
    if (offset) *offset = 0;
    return 0;
}
VMA_EXPORT void vmaVirtualFree(void* block, void* allocation) {
    (void) block;
    if (allocation) free(allocation);
}
VMA_EXPORT void vmaClearVirtualBlock(void* block) { (void) block; }
VMA_EXPORT void vmaSetVirtualAllocationUserData(void* block, void* allocation, void* userData) { (void) block; (void) allocation; (void) userData; }
VMA_EXPORT void vmaGetVirtualBlockStatistics(void* block, void* stats) { (void) block; if (stats) memset(stats, 0, 32); }
VMA_EXPORT void vmaCalculateVirtualBlockStatistics(void* block, void* stats) { (void) block; if (stats) memset(stats, 0, 32); }
VMA_EXPORT void vmaBuildVirtualBlockStatsString(void* block, char** statsString, int detailedMap) { (void) block; (void) detailedMap; if (statsString) *statsString = (char*) calloc(1, 4); }
VMA_EXPORT void vmaFreeVirtualBlockStatsString(void* block, char* statsString) { (void) block; if (statsString) free(statsString); }
VMA_EXPORT void vmaBuildStatsString(void* allocator, char** statsString, int detailedMap) { (void) allocator; (void) detailedMap; if (statsString) *statsString = (char*) calloc(1, 4); }
VMA_EXPORT void vmaFreeStatsString(void* allocator, char* statsString) { (void) allocator; if (statsString) free(statsString); }

// LWJGL JNI exports
JNIEXPORT void JNICALL Java_org_lwjgl_util_vma_LibVma_setupMalloc(
    JNIEnv* env, jclass clazz, jlong a, jlong b, jlong c, jlong d, jlong e, jlong f) {
    (void) env; (void) clazz; (void) a; (void) b; (void) c; (void) d; (void) e; (void) f;
}

JNIEXPORT jint JNICALL Java_org_lwjgl_util_vma_Vma_nvmaCreateAllocator(
    JNIEnv* env, jclass clazz, jlong createInfo, jlong allocator) {
    (void) env; (void) clazz;
    return (jint) vmaCreateAllocator((const void*)(intptr_t)createInfo, (void**)(intptr_t)allocator);
}

JNIEXPORT void JNICALL Java_org_lwjgl_util_vma_Vma_nvmaDestroyAllocator(
    JNIEnv* env, jclass clazz, jlong allocator) {
    (void) env; (void) clazz;
    vmaDestroyAllocator((void*)(intptr_t)allocator);
}

JNIEXPORT void JNICALL Java_org_lwjgl_util_vma_Vma_nvmaGetAllocatorInfo(
    JNIEnv* env, jclass clazz, jlong allocator, jlong info) {
    (void) env; (void) clazz;
    vmaGetAllocatorInfo((void*)(intptr_t)allocator, (void*)(intptr_t)info);
}

JNIEXPORT void JNICALL Java_org_lwjgl_util_vma_Vma_nvmaGetPhysicalDeviceProperties(
    JNIEnv* env, jclass clazz, jlong allocator, jlong pp) {
    (void) env; (void) clazz;
    vmaGetPhysicalDeviceProperties((void*)(intptr_t)allocator, (const void**)(intptr_t)pp);
}

JNIEXPORT void JNICALL Java_org_lwjgl_util_vma_Vma_nvmaGetMemoryProperties(
    JNIEnv* env, jclass clazz, jlong allocator, jlong mp) {
    (void) env; (void) clazz;
    vmaGetMemoryProperties((void*)(intptr_t)allocator, (const void**)(intptr_t)mp);
}

JNIEXPORT void JNICALL Java_org_lwjgl_util_vma_Vma_nvmaGetMemoryTypeProperties(
    JNIEnv* env, jclass clazz, jlong allocator, jint typeIndex, jlong flags) {
    (void) env; (void) clazz;
    vmaGetMemoryTypeProperties((void*)(intptr_t)allocator, (uint32_t)typeIndex, (uint32_t*)(intptr_t)flags);
}

JNIEXPORT void JNICALL Java_org_lwjgl_util_vma_Vma_nvmaSetCurrentFrameIndex(
    JNIEnv* env, jclass clazz, jlong allocator, jint frameIndex) {
    (void) env; (void) clazz;
    vmaSetCurrentFrameIndex((void*)(intptr_t)allocator, (uint32_t)frameIndex);
}

JNIEXPORT void JNICALL Java_org_lwjgl_util_vma_Vma_nvmaCalculateStatistics(
    JNIEnv* env, jclass clazz, jlong allocator, jlong stats) {
    (void) env; (void) clazz;
    vmaCalculateStatistics((void*)(intptr_t)allocator, (void*)(intptr_t)stats);
}

JNIEXPORT void JNICALL Java_org_lwjgl_util_vma_Vma_nvmaGetHeapBudgets(
    JNIEnv* env, jclass clazz, jlong allocator, jlong budgets) {
    (void) env; (void) clazz;
    vmaGetHeapBudgets((void*)(intptr_t)allocator, (void*)(intptr_t)budgets);
}

JNIEXPORT jint JNICALL Java_org_lwjgl_util_vma_Vma_nvmaFindMemoryTypeIndex(
    JNIEnv* env, jclass clazz, jlong allocator, jint memoryTypeBits, jlong allocInfo, jlong typeIndex) {
    (void) env; (void) clazz;
    return (jint) vmaFindMemoryTypeIndex((void*)(intptr_t)allocator, (uint32_t)memoryTypeBits, (const void*)(intptr_t)allocInfo, (uint32_t*)(intptr_t)typeIndex);
}

JNIEXPORT jint JNICALL Java_org_lwjgl_util_vma_Vma_nvmaFindMemoryTypeIndexForBufferInfo(
    JNIEnv* env, jclass clazz, jlong allocator, jlong bufferInfo, jlong allocInfo, jlong typeIndex) {
    (void) env; (void) clazz;
    return (jint) vmaFindMemoryTypeIndexForBufferInfo((void*)(intptr_t)allocator, (const void*)(intptr_t)bufferInfo, (const void*)(intptr_t)allocInfo, (uint32_t*)(intptr_t)typeIndex);
}

JNIEXPORT jint JNICALL Java_org_lwjgl_util_vma_Vma_nvmaFindMemoryTypeIndexForImageInfo(
    JNIEnv* env, jclass clazz, jlong allocator, jlong imageInfo, jlong allocInfo, jlong typeIndex) {
    (void) env; (void) clazz;
    return (jint) vmaFindMemoryTypeIndexForImageInfo((void*)(intptr_t)allocator, (const void*)(intptr_t)imageInfo, (const void*)(intptr_t)allocInfo, (uint32_t*)(intptr_t)typeIndex);
}

JNIEXPORT jint JNICALL Java_org_lwjgl_util_vma_Vma_nvmaCreatePool(
    JNIEnv* env, jclass clazz, jlong allocator, jlong createInfo, jlong pool) {
    (void) env; (void) clazz;
    return (jint) vmaCreatePool((void*)(intptr_t)allocator, (const void*)(intptr_t)createInfo, (void**)(intptr_t)pool);
}

JNIEXPORT void JNICALL Java_org_lwjgl_util_vma_Vma_nvmaDestroyPool(
    JNIEnv* env, jclass clazz, jlong allocator, jlong pool) {
    (void) env; (void) clazz;
    vmaDestroyPool((void*)(intptr_t)allocator, (void*)(intptr_t)pool);
}

JNIEXPORT void JNICALL Java_org_lwjgl_util_vma_Vma_nvmaGetPoolStatistics(
    JNIEnv* env, jclass clazz, jlong allocator, jlong pool, jlong poolStats) {
    (void) env; (void) clazz;
    vmaGetPoolStatistics((void*)(intptr_t)allocator, (void*)(intptr_t)pool, (void*)(intptr_t)poolStats);
}

JNIEXPORT void JNICALL Java_org_lwjgl_util_vma_Vma_nvmaCalculatePoolStatistics(
    JNIEnv* env, jclass clazz, jlong allocator, jlong pool, jlong poolStats) {
    (void) env; (void) clazz;
    vmaCalculatePoolStatistics((void*)(intptr_t)allocator, (void*)(intptr_t)pool, (void*)(intptr_t)poolStats);
}

JNIEXPORT jint JNICALL Java_org_lwjgl_util_vma_Vma_nvmaCheckPoolCorruption(
    JNIEnv* env, jclass clazz, jlong allocator, jlong pool) {
    (void) env; (void) clazz;
    return (jint) vmaCheckPoolCorruption((void*)(intptr_t)allocator, (void*)(intptr_t)pool);
}

JNIEXPORT void JNICALL Java_org_lwjgl_util_vma_Vma_nvmaGetPoolName(
    JNIEnv* env, jclass clazz, jlong allocator, jlong pool, jlong name) {
    (void) env; (void) clazz;
    vmaGetPoolName((void*)(intptr_t)allocator, (void*)(intptr_t)pool, (const char**)(intptr_t)name);
}

JNIEXPORT void JNICALL Java_org_lwjgl_util_vma_Vma_nvmaSetPoolName(
    JNIEnv* env, jclass clazz, jlong allocator, jlong pool, jlong name) {
    (void) env; (void) clazz;
    vmaSetPoolName((void*)(intptr_t)allocator, (void*)(intptr_t)pool, (const char*)(intptr_t)name);
}

JNIEXPORT jint JNICALL Java_org_lwjgl_util_vma_Vma_nvmaAllocateMemory(
    JNIEnv* env, jclass clazz, jlong allocator, jlong vkMemoryRequirements, jlong createInfo, jlong allocation, jlong allocInfo) {
    (void) env; (void) clazz;
    return (jint) vmaAllocateMemory((void*)(intptr_t)allocator, (const void*)(intptr_t)vkMemoryRequirements, (const void*)(intptr_t)createInfo, (void**)(intptr_t)allocation, (void*)(intptr_t)allocInfo);
}

JNIEXPORT jint JNICALL Java_org_lwjgl_util_vma_Vma_nvmaAllocateMemoryPages(
    JNIEnv* env, jclass clazz, jlong allocator, jlong vkMemoryRequirements, jlong createInfo, jlong count, jlong allocations, jlong allocInfo) {
    (void) env; (void) clazz;
    return (jint) vmaAllocateMemoryPages((void*)(intptr_t)allocator, (const void*)(intptr_t)vkMemoryRequirements, (const void*)(intptr_t)createInfo, (size_t)count, (void**)(intptr_t)allocations, (void*)(intptr_t)allocInfo);
}

JNIEXPORT jint JNICALL Java_org_lwjgl_util_vma_Vma_nvmaAllocateMemoryForBuffer(
    JNIEnv* env, jclass clazz, jlong allocator, jlong buffer, jlong createInfo, jlong allocation, jlong allocInfo) {
    (void) env; (void) clazz;
    return (jint) vmaAllocateMemoryForBuffer((void*)(intptr_t)allocator, (uint64_t)buffer, (const void*)(intptr_t)createInfo, (void**)(intptr_t)allocation, (void*)(intptr_t)allocInfo);
}

JNIEXPORT jint JNICALL Java_org_lwjgl_util_vma_Vma_nvmaAllocateMemoryForImage(
    JNIEnv* env, jclass clazz, jlong allocator, jlong image, jlong createInfo, jlong allocation, jlong allocInfo) {
    (void) env; (void) clazz;
    return (jint) vmaAllocateMemoryForImage((void*)(intptr_t)allocator, (uint64_t)image, (const void*)(intptr_t)createInfo, (void**)(intptr_t)allocation, (void*)(intptr_t)allocInfo);
}

JNIEXPORT void JNICALL Java_org_lwjgl_util_vma_Vma_nvmaFreeMemory(
    JNIEnv* env, jclass clazz, jlong allocator, jlong allocation) {
    (void) env; (void) clazz;
    vmaFreeMemory((void*)(intptr_t)allocator, (void*)(intptr_t)allocation);
}

JNIEXPORT void JNICALL Java_org_lwjgl_util_vma_Vma_nvmaFreeMemoryPages(
    JNIEnv* env, jclass clazz, jlong allocator, jlong count, jlong allocations) {
    (void) env; (void) clazz;
    vmaFreeMemoryPages((void*)(intptr_t)allocator, (size_t)count, (void**)(intptr_t)allocations);
}

JNIEXPORT void JNICALL Java_org_lwjgl_util_vma_Vma_nvmaGetAllocationInfo(
    JNIEnv* env, jclass clazz, jlong allocator, jlong allocation, jlong allocInfo) {
    (void) env; (void) clazz;
    vmaGetAllocationInfo((void*)(intptr_t)allocator, (void*)(intptr_t)allocation, (void*)(intptr_t)allocInfo);
}

JNIEXPORT void JNICALL Java_org_lwjgl_util_vma_Vma_nvmaGetAllocationInfo2(
    JNIEnv* env, jclass clazz, jlong allocator, jlong allocation, jlong allocInfo) {
    (void) env; (void) clazz;
    vmaGetAllocationInfo2((void*)(intptr_t)allocator, (void*)(intptr_t)allocation, (void*)(intptr_t)allocInfo);
}

JNIEXPORT void JNICALL Java_org_lwjgl_util_vma_Vma_nvmaSetAllocationUserData(
    JNIEnv* env, jclass clazz, jlong allocator, jlong allocation, jlong userData) {
    (void) env; (void) clazz;
    vmaSetAllocationUserData((void*)(intptr_t)allocator, (void*)(intptr_t)allocation, (void*)(intptr_t)userData);
}

JNIEXPORT void JNICALL Java_org_lwjgl_util_vma_Vma_nvmaSetAllocationName(
    JNIEnv* env, jclass clazz, jlong allocator, jlong allocation, jlong name) {
    (void) env; (void) clazz;
    vmaSetAllocationName((void*)(intptr_t)allocator, (void*)(intptr_t)allocation, (const char*)(intptr_t)name);
}

JNIEXPORT void JNICALL Java_org_lwjgl_util_vma_Vma_nvmaGetAllocationMemoryProperties(
    JNIEnv* env, jclass clazz, jlong allocator, jlong allocation, jlong flags) {
    (void) env; (void) clazz;
    vmaGetAllocationMemoryProperties((void*)(intptr_t)allocator, (void*)(intptr_t)allocation, (uint32_t*)(intptr_t)flags);
}

JNIEXPORT jint JNICALL Java_org_lwjgl_util_vma_Vma_nvmaGetMemoryWin32Handle(
    JNIEnv* env, jclass clazz, jlong allocator, jlong allocation, jlong handle, jlong target) {
    (void) env; (void) clazz;
    return (jint) vmaGetMemoryWin32Handle((void*)(intptr_t)allocator, (void*)(intptr_t)allocation, (void*)(intptr_t)handle, (void**)(intptr_t)target);
}

JNIEXPORT jint JNICALL Java_org_lwjgl_util_vma_Vma_nvmaMapMemory(
    JNIEnv* env, jclass clazz, jlong allocator, jlong allocation, jlong data) {
    (void) env; (void) clazz;
    return (jint) vmaMapMemory((void*)(intptr_t)allocator, (void*)(intptr_t)allocation, (void**)(intptr_t)data);
}

JNIEXPORT void JNICALL Java_org_lwjgl_util_vma_Vma_nvmaUnmapMemory(
    JNIEnv* env, jclass clazz, jlong allocator, jlong allocation) {
    (void) env; (void) clazz;
    vmaUnmapMemory((void*)(intptr_t)allocator, (void*)(intptr_t)allocation);
}

JNIEXPORT void JNICALL Java_org_lwjgl_util_vma_Vma_nvmaFlushAllocation(
    JNIEnv* env, jclass clazz, jlong allocator, jlong allocation, jlong offset, jlong size) {
    (void) env; (void) clazz;
    vmaFlushAllocation((void*)(intptr_t)allocator, (void*)(intptr_t)allocation, (uint64_t)offset, (uint64_t)size);
}

JNIEXPORT void JNICALL Java_org_lwjgl_util_vma_Vma_nvmaInvalidateAllocation(
    JNIEnv* env, jclass clazz, jlong allocator, jlong allocation, jlong offset, jlong size) {
    (void) env; (void) clazz;
    vmaInvalidateAllocation((void*)(intptr_t)allocator, (void*)(intptr_t)allocation, (uint64_t)offset, (uint64_t)size);
}

JNIEXPORT jint JNICALL Java_org_lwjgl_util_vma_Vma_nvmaFlushAllocations(
    JNIEnv* env, jclass clazz, jlong allocator, jint count, jlong allocations, jlong offsets, jlong sizes) {
    (void) env; (void) clazz;
    return (jint) vmaFlushAllocations((void*)(intptr_t)allocator, (uint32_t)count, (void**)(intptr_t)allocations, (const uint64_t*)(intptr_t)offsets, (const uint64_t*)(intptr_t)sizes);
}

JNIEXPORT jint JNICALL Java_org_lwjgl_util_vma_Vma_nvmaInvalidateAllocations(
    JNIEnv* env, jclass clazz, jlong allocator, jint count, jlong allocations, jlong offsets, jlong sizes) {
    (void) env; (void) clazz;
    return (jint) vmaInvalidateAllocations((void*)(intptr_t)allocator, (uint32_t)count, (void**)(intptr_t)allocations, (const uint64_t*)(intptr_t)offsets, (const uint64_t*)(intptr_t)sizes);
}

JNIEXPORT jint JNICALL Java_org_lwjgl_util_vma_Vma_nvmaCopyMemoryToAllocation(
    JNIEnv* env, jclass clazz, jlong allocator, jlong srcHostPointer, jlong dstAllocation, jlong dstAllocationOffset, jlong size) {
    (void) env; (void) clazz;
    return (jint) vmaCopyMemoryToAllocation((void*)(intptr_t)allocator, (const void*)(intptr_t)srcHostPointer, (void*)(intptr_t)dstAllocation, (uint64_t)dstAllocationOffset, (uint64_t)size);
}

JNIEXPORT jint JNICALL Java_org_lwjgl_util_vma_Vma_nvmaCopyAllocationToMemory(
    JNIEnv* env, jclass clazz, jlong allocator, jlong srcAllocation, jlong srcAllocationOffset, jlong dstHostPointer, jlong size) {
    (void) env; (void) clazz;
    return (jint) vmaCopyAllocationToMemory((void*)(intptr_t)allocator, (void*)(intptr_t)srcAllocation, (uint64_t)srcAllocationOffset, (void*)(intptr_t)dstHostPointer, (uint64_t)size);
}

JNIEXPORT jint JNICALL Java_org_lwjgl_util_vma_Vma_nvmaCheckCorruption(
    JNIEnv* env, jclass clazz, jlong allocator, jint memoryTypeBits) {
    (void) env; (void) clazz;
    return (jint) vmaCheckCorruption((void*)(intptr_t)allocator, (uint32_t)memoryTypeBits);
}

JNIEXPORT jint JNICALL Java_org_lwjgl_util_vma_Vma_nvmaBeginDefragmentation(
    JNIEnv* env, jclass clazz, jlong allocator, jlong info, jlong context) {
    (void) env; (void) clazz;
    return (jint) vmaBeginDefragmentation((void*)(intptr_t)allocator, (const void*)(intptr_t)info, (void**)(intptr_t)context);
}

JNIEXPORT void JNICALL Java_org_lwjgl_util_vma_Vma_nvmaEndDefragmentation(
    JNIEnv* env, jclass clazz, jlong allocator, jlong context, jlong stats) {
    (void) env; (void) clazz;
    vmaEndDefragmentation((void*)(intptr_t)allocator, (void*)(intptr_t)context, (void*)(intptr_t)stats);
}

JNIEXPORT jint JNICALL Java_org_lwjgl_util_vma_Vma_nvmaBeginDefragmentationPass(
    JNIEnv* env, jclass clazz, jlong allocator, jlong context, jlong passInfo) {
    (void) env; (void) clazz;
    return (jint) vmaBeginDefragmentationPass((void*)(intptr_t)allocator, (void*)(intptr_t)context, (void*)(intptr_t)passInfo);
}

JNIEXPORT jint JNICALL Java_org_lwjgl_util_vma_Vma_nvmaEndDefragmentationPass(
    JNIEnv* env, jclass clazz, jlong allocator, jlong context, jlong passInfo) {
    (void) env; (void) clazz;
    return (jint) vmaEndDefragmentationPass((void*)(intptr_t)allocator, (void*)(intptr_t)context, (void*)(intptr_t)passInfo);
}

JNIEXPORT jint JNICALL Java_org_lwjgl_util_vma_Vma_nvmaBindBufferMemory(
    JNIEnv* env, jclass clazz, jlong allocator, jlong allocation, jlong buffer) {
    (void) env; (void) clazz;
    return (jint) vmaBindBufferMemory((void*)(intptr_t)allocator, (void*)(intptr_t)allocation, (uint64_t)buffer);
}

JNIEXPORT jint JNICALL Java_org_lwjgl_util_vma_Vma_nvmaBindBufferMemory2(
    JNIEnv* env, jclass clazz, jlong allocator, jlong allocation, jlong allocationLocalOffset, jlong buffer, jlong pNext) {
    (void) env; (void) clazz;
    return (jint) vmaBindBufferMemory2((void*)(intptr_t)allocator, (void*)(intptr_t)allocation, (uint64_t)allocationLocalOffset, (uint64_t)buffer, (const void*)(intptr_t)pNext);
}

JNIEXPORT jint JNICALL Java_org_lwjgl_util_vma_Vma_nvmaBindImageMemory(
    JNIEnv* env, jclass clazz, jlong allocator, jlong allocation, jlong image) {
    (void) env; (void) clazz;
    return (jint) vmaBindImageMemory((void*)(intptr_t)allocator, (void*)(intptr_t)allocation, (uint64_t)image);
}

JNIEXPORT jint JNICALL Java_org_lwjgl_util_vma_Vma_nvmaBindImageMemory2(
    JNIEnv* env, jclass clazz, jlong allocator, jlong allocation, jlong allocationLocalOffset, jlong image, jlong pNext) {
    (void) env; (void) clazz;
    return (jint) vmaBindImageMemory2((void*)(intptr_t)allocator, (void*)(intptr_t)allocation, (uint64_t)allocationLocalOffset, (uint64_t)image, (const void*)(intptr_t)pNext);
}

JNIEXPORT jint JNICALL Java_org_lwjgl_util_vma_Vma_nvmaCreateBuffer(
    JNIEnv* env, jclass clazz, jlong allocator, jlong bufferCreateInfo, jlong allocationCreateInfo, jlong buffer, jlong allocation, jlong allocInfo) {
    (void) env; (void) clazz;
    return (jint) vmaCreateBuffer((void*)(intptr_t)allocator, (const void*)(intptr_t)bufferCreateInfo, (const void*)(intptr_t)allocationCreateInfo, (uint64_t*)(intptr_t)buffer, (void**)(intptr_t)allocation, (void*)(intptr_t)allocInfo);
}

JNIEXPORT jint JNICALL Java_org_lwjgl_util_vma_Vma_nvmaCreateBufferWithAlignment(
    JNIEnv* env, jclass clazz, jlong allocator, jlong bufferCreateInfo, jlong allocationCreateInfo, jlong minAlignment, jlong buffer, jlong allocation, jlong allocInfo) {
    (void) env; (void) clazz;
    return (jint) vmaCreateBufferWithAlignment((void*)(intptr_t)allocator, (const void*)(intptr_t)bufferCreateInfo, (const void*)(intptr_t)allocationCreateInfo, (uint64_t)minAlignment, (uint64_t*)(intptr_t)buffer, (void**)(intptr_t)allocation, (void*)(intptr_t)allocInfo);
}

JNIEXPORT jint JNICALL Java_org_lwjgl_util_vma_Vma_nvmaCreateAliasingBuffer(
    JNIEnv* env, jclass clazz, jlong allocator, jlong allocation, jlong bufferCreateInfo, jlong buffer) {
    (void) env; (void) clazz;
    return (jint) vmaCreateAliasingBuffer((void*)(intptr_t)allocator, (void*)(intptr_t)allocation, (const void*)(intptr_t)bufferCreateInfo, (uint64_t*)(intptr_t)buffer);
}

JNIEXPORT jint JNICALL Java_org_lwjgl_util_vma_Vma_nvmaCreateAliasingBuffer2(
    JNIEnv* env, jclass clazz, jlong allocator, jlong allocation, jlong allocationLocalOffset, jlong bufferCreateInfo, jlong buffer) {
    (void) env; (void) clazz;
    return (jint) vmaCreateAliasingBuffer2((void*)(intptr_t)allocator, (void*)(intptr_t)allocation, (uint64_t)allocationLocalOffset, (const void*)(intptr_t)bufferCreateInfo, (uint64_t*)(intptr_t)buffer);
}

JNIEXPORT void JNICALL Java_org_lwjgl_util_vma_Vma_nvmaDestroyBuffer(
    JNIEnv* env, jclass clazz, jlong allocator, jlong buffer, jlong allocation) {
    (void) env; (void) clazz;
    vmaDestroyBuffer((void*)(intptr_t)allocator, (uint64_t)buffer, (void*)(intptr_t)allocation);
}

JNIEXPORT jint JNICALL Java_org_lwjgl_util_vma_Vma_nvmaCreateImage(
    JNIEnv* env, jclass clazz, jlong allocator, jlong imageCreateInfo, jlong allocationCreateInfo, jlong image, jlong allocation, jlong allocInfo) {
    (void) env; (void) clazz;
    return (jint) vmaCreateImage((void*)(intptr_t)allocator, (const void*)(intptr_t)imageCreateInfo, (const void*)(intptr_t)allocationCreateInfo, (uint64_t*)(intptr_t)image, (void**)(intptr_t)allocation, (void*)(intptr_t)allocInfo);
}

JNIEXPORT jint JNICALL Java_org_lwjgl_util_vma_Vma_nvmaCreateAliasingImage(
    JNIEnv* env, jclass clazz, jlong allocator, jlong allocation, jlong imageCreateInfo, jlong image) {
    (void) env; (void) clazz;
    return (jint) vmaCreateAliasingImage((void*)(intptr_t)allocator, (void*)(intptr_t)allocation, (const void*)(intptr_t)imageCreateInfo, (uint64_t*)(intptr_t)image);
}

JNIEXPORT jint JNICALL Java_org_lwjgl_util_vma_Vma_nvmaCreateAliasingImage2(
    JNIEnv* env, jclass clazz, jlong allocator, jlong allocation, jlong allocationLocalOffset, jlong imageCreateInfo, jlong image) {
    (void) env; (void) clazz;
    return (jint) vmaCreateAliasingImage2((void*)(intptr_t)allocator, (void*)(intptr_t)allocation, (uint64_t)allocationLocalOffset, (const void*)(intptr_t)imageCreateInfo, (uint64_t*)(intptr_t)image);
}

JNIEXPORT void JNICALL Java_org_lwjgl_util_vma_Vma_nvmaDestroyImage(
    JNIEnv* env, jclass clazz, jlong allocator, jlong image, jlong allocation) {
    (void) env; (void) clazz;
    vmaDestroyImage((void*)(intptr_t)allocator, (uint64_t)image, (void*)(intptr_t)allocation);
}

JNIEXPORT jint JNICALL Java_org_lwjgl_util_vma_Vma_nvmaCreateVirtualBlock(
    JNIEnv* env, jclass clazz, jlong createInfo, jlong block) {
    (void) env; (void) clazz;
    return (jint) vmaCreateVirtualBlock((const void*)(intptr_t)createInfo, (void**)(intptr_t)block);
}

JNIEXPORT void JNICALL Java_org_lwjgl_util_vma_Vma_nvmaDestroyVirtualBlock(
    JNIEnv* env, jclass clazz, jlong block) {
    (void) env; (void) clazz;
    vmaDestroyVirtualBlock((void*)(intptr_t)block);
}

JNIEXPORT jint JNICALL Java_org_lwjgl_util_vma_Vma_nvmaIsVirtualBlockEmpty(
    JNIEnv* env, jclass clazz, jlong block) {
    (void) env; (void) clazz;
    return (jint) vmaIsVirtualBlockEmpty((void*)(intptr_t)block);
}

JNIEXPORT void JNICALL Java_org_lwjgl_util_vma_Vma_nvmaGetVirtualAllocationInfo(
    JNIEnv* env, jclass clazz, jlong block, jlong allocation, jlong allocInfo) {
    (void) env; (void) clazz;
    vmaGetVirtualAllocationInfo((void*)(intptr_t)block, (void*)(intptr_t)allocation, (void*)(intptr_t)allocInfo);
}

JNIEXPORT jint JNICALL Java_org_lwjgl_util_vma_Vma_nvmaVirtualAllocate(
    JNIEnv* env, jclass clazz, jlong block, jlong createInfo, jlong allocation, jlong offset) {
    (void) env; (void) clazz;
    return (jint) vmaVirtualAllocate((void*)(intptr_t)block, (const void*)(intptr_t)createInfo, (void**)(intptr_t)allocation, (uint64_t*)(intptr_t)offset);
}

JNIEXPORT void JNICALL Java_org_lwjgl_util_vma_Vma_nvmaVirtualFree(
    JNIEnv* env, jclass clazz, jlong block, jlong allocation) {
    (void) env; (void) clazz;
    vmaVirtualFree((void*)(intptr_t)block, (void*)(intptr_t)allocation);
}

JNIEXPORT void JNICALL Java_org_lwjgl_util_vma_Vma_nvmaClearVirtualBlock(
    JNIEnv* env, jclass clazz, jlong block) {
    (void) env; (void) clazz;
    vmaClearVirtualBlock((void*)(intptr_t)block);
}

JNIEXPORT void JNICALL Java_org_lwjgl_util_vma_Vma_nvmaSetVirtualAllocationUserData(
    JNIEnv* env, jclass clazz, jlong block, jlong allocation, jlong userData) {
    (void) env; (void) clazz;
    vmaSetVirtualAllocationUserData((void*)(intptr_t)block, (void*)(intptr_t)allocation, (void*)(intptr_t)userData);
}

JNIEXPORT void JNICALL Java_org_lwjgl_util_vma_Vma_nvmaGetVirtualBlockStatistics(
    JNIEnv* env, jclass clazz, jlong block, jlong stats) {
    (void) env; (void) clazz;
    vmaGetVirtualBlockStatistics((void*)(intptr_t)block, (void*)(intptr_t)stats);
}

JNIEXPORT void JNICALL Java_org_lwjgl_util_vma_Vma_nvmaCalculateVirtualBlockStatistics(
    JNIEnv* env, jclass clazz, jlong block, jlong stats) {
    (void) env; (void) clazz;
    vmaCalculateVirtualBlockStatistics((void*)(intptr_t)block, (void*)(intptr_t)stats);
}

JNIEXPORT void JNICALL Java_org_lwjgl_util_vma_Vma_nvmaBuildVirtualBlockStatsString(
    JNIEnv* env, jclass clazz, jlong block, jlong statsString, jint detailedMap) {
    (void) env; (void) clazz;
    vmaBuildVirtualBlockStatsString((void*)(intptr_t)block, (char**)(intptr_t)statsString, (int)detailedMap);
}

JNIEXPORT void JNICALL Java_org_lwjgl_util_vma_Vma_nvmaFreeVirtualBlockStatsString(
    JNIEnv* env, jclass clazz, jlong block, jlong statsString) {
    (void) env; (void) clazz;
    vmaFreeVirtualBlockStatsString((void*)(intptr_t)block, (char*)(intptr_t)statsString);
}

JNIEXPORT void JNICALL Java_org_lwjgl_util_vma_Vma_nvmaBuildStatsString(
    JNIEnv* env, jclass clazz, jlong allocator, jlong statsString, jint detailedMap) {
    (void) env; (void) clazz;
    vmaBuildStatsString((void*)(intptr_t)allocator, (char**)(intptr_t)statsString, (int)detailedMap);
}

JNIEXPORT void JNICALL Java_org_lwjgl_util_vma_Vma_nvmaFreeStatsString(
    JNIEnv* env, jclass clazz, jlong allocator, jlong statsString) {
    (void) env; (void) clazz;
    vmaFreeStatsString((void*)(intptr_t)allocator, (char*)(intptr_t)statsString);
}
