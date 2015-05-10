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
public class BasicChurnScenario {


    public static SimulationScenario boot(final long seed, final int throughput, final int initialClusterSize,  final int numEntries, final double churnRate, final int numOfChurnRounds , final int churnEntryJoin) {

        
        double result = (initialClusterSize * churnRate);
        final int entryChangePerSecond = (int) Math.ceil(result);
        
        
        SimulationScenario scenario = new SimulationScenario() {

            {

                StochasticProcess startAggregatorNode = new StochasticProcess() {
                    {
                        eventInterArrivalTime(constant(1000));
                        raise(1 , SweepOperations.startAggregatorNodeCmd);
                    }
                };


                StochasticProcess specialPeerJoin = new StochasticProcess() {
                    {
                        eventInterArrivalTime(constant(1000));
                        raise(7 , SweepOperations.startLeaderGroupNodes, constant(Integer.MIN_VALUE));
                    }
                };
                
                
                StochasticProcess initialPeerJoin = new StochasticProcess() {
                    {
                        eventInterArrivalTime(constant(1000));
                        raise(initialClusterSize , SweepOperations.startNodeCmdOperation, uniform(1 , Integer.MAX_VALUE));
                    }
                };

                StochasticProcess addIndexEntryCommand = new StochasticProcess() {
                    {
                        eventInterArrivalTime(constant(3000 / throughput));
                        raise( numEntries , SweepOperations.addIndexEntryCommand, constant(Integer.MIN_VALUE));
                    }
                };
                
                

                StochasticProcess churnPeerKillProcess = new StochasticProcess() {
                    {
                        System.out.println(" Initiating Killing of Nodes as part of churn ... ");
                        eventInterArrivalTime(constant(1000 / entryChangePerSecond));
                        raise(numOfChurnRounds * entryChangePerSecond , SweepOperations.killNodeCmdOperation, uniform(1 , Integer.MAX_VALUE));
                    }
                };

                StochasticProcess churnPeerJoin = new StochasticProcess() {
                    {
                        System.out.println(" Initiating the peer join as part of churn ... ");
                        eventInterArrivalTime(constant(1000 / entryChangePerSecond));
                        raise(numOfChurnRounds * entryChangePerSecond , SweepOperations.startNodeCmdOperation, uniform(1 , Integer.MAX_VALUE));
                    }
                };
                
                
                
                StochasticProcess churnEntryAddition = new StochasticProcess() {
                    {
                        eventInterArrivalTime(constant(3000 / throughput));
                        raise( churnEntryJoin , SweepOperations.addIndexEntryCommand, constant(Integer.MIN_VALUE));
                    }
                };

                startAggregatorNode.start();
                specialPeerJoin.startAfterTerminationOf(10000, startAggregatorNode);
                initialPeerJoin.startAfterTerminationOf(5000, specialPeerJoin);
                addIndexEntryCommand.startAfterTerminationOf(50000, initialPeerJoin);

                // Churn Scenario Commands.
                churnPeerJoin.startAfterTerminationOf(150000, addIndexEntryCommand);
                churnPeerKillProcess.startAtSameTimeWith(churnPeerJoin);
                churnEntryAddition.startAtSameTimeWith(churnPeerJoin);

            }
        };

        scenario.setSeed(seed);

        return scenario;
    }
    
    
    
}
