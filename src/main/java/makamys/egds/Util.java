package makamys.egds;

import static makamys.egds.HookConfig.log;

import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

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
    
}
