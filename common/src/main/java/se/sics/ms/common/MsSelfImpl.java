package se.sics.ms.common;

import se.sics.gvod.common.SelfImpl;
import se.sics.gvod.common.VodDescriptor;
import se.sics.gvod.common.vod.VodView;
import se.sics.gvod.config.VodConfig;
import se.sics.gvod.net.Nat;
import se.sics.gvod.net.VodAddress;
import se.sics.ms.types.OverlayId;

import java.net.InetAddress;
import java.util.concurrent.atomic.AtomicLong;

/**
 *
 * @author: Steffen Grohsschmiedt
 */
public class MsSelfImpl extends SelfImpl {

    // FIXME: Removing the static reference as creating problems in simulations.
    private AtomicLong numberOfIndexEntries = new AtomicLong();

    public MsSelfImpl(VodAddress addr) {
        super(addr);
    }

    public MsSelfImpl(Nat nat, InetAddress ip, int port, int nodeId, int overlayId) {
        super(nat, ip, port, nodeId, overlayId);
    }

    @Override
    public VodDescriptor getDescriptor() {
        int age = 0;
        return  new VodDescriptor(getAddress(), VodView.getPeerUtility(this), age, VodConfig.LB_MTU_MEASURED,
                numberOfIndexEntries.get());
    }

    public void setNumberOfIndexEntries(long numberOfIndexEntries) {
        this.numberOfIndexEntries.set(numberOfIndexEntries);
    }

    public void incrementNumberOfIndexEntries() {
        this.numberOfIndexEntries.incrementAndGet();
    }

    public void setOverlayId(int overlayId) {
        this.overlayId = overlayId;
    }

    public int getCategoryId() {
        return OverlayId.getCategoryId(overlayId);
    }

    public int getPartitionId() {
        return OverlayId.getPartitionId(overlayId);
    }

    public int getPartitionIdDepth() {
        return OverlayId.getPartitionIdDepth(overlayId);
    }

    public VodAddress.PartitioningType getPartitioningType() {
        return OverlayId.getPartitioningType(overlayId);
    }

    public long getNumberOfIndexEntries(){
        return numberOfIndexEntries.get();
    }

}
