package io.github.makamys.eclipsetweaks.modules.gradlescope.asm;

import static io.github.makamys.eclipsetweaks.EclipseTweaks.log;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

import org.eclipse.debug.core.ILaunchConfiguration;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;

import io.github.makamys.eclipsetweaks.IClassTransformer;
import io.github.makamys.eclipsetweaks.modules.gradlescope.ClasspathModificationHelper;

public class LaunchConfigurationScopeTransformer implements IClassTransformer {
    /**
     * <pre>
     * public static LaunchConfigurationScope from(ILaunchConfiguration configuration) {
     * ...
     * -           Optional<Set<String>> scope = ClasspathUtils.scopesFor(sourceFolder.getRawClasspathEntry());
     * +           scope = Hooks.modifyScope(scope);
     * </pre> 
     */
    public boolean transform(ClassNode classNode) {
        for(MethodNode m : classNode.methods) {
            String className = classNode.name;
            String methodName = m.name;
            String methodDesc = m.desc;
            if(methodName.equals("from")) {
                log("Patching " + className + "#" + methodName + methodDesc);
                
                MethodInsnNode old = null;
                for(int i = 0; i < m.instructions.size(); i++) {
                    AbstractInsnNode ins = m.instructions.get(i);
                    if(ins instanceof MethodInsnNode) {
                        MethodInsnNode mi = (MethodInsnNode)ins;
                        if(mi.getOpcode() == Opcodes.INVOKESTATIC && mi.owner.equals("org/eclipse/buildship/core/internal/util/classpath/ClasspathUtils") && mi.name.equals("scopesFor") && mi.desc.equals("(Lorg/eclipse/jdt/core/IClasspathEntry;)Ljava/util/Optional;")) {
                            old = mi;
                            break;
                        }
                    }
                }
                
                if(old != null) {
                    InsnList insns = new InsnList();
                    // current stack: [Ljava/util/Optional;]
                    insns.add(new VarInsnNode(Opcodes.ALOAD, 0)); // configuration
                    insns.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "io/github/makamys/eclipsetweaks/modules/gradlescope/asm/LaunchConfigurationScopeTransformer$Hooks", "modifyScope", "(Ljava/util/Optional;Lorg/eclipse/debug/core/ILaunchConfiguration;)Ljava/util/Optional;"));
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
        public static Optional<Set<String>> modifyScope(Optional<Set<String>> original, ILaunchConfiguration config) {
            if(ClasspathModificationHelper.enabled && !ClasspathModificationHelper.scopes.isEmpty()) {
                log("Forcing scope " + ClasspathModificationHelper.scopes);
                return Optional.of(new HashSet<>(ClasspathModificationHelper.scopes));
            }
            return original;
        }
    }
    
}
