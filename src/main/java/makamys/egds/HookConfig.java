package makamys.egds;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import org.eclipse.osgi.internal.hookregistry.ClassLoaderHook;
import org.eclipse.osgi.internal.hookregistry.HookConfigurator;
import org.eclipse.osgi.internal.hookregistry.HookRegistry;
import org.eclipse.osgi.internal.loader.classpath.ClasspathEntry;
import org.eclipse.osgi.internal.loader.classpath.ClasspathManager;
import org.eclipse.osgi.storage.bundlefile.BundleEntry;
import org.gradle.tooling.GradleConnector;
import org.gradle.tooling.ProjectConnection;
import org.gradle.tooling.model.idea.IdeaDependency;
import org.gradle.tooling.model.idea.IdeaModule;
import org.gradle.tooling.model.idea.IdeaProject;
import org.gradle.tooling.model.idea.IdeaSingleEntryLibraryDependency;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;

public class HookConfig implements HookConfigurator {
    
    private static final boolean ENABLE_LOG = Boolean.parseBoolean(System.getProperty("egds.enableLog", "false"));
    
    @Override
    public void addHooks(HookRegistry hookRegistry) {
        log("addHooks");
        hookRegistry.addClassLoaderHook(new MyClassLoaderHook());
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
                        MethodInsnNode old = null;
                        for(int i = 0; i < m.instructions.size(); i++) {
                            AbstractInsnNode ins = m.instructions.get(i);
                            if(ins instanceof MethodInsnNode) {
                                MethodInsnNode mi = (MethodInsnNode)ins;
                                
                                if(mi.getOpcode() == Opcodes.INVOKEVIRTUAL && mi.owner.equals("org/eclipse/jdt/launching/VMRunnerConfiguration") && mi.name.equals("getClassPath") && mi.desc.equals("()[Ljava/lang/String;")) {
                                    old = mi;
                                    break;
                                }
                            }
                        }
                        
                        if(old != null) {
                            m.instructions.insertBefore(old, new VarInsnNode(Opcodes.ALOAD, 0)); // this
                            m.instructions.insertBefore(old, new MethodInsnNode(Opcodes.INVOKESTATIC, "makamys/egds/HookConfig", "redirectGetClasspath", "(Ljava/lang/Object;Ljava/lang/Object;)[Ljava/lang/String;"));
                            m.instructions.remove(old);
                        }
                    }
                }
                
                ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES);
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
    
    public static String[] redirectGetClasspath(Object conf, Object standardVMDebugger) {
        String[] cp = new String[] {};
        try {
            cp = (String[])conf.getClass().getMethod("getClassPath").invoke(conf);
        } catch (Exception e) {
            log("Failed to call getClassPath: " + e.getMessage());
        }
        
        String[] vmArgs = null;
        try {
            vmArgs = (String[])conf.getClass().getMethod("getVMArguments").invoke(conf);
        } catch (Exception e) {
            log("Failed to call getVMArguments: " + e.getMessage());
        }
        
        if(vmArgs != null && Arrays.stream(vmArgs).anyMatch(s -> s.equals("egds.enable"))) {
            try {
                List<String> providedDeps = getProvidedDeps(cp, conf, standardVMDebugger).stream().map(p -> toCanonicalPath(p)).collect(Collectors.toList());
                String[] goodCP = Arrays.stream(cp).filter(p -> !providedDeps.contains(toCanonicalPath(p))).toArray(String[]::new);
                log("Original CP: " + Arrays.toString(cp));
                log("Modified CP: " + Arrays.toString(goodCP));
                cp = goodCP;
            } catch(Exception e) {
                log("Failed to modify class path: " + e.getMessage());
            }
        }
        
        return cp;
    }
    
    private static String toCanonicalPath(String path) {
        try {
            return new File(path).getCanonicalPath();
        } catch (IOException e) {
            log("Failed to canonicalize " + path + ": " + e.getMessage());
        }
        return "";
    }
    
    private static List<String> getProvidedDeps(String[] cp, Object conf, Object standardVMDebugger) {
        File javaHome = null;
        try {
            Method mConstructProgramString = standardVMDebugger.getClass().getSuperclass().getDeclaredMethod("constructProgramString", conf.getClass());
            mConstructProgramString.setAccessible(true);
            String javaPath = (String)mConstructProgramString.invoke(standardVMDebugger, conf);
            javaHome = new File(javaPath).getParentFile().getParentFile();
        } catch (Exception e) {
            log("Failed to call constructProgramString: " + e.getMessage());
            return new ArrayList<>();
        }
        
        File gradleProjectDir = guessGradleProjectDir(conf, cp);
        
        List<String> files = new ArrayList<>();
        
        if(gradleProjectDir != null) {    
            ProjectConnection connection = GradleConnector.newConnector().forProjectDirectory(gradleProjectDir).connect();
            IdeaProject be = connection.model(IdeaProject.class).setJavaHome(javaHome).get();
            for(IdeaModule module : be.getModules()) {
                for(IdeaDependency dep : module.getDependencies()) {
                    if(dep instanceof IdeaSingleEntryLibraryDependency) {
                        IdeaSingleEntryLibraryDependency ldep = (IdeaSingleEntryLibraryDependency)dep;
                        if(ldep.getScope().getScope().toLowerCase().equals("provided")) {
                            files.add(ldep.getFile().getPath());
                        }
                    }
                }
            }
        } else {
            log("Failed to guess Gradle project dir. cp: " + Arrays.toString(cp));
        }
        
        log("Provided dependencies: " + files);
        
        return files;
    }
    
    private static File guessGradleProjectDir(Object conf, String[] cp) {
        for(String string : cp) {
            File f = new File(string);
            while(f != null && !(f.isDirectory() && (f.getName().equals("bin") || f.getName().equals("build")))) {
                f = f.getParentFile();
            }
            if(f != null) {
                return f.getParentFile();
            }
        }
        return null;
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
        
        try {
            FileWriter out = new FileWriter(new File(System.getProperty("java.io.tmpdir"), "hook-config-test.log"), true);
            out.write(msg + "\n");
            out.flush();
        } catch (IOException e) {
            
        }
    }    

}
