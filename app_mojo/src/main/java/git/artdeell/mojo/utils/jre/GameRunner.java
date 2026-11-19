package git.artdeell.mojo.utils.jre;

import android.content.Context;
import android.util.ArrayMap;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import git.artdeell.mojo.Architecture;
import git.artdeell.mojo.JVersionList;
import git.artdeell.mojo.Logger;
import git.artdeell.mojo.Tools;
import git.artdeell.mojo.authenticator.accounts.Account;
import git.artdeell.mojo.authenticator.AuthType;
import git.artdeell.mojo.game.renderer.def.Renderers;
import git.artdeell.mojo.game.renderer.impl.GLESRenderSpec;
import git.artdeell.mojo.instances.Instance;
import git.artdeell.mojo.utils.DownloadUtils;
import git.artdeell.mojo.utils.lifecycle.LifecycleAwareAlertDialog;
import git.artdeell.mojo.multirt.MultiRTUtils;
import git.artdeell.mojo.multirt.Runtime;
import git.artdeell.mojo.prefs.LauncherPreferences;
import git.artdeell.mojo.game.renderer.GameRenderer;
import git.artdeell.mojo.game.renderer.RenderSpec;
import git.artdeell.mojo.utils.DateUtils;
import git.artdeell.mojo.utils.FileUtils;
import git.artdeell.mojo.utils.GpuUtils;
import git.artdeell.mojo.utils.GameOptionsUtils;
import git.artdeell.mojo.utils.JREUtils;
import git.artdeell.mojo.utils.JSONUtils;
import git.artdeell.mojo.utils.MCOptionUtils;
import git.artdeell.mojo.utils.OldVersionsUtils;
import git.artdeell.mojo.utils.SocUtils;
import git.artdeell.mojo.modding.forge.ForgelikeUtils;
import git.artdeell.mojo.value.MoJsonRule;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import git.artdeell.mojo.R;

public class GameRunner {
    private static final String FILEDIALOG_BRIDGE_ASSET = "components/filedialog_bridge/filedialog_bridge.jar";
    /**
     * Optimization mods based on Sodium can mitigate the render distance issue. Check if Sodium
     * or its derivative is currently installed to skip the render distance check.
     * @param gameDir current game directory
     * @return whether sodium or a sodium-based mod is installed
     */
    public static boolean hasSodium(File gameDir) {
        File modsDir = new File(gameDir, "mods");
        if(!modsDir.exists() || !modsDir.isDirectory()) return false;
        File[] mods = modsDir.listFiles(file -> file.isFile() && file.getName().endsWith(".jar"));
        if(mods == null) return false;
        for(File file : mods) {
            String name = file.getName().toLowerCase(Locale.ROOT);
            if(name.contains("sodium") ||
                    name.contains("embeddium") ||
                    name.contains("rubidium") ||
                    name.contains("xenon") ||
                    name.contains("magnesium") ||
                    name.contains("iris") ||
                    name.contains("oculus") ||
                    name.contains("radium") ||
                    name.contains("indium") ||
                    name.contains("moreculling") ||
                    name.contains("more-culling") ||
                    name.contains("entityculling") ||
                    name.contains("cullleaves") ||
                    name.contains("immediatelyfast") ||
                    name.contains("ferritecore") ||
                    name.contains("modernfix") ||
                    name.contains("krypton") ||
                    name.contains("lithium")) return true;
        }
        return false;
    }

    /**
     * Check if VulkanMod is installed
     * @param gameDir current game directory
     * @return whether VulkanMod is installed
     */
    public static boolean hasVulkanMod(File gameDir) {
        File modsDir = new File(gameDir, "mods");
        if(!modsDir.exists() || !modsDir.isDirectory()) return false;
        File[] mods = modsDir.listFiles(file -> file.isFile() && file.getName().endsWith(".jar"));
        if(mods == null) return false;
        for(File file : mods) {
            String name = file.getName().toLowerCase(Locale.ROOT);
            if(name.contains("vulkanmod") || name.contains("vulkan-mod")) return true;
        }
        return false;
    }

    /**
     * Check if Angelica is currently installed to allow usage of LTW
     * @param gameDir current game directory
     * @return whether Angelica is installed
     */
    public static boolean hasAngelica(File gameDir) {
        File modsDir = new File(gameDir, "mods");
        if(!modsDir.exists() || !modsDir.isDirectory()) return false;
        File[] mods = modsDir.listFiles(file -> file.isFile() && file.getName().endsWith(".jar"));
        if(mods == null) return false;
        for(File file : mods) {
            String name = file.getName().toLowerCase(Locale.ROOT);
            if(name.contains("angelica")) return true;
        }
        return false;
    }

    /**
     * Initialize OpenGL and do checks to see if the GPU of the device is affected by the render
     * distance issue.

     * Currently only checks whether the user has an Adreno GPU capable of OpenGL ES 3.

     * This issue is caused by a very severe limit on the amount of GL buffer names that could be allocated
     * by the Adreno properietary GLES driver.

     * @return whether the GPU is affected by the Large Thin Wrapper render distance issue on vanilla
     */

    private static boolean affectedByRenderDistanceIssue(JVersionList.Version version) throws ParseException {
        if(LauncherPreferences.PREF_USE_ANGLE || LauncherPreferences.PREF_USE_SYSTEM_ANGLE) return false;
        String id = version != null && version.id != null ? version.id.toLowerCase() : "";
        if (id.startsWith("26.") || id.startsWith("26w") || id.contains("26.4") || id.startsWith("25.") || id.startsWith("25w")) return false;
        GpuUtils.GLInfo info = GpuUtils.getGlInfo();
        Date origDate = DateUtils.getOriginalReleaseDate(version);
        if (origDate == null) return false;
        return info.isAdreno() &&
                info.glesMajorVersion >= 3 &&
                // 1.21.5 fixes the RD issue, released on march 25 2025
                DateUtils.dateBefore(origDate, 2025, 2, 25);
    }

