package se.sics.ms.util;

import se.sics.kompics.network.Transport;
import se.sics.p2ptoolbox.util.network.impl.BasicContentMsg;
import se.sics.p2ptoolbox.util.network.impl.DecoratedAddress;
import se.sics.p2ptoolbox.util.network.impl.DecoratedHeader;

import java.util.*;

/**
 * Common Helper Common Helper for the
 * Created by babbar on 2015-04-18.
 */
public class CommonHelper {

    /**
     * Generic method used to return a sorted list.
     * @param collection Any Collection of samples.
     * @param comparator Comparator for sorting.
     * @param <E> Collection Type
     *
     * @return Sorted Collection
     */
    public static  <E> List<E> sortCollection(Collection<E> collection, Comparator<E> comparator){

        List<E> list = new ArrayList<E>();
        list.addAll(collection);
        Collections.sort(list, comparator);

        return list;
    }


    /**
     * Convenience Constructor method for the basic content message.
     * @param header header
     * @param content content
     * @param <E> type
     * @return content message
     */
    public static <E> BasicContentMsg<DecoratedAddress, DecoratedHeader<DecoratedAddress>, E> getDecoratedContentMsg(DecoratedHeader<DecoratedAddress> header, E content){
        return new BasicContentMsg<DecoratedAddress, DecoratedHeader<DecoratedAddress>, E>(header, content);
    }
    
    
    public static <E> BasicContentMsg<DecoratedAddress, DecoratedHeader<DecoratedAddress>, E> getDecoratedContentMessage(DecoratedAddress sourceAddress, DecoratedAddress destination, Transport transport, E content){
        
        DecoratedHeader<DecoratedAddress> decoratedHeader = new DecoratedHeader<DecoratedAddress>(sourceAddress, destination,  transport);
        return new BasicContentMsg<DecoratedAddress, DecoratedHeader<DecoratedAddress>, E>(decoratedHeader, content);
        
    }


}
