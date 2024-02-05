package io.github.makamys.egds.helpers;

import static io.github.makamys.egds.HookConfig.log;

import java.io.File;
import java.util.Arrays;
import java.util.List;

import org.eclipse.debug.core.ILaunchConfiguration;
import org.eclipse.jdt.launching.AbstractJavaLaunchConfigurationDelegate;
import org.eclipse.jdt.launching.JavaRuntime;

import io.github.makamys.egds.Config;
import io.github.makamys.egds.Util;

/** Store state used by hooks during the invocation of {@link AbstractJavaLaunchConfigurationDelegate#getClasspath(ILaunchConfiguration)}. */
public class ClasspathModificationHelper {
    public static boolean egdsEnabled;
    public static List<String> scopes;
    public static Config config;
    
    private static final List<String> DEFAULT_SCOPES = Arrays.asList("main");

    public static void init(ILaunchConfiguration configuration) {
        try {
            String args = configuration.getAttribute("org.eclipse.jdt.launching.VM_ARGUMENTS", "");
            log("Preparing to launch");
            log("VM args: " + args);
            egdsEnabled = !args.contains("-Degds.disable");
            for(String kv : args.split(" ")) {
                if(kv.startsWith("-Degds.scope=")) {
                    scopes = Arrays.asList(kv.split("=")[1].split(","));
                }
            }
            if(scopes == null) {
                scopes = Arrays.asList();
            }
            
            File gradleProjectDir = JavaRuntime.getJavaProject(configuration).getResource().getLocation().toFile();
            
            config = Config.load(gradleProjectDir);
            
            log("  Enabled: " + egdsEnabled);
            log("  Scopes: " + scopes);
            
            if(useScopes()) {
                log("Using extra classpath attributes");
            } else {
                log("Using blacklist");
            }
        } catch(Exception e) {
            egdsEnabled = false;
            log("Failed to capture launch configuration");
            log(e);
        }
    }
    
    public static void reset() {
        egdsEnabled = false;
        scopes = null;
        config = null;
    }
    
    public static boolean useScopes() {
        return config == null || config.dependencyBlacklist.isEmpty();
    }
    
    public static List<String> getScopeOrDefault() {
        return !scopes.isEmpty() ? scopes : DEFAULT_SCOPES;
    }

    public static boolean isGoodScope(List<String> scope) {
        return !Util.intersects(getScopeOrDefault(), scope);
    }
}
