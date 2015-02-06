/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package se.sics.ms.scenarios;

import se.sics.ms.simulation.Operations;

/**
 *
 * @author alidar
 */

/**
 * Scenario to test partitioning code.
 */
@SuppressWarnings("serial")
public class SearchResponseScenario extends Scenario {
    private static ThreadedSimulationScenario scenario = new ThreadedSimulationScenario() {
        {
            StochasticProcess joinNodes = new StochasticProcess() {
                {
                    eventInterArrivalTime(constant(100));
                    raise(5, Operations.peerJoin(), uniform(0, Integer.MAX_VALUE));
                }
            };

            StochasticProcess addEntries = new StochasticProcess() {
                {
                    eventInterArrivalTime(constant(100));
                    raise(1, Operations.addIndexEntry(), uniform(0, Integer.MAX_VALUE));
                }
            };

            StochasticProcess addEntries1 = new StochasticProcess() {
                {
                    eventInterArrivalTime(constant(100));
                    raise(1, Operations.addIndexEntry(), uniform(0, Integer.MAX_VALUE));
                }
            };

            StochasticProcess addEntries2 = new StochasticProcess() {
                {
                    eventInterArrivalTime(constant(100));
                    raise(10, Operations.addIndexEntry(), uniform(0, Integer.MAX_VALUE));
                }
            };

            StochasticProcess searchEntries = new StochasticProcess() {{

                eventInterArrivalTime(constant(300));
                raise(1, Operations.search(), constant(4041837));

            }};


            joinNodes.start();
            // Add Entries.
            addEntries.startAfterTerminationOf(3000, joinNodes);

//            addEntries1.startAfterTerminationOf(500000, addEntries);
//            addEntries2.startAfterTerminationOf(100000, addEntries1);
            // Start Searching Them.
//            searchEntries.startAfterTerminationOf(500000,addEntries);
        }
    };

    public SearchResponseScenario() {
        super(scenario);
    }

}