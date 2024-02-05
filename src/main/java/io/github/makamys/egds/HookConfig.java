package io.github.makamys.egds;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import org.eclipse.osgi.internal.hookregistry.ClassLoaderHook;
import org.eclipse.osgi.internal.hookregistry.HookConfigurator;
import org.eclipse.osgi.internal.hookregistry.HookRegistry;
import org.eclipse.osgi.internal.loader.classpath.ClasspathEntry;
import org.eclipse.osgi.internal.loader.classpath.ClasspathManager;
import org.eclipse.osgi.storage.bundlefile.BundleEntry;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.tree.ClassNode;
import io.github.makamys.egds.hooks.AbstractJavaLaunchConfigurationDelegateTransformer;
import io.github.makamys.egds.hooks.GradleClasspathContainerRuntimeClasspathEntryResolverTransformer;
import io.github.makamys.egds.hooks.JavaRuntimeTransformer;

public class HookConfig implements HookConfigurator {
    
    private static final boolean ENABLE_LOG = Boolean.parseBoolean(System.getProperty("egds.enableLog", "false"));
    private static final File LOG_PATH = new File(System.getProperty("java.io.tmpdir"), "EclipseGradleDependencyScope.log");
    
    public HookConfig() {
        if(ENABLE_LOG) {
            LOG_PATH.delete();
        }
    }
    
    @Override
    public void addHooks(HookRegistry hookRegistry) {
        log("Adding class loader hook");
        hookRegistry.addClassLoaderHook(new MyClassLoaderHook());
        log("Initialization complete");
    }
    
    public static class MyClassLoaderHook extends ClassLoaderHook {
        private static final Map<String, IClassTransformer> TRANSFORMERS = new HashMap<>();
        
        static {
            TRANSFORMERS.put("org.eclipse.jdt.launching.AbstractJavaLaunchConfigurationDelegate", new AbstractJavaLaunchConfigurationDelegateTransformer());
            TRANSFORMERS.put("org.eclipse.jdt.launching.JavaRuntime", new JavaRuntimeTransformer());
            TRANSFORMERS.put("org.eclipse.buildship.core.internal.workspace.GradleClasspathContainerRuntimeClasspathEntryResolver", new GradleClasspathContainerRuntimeClasspathEntryResolverTransformer());
        }
        
        @Override
        public byte[] processClass(String name, byte[] bytes, ClasspathEntry classpathEntry, BundleEntry entry,
                ClasspathManager manager) {
            IClassTransformer transformer = TRANSFORMERS.get(name);
            if(transformer == null) return bytes;
            
            log("Beginning transformation of " + name);
            
            boolean ok = false;
            try {
                ClassNode classNode = new ClassNode();
                ClassReader classReader = new ClassReader(bytes);
                classReader.accept(classNode, 0);
                if(transformer.transform(classNode)) {
                    ok = true;
                } else {
                    log("Transformation failed");
                }
                
                ClassWriter writer = new ClassWriter(0);
                classNode.accept(writer);
                bytes = writer.toByteArray();
            } catch(Exception e) {
                log("Encountered exception");
                StringWriter sw = new StringWriter();
                PrintWriter pw = new PrintWriter(sw);
                e.printStackTrace(pw);
                log(sw.toString());
                ok = false;
            }
            if(ok) {
                log("Success!");
            }
            return bytes;
        }
        
        public boolean addClassPathEntry(ArrayList<ClasspathEntry> cpEntries, String cp, ClasspathManager hostmanager, org.eclipse.osgi.storage.BundleInfo.Generation sourceGeneration) {
            hostmanager.addClassPathEntry(cpEntries, "external:" + getJarPath(), hostmanager, sourceGeneration);
            return true;
        }
    }
    
    // XXX bad
    private static String getJarPath() {
        for(String ext : System.getProperty("osgi.framework.extensions").split(",")) {
            if(ext.contains("EclipseGradleDependencyScope") && ext.startsWith("reference:file:")) {
                return ext.substring("reference:file:".length());
            }
        }
        return null;
    }
    
    public static void log(String msg) {
        if(!ENABLE_LOG) return;
        
        String timestamp = new SimpleDateFormat("HH:mm:ss").format(new java.util.Date());
        
        try(FileWriter out = new FileWriter(LOG_PATH, true)) {
            out.write("[" + timestamp + "] " + msg + "\n");
            out.flush();
        } catch (IOException e) {
            
        }
    }
    
    public static void log(Exception e) {
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        e.printStackTrace(pw);
        log(sw.toString());
    }

}
