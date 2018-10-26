package amod.demo.dispatcher.IAMoD;

import java.net.Socket;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.apache.commons.lang3.tuple.Pair;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.api.core.v01.network.Node;
import org.matsim.core.api.experimental.events.EventsManager;
import org.matsim.core.config.Config;
import org.matsim.core.network.NetworkUtils;
import org.matsim.core.router.util.TravelTime;
import org.matsim.core.utils.collections.QuadTree;

import com.google.inject.Inject;
import com.google.inject.name.Named;

import amod.demo.dispatcher.SMPC.SMPCutils;
import amod.demo.dispatcher.carpooling.ICRApoolingDispatcherUtils;
import amod.demo.dispatcher.carpooling.RebalanceCarSelector;
import amod.demo.dispatcher.claudioForDejan.TravelTimeCalculatorClaudioForDejan;
import ch.ethz.idsc.amodeus.dispatcher.core.PartitionedDispatcher;
import ch.ethz.idsc.amodeus.dispatcher.core.RoboTaxi;
import ch.ethz.idsc.amodeus.dispatcher.core.RoboTaxiStatus;
import ch.ethz.idsc.amodeus.dispatcher.util.AbstractRoboTaxiDestMatcher;
import ch.ethz.idsc.amodeus.dispatcher.util.AbstractVirtualNodeDest;
import ch.ethz.idsc.amodeus.dispatcher.util.BipartiteMatchingUtils;
import ch.ethz.idsc.amodeus.dispatcher.util.DistanceFunction;
import ch.ethz.idsc.amodeus.dispatcher.util.DistanceHeuristics;
import ch.ethz.idsc.amodeus.dispatcher.util.EuclideanDistanceFunction;
import ch.ethz.idsc.amodeus.dispatcher.util.GlobalBipartiteMatching;
import ch.ethz.idsc.amodeus.dispatcher.util.RandomVirtualNodeDest;
import ch.ethz.idsc.amodeus.matsim.SafeConfig;
import ch.ethz.idsc.amodeus.virtualnetwork.VirtualLink;
import ch.ethz.idsc.amodeus.virtualnetwork.VirtualNetwork;
import ch.ethz.idsc.amodeus.virtualnetwork.VirtualNode;
import ch.ethz.idsc.jmex.Container;
import ch.ethz.idsc.jmex.DoubleArray;
import ch.ethz.idsc.jmex.java.JavaContainerSocket;
import ch.ethz.idsc.jmex.matlab.MfileContainerServer;
import ch.ethz.idsc.tensor.Tensor;
import ch.ethz.idsc.tensor.Tensors;
import ch.ethz.matsim.av.config.AVDispatcherConfig;
import ch.ethz.matsim.av.config.AVGeneratorConfig;
import ch.ethz.matsim.av.dispatcher.AVDispatcher;
import ch.ethz.matsim.av.framework.AVModule;
import ch.ethz.matsim.av.router.AVRouter;

public class IAMoDdispatcher extends PartitionedDispatcher {
	private final int rebalancingPeriod;
	private final int dispatchPeriod;
	private final AbstractVirtualNodeDest virtualNodeDest;
	private final AbstractRoboTaxiDestMatcher vehicleDestMatcher;
	private final int numRobotaxi;
	private int total_rebalanceCount = 0;
	private Tensor printVals = Tensors.empty();
	// private final LPVehicleRebalancing lpVehicleRebalancing;
	private final DistanceFunction distanceFunction;
	private final DistanceHeuristics distanceHeuristics;
	private final Network network;
	private final QuadTree<Link> pendingLinkTree;
	private final double[] networkBounds;
	private final Config config;
	private final int timeStep;
	private RebalanceCarSelector rebalanceSelector;
	private double dispatchTime;
	private final BipartiteMatchingUtils bipartiteMatchingEngine;
	private final AVRouter router;
	private final List<Id<Node>> nodeList;
	private final List<double[]> reducedNetwork;
	private final List<double[]> reducedNetworkCap;
	private final List<double[]> reducedNetworkVel;
	private final List<double[]> reducedNetworkDist;
	private final List<double[]> reducedNetworkLoc;
	private double timeInvariantPeriod;

	// final JavaContainerSocket javaContainerSocket;

