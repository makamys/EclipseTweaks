package io.github.makamys.egds.hooks;

import static io.github.makamys.egds.HookConfig.log;

import java.util.Arrays;
import org.eclipse.jdt.core.IClasspathEntry;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

import io.github.makamys.egds.IClassTransformer;
import io.github.makamys.egds.Util;
import io.github.makamys.egds.helpers.ClasspathModificationHelper;

public class JavaRuntimeTransformer implements IClassTransformer {
    
    /**
     * <pre> 
     * private static IRuntimeClasspathEntry[] resolveOutputLocations(IJavaProject project, int classpathProperty, IClasspathAttribute[] attributes, boolean excludeTestCode) throws CoreException {
     * ...
     * -       IClasspathEntry[] entries = project.getRawClasspath();
     * +       entries = Hooks.modifyResolveRuntimeClasspath(entries);
     * ...
     * }
     * </pre>
     */
    @Override
    public boolean transform(ClassNode classNode) {
        for(MethodNode m : classNode.methods) {
            String className = classNode.name;
            String methodName = m.name;
            String methodDesc = m.desc;
            if(methodName.equals("resolveOutputLocations")) {
                log("Patching " + className + "#" + methodName + methodDesc);
                MethodInsnNode old = null;
                for(int i = 0; i < m.instructions.size(); i++) {
                    AbstractInsnNode ins = m.instructions.get(i);
                    if(ins instanceof MethodInsnNode) {
                        MethodInsnNode mi = (MethodInsnNode)ins;
                        if(mi.getOpcode() == Opcodes.INVOKEINTERFACE && mi.owner.equals("org/eclipse/jdt/core/IJavaProject") && mi.name.equals("getRawClasspath") && mi.desc.equals("()[Lorg/eclipse/jdt/core/IClasspathEntry;")) {
                            old = mi;
                            break;
                        }
                    }
                }
                
                if(old != null) {
                    InsnList insns = new InsnList();
                    insns.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "io/github/makamys/egds/hooks/JavaRuntimeTransformer$Hooks", "modifyGetRawClasspath", "([Lorg/eclipse/jdt/core/IClasspathEntry;)[Lorg/eclipse/jdt/core/IClasspathEntry;"));
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
        public static IClasspathEntry[] modifyGetRawClasspath(IClasspathEntry[] entries) {
            try {
                if(ClasspathModificationHelper.egdsEnabled) {
                    if(ClasspathModificationHelper.useScopes()) {
                        IClasspathEntry[] goodCP = Arrays.stream(entries).filter(p -> !AbstractJavaLaunchConfigurationDelegateTransformer.Hooks.isMissingScope(p)).toArray(IClasspathEntry[]::new);
                        
                        log("\nOriginal project CP:\n" + Util.toIndentedList(Arrays.asList(entries)) + "\n");
                        log("Removed entries:\n" + Util.toIndentedList(Util.subtract(Arrays.asList(entries), Arrays.asList(goodCP))) + "\n");
                        
                        entries = goodCP;
                    }
                }
            } catch(Exception e) {
                log("Failed to modify project classpath");
                log(e);
            }
            
            return entries;
        }
    }
    
}
