/* amod - Copyright (c) 2018, ETH Zurich, Institute for Dynamic Systems and Control */
package amod.demo;

import java.io.File;
import java.net.MalformedURLException;

/** Helper class to run a default preparer and server. */
public enum RunScenario {
    ;

    private final static String SAN_FRANCISCO = "scenarios/SanFrancisco";
    private final static String BERLIN = "scenarios/Berlin/fromAmoDeus";

    private static final boolean DO_PREPARE_SCENARIO = true;
    private static final boolean DO_RUN_SCENARIO = false;

    public static void main(String[] args) throws MalformedURLException, Exception {
        String[] parameters  = new String[]{SAN_FRANCISCO};
//        String[] parameters  = new String[]{BERLIN};

        if(DO_PREPARE_SCENARIO) ScenarioPreparer.main(parameters);
        if(DO_RUN_SCENARIO)  ScenarioServer.main(parameters);
    }

}