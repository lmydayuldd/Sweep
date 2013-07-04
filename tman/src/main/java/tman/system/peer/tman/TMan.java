package tman.system.peer.tman;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import se.sics.gvod.common.Self;
import se.sics.gvod.common.VodDescriptor;
import se.sics.gvod.croupier.PeerSamplePort;
import se.sics.gvod.croupier.events.CroupierSample;
import se.sics.gvod.net.VodNetwork;
import se.sics.gvod.timer.*;
import se.sics.kompics.ComponentDefinition;
import se.sics.kompics.Handler;
import se.sics.kompics.Negative;
import se.sics.kompics.Positive;
import se.sics.kompics.address.Address;
import tman.system.peer.tman.BroadcastTManPartnersPort.TmanPartners;
import tman.system.peer.tman.IndexRoutingPort.IndexEvent;
import tman.system.peer.tman.IndexRoutingPort.IndexMessage;
import tman.system.peer.tman.LeaderRequest.AddIndexEntry;
import tman.system.peer.tman.LeaderRequest.GapCheck;
import tman.system.peer.tman.LeaderStatusPort.LeaderStatus;
import tman.system.peer.tman.LeaderStatusPort.NodeCrashEvent;
import tman.system.peer.tman.LeaderStatusPort.NodeSuggestion;
import tman.system.peer.tman.TmanMessage.TManRequest;
import tman.system.peer.tman.TmanMessage.TManResponse;

import common.configuration.TManConfiguration;
import common.peer.RequestTimeout;

/**
 * Component creating a gradient network from Cyclon samples according to a
 * preference function using the TMan framework.
 */
public final class TMan extends ComponentDefinition {
	Positive<PeerSamplePort> croupierSamplePort = positive(PeerSamplePort.class);
	Positive<VodNetwork> networkPort = positive(VodNetwork.class);
	Positive<Timer> timerPort = positive(Timer.class);
	Negative<RoutedEventsPort> routedEventsPort = negative(RoutedEventsPort.class);
	Positive<BroadcastTManPartnersPort> broadcastTmanPartnersPort = positive(BroadcastTManPartnersPort.class);
	Negative<LeaderStatusPort> leaderStatusPort = negative(LeaderStatusPort.class);
	Negative<IndexRoutingPort> indexRoutingPort = negative(IndexRoutingPort.class);

	private long period;
	private Self self;
	private TManConfiguration tmanConfiguration;
	private Random random;
	private TManView tmanView;
	private Map<UUID, VodDescriptor> outstandingShuffles;
	private boolean leader;

	/**
	 * Timeout to periodically issue exchanges.
	 */
	public class TManSchedule extends Timeout {

		public TManSchedule(SchedulePeriodicTimeout request) {
			super(request);
		}
	}

	public TMan() {
		subscribe(handleInit, control);
		subscribe(handleRound, timerPort);
		subscribe(handleRequestTimeout, timerPort);
		subscribe(handleCyclonSample, croupierSamplePort);
		subscribe(handleTManResponse, networkPort);
		subscribe(handleTManRequest, networkPort);
		subscribe(handleAddIndexEntryRequest, routedEventsPort);
		subscribe(handleRoutedMessage, networkPort);
		subscribe(handleLeaderStatus, leaderStatusPort);
		subscribe(handleGapCheck, routedEventsPort);
		subscribe(handleIndexRouting, indexRoutingPort);
		subscribe(handleIndexMessage, networkPort);
		subscribe(handleNodeCrash, leaderStatusPort);
		subscribe(handeNodeSuggestion, leaderStatusPort);
	}

	/**
	 * Initialize the state of the component.
	 */
	Handler<TManInit> handleInit = new Handler<TManInit>() {
		@Override
		public void handle(TManInit init) {
			self = init.getSelf();
			tmanConfiguration = init.getConfiguration();
			period = tmanConfiguration.getPeriod();
			outstandingShuffles = Collections.synchronizedMap(new HashMap<UUID, Address>());
			random = new Random(init.getConfiguration().getSeed());
			tmanView = new TManView(self, tmanConfiguration.getViewSize(),
					tmanConfiguration.getConvergenceSimilarity());
			leader = false;

			SchedulePeriodicTimeout rst = new SchedulePeriodicTimeout(period, period);
			rst.setTimeoutEvent(new TManSchedule(rst));
			trigger(rst, timerPort);
		}
	};

	/**
	 * Initiate a identifier exchange every round.
	 */
	Handler<TManSchedule> handleRound = new Handler<TManSchedule>() {
		@Override
		public void handle(TManSchedule event) {
			if (tmanView.isEmpty() == false) {
				initiateShuffle(tmanView.selectPeerToShuffleWith());
			}
		}
	};

