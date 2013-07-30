package se.sics.peersearch.messages;

import io.netty.buffer.ByteBuf;
import se.sics.gvod.common.msgs.MessageDecodingException;
import se.sics.gvod.common.msgs.DirectMsgNettyFactory;
import se.sics.gvod.net.VodAddress;
import se.sics.gvod.net.util.UserTypesDecoderFactory;

/**
 * Created with IntelliJ IDEA.
 * User: kazarindn
 * Date: 7/2/13
 * Time: 12:34 PM
 */
public class LeaderAnnouncementMessageFactory extends DirectMsgNettyFactory.Oneway {
    public static LeaderDeathAnnouncementMessage fromBuffer(ByteBuf buffer)
            throws MessageDecodingException {
        return (LeaderDeathAnnouncementMessage)
                new LeaderAnnouncementMessageFactory().decode(buffer, false);
    }

    @Override
    protected LeaderDeathAnnouncementMessage process(ByteBuf buffer) throws MessageDecodingException {
        VodAddress leader = UserTypesDecoderFactory.readVodAddress(buffer);
        return new LeaderDeathAnnouncementMessage(vodSrc, vodDest, leader);
    }
}