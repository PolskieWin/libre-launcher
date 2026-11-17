package git.artdeell.mojo.modding;

public class ComparableVersionString implements Comparable<ComparableVersionString> {
    private int major;
    private int minor;
    private int patch;
    private final String original;
    private final boolean isValid;

    private ComparableVersionString(String str) {
        this.original = str;
        this.isValid = false;
    }

    public ComparableVersionString(String original, int major, int minor, int patch) {
        this.major = major;
        this.minor = minor;
        this.patch = patch;
        this.original = original;
        this.isValid = true;
    }

    @Override
    public int compareTo(ComparableVersionString str) {
        if (!this.isValid && !str.isValid) {
            return this.original.compareTo(str.original);
        }
        if (!this.isValid) return -1;
        if (!str.isValid) return 1;

        if (this.major != str.major) return Integer.compare(this.major, str.major);
        if (this.minor != str.minor) return Integer.compare(this.minor, str.minor);
        if (this.patch != str.patch) return Integer.compare(this.patch, str.patch);
        return this.original.compareTo(str.original);
    }

    public String getOriginal() {
        return original;
    }

    /**
     * @return the original but if the patch was .0 it will not include it, e.g.
     *         "1.20.0" -> "1.20"
     */
    public String getProper() {
        if(!this.isValid) return original;

        StringBuilder sb = new StringBuilder();
        sb.append(major);
        sb.append('.');
        sb.append(minor);
        if(patch != 0) {
            sb.append('.');
            sb.append(patch);
        }
        return sb.toString();
    }

    public boolean isValid() {
        return isValid;
    }

    public static ComparableVersionString parse(String str) {
        if (str == null) return new ComparableVersionString("");
        String[] split = str.trim().split("\\.");
        if (split.length < 2) return new ComparableVersionString(str);

        try {
            int major = parseLeadingInt(split[0]);
            int minor = parseLeadingInt(split[1]);
            int patch = 0;
            if (split.length >= 3) {
                patch = parseLeadingInt(split[2]);
            } else {
                java.util.regex.Matcher m = java.util.regex.Pattern.compile("(?:snapshot|pre|rc)[\\s-]*(\\d+)", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(split[1]);
                if (m.find()) {
                    patch = Integer.parseInt(m.group(1));
                }
            }
            return new ComparableVersionString(str, major, minor, patch);
        } catch (Exception e) {
            return new ComparableVersionString(str);
        }
    }

    private static int parseLeadingInt(String s) {
        if (s == null) return 0;
        int i = 0;
        while (i < s.length() && Character.isDigit(s.charAt(i))) {
            i++;
        }
        return i > 0 ? Integer.parseInt(s.substring(0, i)) : 0;
    }
}