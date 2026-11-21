package git.artdeell.mojo.modding;

import android.app.Activity;
import android.content.Context;
import android.os.Build;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;

import git.artdeell.mojo.R;
import git.artdeell.mojo.instances.Instance;

public final class PrebootScanner {
    private static final String TAG = "PrebootScanner";

    static {
        try {
            System.loadLibrary("mojort");
        } catch (Throwable t) {
            Log.e(TAG, "Failed to load mojort library", t);
        }
    }

    private PrebootScanner() {}

    public static class ScanIssue {
        public String type = "";
        public String modId = "";
        public String title = "";
        public String message = "";
        public String affectedFile = "";
        public String fixAction = ""; // "DISABLE_FILE" | "SWITCH_RENDERER"
        public String fixTarget = "";
    }

    public static class ScanReport {
        public boolean hasIssues;
        public int scannedModCount;
        public final List<ScanIssue> issues = new ArrayList<>();
    }

    public static native String nativeScanMods(String modsDirPath, String renderer, String abi);

    private static class ModInfo {
        String modId = "";
        String name = "";
        String version = "";
        String fileName = "";
        final Map<String, String> depends = new HashMap<>();
        final Set<String> provides = new HashSet<>();
        boolean hasX86Native = false;
        boolean hasArm64Native = false;
    }

    public static boolean isPlatformDependency(String dep) {
        if (dep == null || dep.trim().isEmpty()) return true;
        String d = dep.trim().toLowerCase(Locale.ROOT);
        switch (d) {
            case "minecraft":
            case "java":
            case "fabricloader":
            case "fabric-loader":
            case "fabric":
            case "fabric-api-base":
            case "fabric-api":
            case "quilt_loader":
            case "quilt-loader":
            case "quilted_fabric_api":
            case "mixinextras":
            case "mixinsquared":
            case "fabric-language-kotlin":
            case "fabric-language-scala":
            case "forge":
            case "neoforge":
            case "neoforged":
            case "neoforge_loader":
            case "fml":
            case "minecraftforge":
            case "net.minecraftforge":
            case "net.neoforged":
            case "kotlinforforge":
            case "scalaforsmartforge":
            case "modmenu":
                return true;
            default:
                if (d.startsWith("java-") || d.startsWith("minecraft-")) {
                    return true;
                }
                return false;
        }
    }

