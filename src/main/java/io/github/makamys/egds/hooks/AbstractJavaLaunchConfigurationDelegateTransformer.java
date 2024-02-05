package io.github.makamys.egds.hooks;

import static io.github.makamys.egds.HookConfig.log;

import java.util.Arrays;
import org.eclipse.debug.core.ILaunchConfiguration;
import org.eclipse.jdt.core.IClasspathAttribute;
import org.eclipse.jdt.core.IClasspathEntry;
import org.eclipse.jdt.internal.launching.RuntimeClasspathEntry;
import org.eclipse.jdt.launching.AbstractJavaLaunchConfigurationDelegate;
import org.eclipse.jdt.launching.IRuntimeClasspathEntry;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;

import io.github.makamys.egds.IClassTransformer;
import io.github.makamys.egds.Util;
import io.github.makamys.egds.helpers.ClasspathModificationHelper;

public class AbstractJavaLaunchConfigurationDelegateTransformer implements IClassTransformer {
    
    /**
     * <pre> 
     * public String[] getClasspath(ILaunchConfiguration configuration) throws CoreException {
     * +   Hooks.captureConfiguration(configuration);
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
                
                InsnList preInsns = new InsnList();
                preInsns.add(new VarInsnNode(Opcodes.ALOAD, 1)); // ILaunchConfiguration
                preInsns.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "io/github/makamys/egds/hooks/AbstractJavaLaunchConfigurationDelegateTransformer$Hooks", "captureLaunchConfiguration", "(Lorg/eclipse/debug/core/ILaunchConfiguration;)V"));
                m.instructions.insert(preInsns);
                
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
        public static void captureLaunchConfiguration(ILaunchConfiguration configuration) {
            ClasspathModificationHelper.init(configuration);
        }
        
        public static IRuntimeClasspathEntry[] modifyResolveRuntimeClasspath(IRuntimeClasspathEntry[] entries, AbstractJavaLaunchConfigurationDelegate launchDelegate, ILaunchConfiguration configuration) {
            try {
                if(ClasspathModificationHelper.egdsEnabled) {
                    IRuntimeClasspathEntry[] goodCP = modifyClasspath(entries, launchDelegate, configuration);
                    
                    log("\nOriginal final CP:\n" + Util.toIndentedList(Arrays.asList(entries)) + "\n");
                    log("Removed entries:\n" + Util.toIndentedList(Util.subtract(Arrays.asList(entries), Arrays.asList(goodCP))) + "\n");
                    
                    entries = goodCP;
                }
            } catch(Exception e) {
                log("Failed to modify final classpath");
                log(e);
            } finally {
                ClasspathModificationHelper.reset();
            }
            
            return entries;
        }
        
        private static IRuntimeClasspathEntry[] modifyClasspath(IRuntimeClasspathEntry[] cp, AbstractJavaLaunchConfigurationDelegate launchDelegate, ILaunchConfiguration configuration) throws Exception {
            if(!ClasspathModificationHelper.useScopes()) {
                return Arrays.stream(cp).filter(p -> !ClasspathModificationHelper.config.isDependencyBlacklisted(p.getLocation())).toArray(IRuntimeClasspathEntry[]::new);
            } else {
                return Arrays.stream(cp).filter(p -> !isMissingScope(p)).toArray(IRuntimeClasspathEntry[]::new);
            }
        }
        
        public static boolean isMissingScope(IRuntimeClasspathEntry p) {
            if(p instanceof RuntimeClasspathEntry) {
                return isMissingScope(p.getClasspathEntry());
            }
            return false;
        }
        
        public static boolean isMissingScope(IClasspathEntry p) {
            for(IClasspathAttribute att : p.getExtraAttributes()) {
                if(att.getName().equals("gradle_used_by_scope") && ClasspathModificationHelper.isGoodScope(Arrays.asList(att.getValue().split(",")))) {
                    return true;
                }
            }
            return false;
        }
    }
    
}
