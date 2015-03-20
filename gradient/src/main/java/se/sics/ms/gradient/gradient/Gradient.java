package se.sics.ms.gradient.gradient;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import se.sics.co.FailureDetectorPort;
import se.sics.gvod.common.RTTStore;
import se.sics.gvod.common.net.RttStats;
import se.sics.gvod.config.GradientConfiguration;
import se.sics.gvod.net.VodAddress;
import se.sics.gvod.net.VodNetwork;
import se.sics.gvod.timer.*;
import se.sics.gvod.timer.Timer;
import se.sics.gvod.timer.UUID;
import se.sics.kompics.*;
import se.sics.ms.common.MsSelfImpl;
import se.sics.ms.common.TransportHelper;
import se.sics.ms.configuration.MsConfig;
import se.sics.ms.gradient.control.CheckLeaderInfoUpdate;
import se.sics.ms.gradient.control.ControlMessageInternal;
import se.sics.ms.gradient.events.*;
import se.sics.ms.gradient.misc.UtilityComparator;
import se.sics.ms.gradient.ports.GradientRoutingPort;
import se.sics.ms.gradient.ports.GradientViewChangePort;
import se.sics.ms.gradient.ports.LeaderStatusPort;
import se.sics.ms.gradient.ports.LeaderStatusPort.LeaderStatus;
import se.sics.ms.gradient.ports.LeaderStatusPort.NodeCrashEvent;
import se.sics.ms.gradient.ports.PublicKeyPort;
import se.sics.ms.messages.*;
import se.sics.ms.ports.SelfChangedPort;
import se.sics.ms.timeout.IndividualTimeout;
import se.sics.ms.types.*;
import se.sics.ms.types.OverlayId;
import se.sics.ms.util.PartitionHelper;

import java.security.PublicKey;
import java.util.*;

import static se.sics.ms.util.PartitionHelper.updateBucketsInRoutingTable;
import se.sics.p2ptoolbox.croupier.api.CroupierPort;
import se.sics.p2ptoolbox.croupier.api.msg.CroupierSample;
import se.sics.p2ptoolbox.croupier.api.util.CroupierPeerView;
import se.sics.p2ptoolbox.croupier.api.util.PeerView;

/**
 * Component creating a gradient network from Croupier samples according to a
 * preference function.
 */
public final class Gradient extends ComponentDefinition {

    private static final Logger logger = LoggerFactory.getLogger(Gradient.class);
    Positive<CroupierPort> croupierSamplePort = positive(CroupierPort.class);
    Positive<VodNetwork> networkPort = positive(VodNetwork.class);
    Positive<Timer> timerPort = positive(Timer.class);
    Positive<GradientViewChangePort> gradientViewChangePort = positive(GradientViewChangePort.class);
    Positive<FailureDetectorPort> fdPort = requires(FailureDetectorPort.class);
    Negative<LeaderStatusPort> leaderStatusPort = negative(LeaderStatusPort.class);
    Positive<PublicKeyPort> publicKeyPort = positive(PublicKeyPort.class);
    Negative<GradientRoutingPort> gradientRoutingPort = negative(GradientRoutingPort.class);
    Positive<SelfChangedPort> selfChangedPort = positive(SelfChangedPort.class);

    private MsSelfImpl self;
    private GradientConfiguration config;
    private Random random;
    private GradientView gradientView;
    private UtilityComparator utilityComparator = new UtilityComparator();
    private Map<UUID, VodAddress> outstandingShuffles;
    private boolean leader;
    private VodAddress leaderAddress;
    private PublicKey leaderPublicKey;
    private Map<Integer, Long> shuffleTimes = new HashMap<Integer, Long>();
    int latestRttRingBufferPointer = 0;
    private long[] latestRtts;
    String compName;
    // This is a routing table maintaining a a list of descriptors for each category and its partitions.
    private Map<MsConfig.Categories, Map<Integer, HashSet<SearchDescriptor>>> routingTable;


    Comparator<SearchDescriptor> peerConnectivityComparator = new Comparator<SearchDescriptor>() {
        @Override
        public int compare(SearchDescriptor t0, SearchDescriptor t1) {
            if (t0.getVodAddress().equals(t1.getVodAddress())) {
                return 0;
            } else if (t0.isConnected() && t1.isConnected()) {
                return compareAvgRtt(t0, t1);
            } else if (!t0.isConnected() && t1.isConnected()) {
                return 1;
            } else if (t0.isConnected() && !t1.isConnected()) {
                return -1;
            } else if (t0.getAge() > t1.getAge()) {
                return 1;
            } else {
                return -1;
            }
        }

        private int compareAvgRtt(SearchDescriptor t0, SearchDescriptor t1) {
            RTTStore.RTT rtt0 = RTTStore.getRtt(t0.getId(), t0.getVodAddress());
            RTTStore.RTT rtt1 = RTTStore.getRtt(t1.getId(), t1.getVodAddress());

            if (rtt0 == null || rtt1 == null) {
                return 0;
            }

            RttStats rttStats0 = rtt0.getRttStats();
            RttStats rttStats1 = rtt1.getRttStats();
            if (rttStats0.getAvgRTT() == rttStats1.getAvgRTT()) {
                return 0;
            } else if (rttStats0.getAvgRTT() > rttStats1.getAvgRTT()) {
                return 1;
            } else {
                return -1;
            }
        }
    };

    /**
     * Timeout to periodically issue exchanges.
     */
    public class GradientRound extends IndividualTimeout {

        public GradientRound(SchedulePeriodicTimeout request, int id) {
            super(request, id);
        }
    }

