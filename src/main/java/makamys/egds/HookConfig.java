package makamys.egds;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

import org.eclipse.osgi.internal.hookregistry.HookConfigurator;
import org.eclipse.osgi.internal.hookregistry.HookRegistry;

public class HookConfig implements HookConfigurator {

    @Override
    public void addHooks(HookRegistry hookRegistry) {
        log("addHooks");
    }
    
    public static void log(String msg) {
        try(FileWriter fw = new FileWriter(new File(System.getProperty("java.io.tmpdir"), "hook-config-test.log"))){
            fw.write(msg + "\n");
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }

}
