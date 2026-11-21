package git.artdeell.mojo.librepack;

import android.content.ContentResolver;
import android.content.Context;
import android.net.Uri;
import android.util.Log;

import androidx.annotation.Nullable;

import git.artdeell.mojo.R;
import git.artdeell.mojo.Tools;
import git.artdeell.mojo.instances.Instance;
import git.artdeell.mojo.instances.Instances;
import git.artdeell.mojo.utils.FileUtils;
import git.artdeell.mojo.utils.JSONUtils;
import git.artdeell.mojo.utils.ZipUtils;

import org.apache.commons.io.IOUtils;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.Enumeration;

/**
 * Imports a .librepack archive as a new instance.
 * The archive layout must match what {@link LibrePackExporter} produces:
 *   instance.json  — instance metadata
 *   icon.webp      — optional custom icon
 *   data/          — game directory contents
 */
public final class LibrePackImporter {
    private static final String TAG = "LibrePackImporter";

    private LibrePackImporter() {}

    public interface ResultListener {
        void onSuccess(String instanceName);
        void onError(String message);
    }

    /**
     * Copies the URI to cache, then extracts it as a new instance. Must be called from a
     * background thread.
     */
    public static void importAsync(Context context, Uri uri, ResultListener listener) {
        Context appContext = context.getApplicationContext();
        ContentResolver cr = appContext.getContentResolver();

        String fileName = Tools.getFileName(appContext, uri);
        if (fileName == null) fileName = "import.librepack";
        File tmpFile = new File(Tools.DIR_CACHE, fileName + ".lp.tmp");
        try (InputStream in = cr.openInputStream(uri);
             OutputStream out = new FileOutputStream(tmpFile)) {
            if (in == null) {
                listener.onError(appContext.getString(R.string.librepack_import_error_read));
                return;
            }
            IOUtils.copy(in, out);
        } catch (IOException e) {
            Log.e(TAG, "Copy to cache failed", e);
            tmpFile.delete();
            listener.onError(appContext.getString(R.string.librepack_import_error_read));
            return;
        }

        try {
            String instanceName = extractPack(tmpFile);
            listener.onSuccess(instanceName);
        } catch (IOException e) {
            Log.e(TAG, "Extraction failed", e);
            listener.onError(appContext.getString(R.string.librepack_import_error_extract) + ": " + e.getMessage());
        } finally {
            tmpFile.delete();
        }
    }

    private static String extractPack(File zipFile) throws IOException {
        try (ZipFile zip = new ZipFile(zipFile)) {
            // Read instance.json to find or build the instance name
            ZipEntry metaEntry = zip.getEntry("instance.json");
            if (metaEntry == null) {
                throw new IOException("Not a valid .librepack — missing instance.json");
            }

            Instance stub;
            try (InputStream metaStream = zip.getInputStream(metaEntry)) {
                String json = IOUtils.toString(metaStream, "UTF-8");
                stub = Tools.GLOBAL_GSON.fromJson(json, Instance.class);
            }
            if (stub == null) throw new IOException("Could not parse instance.json");

            String nameHint = (stub.name != null && !stub.name.trim().isEmpty())
                    ? stub.name.trim() : "librepack_import";

            // Create a fresh instance shell
            final String[] createdName = {null};
            Instance newInstance = Instances.createInstance(inst -> {
                inst.name        = stub.name;
                inst.versionId   = stub.versionId;
                inst.icon        = stub.icon;
                inst.loader      = stub.loader;
                inst.renderer    = stub.renderer;
                inst.driver      = stub.driver;
                inst.jvmArgs     = stub.jvmArgs;
                inst.argsMode    = stub.argsMode;
                inst.selectedRuntime = stub.selectedRuntime;
                inst.controlLayout   = stub.controlLayout;
                inst.sharedData  = false;
                createdName[0] = inst.name;
            }, nameHint);

            String instanceKey = newInstance.getKey();
            File instanceRoot = new File(Instances.getInstancePath(), instanceKey);
            File gameDir = newInstance.getGameDirectory();

            // Extract icon.webp if present
            ZipEntry iconEntry = zip.getEntry("icon.webp");
            if (iconEntry != null) {
                File iconDest = new File(instanceRoot, "icon.webp");
                FileUtils.ensureParentDirectory(iconDest);
                try (InputStream is = zip.getInputStream(iconEntry);
                     OutputStream os = new FileOutputStream(iconDest)) {
                    IOUtils.copy(is, os);
                }
            }

            // Extract data/ into the game directory
            FileUtils.ensureDirectory(gameDir);
            Enumeration<? extends ZipEntry> entries = zip.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                String name = entry.getName();
                if (!name.startsWith("data/") || entry.isDirectory()) continue;
                String relative = name.substring("data/".length());
                File dest = new File(gameDir, relative);
                FileUtils.ensureParentDirectory(dest);
                try (InputStream is = zip.getInputStream(entry);
                     OutputStream os = new FileOutputStream(dest)) {
                    IOUtils.copy(is, os);
                }
            }

            return newInstance.name != null ? newInstance.name : instanceKey;
        }
    }
}
