package io.github.makamys.egds;

import static io.github.makamys.egds.HookConfig.log;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import java.util.stream.Collectors;

public class Util {
    
    public static Pattern makePattern(String word) {
        String patternStr = word.startsWith("~") ? word.substring(1) : word.replace(".", "\\.").replace("*", ".*");
        
        Pattern pattern = null;
        try {
            pattern = Pattern.compile(patternStr);
        } catch (PatternSyntaxException e) {
            log("Error parsing pattern '" + word + "'");
        }
        return pattern;
    }
    
    public static String toIndentedList(Collection<?> list) {
        return String.join("\n", list.stream().map(e -> "  " + e.toString()).collect(Collectors.toList()));
    }
    
    public static <T> Collection<T> subtract(Collection<T> a, Collection<T> b) {
        Set<T> diff = new HashSet<>(a);
        diff.removeAll(b);
        return diff;
    }
    
    public static <T> boolean intersects(Collection<T> a, Collection<T> b) {
        for(T as : a) {
            if(b.contains(as)) {
                return true;
            }
        }
        return false;
    }
    
}
