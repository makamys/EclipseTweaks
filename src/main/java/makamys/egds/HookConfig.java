package makamys.egds;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

import org.eclipse.debug.core.ILaunchConfiguration;
import org.eclipse.jdt.core.IClasspathAttribute;
import org.eclipse.jdt.internal.launching.RuntimeClasspathEntry;
import org.eclipse.jdt.launching.AbstractJavaLaunchConfigurationDelegate;
import org.eclipse.jdt.launching.IRuntimeClasspathEntry;
import org.eclipse.jdt.launching.JavaRuntime;
import org.eclipse.osgi.internal.hookregistry.ClassLoaderHook;
import org.eclipse.osgi.internal.hookregistry.HookConfigurator;
import org.eclipse.osgi.internal.hookregistry.HookRegistry;
import org.eclipse.osgi.internal.loader.classpath.ClasspathEntry;
import org.eclipse.osgi.internal.loader.classpath.ClasspathManager;
import org.eclipse.osgi.storage.bundlefile.BundleEntry;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnList;
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
            if(name.equals("org.eclipse.jdt.launching.AbstractJavaLaunchConfigurationDelegate")) {
                return transform(name, classbytes);
            }
            return null;
        }

        private byte[] transform(String name, byte[] bytes) {
            boolean ok = false;
            try {
                ClassNode classNode = new ClassNode();
                ClassReader classReader = new ClassReader(bytes);
                classReader.accept(classNode, 0);
                for(MethodNode m : classNode.methods) {
                    String className = classNode.name;
                    String methodName = m.name;
                    String methodDesc = m.desc;
                    if(methodName.equals("getClasspath")) {
                        log("Patching " + className + "#" + methodName + methodDesc);
                        MethodInsnNode old = null;
                        for(int i = 0; i < m.instructions.size(); i++) {
                            AbstractInsnNode ins = m.instructions.get(i);
                            if(ins instanceof MethodInsnNode) {
                                MethodInsnNode mi = (MethodInsnNode)ins;
                                if(mi.getOpcode() == Opcodes.INVOKESTATIC && mi.owner.equals("org/eclipse/jdt/launching/JavaRuntime") && mi.name.equals("resolveRuntimeClasspath") && mi.desc.equals("([Lorg/eclipse/jdt/launching/IRuntimeClasspathEntry;Lorg/eclipse/debug/core/ILaunchConfiguration;)[Lorg/eclipse/jdt/launching/IRuntimeClasspathEntry;")) {
                                    old = mi;
                                    break;
                                }
                            }
                        }
                        
                        if(old != null) {
                            InsnList insns = new InsnList();
                            // current stack: [entries]
                            insns.add(new VarInsnNode(Opcodes.ALOAD, 0)); // this
                            insns.add(new VarInsnNode(Opcodes.ALOAD, 1)); // configuration
                            insns.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "makamys/egds/HookConfig", "modifyResolveRuntimeClasspath", "([Lorg/eclipse/jdt/launching/IRuntimeClasspathEntry;Lorg/eclipse/jdt/launching/AbstractJavaLaunchConfigurationDelegate;Lorg/eclipse/debug/core/ILaunchConfiguration;)[Lorg/eclipse/jdt/launching/IRuntimeClasspathEntry;"));
                            m.instructions.insert(old, insns);
                            ok = true;
                        } else {
                            log("Failed to find hook point!");
                        }
                    }
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
    
    public static IRuntimeClasspathEntry[] modifyResolveRuntimeClasspath(IRuntimeClasspathEntry[] entries, AbstractJavaLaunchConfigurationDelegate launchDelegate, ILaunchConfiguration configuration) {
        try {
            if(!configuration.getAttribute("org.eclipse.jdt.launching.VM_ARGUMENTS", "").contains("-Degds.disable")) {
                IRuntimeClasspathEntry[] goodCP = modifyClasspath(entries, launchDelegate, configuration);
                
                log("\nOriginal CP:\n" + toIndentedList(Arrays.asList(entries)) + "\n");
                log("Modified CP:\n" + toIndentedList(Arrays.asList(goodCP)) + "\n");
                log("Removed entries:\n" + toIndentedList(subtract(Arrays.asList(entries), Arrays.asList(goodCP))) + "\n");
                
                entries = goodCP;
            }
        } catch(Exception e) {
            log("Failed to modify classpath");
            log(e);
        }
        
        return entries;
    }
    
    private static String toIndentedList(Collection<?> list) {
        return String.join("\n", list.stream().map(e -> "  " + e.toString()).collect(Collectors.toList()));
    }
    
    private static <T> Collection<T> subtract(Collection<T> a, Collection<T> b) {
        Set<T> diff = new HashSet<>(a);
        diff.removeAll(b);
        return diff;
    }
    
    private static IRuntimeClasspathEntry[] modifyClasspath(IRuntimeClasspathEntry[] cp, AbstractJavaLaunchConfigurationDelegate launchDelegate, ILaunchConfiguration configuration) throws Exception {
        File gradleProjectDir = JavaRuntime.getJavaProject(configuration).getPath().toFile();
        
        Config config = Config.load(gradleProjectDir);
        
        if(config != null && !config.dependencyBlacklist.isEmpty()) {
            log("Using blacklist");
            return Arrays.stream(cp).filter(p -> !config.isDependencyBlacklisted(p.getLocation())).toArray(IRuntimeClasspathEntry[]::new);
        } else {
            log("Using extra classpath attributes");
            return Arrays.stream(cp).filter(p -> !isMissingScope(p)).toArray(IRuntimeClasspathEntry[]::new);
        }
    }
    
    private static boolean isMissingScope(IRuntimeClasspathEntry p) {
        if(p instanceof RuntimeClasspathEntry) {
            for(IClasspathAttribute att : p.getClasspathEntry().getExtraAttributes()) {
                if(att.getName().equals("gradle_used_by_scope") && att.getValue().isEmpty()) {
                    return true;
                }
            }
        }
        return false;
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
            FileWriter out = new FileWriter(new File(System.getProperty("java.io.tmpdir"), "EclipseGradleDependencyScope.log"), true);
            out.write(msg + "\n");
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