    public Gradient(GradientInit init) {
        doInit(init);
        subscribe(handleStart, control);
        subscribe(handleRound, timerPort);
        subscribe(handleShuffleRequestTimeout, timerPort);
        subscribe(handleCroupierSample, croupierSamplePort);
        subscribe(handleShuffleResponse, networkPort);
        subscribe(handleShuffleRequest, networkPort);
        subscribe(handleLeaderLookupRequest, networkPort);
        subscribe(handleLeaderLookupResponse, networkPort);
        subscribe(handleLeaderStatus, leaderStatusPort);
        subscribe(handleNodeCrash, leaderStatusPort);
        subscribe(handleLeaderUpdate, leaderStatusPort);
        subscribe(handlePublicKeyBroadcast, publicKeyPort);
        subscribe(handleAddIndexEntryRequest, gradientRoutingPort);
        subscribe(handleIndexHashExchangeRequest, gradientRoutingPort);
        subscribe(handleReplicationPrepareCommit, gradientRoutingPort);
        subscribe(handleSearchRequest, gradientRoutingPort);
        subscribe(handleReplicationCommit, gradientRoutingPort);
        subscribe(handleLeaderLookupRequestTimeout, timerPort);
        subscribe(handleSearchResponse, networkPort);
        subscribe(handleSearchRequestTimeout, timerPort);
        subscribe(handleViewSizeRequest, gradientRoutingPort);

        subscribe(handleLeaderGroupInformationRequest, gradientRoutingPort);
        subscribe(handleFailureDetector, fdPort);
		subscribe(handlerControlMessageExchangeInitiation, gradientRoutingPort);
        subscribe(handlerControlMessageInternalRequest, gradientRoutingPort);

        subscribe(handlerSelfChanged, selfChangedPort);

    }
    /**
     * Initialize the state of the component.
     */
    private void doInit(GradientInit init) {

        self = ((MsSelfImpl)init.getSelf()).clone();
        config = init.getConfiguration();
        outstandingShuffles = Collections.synchronizedMap(new HashMap<UUID, VodAddress>());
        random = new Random(init.getConfiguration().getSeed());
        gradientView = new GradientView(self, config.getViewSize(), config.getConvergenceTest(), config.getConvergenceTestRounds());
        routingTable = new HashMap<MsConfig.Categories, Map<Integer, HashSet<SearchDescriptor>>>();
        leader = false;
        leaderAddress = null;
        latestRtts = new long[config.getLatestRttStoreLimit()];
        compName = "(" + self.getId() + ", " + self.getOverlayId() + ") ";
    }

    public Handler<Start> handleStart = new Handler<Start>() {

        @Override
        public void handle(Start e) {
            SchedulePeriodicTimeout rst = new SchedulePeriodicTimeout(config.getShufflePeriod(), config.getShufflePeriod());

            rst.setTimeoutEvent(new GradientRound(rst, self.getId()));
            trigger(rst, timerPort);
        }
    };

    private void removeNodeFromRoutingTable(OverlayAddress nodeToRemove)
    {
        MsConfig.Categories category = categoryFromCategoryId(nodeToRemove.getCategoryId());
        Map<Integer, HashSet<SearchDescriptor>> categoryRoutingMap = routingTable.get(category);
        if(categoryRoutingMap != null) {
            Set<SearchDescriptor> bucket = categoryRoutingMap.get(nodeToRemove.getPartitionId());

            if (bucket != null) {
                Iterator<SearchDescriptor> i = bucket.iterator();
                while (i.hasNext()) {
                    SearchDescriptor descriptor = i.next();

                    if (descriptor.getVodAddress().equals(nodeToRemove)) {
                        i.remove();
                        break;
                    }
                }
            }
        }
    }

    private void removeNodesFromLocalState(HashSet<VodAddress> nodesToRemove) {

        for(VodAddress suspectedNode: nodesToRemove) {

            removeNodeFromLocalState(new OverlayAddress(suspectedNode));
        }
    }
    private void removeNodeFromLocalState(OverlayAddress overlayAddress)
    {
        //remove suspected node from gradient view
        gradientView.remove(overlayAddress.getAddress());

        //remove suspected node from routing table
        removeNodeFromRoutingTable(overlayAddress);

        //remove suspected nodes from rtt store
        RTTStore.removeSamples(overlayAddress.getId(), overlayAddress.getAddress());
    }

    private void publishUnresponsiveNode(VodAddress nodeAddress)
    {
        trigger(new FailureDetectorPort.FailureDetectorEvent(nodeAddress), fdPort);
    }

    final Handler<FailureDetectorPort.FailureDetectorEvent> handleFailureDetector = new Handler<FailureDetectorPort.FailureDetectorEvent>() {

        @Override
        public void handle(FailureDetectorPort.FailureDetectorEvent event) {
            removeNodesFromLocalState(event.getSuspectedNodes());
        }
    };


    /**
     * Initiate a identifier exchange every round.
     */
    final Handler<GradientRound> handleRound = new Handler<GradientRound>() {
        @Override
        public void handle(GradientRound event) {
            if (!gradientView.isEmpty()) {
                initiateShuffle(gradientView.selectPeerToShuffleWith());
            }
        }
    };

