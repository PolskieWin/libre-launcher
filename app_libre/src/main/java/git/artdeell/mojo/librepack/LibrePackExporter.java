package git.artdeell.mojo.librepack;

import androidx.annotation.Nullable;

import git.artdeell.mojo.instances.Instance;
import git.artdeell.mojo.instances.Instances;
import git.artdeell.mojo.utils.ZipUtils;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.zip.ZipOutputStream;
/**
 * Packs an instance directory into a .librepack archive (a plain ZIP).
 * Layout inside the archive:
 *   instance.json          — instance metadata (copied verbatim)
 *   icon.webp              — custom icon, if present
 *   data/                  — full recursive copy of the instance game directory
 *
 * The archive is written to a temporary file first so callers get an atomic result.
 */
public final class LibrePackExporter {

    public interface ProgressListener {
        void onProgress(String message);
    }

    private LibrePackExporter() {}

    /**
     * @param instance   the instance to export
     * @param outputFile destination .librepack file
     * @param listener   optional progress callback, called on the calling thread
     * @throws IOException on any I/O failure
     */
    public static void export(Instance instance, File outputFile,
                              @Nullable ProgressListener listener) throws IOException {
        // mInstanceRoot is protected; derive the instance root from its key
        String instanceKey = instance.getKey();
        File instanceRoot = (instanceKey != null)
                ? new File(Instances.getInstancePath(), instanceKey)
                : null;
        File gameDir = instance.getGameDirectory();

        File tmpOut = new File(outputFile.getParentFile(), outputFile.getName() + ".tmp");
        try (FileOutputStream fos = new FileOutputStream(tmpOut);
             ZipOutputStream zos = new ZipOutputStream(fos)) {

            // 1. instance.json
            File metadata = new File(instanceRoot, "instance.json");
            if (metadata.exists()) {
                if (listener != null) listener.onProgress("instance.json");
                ZipUtils.putFile(zos, "instance.json", metadata);
            }

            // 2. custom icon
            File icon = new File(instanceRoot, "icon.webp");
            if (icon.exists()) {
                if (listener != null) listener.onProgress("icon.webp");
                ZipUtils.putFile(zos, "icon.webp", icon);
            }

            // 3. game directory contents under data/
            if (gameDir != null && gameDir.isDirectory()) {
                packDirectory(zos, gameDir, "data/", listener);
            }

            zos.finish();
        } catch (IOException e) {
            tmpOut.delete();
            throw e;
        }

        if (outputFile.exists() && !outputFile.delete()) {
            tmpOut.delete();
            throw new IOException("Could not replace existing file: " + outputFile);
        }
        if (!tmpOut.renameTo(outputFile)) {
            tmpOut.delete();
            throw new IOException("Could not finalize export file: " + outputFile);
        }
    }

    private static void packDirectory(ZipOutputStream zos, File dir,
                                      String prefix,
                                      @Nullable ProgressListener listener) throws IOException {
        File[] entries = dir.listFiles();
        if (entries == null) return;
        for (File entry : entries) {
            String entryPath = prefix + entry.getName();
            if (entry.isDirectory()) {
                packDirectory(zos, entry, entryPath + "/", listener);
            } else {
                if (listener != null) listener.onProgress(entry.getName());
                ZipUtils.putFile(zos, entryPath, entry);
            }
        }
    }
}
