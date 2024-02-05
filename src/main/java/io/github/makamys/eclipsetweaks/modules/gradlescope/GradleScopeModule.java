package io.github.makamys.eclipsetweaks.modules.gradlescope;

import java.util.Map;

import io.github.makamys.eclipsetweaks.IClassTransformer;
import io.github.makamys.eclipsetweaks.IModule;
import io.github.makamys.eclipsetweaks.modules.gradlescope.asm.AbstractJavaLaunchConfigurationDelegateTransformer;
import io.github.makamys.eclipsetweaks.modules.gradlescope.asm.GradleClasspathContainerRuntimeClasspathEntryResolverTransformer;
import io.github.makamys.eclipsetweaks.modules.gradlescope.asm.JavaRuntimeTransformer;
import io.github.makamys.eclipsetweaks.modules.gradlescope.asm.LaunchConfigurationScopeTransformer;

public enum GradleScopeModule implements IModule {
    INSTANCE;

    public boolean initModule(Map<String, IClassTransformer> transformers) {
        if(!Boolean.parseBoolean(System.getProperty("eclipseTweaks.gradleScope.enabled", "true"))) return false;
        
        transformers.put("org.eclipse.jdt.launching.AbstractJavaLaunchConfigurationDelegate", new AbstractJavaLaunchConfigurationDelegateTransformer());
        transformers.put("org.eclipse.jdt.launching.JavaRuntime", new JavaRuntimeTransformer());
        transformers.put("org.eclipse.buildship.core.internal.workspace.GradleClasspathContainerRuntimeClasspathEntryResolver", new GradleClasspathContainerRuntimeClasspathEntryResolverTransformer());
        transformers.put("org.eclipse.buildship.core.internal.launch.LaunchConfigurationScope", new LaunchConfigurationScopeTransformer());
        
        return true;
    }
}
