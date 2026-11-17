package git.artdeell.mojo.utils;

import android.os.Build;
import android.system.Os;
import android.util.Log;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

public class SocUtils {
    private static final String TAG = "SocUtils";
    private static final Map<String, String> SOC_NAMES = new HashMap<>();

    static {
        // Snapdragon 8 series
        SOC_NAMES.put("SM8750", "Snapdragon 8 Elite (SM8750)");
        SOC_NAMES.put("SM8650", "Snapdragon 8 Gen 3 (SM8650)");
        SOC_NAMES.put("SM8550", "Snapdragon 8 Gen 2 (SM8550)");
        SOC_NAMES.put("SM8475", "Snapdragon 8+ Gen 1 (SM8475)");
        SOC_NAMES.put("SM8450", "Snapdragon 8 Gen 1 (SM8450)");
        SOC_NAMES.put("SM8350", "Snapdragon 888 (SM8350)");
        SOC_NAMES.put("SM8250", "Snapdragon 865 (SM8250)");
        SOC_NAMES.put("SM8150", "Snapdragon 855 (SM8150)");
        SOC_NAMES.put("SDM845", "Snapdragon 845 (SDM845)");
        SOC_NAMES.put("SDM850", "Snapdragon 850 (SDM850)");
        SOC_NAMES.put("MSM8998", "Snapdragon 835 (MSM8998)");
        SOC_NAMES.put("MSM8996", "Snapdragon 820 (MSM8996)");

        // Snapdragon 7 series
        SOC_NAMES.put("SM7675", "Snapdragon 7+ Gen 3 (SM7675)");
        SOC_NAMES.put("SM7550", "Snapdragon 7 Gen 3 (SM7550)");
        SOC_NAMES.put("SM7475", "Snapdragon 7+ Gen 2 (SM7475)");
        SOC_NAMES.put("SM7450", "Snapdragon 7 Gen 1 (SM7450)");
        SOC_NAMES.put("SM7325", "Snapdragon 778G (SM7325)");
        SOC_NAMES.put("SM7225", "Snapdragon 750G (SM7225)");
        SOC_NAMES.put("SM7150", "Snapdragon 730G (SM7150)");
        SOC_NAMES.put("SM7125", "Snapdragon 720G (SM7125)");

        // Snapdragon 6 & 4 series
        SOC_NAMES.put("SM6450", "Snapdragon 6 Gen 1 (SM6450)");
        SOC_NAMES.put("SM6375", "Snapdragon 695 (SM6375)");
        SOC_NAMES.put("SM6225", "Snapdragon 680 (SM6225)");
        SOC_NAMES.put("SM6125", "Snapdragon 665 (SM6125)");
        SOC_NAMES.put("SM6115", "Snapdragon 662 (SM6115)");
        SOC_NAMES.put("SDM660", "Snapdragon 660 (SDM660)");
        SOC_NAMES.put("SDM636", "Snapdragon 636 (SDM636)");
        SOC_NAMES.put("SM4450", "Snapdragon 4 Gen 2 (SM4450)");
        SOC_NAMES.put("SM4375", "Snapdragon 4 Gen 1 (SM4375)");
        SOC_NAMES.put("SM4350", "Snapdragon 480 (SM4350)");
        SOC_NAMES.put("SM4250", "Snapdragon 460 (SM4250)");

        // Qualcomm platforms
        SOC_NAMES.put("taro", "Snapdragon 8 Gen 1 (taro)");
        SOC_NAMES.put("kalama", "Snapdragon 8 Gen 2 (kalama)");
        SOC_NAMES.put("pineapple", "Snapdragon 8 Gen 3 (pineapple)");
        SOC_NAMES.put("sun", "Snapdragon 8 Elite (sun)");
        SOC_NAMES.put("lahaina", "Snapdragon 888 (lahaina)");
        SOC_NAMES.put("kona", "Snapdragon 865 (kona)");
        SOC_NAMES.put("msmnile", "Snapdragon 855 (msmnile)");

        // MediaTek
        SOC_NAMES.put("MT6991", "Dimensity 9400 (MT6991)");
        SOC_NAMES.put("MT6989", "Dimensity 9300 (MT6989)");
        SOC_NAMES.put("MT6985", "Dimensity 9200 (MT6985)");
        SOC_NAMES.put("MT6983", "Dimensity 9000 (MT6983)");
        SOC_NAMES.put("MT6897", "Dimensity 8300 (MT6897)");
        SOC_NAMES.put("MT6895", "Dimensity 8100 (MT6895)");
        SOC_NAMES.put("MT6893", "Dimensity 1200 (MT6893)");
        SOC_NAMES.put("MT6891", "Dimensity 1100 (MT6891)");
        SOC_NAMES.put("MT6886", "Dimensity 7200 (MT6886)");
        SOC_NAMES.put("MT6877", "Dimensity 900 (MT6877)");
        SOC_NAMES.put("MT6875", "Dimensity 820 (MT6875)");
        SOC_NAMES.put("MT6853", "Dimensity 720 (MT6853)");
        SOC_NAMES.put("MT6833", "Dimensity 700 (MT6833)");
        SOC_NAMES.put("MT6789", "Helio G99 (MT6789)");
        SOC_NAMES.put("MT6785", "Helio G90/G95 (MT6785)");
        SOC_NAMES.put("MT6769", "Helio G80/G85 (MT6769)");

        // Google Tensor
        SOC_NAMES.put("GS101", "Google Tensor G1 (GS101)");
        SOC_NAMES.put("GS201", "Google Tensor G2 (GS201)");
        SOC_NAMES.put("G5300", "Google Tensor G3 (G5300)");
        SOC_NAMES.put("G5400", "Google Tensor G4 (G5400)");
        SOC_NAMES.put("zuma", "Google Tensor G3 (zuma)");
        SOC_NAMES.put("zumapro", "Google Tensor G4 (zumapro)");
        SOC_NAMES.put("whitechapel", "Google Tensor G1");

        // Samsung Exynos
        SOC_NAMES.put("s5e9945", "Exynos 2400 (s5e9945)");
        SOC_NAMES.put("s5e9925", "Exynos 2200 (s5e9925)");
        SOC_NAMES.put("s5e9840", "Exynos 2100 (s5e9840)");
        SOC_NAMES.put("s5e9830", "Exynos 990 (s5e9830)");
        SOC_NAMES.put("s5e9820", "Exynos 9820 (s5e9820)");
        SOC_NAMES.put("s5e8845", "Exynos 1480 (s5e8845)");
        SOC_NAMES.put("s5e8835", "Exynos 1380 (s5e8835)");
        SOC_NAMES.put("s5e8825", "Exynos 1280 (s5e8825)");
    }

