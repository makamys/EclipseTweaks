package makamys.fgech;

import java.lang.instrument.Instrumentation;

public class FGECH {

    public static void premain(String agentArgs, Instrumentation inst) {
        System.out.println("hello premain");
    }

}
