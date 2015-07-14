package se.sics.ms.scenarios.special;

import se.sics.ms.simulation.SweepOperations;
import se.sics.p2ptoolbox.simulator.dsl.SimulationScenario;

/**
 * Scenario for generating the flash crowd in the system.
 * Flash Crowd means that once the system has stabalized in terms of nodes have added entries, send in a burst
 * of new nodes joining the system.
 * 
 * The
 * Created by babbarshaer on 2015-05-10.
 */
public class BasicFLashCrowdScenario {


    public static SimulationScenario boot(final long seed, final int throughput, final int numEntries,  final int initialClusterSize, final int flashCrowdSize) {

        SimulationScenario scenario = new SimulationScenario() {

            {

                StochasticProcess changeNetworkModel = new StochasticProcess() {
                    {
                        eventInterArrivalTime(constant(300));
                        raise(1, SweepOperations.uniformNetworkModel);
                    }
                };

                StochasticProcess startAggregatorNode = new StochasticProcess() {
                    {
                        eventInterArrivalTime(constant(1000));
                        raise(1 , SweepOperations.startAggregatorNodeCmd);
                    }
                };

                StochasticProcess partitionEntryAdd = new StochasticProcess() {
                    {
                        eventInterArrivalTime(constant(1000 / throughput));
                        raise( numEntries , SweepOperations.addPartitionIndexEntryCommand, uniform(0,Integer.MAX_VALUE));
                    }
                };



                StochasticProcess initialPeerJoin = new StochasticProcess() {
                    {
                        eventInterArrivalTime(constant(500));
                        raise(initialClusterSize-1 , SweepOperations.startNodeCmdOperation, uniform(0,Integer.MAX_VALUE));
                    }
                };


                StochasticProcess flashCrowdPeerJoin = new StochasticProcess() {
                    {
                        System.out.println(" Initiating the Flash Crowd peer join");
                        eventInterArrivalTime(uniform(400, 2000));
                        raise(flashCrowdSize , SweepOperations.startNodeCmdOperation, uniform(0,Integer.MAX_VALUE));
                    }
                };
                

                StochasticProcess specialPeerJoin = new StochasticProcess() {
                    {
                        eventInterArrivalTime(constant(1000));
                        raise(1 , SweepOperations.startNodeCmdOperation, constant(Integer.MIN_VALUE));
                    }
                };

                StochasticProcess addIndexEntryCommand = new StochasticProcess() {
                    {
                        eventInterArrivalTime(constant(3000 / throughput));
                        raise( numEntries , SweepOperations.addIndexEntryCommand, uniform(0, Integer.MAX_VALUE));
                    }
                };

                StochasticProcess searchIndexEntry = new StochasticProcess() {
                    {
                        eventInterArrivalTime(constant(3000));
                        raise(1, SweepOperations.searchIndexEntry, uniform(0, Integer.MAX_VALUE), constant(3000), constant(3));

                    }

                };


                changeNetworkModel.start();
                startAggregatorNode.startAfterTerminationOf(1000, changeNetworkModel);
                specialPeerJoin.startAfterTerminationOf(10000, startAggregatorNode);
                initialPeerJoin.startAfterTerminationOf(5000, specialPeerJoin);
                addIndexEntryCommand.startAfterTerminationOf(40000, initialPeerJoin);
                flashCrowdPeerJoin.startAfterTerminationOf(150 * 1000, addIndexEntryCommand);
                
                
                
//                peerJoin.start();
//                specialPeerJoin.startAfterTerminationOf(30000, peerJoin);
//                addIndexEntryCommand.startAfterTerminationOf(30000, peerJoin);
//                searchIndexEntry.startAfterTerminationOf(50000, addIndexEntryCommand);
                // === Add a termination event.
            }
        };

        scenario.setSeed(seed);

        return scenario;
    }
    
    
    
}
