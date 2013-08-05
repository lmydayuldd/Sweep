package se.sics.peersearch.messages;

import io.netty.buffer.ByteBuf;
import se.sics.gvod.common.VodDescriptor;
import se.sics.gvod.common.msgs.DirectMsgNetty;
import se.sics.gvod.common.msgs.MessageEncodingException;
import se.sics.gvod.net.VodAddress;
import se.sics.gvod.net.msgs.RewriteableMsg;
import se.sics.gvod.net.util.UserTypesEncoderFactory;
import se.sics.peersearch.net.MessageFrameDecoder;

/**
 * Created with IntelliJ IDEA.
 * User: kazarindn
 * Date: 7/8/13
 * Time: 12:26 PM
 */
public class RejectLeaderMessage extends DirectMsgNetty.Oneway {
    private final VodDescriptor betterLeader;

    public RejectLeaderMessage(VodAddress source, VodAddress destination, VodDescriptor betterLeader) {
        super(source, destination);

        if(betterLeader == null)
            throw new NullPointerException("betterLeader can't be null");

        this.betterLeader = betterLeader;
    }

    public VodDescriptor getBetterLeader() {
        return betterLeader;
    }

    @Override
    public int getSize() {
        return getHeaderSize()+16;
    }

    @Override
    public RewriteableMsg copy() {
        return new RejectLeaderMessage(vodSrc, vodDest, betterLeader);
    }

    @Override
    public ByteBuf toByteArray() throws MessageEncodingException {
        ByteBuf buffer = createChannelBufferWithHeader();
        UserTypesEncoderFactory.writeVodNodeDescriptor(buffer, betterLeader);
        return buffer;
    }

    @Override
    public byte getOpcode() {
        return MessageFrameDecoder.REJECT_LEADER_MESSAGE;
    }
}