    public static String getSystemProperty(String key) {
        try {
            Class<?> c = Class.forName("android.os.SystemProperties");
            Method m = c.getMethod("get", String.class);
            String val = (String) m.invoke(null, key);
            return (val != null && !val.isEmpty()) ? val : null;
        } catch (Throwable ignored) {
            return null;
        }
    }

    public static String getSocModel() {
        String raw = null;
        if (Build.VERSION.SDK_INT >= 31) {
            try {
                java.lang.reflect.Field f = Build.class.getField("SOC_MODEL");
                Object val = f.get(null);
                if (val instanceof String && !((String) val).isEmpty() && !Build.UNKNOWN.equals(val)) {
                    raw = (String) val;
                }
            } catch (Throwable ignored) {}
        }
        if (raw == null) raw = getSystemProperty("ro.soc.model");
        if (raw == null) raw = getSystemProperty("ro.board.platform");
        if (raw == null) raw = Build.HARDWARE;

        if (raw != null) {
            String trimmed = raw.trim();
            String mapped = SOC_NAMES.get(trimmed);
            if (mapped != null) return mapped;
            mapped = SOC_NAMES.get(trimmed.toUpperCase());
            if (mapped != null) return mapped;
            mapped = SOC_NAMES.get(trimmed.toLowerCase());
            if (mapped != null) return mapped;
            return trimmed;
        }
        return "ARM64 Processor";
    }

    public static File setupFakeProc(File cacheDir) {
        if (cacheDir == null) return null;
        File fakeProcDir = new File(cacheDir, "fake_proc");
        if (!fakeProcDir.exists() && !fakeProcDir.mkdirs()) {
            return null;
        }

        File procReal = new File("/proc");
        File[] entries = procReal.listFiles();
        if (entries != null) {
            for (File entry : entries) {
                String name = entry.getName();
                if ("cpuinfo".equals(name)) continue;
                File link = new File(fakeProcDir, name);
                if (!link.exists()) {
                    try {
                        Os.symlink(entry.getAbsolutePath(), link.getAbsolutePath());
                    } catch (Throwable ignored) {}
                }
            }
        }

        String soc = getSocModel();
        File cpuinfoReal = new File("/proc/cpuinfo");
        File cpuinfoOut = new File(fakeProcDir, "cpuinfo");
        try (BufferedReader reader = new BufferedReader(new FileReader(cpuinfoReal));
             BufferedWriter writer = new BufferedWriter(new FileWriter(cpuinfoOut))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.startsWith("processor\t") || line.startsWith("processor ")) {
                    writer.write(line);
                    writer.newLine();
                    writer.write("model name\t: " + soc);
                    writer.newLine();
                } else if (!line.startsWith("model name") && !line.startsWith("Hardware")) {
                    writer.write(line);
                    writer.newLine();
                }
            }
            writer.write("Hardware\t: " + soc);
            writer.newLine();
        } catch (Throwable t) {
            Log.w(TAG, "Failed to create fake cpuinfo", t);
            return null;
        }

        return fakeProcDir;
    }
}
