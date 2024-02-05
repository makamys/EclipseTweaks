package io.github.makamys.eclipsetweaks;

import java.util.Map;

public interface IModule {
    public boolean initModule(Map<String, IClassTransformer> transformers);
}
