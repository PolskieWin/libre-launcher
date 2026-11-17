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
 * Libre Mobile - High-Performance Snapdragon & Adreno GPU Renderer
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

    @Override
    public String name() {
        return "Libre Mobile (Snapdragon)";
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

        // Core 4.6 profile over Zink / Turnip
        envMap.put("MESA_LOADER_DRIVER_OVERRIDE", "zink");
        envMap.put("MESA_GLSL_VERSION_OVERRIDE", "460");
        envMap.put("MESA_GL_VERSION_OVERRIDE", "4.6");

        // Snapdragon Adreno Direct Optimizations
        envMap.put("TU_DEBUG", "sysmem");
        envMap.put("FD_DEV_FEATURES", "enable_tp_ubwc_flag_hint=1");
        envMap.put("mesa_glthread", "true");
        envMap.put("MESA_NO_ERROR", "1");
        envMap.put("LIBGL_NOERROR", "1");

        // 1GB Persistent Shader Cache
        File cacheDir = Tools.DIR_CACHE;
        if (cacheDir != null) {
            envMap.put("MESA_GLSL_CACHE_DIR", cacheDir.getAbsolutePath());
            envMap.put("MESA_SHADER_CACHE_DIR", cacheDir.getAbsolutePath());
            envMap.put("MESA_SHADER_CACHE_MAX_SIZE", "1024M");
        }
    }

    @Override
    public boolean compatibleDevice(Context context) {
        // Optimized for Snapdragon / Adreno GPUs, compatible with all Vulkan devices
        return hasMesa() && GpuUtils.checkVulkanSupport(context.getPackageManager());
    }

    @Override
    public boolean setupRenderer() {
        try {
            nativeSetupSnapdragon();
        } catch (Throwable t) {
            Log.w("LibreMobile", "Could not apply native Snapdragon tweaks", t);
        }
        MojoExec.preloadVulkan();
        return super.setupRenderer();
    }
}
