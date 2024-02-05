package io.github.makamys.eclipsetweaks;

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

import io.github.makamys.eclipsetweaks.modules.faststep.FastStepModule;
import io.github.makamys.eclipsetweaks.modules.gradlescope.GradleScopeModule;

public class EclipseTweaks implements HookConfigurator {
    
    private static final Map<String, IClassTransformer> TRANSFORMERS = new HashMap<>();
    
    private static final boolean ENABLE_LOG = Boolean.parseBoolean(System.getProperty("eclipseTweaks.log", "false"));
    private static final File LOG_PATH = new File(System.getProperty("java.io.tmpdir"), "EclipseTweaks.log");
    
    public EclipseTweaks() {
        if(ENABLE_LOG) {
            LOG_PATH.delete();
        }
    }
    
    @Override
    public void addHooks(HookRegistry hookRegistry) {
        log("Initializing modules");
        initializeModules();
        log("Adding class loader hook");
        hookRegistry.addClassLoaderHook(new MyClassLoaderHook());
        log("Initialization complete");
    }
    
    private void initializeModules() {
        initModule(GradleScopeModule.INSTANCE);
        initModule(FastStepModule.INSTANCE);
    }
    
    private static void initModule(IModule module) {
        if(module.initModule(TRANSFORMERS)) {
            log("Initialized module " + module.getClass().getSimpleName());
        } else {
            log("Skipped module " + module.getClass().getSimpleName());
        }
    }

    public static class MyClassLoaderHook extends ClassLoaderHook {
        
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
            if(ext.contains("EclipseTweaks") && ext.startsWith("reference:file:")) {
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
