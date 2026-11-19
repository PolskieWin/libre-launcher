package git.artdeell.mojo.multirt;

import static git.artdeell.mojo.Tools.NATIVE_LIB_DIR;
import static org.apache.commons.io.FileUtils.listFiles;
import static org.apache.commons.io.FileUtils.write;

import android.system.Os;
import android.util.Log;

import com.kdt.mcgui.ProgressLayout;

import git.artdeell.mojo.Architecture;
import git.artdeell.mojo.NewJREUtil;
import git.artdeell.mojo.R;
import git.artdeell.mojo.Tools;
import git.artdeell.mojo.utils.MathUtils;
import git.artdeell.mojo.utils.jre.JavaRunner;

import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.compressors.xz.XZCompressorInputStream;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;

public class MultiRTUtils {

    private static final HashMap<String,Runtime> sCache = new HashMap<>();

    public static File getRuntimeFolder() {
        String base = Tools.MULTIRT_HOME;
        if (base == null) {
            base = (Tools.DIR_DATA != null ? Tools.DIR_DATA : "/data/data/org.libre.launcher") + "/runtimes";
        }
        return new File(base);
    }
    private static final String JAVA_VERSION_STR = "JAVA_VERSION=\"";
    private static final String OS_ARCH_STR = "OS_ARCH=\"";

    public static List<Runtime> getRuntimes() {
        File runtimeFolder = getRuntimeFolder();
        if(!runtimeFolder.exists() && !runtimeFolder.mkdirs()) {
            throw new RuntimeException("Failed to create runtime directory");
        }

        ArrayList<Runtime> runtimes = new ArrayList<>();
        File[] files = runtimeFolder.listFiles();
        if(files != null) for(File f : files) {
            Runtime r = read(f.getName());
            if (git.artdeell.mojo.prefs.LauncherPreferences.isStrippedMode() && r.javaVersion < 21 && r.javaVersion != 0) {
                continue;
            }
            runtimes.add(r);
        }
        else throw new RuntimeException("The runtime directory does not exist");

        return runtimes;
    }

    public static List<NewJREUtil.ExternalRuntime> getRuntimesToDownload() {
        List<NewJREUtil.ExternalRuntime> runtimesToDownload = new ArrayList<>();
        NewJREUtil.ExternalRuntime[] downloadableRuntimes = NewJREUtil.ExternalRuntime.values();

        for (NewJREUtil.ExternalRuntime downloadableruntime : downloadableRuntimes) {
            if (git.artdeell.mojo.prefs.LauncherPreferences.isStrippedMode() && downloadableruntime.majorVersion < 21) {
                continue;
            }
            if(getExactJreName(downloadableruntime.majorVersion) == null){
                // x86 isn't supported for JRE21+
                if (!(Tools.DEVICE_ARCHITECTURE == Architecture.ARCH_X86 && downloadableruntime.majorVersion >= 21))
                    runtimesToDownload.add(downloadableruntime);
            }
        }
        return runtimesToDownload;
    }

    public static String formatRuntimeDisplayName(String name) {
        if (name == null) return "";
        String clean = name.replace(".tar.xz", "").trim();
        if ("Internal-21".equalsIgnoreCase(clean) || "Internal 21".equalsIgnoreCase(clean)) {
            return "Custom JRE 21 (High-FPS Android)";
        }
        if ("Internal-25".equalsIgnoreCase(clean) || "Internal 25".equalsIgnoreCase(clean)) {
            return "Custom JRE 25 (Next-Gen FPS Android)";
        }
        if ("Internal-17".equalsIgnoreCase(clean) || "Internal 17".equalsIgnoreCase(clean)) {
            return "Custom JRE 17 (Optimized Android)";
        }
        return clean.replace("-", " ");
    }

    public static boolean isRuntimeValid(Runtime runtime) {
        if (runtime == null || runtime.name == null) return false;
        File rtHome = new File(getRuntimeFolder(), runtime.name);
        if (!rtHome.exists() || !rtHome.isDirectory()) return false;
        return isRuntimeDirectoryValid(rtHome, runtime.javaVersion);
    }

    public static boolean isRuntimeDirectoryValid(File homeDir, int javaVersion) {
        if (homeDir == null || !homeDir.exists()) return false;
        if (javaVersion <= 8 && javaVersion > 0) {
            File rtJar1 = new File(homeDir, "lib/rt.jar");
            File rtJar2 = new File(homeDir, "jre/lib/rt.jar");
            return (rtJar1.exists() && rtJar1.length() > 0) || (rtJar2.exists() && rtJar2.length() > 0);
        } else {
            File modules = new File(homeDir, "lib/modules");
            if (modules.exists() && modules.length() > 0) return true;
            File rtJar1 = new File(homeDir, "lib/rt.jar");
            File rtJar2 = new File(homeDir, "jre/lib/rt.jar");
            return (rtJar1.exists() && rtJar1.length() > 0) || (rtJar2.exists() && rtJar2.length() > 0);
        }
    }

