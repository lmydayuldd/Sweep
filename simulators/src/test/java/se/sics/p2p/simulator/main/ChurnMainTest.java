package se.sics.p2p.simulator.main;

import se.sics.ms.scenarios.special.BasicChurnScenario;
import se.sics.ms.scenarios.special.FastConvergenceScenario;
import se.sics.p2ptoolbox.simulator.run.LauncherComp;

/**
 * Main Test Class for working with the convergence scenarios.
 * 
 * Created by babbarshaer on 2015-05-09.
 */
public class ChurnMainTest {
    
    public static void main(String[] args) {
        
        if(args.length < 7){
            throw new RuntimeException("Arguments Expected: { seed, throughput, initialClusterSize, numEntries, churnPeerAdd, churnPeerKill, churnEntryAdd }");
        }

        long seed = Long.valueOf(args[0]);
        int throughput = Integer.valueOf(args[1]);
        int initialClusterSize = Integer.valueOf(args[2]);
        int numEntries = Integer.valueOf(args[3]);
        int churnPeerAdd = Integer.valueOf(args[4]);
        int churnPeerKill = Integer.valueOf(args[5]);
        int churnEntryAdd = Integer.valueOf(args[6]);

        

        System.out.println(" Starting the Convergence Scenario with" 
                + " seed: " + seed 
                + " throughput: " + throughput 
                + " initialClusterSize: "+ initialClusterSize 
                +" entries: " + numEntries 
                + " churnPeerAdd: " + churnPeerAdd 
                + " churnPeerKill: " + churnPeerKill 
                + " churnEntryJoin: "  + churnEntryAdd);

        BasicChurnScenario.boot(seed, throughput, initialClusterSize, numEntries, churnPeerAdd, churnPeerKill, churnEntryAdd).simulate(LauncherComp.class);
    }

}
