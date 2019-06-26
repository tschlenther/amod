/**
 * 
 */
package amod.demo.dispatcher;

import ch.ethz.idsc.amodeus.dispatcher.core.DispatcherConfig;
import ch.ethz.idsc.amodeus.dispatcher.core.RoboTaxi;
import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.contrib.util.distance.DistanceUtils;
import org.matsim.core.api.experimental.events.EventsManager;
import org.matsim.core.config.Config;
import org.matsim.core.router.util.TravelTime;

import com.google.inject.Inject;
import com.google.inject.name.Named;

import ch.ethz.idsc.amodeus.dispatcher.core.RebalancingDispatcher;
import ch.ethz.idsc.amodeus.net.MatsimAmodeusDatabase;
import ch.ethz.matsim.av.config.AVDispatcherConfig;
import ch.ethz.matsim.av.dispatcher.AVDispatcher;
import ch.ethz.matsim.av.framework.AVModule;
import ch.ethz.matsim.av.passenger.AVRequest;
import ch.ethz.matsim.av.plcpc.ParallelLeastCostPathCalculator;
import ch.ethz.matsim.av.router.AVRouter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * @author asasidharan
 *
 */
public class CustomRandomDispatcher extends RebalancingDispatcher {

    private final int dispatchPeriod;
    private final ArrayList<Link> links;
    private final Random randGen = new Random(1234);


    protected CustomRandomDispatcher(Config config, AVDispatcherConfig avDispatcherConfig, TravelTime travelTime,
                                     ParallelLeastCostPathCalculator parallelLeastCostPathCalculator, EventsManager eventsManager,
                                     MatsimAmodeusDatabase db, Network network) {
		super(config, avDispatcherConfig, travelTime, parallelLeastCostPathCalculator, eventsManager, db);
        links = new ArrayList<>(network.getLinks().values());
        Collections.shuffle(links, randGen);
        DispatcherConfig dispatcherConfig = DispatcherConfig.wrap(avDispatcherConfig);
        dispatchPeriod = dispatcherConfig.getDispatchPeriod(10);
	}


	@Override
	protected void redispatch(double now) {
        if(now % dispatchPeriod == 0){
            System.out.println("dispatching at time=" + now);

            if(! getUnassignedAVRequests().isEmpty() && ! getDivertableUnassignedRoboTaxis().isEmpty()){
                List<RoboTaxi> availableTaxis = new ArrayList<>(getDivertableUnassignedRoboTaxis());
                for(AVRequest request : getUnassignedAVRequests()){
                    if(availableTaxis.size() == 0){
                        System.out.println("there is an undersupply of taxis the moment");
                        break;
                    } else{
                        setRoboTaxiPickup(availableTaxis.remove(randGen.nextInt(availableTaxis.size())), request);
                    }
                }
            }

            if(! getDivertableUnassignedRoboTaxis().isEmpty()){
                List<RoboTaxi> availableTaxis = new ArrayList<>(getDivertableUnassignedRoboTaxis());
                System.out.println("number of idle taxis after dispatch = " + availableTaxis.size());
                for (RoboTaxi taxi : availableTaxis) setRoboTaxiRebalance(taxi,links.get(100));
            }
        }
    }



	private RoboTaxi getClosestUnassignedTaxi(AVRequest request){
	    Coord requestCoord = request.getFromLink().getCoord();
	    double closestDist = Double.MAX_VALUE;
	    RoboTaxi closestTaxi = null;
	    for(RoboTaxi taxi : getDivertableUnassignedRoboTaxis()){
            double dist = DistanceUtils.calculateSquaredDistance(requestCoord, taxi.getDivertableLocation().getCoord());
            if(dist < closestDist){
                closestDist = dist;
                closestTaxi = taxi;
            }
        }
        return closestTaxi;
    }
	
	public static class Factory implements AVDispatcherFactory {
        @Inject
        @Named(AVModule.AV_MODE)
        private TravelTime travelTime;

        @Inject
        private EventsManager eventsManager;

        @Inject
        @Named(AVModule.AV_MODE)
        private Network network;

        @Inject
        private Config config;

        @Inject
        private MatsimAmodeusDatabase db;

        @Override
        public AVDispatcher createDispatcher(AVDispatcherConfig avconfig, AVRouter router) {
            return new CustomRandomDispatcher(config, avconfig, travelTime, router, eventsManager, db, network);
        }
    }
}