	IAMoDdispatcher( //
			Config config, AVDispatcherConfig avconfig, //
			AVGeneratorConfig generatorConfig, //
			TravelTime travelTime, //
			AVRouter router, //
			EventsManager eventsManager, //
			Network network, //
			VirtualNetwork<Link> virtualNetwork, //
			AbstractVirtualNodeDest abstractVirtualNodeDest, //
			AbstractRoboTaxiDestMatcher abstractVehicleDestMatcher) {
		super(config, avconfig, travelTime, router, eventsManager, virtualNetwork);
		virtualNodeDest = abstractVirtualNodeDest;
		vehicleDestMatcher = abstractVehicleDestMatcher;
		numRobotaxi = (int) generatorConfig.getNumberOfVehicles();
		networkBounds = NetworkUtils.getBoundingBox(network.getNodes().values());
		pendingLinkTree = new QuadTree<>(networkBounds[0], networkBounds[1], networkBounds[2], networkBounds[3]);
		for (Link link : network.getLinks().values()) {
			pendingLinkTree.put(link.getCoord().getX(), link.getCoord().getY(), link);
		}
		// lpVehicleRebalancing = new LPVehicleRebalancing(virtualNetwork);
		SafeConfig safeConfig = SafeConfig.wrap(avconfig);
		dispatchPeriod = safeConfig.getInteger("dispatchPeriod", 30);
		rebalancingPeriod = safeConfig.getInteger("rebalancingPeriod", 300);
		this.network = network;
		distanceHeuristics = DistanceHeuristics.valueOf(safeConfig.getString("distanceHeuristics", //
				DistanceHeuristics.EUCLIDEAN.name()).toUpperCase());
		System.out.println("Using DistanceHeuristics: " + distanceHeuristics.name());
		this.distanceFunction = distanceHeuristics.getDistanceFunction(network);
		this.config = config;
		this.timeStep = 5;
		this.timeInvariantPeriod = 1*60*60;
		this.bipartiteMatchingEngine = new BipartiteMatchingUtils(network);
		this.router = router;
		this.nodeList = IAMoDdispatcherUtils.getNetworkReducedNodeList(network);
		this.reducedNetwork = IAMoDdispatcherUtils.getReducedNetwork(network, nodeList);
		this.reducedNetworkCap = IAMoDdispatcherUtils.getReducedNetworkCapacityForMatlab(network, nodeList);
		this.reducedNetworkVel = IAMoDdispatcherUtils.getReducedNetworkVelocityForMatlab(network, nodeList);
		this.reducedNetworkDist = IAMoDdispatcherUtils.getReducedNetworkDistance(network, nodeList);
		this.reducedNetworkLoc = IAMoDdispatcherUtils.getReducedNetworkLocationForMatlab(network, nodeList);
	}

