/* amod - Copyright (c) 2018, ETH Zurich, Institute for Dynamic Systems and Control */
package amod.berlin;

import amod.demo.ScenarioViewer;
import amod.demo.analysis.CustomAnalysis;
import amod.demo.dispatcher.DemoDispatcher;
import amod.demo.ext.Static;
import amod.demo.generator.DemoGenerator;
import ch.ethz.idsc.amodeus.analysis.Analysis;
import ch.ethz.idsc.amodeus.data.LocationSpec;
import ch.ethz.idsc.amodeus.data.ReferenceFrame;
import ch.ethz.idsc.amodeus.linkspeed.LinkSpeedDataContainer;
import ch.ethz.idsc.amodeus.linkspeed.LinkSpeedUtils;
import ch.ethz.idsc.amodeus.linkspeed.TrafficDataModule;
import ch.ethz.idsc.amodeus.matsim.mod.*;
import ch.ethz.idsc.amodeus.net.DatabaseModule;
import ch.ethz.idsc.amodeus.net.MatsimAmodeusDatabase;
import ch.ethz.idsc.amodeus.net.SimulationServer;
import ch.ethz.idsc.amodeus.options.ScenarioOptions;
import ch.ethz.idsc.amodeus.options.ScenarioOptionsBase;
import ch.ethz.idsc.amodeus.routing.DefaultAStarLMRouter;
import ch.ethz.idsc.amodeus.util.io.MultiFileTools;
import ch.ethz.idsc.amodeus.util.math.GlobalAssert;
import ch.ethz.matsim.av.framework.AVConfigGroup;
import ch.ethz.matsim.av.framework.AVModule;
import ch.ethz.matsim.av.framework.AVUtils;
import com.google.inject.Key;
import com.google.inject.name.Names;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.network.Network;
import org.matsim.api.core.v01.population.Population;
import org.matsim.contrib.dvrp.run.DvrpConfigGroup;
import org.matsim.contrib.dvrp.trafficmonitoring.DvrpTravelTimeModule;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.config.groups.PlanCalcScoreConfigGroup.ActivityParams;
import org.matsim.core.controler.AbstractModule;
import org.matsim.core.controler.Controler;
import org.matsim.core.scenario.ScenarioUtils;

import java.io.File;
import java.net.MalformedURLException;
import java.util.Objects;

/** This class runs an AMoDeus simulation based on MATSim. The results can be viewed
 * if the {@link ScenarioViewer} is executed in the same working directory and the button "Connect"
 * is pressed. */
public enum BerlinScenarioServer {
    ;

    public static void main(String[] args) throws MalformedURLException, Exception {
        File workingDirectory = new File("scenarios/BerlinScenario/5.3");
//        simulate(MultiFileTools.getDefaultWorkingDirectory());
        simulate(workingDirectory);
    }