    /**
     * Initiate the shuffling process for the given node.
     *
     * @param exchangePartner the address of the node to shuffle with
     */
    private void initiateShuffle(SearchDescriptor exchangePartner) {
        Set<SearchDescriptor> exchangeNodes = gradientView.getExchangeDescriptors(exchangePartner, config.getShuffleLength());

        ScheduleTimeout rst = new ScheduleTimeout(config.getShufflePeriod());
        rst.setTimeoutEvent(new GradientShuffleMessage.RequestTimeout(rst, self.getId()));
        UUID rTimeoutId = (UUID) rst.getTimeoutEvent().getTimeoutId();
        outstandingShuffles.put(rTimeoutId, exchangePartner.getVodAddress());

        GradientShuffleMessage.Request rRequest = new GradientShuffleMessage.Request(self.getAddress(), exchangePartner.getVodAddress(), rTimeoutId, exchangeNodes);
        exchangePartner.setConnected(true);

        trigger(rst, timerPort);
        trigger(rRequest, networkPort);

        gradientView.incrementDescriptorAges();

        shuffleTimes.put(rTimeoutId.getId(), System.currentTimeMillis());
    }
    /**
     * Answer a {@link GradientShuffleMessage.Request} with the nodes from the
     * view preferred by the inquirer.
     */
    final Handler<GradientShuffleMessage.Request> handleShuffleRequest = new Handler<GradientShuffleMessage.Request>() {
        @Override
        public void handle(GradientShuffleMessage.Request event) {
            Set<SearchDescriptor> searchDescriptors = event.getSearchDescriptors();

            SearchDescriptor exchangePartnerDescriptor = null;
            for (SearchDescriptor searchDescriptor : searchDescriptors) {
                if (searchDescriptor.getVodAddress().equals(event.getVodSource())) {
                    exchangePartnerDescriptor = searchDescriptor;
                    break;
                }
            }

            // Requester didn't follow the protocol
            if (exchangePartnerDescriptor == null) {
                return;
            }

            Set<SearchDescriptor> exchangeNodes = gradientView.getExchangeDescriptors(exchangePartnerDescriptor, config.getShuffleLength());
            GradientShuffleMessage.Response rResponse = new GradientShuffleMessage.Response(self.getAddress(), event.getVodSource(), event.getTimeoutId(), exchangeNodes);
            trigger(rResponse, networkPort);

            gradientView.merge(searchDescriptors);
            gradientView.incrementDescriptorAges();

            sendGradientViewChange();

            // Publish The Gradient Sample.
            publishSample();
        }
    };
    /**
     * Merge the entries from the response to the view.
     */
    final Handler<GradientShuffleMessage.Response> handleShuffleResponse = new Handler<GradientShuffleMessage.Response>() {
        @Override
        public void handle(GradientShuffleMessage.Response event) {
            UUID shuffleId = (UUID) event.getTimeoutId();

            if (outstandingShuffles.containsKey(shuffleId)) {
                outstandingShuffles.remove(shuffleId);
                CancelTimeout ct = new CancelTimeout(shuffleId);
                trigger(ct, timerPort);
            }

            Collection<SearchDescriptor> sample = event.getSearchDescriptors();

            boolean isNeverBefore = self.getPartitioningType() == VodAddress.PartitioningType.NEVER_BEFORE;

            Set<SearchDescriptor> updatedSample = new HashSet<SearchDescriptor>();
            if (!isNeverBefore) {

                int bitsToCheck = self.getPartitionIdDepth();
                boolean isOnceBefore = self.getPartitioningType() == VodAddress.PartitioningType.ONCE_BEFORE;
                for (SearchDescriptor d : sample) {
                    PartitionId partitionId = PartitionHelper.determineSearchDescriptorPartition(d,
                            isOnceBefore, bitsToCheck);
                    VodAddress a = PartitionHelper.updatePartitionId(d, partitionId);
                    updatedSample.add(new SearchDescriptor(a, d));
                }
            } else {
                for (SearchDescriptor d : sample) {
                    VodAddress a = PartitionHelper.updatePartitionId(d,
                            new PartitionId(VodAddress.PartitioningType.NEVER_BEFORE, 0, 0));
                    updatedSample.add(new SearchDescriptor(a, d));
                }
            }

            // Remove all samples from other partitions
//            Iterator<SearchDescriptor> iterator = descriptors.iterator();
            Iterator<SearchDescriptor> iterator = updatedSample.iterator();
            Set<SearchDescriptor> toRemove = new HashSet<SearchDescriptor>();
            while (iterator.hasNext()) {
                SearchDescriptor d = iterator.next();
                OverlayAddress next = d.getOverlayAddress();
                if (next.getPartitionId() != self.getPartitionId()
                        || next.getPartitionIdDepth() != self.getPartitionIdDepth()
                        || next.getPartitioningType() != self.getPartitioningType()) {
//                    iterator.remove();
                    toRemove.add(d);
                }
            }
            updatedSample.removeAll(toRemove);

//            gradientView.merge(sample);
            gradientView.merge(updatedSample);
            sendGradientViewChange();

            long timeStarted = shuffleTimes.remove(event.getTimeoutId().getId());
            long rtt = System.currentTimeMillis() - timeStarted;
            RTTStore.addSample(self.getId(), event.getVodSource(), rtt);
            updateLatestRtts(rtt);
        }
    };

    /**
     * Broadcast the current view to the listening components.
     */
    void sendGradientViewChange() {
        if (gradientView.isChanged()) {
            // Create a copy so components don't affect each other
            SortedSet<SearchDescriptor> view = new TreeSet<SearchDescriptor>(gradientView.getAll());

            Iterator<SearchDescriptor> iterator = view.iterator();
            while (iterator.hasNext()) {
                OverlayAddress next = iterator.next().getOverlayAddress();
                if (next.getPartitionId() != self.getPartitionId()
                        || next.getPartitionIdDepth() != self.getPartitionIdDepth()
                        || next.getPartitioningType() != self.getPartitioningType()) {
                    iterator.remove();
                }
            }


            trigger(new GradientViewChangePort.GradientViewChanged(gradientView.isConverged(), view), gradientViewChangePort);
        }
    }
    /**
     * Remove a node from the view if it didn't respond to a request.
     */
    final Handler<GradientShuffleMessage.RequestTimeout> handleShuffleRequestTimeout = new Handler<GradientShuffleMessage.RequestTimeout>() {
        @Override
        public void handle(GradientShuffleMessage.RequestTimeout event) {
            UUID rTimeoutId = (UUID) event.getTimeoutId();
            VodAddress deadNode = outstandingShuffles.remove(rTimeoutId);

            if (deadNode == null) {
                logger.warn("{} bogus timeout with id: {}", self.getAddress(), event.getTimeoutId());
                return;
            }

            publishUnresponsiveNode(deadNode);
            shuffleTimes.remove(event.getTimeoutId().getId());
            RTTStore.removeSamples(deadNode.getId(), deadNode);
        }
    };
    /**
     * Initiate a exchange with a random node of each Croupier sample to speed
     * up convergence and prevent partitioning.
     */
    final Handler<CroupierSample> handleCroupierSample = new Handler<CroupierSample>() {
        @Override
        public void handle(CroupierSample event) {
            //TODO Alex/Croupier - extract SearchDescriptor - which should pe a PeerView - you probably want both public and private
            List<SearchDescriptor> sample = new ArrayList<SearchDescriptor>();
            List<SearchDescriptor> updatedSample = new ArrayList<SearchDescriptor>();

            checkInstanceAndAdd(SearchDescriptor.class, sample, event.publicSample);
            checkInstanceAndAdd(SearchDescriptor.class, sample, event.privateSample);
            
            if ((self.getPartitioningType() != VodAddress.PartitioningType.NEVER_BEFORE)) {
                boolean isOnePartition = self.getPartitioningType() == VodAddress.PartitioningType.ONCE_BEFORE;
                if (!isOnePartition) {
                    int bitsToCheck = self.getPartitionIdDepth();

                    for (SearchDescriptor d : sample) {
                        PartitionId partitionId = PartitionHelper.determineSearchDescriptorPartition(d,
                                isOnePartition, bitsToCheck);

                        VodAddress a = PartitionHelper.updatePartitionId(d, partitionId);
                        updatedSample.add(new SearchDescriptor(a, d));

                    }
                } else {
                    for (SearchDescriptor d : sample) {
                        PartitionId partitionId = PartitionHelper.determineSearchDescriptorPartition(d,
                                isOnePartition, 1);

                        VodAddress a = PartitionHelper.updatePartitionId(d, partitionId);
                        updatedSample.add(new SearchDescriptor(a, d));

                    }
                }
            }
            else {
                for (SearchDescriptor d : sample) {
                    VodAddress a = PartitionHelper.updatePartitionId(d,
                            new PartitionId(VodAddress.PartitioningType.NEVER_BEFORE, 0, 0));
                    updatedSample.add(new SearchDescriptor(a, d));
                }
            }

            incrementRoutingTableAge();
            // FIXME: Switch on the adding of entries in the table, once the croupier is fixed ?
//            addRoutingTableEntries(sample);
            if(self.getPartitioningType() != VodAddress.PartitioningType.NEVER_BEFORE)
                addRoutingTableEntries(updatedSample);
            else {
                updatedSample = sample;
                addRoutingTableEntries(updatedSample);
            }

            // Remove all samples from other partitions
//            Iterator<SearchDescriptor> iterator = sample.iterator();
            Iterator<SearchDescriptor> iterator = updatedSample.iterator();
            while (iterator.hasNext()) {
                OverlayAddress next = iterator.next().getOverlayAddress();
                if (next.getCategoryId() != self.getCategoryId()
                        || next.getPartitionId() != self.getPartitionId()
                        || next.getPartitionIdDepth() != self.getPartitionIdDepth()
                        || next.getPartitioningType() != self.getPartitioningType()) {
                    iterator.remove();
                }
            }

            //Merge croupier sample to have quicker convergence of gradient
            gradientView.merge(updatedSample);

            // Shuffle with one sample from our partition
            if (updatedSample.size() > 0) {
                int n = random.nextInt(updatedSample.size());
                initiateShuffle(updatedSample.get(n));
            }
        }
    };
    
    
    private <T extends PeerView> void checkInstanceAndAdd(Class<T> classType, List<T> baseList, Collection<CroupierPeerView> sampleList){
        
        for(CroupierPeerView croupierPeerView : sampleList){
            
            if((classType).isInstance(croupierPeerView.pv)){
                baseList.add((T)croupierPeerView.pv);
            }
        }
    }
    

