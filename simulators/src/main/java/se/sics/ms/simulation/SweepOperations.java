package se.sics.ms.simulation;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import se.sics.kompics.KompicsEvent;
import se.sics.kompics.network.Address;
import se.sics.kompics.network.Msg;
import se.sics.kompics.network.Transport;
import se.sics.ms.events.simEvents.AddIndexEntryP2pSimulated;
import se.sics.ms.events.simEvents.SearchP2pSimulated;
import se.sics.ms.search.SearchPeer;
import se.sics.ms.search.SearchPeerInit;
import se.sics.ms.types.IndexEntry;
import se.sics.ms.types.SearchPattern;
import se.sics.p2ptoolbox.simulator.SimulationContext;
import se.sics.p2ptoolbox.simulator.cmd.NetworkOpCmd;
import se.sics.p2ptoolbox.simulator.cmd.OperationCmd;
import se.sics.p2ptoolbox.simulator.cmd.impl.StartNodeCmd;
import se.sics.p2ptoolbox.simulator.dsl.adaptor.Operation1;
import se.sics.p2ptoolbox.simulator.dsl.adaptor.Operation2;
import se.sics.p2ptoolbox.util.network.impl.BasicContentMsg;
import se.sics.p2ptoolbox.util.network.impl.DecoratedAddress;
import se.sics.p2ptoolbox.util.network.impl.DecoratedHeader;
import java.util.Set;

/**
 * Operations for controlling the sequence of events in sweep.
 * <p/>
 * Created by babbarshaer on 2015-02-04.
 */
public class SweepOperations {

    private static Logger logger = LoggerFactory.getLogger(SweepOperations.class);

    public static Operation1<StartNodeCmd, Long> startNodeCmdOperation = new Operation1<StartNodeCmd, Long>() {
        @Override
        public StartNodeCmd generate(final Long id) {

            return new StartNodeCmd<SearchPeer, DecoratedAddress>() {

                long nodeId = SweepOperationsHelper.getStableId(id);

                @Override
                public Integer getNodeId() {
                    return (int) nodeId;
                }

                @Override
                public int bootstrapSize() {
                    return 2;
                }

                @Override
                public Class getNodeComponentDefinition() {
                    return SearchPeer.class;
                }

                @Override
                public SearchPeerInit getNodeComponentInit(DecoratedAddress address, Set<DecoratedAddress> bootstrapNodes) {
                    return SweepOperationsHelper.generatePeerInit(address, bootstrapNodes, nodeId);
                }

                @Override
                public DecoratedAddress getAddress() {
                    return SweepOperationsHelper.getBasicAddress(nodeId);
                }
            };
        }
    };


    public static Operation2<OperationCmd, Long, Long> generatePartitionNodeMap = new Operation2<OperationCmd, Long, Long>() {
        
        @Override
        public OperationCmd generate(final Long depth, final Long bucketSize) {
            
            SweepOperationsHelper.generateNodesPerPartition( depth, bucketSize );
            
            return new OperationCmd() {
                
                @Override
                public void beforeCmd(SimulationContext context) {
                    
                }

                @Override
                public boolean myResponse(KompicsEvent response) {
                    return false;
                }

                @Override
                public void validate(SimulationContext context, KompicsEvent response) throws ValidationException {

                }

                @Override
                public void afterValidation(SimulationContext context) {

                }
            };
        }
    };
    
    

    public static Operation1<StartNodeCmd, Long> startPartitionNodeCmd = new Operation1<StartNodeCmd, Long>() {
        @Override
        public StartNodeCmd generate(final Long id) {

            return new StartNodeCmd<SearchPeer, DecoratedAddress>() {

                long nodeId = SweepOperationsHelper.getPartitionBucketNode(id);

                @Override
                public Integer getNodeId() {
                    return (int) nodeId;
                }

                @Override
                public int bootstrapSize() {
                    return 2;
                }

                @Override
                public Class getNodeComponentDefinition() {
                    return SearchPeer.class;
                }

                @Override
                public SearchPeerInit getNodeComponentInit(DecoratedAddress address, Set<DecoratedAddress> bootstrapNodes) {
                    return SweepOperationsHelper.generatePeerInit(address, bootstrapNodes, nodeId);
                }

                @Override
                public DecoratedAddress getAddress() {
                    return SweepOperationsHelper.getBasicAddress(nodeId);
                }
            };
        }
    };
    
    
    


    public static Operation1<NetworkOpCmd, Long> addIndexEntryCommand = new Operation1<NetworkOpCmd, Long>() {

        @Override
        public NetworkOpCmd generate(final Long id) {
            return new NetworkOpCmd() {

                DecoratedAddress destination = SweepOperationsHelper.getNodeAddressToCommunicate(id);
                IndexEntry junkEntry = SweepOperationsHelper.generateIndexEntry();

                @Override
                public void beforeCmd(SimulationContext simulationContext) {

                }

                @Override
                public boolean myResponse(KompicsEvent kompicsEvent) {
                    return false;
                }

                @Override
                public void validate(SimulationContext simulationContext, KompicsEvent kompicsEvent) throws ValidationException {

                }

                @Override
                public void afterValidation(SimulationContext simulationContext) {

                }

                @Override
                public Msg getNetworkMsg(Address address) {

                    logger.debug("Add Index Entry id invoked for id -> " + id);

                    AddIndexEntryP2pSimulated request = new AddIndexEntryP2pSimulated(junkEntry);
                    DecoratedHeader<DecoratedAddress> header = new DecoratedHeader<DecoratedAddress>((DecoratedAddress) address, destination, Transport.UDP);
                    BasicContentMsg<DecoratedAddress, DecoratedHeader<DecoratedAddress>, AddIndexEntryP2pSimulated> msg = new BasicContentMsg<DecoratedAddress, DecoratedHeader<DecoratedAddress>, AddIndexEntryP2pSimulated>(header, request);
                    return msg;
                }
            };
        }
    };


    public static Operation1<NetworkOpCmd, Long> searchIndexEntry = new Operation1<NetworkOpCmd, Long>() {

        @Override
        public NetworkOpCmd generate(final Long id) {
            return new NetworkOpCmd() {

                DecoratedAddress destination = SweepOperationsHelper.getNodeAddressToCommunicate(id);
                SearchPattern pattern = SweepOperationsHelper.generateSearchPattern();

                @Override
                public void beforeCmd(SimulationContext simulationContext) {

                }

                @Override
                public boolean myResponse(KompicsEvent kompicsEvent) {
                    return false;
                }

                @Override
                public void validate(SimulationContext simulationContext, KompicsEvent kompicsEvent) throws ValidationException {

                }

                @Override
                public void afterValidation(SimulationContext simulationContext) {

                }

                @Override
                public Msg getNetworkMsg(Address address) {

                    logger.debug("Search Index Entry Command invoked for ->" + destination.getId());

                    SearchP2pSimulated simulated = new SearchP2pSimulated(pattern);
                    DecoratedHeader<DecoratedAddress> header = new DecoratedHeader<DecoratedAddress>((DecoratedAddress) address, destination, Transport.UDP);
                    BasicContentMsg<DecoratedAddress, DecoratedHeader<DecoratedAddress>, SearchP2pSimulated> msg = new BasicContentMsg<DecoratedAddress, DecoratedHeader<DecoratedAddress>, SearchP2pSimulated>(header, simulated);

                    return msg;
                }
            };
        }
    };


}
