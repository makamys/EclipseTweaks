package io.github.makamys.eclipsetweaks.modules.faststep;

import java.util.Map;

import io.github.makamys.eclipsetweaks.IClassTransformer;
import io.github.makamys.eclipsetweaks.IModule;
import io.github.makamys.eclipsetweaks.modules.faststep.asm.SourceLookupFacilityTransformer;

public enum FastStepModule implements IModule {
    INSTANCE;

    public boolean initModule(Map<String, IClassTransformer> transformers) {
        if(!Boolean.parseBoolean(System.getProperty("eclipseTweaks.fastStep.enabled", "true"))) return false;
        
        transformers.put("org.eclipse.debug.internal.ui.sourcelookup.SourceLookupFacility$SourceDisplayJob", new SourceLookupFacilityTransformer(1));
        transformers.put("org.eclipse.debug.internal.ui.sourcelookup.SourceLookupFacility$SourceLookupJob", new SourceLookupFacilityTransformer(2));
        
        return true;
    }
}
