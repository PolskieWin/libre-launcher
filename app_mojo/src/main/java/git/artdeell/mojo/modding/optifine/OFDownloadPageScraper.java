package git.artdeell.mojo.modding.optifine;

import org.htmlcleaner.HtmlCleaner;
import org.htmlcleaner.HtmlNode;
import org.htmlcleaner.TagNode;
import org.htmlcleaner.TagNodeVisitor;

import java.io.IOException;
import java.net.URL;

public class OFDownloadPageScraper implements TagNodeVisitor {
    public static String run(String urlInput) throws IOException{
        return new OFDownloadPageScraper().runInner(urlInput);
    }

    private String mDownloadFullUrl;

    private String runInner(String url) throws IOException {
        try {
            HtmlCleaner htmlCleaner = new HtmlCleaner();
            htmlCleaner.clean(new URL(url)).traverse(this);
            if (mDownloadFullUrl != null) return mDownloadFullUrl;
        } catch (Exception ignored) {}

        try {
            String pageHtml = git.artdeell.mojo.utils.DownloadUtils.downloadString(url);
            java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("(downloadx?\\?f=[^\"'>\\s]+)").matcher(pageHtml);
            if (matcher.find()) {
                String match = matcher.group(1);
                return "https://optifine.net/" + match;
            }
        } catch (Exception ignored) {}

        return mDownloadFullUrl != null ? mDownloadFullUrl : url;
    }

    @Override
    public boolean visit(TagNode parentNode, HtmlNode htmlNode) {
        if(isDownloadUrl(parentNode, htmlNode)) {
            TagNode tagNode = (TagNode) htmlNode;
            String href = tagNode.getAttributeByName("href");
            if(!href.startsWith("https://")) href = "https://optifine.net/"+href;
            this.mDownloadFullUrl = href;
            return false;
        }
        return true;
    }

    public boolean isDownloadUrl(TagNode parentNode, HtmlNode htmlNode) {
        if(!(htmlNode instanceof TagNode)) return false;
        if(parentNode == null) return false;
        TagNode tagNode = (TagNode) htmlNode;
        if(!(parentNode.getName().equals("span")
            && "Download".equals(parentNode.getAttributeByName("id")))) return false;
        return tagNode.getName().equals("a") &&
                "onDownload()".equals(tagNode.getAttributeByName("onclick"));
    }
}