    public static List<Runtime> getValidRuntimes() {
        List<Runtime> all = getRuntimes();
        List<Runtime> valid = new ArrayList<>();
        for (Runtime r : all) {
            if (isRuntimeValid(r)) {
                valid.add(r);
            }
        }
        return valid;
    }

    public static Runtime getHighestValidRuntime() {
        List<Runtime> runtimes = getValidRuntimes();
        Runtime best = null;
        for (Runtime r : runtimes) {
            if (best == null || r.javaVersion > best.javaVersion) {
                best = r;
            }
        }
        return best;
    }

    public static String getExactJreName(int majorVersion) {
        List<Runtime> runtimes = getValidRuntimes();
        for(Runtime r : runtimes)
            if(r.javaVersion == majorVersion)return r.name;

        return null;
    }

    public static String getNearestJreName(int majorVersion) {
        List<Runtime> runtimes = getValidRuntimes();
        MathUtils.RankedValue<Runtime> nearestRankedRuntime = MathUtils.findNearestPositive(majorVersion, runtimes, (runtime)->runtime.javaVersion);
        if(nearestRankedRuntime != null && nearestRankedRuntime.value != null) {
            return nearestRankedRuntime.value.name;
        }
        Runtime highest = getHighestValidRuntime();
        return highest != null ? highest.name : null;
    }

    public static void installRuntimeNamed(String nativeLibDir, InputStream runtimeInputStream, String name) throws IOException {
        File dest = new File(getRuntimeFolder(),"/"+name);
        if(dest.exists()) FileUtils.deleteDirectory(dest);
        try {
            uncompressTarXZ(runtimeInputStream, dest);
            runtimeInputStream.close();
            read(name);
        } finally {
            ProgressLayout.clearProgress(ProgressLayout.UNPACK_RUNTIME);
        }
    }

    public static void postPrepare(String name) throws IOException {
        File dest = new File(getRuntimeFolder(),"/" + name);
        if(!dest.exists()) return;

        Runtime runtime = read(name);
        File vmPath = JavaRunner.findVmPath(dest, runtime.arch);
        if(vmPath == null) throw new IOException("Could not find libjvm.so after extraction");
        File libDir = Objects.requireNonNull(vmPath.getParentFile()).getParentFile();

        File ftIn = new File(libDir, "libfreetype.so.6");
        File ftOut = new File(libDir,  "libfreetype.so");
        if (ftIn.exists() && (!ftOut.exists() || ftIn.length() != ftOut.length())) {
            if(!ftIn.renameTo(ftOut)) throw new IOException("Failed to rename freetype");
        }
        String nativeDir = NATIVE_LIB_DIR;
        if (nativeDir == null && Tools.APP_CONTEXT != null && Tools.APP_CONTEXT.getApplicationInfo() != null) {
            nativeDir = Tools.APP_CONTEXT.getApplicationInfo().nativeLibraryDir;
            NATIVE_LIB_DIR = nativeDir;
        }
        if (nativeDir != null) {
            File nativeFt = new File(nativeDir, "libfreetype.so");
            if (nativeFt.exists() && !ftOut.exists()) {
                copyDummyNativeLib("libfreetype.so", libDir);
            }
        }

        // Refresh libraries
        copyDummyNativeLib("libawt_xawt.so", libDir);
    }

    public static void installRuntimeNamedBinpack(InputStream universalFileInputStream, InputStream platformBinsInputStream, String name, String binpackVersion) throws IOException {
        File dest = new File(getRuntimeFolder(),"/"+name);
        if(dest.exists()) FileUtils.deleteDirectory(dest);
        try {
            installRuntimeNamedNoRemove(universalFileInputStream, dest);
            installRuntimeNamedNoRemove(platformBinsInputStream, dest);

            File binpack_verfile = new File(getRuntimeFolder(), "/" + name + "/pojav_version");
            write(binpack_verfile, binpackVersion, StandardCharsets.UTF_8);
            forceReread(name);
        } finally {
            ProgressLayout.clearProgress(ProgressLayout.UNPACK_RUNTIME);
        }
    }