    /** runs a simulation run using input data from Amodeus.properties, av.xml and MATSim config.xml
     * 
     * @throws MalformedURLException
     * @throws Exception */
    public static void simulate(File workingDirectory) throws MalformedURLException, Exception {
        Static.setup();
        Static.checkGLPKLib();


        /** working directory and options */
        ScenarioOptions scenarioOptions = new ScenarioOptions(workingDirectory, ScenarioOptionsBase.getDefault());

        /** set to true in order to make server wait for at least 1 client, for
         * instance viewer client, for fals the ScenarioServer starts the simulation
         * immediately */
        boolean waitForClients = scenarioOptions.getBoolean("waitForClients");
        File configFile = new File(scenarioOptions.getSimulationConfigName());

        /** geographic information */
        LocationSpec locationSpec = scenarioOptions.getLocationSpec();
        ReferenceFrame referenceFrame = locationSpec.referenceFrame();

        /** open server port for clients to connect to */
        SimulationServer.INSTANCE.startAcceptingNonBlocking();
        SimulationServer.INSTANCE.setWaitForClients(waitForClients);

        /** load MATSim configs - including av.xml configurations, load routing packages */
        GlobalAssert.that(configFile.exists());
        DvrpConfigGroup dvrpConfigGroup = new DvrpConfigGroup();
        dvrpConfigGroup.setTravelTimeEstimationAlpha(0.05);
        Config config = ConfigUtils.loadConfig(configFile.toString(), new AVConfigGroup(), dvrpConfigGroup);
        config.planCalcScore().addActivityParams(new ActivityParams("activity"));
        /** MATSim does not allow the typical duration not to be set, therefore for scenarios
         * generated from taxi data such as the "SanFrancisco" scenario, it is set to 1 hour. */
        for (ActivityParams activityParams : config.planCalcScore().getActivityParams()) {
            // TODO set typical duration in scenario generation and remove
            activityParams.setTypicalDuration(3600.0);
        }

        /** output directory for saving results */
        String outputdirectory = config.controler().getOutputDirectory();

        /** load MATSim scenario for simulation */
        Scenario scenario = ScenarioUtils.loadScenario(config);
        Network network = scenario.getNetwork();
        Population population = scenario.getPopulation();
        GlobalAssert.that(Objects.nonNull(network));
        GlobalAssert.that(Objects.nonNull(population));

        MatsimAmodeusDatabase db = MatsimAmodeusDatabase.initialize(network, referenceFrame);
        Controler controler = new Controler(scenario);
        controler.addOverridingModule(new DvrpTravelTimeModule());

        try {
            // load linkSpeedData if possible
            File linkSpeedDataFile = new File(scenarioOptions.getLinkSpeedDataName());
            System.out.println(linkSpeedDataFile.toString());
            LinkSpeedDataContainer lsData = LinkSpeedUtils.loadLinkSpeedData(linkSpeedDataFile);
            controler.addOverridingModule(new TrafficDataModule(lsData));
        } catch (Exception exception) {
            System.err.println("Could not load static linkspeed data, running with freespeeds.");
        }
        controler.addOverridingModule(new AVModule());
        controler.addOverridingModule(new DatabaseModule());
        controler.addOverridingModule(new AmodeusVehicleGeneratorModule());
        controler.addOverridingModule(new AmodeusDispatcherModule());
        controler.addOverridingModule(new AmodeusDatabaseModule(db));
        controler.addOverridingModule(new AmodeusVirtualNetworkModule(scenarioOptions));
        controler.addOverridingModule(new AmodeusVehicleToVSGeneratorModule());
        controler.addOverridingModule(new AmodeusModule());
        controler.addOverridingModule(new AbstractModule() {
            @Override
            public void install() {
                bind(Key.get(Network.class, Names.named("dvrp_routing"))).to(Network.class);
            }
        });

        /** With the subsequent lines an additional user-defined dispatcher is added, functionality
         * in class
         * DemoDispatcher, as long as the dispatcher was not selected in the file av.xml, it is not
         * used in the simulation. */
        controler.addOverridingModule(new AbstractModule() {
            @Override
            public void install() {
                AVUtils.registerDispatcherFactory(binder(), //
                        DemoDispatcher.class.getSimpleName(), DemoDispatcher.Factory.class);
            }
        });

        /** With the subsequent lines, additional user-defined initial placement logic called
         * generator is added,
         * functionality in class DemoGenerator. As long as the generator is not selected in the
         * file av.xml,
         * it is not used in the simulation. */
        controler.addOverridingModule(new AbstractModule() {
            @Override
            public void install() {
                AVUtils.registerGeneratorFactory(binder(), "DemoGenerator", DemoGenerator.Factory.class);
            }
        });

        /** With the subsequent lines, another custom router is added apart from the
         * {@link DefaultAVRouter},
         * it has to be selected in the av.xml file with the lines as follows:
         * <operator id="op1">
         * <param name="routerName" value="DefaultAStarLMRouter" />
         * <generator strategy="PopulationDensity">
         * ...
         * 
         * otherwise the normal {@link DefaultAVRouter} will be used. */
        controler.addOverridingModule(new AbstractModule() {
            @Override
            public void install() {
                bind(DefaultAStarLMRouter.Factory.class);
                AVUtils.bindRouterFactory(binder(), DefaultAStarLMRouter.class.getSimpleName())//
                        .to(DefaultAStarLMRouter.Factory.class);
            }
        });

        /** run simulation */
        controler.run();

        /** close port for visualizaiton */
        SimulationServer.INSTANCE.stopAccepting();

        /** perform analysis of simulation, a demo of how to add custom
         * analysis methods is provided in the package amod.demo.analysis */
        Analysis analysis = Analysis.setup(scenarioOptions, new File(outputdirectory), network, db);
        CustomAnalysis.addTo(analysis);
        analysis.run();

    }
}