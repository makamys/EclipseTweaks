package makamys.egds;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;

import org.eclipse.osgi.internal.hookregistry.ClassLoaderHook;
import org.eclipse.osgi.internal.hookregistry.HookConfigurator;
import org.eclipse.osgi.internal.hookregistry.HookRegistry;
import org.eclipse.osgi.internal.loader.classpath.ClasspathEntry;
import org.eclipse.osgi.internal.loader.classpath.ClasspathManager;
import org.eclipse.osgi.storage.bundlefile.BundleEntry;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

public class HookConfig implements HookConfigurator {
    
    @Override
    public void addHooks(HookRegistry hookRegistry) {
        log("addHooks");
        hookRegistry.addClassLoaderHook(new MyClassLoaderHook());
    }
    
    public static void log(String msg) {
        try {
            FileWriter out = new FileWriter(new File(System.getProperty("java.io.tmpdir"), "hook-config-test.log"), true);
            out.write(msg + "\n");
            out.flush();
        } catch (IOException e) {
            
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
    
    public static class MyClassLoaderHook extends ClassLoaderHook {
        @Override
        public byte[] processClass(String name, byte[] classbytes, ClasspathEntry classpathEntry, BundleEntry entry,
                ClasspathManager manager) {
            if(name.equals("org.eclipse.jdt.internal.launching.StandardVMDebugger")) {
                return transform(name, classbytes);
            }
            return null;
        }

        private byte[] transform(String name, byte[] bytes) {
            try {
                ClassNode classNode = new ClassNode();
                ClassReader classReader = new ClassReader(bytes);
                classReader.accept(classNode, 0);
                for(MethodNode m : classNode.methods) {
                    String className = classNode.name;
                    String methodName = m.name;
                    String methodDesc = m.desc;
                    if(methodName.equals("getCommandLine")) {
                        log("Patching " + className + "#" + methodName + methodDesc);
                        
                        m.instructions.insert(new MethodInsnNode(Opcodes.INVOKESTATIC, "makamys/egds/HookConfig", "hook", "()V"));
                    }
                }
                
                ClassWriter writer = new ClassWriter(0);
                classNode.accept(writer);
                return writer.toByteArray();
            } catch(Exception e) {
                e.printStackTrace();
            }
            return bytes;
        }
        
        public boolean addClassPathEntry(ArrayList<ClasspathEntry> cpEntries, String cp, ClasspathManager hostmanager, org.eclipse.osgi.storage.BundleInfo.Generation sourceGeneration) {
            hostmanager.addClassPathEntry(cpEntries, "external:" + getJarPath(), hostmanager, sourceGeneration);
            return true;
        }
    }
    
    
    public static void hook() {
        log("Hook called");
    }
    
    public static void callHook() {
        hook();
    }

}
