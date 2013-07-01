package search.main;

import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.HashSet;
import java.util.Set;
import java.util.logging.Level;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import se.sics.gvod.address.Address;
import se.sics.gvod.common.Self;
import se.sics.gvod.common.SelfImpl;
import se.sics.gvod.common.util.ToVodAddr;
import se.sics.gvod.config.VodConfig;
import se.sics.gvod.nat.traversal.NatTraverser;
import se.sics.gvod.nat.traversal.events.NatTraverserInit;
import se.sics.gvod.net.NatNetworkControl;
import se.sics.gvod.net.NettyInit;
import se.sics.gvod.net.NettyNetwork;
import se.sics.gvod.net.VodNetwork;
import se.sics.gvod.timer.Timer;
import se.sics.gvod.timer.java.JavaTimer;
import se.sics.kompics.Component;

import se.sics.kompics.ComponentDefinition;
import se.sics.kompics.Fault;
import se.sics.kompics.Handler;
import se.sics.kompics.Kompics;
import se.sics.kompics.Start;
import se.sics.kompics.nat.utils.getip.ResolveIp;
import se.sics.kompics.nat.utils.getip.ResolveIpPort;
import se.sics.kompics.nat.utils.getip.events.GetIpRequest;
import se.sics.kompics.nat.utils.getip.events.GetIpResponse;
import se.sics.peersearch.net.PsMsgFrameDecoder;
import search.system.peer.SearchPeer;
import search.system.peer.SearchPeerInit;

public class ProductionPs extends ComponentDefinition {
    private static final Logger logger = LoggerFactory.getLogger(ProductionPs.class);

    Component network;
    Component timer;
    Component natTraverser;
    Component searchPeer;
    private Component resolveIp;
    private Self self;

    public ProductionPs() {
        network = create(NettyNetwork.class);
        timer = create(JavaTimer.class);
        natTraverser = create(NatTraverser.class);
        searchPeer = create(SearchPeer.class);
        
        resolveIp = create(ResolveIp.class);

        connect(natTraverser.getNegative(Timer.class), timer.getPositive(Timer.class));
        connect(natTraverser.getNegative(VodNetwork.class), network.getPositive(VodNetwork.class));
        connect(natTraverser.getNegative(NatNetworkControl.class), network.getPositive(NatNetworkControl.class));
        connect(resolveIp.getNegative(Timer.class), timer.getPositive(Timer.class));


        subscribe(handleStart, control);
        subscribe(handleGetIpResponse, resolveIp.getPositive(ResolveIpPort.class));
        subscribe(handleFault, natTraverser.getControl());
        subscribe(handleNettyFault, network.getControl());
    }
    Handler<Start> handleStart = new Handler<Start>() {
        @Override
        public void handle(Start event) {
            
            trigger(new GetIpRequest(false),
                    resolveIp.getPositive(ResolveIpPort.class));
            
        }
    };

    public Handler<GetIpResponse> handleGetIpResponse = new Handler<GetIpResponse>() {
        @Override
        public void handle(GetIpResponse event) {

            InetAddress localIp = event.getIpAddress();
            Address myAddr = new Address(localIp, VodConfig.getPort(), 1);
            NettyInit nInit = new NettyInit(myAddr, true, VodConfig.getSeed(),
                    PsMsgFrameDecoder.class);
            trigger(nInit, network.getControl());
            
            self = new SelfImpl(ToVodAddr.systemAddr(myAddr));
            
            Set<Address> publicNodes = new HashSet<Address>();
            try {
                InetAddress inet = InetAddress.getByName("cloud7.sics.se");
                publicNodes.add(new Address(inet, VodConfig.getPort(), 0));
            } catch (UnknownHostException ex) {
                java.util.logging.Logger.getLogger(ProductionPs.class.getName()).log(Level.SEVERE, null, ex);
            }
            
            trigger(new NatTraverserInit(self, publicNodes, VodConfig.getSeed()),
                    natTraverser.getControl());
            
//            trigger(new SearchPeerInit(self.getAddress(), , null, null, null, null),
//                    searchPeer.getControl());
        }
    };
    
    public Handler<Fault> handleFault =
            new Handler<Fault>() {
        @Override
        public void handle(Fault ex) {

            logger.debug(ex.getFault().toString());
            System.exit(-1);
        }
    }; 
    
    Handler<Fault> handleNettyFault = new Handler<Fault>() {
        @Override
        public void handle(Fault msg) {
            logger.error("Problem in Netty: {}", msg.getFault().getMessage());
            System.exit(-1);
        }
    };        
    
    /**
     * Starts the execution of the program
     *
     * @param args the command line arguments
     * @throws IOException in case the configuration file couldn't be created
     */
    public static void main(String[] args) throws IOException {

        int cores = Runtime.getRuntime().availableProcessors();
        int numWorkers = Math.max(1, cores - 1);
        
        System.setProperty("java.net.preferIPv4Stack", "true");
        try {
            VodConfig.init(args);
        } catch (IOException ex) {
            java.util.logging.Logger.getLogger(ProductionPs.class.getName()).log(Level.SEVERE, null, ex);
        }
        
        Kompics.createAndStart(ProductionPs.class, numWorkers);
    }
}
