/**
 * 
 */
package amod.sanfrancisco;

import java.io.File;
import java.net.MalformedURLException;

import amod.demo.ScenarioServer;

/**
 * @author asasidharan
 *
 */
public class SanfranciscoScenarioServer {

	public static void main(String[] args) throws MalformedURLException, Exception {
        File workingDirectory = new File("scenarios/SanFrancisco");
        ScenarioServer.simulate(workingDirectory);
    }
	
}
