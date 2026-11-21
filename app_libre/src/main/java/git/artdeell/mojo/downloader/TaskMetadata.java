package git.artdeell.mojo.downloader;

import androidx.annotation.NonNull;

import java.io.File;
import java.net.URL;

public class TaskMetadata {
    public File path;
    public URL url;
    public final int mirrorType;
    public long size;
    public String sha1Hash;
    public String sha512Hash;

    public TaskMetadata(File path, URL url, int mirrorType) {
        this.path = path;
        this.url = url;
        this.mirrorType = mirrorType;
    }

    public TaskMetadata(File path, URL url, long size, String hash, int mirrorType) {
        this(path, url, mirrorType);
        this.sha1Hash = hash;
        this.size = size;
    }

    public TaskMetadata(File path, URL url, long size, String sha1Hash, String sha512Hash, int mirrorType) {
        this(path, url, mirrorType);
        this.sha1Hash = sha1Hash;
        this.sha512Hash = sha512Hash;
        this.size = size;
    }

    @NonNull
    @Override
    public String toString() {
        return "TaskMetadata{\nurl="+url+";\npath="+path+"\nhash="+sha1Hash+";\nsha512="+sha512Hash+";\nsize="+size+"\n}";
    }
}
