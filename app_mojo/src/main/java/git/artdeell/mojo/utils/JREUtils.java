package git.artdeell.mojo.utils;

import android.content.*;
import android.system.*;
import android.util.*;

import androidx.appcompat.app.AppCompatActivity;

import java.io.*;
import java.nio.file.Files;
import java.util.*;

import git.artdeell.mojo.Logger;
import git.artdeell.mojo.Tools;
import git.artdeell.mojo.multirt.MultiRTUtils;
import git.artdeell.mojo.multirt.Runtime;
import git.artdeell.mojo.prefs.LauncherPreferences;
import git.artdeell.mojo.utils.plugins.LibraryPlugin;
import git.artdeell.mojo.prefs.*;

public class JREUtils {
    public static void redirectAndPrintJRELog() {
        Log.v("jrelog","Log starts here");
        new Thread(new Runnable(){
            int failTime = 0;
            ProcessBuilder logcatPb;
            @Override
            public void run() {
                try {
                    if (logcatPb == null) {
                        // No filtering by tag anymore as that relied on incorrect log levels set in log.h
                        logcatPb = new ProcessBuilder().command("logcat", /* "-G", "1mb", */ "-v", "brief", "-s", "jrelog", "LIBGL", "NativeInput").redirectErrorStream(true);
                    }

                    Log.i("jrelog-logcat","Clearing logcat");
                    new ProcessBuilder().command("logcat", "-c").redirectErrorStream(true).start();
                    Log.i("jrelog-logcat","Starting logcat");
                    java.lang.Process p = logcatPb.start();

                    byte[] buf = new byte[1024];
                    int len;
                    while ((len = p.getInputStream().read(buf)) != -1) {
                        String currStr = new String(buf, 0, len);
                        Logger.appendToLog(currStr);
                    }

                    if (p.waitFor() != 0) {
                        Log.e("jrelog-logcat", "Logcat exited with code " + p.exitValue());
                        failTime++;
                        Log.i("jrelog-logcat", (failTime <= 10 ? "Restarting logcat" : "Too many restart fails") + " (attempt " + failTime + "/10");
                        if (failTime <= 10) {
                            run();
                        } else {
                            Logger.appendToLog("ERROR: Unable to get more log.");
                        }
                    }
                } catch (Throwable e) {
                    Log.e("jrelog-logcat", "Exception on logging thread", e);
                    Logger.appendToLog("Exception on logging thread:\n" + Log.getStackTraceString(e));
                }
            }
        }).start();
        Log.i("jrelog-logcat","Logcat thread started");

    }

    private static void overrideEnvVars(Map<String, String> envMap) throws IOException {
        File customEnvFile = new File(Tools.DIR_GAME_HOME, "custom_env.txt");
        if(!customEnvFile.exists() || !customEnvFile.isFile()) return;
        BufferedReader reader = new BufferedReader(new FileReader(customEnvFile));
        String line;
        while ((line = reader.readLine()) != null) {
            // Not use split() as only split first one
            int index = line.indexOf("=");
            envMap.put(line.substring(0, index), line.substring(index + 1));
        }
        reader.close();
    }

    public static void setupFfmpegEnv(Context ctx, Map<String, String> envMap) {
        LibraryPlugin ffmpeg = LibraryPlugin.discoverPlugin(ctx, LibraryPlugin.ID_FFMPEG_PLUGIN);
        if(ffmpeg == null) return;
        envMap.put("POJAV_FFMPEG_PATH", ffmpeg.resolveAbsolutePath("libffmpeg.so"));
    }
    public static void setGameEnvironment(Context context, Map<String, String> versionSpecificEnv) throws Throwable {
        Map<String, String> envMap = new ArrayMap<>();
        if(versionSpecificEnv != null) envMap.putAll(versionSpecificEnv);
		// This is currently required for YSM mod to function
		File modRuntimeDir = new File(Tools.DIR_CACHE, "app_runtime_mod");
		envMap.put("MOD_ANDROID_RUNTIME", modRuntimeDir.getAbsolutePath());

        setupFfmpegEnv(context, envMap);

        if(LauncherPreferences.PREF_BIG_CORE_AFFINITY) envMap.put("POJAV_BIG_CORE_AFFINITY", "1");
        if(LauncherPreferences.PREF_ALSOFT_FORCE_OPENSL) envMap.put("ALSOFT_DRIVERS", "opensl");

        overrideEnvVars(envMap);

        for (Map.Entry<String, String> env : envMap.entrySet()) {
            Logger.appendToLog("Added custom env: " + env.getKey() + "=" + env.getValue());
            try {
                Os.setenv(env.getKey(), env.getValue(), true);
            }catch (NullPointerException exception){
                Log.e("JREUtils", exception.toString());
            }
        }
    }

