package io.github.makamys.egds.hooks;

import static io.github.makamys.egds.HookConfig.log;

import org.eclipse.jdt.core.IJavaProject;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;

import io.github.makamys.egds.IClassTransformer;
import io.github.makamys.egds.helpers.ClasspathModificationHelper;

public class GradleClasspathContainerRuntimeClasspathEntryResolverTransformer implements IClassTransformer {
    
    /**
     * <pre>
     * private IRuntimeClasspathEntry[] resolveRuntimeClasspathEntry(IRuntimeClasspathEntry entry, IJavaProject project, LaunchConfigurationScope configurationScopes, boolean excludeTestCode, boolean moduleSupport) throws CoreException {
     * ...
     * -    PlatformUtils.supportsTestAttributes()
     * +    Hooks.modifySupportsTestAttributes(PlatformUtils.supportsTestAttributes())
     * </pre> 
     */
    public boolean transform(ClassNode classNode) {
        int found = 0;
        for(MethodNode m : classNode.methods) {
            String className = classNode.name;
            String methodName = m.name;
            String methodDesc = m.desc;
            if(methodName.equals("resolveRuntimeClasspathEntry")) {
                MethodInsnNode old = null;
                for(int i = 0; i < m.instructions.size(); i++) {
                    AbstractInsnNode ins = m.instructions.get(i);
                    if(ins instanceof MethodInsnNode) {
                        MethodInsnNode mi = (MethodInsnNode)ins;
                        if(mi.getOpcode() == Opcodes.INVOKESTATIC && mi.owner.equals("org/eclipse/buildship/core/internal/util/eclipse/PlatformUtils") && mi.name.equals("supportsTestAttributes") && mi.desc.equals("()Z")) {
                            old = mi;
                            break;
                        }
                    }
                }
                
                if(old != null) {
                    InsnList insns = new InsnList();
                    // current stack: [I]
                    insns.add(new VarInsnNode(Opcodes.ALOAD, 1)); // IJavaProject
                    insns.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "io/github/makamys/egds/hooks/GradleClasspathContainerRuntimeClasspathEntryResolverTransformer$Hooks", "modifySupportsTestAttributes", "(ILorg/eclipse/jdt/core/IJavaProject;)I"));
                    m.instructions.insert(old, insns);
                    found += 1;
                    log("Patching PlatformUtils#supportsTestAttributes call in " + className + "#" + methodName + methodDesc);
                }
            }
        }
        return found > 0;
    }
    
    public static class Hooks {
        public static int modifySupportsTestAttributes(int original, IJavaProject project) {
            if(ClasspathModificationHelper.egdsEnabled) {
                if(original != 0) {
                    log("Forcing Buildship classpath resolver to use dependency scope");
                }
                return 0;
            } else {
                return original;
            }
        }
    }
    
}
