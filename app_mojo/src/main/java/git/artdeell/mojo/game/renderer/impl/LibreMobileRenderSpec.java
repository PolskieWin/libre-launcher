package git.artdeell.mojo.game.renderer.impl;

import android.content.Context;
import android.util.Log;

import git.artdeell.mojo.R;
import git.artdeell.mojo.Tools;
import git.artdeell.mojo.prefs.LauncherPreferences;
import git.artdeell.mojo.game.renderer.def.Renderers;
import git.artdeell.mojo.utils.GpuUtils;
import git.artdeell.mojo.vulkan.DriverManager;
import git.artdeell.mojo.vulkan.driver.AdrenoDriver;
import git.artdeell.mojo.vulkan.driver.BuiltinDriver;
import git.artdeell.mojo.vulkan.driver.Driver;
import git.artdeell.mojoexec.MojoExec;

import java.io.File;
import java.util.List;
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

    public static native boolean nativeIsFlagshipAdreno();
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

        // Universal Core 4.6 profile over Zink with 26.4 snapshots support
        envMap.put("LIBRE_MOBILE_ACTIVE", "1");
        envMap.put("MESA_LOADER_DRIVER_OVERRIDE", "zink");
        envMap.put("MESA_GLSL_VERSION_OVERRIDE", "460");
        envMap.put("MESA_GL_VERSION_OVERRIDE", "4.6COMPAT");

        // Universal Mobile Enhancements
        envMap.put("mesa_glthread", "true");
        envMap.put("MESA_NO_ERROR", "1");
        envMap.put("LIBGL_NOERROR", "1");
        envMap.put("ZINK_DESCRIPTORS", "lazy");
        envMap.put("allow_higher_compat_version", "true");
        envMap.put("allow_glsl_extension_directive_midshader", "true");
        envMap.put("glsl_zero_init", "true");
        envMap.put("MESA_EXTENSION_OVERRIDE", "+GL_EXT_texture_filter_anisotropic +GL_ARB_buffer_storage +GL_ARB_clip_control +GL_ARB_shader_draw_parameters +GL_ARB_direct_state_access +GL_ARB_vertex_attrib_binding +GL_ARB_multi_bind +GL_ARB_gpu_shader5 +GL_EXT_clip_cull_distance +GL_ARB_shader_storage_buffer_object +GL_ARB_compute_shader +GL_ARB_explicit_uniform_location +GL_ARB_multi_draw_indirect +GL_ARB_texture_query_levels +GL_ARB_conservative_depth +GL_ARB_shader_image_load_store +GL_ARB_shading_language_include +GL_ARB_polygon_offset_clamp +GL_ARB_texture_storage +GL_ARB_arrays_of_arrays +GL_ARB_clear_texture");

        boolean isFlagship = false;
        try {
            isFlagship = nativeIsFlagshipAdreno();
        } catch (Throwable ignored) {}

        if (isAdreno) {
            StringBuilder tu = new StringBuilder();
            if (LauncherPreferences.PREF_FREEDRENO_SYSMEM) tu.append("sysmem,");
            if (LauncherPreferences.PREF_TU_DEBUG_NOBIN) tu.append("nobin,");
            if (tu.length() > 0) {
                tu.setLength(tu.length() - 1);
                envMap.put("TU_DEBUG", tu.toString());
            } else {
                envMap.remove("TU_DEBUG");
            }

            if (isFlagship) {
                // Flagship Tuned Turnip
                envMap.put("MESA_VK_WSI_PRESENT_MODE", "mailbox");
                boolean is7xx8xx = glInfo.renderer != null && (
                        glInfo.renderer.contains("730") || glInfo.renderer.contains("740") ||
                        glInfo.renderer.contains("750") || glInfo.renderer.contains("830") ||
                        glInfo.renderer.contains("735") || glInfo.renderer.contains("725") ||
                        glInfo.renderer.contains("732")
                );
                if (is7xx8xx || isFlagship) {
                    envMap.put("FD_DEV_FEATURES", "enable_tp_ubwc_flag_hint=1");
                }
            } else {
                // Normal / Untuned Standard Turnip
                envMap.remove("FD_DEV_FEATURES");
                envMap.remove("MESA_VK_WSI_PRESENT_MODE");
            }
        } else if (isMali) {
            envMap.remove("FD_DEV_FEATURES");
            envMap.remove("TU_DEBUG");
            envMap.remove("MESA_VK_WSI_PRESENT_MODE");
            envMap.put("ZINK_DEBUG", "compact");
            envMap.put("PAN_MESA_DEBUG", "gl3");
        } else if (isXclipse) {
            envMap.remove("FD_DEV_FEATURES");
            envMap.remove("TU_DEBUG");
            envMap.remove("MESA_VK_WSI_PRESENT_MODE");
            envMap.put("RADV_DEBUG", "nocompact");
        } else {
            envMap.remove("FD_DEV_FEATURES");
            envMap.remove("TU_DEBUG");
            envMap.remove("MESA_VK_WSI_PRESENT_MODE");
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
        return hasMesa() && GpuUtils.checkVulkanSupport(context.getPackageManager());
    }

    @Override
    public boolean setupRenderer() {
        GpuUtils.GLInfo glInfo = GpuUtils.getGlInfo();
        boolean isAdreno = glInfo.isAdreno();
        boolean isMali = glInfo.isArm();
        boolean isXclipse = glInfo.renderer != null && (glInfo.renderer.contains("Xclipse") || glInfo.renderer.contains("AMD"));

        boolean isFlagship = false;
        try {
            isFlagship = nativeIsFlagshipAdreno();
        } catch (Throwable ignored) {}

        // Auto-select flagship Turnip driver build if installed and unconfigured
        if (isAdreno && isFlagship && DriverManager.isSupportedByDevice()) {
            try {
                List<Driver> drivers = DriverManager.getDrivers();
                Driver bestTurnip = null;
                for (Driver d : drivers) {
                    if (d instanceof BuiltinDriver) continue;
                    String dName = d.getName() != null ? d.getName().toLowerCase() : "";
                    String dDesc = "";
                    if (d instanceof AdrenoDriver) {
                        AdrenoDriver ad = (AdrenoDriver) d;
                        if (ad.description != null) {
                            dDesc = ad.description.toLowerCase();
                        }
                    }
                    if (dName.contains("turnip") || dDesc.contains("turnip") || dName.contains("flagship")) {
                        bestTurnip = d;
                        break;
                    }
                }
                if (bestTurnip != null && DriverManager.getPreferredDriver() instanceof BuiltinDriver) {
                    DriverManager.setPreferredDriver(bestTurnip);
                }
            } catch (Throwable t) {
                Log.w("LibreMobile", "Failed to auto-select flagship Turnip driver", t);
            }
        }

        try {
            nativeSetupMobile(isAdreno, isMali, isXclipse);
        } catch (Throwable t) {
            Log.w("LibreMobile", "Could not apply native mobile tweaks", t);
        }
        MojoExec.preloadVulkan();
        return super.setupRenderer();
    }
}
