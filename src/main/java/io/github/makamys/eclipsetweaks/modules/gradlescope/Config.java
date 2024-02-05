package io.github.makamys.eclipsetweaks.modules.gradlescope;

import static io.github.makamys.eclipsetweaks.EclipseTweaks.log;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import io.github.makamys.eclipsetweaks.Util;

public class Config {
    
    public List<Pattern> dependencyBlacklist;

    private Config(Properties props) {
        dependencyBlacklist = Arrays.stream(props.getProperty("gradleScope.dependencyBlacklist", "").split(","))
                .map(Util::makePattern)
                .collect(Collectors.toList());
    }
    
    public static Config load(File dir) {
        File configFile = new File(dir, "eclipseTweaks.properties");
        File oldConfigFile = new File(dir, "egds.properties");
        if(oldConfigFile.exists()) {
            log("Found old config file, you will need to migrate it manually: " + oldConfigFile);
        }
        if(configFile.exists()) {
            Properties props = new Properties();
            try(InputStream is = new FileInputStream(configFile)) {
                props.load(is);
                
                return new Config(props);
            } catch(IOException e) {
                
            }
        }
        return null;
    }

    public boolean isDependencyBlacklisted(String dep) {
        String name = new File(dep).getName();
        return dependencyBlacklist.stream().anyMatch(p -> p.matcher(name).matches());
    }
    
}
