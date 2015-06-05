package se.sics.ms.gradient.gradient;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import se.sics.kompics.*;
import se.sics.kompics.network.Network;
import se.sics.kompics.timer.Timer;
import se.sics.ms.gradient.events.PAGUpdate;
import se.sics.ms.gradient.misc.SimpleUtilityComparator;
import se.sics.ms.gradient.ports.PAGPort;
import se.sics.ms.types.SearchDescriptor;
import se.sics.ms.util.CommonHelper;
import se.sics.p2ptoolbox.croupier.CroupierPort;
import se.sics.p2ptoolbox.croupier.msg.CroupierSample;
import se.sics.p2ptoolbox.croupier.msg.CroupierUpdate;
import se.sics.p2ptoolbox.gradient.GradientComp;
import se.sics.p2ptoolbox.gradient.GradientPort;
import se.sics.p2ptoolbox.gradient.msg.GradientShuffle;
import se.sics.p2ptoolbox.gradient.util.GradientLocalView;
import se.sics.p2ptoolbox.util.config.SystemConfig;
import se.sics.p2ptoolbox.util.network.impl.BasicContentMsg;
import se.sics.p2ptoolbox.util.network.impl.DecoratedAddress;
import se.sics.p2ptoolbox.util.network.impl.DecoratedHeader;

/**
 * Main component for the exerting tight control over the gradient and the
 * croupier components in terms of analyzing samples and descriptors selected
 * to exchange data with.
 *
 * Created by babbarshaer on 2015-06-03.
 */
public class PartitionAwareGradient extends ComponentDefinition {


    private Logger logger = LoggerFactory.getLogger(PartitionAwareGradient.class);
    private Component gradient;
    private SystemConfig systemConfig;
    private SearchDescriptor selfDescriptor;
    private String prefix;
    
    // PORTS.
    private Positive<Timer> timerPositive = requires(Timer.class);
    private Positive<Network> networkPositive = requires(Network.class);
    private Positive<CroupierPort> croupierPortPositive = requires(CroupierPort.class);
    private Negative<PAGPort> pagPortNegative = provides(PAGPort.class);
    private Negative<GradientPort> gradientPortNegative = provides(GradientPort.class);


    public PartitionAwareGradient(PAGInit init){

        doInit(init);

        subscribe(startHandler, control);
        subscribe(updateHandler, pagPortNegative);
        
        subscribe(croupierUpdateHandler, gradient.getNegative(CroupierPort.class));
        subscribe(croupierSampleHandler, croupierPortPositive);

        subscribe(handleShuffleRequestFromNetwork, networkPositive);
        subscribe(handleShuffleResponseFromNetwork, networkPositive);

        subscribe(handleShuffleRequestFromGradient, gradient.getNegative(Network.class));
        subscribe(handleShuffleResponseFromGradient, gradient.getNegative(Network.class));
    }

    
    /**
     * Initializer for the Partition Aware Gradient.
     * @param init init
     */
    private void doInit(PAGInit init) {
        
        logger.debug(" Initializing the Partition Aware Gradient ");
        
        systemConfig = init.getSystemConfig();
        prefix = String.valueOf(init.getBasicAddress().getId());
        
        // Gradient Connections.
        GradientComp.GradientInit gInit = new GradientComp.GradientInit(
                systemConfig, 
                init.getGradientConfig(),
                init.getOverlayId(),
                new SimpleUtilityComparator(), 
                new SweepGradientFilter());
        
        gradient = create(GradientComp.class, gInit);
        connect(gradient.getNegative(Timer.class), timerPositive);
        connect(gradient.getPositive(GradientPort.class), gradientPortNegative);    // Auxiliary Port for Direct Transfer of Data.
    }

    
    Handler<Start> startHandler = new Handler<Start>() {
        @Override
        public void handle(Start event) {
            logger.debug(" {}: Partition Aware Gradient Initialized ... ", prefix);
        }
    };

    /**
     * Simple handler for the self update
     * which is pushed by the application whenever the value
     * in self descriptor changes.
     * 
     */
    Handler<PAGUpdate> updateHandler = new Handler<PAGUpdate>() {
        @Override
        public void handle(PAGUpdate event) {

            logger.debug(" {}: Received update from the application ", prefix);
            selfDescriptor = event.getSelfView();
        }
    };

