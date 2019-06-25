/**
 * 
 */
package amod.demo.dispatcher;

import java.util.Iterator;

import org.matsim.api.core.v01.network.Network;
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

/**
 * @author asasidharan
 *
 */
public class DemoDispatcherSanfrancisco extends RebalancingDispatcher {

	protected DemoDispatcherSanfrancisco(Config config, AVDispatcherConfig avDispatcherConfig, TravelTime travelTime,
			ParallelLeastCostPathCalculator parallelLeastCostPathCalculator, EventsManager eventsManager,
			MatsimAmodeusDatabase db) {
		super(config, avDispatcherConfig, travelTime, parallelLeastCostPathCalculator, eventsManager, db);
		// TODO Auto-generated constructor stub
	}


	@Override
	protected void redispatch(double now) {
		
		for (Iterator<AVRequest> itr = getAVRequests().iterator(); itr.hasNext();) {
			System.out.println("Inside request itr ===============================================================================");
			AVRequest request = itr.next();
			getPickupTaxi(request);
		
		}

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
            return new DemoDispatcherSanfrancisco(config, avconfig, travelTime, router, eventsManager, db);
        }
    }
}