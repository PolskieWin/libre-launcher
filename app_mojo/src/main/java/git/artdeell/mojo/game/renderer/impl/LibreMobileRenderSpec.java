package git.artdeell.mojo.game.renderer.impl;

import android.content.Context;
import android.util.Log;

import git.artdeell.mojo.R;
import git.artdeell.mojo.Tools;
import git.artdeell.mojo.game.renderer.def.Renderers;
import git.artdeell.mojo.utils.GpuUtils;
import git.artdeell.mojoexec.MojoExec;

import java.io.File;
import java.util.Map;

/**
 * Libre Mobile - High-Performance Universal Mobile GPU Renderer
 * Engineered for Snapdragon (Adreno), MediaTek/Tensor (Mali/Immortalis), and Exynos (Xclipse).
 */
public class LibreMobileRenderSpec extends MesaRenderSpec {

    static {
        try {
            System.loadLibrary("mojort");
        } catch (Throwable t) {
            Log.w("LibreMobile", "Failed to load mojort", t);
        }
    }

    public static native boolean nativeSetupSnapdragon();
    public static native boolean nativeSetupMobile(boolean isAdreno, boolean isMali, boolean isXclipse);

    @Override
    public String name() {
        return "Libre Mobile";
    }

    @Override
    public int displayName() {
        return R.string.mcl_setting_renderer_libre_mobile;
    }

    @Override
    public String tag() {
        return Renderers.LIBRE_MOBILE_RENDERER;
    }

    @Override
    public void setupEnvironment(Context context, Map<String, String> envMap) {
        super.setupEnvironment(context, envMap);

        GpuUtils.GLInfo glInfo = GpuUtils.getGlInfo();
        boolean isAdreno = glInfo.isAdreno();
        boolean isMali = glInfo.isArm();
        boolean isXclipse = glInfo.renderer != null && (glInfo.renderer.contains("Xclipse") || glInfo.renderer.contains("AMD"));

        // Universal Core 4.6 profile over Zink
        envMap.put("MESA_LOADER_DRIVER_OVERRIDE", "zink");
        envMap.put("MESA_GLSL_VERSION_OVERRIDE", "460");
        envMap.put("MESA_GL_VERSION_OVERRIDE", "4.6");

        // Universal Mobile Enhancements
        envMap.put("mesa_glthread", "true");
        envMap.put("MESA_NO_ERROR", "1");
        envMap.put("LIBGL_NOERROR", "1");
        envMap.put("ZINK_DESCRIPTORS", "lazy");
        envMap.put("allow_higher_compat_version", "true");
        envMap.put("MESA_VK_WSI_PRESENT_MODE", "mailbox");
        envMap.put("MESA_EXTENSION_OVERRIDE", "+GL_EXT_texture_filter_anisotropic +GL_ARB_buffer_storage");

        // GPU-Specific Performance Tuning
        if (isAdreno) {
            envMap.put("TU_DEBUG", "sysmem");
            envMap.put("FD_DEV_FEATURES", "enable_tp_ubwc_flag_hint=1");
        } else if (isMali) {
            envMap.put("ZINK_DEBUG", "compact");
            envMap.put("PAN_MESA_DEBUG", "gl3");
        } else if (isXclipse) {
            envMap.put("RADV_DEBUG", "nocompact");
        }

        // 2GB High-Performance Persistent Shader Cache
        File cacheDir = Tools.DIR_CACHE;
        if (cacheDir != null) {
            File shaderDir = new File(cacheDir, "libre_shaders");
            if (!shaderDir.exists()) shaderDir.mkdirs();
            envMap.put("MESA_GLSL_CACHE_DIR", shaderDir.getAbsolutePath());
            envMap.put("MESA_SHADER_CACHE_DIR", shaderDir.getAbsolutePath());
            envMap.put("MESA_SHADER_CACHE_MAX_SIZE", "2048M");
            envMap.put("MESA_GLSL_CACHE_MAX_SIZE", "2048M");
        }
    }

    @Override
    public boolean compatibleDevice(Context context) {
        // High-performance Vulkan renderer compatible with all mobile Vulkan GPUs
        return hasMesa() && GpuUtils.checkVulkanSupport(context.getPackageManager());
    }

    @Override
    public boolean setupRenderer() {
        try {
            GpuUtils.GLInfo glInfo = GpuUtils.getGlInfo();
            boolean isAdreno = glInfo.isAdreno();
            boolean isMali = glInfo.isArm();
            boolean isXclipse = glInfo.renderer != null && (glInfo.renderer.contains("Xclipse") || glInfo.renderer.contains("AMD"));
            nativeSetupMobile(isAdreno, isMali, isXclipse);
        } catch (Throwable t) {
            Log.w("LibreMobile", "Could not apply native mobile tweaks", t);
        }
        MojoExec.preloadVulkan();
        return super.setupRenderer();
    }
}
