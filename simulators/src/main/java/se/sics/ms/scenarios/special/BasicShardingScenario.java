package se.sics.ms.scenarios.special;

import se.sics.ms.simulation.SweepOperations;
import se.sics.p2ptoolbox.simulator.dsl.SimulationScenario;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 
 * Scenario for testing the sharding mechanism in the system. The sharding mechanism helps to 
 * ease of the load on the system in terms of dividing the entries equally among the nodes in the system.
 * 
 *
 * Created by babbarshaer on 2015-05-10.
 */
public class BasicShardingScenario {


    public static SimulationScenario boot(final long seed, final long depth, final long bucketSize, final int throughput, final int numEntries) {


        SimulationScenario scenario = new SimulationScenario() {

            {

                StochasticProcess changeNetworkModel = new StochasticProcess() {
                    {
                        eventInterArrivalTime(constant(300));
                        raise(1, SweepOperations.uniformNetworkModel);
                    }
                };


                StochasticProcess generatePartitionNodeMap = new StochasticProcess() {
                    {
                        eventInterArrivalTime(constant(1000));
                        raise(1, SweepOperations.generatePartitionNodeMap, constant(depth), constant(bucketSize));
                    }
                };


                StochasticProcess partitionPeerJoin = new StochasticProcess() {
                    {
                        eventInterArrivalTime(constant(1000));
                        raise((int) (Math.pow(2, depth) * bucketSize), SweepOperations.startPartitionNodeCmd, uniform(0, Integer.MAX_VALUE));
                    }
                };


                StochasticProcess partitionEntryAdd = new StochasticProcess() {
                    {
                        eventInterArrivalTime(constant(4000 / throughput));
                        raise(numEntries, SweepOperations.addPartitionIndexEntryCommand, uniform(0, Integer.MAX_VALUE));
                    }
                };


                StochasticProcess peerJoin = new StochasticProcess() {
                    {
                        eventInterArrivalTime(constant(1000));
                        raise(1, SweepOperations.startNodeCmdOperation, uniform(0, Integer.MAX_VALUE));
                    }
                };


                StochasticProcess specialPeerJoin = new StochasticProcess() {
                    {
                        eventInterArrivalTime(constant(1000));
                        raise(1, SweepOperations.startNodeCmdOperation, constant(Integer.MIN_VALUE));
                    }
                };

                StochasticProcess addIndexEntryCommand = new StochasticProcess() {
                    {
                        eventInterArrivalTime(constant(3000 / throughput));
                        raise(1, SweepOperations.addIndexEntryCommand, uniform(0, Integer.MAX_VALUE));
                    }
                };

                
                // =====================================
                
                Map<Integer, List<StochasticProcess>> fillLevelCommand = new HashMap<Integer, List<StochasticProcess>>();
                
                final int shardSize = 10;
                
                StochasticProcess fillBucket0 = new StochasticProcess() {{
                        eventInterArrivalTime(constant(4000));
                        raise(shardSize, SweepOperations.addBucketAwareEntry, constant(0));
                }};
                
                List<StochasticProcess> level0List = new ArrayList<StochasticProcess>();
                level0List.add(fillBucket0);
                fillLevelCommand.put(0, level0List);
                
                
                for(int level =1 ; level < depth; level++) {
                    List<StochasticProcess> spList = new ArrayList<StochasticProcess>();
                    fillLevelCommand.put(level, spList);
                    for (int j = 0; j < Math.pow(2, level) ; j++) {
                        final int bucket = j;
                        StochasticProcess bucketFill = new StochasticProcess() {
                            {
                                eventInterArrivalTime(constant(4000));
                                raise(shardSize / 2, SweepOperations.addBucketAwareEntry, constant(bucket));
                            }
                        };
                        spList.add(bucketFill);
                    }
                }

                StochasticProcess searchIndexEntry = new StochasticProcess() {
                    {
                        eventInterArrivalTime(constant(3000));
                        raise(1, SweepOperations.searchIndexEntry, uniform(0, Integer.MAX_VALUE), constant(3000), constant(3));

                    }
                };

                changeNetworkModel.start();
                generatePartitionNodeMap.startAfterTerminationOf(10000, changeNetworkModel);
                partitionPeerJoin.startAfterTerminationOf(10000, generatePartitionNodeMap);
               // partitionEntryAdd.startAfterTerminationOf(40000, partitionPeerJoin);

                StochasticProcess previous = partitionPeerJoin;
                for(int level = 0; level < fillLevelCommand.size(); level++) {
                    
                    List<StochasticProcess> bucketCmdList = fillLevelCommand.get(level);
                    for(StochasticProcess bucketCmd : bucketCmdList) {
                        bucketCmd.startAfterTerminationOf(150 * 1000, previous);
                    }
                    previous = bucketCmdList.get(0);
                }
                
            }

        };

        scenario.setSeed(seed);
        return scenario;
    }
}