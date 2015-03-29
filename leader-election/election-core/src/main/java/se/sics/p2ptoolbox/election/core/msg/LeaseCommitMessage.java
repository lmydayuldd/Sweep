package se.sics.p2ptoolbox.election.core.msg;

import se.sics.gvod.net.VodAddress;
import se.sics.gvod.net.msgs.RewriteableMsg;
import se.sics.p2ptoolbox.election.core.data.LeaseCommit;
import se.sics.p2ptoolbox.serialization.msg.NetContentMsg;

import java.util.UUID;

/**
 * Container class for the Lease Commit message Communication.
 * Created by babbarshaer on 2015-03-29.
 */
public class LeaseCommitMessage {
    
    
    public static class Request extends NetContentMsg.Request<LeaseCommit.Request>{


        public Request(VodAddress src, VodAddress dest, UUID id, LeaseCommit.Request content) {
            super(src, dest, id, content);
        }

        @Override
        public RewriteableMsg copy() {
            return null;
        }
    }
    
    public static class Response extends NetContentMsg.Response<LeaseCommit.Response>{

        public Response(VodAddress src, VodAddress dest, UUID id, LeaseCommit.Response content) {
            super(src, dest, id, content);
        }

        @Override
        public RewriteableMsg copy() {
            return null;
        }
    }
    
    
    
}