	/**
	 * Initiate a exchange with a random node of each Cyclon sample to speed up
	 * convergence and prevent partitioning.
	 */
	Handler<CroupierSample> handleCyclonSample = new Handler<CroupierSample>() {
		@Override
		public void handle(CroupierSample event) {
			List<VodDescriptor> sample = event.getNodes();

			if (sample.size() > 0) {
				int n = random.nextInt(sample.size());
				initiateShuffle(sample.get(n));
			}
		}
	};

	/**
	 * Answer a {@link TManRequest} with the nodes from the view preferred by
	 * the inquirer.
	 */
	Handler<TManRequest> handleTManRequest = new Handler<TManRequest>() {
		@Override
		public void handle(TManRequest event) {
			Address exchangePartner = event.getSource();
			Collection<VodDescriptor> exchangeSets = tmanView.getExchangeNodes(exchangePartner,
					tmanConfiguration.getExchangeCount());
			TManResponse rResponse = new TManResponse(event.getRequestId(), exchangeSets, self.getAddress(),
					exchangePartner);
			trigger(rResponse, networkPort);

			tmanView.merge(event.getExchangeCollection());
			broadcastView();
		}
	};

	/**
	 * Merge the entries from the response to the view.
	 */
	Handler<TManResponse> handleTManResponse = new Handler<TManResponse>() {
		@Override
		public void handle(TManResponse event) {
			// cancel shuffle timeout
			UUID shuffleId = event.getRequestId();
			if (outstandingShuffles.containsKey(shuffleId)) {
				outstandingShuffles.remove(shuffleId);
				CancelTimeout ct = new CancelTimeout(shuffleId);
				trigger(ct, timerPort);
			}

			tmanView.merge(event.getExchangeCollection());
			broadcastView();
		}
	};

	/**
	 * This handler listens to updates regarding the leader status
	 */
	Handler<LeaderStatus> handleLeaderStatus = new Handler<LeaderStatus>() {
		@Override
		public void handle(LeaderStatus event) {
			leader = event.isLeader();
		}
	};

	/**
	 * Updates TMan's view by removing crashed nodes from it, eg. old leaders
	 */
	Handler<NodeCrashEvent> handleNodeCrash = new Handler<NodeCrashEvent>() {
		@Override
		public void handle(NodeCrashEvent event) {
			tmanView.remove(event.getDeadNode());
		}
	};

	/**
	 * A handler that takes a suggestion and adds it to the view. This will
	 * prevent the node from thinking that it is a leader candidate in case it
	 * only has nodes below itself even though it's not at the top of the
	 * overlay topology. Even if the suggested node might not fit in perfectly
	 * it can be dropped later when the node converges
	 */
	Handler<NodeSuggestion> handeNodeSuggestion = new Handler<NodeSuggestion>() {
		@Override
		public void handle(NodeSuggestion event) {
			if (event.getSuggestion() != null && event.getSuggestion().getId() < self.getId()) {
				ArrayList<Address> suggestionList = new ArrayList<Address>();
				suggestionList.add(event.getSuggestion());
				tmanView.merge(suggestionList);
			}
		}
	};

	/**
	 * Remove a node from the view if it didn't respond to a request.
	 */
	Handler<RequestTimeout> handleRequestTimeout = new Handler<RequestTimeout>() {
		@Override
		public void handle(RequestTimeout event) {
			UUID rTimeoutId = (UUID)event.getTimeoutId();
			Address deadNode = outstandingShuffles.remove(rTimeoutId);

			if (deadNode != null) {
				tmanView.remove(deadNode);
			}
		}
	};

	/**
	 * Forward a {@link AddIndexEntry} event to the leader. Return it back to
	 * Search in case this is the leader.
	 */
	Handler<AddIndexEntry> handleAddIndexEntryRequest = new Handler<AddIndexEntry>() {
		@Override
		public void handle(AddIndexEntry event) {
			if (leader) {
				trigger(event, routedEventsPort);
			} else {
				forwardToLeader(new RoutedMessage(self, event));
			}
		}
	};

	/**
	 * Forward the {@link RoutedMessage} to the leader.
	 */
	Handler<RoutedMessage> handleRoutedMessage = new Handler<RoutedMessage>() {
		@Override
		public void handle(RoutedMessage event) {
			if (leader) {
				trigger(event.getEvent(), routedEventsPort);
			} else {
				forwardToLeader(event);
			}
		}
	};

