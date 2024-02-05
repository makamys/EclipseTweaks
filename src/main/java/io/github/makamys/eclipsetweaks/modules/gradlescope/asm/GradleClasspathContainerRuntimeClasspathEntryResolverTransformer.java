package io.github.makamys.eclipsetweaks.modules.gradlescope.asm;

import static io.github.makamys.eclipsetweaks.EclipseTweaks.log;

import org.eclipse.debug.core.ILaunchConfiguration;
import org.eclipse.jdt.core.IJavaProject;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;

import io.github.makamys.eclipsetweaks.IClassTransformer;
import io.github.makamys.eclipsetweaks.modules.gradlescope.ClasspathModificationHelper;

public class GradleClasspathContainerRuntimeClasspathEntryResolverTransformer implements IClassTransformer {
    
    /**
     * <pre>
     * public IRuntimeClasspathEntry[] resolveRuntimeClasspathEntry(IRuntimeClasspathEntry entry, ILaunchConfiguration configuration) throws CoreException {
     * +    Hooks.captureConfiguration(configuration);
     * ...
     * }
     * 
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
            if(methodName.equals("resolveRuntimeClasspathEntry") && methodDesc.equals("(Lorg/eclipse/jdt/launching/IRuntimeClasspathEntry;Lorg/eclipse/debug/core/ILaunchConfiguration;)[Lorg/eclipse/jdt/launching/IRuntimeClasspathEntry;")) {
                log("Patching " + className + "#" + methodName + methodDesc);
                
                InsnList preInsns = new InsnList();
                preInsns.add(new VarInsnNode(Opcodes.ALOAD, 2)); // ILaunchConfiguration
                preInsns.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "io/github/makamys/eclipsetweaks/modules/gradlescope/asm/GradleClasspathContainerRuntimeClasspathEntryResolverTransformer$Hooks", "captureLaunchConfiguration", "(Lorg/eclipse/debug/core/ILaunchConfiguration;)V"));
                m.instructions.insert(preInsns);
                found++;
            } else if(methodName.equals("resolveRuntimeClasspathEntry") && methodDesc.equals("(Lorg/eclipse/jdt/launching/IRuntimeClasspathEntry;Lorg/eclipse/jdt/core/IJavaProject;Lorg/eclipse/buildship/core/internal/launch/LaunchConfigurationScope;ZZ)[Lorg/eclipse/jdt/launching/IRuntimeClasspathEntry;")) {
                log("Patching " + className + "#" + methodName + methodDesc);
                
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
                    insns.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "io/github/makamys/eclipsetweaks/modules/gradlescope/asm/GradleClasspathContainerRuntimeClasspathEntryResolverTransformer$Hooks", "modifySupportsTestAttributes", "(ILorg/eclipse/jdt/core/IJavaProject;)I"));
                    m.instructions.insert(old, insns);
                    found++;
                } else {
                    log("Failed to find hook point!");
                }
            }
        }
        return found == 2;
    }
    
    public static class Hooks {
        // This is loaded by a separate classloader than AbstractJavaLaunchConfigurationDelegate so we have to load it again
        public static void captureLaunchConfiguration(ILaunchConfiguration configuration) {
            ClasspathModificationHelper.init(configuration);
        }
        
        public static int modifySupportsTestAttributes(int original, IJavaProject project) {
            if(ClasspathModificationHelper.enabled) {
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
