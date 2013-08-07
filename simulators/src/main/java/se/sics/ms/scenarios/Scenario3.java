package se.sics.ms.scenarios;

import se.sics.ms.simulation.Operations;

/**
 * Initializes the system with 100 nodes, add 200 entries to the index and
 * another 100 nodes after that. During the adding process the leader is
 * crashed. Hence, some add operations might be lost. Later, another 100 nodes
 * are joined.
 */
@SuppressWarnings("serial")
public class Scenario3 extends Scenario {
	private static ThreadedSimulationScenario scenario = new ThreadedSimulationScenario() {
		{
			StochasticProcess startUp = new StochasticProcess() {
				{
					eventInterArrivalTime(constant(100));
					raise(1, Operations.peerJoin(), uniform(0, 0));
				}
			};

			StochasticProcess joinNodes = new StochasticProcess() {
				{
					eventInterArrivalTime(constant(100));
					raise(99, Operations.peerJoin(), uniform(0, Integer.MAX_VALUE));
				}
			};

			StochasticProcess massiveJoin = new StochasticProcess() {
				{
					eventInterArrivalTime(constant(100));
					raise(100, Operations.peerJoin(), uniform(0, Integer.MAX_VALUE));
				}
			};

			StochasticProcess failLeader = new StochasticProcess() {
				{
					eventInterArrivalTime(constant(1000));
					raise(1, Operations.peerFail(), uniform(0, 0));
				}
			};

			StochasticProcess addEntries = new StochasticProcess() {
				{
					eventInterArrivalTime(constant(500));
					raise(200, Operations.addIndexEntry(), uniform(0, 200));
				}
			};

			startUp.start();
			joinNodes.startAfterTerminationOf(2000, startUp);
			addEntries.startAfterTerminationOf(5000, joinNodes);
			failLeader.startAfterTerminationOf(7000, joinNodes);
			massiveJoin.startAfterTerminationOf(2000, addEntries);
		}
	};

	public Scenario3() {
		super(scenario);
	}
}
