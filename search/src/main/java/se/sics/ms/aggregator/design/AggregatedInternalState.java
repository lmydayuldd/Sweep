//package se.sics.ms.aggregator.design;
//
//import se.sics.ktoolbox.aggregator.server.util.DesignInfo;
//import se.sics.ms.data.InternalStatePacket;
//import java.util.Collection;
//
///**
// *
// * Created by babbarshaer on 2015-09-09.
// */
//public class AggregatedInternalState implements DesignInfo {
//    
//    private Collection<InternalStatePacket> internalStatePackets;
//    
//    public AggregatedInternalState(Collection<InternalStatePacket> internalStatePackets){
//        this.internalStatePackets = internalStatePackets;
//    }
//
//    @Override
//    public String toString() {
//        return "AggregatedInternalState{" +
//                "internalStatePackets=" + internalStatePackets +
//                '}';
//    }
//
//    public Collection<InternalStatePacket> getInternalStatePackets() {
//        return internalStatePackets;
//    }
//}
