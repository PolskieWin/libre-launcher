package git.artdeell.mojo.value;

import java.util.Map;
import git.artdeell.mojo.Architecture;

public class MoJsonRule {
    public String action;
    public OSDescriptor os;
    public Map<String, Boolean> features;

    public int getPrecedenceLevel() {
        int precedence = 0;
        if(os != null) precedence += os.getPrecedenceLevel();
        if(features != null) precedence += features.size();
        return precedence == 0 ? 1 : precedence;
    }

    public boolean matches(Map<String, Boolean> activeFeatures) {
        if(os != null && !os.matches()) return false;
        if(features != null) {
            for(Map.Entry<String, Boolean> entry : features.entrySet()) {
                boolean active = activeFeatures != null && Boolean.TRUE.equals(activeFeatures.get(entry.getKey()));
                if(entry.getValue() != null && entry.getValue() != active) {
                    return false;
                }
            }
        }
        return true;
    }

    public boolean matches() {
        return matches(null);
    }

    public static String ruleSetCheck(MoJsonRule[] rules, Map<String, Boolean> activeFeatures) {
        int precedenceLevel = 0;
        String action = "disallow";
        for(MoJsonRule rule : rules) {
            int ruleLevel = rule.getPrecedenceLevel();
            if(ruleLevel <= precedenceLevel) {
                continue;
            }
            if(rule.matches(activeFeatures)) action = rule.action;
            precedenceLevel = ruleLevel;
        }
        return action;
    }

    public static String ruleSetCheck(MoJsonRule[] rules) {
        return ruleSetCheck(rules, null);
    }

    public static class OSDescriptor {
        public String name;
        public String version;
        public String arch;

        public int getPrecedenceLevel() {
            int precedence = 0;
            if(name != null) precedence += 1;
            if(version != null) precedence += 2;
            if(arch != null) precedence += 3;
            return precedence;
        }

        private static boolean propertyMatches(String value, String expected) {
            if(value == null) return true;
            return value.equals(expected);
        }

        public boolean matches() {
            // TODO: version matching
            return propertyMatches(name, "linux") &&
                    propertyMatches(arch, Architecture.archAsString(Architecture.getDeviceArchitecture())) &&
                    version == null;
        }
    }
}
