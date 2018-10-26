package amod.demo.dispatcher.IAMoD;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Set;

import org.apache.commons.lang3.tuple.Pair;
import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.api.core.v01.network.Node;
import org.matsim.api.core.v01.population.Activity;
import org.matsim.api.core.v01.population.Person;
import org.matsim.api.core.v01.population.Plan;
import org.matsim.api.core.v01.population.PlanElement;
import org.matsim.api.core.v01.population.Population;
import org.matsim.core.config.Config;
import org.matsim.core.network.NetworkUtils;
import org.matsim.core.router.StageActivityTypes;
import org.matsim.core.router.StageActivityTypesImpl;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.pt.PtConstants;

import amod.demo.ext.UserReferenceFrames;
import ch.ethz.idsc.amodeus.prep.PopulationTools;
import ch.ethz.idsc.amodeus.prep.Request;
import ch.ethz.idsc.amodeus.util.math.GlobalAssert;
import ch.ethz.idsc.amodeus.virtualnetwork.VirtualNetwork;
import ch.ethz.idsc.amodeus.virtualnetwork.VirtualNode;
import ch.ethz.matsim.av.passenger.AVRequest;

public enum IAMoDdispatcherUtils {
	;

	public static List<Id<Node>> getNetworkReducedNodeList(Network network) {
		List<Id<Node>> listNodes = new ArrayList<Id<Node>>();
		int indexNode = 0;
		for (Node node : network.getNodes().values()) {
			if (node.getInLinks().size() >= 2 || node.getOutLinks().size() >= 2) {
				listNodes.add(indexNode, node.getId());
				indexNode += 1;
			}
		}

		return listNodes;
	}

	public static List<double[]> getReducedNetwork(Network network, List<Id<Node>> listNodes) {

		List<double[]> listNodesInter = new ArrayList<double[]>();

		for (Id<Node> nodeID : listNodes) {
			Node node = network.getNodes().get(nodeID);
			int size = node.getOutLinks().values().size();
			int i = 0;
			double[] adjacancy = new double[size];
			Node nodeIt = null;
			for (Link linkOut : node.getOutLinks().values()) {
				Node nodeTo = linkOut.getToNode();
				nodeIt = nodeTo;
				while (nodeIt.getOutLinks().size() < 2 && nodeIt.getInLinks().size() < 2
						&& !nodeIt.getOutLinks().values().isEmpty()) {

					nodeIt = nodeIt.getOutLinks().values().iterator().next().getToNode();

				}

				Id<Node> toNode = nodeIt.getId();
				adjacancy[i] = listNodes.indexOf(toNode);
				i += 1;
			}
			listNodesInter.add(listNodes.indexOf(node.getId()), adjacancy);
		}

		return listNodesInter;

	}

	public static List<double[]> getReducedNetworkDistance(Network network, List<Id<Node>> listNodes) {

		List<double[]> listNodesInter = new ArrayList<double[]>();

		for (Id<Node> nodeID : listNodes) {
			Node node = network.getNodes().get(nodeID);
			int size = node.getOutLinks().values().size();
			int i = 0;
			double[] adjacancy = new double[size];
			Node nodeIt = null;
			double distance = 0;
			for (Link linkOut : node.getOutLinks().values()) {
				Node nodeTo = linkOut.getToNode();
				distance = linkOut.getLength() / 3.28084;
				nodeIt = nodeTo;
				while (nodeIt.getOutLinks().size() < 2 && nodeIt.getInLinks().size() < 2
						&& !nodeIt.getOutLinks().values().isEmpty()) {
					Link link = nodeIt.getOutLinks().values().iterator().next();
					distance = distance + link.getLength() / 3.28084;
					nodeIt = link.getToNode();

				}

				adjacancy[i] = distance;
				i += 1;
			}
			listNodesInter.add(listNodes.indexOf(node.getId()), adjacancy);
		}

		return listNodesInter;

	}

	public static List<double[]> getReducedNetworkVelocityForMatlab(Network network, List<Id<Node>> listNodes) {
		List<double[]> listNodesVelocity = new ArrayList<double[]>();

		for (Id<Node> nodeID : listNodes) {
			Node node = network.getNodes().get(nodeID);
			if (node.getOutLinks().isEmpty() == true) {
				double[] adjacancy = {};
				listNodesVelocity.add(adjacancy);
			} else {
				int size = node.getOutLinks().values().size();
				double[] adjacancy = new double[size];
				int i = 0;
				for (Link linkOut : node.getOutLinks().values()) {
					adjacancy[i] = linkOut.getFreespeed();
					i += 1;
				}
				listNodesVelocity.add(listNodes.indexOf(node.getId()), adjacancy);
			}

		}

		return listNodesVelocity;

	}