    public static ScanReport scanInstance(@Nullable Instance instance) {
        ScanReport report = new ScanReport();
        if (instance == null) return report;

        File gameDir = instance.getGameDirectory();
        if (gameDir == null) return report;

        File modsDir = new File(gameDir, "mods");
        if (!modsDir.exists() || !modsDir.isDirectory()) {
            return report;
        }

        String renderer = instance.getLaunchRenderer();
        if (renderer == null) renderer = "libre_mobile";
        String abi = (Build.SUPPORTED_ABIS != null && Build.SUPPORTED_ABIS.length > 0)
                ? Build.SUPPORTED_ABIS[0] : "arm64-v8a";

        File[] files = modsDir.listFiles((dir, name) -> name.toLowerCase(Locale.ROOT).endsWith(".jar"));
        if (files == null || files.length == 0) {
            return report;
        }

        List<ModInfo> scannedMods = new ArrayList<>();
        Set<String> nestedModIds = new HashSet<>();

        for (File file : files) {
            ModInfo mod = scanJarFile(file, nestedModIds);
            if (mod != null) {
                scannedMods.add(mod);
            }
        }

        report.scannedModCount = scannedMods.size();

        // 1. Check Duplicate Mod IDs
        Map<String, ModInfo> seenModIds = new HashMap<>();
        for (ModInfo mod : scannedMods) {
            if (mod.modId == null || mod.modId.isEmpty()) continue;
            String lowerId = mod.modId.toLowerCase(Locale.ROOT);
            if (seenModIds.containsKey(lowerId)) {
                ModInfo firstMod = seenModIds.get(lowerId);
                ScanIssue issue = new ScanIssue();
                issue.type = "DUPLICATE_MOD";
                issue.modId = mod.modId;
                issue.title = "Duplicate Mod: " + mod.name;
                issue.message = "Multiple JAR files provide mod ID '" + mod.modId + "' (" + (firstMod != null ? firstMod.fileName : "existing") + " and " + mod.fileName + "). Duplicate mods cause crash loops.";
                issue.affectedFile = mod.fileName;
                issue.fixAction = "DISABLE_FILE";
                issue.fixTarget = mod.fileName;
                report.issues.add(issue);
            } else {
                seenModIds.put(lowerId, mod);
            }
        }

        // 2. Check Missing Dependencies
        Set<String> installedIds = new HashSet<>();
        for (ModInfo mod : scannedMods) {
            if (mod.modId != null && !mod.modId.isEmpty()) {
                installedIds.add(mod.modId.toLowerCase(Locale.ROOT));
            }
            for (String prov : mod.provides) {
                if (prov != null && !prov.isEmpty()) {
                    installedIds.add(prov.toLowerCase(Locale.ROOT));
                }
            }
        }
        installedIds.addAll(nestedModIds);

        for (ModInfo mod : scannedMods) {
            for (Map.Entry<String, String> entry : mod.depends.entrySet()) {
                String depId = entry.getKey();
                if (isPlatformDependency(depId)) {
                    continue;
                }

                if (!installedIds.contains(depId.toLowerCase(Locale.ROOT))) {
                    ScanIssue issue = new ScanIssue();
                    issue.type = "MISSING_DEPENDENCY";
                    issue.modId = mod.modId;
                    issue.title = "Missing Dependency: " + depId;
                    String ver = entry.getValue();
                    if (ver != null && !ver.isEmpty() && !"*".equals(ver)) {
                        issue.message = "Mod '" + mod.name + "' requires '" + depId + "' (" + ver + "), which is missing from your mods folder.";
                    } else {
                        issue.message = "Mod '" + mod.name + "' requires '" + depId + "', which is missing from your mods folder.";
                    }
                    issue.affectedFile = mod.fileName;
                    issue.fixAction = "DISABLE_FILE";
                    issue.fixTarget = mod.fileName;
                    report.issues.add(issue);
                }
            }
        }

        // 3. Check Incompatible x86 Native Libraries on ARM
        boolean isArm = (abi.toLowerCase(Locale.ROOT).contains("arm") || abi.toLowerCase(Locale.ROOT).contains("aarch"));
        if (isArm) {
            for (ModInfo mod : scannedMods) {
                if (mod.hasX86Native && !mod.hasArm64Native) {
                    ScanIssue issue = new ScanIssue();
                    issue.type = "INCOMPATIBLE_NATIVE";
                    issue.modId = mod.modId;
                    issue.title = "Incompatible x86 Native Lib: " + mod.name;
                    issue.message = "'" + mod.fileName + "' bundles PC/x86 native libraries with no Android ARM64 support.";
                    issue.affectedFile = mod.fileName;
                    issue.fixAction = "DISABLE_FILE";
                    issue.fixTarget = mod.fileName;
                    report.issues.add(issue);
                }
            }
        }

        // 4. Check Incompatible Android Renderers
        boolean hasSodiumFamily = false;
        String sodiumFile = "";
        boolean hasVulkanMod = false;
        String vulkanFile = "";
        boolean hasCanvas = false;
        String canvasFile = "";
        boolean hasOptifine = false;
        String optiFile = "";

        for (ModInfo mod : scannedMods) {
            String id = mod.modId.toLowerCase(Locale.ROOT);
            if ("sodium".equals(id) || "embeddium".equals(id) || "iris".equals(id) ||
                "oculus".equals(id) || "xenon".equals(id) || "rubidium".equals(id)) {
                hasSodiumFamily = true;
                sodiumFile = mod.fileName;
            }
            if ("vulkanmod".equals(id) || id.contains("vulkan-mod")) {
                hasVulkanMod = true;
                vulkanFile = mod.fileName;
            }
            if ("canvas".equals(id)) {
                hasCanvas = true;
                canvasFile = mod.fileName;
            }
            if ("optifine".equals(id) || "optifabric".equals(id)) {
                hasOptifine = true;
                optiFile = mod.fileName;
            }
        }

        if (hasSodiumFamily && "opengles2".equalsIgnoreCase(renderer)) {
            ScanIssue issue = new ScanIssue();
            issue.type = "INCOMPATIBLE_RENDERER";
            issue.modId = "sodium";
            issue.title = "Sodium / Iris Incompatible with GL4ES";
            issue.message = "Sodium requires modern Core OpenGL and cannot run with GL4ES. Switch to Libre Mobile or LTW to prevent crashes.";
            issue.affectedFile = sodiumFile;
            issue.fixAction = "SWITCH_RENDERER";
            issue.fixTarget = "libre_mobile";
            report.issues.add(issue);
        }

        if (hasVulkanMod && ("opengles2".equalsIgnoreCase(renderer) || "opengles3_ltw".equalsIgnoreCase(renderer) || "opengles_mobileglues".equalsIgnoreCase(renderer))) {
            ScanIssue issue = new ScanIssue();
            issue.type = "INCOMPATIBLE_RENDERER";
            issue.modId = "vulkanmod";
            issue.title = "VulkanMod Incompatible with OpenGL";
            issue.message = "VulkanMod requires a Vulkan/Turnip driver and cannot run on OpenGL ES wrappers.";
            issue.affectedFile = vulkanFile;
            issue.fixAction = "DISABLE_FILE";
            issue.fixTarget = vulkanFile;
            report.issues.add(issue);
        }

        if (hasCanvas && ("opengles2".equalsIgnoreCase(renderer) || "opengles3_ltw".equalsIgnoreCase(renderer))) {
            ScanIssue issue = new ScanIssue();
            issue.type = "INCOMPATIBLE_RENDERER";
            issue.modId = "canvas";
            issue.title = "Canvas Incompatible with Mobile GL";
            issue.message = "Canvas Renderer requires desktop OpenGL (Zink) and crashes under GL4ES or LTW.";
            issue.affectedFile = canvasFile;
            issue.fixAction = "SWITCH_RENDERER";
            issue.fixTarget = "vulkan_zink";
            report.issues.add(issue);
        }

        if (hasOptifine && hasSodiumFamily) {
            ScanIssue issue = new ScanIssue();
            issue.type = "DUPLICATE_MOD";
            issue.modId = "optifine";
            issue.title = "Conflicting Shaders: OptiFine + Sodium";
            issue.message = "OptiFine and Sodium conflict directly. Please disable one of them to prevent crash loops.";
            issue.affectedFile = optiFile;
            issue.fixAction = "DISABLE_FILE";
            issue.fixTarget = optiFile;
            report.issues.add(issue);
        }

        report.hasIssues = !report.issues.isEmpty();
        return report;
    }

