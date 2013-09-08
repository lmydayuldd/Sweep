package se.sics.ms.util;

import se.sics.gvod.common.VodDescriptor;
import se.sics.gvod.net.VodAddress;
import se.sics.ms.common.MsSelfImpl;
import se.sics.ms.types.PartitionId;

import java.util.*;

/**
 * Created with IntelliJ IDEA.
 * User: kazarindn
 * Date: 8/22/13
 * Time: 2:18 PM
 */
public class PartitionHelper {
    /**
     * Returns next bit of the partitionId after partitioning is performed
     * @param yourNodeId node id
     * @param partitionId current partition id
     * @return next bit of the partition id
     */
    public static boolean determineYourNewPartitionSubId(int yourNodeId, PartitionId partitionId) {
        if(partitionId == null)
            throw new IllegalArgumentException("currentPartitionId can't be null");

        if(partitionId.getPartitioningType() == VodAddress.PartitioningType.NEVER_BEFORE)
            return (yourNodeId & 1) != 0;

        return (yourNodeId & (1 << partitionId.getPartitionIdDepth())) != 0;
    }

    /**
     * Returns new partition id for VodDescriptor
     * @param descriptor
     * @param isFirstPartition
     * @param bitsToCheck
     * @return
     */
    public static PartitionId determineVodDescriptorPartition(VodDescriptor descriptor, boolean isFirstPartition,
                                                                      int bitsToCheck) {
        if(descriptor == null)
            throw new IllegalArgumentException("descriptor can't be null");

        if(isFirstPartition)
            return new PartitionId(VodAddress.PartitioningType.ONCE_BEFORE, 1, descriptor.getId() & 1);

        int partitionId = 0;

        for(int i=0; i<bitsToCheck; i++)
            partitionId = partitionId | (descriptor.getId() & (1<<i));

        return new PartitionId(VodAddress.PartitioningType.MANY_BEFORE, bitsToCheck, partitionId);
    }

    /**
     * Set's new new partitionId for address
     * @param address
     * @param partitionId
     */
    public static void setPartitionId(VodAddress address, PartitionId partitionId) {
        if(address == null)
            throw new IllegalArgumentException("address can't be null");
        if(partitionId == null)
            throw new IllegalArgumentException("partitionId can't be null");

        int categoryId = address.getCategoryId();
        int newOverlayId = VodAddress.encodePartitionDataAndCategoryIdAsInt(partitionId.getPartitioningType(),
                partitionId.getPartitionIdDepth(), partitionId.getPartitionId(), categoryId);
        address.setOverlayId(newOverlayId);
    }

    /**
     * Ensures that all entries in descriptors belong to the same partition as self by recalculation of partitionIds
     * on this descriptors and throwing out those that are from another partitions
     * @param partitionId
     * @param descriptors
     */
    public static void adjustDescriptorsToNewPartitionId(PartitionId partitionId, Collection<VodDescriptor> descriptors) {
        if(partitionId == null)
            throw new IllegalArgumentException("partitionId can't be null");
        if(descriptors == null)
            return;

        //this method has to be called after the partitionsNumber is already incremented
        boolean isFirstPartition = partitionId.getPartitioningType() == VodAddress.PartitioningType.ONCE_BEFORE;
        if(isFirstPartition) {
            for(VodDescriptor descriptor : descriptors) {
                PartitionId descriptorPartitionId = determineVodDescriptorPartition(descriptor,
                        isFirstPartition, 1);

                setPartitionId(descriptor.getVodAddress(), descriptorPartitionId);
            }
        }
        else {
            int bitsToCheck = partitionId.getPartitionIdDepth();

            for(VodDescriptor descriptor : descriptors) {
                PartitionId descriptorPartitionId = determineVodDescriptorPartition(descriptor,
                        isFirstPartition, bitsToCheck);

                setPartitionId(descriptor.getVodAddress(), descriptorPartitionId);
            }
        }

        Iterator<VodDescriptor> iterator = descriptors.iterator();
        while (iterator.hasNext()) {
            VodAddress next = iterator.next().getVodAddress();
            if(next.getPartitionId() != partitionId.getPartitionId()
                    || next.getPartitionIdDepth() != partitionId.getPartitionIdDepth()
                    || next.getPartitioningType() != partitionId.getPartitioningType())
                iterator.remove();

        }
    }