	/**
	 * Broadcasts the {@link IndexEvent} to all nodes in its view that is below
	 * itself in the gradient topology tree
	 */
	Handler<IndexEvent> handleIndexRouting = new Handler<IndexEvent>() {
		@Override
		public void handle(IndexEvent event) {
			IndexMessage indexMessage = null;

			for (Address addr : tmanView.getLowerNodes()) {
				indexMessage = new IndexMessage(event, self, addr);
				trigger(indexMessage, networkPort);
			}
		}
	};

	/**
	 * Forwards the {@link IndexMessage} to whoever that listens to the
	 * indexRoutingPort
	 */
	Handler<IndexMessage> handleIndexMessage = new Handler<IndexMessage>() {
		@Override
		public void handle(IndexMessage event) {
			trigger(event.getEvent(), indexRoutingPort);
		}
	};

	/**
	 * Forward a {@link GapCheck} event to the leader. Return it back to Search
	 * in case this is the leader.
	 */
	Handler<GapCheck> handleGapCheck = new Handler<GapCheck>() {
		@Override
		public void handle(GapCheck event) {
			if (leader) {
				trigger(event, routedEventsPort);
			} else {
				forwardToLeader(new RoutedMessage(self, event));
			}
		}
	};

	/**
	 * Initiate the shuffling process for the given node.
	 * 
	 * @param exchangePartner
	 *            the address of the node to shuffle with
	 */
	private void initiateShuffle(VodDescriptor exchangePartner) {
		Collection<Address> exchangeSets = tmanView.getExchangeNodes(exchangePartner,
				tmanConfiguration.getExchangeCount());

		ScheduleTimeout rst = new ScheduleTimeout(tmanConfiguration.getPeriod());
		rst.setTimeoutEvent(new RequestTimeout(rst));
		UUID rTimeoutId = (UUID)rst.getTimeoutEvent().getTimeoutId();

		outstandingShuffles.put(rTimeoutId, exchangePartner);
		TManRequest rRequest = new TManRequest(rTimeoutId, exchangeSets, self, exchangePartner);

		trigger(rst, timerPort);
		trigger(rRequest, networkPort);
	}

	/**
	 * Route a message to the leader. Forwards the message to nodes closer to
	 * the leader with a higher probability so that not always the same route is
	 * chosen. His decreases the probability of always choosing a wrong route.
	 * 
	 * @param message
	 *            the message to be forwarded
	 */
	private void forwardToLeader(RoutedMessage message) {
		ArrayList<Address> peers = tmanView.getHigherNodes();
		if (peers.size() == 0) {
			return;
		}
		message.setDestination(getSoftMaxAddress(peers));
		trigger(message, networkPort);
	}

	/**
	 * Broadcast the current view to the listening components.
	 */
	private void broadcastView() {
		trigger(new TmanPartners(tmanView.isConverged(), tmanView.getHigherNodes(),
				tmanView.getLowerNodes()), broadcastTmanPartnersPort);
	}

	// If you call this method with a list of entries, it will
	// return a single node, weighted towards the 'best' node (as defined by
	// ComparatorById) with the temperature controlling the weighting.
	// A temperature of '1.0' will be greedy and always return the best node.
	// A temperature of '0.000001' will return a random node.
	// A temperature of '0.0' will throw a divide by zero exception :)
	// Reference:
	// http://webdocs.cs.ualberta.ca/~sutton/book/2/node4.html
	private Address getSoftMaxAddress(List<Address> entries) {
		Collections.sort(entries, new ClosetIdToLeader());

		double rnd = random.nextDouble();
		double total = 0.0d;
		double[] values = new double[entries.size()];
		int j = entries.size() + 1;
		for (int i = 0; i < entries.size(); i++) {
			// get inverse of values - lowest have highest value.
			double val = j;
			j--;
			values[i] = Math.exp(val / tmanConfiguration.getTemperature());
			total += values[i];
		}

		for (int i = 0; i < values.length; i++) {
			if (i != 0) {
				values[i] += values[i - 1];
			}
			// normalise the probability for this entry
			double normalisedUtility = values[i] / total;
			if (normalisedUtility >= rnd) {
				return entries.get(i);
			}
		}

		return entries.get(entries.size() - 1);
	}

	private class ClosetIdToLeader implements Comparator<Address> {
		@Override
		public int compare(Address o1, Address o2) {
			assert (o1.getId() == o2.getId());

			if (o1.getId() > o2.getId()) {
				return 1;
			} else if (o1.getId() < o2.getId()) {
				return -1;
			}
			return 0;
		}
	}
}