    private void incrementRoutingTableAge() {
        for (Map<Integer, HashSet<SearchDescriptor>> categoryRoutingMap : routingTable.values()) {
            for (HashSet<SearchDescriptor> bucket : categoryRoutingMap.values()) {
                for (SearchDescriptor descriptor : bucket) {
                    descriptor.incrementAndGetAge();
                }
            }
        }
    }

    private void addRoutingTableEntries(List<SearchDescriptor> nodes) {
        for (SearchDescriptor searchDescriptor : nodes) {
            MsConfig.Categories category = categoryFromCategoryId(searchDescriptor.getOverlayId().getCategoryId());
            int partition = searchDescriptor.getOverlayAddress().getPartitionId();

            Map<Integer, HashSet<SearchDescriptor>> categoryRoutingMap = routingTable.get(category);
            if (categoryRoutingMap == null) {
                categoryRoutingMap = new HashMap<Integer, HashSet<SearchDescriptor>>();
                routingTable.put(category, categoryRoutingMap);
            }

            HashSet<SearchDescriptor> bucket = categoryRoutingMap.get(partition);
            if (bucket == null) {
                bucket = new HashSet<SearchDescriptor>();
                categoryRoutingMap.put(partition, bucket);

                //update old routing tables if see an entry from a new partition
                PartitionId newPartitionId = new PartitionId(searchDescriptor.getOverlayAddress().getPartitioningType(),
                        searchDescriptor.getOverlayAddress().getPartitionIdDepth(), searchDescriptor.getOverlayAddress().getPartitionId());
                updateBucketsInRoutingTable(newPartitionId, categoryRoutingMap, bucket);
            }

            bucket.add(searchDescriptor);
            // keep the best descriptors in this partition
            TreeSet<SearchDescriptor> sortedBucket = sortByConnectivity(bucket);
            while (bucket.size() > config.getMaxNumRoutingEntries()) {
                bucket.remove(sortedBucket.pollLast());
            }
        }
    }
    /**
     * This handler listens to updates regarding the leader status
     */
    final Handler<LeaderStatus> handleLeaderStatus = new Handler<LeaderStatus>() {
        @Override
        public void handle(LeaderStatus event) {
            leader = event.isLeader();
        }
    };
    /**
     * Updates gradient's view by removing crashed nodes from it, eg. old
     * leaders
     */
    final Handler<NodeCrashEvent> handleNodeCrash = new Handler<NodeCrashEvent>() {
        @Override
        public void handle(NodeCrashEvent event) {
            VodAddress deadNode = event.getDeadNode();

            publishUnresponsiveNode(deadNode);
        }
    };
    private IndexEntry indexEntryToAdd;
    private TimeoutId addIndexEntryRequestTimeoutId;
    final private HashSet<SearchDescriptor> queriedNodes = new HashSet<SearchDescriptor>();
    final private HashMap<TimeoutId, SearchDescriptor> openRequests = new HashMap<TimeoutId, SearchDescriptor>();
    final private HashMap<VodAddress, Integer> locatedLeaders = new HashMap<VodAddress, Integer>();
    private List<VodAddress> leadersAlreadyComunicated = new ArrayList<VodAddress>();