    private static boolean checkRenderDistance(JVersionList.Version version, File gamedir) throws ParseException {
        if(!affectedByRenderDistanceIssue(version)) return false;
        if(hasSodium(gamedir) || hasVulkanMod(gamedir)) return false;
        try {
            MCOptionUtils.load();
        }catch (Exception e) {
            Log.e("Tools", "Failed to load config", e);
        }
        int renderDistance = GameOptionsUtils.parseIntDefault(MCOptionUtils.get("renderDistance"),12);
        // 7 is the render distance "magic number" above which MC creates too many buffers
        // for Adreno's OpenGL ES implementation
        return renderDistance > 7;
    }

    private static boolean isGl4esCompatible(JVersionList.Version version) throws Exception{
        String id = version != null && version.id != null ? version.id.toLowerCase() : "";
        if (id.startsWith("26.") || id.startsWith("26w") || id.contains("26.4") || id.startsWith("25.") || id.startsWith("25w")) return false;
        Date origDate = DateUtils.getOriginalReleaseDate(version);
        if (origDate == null) return false;
        return DateUtils.dateBefore(origDate, 2025, 1, 7);
    }

    private static boolean isCompatContext(JVersionList.Version version) throws Exception{
        if (version == null) return false;
        String id = version.id != null ? version.id.toLowerCase() : "";
        if (id.startsWith("26.") || id.startsWith("26w") || id.contains("26.4") || id.startsWith("25.") || id.startsWith("25w") || id.startsWith("24.") || id.startsWith("24w") || id.startsWith("1.21") || id.startsWith("1.20") || id.startsWith("1.19") || id.startsWith("1.18") || id.startsWith("1.17")) {
            return false;
        }
        Date origDate = DateUtils.getOriginalReleaseDate(version);
        if (origDate == null) return false;
        // Day before the release date of 21w10a, the first OpenGL 3 Core Minecraft version
        return DateUtils.dateBefore(origDate, 2021, 3, 9);
    }

    private static boolean showDialog(AppCompatActivity activity, int message) throws InterruptedException {
        LifecycleAwareAlertDialog.DialogCreator dialogCreator = ((alertDialog, dialogBuilder) ->
                dialogBuilder.setMessage(activity.getString(message))
                        .setCancelable(false)
                        .setPositiveButton(android.R.string.ok, (d, w)->{}));
        return LifecycleAwareAlertDialog.haltOnDialog(activity.getLifecycle(), activity, dialogCreator);
    }

    // Autoswitch to provided renderer if supported, otherwise - crash with resId dialog message
    private static void switchRendererIfSupported(boolean support,
                                                  RenderSpec renderer,
                                                  GameRenderer gameRenderer,
                                                  Instance instance,
                                                  AppCompatActivity activity,
                                                  int resId) throws InterruptedException, IOException {
        if(support) {
            instance.renderer = renderer.tag();
            instance.write();
            gameRenderer.setCurrentRenderer(renderer);
        }else {
            showDialog(activity, resId);
            System.exit(0);
        }
    }

