/**
 * 
 */
package amod.sanfrancisco;

import java.io.File;
import java.net.MalformedURLException;

import amod.demo.ScenarioPreparer;

/**
 * @author asasidharan
 *
 */
public class SanfranciscoScenarioPrepare {

	public static void main(String[] args) throws MalformedURLException, Exception {
        File workingDirectory = new File("scenarios/SanFrancisco");
        ScenarioPreparer.run(workingDirectory);
    }
	
}