    // TODO: {Abhi} Move it to separate component.
    final Handler<GradientRoutingPort.AddIndexEntryRequest> handleAddIndexEntryRequest = new Handler<GradientRoutingPort.AddIndexEntryRequest>() {
        @Override
        public void handle(GradientRoutingPort.AddIndexEntryRequest event) {
            MsConfig.Categories selfCategory = categoryFromCategoryId(self.getCategoryId());
            MsConfig.Categories addCategory = event.getEntry().getCategory();

            indexEntryToAdd = event.getEntry();
            addIndexEntryRequestTimeoutId = event.getTimeoutId();
            locatedLeaders.clear();
            queriedNodes.clear();
            openRequests.clear();
            leadersAlreadyComunicated.clear();

            //Entry and my overlay in the same category, add to my overlay
            if (addCategory == selfCategory) {
                if (leader) {
                    trigger(new AddIndexEntryMessage.Request(self.getAddress(), self.getAddress(), event.getTimeoutId(), event.getEntry()), networkPort);
                }

                //if we have direct pointer to the leader
                else if(leaderAddress != null) {
                    //sendLeaderLookupRequest(new SearchDescriptor(leaderAddress));
                    trigger(new AddIndexEntryMessage.Request(self.getAddress(), leaderAddress, event.getTimeoutId(), event.getEntry()), networkPort);
                }

                else
                {
                    NavigableSet<SearchDescriptor> startNodes = new TreeSet<SearchDescriptor>(utilityComparator);
                    startNodes.addAll(gradientView.getAll());

                    //Also add nodes from croupier sample to have more chances of getting higher utility nodes, this works
                    //as a finger table to random nodes
                    Map<Integer, HashSet<SearchDescriptor>> croupierPartitions = routingTable.get(selfCategory);
                    if (croupierPartitions != null && !croupierPartitions.isEmpty()) {
                        HashSet<SearchDescriptor> croupierNodes =  croupierPartitions.get(self.getPartitionId());
                        if(croupierNodes != null && !croupierNodes.isEmpty()) {
                            startNodes.addAll(croupierNodes);
                        }
                    }

                    // Higher utility nodes are further away in the sorted set
                    Iterator<SearchDescriptor> iterator = startNodes.descendingIterator();

                    for (int i = 0; i < LeaderLookupMessage.QueryLimit && iterator.hasNext(); i++) {
                        SearchDescriptor node = iterator.next();
                        sendLeaderLookupRequest(node);
                    }
                }
            }
            else {
                Map<Integer, HashSet<SearchDescriptor>> partitions = routingTable.get(addCategory);
                if (partitions == null || partitions.isEmpty()) {
                    logger.info("{} handleAddIndexEntryRequest: no partition for category {} ", self.getAddress(), addCategory);
                    return;
                }

                ArrayList<Integer> categoryPartitionsIds = new ArrayList<Integer>(partitions.keySet());
                int categoryPartitionId = (int) (Math.random() * categoryPartitionsIds.size());

                HashSet<SearchDescriptor> startNodes = partitions.get(categoryPartitionsIds.get(categoryPartitionId));
                if (startNodes == null) {
                    logger.info("{} handleAddIndexEntryRequest: no nodes for partition {} ", self.getAddress(),
                            categoryPartitionsIds.get(categoryPartitionId));
                    return;
                }

                // Need to sort it every time because values like RTT might have been changed
                SortedSet<SearchDescriptor> sortedStartNodes = sortByConnectivity(startNodes);
                Iterator iterator = sortedStartNodes.iterator();

                for (int i = 0; i < LeaderLookupMessage.QueryLimit && iterator.hasNext(); i++) {
                    SearchDescriptor node = (SearchDescriptor) iterator.next();
                    sendLeaderLookupRequest(node);
                }
            }
        }
    };
    
    // TODO: {Abhi} Move it to separate component.
    final Handler<LeaderLookupMessage.RequestTimeout> handleLeaderLookupRequestTimeout = new Handler<LeaderLookupMessage.RequestTimeout>() {
        @Override
        public void handle(LeaderLookupMessage.RequestTimeout event) {
            SearchDescriptor unresponsiveNode = openRequests.remove(event.getTimeoutId());
            shuffleTimes.remove(event.getTimeoutId().getId());

            if (unresponsiveNode == null) {
                logger.warn("{} bogus timeout with id: {}", self.getAddress(), event.getTimeoutId());
                return;
            }

            publishUnresponsiveNode(unresponsiveNode.getVodAddress());
            logger.info("{}: {} did not response to LeaderLookupRequest", self.getAddress(), unresponsiveNode);
        }
    };

    // TODO: {Abhi} Move it to separate component.
    final Handler<LeaderLookupMessage.Request> handleLeaderLookupRequest = new Handler<LeaderLookupMessage.Request>() {
        @Override
        public void handle(LeaderLookupMessage.Request event) {
            TreeSet<SearchDescriptor> higherNodes = new TreeSet<SearchDescriptor>(gradientView.getHigherUtilityNodes());
            ArrayList<SearchDescriptor> searchDescriptors = new ArrayList<SearchDescriptor>();

            // Higher utility nodes are further away in the sorted set
            Iterator<SearchDescriptor> iterator = higherNodes.descendingIterator();
            while (searchDescriptors.size() < LeaderLookupMessage.ResponseLimit && iterator.hasNext()) {
                searchDescriptors.add(iterator.next());
            }

            // Some space left, also return lower nodes
            if (searchDescriptors.size() < LeaderLookupMessage.ResponseLimit) {
                TreeSet<SearchDescriptor> lowerNodes = new TreeSet<SearchDescriptor>(gradientView.getLowerUtilityNodes());
                iterator = lowerNodes.iterator();
                while (searchDescriptors.size() < LeaderLookupMessage.ResponseLimit && iterator.hasNext()) {
                    searchDescriptors.add(iterator.next());
                }
            }

            trigger(new LeaderLookupMessage.Response(self.getAddress(), event.getVodSource(), event.getTimeoutId(), leader, searchDescriptors), networkPort);
        }
    };