    public static void launchGame(final AppCompatActivity activity, Account account,
                                  Instance instance, String versionId, File[] classpath, GameRenderer gameRenderer) throws Throwable {
        boolean hasToken = account != null && (
                (account.accessToken != null && !account.accessToken.equals("0") && !account.accessToken.trim().isEmpty())
                || (account.refreshToken != null && !account.refreshToken.equals("0") && !account.refreshToken.trim().isEmpty())
        );
        if (account == null || account.authType != AuthType.MICROSOFT || !account.isMicrosoft || !hasToken) {
            throw new IllegalStateException("Valid Microsoft authentication is strictly required to launch Minecraft: Java Edition.");
        }

        int freeDeviceMemory = Tools.getFreeDeviceMemory(activity);
        int localeString;
        int freeAddressSpace = Architecture.is32BitsDevice() ? Tools.getMaxContinuousAddressSpaceSize() : -1;
        Log.i("MemStat", "Free RAM: " + freeDeviceMemory + " Addressable: " + freeAddressSpace);
        boolean showAddressMemoryWarning = freeDeviceMemory > freeAddressSpace && freeAddressSpace != -1;
        if(showAddressMemoryWarning) {
            freeDeviceMemory = freeAddressSpace;
            localeString = R.string.address_memory_warning_msg;
        } else {
            localeString = R.string.memory_warning_msg;
        }

        if(LauncherPreferences.PREF_RAM_ALLOCATION > freeDeviceMemory && (showAddressMemoryWarning || LauncherPreferences.PREF_SHOW_MEMORY_WARNING_DIALOG)) {
            int finalDeviceMemory = freeDeviceMemory;
            LifecycleAwareAlertDialog.DialogCreator dialogCreator = (dialog, builder) -> {
                builder.setMessage(activity.getString(localeString, finalDeviceMemory, LauncherPreferences.PREF_RAM_ALLOCATION))
                        .setPositiveButton(android.R.string.ok, (d, w) -> {
                        });

                if (!showAddressMemoryWarning) {
                    builder.setNegativeButton(R.string.option_do_not_show_again, (d, w) -> {
                        LauncherPreferences.DEFAULT_PREF.edit().putBoolean("showMemoryWarning", false).apply();
                        Toast.makeText(activity, R.string.notification_permission_toast, Toast.LENGTH_SHORT).show();
                    });
                }
            };


                if (LifecycleAwareAlertDialog.haltOnDialog(activity.getLifecycle(), activity, dialogCreator)) {
                    return; // If the dialog's lifecycle has ended, return without
                    // actually launching the game, thus giving us the opportunity
                    // to start after the activity is shown again
                }
            }
        File gamedir = instance.getGameDirectory();
        JVersionList.Version versionInfo = Tools.getVersionInfo(versionId);
        // We don't need the library list, the asset index, client download info for the code below
        versionInfo.libraries = null;
        versionInfo.downloads = null;

        RenderSpec renderer = gameRenderer.getCurrentRenderer();

        if (git.artdeell.mojo.prefs.LauncherPreferences.isStrippedMode()) {
            String currentId = renderer != null ? renderer.tag() : null;
            if (!Renderers.LIBRE_MOBILE_RENDERER.equals(currentId) && !Renderers.MOBILEGLUES_RENDERER.equals(currentId)) {
                RenderSpec target = GameRenderer.getKnownRenderer(Renderers.LIBRE_MOBILE_RENDERER);
                if (target == null || !target.compatibleDevice(activity)) {
                    target = GameRenderer.getKnownRenderer(Renderers.MOBILEGLUES_RENDERER);
                }
                if (target != null) {
                    switchRendererIfSupported(true, target, gameRenderer, instance, activity, 0);
                    renderer = gameRenderer.getCurrentRenderer();
                }
            }
        }

        if(!versionInfo.disableRendererChecks) {
            boolean hasVm = hasVulkanMod(gamedir);
            // Switch renderer to GL4ES when running a compat context version on LTW
            if(!hasVm && isCompatContext(versionInfo) && !hasAngelica(gamedir) && renderer instanceof GLESRenderSpec.LTWRenderSpec) {
                switchRendererIfSupported(true, GameRenderer.getKnownRenderer(Renderers.GL4ES_RENDERER), gameRenderer, instance, activity, 0);
            }

            boolean isGl4es = renderer instanceof GLESRenderSpec.GL4ESRenderSpec;
            RenderSpec ltw = GameRenderer.getKnownRenderer(Renderers.LTW_RENDERER);
            boolean ltwSupported = ltw != null && ltw.compatibleDevice(activity);
            // Block Sodium from running with GL4ES on 1.17+
            if(!hasVm && !isCompatContext(versionInfo) && isGl4es && hasSodium(gamedir)) {
                if (ltwSupported) {
                    switchRendererIfSupported(true, ltw, gameRenderer, instance, activity, 0);
                } else {
                    RenderSpec mg = GameRenderer.getKnownRenderer(Renderers.MOBILEGLUES_RENDERER);
                    boolean mgSupported = mg != null && mg.compatibleDevice(activity);
                    if (mgSupported) {
                        switchRendererIfSupported(true, mg, gameRenderer, instance, activity, 0);
                    } else {
                        RenderSpec zink = GameRenderer.getKnownRenderer(Renderers.ZINK_RENDERER);
                        boolean zinkSupported = zink != null && zink.compatibleDevice(activity);
                        if (zinkSupported) {
                            switchRendererIfSupported(true, zink, gameRenderer, instance, activity, 0);
                        } else {
                            switchRendererIfSupported(false, ltw, gameRenderer, instance, activity, R.string.compat_sodium_not_supported);
                        }
                    }
                }
            }

            // Switch renderer when running modern versions incompatible with GL4ES
            if(!hasVm && !isGl4esCompatible(versionInfo) && isGl4es) {
                RenderSpec lm = GameRenderer.getKnownRenderer(Renderers.LIBRE_MOBILE_RENDERER);
                boolean lmSupported = lm != null && lm.compatibleDevice(activity);
                if (lmSupported) {
                    switchRendererIfSupported(true, lm, gameRenderer, instance, activity, 0);
                } else if (ltwSupported) {
                    switchRendererIfSupported(true, ltw, gameRenderer, instance, activity, 0);
                } else {
                    RenderSpec mg = GameRenderer.getKnownRenderer(Renderers.MOBILEGLUES_RENDERER);
                    boolean mgSupported = mg != null && mg.compatibleDevice(activity);
                    if (mgSupported) {
                        switchRendererIfSupported(true, mg, gameRenderer, instance, activity, 0);
                    } else {
                        RenderSpec zink = GameRenderer.getKnownRenderer(Renderers.ZINK_RENDERER);
                        boolean zinkSupported = zink != null && zink.compatibleDevice(activity);
                        if (zinkSupported) {
                            switchRendererIfSupported(true, zink, gameRenderer, instance, activity, 0);
                        } else {
                            switchRendererIfSupported(false, ltw, gameRenderer, instance, activity, R.string.compat_version_not_supported);
                        }
                    }
                }
            }
        }

        boolean isLtw = renderer instanceof GLESRenderSpec.LTWRenderSpec;

        if(isLtw && checkRenderDistance(versionInfo, gamedir)) {
            if(showDialog(activity, R.string.ltw_render_distance_warning_msg)) return;
            // If the code goes here, it means that the user clicked "OK". Fix the render distance.
            try {
                MCOptionUtils.set("renderDistance", "7");
                MCOptionUtils.save();
            }catch (Exception e) {
                Log.e("Tools", "Failed to fix render distance setting", e);
            }
        }

        GameOptionsUtils.fixOptions(isLtw);

        if(isLtw && GpuUtils.getGlInfo().forcedMsaa) {
            if(showDialog(activity, R.string.ltw_4x_msaa_warning_msg)) return;
        }

        int requiredJavaVersion = 8;
        if(versionInfo.javaVersion != null && versionInfo.javaVersion.majorVersion > 0) {
            requiredJavaVersion = versionInfo.javaVersion.majorVersion;
        } else {
            String id = versionInfo.id != null ? versionInfo.id.toLowerCase() : "";
            String inherits = versionInfo.inheritsFrom != null ? versionInfo.inheritsFrom.toLowerCase() : "";
            if (id.startsWith("neoforge-")) {
                inherits = ForgelikeUtils.getMcVersionForNeoVersion(id.substring("neoforge-".length())).toLowerCase();
            }
            if (id.startsWith("26.") || id.startsWith("26w") || id.contains("26.4") || id.startsWith("25.") || id.startsWith("25w") || id.startsWith("24.") || id.startsWith("24w") || id.startsWith("1.21") || id.startsWith("1.20.5") || id.startsWith("1.20.6")
                    || inherits.startsWith("26.") || inherits.startsWith("26w") || inherits.contains("26.4") || inherits.startsWith("25.") || inherits.startsWith("25w") || inherits.startsWith("24.") || inherits.startsWith("24w") || inherits.startsWith("1.21") || inherits.startsWith("1.20.5") || inherits.startsWith("1.20.6")) {
                requiredJavaVersion = 21;
            } else if (id.startsWith("1.18") || id.startsWith("1.19") || id.startsWith("1.20") || inherits.startsWith("1.18") || inherits.startsWith("1.19") || inherits.startsWith("1.20")) {
                requiredJavaVersion = 17;
            } else if (id.startsWith("1.17") || inherits.startsWith("1.17")) {
                requiredJavaVersion = 16;
            }
        }

        Runtime runtime = MultiRTUtils.forceReread(pickRuntime(instance, requiredJavaVersion));

        // Pre-process specific files
        disableSplash(gamedir);
        List<String> launchArgs = getMoJsonClientArgs(account, versionInfo, gamedir);

        // Select the appropriate openGL version
        OldVersionsUtils.selectOpenGlVersion(versionInfo);

        ArrayList<String> launchClassPath = new ArrayList<>(classpath.length + 1);
        classpath = prependFileDialogBridge(activity, classpath);
        for(int i = 0; i < classpath.length; i++) {
            File classpathEntry = classpath[i];
            String entryPath = classpathEntry.getAbsolutePath();
            if(!classpathEntry.exists()) {
                Log.w("GameRunner", "Skipped classpath entry " + entryPath + " because it is missing");
            }
            launchClassPath.add(entryPath);
            // Unreference the classpath entry to avoid retaining it on heap
            classpath[i] = null;
        }
        launchClassPath.trimToSize();

        List<String> javaArgList = new ArrayList<>();

        if (versionInfo.logging != null && versionInfo.logging.client != null && versionInfo.logging.client.file != null) {
            String configFile = Tools.DIR_DATA + "/security/" + versionInfo.logging.client.file.id.replace("client", "log4j-rce-patch");
            if (!new File(configFile).exists()) {
                configFile = Tools.DIR_GAME_NEW + "/" + versionInfo.logging.client.file.id;
            }
            javaArgList.add("-Dlog4j.configurationFile=" + configFile);
        }

        versionInfo.logging = null;

        File versionSpecificNativesDir = new File(Tools.DIR_CACHE, "natives/"+versionId);
        if(versionSpecificNativesDir.exists()) {
            String dirPath = versionSpecificNativesDir.getAbsolutePath();
            javaArgList.add("-Djava.library.path="+dirPath+":"+Tools.NATIVE_LIB_DIR);
            javaArgList.add("-Djna.boot.library.path="+dirPath);
            // Sometimes, the game can extract natives itself onto this path
            javaArgList.add("-Dorg.lwjgl.librarypath="+dirPath);
        }

        File lwjglExtractDir = new File(Tools.DIR_CACHE, "lwjgl_native/"+versionId);
        FileUtils.ensureDirectory(lwjglExtractDir);
        javaArgList.add("-Dorg.lwjgl.system.SharedLibraryExtractPath="+lwjglExtractDir.getAbsolutePath());

        mergeMoJsonArgs(javaArgList, getMoJsonJvmArgs(versionId), gamedir);
        ensureForgeNeoForgeArgs(javaArgList, versionId, runtime.javaVersion);

        versionInfo.arguments = null;
        versionInfo.minecraftArguments = null;
        versionInfo.assets = null;
        versionInfo.assetIndex = null;

        javaArgList.addAll(JREUtils.parseJavaArguments(instance.getLaunchArgs()));

        Map<String, String> versionEnvMap = versionInfo.environment;

        // TODO: this should be decoupled from GameRunner completely
        gameRenderer.setupEnvironment(activity);
        JREUtils.setGameEnvironment(activity, versionEnvMap);
        JREUtils.chdir(instance.getGameDirectory().getAbsolutePath());

        if(!gameRenderer.maybeSetupRenderer()) {
            if(showDialog(activity, R.string.gr_err_renderer_load_Failed)) return;
            System.exit(0);
        }
        javaArgList.add("-Dorg.lwjgl.opengl.libname=libGLMojo.so");
        javaArgList.add("-Dorg.lwjgl.freetype.libname="+ Tools.NATIVE_LIB_DIR+"/libfreetype.so");

        File fakeProcDir = SocUtils.setupFakeProc(Tools.DIR_CACHE);
        if (fakeProcDir != null) {
            String fakeProcPath = fakeProcDir.getAbsolutePath();
            javaArgList.add("-Doshi.util.proc.path=" + fakeProcPath);
            javaArgList.add("-Doshi.os.linux.proc.path=" + fakeProcPath);
        }

        activity.runOnUiThread(() -> Toast.makeText(activity, activity.getString(R.string.autoram_info_msg,LauncherPreferences.PREF_RAM_ALLOCATION), Toast.LENGTH_SHORT).show());

        Log.i("GameRunner", "Running with "+ launchArgs.toString());

        String mainClass = versionInfo.mainClass;
        if (mainClass == null || mainClass.trim().isEmpty()) {
            String vId = versionId != null ? versionId.toLowerCase() : "";
            if (vId.contains("neoforge") || vId.contains("forge") || vId.contains("fml")) {
                mainClass = "cpw.mods.bootstraplauncher.BootstrapLauncher";
            } else if (vId.contains("fabric")) {
                mainClass = "net.fabricmc.loader.impl.launch.knot.KnotClient";
            } else {
                mainClass = "net.minecraft.client.main.Main";
            }
        }

        try {
            launchArgs = sanitizeQuickPlayArgs(launchArgs);
            JavaRunner.nativeSetupExit(activity);
            JavaRunner.startJvm(runtime, javaArgList, launchClassPath, mainClass, launchArgs);
        }catch (VMLoadException e) {
            LifecycleAwareAlertDialog.DialogCreator dialogCreator = (dialog, builder) ->
                builder.setMessage(e.toString(activity)).setPositiveButton(android.R.string.ok, (d, w)->{});

            if(LifecycleAwareAlertDialog.haltOnDialog(activity.getLifecycle(), activity, dialogCreator)) {
                return;
            }
        }

        Tools.restartLauncherActivity(activity);
        Tools.fullyExit();
    }