    @Nullable
    private static ModInfo scanJarFile(File jarFile, Set<String> nestedModIds) {
        ModInfo mod = new ModInfo();
        mod.fileName = jarFile.getName();

        boolean foundManifest = false;
        String fabricJson = null;
        String quiltJson = null;
        String modsToml = null;

        try (ZipFile zip = new ZipFile(jarFile)) {
            Enumeration<? extends ZipEntry> entries = zip.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                String name = entry.getName();

                if ("fabric.mod.json".equals(name)) {
                    fabricJson = readEntryUtf8(zip, entry);
                    foundManifest = true;
                } else if ("quilt.mod.json".equals(name)) {
                    quiltJson = readEntryUtf8(zip, entry);
                    foundManifest = true;
                } else if ("META-INF/mods.toml".equalsIgnoreCase(name) || "META-INF/neoforge.mods.toml".equalsIgnoreCase(name) || "mods.toml".equalsIgnoreCase(name) || "neoforge.mods.toml".equalsIgnoreCase(name)) {
                    modsToml = readEntryUtf8(zip, entry);
                    foundManifest = true;
                }

                if (name.endsWith(".so") || name.endsWith(".dll") || name.endsWith(".dylib")) {
                    String lower = name.toLowerCase(Locale.ROOT);
                    if (lower.contains("x86_64") || lower.contains("x86") || lower.contains("amd64") || lower.contains("i386") || lower.contains("linux-x86")) {
                        mod.hasX86Native = true;
                    }
                    if (lower.contains("aarch64") || lower.contains("arm64") || lower.contains("arm64-v8a")) {
                        mod.hasArm64Native = true;
                    }
                }

                if ((name.startsWith("META-INF/jars/") || name.startsWith("META-INF/jarJar/")) && name.endsWith(".jar") && !entry.isDirectory()) {
                    scanNestedJar(zip, entry, nestedModIds);
                }
            }

            if (!foundManifest) {
                return null;
            }

            if (fabricJson != null) {
                parseFabricManifest(fabricJson, mod, nestedModIds);
            } else if (quiltJson != null) {
                parseQuiltManifest(quiltJson, mod, nestedModIds);
            } else if (modsToml != null) {
                parseTomlManifest(modsToml, mod);
            }

            if (mod.modId.isEmpty()) {
                mod.modId = jarFile.getName();
            }
            if (mod.name.isEmpty()) {
                mod.name = mod.modId;
            }
            if (mod.version.isEmpty()) {
                mod.version = "1.0.0";
            }

            return mod;
        } catch (Throwable t) {
            Log.w(TAG, "Failed to scan jar: " + jarFile.getName(), t);
            return null;
        }
    }

    private static void parseFabricManifest(String jsonStr, ModInfo mod, Set<String> nestedModIds) {
        try {
            JSONObject obj = new JSONObject(stripJsonComments(jsonStr));
            mod.modId = obj.optString("id", "").trim();
            mod.name = obj.optString("name", mod.modId).trim();
            mod.version = obj.optString("version", "1.0.0").trim();

            Object providesObj = obj.opt("provides");
            if (providesObj instanceof JSONArray) {
                JSONArray arr = (JSONArray) providesObj;
                for (int i = 0; i < arr.length(); i++) {
                    String prov = arr.optString(i, "").trim();
                    if (!prov.isEmpty()) {
                        mod.provides.add(prov);
                        nestedModIds.add(prov.toLowerCase(Locale.ROOT));
                    }
                }
            }

            JSONObject dependsObj = obj.optJSONObject("depends");
            if (dependsObj != null) {
                Iterator<String> keys = dependsObj.keys();
                while (keys.hasNext()) {
                    String depId = keys.next().trim();
                    if (depId.isEmpty()) continue;
                    Object verObj = dependsObj.opt(depId);
                    String verStr = "*";
                    if (verObj instanceof String) {
                        verStr = (String) verObj;
                    } else if (verObj != null) {
                        verStr = verObj.toString();
                    }
                    mod.depends.put(depId, verStr);
                }
            }
        } catch (Throwable t) {
            Log.w(TAG, "Error parsing fabric.mod.json", t);
        }
    }

    private static void parseQuiltManifest(String jsonStr, ModInfo mod, Set<String> nestedModIds) {
        try {
            JSONObject obj = new JSONObject(stripJsonComments(jsonStr));
            JSONObject quiltLoader = obj.optJSONObject("quilt_loader");
            if (quiltLoader != null) {
                mod.modId = quiltLoader.optString("id", "").trim();
                JSONObject metadata = quiltLoader.optJSONObject("metadata");
                if (metadata != null) {
                    mod.name = metadata.optString("name", mod.modId).trim();
                    mod.version = metadata.optString("version", "1.0.0").trim();
                }
                Object providesObj = quiltLoader.opt("provides");
                if (providesObj instanceof JSONArray) {
                    JSONArray arr = (JSONArray) providesObj;
                    for (int i = 0; i < arr.length(); i++) {
                        String prov = arr.optString(i, "").trim();
                        if (!prov.isEmpty()) {
                            mod.provides.add(prov);
                            nestedModIds.add(prov.toLowerCase(Locale.ROOT));
                        }
                    }
                }
                Object dependsObj = quiltLoader.opt("depends");
                if (dependsObj instanceof JSONArray) {
                    JSONArray arr = (JSONArray) dependsObj;
                    for (int i = 0; i < arr.length(); i++) {
                        Object item = arr.get(i);
                        if (item instanceof String) {
                            String depId = ((String) item).trim();
                            if (!depId.isEmpty()) mod.depends.put(depId, "*");
                        } else if (item instanceof JSONObject) {
                            JSONObject dObj = (JSONObject) item;
                            String depId = dObj.optString("id", "").trim();
                            if (!depId.isEmpty()) {
                                String ver = dObj.optString("versions", "*");
                                mod.depends.put(depId, ver);
                            }
                        }
                    }
                }
            }
        } catch (Throwable t) {
            Log.w(TAG, "Error parsing quilt.mod.json", t);
        }
    }

    private static void parseTomlManifest(String toml, ModInfo mod) {
        if (toml == null) return;
        String[] lines = toml.split("\\r?\\n");
        boolean inMods = false;
        boolean inDeps = false;
        for (String rawLine : lines) {
            String line = rawLine.trim();
            if (line.isEmpty() || line.startsWith("#")) continue;
            if (line.startsWith("[[mods]]") || line.startsWith("[mods]")) {
                inMods = true;
                inDeps = false;
                continue;
            } else if (line.startsWith("[[dependencies") || line.startsWith("[dependencies")) {
                inDeps = true;
                inMods = false;
                continue;
            } else if (line.startsWith("[")) {
                inMods = false;
                inDeps = false;
                continue;
            }
            int eq = line.indexOf('=');
            if (eq < 0) continue;
            String key = line.substring(0, eq).trim();
            String val = line.substring(eq + 1).trim();
            int hash = val.indexOf('#');
            if (hash >= 0) val = val.substring(0, hash).trim();
            if ((val.startsWith("\"") && val.endsWith("\"")) || (val.startsWith("'") && val.endsWith("'"))) {
                if (val.length() >= 2) {
                    val = val.substring(1, val.length() - 1);
                }
            }
            if (inDeps) {
                if ("modId".equalsIgnoreCase(key) && !val.isEmpty()) {
                    mod.depends.put(val, "*");
                }
            } else if (inMods || mod.modId.isEmpty()) {
                if ("modId".equalsIgnoreCase(key)) {
                    mod.modId = val;
                } else if ("displayName".equalsIgnoreCase(key) || "name".equalsIgnoreCase(key)) {
                    mod.name = val;
                } else if ("version".equalsIgnoreCase(key)) {
                    mod.version = val;
                }
            }
        }
        if (mod.name == null || mod.name.isEmpty()) {
            mod.name = mod.modId;
        }
        if (mod.version == null || mod.version.isEmpty()) {
            mod.version = "1.0.0";
        }
    }

    private static void scanNestedJar(ZipFile zip, ZipEntry jarEntry, Set<String> nestedModIds) {
        try (InputStream is = zip.getInputStream(jarEntry);
             ZipInputStream zis = new ZipInputStream(is)) {
            ZipEntry subEntry;
            while ((subEntry = zis.getNextEntry()) != null) {
                String subName = subEntry.getName();
                if ("fabric.mod.json".equals(subName) || "quilt.mod.json".equals(subName)) {
                    ByteArrayOutputStream baos = new ByteArrayOutputStream();
                    byte[] buf = new byte[4096];
                    int len;
                    while ((len = zis.read(buf)) != -1) {
                        baos.write(buf, 0, len);
                    }
                    String subJson = baos.toString("UTF-8");
                    try {
                        JSONObject subObj = new JSONObject(stripJsonComments(subJson));
                        String id = subObj.optString("id", "").trim();
                        if (!id.isEmpty()) {
                            nestedModIds.add(id.toLowerCase(Locale.ROOT));
                        }
                        JSONObject ql = subObj.optJSONObject("quilt_loader");
                        if (ql != null) {
                            String qid = ql.optString("id", "").trim();
                            if (!qid.isEmpty()) {
                                nestedModIds.add(qid.toLowerCase(Locale.ROOT));
                            }
                        }
                        Object providesObj = subObj.opt("provides");
                        if (providesObj instanceof JSONArray) {
                            JSONArray arr = (JSONArray) providesObj;
                            for (int i = 0; i < arr.length(); i++) {
                                String prov = arr.optString(i, "").trim();
                                if (!prov.isEmpty()) {
                                    nestedModIds.add(prov.toLowerCase(Locale.ROOT));
                                }
                            }
                        }
                    } catch (Throwable t) {
                        Log.w(TAG, "Failed parsing nested manifest in " + jarEntry.getName(), t);
                    }
                }
                zis.closeEntry();
            }
        } catch (Throwable t) {
            Log.w(TAG, "Failed scanning nested jar: " + jarEntry.getName(), t);
        }
    }

    private static String readEntryUtf8(ZipFile zip, ZipEntry entry) throws Exception {
        try (InputStream is = zip.getInputStream(entry);
             ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            byte[] buf = new byte[4096];
            int r;
            while ((r = is.read(buf)) != -1) {
                baos.write(buf, 0, r);
            }
            return baos.toString("UTF-8");
        }
    }

    private static String stripJsonComments(String json) {
        if (json == null) return "";
        StringBuilder sb = new StringBuilder();
        boolean inString = false;
        for (int i = 0; i < json.length(); i++) {
            char c = json.charAt(i);
            if (c == '\\' && inString && i + 1 < json.length()) {
                sb.append(c).append(json.charAt(i + 1));
                i++;
                continue;
            }
            if (c == '\"') {
                inString = !inString;
                sb.append(c);
                continue;
            }
            if (!inString && c == '/' && i + 1 < json.length()) {
                if (json.charAt(i + 1) == '/') {
                    i += 2;
                    while (i < json.length() && json.charAt(i) != '\n') {
                        i++;
                    }
                    if (i < json.length()) sb.append('\n');
                    continue;
                } else if (json.charAt(i + 1) == '*') {
                    i += 2;
                    while (i + 1 < json.length() && !(json.charAt(i) == '*' && json.charAt(i + 1) == '/')) {
                        i++;
                    }
                    i++;
                    continue;
                }
            }
            sb.append(c);
        }
        return sb.toString();
    }

    public static boolean applyFixes(@NonNull Context context, @NonNull Instance instance, @NonNull ScanReport report) {
        boolean modified = false;
        File modsDir = new File(instance.getGameDirectory(), "mods");

        for (ScanIssue issue : report.issues) {
            if ("DISABLE_FILE".equalsIgnoreCase(issue.fixAction)) {
                if (issue.fixTarget != null && !issue.fixTarget.isEmpty()) {
                    File targetFile = new File(modsDir, issue.fixTarget);
                    if (targetFile.exists() && targetFile.isFile()) {
                        File disabledFile = new File(modsDir, issue.fixTarget + ".disabled");
                        if (targetFile.renameTo(disabledFile)) {
                            modified = true;
                        }
                    }
                }
            } else if ("SWITCH_RENDERER".equalsIgnoreCase(issue.fixAction)) {
                if (issue.fixTarget != null && !issue.fixTarget.isEmpty()) {
                    instance.renderer = issue.fixTarget;
                    instance.maybeWrite();
                    modified = true;
                }
            }
        }

        return modified;
    }

    public static void showWarningDialog(@NonNull Activity activity,
                                         @NonNull Instance instance,
                                         @NonNull ScanReport report,
                                         @NonNull Runnable onLaunch) {
        StringBuilder msg = new StringBuilder();
        msg.append(activity.getString(R.string.preboot_warning_intro)).append("\n\n");

        for (int i = 0; i < report.issues.size(); i++) {
            ScanIssue issue = report.issues.get(i);
            msg.append("• ").append(issue.title).append("\n");
            msg.append("  ").append(issue.message).append("\n");
            if (i + 1 < report.issues.size()) {
                msg.append("\n");
            }
        }

        new MaterialAlertDialogBuilder(activity, R.style.ThemeOverlay_Copper_MaterialAlertDialog)
                .setTitle(R.string.preboot_warning_title)
                .setMessage(msg.toString())
                .setPositiveButton(R.string.preboot_fix_and_launch, (dialog, which) -> {
                    boolean fixed = applyFixes(activity, instance, report);
                    if (fixed) {
                        Toast.makeText(activity, R.string.preboot_fixes_applied, Toast.LENGTH_SHORT).show();
                    }
                    onLaunch.run();
                })
                .setNeutralButton(R.string.preboot_launch_anyway, (dialog, which) -> {
                    onLaunch.run();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }
}