    // TODO: {Abhi} Move it to separate component.
    final Handler<LeaderLookupMessage.Response> handleLeaderLookupResponse = new Handler<LeaderLookupMessage.Response>() {
        @Override
        public void handle(LeaderLookupMessage.Response event) {
            if (!openRequests.containsKey(event.getTimeoutId())) {
                return;
            }

            long timeStarted = shuffleTimes.remove(event.getTimeoutId().getId());
            long rtt = System.currentTimeMillis() - timeStarted;
            RTTStore.addSample(self.getId(), event.getVodSource(), rtt);
            updateLatestRtts(rtt);

            CancelTimeout cancelTimeout = new CancelTimeout(event.getTimeoutId());
            trigger(cancelTimeout, timerPort);
            openRequests.remove(event.getTimeoutId());

            if (event.isLeader()) {
                VodAddress source = event.getVodSource();
                Integer numberOfAnswers;
                if (locatedLeaders.containsKey(source)) {
                    numberOfAnswers = locatedLeaders.get(event.getVodSource()) + 1;
                } else {
                    numberOfAnswers = 1;
                }
                locatedLeaders.put(event.getVodSource(), numberOfAnswers);
            } else {
                List<SearchDescriptor> higherUtilityNodes = event.getSearchDescriptors();

                if (higherUtilityNodes.size() > LeaderLookupMessage.QueryLimit) {
                    Collections.sort(higherUtilityNodes, utilityComparator);
                    // Higher utility nodes are further away
                    Collections.reverse(higherUtilityNodes);
                }

                // If the lowest returned nodes is an announced leader, increment it's counter
                if (higherUtilityNodes.size() > 0) {
                    SearchDescriptor first = higherUtilityNodes.get(0);
                    if (locatedLeaders.containsKey(first.getVodAddress())) {
                        Integer numberOfAnswers = locatedLeaders.get(first.getVodAddress()) + 1;
                        locatedLeaders.put(first.getVodAddress(), numberOfAnswers);
                    }
                }

                Iterator<SearchDescriptor> iterator = higherUtilityNodes.iterator();
                for (int i = 0; i < LeaderLookupMessage.QueryLimit && iterator.hasNext(); i++) {
                    SearchDescriptor node = iterator.next();
                    // Don't query nodes twice
                    if (queriedNodes.contains(node)) {
                        i--;
                        continue;
                    }
                    sendLeaderLookupRequest(node);
                }
            }

            // Check it a quorum was reached
            for (VodAddress locatedLeader : locatedLeaders.keySet()) {
                if (locatedLeaders.get(locatedLeader) > LeaderLookupMessage.QueryLimit / 2) {

                    if(!leadersAlreadyComunicated.contains(locatedLeader)){
                        trigger(new AddIndexEntryMessage.Request(self.getAddress(), locatedLeader, addIndexEntryRequestTimeoutId, indexEntryToAdd), networkPort);
                        leadersAlreadyComunicated.add(locatedLeader);
                    }
                }
            }
        }
    };

    private void sendLeaderLookupRequest(SearchDescriptor node) {
        ScheduleTimeout scheduleTimeout = new ScheduleTimeout(config.getLeaderLookupTimeout());
        scheduleTimeout.setTimeoutEvent(new LeaderLookupMessage.RequestTimeout(scheduleTimeout, self.getId()));
        scheduleTimeout.getTimeoutEvent().setTimeoutId(UUID.nextUUID());
        openRequests.put(scheduleTimeout.getTimeoutEvent().getTimeoutId(), node);
        trigger(scheduleTimeout, timerPort);

        queriedNodes.add(node);
        trigger(new LeaderLookupMessage.Request(self.getAddress(), node.getVodAddress(), scheduleTimeout.getTimeoutEvent().getTimeoutId()), networkPort);

        node.setConnected(true);
        shuffleTimes.put(scheduleTimeout.getTimeoutEvent().getTimeoutId().getId(), System.currentTimeMillis());
    }

    // TODO: {Abhi} Move it to separate component.
    final Handler<GradientRoutingPort.ReplicationPrepareCommitRequest> handleReplicationPrepareCommit = new Handler<GradientRoutingPort.ReplicationPrepareCommitRequest>() {
        @Override
        public void handle(GradientRoutingPort.ReplicationPrepareCommitRequest event) {
            for (SearchDescriptor peer : gradientView.getLowerUtilityNodes()) {
                trigger(new ReplicationPrepareCommitMessage.Request(self.getAddress(), peer.getVodAddress(), event.getTimeoutId(), event.getEntry()), networkPort);
            }
        }
    };

    // TODO: {Abhi} Move it to separate component.
    final Handler<GradientRoutingPort.ReplicationCommit> handleReplicationCommit = new Handler<GradientRoutingPort.ReplicationCommit>() {
        @Override
        public void handle(GradientRoutingPort.ReplicationCommit event) {
            for (SearchDescriptor peer : gradientView.getLowerUtilityNodes()) {
                trigger(new ReplicationCommitMessage.Request(self.getAddress(), peer.getVodAddress(), event.getTimeoutId(), event.getIndexEntryId(), event.getSignature()), networkPort);
            }
        }
    };

    // TODO: {Abhi} Move it to separate component.
    final Handler<GradientRoutingPort.IndexHashExchangeRequest> handleIndexHashExchangeRequest = new Handler<GradientRoutingPort.IndexHashExchangeRequest>() {
        @Override
        public void handle(GradientRoutingPort.IndexHashExchangeRequest event) {
            ArrayList<SearchDescriptor> nodes = new ArrayList<SearchDescriptor>(gradientView.getHigherUtilityNodes());
            if (nodes.isEmpty() || nodes.size() < event.getNumberOfRequests()) {
                // TODO: Revert Back debug check.
                logger.debug(" {}: Not enough nodes to perform Index Hash Exchange." + self.getAddress().getId());
                return;
            }

            HashSet<VodAddress> nodesSelectedForExchange = new HashSet<VodAddress>();

            for (int i = 0; i < event.getNumberOfRequests(); i++) {
                int n = random.nextInt(nodes.size());
                SearchDescriptor node = nodes.get(n);
                nodes.remove(node);

                nodesSelectedForExchange.add(node.getVodAddress());

                trigger(new IndexHashExchangeMessage.Request(self.getAddress(), node.getVodAddress(), event.getTimeoutId(),
                        event.getLowestMissingIndexEntry(), event.getExistingEntries()), networkPort);
            }

            trigger(new GradientRoutingPort.IndexHashExchangeResponse(nodesSelectedForExchange), gradientRoutingPort);
        }
    };