    /**
     * Unpack the file dialog bridge jar and put it ahead of the game classpath, so
     * games link against the launcher dialog implementations (tinyfd, NFD) instead
     * of the bundled native glue libraries (which have no Android file picker).
     */
    private static File[] prependFileDialogBridge(AppCompatActivity activity, File[] classpath) {
        try {
            File bridgeDir = new File(Tools.DIR_CACHE, "filedialog_bridge");
            if (!bridgeDir.isDirectory() && !bridgeDir.mkdirs()) return classpath;
            File bridgeJar = new File(bridgeDir, "filedialog_bridge.jar");
            try (InputStream in = activity.getAssets().open(FILEDIALOG_BRIDGE_ASSET);
                 OutputStream out = new FileOutputStream(bridgeJar)) {
                byte[] buffer = new byte[8192];
                int read;
                while ((read = in.read(buffer)) != -1) out.write(buffer, 0, read);
            }
            File[] newClasspath = new File[classpath.length + 1];
            newClasspath[0] = bridgeJar;
            System.arraycopy(classpath, 0, newClasspath, 1, classpath.length);
            Log.i("GameRunner", "File dialog bridge prepended to the game classpath");
            return newClasspath;
        } catch (Throwable t) {
            Log.w("GameRunner", "File dialog bridge unavailable, file dialogs will stay inactive", t);
            return classpath;
        }
    }

