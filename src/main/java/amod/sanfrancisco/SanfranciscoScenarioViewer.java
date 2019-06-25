/**
 * 
 */
package amod.sanfrancisco;

import java.io.File;
import java.net.MalformedURLException;

import amod.demo.ScenarioViewer;

/**
 * @author asasidharan
 *
 */
public class SanfranciscoScenarioViewer {

	public static void main(String[] args) throws MalformedURLException, Exception {
        File workingDirectory = new File("scenarios/SanFrancisco");
        ScenarioViewer.run(workingDirectory);
    }
	
	
}
