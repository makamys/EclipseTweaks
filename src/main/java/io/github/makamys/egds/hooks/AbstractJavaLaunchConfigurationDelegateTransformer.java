package io.github.makamys.egds.hooks;

import static io.github.makamys.egds.HookConfig.log;

import java.io.File;
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
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;

import io.github.makamys.egds.Config;
import io.github.makamys.egds.IClassTransformer;

public class AbstractJavaLaunchConfigurationDelegateTransformer implements IClassTransformer {
    
    /**
     * <pre> 
     * public String[] getClasspath(ILaunchConfiguration configuration) throws CoreException {
     *     IRuntimeClasspathEntry[] entries = JavaRuntime.computeUnresolvedRuntimeClasspath(configuration);
     *     entries = JavaRuntime.resolveRuntimeClasspath(entries, configuration);
     * +   entries = Hooks.modifyResolveRuntimeClasspath(entries);
     * </pre>
     */
    @Override
    public boolean transform(ClassNode classNode) {
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
                    insns.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "io/github/makamys/egds/hooks/AbstractJavaLaunchConfigurationDelegateTransformer$Hooks", "modifyResolveRuntimeClasspath", "([Lorg/eclipse/jdt/launching/IRuntimeClasspathEntry;Lorg/eclipse/jdt/launching/AbstractJavaLaunchConfigurationDelegate;Lorg/eclipse/debug/core/ILaunchConfiguration;)[Lorg/eclipse/jdt/launching/IRuntimeClasspathEntry;"));
                    m.instructions.insert(old, insns);
                    return true;
                } else {
                    log("Failed to find hook point!");
                }
            }
        }
        return false;
    }
    
    public static class Hooks {
        public static IRuntimeClasspathEntry[] modifyResolveRuntimeClasspath(IRuntimeClasspathEntry[] entries, AbstractJavaLaunchConfigurationDelegate launchDelegate, ILaunchConfiguration configuration) {
            try {
                if(!configuration.getAttribute("org.eclipse.jdt.launching.VM_ARGUMENTS", "").contains("-Degds.disable")) {
                    IRuntimeClasspathEntry[] goodCP = modifyClasspath(entries, launchDelegate, configuration);
                    
                    log("\nOriginal CP:\n" + toIndentedList(Arrays.asList(entries)) + "\n");
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
    }
    
}