    private static void disableSplash(File dir) {
        File configDir = new File(dir, "config");
        if(FileUtils.ensureDirectorySilently(configDir)) {
            File forgeSplashFile = new File(dir, "config/splash.properties");
            String forgeSplashContent = "enabled=true";
            try {
                if (forgeSplashFile.exists()) {
                    forgeSplashContent = Tools.read(forgeSplashFile.getAbsolutePath());
                }
                if (forgeSplashContent.contains("enabled=true")) {
                    Tools.write(forgeSplashFile,
                            forgeSplashContent.replace("enabled=true", "enabled=false"));
                }
            } catch (IOException e) {
                Log.w(Tools.APP_NAME, "Could not disable Forge 1.12.2 and below splash screen!", e);
            }
        } else {
            Log.w(Tools.APP_NAME, "Failed to create the configuration directory");
        }
    }

    // Skip setting essential flags from the version JSON as we already override them
    private static boolean shouldSkipArg(String arg) {
        if (arg == null || arg.trim().isEmpty()) return true;
        final String[] args = {
                "-Djava.library.path=",
                "-Djna.tmpdir=",
                "-Dorg.lwjgl.system.SharedLibraryExtractPath=",
                "-Dio.netty.native.workdir=",
                "-Djava.class.path="
        };
        for(String s : args) {
            if(arg.startsWith(s)) return true;
        }
        if (arg.equals("-cp") || arg.equals("-classpath") || arg.equals("--class-path")
                || arg.startsWith("-cp=") || arg.startsWith("-classpath=") || arg.startsWith("--class-path=")
                || arg.equals("${classpath}") || arg.startsWith("${classpath")) {
            return true;
        }
        return false;
    }

    private static File resolveArgFile(String rawPath, File gameDir) {
        if (rawPath == null || rawPath.trim().isEmpty()) return null;
        rawPath = rawPath.trim();
        if ((rawPath.startsWith("\"") && rawPath.endsWith("\"")) || (rawPath.startsWith("'") && rawPath.endsWith("'"))) {
            rawPath = rawPath.substring(1, rawPath.length() - 1);
        }
        File direct = new File(rawPath);
        if (direct.exists()) return direct;
        if (gameDir != null) {
            File inGameDir = new File(gameDir, rawPath);
            if (inGameDir.exists()) return inGameDir;
        }
        if (Tools.DIR_HOME_LIBRARY != null) {
            File inLibDir = new File(Tools.DIR_HOME_LIBRARY, rawPath);
            if (inLibDir.exists()) return inLibDir;
        }
        if (Tools.DIR_GAME_NEW != null) {
            File inGameNew = new File(Tools.DIR_GAME_NEW, rawPath);
            if (inGameNew.exists()) return inGameNew;
        }
        return direct;
    }