    public static String readInternalRuntimeVersion(String name) {
        File versionFile = new File(getRuntimeFolder(),"/" + name + "/pojav_version");
        try {
            if (versionFile.exists()) {
                return Tools.read(versionFile.getAbsolutePath());
            }else{
                return null;
            }
        }catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    public static long readLastUpdateTime(String name) {
        File lastUpdateTimeFile = new File(getRuntimeFolder(), name+"/last_check_time");
        if(!lastUpdateTimeFile.exists()) return -1;
        try {
            return Long.parseLong(Tools.read(lastUpdateTimeFile).trim());
        }catch (IOException | NumberFormatException e) {
            return -1;
        }
    }

    public static void writeLastUpdateTime(String name, long time) {
        File lastUpdateTimeFile = new File(getRuntimeFolder(), name+"/last_check_time");
        try {
            Tools.write(lastUpdateTimeFile, Long.toString(time));
        }catch (IOException ignored) {}
    }

    public static void removeRuntimeNamed(String name) throws IOException {
        File dest = new File(getRuntimeFolder(),"/"+name);
        if(dest.exists()) {
            FileUtils.deleteDirectory(dest);
            sCache.remove(name);
        }
    }

    public static File getRuntimeHome(String name) {
        File dest = new File(getRuntimeFolder(), name);
        Log.i("MiltiRTUitls", "Dest exists? "+dest.exists());
        if((!dest.exists()) || MultiRTUtils.forceReread(name).versionString == null) throw new RuntimeException("Selected runtime is broken!");
        return dest;
    }

    public static Runtime forceReread(String name) {
        sCache.remove(name);
        return read(name);
    }

    public static Runtime read(String name) {
        Runtime returnRuntime = sCache.get(name);
        if(returnRuntime != null) return returnRuntime;
        File release = new File(getRuntimeFolder(),name+"/release");
        if(!release.exists()) {
            return new Runtime(name);
        }
        try {
            String content = Tools.read(release.getAbsolutePath());
            String javaVersion = Tools.extractUntilCharacter(content, JAVA_VERSION_STR, '"');
            String osArch = Tools.extractUntilCharacter(content, OS_ARCH_STR, '"');
            if(javaVersion != null && osArch != null) {
                String[] javaVersionSplit = javaVersion.split("[.\\-+]");
                int javaVersionInt = 0;
                if (javaVersionSplit.length > 0) {
                    if (javaVersionSplit[0].equals("1") && javaVersionSplit.length > 1) {
                        try {
                            javaVersionInt = Integer.parseInt(javaVersionSplit[1]);
                        } catch (NumberFormatException ignored) {}
                    } else {
                        try {
                            javaVersionInt = Integer.parseInt(javaVersionSplit[0]);
                        } catch (NumberFormatException ignored) {}
                    }
                }
                returnRuntime = new Runtime(name, javaVersion, osArch, javaVersionInt);
            }else{
                returnRuntime =  new Runtime(name);
            }
        }catch(Exception e) {
            returnRuntime =  new Runtime(name);
        }
        sCache.put(name, returnRuntime);
        return returnRuntime;
    }


    @SuppressWarnings("SameParameterValue")
    private static void copyDummyNativeLib(String name, File dest) throws IOException {
        String nativeDir = NATIVE_LIB_DIR;
        if (nativeDir == null && Tools.APP_CONTEXT != null && Tools.APP_CONTEXT.getApplicationInfo() != null) {
            nativeDir = Tools.APP_CONTEXT.getApplicationInfo().nativeLibraryDir;
            NATIVE_LIB_DIR = nativeDir;
        }
        if (nativeDir == null) return;
        File src = new File(nativeDir, name);
        if (!src.exists()) return;
        File fileLib = new File(dest, name);
        if (fileLib.exists() && fileLib.length() == src.length() && !java.nio.file.Files.isSymbolicLink(fileLib.toPath())) {
            return;
        }
        try {
            java.nio.file.Files.deleteIfExists(fileLib.toPath());
        } catch (Throwable ignored) {
            fileLib.delete();
        }
        try (InputStream is = new FileInputStream(src);
             OutputStream os = new FileOutputStream(fileLib)) {
            IOUtils.copy(is, os);
        }
        try {
            android.system.Os.chmod(fileLib.getAbsolutePath(), 0755);
        } catch (Throwable ignored) {}
    }

    private static void installRuntimeNamedNoRemove(InputStream runtimeInputStream, File dest) throws IOException {
        uncompressTarXZ(runtimeInputStream,dest);
        runtimeInputStream.close();
    }

    private static void uncompressTarXZ(final InputStream tarFileInputStream, final File dest) throws IOException {
        git.artdeell.mojo.utils.FileUtils.ensureDirectory(dest);

        byte[] buffer = new byte[8192];
        try(TarArchiveInputStream tarIn = new TarArchiveInputStream(new XZCompressorInputStream(tarFileInputStream))) {
            TarArchiveEntry tarEntry;
            // tarIn is a TarArchiveInputStream
            while ((tarEntry = tarIn.getNextTarEntry()) != null) {

                final String tarEntryName = tarEntry.getName();
                ProgressLayout.setProgress(ProgressLayout.UNPACK_RUNTIME, 100, R.string.global_unpacking, tarEntryName);

                File destPath = new File(dest, tarEntry.getName());
                git.artdeell.mojo.utils.FileUtils.ensureParentDirectory(destPath);
                if (tarEntry.isSymbolicLink()) {
                    try {
                        // android.system.Os
                        // Libcore one support all Android versions
                        Os.symlink(tarEntry.getName(), tarEntry.getLinkName());
                    } catch (Throwable e) {
                        Log.e("MultiRT", e.toString());
                    }
                } else if (tarEntry.isDirectory()) {
                    git.artdeell.mojo.utils.FileUtils.ensureDirectory(destPath);
                } else if (!destPath.exists() || destPath.length() != tarEntry.getSize()) {
                    FileOutputStream os = new FileOutputStream(destPath);
                    IOUtils.copyLarge(tarIn, os, buffer);
                    os.close();
                }
            }
        }
    }
}