	public static List<double[]> getReducedNetworkCapacityForMatlab(Network network, List<Id<Node>> listNodes) {
		List<double[]> listNodesCapacity = new ArrayList<double[]>();

		for (Id<Node> nodeID : listNodes) {
			Node node = network.getNodes().get(nodeID);
			if (node.getOutLinks().isEmpty() == true) {
				double[] adjacancy = {};
				listNodesCapacity.add(adjacancy);
			} else {
				int size = node.getOutLinks().values().size();
				double[] adjacancy = new double[size];
				int i = 0;
				for (Link linkOut : node.getOutLinks().values()) {
					adjacancy[i] = linkOut.getCapacity() / (60 * 60);
					i += 1;
				}
				listNodesCapacity.add(listNodes.indexOf(node.getId()), adjacancy);
			}

		}

		return listNodesCapacity;

	}

	public static List<double[]> getReducedNetworkLocationForMatlab(Network network, List<Id<Node>> listNodes) {
		List<double[]> listNodesLocation = new ArrayList<double[]>();

		for (Id<Node> nodeID : listNodes) {
			Node node = network.getNodes().get(nodeID);
			Coord coordNode = node.getCoord();
			Coord coordNodeWGS84 = UserReferenceFrames.SANFRANCISCO.coords_toWGS84().transform(coordNode);
			double[] location = new double[2];
			location[0] = coordNodeWGS84.getY();
			location[1] = coordNodeWGS84.getX();
			listNodesLocation.add(listNodes.indexOf(node.getId()), location);
		}

		return listNodesLocation;

	}

	public static List<double[]> getRequestMatlab(Network network, List<Id<Node>> listNodes, Config config,
			double round_now) {
		Scenario scenario = ScenarioUtils.loadScenario(config);
		Population population = scenario.getPopulation();
		StageActivityTypes stageActivityTypes = new StageActivityTypesImpl(PtConstants.TRANSIT_ACTIVITY_TYPE);
		List<double[]> dataList = new ArrayList<>();

		int helper = 0;
		int FromnodeIndex = 0;
		int TonodeIndex = 0;

		for (Person person : population.getPersons().values()) {
			for (Plan plan : person.getPlans()) {
				for (PlanElement planElement : plan.getPlanElements()) {
					if (planElement instanceof Activity) {
						Activity activity = (Activity) planElement;
						if (activity.getEndTime() >= round_now && activity.getEndTime() <= (round_now + 2 * 60 * 60)) {
							if (!stageActivityTypes.isStageActivity(activity.getType())) {
								Link link = network.getLinks().getOrDefault(activity.getLinkId(), null);
								if (link != null) {
									Node nodeReq = link.getFromNode();
									Node closestIntersec = findClostestIntersection(nodeReq, listNodes, network);
									FromnodeIndex = listNodes.indexOf(closestIntersec.getId());
									helper = 1;
								}
							}
						}
						if (activity.getStartTime() != Double.NEGATIVE_INFINITY && helper == 1) {
							if (!stageActivityTypes.isStageActivity(activity.getType())) {
								Link link = network.getLinks().getOrDefault(activity.getLinkId(), null);
								if (link != null) {
									Node nodeToReq = link.getToNode();
									Node closestToIntersec = findClostestIntersection(nodeToReq, listNodes, network);
									TonodeIndex = listNodes.indexOf(closestToIntersec.getId());
									dataList.add(new double[] { FromnodeIndex, TonodeIndex });
									helper = 0;
								}
							}
						}
					}

				}
			}
		}

		return dataList;

	}

	public static List<double[]> getFlowsOutForMATLAB(Network network, List<Id<Node>> listNodes, Config config,
			double round_now) {
		Scenario scenario = ScenarioUtils.loadScenario(config);
		Population population = scenario.getPopulation();
		List<double[]> dataList = new ArrayList<>();
		Set<Request> avRequests = PopulationTools.getAVRequests(population, network);
		int fromNodeIndex = 0;
		int toNodeIndex = 0;
		for (Request avRequest : avRequests) {
			double startTime = avRequest.startTime();
			if (startTime >= round_now && startTime <= ((round_now + 2 * 60 * 60))) {
				Link fromLink = avRequest.startLink();
				Link toLink = avRequest.endLink();
				if (fromLink != null && toLink != null) {
					Node nodeFrom = fromLink.getFromNode();
					Node closestFromIntersec = findClostestIntersection(nodeFrom, listNodes, network);
					Node nodeTo = toLink.getToNode();
					Node closestToIntersec = findClostestIntersection(nodeTo, listNodes, network);
					fromNodeIndex = listNodes.indexOf(closestFromIntersec.getId());
					toNodeIndex = listNodes.indexOf(closestToIntersec.getId());
					dataList.add(new double[] { fromNodeIndex, toNodeIndex });
				}

			}
		}
		

		return dataList;

	}

	static Node findClostestIntersection(Node nodeReqest, List<Id<Node>> nodeIdList, Network network) {
		GlobalAssert.that(nodeIdList != null);
		Node closestNode = null;
		double min = Double.POSITIVE_INFINITY;
		for (Id<Node> nodeId : nodeIdList) {
			Node node = network.getNodes().get(nodeId);
			double newDistance = NetworkUtils.getEuclideanDistance( //
					nodeReqest.getCoord(), //
					node.getCoord());
			if (closestNode == null || newDistance < min) {
				min = newDistance;
				closestNode = node;
			}
		}
		return closestNode;
	}
}