    // TODO: {Abhi} Move it to separate component.
    final Handler<GradientRoutingPort.SearchRequest> handleSearchRequest = new Handler<GradientRoutingPort.SearchRequest>() {
        @Override
        public void handle(GradientRoutingPort.SearchRequest event) {
            MsConfig.Categories category = event.getPattern().getCategory();
            Map<Integer, HashSet<SearchDescriptor>> categoryRoutingMap = routingTable.get(category);

            if (categoryRoutingMap == null) {
                return;
            }
            trigger(new NumberOfPartitions(event.getTimeoutId(), categoryRoutingMap.keySet().size()), gradientRoutingPort);

            for (Integer partition : categoryRoutingMap.keySet()) {
                // if your partition, hit only self
                if (partition == self.getPartitionId()
                        && category == categoryFromCategoryId(self.getCategoryId())) {
                    trigger(new SearchMessage.Request(self.getAddress(), self.getAddress(),
                            event.getTimeoutId(), event.getTimeoutId(), event.getPattern(),
                            partition), networkPort);

                    continue;
                }

                TreeSet<SearchDescriptor> bucket = sortByConnectivity(categoryRoutingMap.get(partition));
                TreeSet<SearchDescriptor> unconnectedNodes = null;
                Iterator<SearchDescriptor> iterator = bucket.iterator();
                for (int i = 0; i < config.getSearchParallelism() && iterator.hasNext(); i++) {
                    SearchDescriptor searchDescriptor = iterator.next();

                    RTTStore.RTT rtt = RTTStore.getRtt(searchDescriptor.getId(), searchDescriptor.getVodAddress());
                    double latestRttsAvg = getLatestRttsAvg();
                    if (rtt != null && latestRttsAvg != 0 && rtt.getRttStats().getAvgRTT() > (config.getRttAnomalyTolerance() * latestRttsAvg)) {
                        if (unconnectedNodes == null) {
                            unconnectedNodes = getUnconnectedNodes(bucket);
                        }

                        if (!unconnectedNodes.isEmpty()) {
                            searchDescriptor = unconnectedNodes.pollFirst();
                        }
                    }

                    ScheduleTimeout scheduleTimeout = new ScheduleTimeout(event.getQueryTimeout());
                    scheduleTimeout.setTimeoutEvent(new SearchMessage.RequestTimeout(scheduleTimeout, self.getId(), searchDescriptor));
                    trigger(scheduleTimeout, timerPort);
                    trigger(new SearchMessage.Request(self.getAddress(), searchDescriptor.getVodAddress(),
                            scheduleTimeout.getTimeoutEvent().getTimeoutId(), event.getTimeoutId(), event.getPattern(),
                            partition), networkPort);

                    shuffleTimes.put(scheduleTimeout.getTimeoutEvent().getTimeoutId().getId(), System.currentTimeMillis());
                    searchDescriptor.setConnected(true);
                }
            }
        }
    };

    // TODO: {Abhi} Move it to separate component.
    final Handler<SearchMessage.Response> handleSearchResponse = new Handler<SearchMessage.Response>() {
        @Override
        public void handle(SearchMessage.Response event) {

            // Search response is a UDT Message, so fix the ports before processing.
            TransportHelper.checkTransportAndUpdateBeforeReceiving(event);

            CancelTimeout cancelTimeout = new CancelTimeout(event.getTimeoutId());
            trigger(cancelTimeout, timerPort);

            Long timeStarted = shuffleTimes.remove(event.getTimeoutId().getId());
            if (timeStarted == null) {
                return;
            }
            long rtt = System.currentTimeMillis() - timeStarted;
            RTTStore.addSample(self.getId(), event.getVodSource(), rtt);
            updateLatestRtts(rtt);
        }
    };

    // TODO: {Abhi} Move it to separate component.
    final Handler<SearchMessage.RequestTimeout> handleSearchRequestTimeout = new Handler<SearchMessage.RequestTimeout>() {
        @Override
        public void handle(SearchMessage.RequestTimeout event) {
            SearchDescriptor unresponsiveNode = event.getSearchDescriptor();

            shuffleTimes.remove(event.getTimeoutId().getId());
            publishUnresponsiveNode(unresponsiveNode.getVodAddress());
        }
    };

    /**
     * Handles broadcast public key request from Search component
     */
    final Handler<PublicKeyBroadcast> handlePublicKeyBroadcast = new Handler<PublicKeyBroadcast>() {
        @Override
        public void handle(PublicKeyBroadcast publicKeyBroadcast) {

            leaderPublicKey = publicKeyBroadcast.getPublicKey();
            //leaderAddress is used by a non leader node to directly send AddIndex request to leader. Since the
            //current node is now a leader, this information is not invalid.
            leaderAddress = null;
        }
    };
    /**
     * Responses with peer's view size
     */
    // TODO: {Abhi} Move it to separate component.
    final Handler<ViewSizeMessage.Request> handleViewSizeRequest = new Handler<ViewSizeMessage.Request>() {
        @Override
        public void handle(ViewSizeMessage.Request request) {
            trigger(new ViewSizeMessage.Response(request.getTimeoutId(), request.getNewEntry(), gradientView.getSize(), request.getSource()), gradientRoutingPort);
        }
    };

    // TODO: {Abhi} Move it to separate component.
    Handler<LeaderGroupInformation.Request> handleLeaderGroupInformationRequest = new Handler<LeaderGroupInformation.Request>() {
        @Override
        public void handle(LeaderGroupInformation.Request event) {

            logger.debug(" Partitioning Protocol Initiated at Leader." + self.getAddress().getId());
            int leaderGroupSize = event.getLeaderGroupSize();
            NavigableSet<SearchDescriptor> lowerUtilityNodes = ((NavigableSet)gradientView.getLowerUtilityNodes()).descendingSet();
            List<VodAddress> leaderGroupAddresses = new ArrayList<VodAddress>();

            // If gradient not full or not enough nodes in leader group.
            if((gradientView.getAll().size() < config.getViewSize())|| (lowerUtilityNodes.size() < leaderGroupSize)){
                trigger(new LeaderGroupInformation.Response(event.getMedianId(),event.getPartitioningType(), leaderGroupAddresses), gradientRoutingPort);
                return;
            }

            int i=0;
            for(SearchDescriptor desc : lowerUtilityNodes){

                if(i == leaderGroupSize)
                    break;
                leaderGroupAddresses.add(desc.getVodAddress());
                i++;
            }
            trigger(new LeaderGroupInformation.Response(event.getMedianId(), event.getPartitioningType(), leaderGroupAddresses), gradientRoutingPort);
        }
    };


    private MsConfig.Categories categoryFromCategoryId(int categoryId) {
        return MsConfig.Categories.values()[categoryId];
    }

    private TreeSet<SearchDescriptor> sortByConnectivity(Collection<SearchDescriptor> searchDescriptors) {
        // Need to sort it every time because values like MsSelfImpl.RTT might have been changed
        TreeSet<SearchDescriptor> sortedSearchDescriptors = new TreeSet<SearchDescriptor>(searchDescriptors);
        return sortedSearchDescriptors;
    }

    private TreeSet<SearchDescriptor> getUnconnectedNodes(Collection<SearchDescriptor> searchDescriptors) {
        TreeSet<SearchDescriptor> unconnectedNodes = new TreeSet<SearchDescriptor>(peerConnectivityComparator);
        for (SearchDescriptor searchDescriptor : searchDescriptors) {
            if (searchDescriptor.isConnected() == false) {
                unconnectedNodes.add(searchDescriptor);
            }
        }
        return unconnectedNodes;
    }

