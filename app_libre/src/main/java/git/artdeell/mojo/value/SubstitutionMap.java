package git.artdeell.mojo.value;

import git.artdeell.mojo.utils.maven.MavenName;

import java.util.HashMap;
import java.util.Map;

public class SubstitutionMap {
    public LibraryMap libraries;
    public ExtraNameMap artifactMapping;

    public LibrarySubstitution findSubstitution(MavenName mavenName) {
        if (mavenName == null) return null;
        switch (mavenName.provider) {
            case "org.lwjgl":
            case "org.lwjgl.lwjgl":
            case "net.java.jinput":
                LibrarySubstitution sub = libraries.get(mavenName);
                if (sub != null) return sub;
                if (mavenName.extra != null && mavenName.extra.contains("unsafe")) {
                    MavenName cleanName = new MavenName(mavenName.provider, mavenName.module, mavenName.version, null);
                    sub = libraries.get(cleanName);
                    if (sub != null) return sub;
                }
                if (mavenName.version != null && mavenName.version.contains("unsafe")) {
                    String cleanVer = mavenName.version.replace("-unsafe", "").replace(":unsafe", "");
                    MavenName cleanName = new MavenName(mavenName.provider, mavenName.module, cleanVer, mavenName.extra);
                    sub = libraries.get(cleanName);
                    if (sub != null) return sub;
                }
                return null;
            default:
                return null;
        }
    }

    public SubstitutionMap prepare() {
        for(Map.Entry<MavenName, MavenName> mappingPair : artifactMapping.entrySet()) {
            libraries.put(mappingPair.getKey(), libraries.get(mappingPair.getValue()));
        }
        artifactMapping.clear();
        return this;
    }

    public static class ExtraNameMap extends HashMap<MavenName, MavenName> {}
    public static class LibraryMap extends HashMap<MavenName, LibrarySubstitution> {}
}
