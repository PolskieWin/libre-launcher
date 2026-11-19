package git.artdeell.mojo.update;

import androidx.annotation.Keep;
import com.google.gson.annotations.SerializedName;
import java.util.List;

@Keep
public class ReleaseInfo {
    @SerializedName("tag_name")
    public String tagName;

    @SerializedName("name")
    public String name;

    @SerializedName("body")
    public String body;

    @SerializedName("html_url")
    public String htmlUrl;

    @SerializedName("assets")
    public List<Asset> assets;

    @Keep
    public static class Asset {
        @SerializedName("name")
        public String name;

        @SerializedName("size")
        public long size;

        @SerializedName("browser_download_url")
        public String browserDownloadUrl;
    }
}