    private static List<String> tokenizeLine(String line) {
        List<String> tokens = new ArrayList<>();
        StringBuilder sb = new StringBuilder();
        boolean inQuotes = false;
        char quoteChar = 0;
        boolean escaped = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (escaped) {
                sb.append(c);
                escaped = false;
            } else if (c == '\\') {
                escaped = true;
            } else if (inQuotes) {
                if (c == quoteChar) {
                    inQuotes = false;
                } else {
                    sb.append(c);
                }
            } else if (c == '"' || c == '\'') {
                inQuotes = true;
                quoteChar = c;
            } else if (Character.isWhitespace(c)) {
                if (sb.length() > 0) {
                    tokens.add(sb.toString());
                    sb.setLength(0);
                }
            } else {
                sb.append(c);
            }
        }
        if (sb.length() > 0) {
            tokens.add(sb.toString());
        }
        return tokens;
    }

    private static List<String> expandArgFile(File file, int depth, File gameDir) {
        if (depth > 5 || !file.exists() || !file.canRead()) {
            return Collections.emptyList();
        }
        List<String> result = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }
                List<String> tokens = tokenizeLine(line);
                for (String token : tokens) {
                    if (token.startsWith("@")) {
                        File nested = resolveArgFile(token.substring(1), gameDir);
                        if (nested != null && nested.exists()) {
                            result.addAll(expandArgFile(nested, depth + 1, gameDir));
                        }
                    } else {
                        result.add(token);
                    }
                }
            }
        } catch (IOException e) {
            Log.w("GameRunner", "Failed to read argfile: " + file.getAbsolutePath(), e);
        }
        return result;
    }

    private static void mergeMoJsonArgs(List<String> userArgs, List<String> moJsonArgs, File gameDir) {
        for (int i = 0; i < moJsonArgs.size(); i++) {
            String arg = moJsonArgs.get(i);
            if (arg == null || arg.trim().isEmpty()) continue;
            if (arg.equals("-cp") || arg.equals("-classpath") || arg.equals("--class-path")) {
                if (i + 1 < moJsonArgs.size()) i++;
                continue;
            }
            if (shouldSkipArg(arg)) continue;
            if (arg.startsWith("@")) {
                File argFile = resolveArgFile(arg.substring(1).trim(), gameDir);
                if (argFile != null && argFile.exists()) {
                    List<String> expanded = expandArgFile(argFile, 0, gameDir);
                    for (int j = 0; j < expanded.size(); j++) {
                        String expArg = expanded.get(j);
                        if (expArg.equals("-cp") || expArg.equals("-classpath") || expArg.equals("--class-path")) {
                            if (j + 1 < expanded.size()) j++;
                            continue;
                        }
                        if (!shouldSkipArg(expArg)) {
                            userArgs.add(expArg);
                        }
                    }
                    continue;
                } else {
                    Log.w("GameRunner", "Argfile not found or unreadable: " + arg);
                    continue;
                }
            }
            userArgs.add(arg);
        }
    }

    private static void addIfMissing(List<String> list, String arg) {
        if (arg.startsWith("--add-opens=") || arg.startsWith("--add-exports=")) {
            int eq1 = arg.indexOf('=');
            int eq2 = arg.indexOf('=', eq1 + 1);
            String targetKey = eq2 > 0 ? arg.substring(0, eq2) : arg;
            for (String item : list) {
                if (item.startsWith(targetKey + "=") || item.equals(arg)) {
                    return;
                }
            }
            list.add(arg);
            return;
        }
        String prefix = arg.contains("=") ? arg.substring(0, arg.indexOf('=')) : arg;
        for (String item : list) {
            if (item.equals(arg) || item.startsWith(prefix + "=")) {
                return;
            }
        }
        list.add(arg);
    }

    private static void ensureForgeNeoForgeArgs(List<String> args, String versionId, int targetJavaVersion) {
        boolean isForgeOrNeo = false;
        String vId = versionId != null ? versionId.toLowerCase() : "";
        if (vId.contains("forge") || vId.contains("neoforge") || vId.contains("fml")) {
            isForgeOrNeo = true;
        }
        if (!isForgeOrNeo) {
            for (String arg : args) {
                if (arg.contains("cpw.mods") || arg.contains("bootstraplauncher") || arg.contains("modlauncher") || arg.contains("neoforge")) {
                    isForgeOrNeo = true;
                    break;
                }
            }
        }
        if (!isForgeOrNeo) return;

        addIfMissing(args, "-Dterminal.ansi=true");
        addIfMissing(args, "-Dterminal.jline=false");
        addIfMissing(args, "-Djline.terminal=none");
        addIfMissing(args, "-Dfml.earlyprogresswindow=false");
        addIfMissing(args, "-Dfml.disableEarlyDisplay=true");
        addIfMissing(args, "-Dneoforge.earlyprogresswindow=false");
        addIfMissing(args, "-Dneoforge.disableEarlyDisplay=true");
        addIfMissing(args, "-Dforge.earlyprogresswindow=false");
        addIfMissing(args, "-Dforge.forceNoStencil=true");

        if (targetJavaVersion >= 16) {
            addIfMissing(args, "--add-modules=ALL-MODULE-PATH");
            addIfMissing(args, "--add-opens=java.base/java.util.jar=ALL-UNNAMED");
            addIfMissing(args, "--add-opens=java.base/java.lang.invoke=ALL-UNNAMED");
            addIfMissing(args, "--add-opens=java.base/java.lang=ALL-UNNAMED");
            addIfMissing(args, "--add-opens=java.base/java.lang.reflect=ALL-UNNAMED");
            addIfMissing(args, "--add-opens=java.base/java.util=ALL-UNNAMED");
            addIfMissing(args, "--add-opens=java.base/java.io=ALL-UNNAMED");
            addIfMissing(args, "--add-opens=java.base/java.net=ALL-UNNAMED");
            addIfMissing(args, "--add-opens=java.base/java.nio=ALL-UNNAMED");
            addIfMissing(args, "--add-opens=java.base/sun.nio.ch=ALL-UNNAMED");
            addIfMissing(args, "--add-opens=java.base/jdk.internal.loader=ALL-UNNAMED");
            addIfMissing(args, "--add-opens=java.base/jdk.internal.misc=ALL-UNNAMED");
            addIfMissing(args, "--add-opens=java.base/sun.security.action=ALL-UNNAMED");
            addIfMissing(args, "--add-exports=java.base/sun.security.action=ALL-UNNAMED");
            addIfMissing(args, "--add-exports=jdk.naming.dns/com.sun.jndi.dns=java.naming");
        }
    }

    private static boolean isRuleAllowed(Object rulesObj) {
        return isRuleAllowed(rulesObj, null);
    }

    private static boolean isRuleAllowed(Object rulesObj, Map<String, Boolean> activeFeatures) {
        if (rulesObj == null) return true;
        if (rulesObj instanceof List) {
            List<?> rulesList = (List<?>) rulesObj;
            if (rulesList.isEmpty()) return true;
            try {
                String rulesJson = Tools.GLOBAL_GSON.toJson(rulesList);
                MoJsonRule[] rules = Tools.GLOBAL_GSON.fromJson(rulesJson, MoJsonRule[].class);
                return "allow".equals(MoJsonRule.ruleSetCheck(rules, activeFeatures));
            } catch (Exception e) {
                return true;
            }
        } else if (rulesObj instanceof MoJsonRule[]) {
            return "allow".equals(MoJsonRule.ruleSetCheck((MoJsonRule[]) rulesObj, activeFeatures));
        }
        return true;
    }

    private static void parseArgIntoList(List<String> list, Object arg) {
        parseArgIntoList(list, arg, null);
    }

    private static void parseArgIntoList(List<String> list, Object arg, Map<String, Boolean> activeFeatures) {
        if (arg == null) return;
        if (arg instanceof String) {
            list.add((String) arg);
        } else if (arg instanceof Map) {
            Map<?, ?> map = (Map<?, ?>) arg;
            if (isRuleAllowed(map.get("rules"), activeFeatures)) {
                Object val = map.get("value");
                if (val == null) val = map.get("values");
                if (val instanceof String) {
                    list.add((String) val);
                } else if (val instanceof List) {
                    for (Object item : (List<?>) val) {
                        if (item != null) list.add(item.toString());
                    }
                }
            }
        } else if (arg instanceof JVersionList.Arguments.ArgValue) {
            JVersionList.Arguments.ArgValue av = (JVersionList.Arguments.ArgValue) arg;
            if (av.rules == null || "allow".equals(MoJsonRule.ruleSetCheck(av.rules, activeFeatures))) {
                if (av.value != null) list.add(av.value);
                if (av.values != null) list.addAll(Arrays.asList(av.values));
            }
        }
    }

    private static List<String> getMoJsonJvmArgs(String versionName) {
        JVersionList.Version versionInfo = Tools.getVersionInfo(versionName, false);
        // Parse Forge 1.17+ and NeoForge additional JVM Arguments
        if (versionInfo == null || versionInfo.arguments == null || versionInfo.arguments.jvm == null) {
            return Collections.emptyList();
        }

        Map<String, String> varArgMap = new ArrayMap<>();
        varArgMap.put("classpath_separator", ":");
        varArgMap.put("library_directory", Tools.DIR_HOME_LIBRARY);
        varArgMap.put("version_name", versionInfo.id);
        varArgMap.put("natives_directory", Tools.NATIVE_LIB_DIR);
        varArgMap.put("launcher_name", Tools.APP_NAME);
        varArgMap.put("launcher_version", "2.00");

        List<String> clientVmArgs = new ArrayList<>();
        for (Object arg : versionInfo.arguments.jvm) {
            parseArgIntoList(clientVmArgs, arg);
        }
        return JSONUtils.insertJSONValueList(clientVmArgs, varArgMap);
    }

    private static List<String> getMoJsonClientArgs(Account profile, JVersionList.Version versionInfo, File gameDir) {
        String username = profile.username;
        String versionName = versionInfo.id;
        if (versionInfo.inheritsFrom != null) {
            versionName = versionInfo.inheritsFrom;
        }

        String userType = "mojang";
        try {
            Date creationDate = DateUtils.getOriginalReleaseDate(versionInfo);
            // Minecraft 22w43a which adds chat reporting (and signing) was released on
            // 26th October 2022. So, if the date is not before that (meaning it is equal or higher)
            // change the userType to MSA to fix the missing signature
            if(creationDate != null && !DateUtils.dateBefore(creationDate, 2022, 9, 26)) {
                userType = "msa";
            }
        }catch (ParseException e) {
            Log.e("CheckForProfileKey", "Failed to determine profile creation date, using \"mojang\"", e);
        }


        Map<String, String> varArgMap = new ArrayMap<>();
        varArgMap.put("auth_session", profile.accessToken); // For legacy versions of MC
        varArgMap.put("auth_access_token", profile.accessToken);
        varArgMap.put("auth_player_name", username);
        varArgMap.put("auth_uuid", profile.profileId.replace("-", ""));
        varArgMap.put("auth_xuid", profile.xuid);
        varArgMap.put("assets_root", Tools.ASSETS_PATH);
        varArgMap.put("assets_index_name", versionInfo.assets);
        varArgMap.put("game_assets", Tools.ASSETS_PATH + File.separator + "virtual" + File.separator + versionInfo.assets);
        varArgMap.put("game_directory", gameDir.getAbsolutePath());
        varArgMap.put("user_properties", "{}");
        varArgMap.put("user_type", userType);
        varArgMap.put("version_name", versionName);
        varArgMap.put("version_type", versionInfo.type);

        List<String> clientArgs = new ArrayList<>();
        if (versionInfo.arguments != null && versionInfo.arguments.game != null) {
            // Support Minecraft 1.13+
            for (Object arg : versionInfo.arguments.game) {
                parseArgIntoList(clientArgs, arg);
            }
        }
        if(versionInfo.minecraftArguments != null){
            clientArgs.addAll(splitAndFilterEmpty(versionInfo.minecraftArguments));
        }
        List<String> rawClientArgs = JSONUtils.insertJSONValueList(clientArgs, varArgMap);
        return sanitizeQuickPlayArgs(rawClientArgs);
    }

    public static List<String> sanitizeQuickPlayArgs(List<String> rawArgs) {
        if (rawArgs == null || rawArgs.isEmpty()) return rawArgs;

        List<String> result = new ArrayList<>();
        String quickPlayTargetArg = null;
        String quickPlayTargetVal = null;
        String quickPlayPathVal = null;

        for (int i = 0; i < rawArgs.size(); i++) {
            String arg = rawArgs.get(i);
            if (arg == null) continue;

            if (arg.equals("--quickPlaySingleplayer") || arg.equals("--quickPlayMultiplayer") || arg.equals("--quickPlayRealms")) {
                String val = (i + 1 < rawArgs.size()) ? rawArgs.get(i + 1) : null;
                i++;
                if (val != null && !val.trim().isEmpty() && !val.startsWith("${") && !val.startsWith("--")) {
                    if (quickPlayTargetArg == null) {
                        quickPlayTargetArg = arg;
                        quickPlayTargetVal = val;
                    }
                }
            } else if (arg.startsWith("--quickPlaySingleplayer=") || arg.startsWith("--quickPlayMultiplayer=") || arg.startsWith("--quickPlayRealms=")) {
                int eq = arg.indexOf('=');
                String key = arg.substring(0, eq);
                String val = arg.substring(eq + 1);
                if (!val.trim().isEmpty() && !val.startsWith("${")) {
                    if (quickPlayTargetArg == null) {
                        quickPlayTargetArg = key;
                        quickPlayTargetVal = val;
                    }
                }
            } else if (arg.equals("--quickPlayPath")) {
                String val = (i + 1 < rawArgs.size()) ? rawArgs.get(i + 1) : null;
                i++;
                if (val != null && !val.trim().isEmpty() && !val.startsWith("${") && !val.startsWith("--")) {
                    quickPlayPathVal = val;
                }
            } else if (arg.startsWith("--quickPlayPath=")) {
                String val = arg.substring("--quickPlayPath=".length());
                if (!val.trim().isEmpty() && !val.startsWith("${")) {
                    quickPlayPathVal = val;
                }
            } else if (arg.startsWith("${quick_play") || arg.equals("${quick_play_singleplayer}") || arg.equals("${quick_play_multiplayer}") || arg.equals("${quick_play_realms}") || arg.equals("${quick_play_path}")) {
                // Ignore stray placeholder
            } else {
                result.add(arg);
            }
        }

        if (quickPlayTargetArg != null && quickPlayTargetVal != null) {
            result.add(quickPlayTargetArg);
            result.add(quickPlayTargetVal);
            if (quickPlayPathVal != null) {
                result.add("--quickPlayPath");
                result.add(quickPlayPathVal);
            }
        }

        return result;
    }

    private static List<String> splitAndFilterEmpty(String argStr) {
        List<String> strList = new ArrayList<>();
        for (String arg : argStr.split(" ")) {
            if (!arg.isEmpty()) {
                strList.add(arg);
            }
        }
        return strList;
    }

    public static @NonNull String pickRuntime(Instance instance, int targetJavaVersion) {
        String runtime = Tools.getSelectedRuntime(instance);
        String profileRuntime = instance.selectedRuntime;
        Runtime pickedRuntime = runtime != null ? MultiRTUtils.read(runtime) : null;
        if(runtime == null || pickedRuntime == null || pickedRuntime.javaVersion == 0 || pickedRuntime.javaVersion < targetJavaVersion || !MultiRTUtils.isRuntimeValid(pickedRuntime)) {
            String preferredRuntime = MultiRTUtils.getNearestJreName(targetJavaVersion);
            if(preferredRuntime == null) {
                Runtime fallback = MultiRTUtils.getHighestValidRuntime();
                if (fallback != null) {
                    preferredRuntime = fallback.name;
                } else {
                    throw new RuntimeException("Failed to autopick runtime!");
                }
            }
            if(profileRuntime != null) {
                instance.selectedRuntime = preferredRuntime;
                instance.maybeWrite();
            }
            runtime = preferredRuntime;
        }
        return runtime;
    }
}