    /**
     * Parse and separate java arguments in a user friendly fashion
     * It supports multi line and absence of spaces between arguments
     * The function also supports auto-removal of improper arguments, although it may miss some.
     *
     * @param args The un-parsed argument list.
     * @return Parsed args as an ArrayList
     */
    public static ArrayList<String> parseJavaArguments(String args){
        ArrayList<String> parsedArguments = new ArrayList<>(0);
        args = args.trim().replace(" ", "");
        //For each prefixes, we separate args.
        String[] separators = new String[]{"-XX:-","-XX:+", "-XX:","--", "-D", "-X", "-javaagent:", "-verbose"};
        for(String prefix : separators){
            while (true){
                int start = args.indexOf(prefix);
                if(start == -1) break;
                //Get the end of the current argument by checking the nearest separator
                int end = -1;
                for(String separator: separators){
                    int tempEnd = args.indexOf(separator, start + prefix.length());
                    if(tempEnd == -1) continue;
                    if(end == -1){
                        end = tempEnd;
                        continue;
                    }
                    end = Math.min(end, tempEnd);
                }
                //Fallback
                if(end == -1) end = args.length();

                //Extract it
                String parsedSubString = args.substring(start, end);
                args = args.replace(parsedSubString, "");

                //Check if two args aren't bundled together by mistake
                if(parsedSubString.indexOf('=') == parsedSubString.lastIndexOf('=')) {
                    int arraySize = parsedArguments.size();
                    if(arraySize > 0){
                        String lastString = parsedArguments.get(arraySize - 1);
                        // Looking for list elements
                        if(lastString.charAt(lastString.length() - 1) == ',' ||
                                parsedSubString.contains(",")){
                            parsedArguments.set(arraySize - 1, lastString + parsedSubString);
                            continue;
                        }
                    }
                    parsedArguments.add(parsedSubString);
                }
                // --a-b= handling (with multiple signs so we don't accidentally remove mandatory args)
                else if(parsedSubString.startsWith("--") && parsedSubString.indexOf('=') != -1) {
                    parsedArguments.add(parsedSubString);
                }
                else Log.w("JAVA ARGS PARSER", "Removed improper arguments: " + parsedSubString);
            }
        }
        return parsedArguments;
    }

    public static Runtime initJavaRuntime(Runtime runtime) {
        if (runtime != null && MultiRTUtils.isRuntimeValid(runtime)) {
            return runtime;
        }
        Runtime fallback = MultiRTUtils.getHighestValidRuntime();
        if (fallback != null) {
            Log.w("JREUtils", "Runtime " + (runtime != null ? runtime.name : "null") + " failed validation; falling back to " + fallback.name);
            return fallback;
        }
        return runtime;
    }

    public static Runtime initJavaRuntime(String runtimeName) {
        Runtime runtime = runtimeName != null ? MultiRTUtils.read(runtimeName) : null;
        return initJavaRuntime(runtime);
    }

    public static int getDetectedVersion() {
        return GpuUtils.getGlInfo().glesMajorVersion;
    }
    public static native int chdir(String path);

    public static native void setLdLibraryPath(String ldLibraryPath);

    public static void prepareRuntimeLibraries(Context context, File vmPath) {
        String nativeLibDir = null;
        if (context != null && context.getApplicationInfo() != null) {
            nativeLibDir = context.getApplicationInfo().nativeLibraryDir;
        }
        if (nativeLibDir == null) {
            nativeLibDir = Tools.NATIVE_LIB_DIR;
        }

        try {
            System.loadLibrary("freetype");
            Log.i("JREUtils", "Preloaded libfreetype.so successfully");
        } catch (UnsatisfiedLinkError e) {
            Log.e("JREUtils", "Failed to preload libfreetype.so", e);
        }

        if (nativeLibDir == null || vmPath == null) return;
        File vmDir = vmPath.getParentFile();
        if (vmDir == null) return;
        File runtimeLibDir = vmDir.getParentFile();
        if (runtimeLibDir == null) return;

        File nativeDir = new File(nativeLibDir);
        String[] libsToSync = new String[] { "libfreetype.so", "libawt_xawt.so", "libgl4es_114.so" };

        for (String libName : libsToSync) {
            File src = new File(nativeDir, libName);
            if (src.exists()) {
                ensureLibInDir(src, runtimeLibDir);
                if (runtimeLibDir.getParentFile() != null && "lib".equals(runtimeLibDir.getParentFile().getName())) {
                    ensureLibInDir(src, runtimeLibDir.getParentFile());
                }
            }
        }
    }

    public static void ensureLibInDir(File src, File targetDir) {
        if (!src.exists() || !targetDir.exists()) return;
        File target = new File(targetDir, src.getName());
        try {
            if (target.exists()) {
                if (!Files.isSymbolicLink(target.toPath()) && target.length() == src.length()) {
                    return;
                }
                try {
                    Files.deleteIfExists(target.toPath());
                } catch (Throwable ignored) {
                    target.delete();
                }
            } else if (Files.isSymbolicLink(target.toPath())) {
                try {
                    Files.deleteIfExists(target.toPath());
                } catch (Throwable ignored) {
                    target.delete();
                }
            }
            copyFile(src, target);
            Log.i("JREUtils", "Copied " + src.getName() + " to " + target.getAbsolutePath());
            try {
                Os.chmod(target.getAbsolutePath(), 0755);
            } catch (Throwable ignored) {}
        } catch (Throwable t) {
            Log.e("JREUtils", "Failed to sync " + src.getName() + " into " + targetDir.getAbsolutePath(), t);
        }
    }

    private static void copyFile(File src, File dst) throws IOException {
        try (InputStream in = new FileInputStream(src);
             OutputStream out = new FileOutputStream(dst)) {
            byte[] buf = new byte[8192];
            int len;
            while ((len = in.read(buf)) > 0) {
                out.write(buf, 0, len);
            }
        }
    }

    static {
        try {
            System.loadLibrary("mojort");
            System.loadLibrary("mojoawt");
        } catch (Throwable t) {
            android.util.Log.e("JREUtils", "Failed to load mojort/mojoawt", t);
        }
        try {
            System.loadLibrary("freetype");
        } catch (Throwable t) {
            android.util.Log.e("JREUtils", "Failed to load freetype", t);
        }
    }
}
