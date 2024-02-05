package io.github.makamys.egds.hooks;

import static io.github.makamys.egds.HookConfig.log;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import io.github.makamys.egds.IClassTransformer;

public class SourceLookupFacilityTransformer implements IClassTransformer {
    private final int expected;
    
    public SourceLookupFacilityTransformer(int expected) {
        this.expected = expected;
    }
    
    /**
     * <pre>
     * In all methods:
     * -            this.schedule(100L);
     * +            this.schedule(Hooks.modifyDelay(100L));
     * </pre> 
     */
    public boolean transform(ClassNode classNode) {
        int found = 0;
        for(MethodNode m : classNode.methods) {
            String className = classNode.name;
            String methodName = m.name;
            String methodDesc = m.desc;
                
            MethodInsnNode old = null;
            for(int i = 0; i < m.instructions.size(); i++) {
                AbstractInsnNode ins = m.instructions.get(i);
                if(ins instanceof MethodInsnNode) {
                    MethodInsnNode mi = (MethodInsnNode)ins;
                    if(mi.getOpcode() == Opcodes.INVOKEVIRTUAL && mi.owner.startsWith("org/eclipse/debug/internal/ui/sourcelookup/SourceLookupFacility$Source") && mi.name.equals("schedule") && mi.desc.equals("(J)V")) {
                        old = mi;
                        break;
                    }
                }
            }
            
            if(old != null) {
                log("Patching schedule call in " + className + "#" + methodName + methodDesc);
                
                InsnList insns = new InsnList();
                // current stack: [J]
                insns.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "io/github/makamys/egds/hooks/SourceLookupFacilityTransformer$Hooks", "modifyDelay", "(J)J"));
                m.instructions.insertBefore(old, insns);
                found++;
            }
        }
        if(found != expected) {
            log("Expected " + expected + " injections, got " + found);
            return false;
        } else {
            return true;
        }
    }
    
    public static class Hooks {
        private static boolean printed = false;
        
        public static long modifyDelay(long delay) {
            if(delay != 0 && !printed) {
                log("Changing source job delay from " + delay + " to " + 0);
                printed = true;
            }
            return 0L;
        }
    }
    
}
