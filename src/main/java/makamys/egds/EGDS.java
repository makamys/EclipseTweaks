package makamys.egds;

import java.lang.instrument.Instrumentation;

public class EGDS {

    public static void premain(String agentArgs, Instrumentation inst) {
        System.out.println("hello premain");
    }

}