    /**
     * Converts a partitionId as LinkedList<Boolean> to Integer
     * @param partition partitionId as LinkedList<Boolean>
     * @return partitionId as integer
     */
    public static int LinkedListPartitionToInt(LinkedList<Boolean> partition) {
        int partitionId = 0;
        int j = 0;
        for(int i = partition.size()-1; i >= 0; i--) {
            if(partition.get(i))
                partitionId = partitionId | (1 << j++);
        }

        return partitionId;
    }

    /**
     * updates a bucket in the categoryRoutingMap regarding new partitionId
     * @param newPartitionId unseen before partition id
     * @param categoryRoutingMap map for looking for an old bucket
     * @param bucket bucket for new partition id
     */
    public static void updateBucketsInRoutingTable(PartitionId newPartitionId, Map<Integer,
            HashSet<VodDescriptor>> categoryRoutingMap, HashSet<VodDescriptor> bucket) {
        if(newPartitionId == null)
            throw new IllegalArgumentException("newPartitionId can't be null");

        if(newPartitionId.getPartitionId() == 0)
            return;

        //if first split
        if(newPartitionId.getPartitionIdDepth() == 1) {
            HashSet<VodDescriptor> oldBucket = categoryRoutingMap.get(0);
            if(oldBucket == null)
                return;

            Iterator<VodDescriptor> oldBucketIterator = oldBucket.iterator();
            while(oldBucketIterator.hasNext()) {
                VodDescriptor next = oldBucketIterator.next();
                VodAddress nextAddress = next.getVodAddress();
                boolean partitionSubId = PartitionHelper.determineYourNewPartitionSubId(next.getId(),
                        new PartitionId(nextAddress.getPartitioningType(), nextAddress.getPartitionIdDepth(),
                                nextAddress.getPartitionId()));
                //first spilling => move to a new bucket all with "true"
                if(partitionSubId) {
                    PartitionId descriptorsPartitionId = new PartitionId(VodAddress.PartitioningType.ONCE_BEFORE,
                            1, 1);
                    setPartitionId(nextAddress, descriptorsPartitionId);
                    oldBucketIterator.remove();
                    bucket.add(next);
                }
            }

            return;
        }

        int oldPartitionId = newPartitionId.getPartitionId() & (0 << newPartitionId.getPartitionIdDepth()-1);
        HashSet<VodDescriptor> oldBucket = categoryRoutingMap.get(oldPartitionId);
        if(oldBucket == null)
            return;

        Iterator<VodDescriptor> oldBucketIterator = oldBucket.iterator();
        while(oldBucketIterator.hasNext()) {
            VodDescriptor next = oldBucketIterator.next();
            VodAddress nextAddress = next.getVodAddress();
            boolean partitionSubId = PartitionHelper.determineYourNewPartitionSubId(next.getId(),
                    new PartitionId(nextAddress.getPartitioningType(), nextAddress.getPartitionIdDepth(),
                            nextAddress.getPartitionId()));

            setPartitionId(nextAddress, new PartitionId(VodAddress.PartitioningType.MANY_BEFORE,
                    partitionSubId ? nextAddress.getPartitionId() | (1 << nextAddress.getPartitionIdDepth()) :
            nextAddress.getPartitionIdDepth(), nextAddress.getPartitionIdDepth() + 1));


            boolean isOne = ((nextAddress.getPartitionId() & (1 << nextAddress.getPartitionIdDepth()-1)) == 1);
            //move to a new bucket all with first "true"
            if(isOne) {
                oldBucketIterator.remove();
                bucket.add(next);
            }
        }

    }
}
