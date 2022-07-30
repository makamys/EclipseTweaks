package makamys.egds;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;

import org.eclipse.osgi.internal.hookregistry.ClassLoaderHook;
import org.eclipse.osgi.internal.hookregistry.HookConfigurator;
import org.eclipse.osgi.internal.hookregistry.HookRegistry;
import org.eclipse.osgi.internal.loader.classpath.ClasspathEntry;
import org.eclipse.osgi.internal.loader.classpath.ClasspathManager;
import org.eclipse.osgi.storage.bundlefile.BundleEntry;

public class HookConfig implements HookConfigurator {

    private static Writer out;
    
    @Override
    public void addHooks(HookRegistry hookRegistry) {
        try {
            out = new FileWriter(new File(System.getProperty("java.io.tmpdir"), "hook-config-test.log"));
        } catch (IOException e) {
            e.printStackTrace();
        }
        
        log("addHooks");
        hookRegistry.addClassLoaderHook(new MyClassLoaderHook());
    }
    
    public static void log(String msg) {
        if(out != null) {
            try {
                out.write(msg + "\n");
                out.flush();
            } catch (IOException e) {
                
            }
        }
    }
    
    public static class MyClassLoaderHook extends ClassLoaderHook {
        @Override
        public byte[] processClass(String name, byte[] classbytes, ClasspathEntry classpathEntry, BundleEntry entry,
                ClasspathManager manager) {
            return super.processClass(name, classbytes, classpathEntry, entry, manager);
        }
    }

}