    private void updateLatestRtts(long rtt) {
        latestRtts[latestRttRingBufferPointer] = rtt;
        latestRttRingBufferPointer = (latestRttRingBufferPointer + 1) % config.getLatestRttStoreLimit();
    }

    private double getLatestRttsAvg() {
        long sum = 0;
        int numberOfSamples = 0;

        for (int i = 0; i < latestRtts.length; i++) {
            if (latestRtts[i] == 0) {
                break;
            }
            sum += latestRtts[i];
            numberOfSamples++;
        }

        if (numberOfSamples == 0) {
            return 0;
        }

        return sum / (double) numberOfSamples;
    }

    // If you call this method with a list of entries, it will
    // return a single node, weighted towards the 'best' node (as defined by
    // ComparatorById) with the temperature controlling the weighting.
    // QueryLimit temperature of '1.0' will be greedy and always return the best node.
    // QueryLimit temperature of '0.000001' will return a random node.
    // QueryLimit temperature of '0.0' will throw a divide by zero exception :)
    // Reference:
    // http://webdocs.cs.ualberta.ca/~sutton/book/2/node4.html
    private SearchDescriptor getSoftMaxAddress(List<SearchDescriptor> entries) {
        Collections.sort(entries, utilityComparator);

        double rnd = random.nextDouble();
        double total = 0.0d;
        double[] values = new double[entries.size()];
        int j = entries.size() + 1;
        for (int i = 0; i < entries.size(); i++) {
            // get inverse of values - lowest have highest value.
            double val = j;
            j--;
            values[i] = Math.exp(val / config.getTemperature());
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


    // Control Message Exchange Code.


    /**
     * Received the command to initiate the pull based control message exchange mechanism.
     */
    // TODO: {Abhi} Move it to separate component.
    Handler<GradientRoutingPort.InitiateControlMessageExchangeRound> handlerControlMessageExchangeInitiation = new Handler<GradientRoutingPort.InitiateControlMessageExchangeRound>() {
        @Override
        public void handle(GradientRoutingPort.InitiateControlMessageExchangeRound event) {

            ArrayList<SearchDescriptor> preferredNodes = new ArrayList<SearchDescriptor>(gradientView.getHigherUtilityNodes());

            // In case the higher utility nodes are less than the required ones, introduce the lower utility nodes also.
            if(preferredNodes.size() < event.getControlMessageExchangeNumber())
                preferredNodes.addAll(gradientView.getLowerUtilityNodes());

            // NOTE: Now if the node size is less than required, then return.
            if(preferredNodes.size() < event.getControlMessageExchangeNumber())
                return;

            //List<Integer> randomIntegerList = getUniqueRandomIntegerList(preferredNodes.size(), event.getControlMessageExchangeNumber());
            for(int i = 0; i < event.getControlMessageExchangeNumber(); i++){
                VodAddress destination = preferredNodes.get(i).getVodAddress();
                trigger(new ControlMessage.Request(self.getAddress(), destination, new OverlayId(self.getOverlayId()), event.getRoundId()), networkPort);
            }
        }
    };


    /**
     * Based on the parameters passed, it returns a random set of elements.
     * @param sizeOfAvailableObjectSet
     * @param randomSetSize
     * @return
     */
    public List<Integer> getUniqueRandomIntegerList(int sizeOfAvailableObjectSet, int randomSetSize){

        //Create an instance of random integer list.
        List<Integer> uniqueRandomIntegerList = new ArrayList<Integer>();

        // In case any size is <=0 just return empty list.
        if(sizeOfAvailableObjectSet <=0 || randomSetSize <=0){
            return uniqueRandomIntegerList;
        }

        // Can't return random element positions in case the size is lower than required.
        if(sizeOfAvailableObjectSet < randomSetSize){
            for(int i =0 ; i < sizeOfAvailableObjectSet ; i ++){
                uniqueRandomIntegerList.add(i);
            }
        }
        else{

            while(uniqueRandomIntegerList.size() < randomSetSize){

                int n = random.nextInt(sizeOfAvailableObjectSet);
                if(!uniqueRandomIntegerList.contains(n))
                    uniqueRandomIntegerList.add(n);
            }
        }

        return uniqueRandomIntegerList;
    }

    // TODO: {Abhi} Move it to separate component.
    Handler<LeaderInfoUpdate> handleLeaderUpdate = new Handler<LeaderInfoUpdate>() {
        @Override
        public void handle(LeaderInfoUpdate leaderInfoUpdate) {

            leaderAddress = leaderInfoUpdate.getLeaderAddress();
            leaderPublicKey = leaderInfoUpdate.getLeaderPublicKey();
        }
    };
    
    // TODO: {Abhi} Move it to separate component.
    Handler<ControlMessageInternal.Request> handlerControlMessageInternalRequest = new Handler<ControlMessageInternal.Request>(){
        @Override
        public void handle(ControlMessageInternal.Request event) {

            if(event instanceof  CheckLeaderInfoUpdate.Request)
                handleCheckLeaderInfoInternalControlMessage((CheckLeaderInfoUpdate.Request) event);
        }
    };

    private void handleCheckLeaderInfoInternalControlMessage(CheckLeaderInfoUpdate.Request event) {

        logger.debug("Check Leader Update Received.");

        trigger(new CheckLeaderInfoUpdate.Response(event.getRoundId(), event.getSourceAddress(),
                leader ? self.getAddress() : leaderAddress, leaderPublicKey), gradientRoutingPort);
    }

    Handler<SelfChangedPort.SelfChangedEvent> handlerSelfChanged = new Handler<SelfChangedPort.SelfChangedEvent>(){
        @Override
        public void handle(SelfChangedPort.SelfChangedEvent event) {

            MsSelfImpl oldSelf = self;
            self = event.getSelf().clone();
            gradientView.setSelf(self);

            if(oldSelf.getPartitionIdDepth() < self.getPartitionIdDepth()) {
                gradientView.adjustViewToNewPartitions();
            }
        }
    };

    private void publishSample() {

        Set<SearchDescriptor> nodes = gradientView.getAll();
        StringBuilder sb = new StringBuilder("Neighbours: { ");
        for (SearchDescriptor d : nodes) {
            sb.append(d.getVodAddress().getId() + ":" + d.getNumberOfIndexEntries() + ":" + d.getReceivedPartitionDepth() + ":" + d.getAge()).append(", ");

        }
        sb.append("}");
        logger.warn(compName + sb);
    }



}
