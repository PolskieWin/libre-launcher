package git.artdeell.mojo.vulkan.driver;

import java.io.File;

import git.artdeell.mojo.Tools;
import git.artdeell.mojo.utils.GpuUtils;

public final class BuiltinDriver implements Driver {
    public static final String DRIVER_LIB = "libvulkan_freedreno.so";
    @Override
    public String getName() {
        return "Built-in";
    }

    @Override
    public String getMainLibrary() {
        return deviceHasDriver() ? DRIVER_LIB : null;
    }

    @Override
    public String getHash() {
        return null;
    }

    public static boolean deviceHasDriver(){
        if (Tools.NATIVE_LIB_DIR == null) return false;
        return new File(Tools.NATIVE_LIB_DIR, DRIVER_LIB).exists();
    }
}