	@Override
	public void redispatch(double now) {

		// PART I: rebalance all vehicles periodically
		final long round_now = Math.round(now);

		if (round_now == 6*60*60) {

			List<double[]> requests = IAMoDdispatcherUtils.getFlowsOutForMATLAB(network, nodeList, config, round_now);
			
			try {
				// initialize server
				JavaContainerSocket javaContainerSocket = new JavaContainerSocket(
						new Socket("localhost", MfileContainerServer.DEFAULT_PORT));

				{ // add inputs to server
					Container container = SMPCutils.getContainerInit();

					double[] numberNodes = new double[]{reducedNetwork.size()};
	                container.add((new DoubleArray("numberNodes", new int[] { 1 }, numberNodes )));

	             // add network to container
	                int index = 0;
	                for(double[] nodeRow: reducedNetwork) {
	                    container.add((new DoubleArray("roadGraph" + index, new int[] { nodeRow.length }, nodeRow)));
	                    index += 1;
	                }

	             // add distance to container
	                int indexdist = 0;
	                for(double[] dist: reducedNetworkDist) {
	                    container.add((new DoubleArray("distance" + indexdist, new int[] { dist.length }, dist)));
	                    indexdist += 1;
	                }

	             // add velocity to container
	                int indexvel = 0;
	                for(double[] nodeRow: reducedNetworkVel) {
	                    container.add((new DoubleArray("velocity" + indexvel, new int[] { nodeRow.length }, nodeRow)));
	                    indexvel += 1;
	                }

	             // add capacity to container
	                int indexcap = 0;
	                for(double[] nodeRow: reducedNetworkCap) {
	                    container.add((new DoubleArray("capacity" + indexcap, new int[] { nodeRow.length }, nodeRow)));
	                    indexcap += 1;
	                }
	                
	                
	             // add location to container
	                int indexloc = 0;
	                for(double[] nodeRow: reducedNetworkLoc) {
	                    container.add((new DoubleArray("location" + indexloc, new int[] { nodeRow.length }, nodeRow)));
	                    indexloc += 1;
	                }
	                
	             // add request to container
	                int indexreq = 0;
	                for(double[] nodeRow: requests) {
	                    container.add((new DoubleArray("request" + indexreq, new int[] { nodeRow.length }, nodeRow)));
	                    indexreq += 1;
	                }
	                
	                double[] numberRequests = new double[]{requests.size()};
	                container.add((new DoubleArray("numberRequests", new int[] { 1 }, numberRequests )));
	                

					System.out.println("Sending to server");
					javaContainerSocket.writeContainer(container);

				}

				{ // get outputs from server
					System.out.println("Waiting for server");
					Container container = javaContainerSocket.blocking_getContainer();
					System.out.println("received: " + container);

					// get control inputs for rebalancing from container
					List<double[]> ControlLaw = new ArrayList<>();
					for (int i = 1; i <= container.size(); ++i) {
						ControlLaw.add(SMPCutils.getArray(container, "solution" + i));
					}

				}

				javaContainerSocket.close();

			} catch (Exception exception) {
				exception.printStackTrace();
				throw new RuntimeException(); // dispatcher will not work if
												// constructor has issues
			}

		}

	}

	private Map<VirtualNode<Link>, List<RoboTaxi>> getVirtualNodeStayRoboTaxi() {
		return virtualNetwork.binToVirtualNode(getRoboTaxiSubset(RoboTaxiStatus.STAY), RoboTaxi::getDivertableLocation);
	}

	private Map<VirtualNode<Link>, List<RoboTaxi>> getVirtualNodeDestinationRebalancingRoboTaxi() {
		return virtualNetwork.binToVirtualNode(getRoboTaxiSubset(RoboTaxiStatus.REBALANCEDRIVE),
				RoboTaxi::getCurrentDriveDestination);
	}

	private Map<VirtualNode<Link>, List<RoboTaxi>> getVirtualNodeDestinationWithCustomerRoboTaxi() {
		return virtualNetwork.binToVirtualNode(getRoboTaxiSubset(RoboTaxiStatus.DRIVEWITHCUSTOMER),
				RoboTaxi::getCurrentDriveDestination);
	}

	private Collection<RoboTaxi> getRobotaxiStayOrFinishRebalance() {
		return getDivertableUnassignedRoboTaxis().stream().filter(
				car -> car.getStatus() == RoboTaxiStatus.STAY || (car.getStatus() == RoboTaxiStatus.REBALANCEDRIVE
						&& virtualNetwork.getVirtualNode(car.getCurrentDriveDestination()) == virtualNetwork
								.getVirtualNode(car.getDivertableLocation())))
				.collect(Collectors.toList());
	}

	@Override
	protected String getInfoLine() {
		return String.format("%s RV=%s H=%s", //
				super.getInfoLine(), //
				total_rebalanceCount, //
				printVals.toString() //
		);
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

		@Inject(optional = true)
		private VirtualNetwork<Link> virtualNetwork;

		@Inject
		private Config config;

		@Override
		public AVDispatcher createDispatcher(AVDispatcherConfig avconfig, AVRouter router) {
			AVGeneratorConfig generatorConfig = avconfig.getParent().getGeneratorConfig();

			AbstractVirtualNodeDest abstractVirtualNodeDest = new RandomVirtualNodeDest();
			AbstractRoboTaxiDestMatcher abstractVehicleDestMatcher = new GlobalBipartiteMatching(
					EuclideanDistanceFunction.INSTANCE);

			return new IAMoDdispatcher(config, avconfig, generatorConfig, travelTime, router, eventsManager, network,
					virtualNetwork, abstractVirtualNodeDest, abstractVehicleDestMatcher);
		}
	}
}
