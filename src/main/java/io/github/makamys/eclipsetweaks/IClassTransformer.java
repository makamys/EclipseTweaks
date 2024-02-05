package io.github.makamys.eclipsetweaks;

import org.objectweb.asm.tree.ClassNode;

public interface IClassTransformer {
    boolean transform(ClassNode classNode);
}