    /**
     * Blocks the direct update from the gradient component to the croupier
     * and relays it through this handler.
     */
    Handler<CroupierUpdate> croupierUpdateHandler = new Handler<CroupierUpdate>() {
        @Override
        public void handle(CroupierUpdate event) {
            
            logger.debug("{}: Received croupier update from the gradient. ", prefix);
            trigger(event, croupierPortPositive);
        }
    };
    
    
    /**
     * Handler that intercepts the sample from Croupier and then looks into the sample,
     * to filter them into safe and unsafe samples. The safe samples are allowed to pass through while 
     * the unsafe samples are blocked and handed over to the application after verification.
     *
     */
    Handler<CroupierSample<GradientLocalView>> croupierSampleHandler = new Handler<CroupierSample<GradientLocalView>>() {
        @Override
        public void handle(CroupierSample<GradientLocalView> event) {

            logger.debug("{}: Received sample from croupier ", prefix);
            trigger(event, gradient.getNegative(CroupierPort.class));
        }
    };


    /**
     *
     * Interceptor for the gradient shuffle request.
     * The component analyzes the node from which the shuffle request is 
     * received and only if the node feels safe, then it is allowed to pass else the request is dropped.
     * <br/>
     * In some cases it might be really difficult to determine if based on the current 
     * state of self the  node is good or bad. Therefore, the component will buffer the request and initiate 
     * a verification mechanism. After verification gets over, appropriate steps are taken.
     * 
     */
    ClassMatchedHandler handleShuffleRequestFromGradient
            = new ClassMatchedHandler<GradientShuffle.Request, BasicContentMsg<DecoratedAddress, DecoratedHeader<DecoratedAddress>, GradientShuffle.Request>>() {

        @Override
        public void handle(GradientShuffle.Request content, BasicContentMsg<DecoratedAddress, DecoratedHeader<DecoratedAddress>, GradientShuffle.Request> context) {
            
            logger.debug("{}: Received Shuffle Request, from gradient,  forwarding it ... ", prefix);
            BasicContentMsg request = new BasicContentMsg(context.getHeader(), content);
            trigger(request, networkPositive);
        }
    };

    /**
     * Same implementation as above but for the Shuffle Response.
     */
    ClassMatchedHandler handleShuffleResponseFromGradient
            = new ClassMatchedHandler<GradientShuffle.Response, BasicContentMsg<DecoratedAddress, DecoratedHeader<DecoratedAddress>, GradientShuffle.Response>>() {

        @Override
        public void handle(GradientShuffle.Response content, BasicContentMsg<DecoratedAddress, DecoratedHeader<DecoratedAddress>, GradientShuffle.Response> context) {
            
            logger.debug("{}: Received gradient shuffle response, forwarding it ...", prefix);
            BasicContentMsg response = new BasicContentMsg(context.getHeader(), content);
            trigger(response, networkPositive);
        }
    };




    /**
     * Interceptor for the gradient shuffle request.
     * The component analyzes the node from which the shuffle request is
     * received and only if the node feels safe, then it is allowed to pass else the request is dropped.
     * <br/>
     * In some cases it might be really difficult to determine if based on the current
     * state of self the  node is good or bad. Therefore, the component will buffer the request and initiate
     * a verification mechanism. After verification gets over, appropriate steps are taken.
     *
     */
    ClassMatchedHandler handleShuffleRequestFromNetwork
            = new ClassMatchedHandler<GradientShuffle.Request, BasicContentMsg<DecoratedAddress, DecoratedHeader<DecoratedAddress>, GradientShuffle.Request>>() {

        @Override
        public void handle(GradientShuffle.Request content, BasicContentMsg<DecoratedAddress, DecoratedHeader<DecoratedAddress>, GradientShuffle.Request> context) {

            logger.debug("{}: Received Shuffle Request, from network..  forwarding it ... ", prefix);
            BasicContentMsg request = new BasicContentMsg(context.getHeader(), content);
            trigger(request, gradient.getNegative(Network.class));
        }
    };

    /**
     * Same implementation as above but for the Shuffle Response.
     */
    ClassMatchedHandler handleShuffleResponseFromNetwork
            = new ClassMatchedHandler<GradientShuffle.Response, BasicContentMsg<DecoratedAddress, DecoratedHeader<DecoratedAddress>, GradientShuffle.Response>>() {

        @Override
        public void handle(GradientShuffle.Response content, BasicContentMsg<DecoratedAddress, DecoratedHeader<DecoratedAddress>, GradientShuffle.Response> context) {

            logger.debug("{}: Received gradient shuffle response, forwarding it ...", prefix);
            BasicContentMsg response = new BasicContentMsg(context.getHeader(), content);
            trigger(response, gradient.getNegative(Network.class));
        }
    };


}

