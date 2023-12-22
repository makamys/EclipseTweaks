package io.github.makamys.egds;

import org.objectweb.asm.tree.ClassNode;

public interface IClassTransformer {
    boolean transform(ClassNode classNode);
}
