package se.sics.p2ptoolbox.election.core.data;

import se.sics.gvod.net.VodAddress;
import se.sics.p2ptoolbox.croupier.api.util.PeerView;

/**
 * Promise Message Object which is 
 * sent between the nodes in the system as part 
 * of the Leader Election Protocol.
 *
 * Created by babbarshaer on 2015-03-29.
 */
public class Promise {

    
    public static class Request{
        
        public final PeerView leaderView;
        public final VodAddress leaderAddress;

        public Request(VodAddress leaderAddress, PeerView leaderView) {
            this.leaderAddress = leaderAddress;
            this.leaderView = leaderView;
        }
    }
    
    public static class Response{
        
        public final boolean acceptCandidate;
        public final boolean isConverged;
        
        public Response(boolean acceptCandidate, boolean isConverged){
            
            this.acceptCandidate = acceptCandidate;
            this.isConverged = isConverged;
        }
        
    }
}
