package se.sics.ms.gradient.gradient;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import se.sics.gvod.common.Self;
import se.sics.gvod.net.VodAddress;
import se.sics.ms.common.MsSelfImpl;
import se.sics.ms.gradient.misc.UtilityComparator;
import se.sics.ms.types.PartitionId;
import se.sics.ms.types.SearchDescriptor;
import se.sics.ms.util.PartitionHelper;

import java.util.*;


/**
 * Class representing the gradient view. It selects nodes according to the
 * preference function for a given node and offers functions to find the optimal
 * exchange partners for a given node.
 */
public class GradientView {
    private static final Logger logger = LoggerFactory.getLogger(GradientView.class);
    private TreeMap<VodAddress, SearchDescriptor> mapping;
	private TreeSet<SearchDescriptor> entries;
	private MsSelfImpl self;
	private int size;
	private final Comparator<SearchDescriptor> preferenceComparator;
    private final int convergenceTestRounds;
    private int currentConvergedRounds;
	private boolean converged, changed;
	private final double convergenceTest;
    private final UtilityComparator utilityComparator = new UtilityComparator();

	/**
	 * @param self
	 *            the address of the local node
	 * @param size
	 *            the maximum size of this view
	 * @param convergenceTest
	 *            the percentage of nodes allowed to change in order to be
	 *            converged
     * @param convergenceTestRounds
     *            the number of rounds the convergenceTest needs to be satisfied for the view to be converged
	 */
	public GradientView(Self self, int size, double convergenceTest, int convergenceTestRounds) {
        this.preferenceComparator = new PreferenceComparator(new SearchDescriptor(self.getDescriptor()));
        this.mapping = new TreeMap<VodAddress, SearchDescriptor>();
		this.entries = new TreeSet<SearchDescriptor>(utilityComparator);
		this.self = (MsSelfImpl)self;
		this.size = size;
		this.converged = false;
        this.changed = false;
		this.convergenceTest = convergenceTest;
        this.convergenceTestRounds = convergenceTestRounds;
	}

	/**
	 * Add a new node to the view and drop the least preferred one if the view
	 * is full.
	 * 
	 * @param searchDescriptor
	 *            the searchDescriptor to be added
	 */
	protected void add(SearchDescriptor searchDescriptor) {
        if (searchDescriptor.getVodAddress().equals(self.getAddress())) {
            logger.warn("{} tried to add itself to its GradientView with number of Index Entries {}: ", self.getAddress());
            logger.warn(" _ISSUE: Search Descriptor id: " + searchDescriptor.getId() + " Descriptor : Number of Index Entries: " + searchDescriptor.getNumberOfIndexEntries());
            return;
        }

        int oldSize = entries.size();

        SearchDescriptor currDescriptor = null;

        for(SearchDescriptor descriptor : entries){
            if(descriptor.getVodAddress().getPeerAddress().equals(searchDescriptor.getVodAddress().getPeerAddress())){
                currDescriptor = descriptor;
                break;
            }
        }

        if(currDescriptor != null){
            if(searchDescriptor.getAge() > currDescriptor.getAge())
                return;
            else{

                // ======= TESTING..
                if(self.getId() == 4041837 && self.getPartitioningType() == VodAddress.PartitioningType.ONCE_BEFORE && searchDescriptor.getVodAddress().getId() == 398137537) {
//                    logger.warn("START ========= ");
//                    logger.warn("======== Current Descriptor: " + currDescriptor.getId() + " Age: " + currDescriptor.getAge() + "Number of Index Entries : " + currDescriptor.getNumberOfIndexEntries());
//                    logger.warn("======== Search Descriptor: " + searchDescriptor.getId() + " Age: " + searchDescriptor.getAge() + " Number of Index Entries : " + searchDescriptor.getNumberOfIndexEntries());
//                    logger.warn("END =========");
//
//                    logger.warn("  ");
                }
                entries.remove(currDescriptor);
                changed = true;
            }
        }

        // NOTE: Presence of overlayId in the search descriptor, allows for duplication in the Search Sample.
        entries.add(searchDescriptor);

        if (!changed) {
            changed = !(oldSize == entries.size());
        }

		if (entries.size() > size) {
			SortedSet<SearchDescriptor> set = getClosestNodes(size + 1);
            SearchDescriptor leastPreferred = set.first();
			remove(leastPreferred.getVodAddress());
		}
	}

    public void setChanged() {
        changed = true;
    }

	/**
	 * Remove a node from the view.
	 * 
	 * @param address
	 *            the node to be removed
	 */
	protected void remove(VodAddress address) {
        int oldSize = entries.size();
//        SearchDescriptor toRemove = mapping.remove(address);
        SearchDescriptor toRemove = null;

        for(SearchDescriptor descriptor:  entries){
            if(descriptor.getVodAddress().equals(address)){
                toRemove = descriptor;
                break;
            }
        }

        if (toRemove == null) {
            return;
        }

		entries.remove(toRemove);
        if (!changed) {
            changed = !(oldSize == entries.size());
        }
	}

	/**
	 * Return the node with the oldest age.
	 * 
	 * @return the address of the node with the oldest age
	 */
	protected SearchDescriptor selectPeerToShuffleWith() {
		if (entries.isEmpty()) {
			return null;
		}

		incrementDescriptorAges();
//		SearchDescriptor oldestEntry = Collections.max(entries);
//
//		return oldestEntry;

      return getClosestNodes(1).first();
	}

	/**
	 * Merge a collection of nodes in the view and drop the least preferred
	 * nodes if the size limit is reached.
	 *
	 * @param searchDescriptors
	 *            the nodes to be merged
	 */
	protected void merge(Collection<SearchDescriptor> searchDescriptors) {

//        if(self.getId() == 319791623 && self.getPartitioningType() == VodAddress.PartitioningType.ONCE_BEFORE){
//            logger.warn("========== RECEIVED DESCRIPTORS FOR MERGING =============== ");
//            for(SearchDescriptor desc : searchDescriptors)
//                logger.warn(" DescriptorID : " + desc.getId() + " Descriptor Overlay : " + desc.getOverlayId() + "Number of Index Entries: " + desc.getNumberOfIndexEntries());
//            logger.warn("=========== END ==========================");
//            logger.warn("");
//        }


        Collection<SearchDescriptor> oldEntries = (Collection<SearchDescriptor>) entries.clone();
		int oldSize = oldEntries.size();

        if(self.getPartitioningType() != VodAddress.PartitioningType.NEVER_BEFORE) {

            PartitionId myPartitionId = new PartitionId(self.getPartitioningType(),
                    self.getPartitionIdDepth(), self.getPartitionId());
            PartitionHelper.adjustDescriptorsToNewPartitionId(myPartitionId, searchDescriptors);
        }

        for (SearchDescriptor searchDescriptor : searchDescriptors) {
            add(searchDescriptor);
        }

//        if(self.getId() == 319791623){
//            logger.warn("========== OLD ENTRIES BEFORE REMOVAL =============== ");
//            for(SearchDescriptor desc : oldEntries)
//                logger.warn(" DescriptorID : " + desc.getId() + " Descriptor Overlay : " + desc.getOverlayId() + "Number of Index Entries: " + desc.getNumberOfIndexEntries());
//            logger.warn("=========== END ==========================");
//            logger.warn("");
//        }


        // Check old entries retain all method.
		oldEntries.retainAll(entries);
		if (oldSize == entries.size() && oldEntries.size() > convergenceTest * entries.size()) {
            currentConvergedRounds++;
		} else {
            if(self.getId() == 4041837 && self.getPartitioningType() == VodAddress.PartitioningType.ONCE_BEFORE){
////                logger.warn("Convergence Test : " + (oldEntries.size() > convergenceTest * entries.size()) + " OldEntriesSize: " + oldEntries.size() + " Converged Entries : " + (convergenceTest) + " * " + entries.size());
////                logger.warn("OldEntries Size "  + oldSize);
//                logger.warn("=========== OLD ENTRIES AFTER RETAIN ALL ============");
//                for(SearchDescriptor desc : oldEntries)
//                    logger.warn(" DescriptorID : " + desc.getId() + " Descriptor Overlay : " + desc.getOverlayId()+ " Number of Index Entries: " + desc.getNumberOfIndexEntries() + " Age: " + desc.getAge());
//
//                logger.warn("=========== END ==========================");
//                logger.warn("");
////
//                logger.warn("=========== Entries Set =============== ");
//                for(SearchDescriptor desc : entries)
//                    logger.warn(" DescriptorID : " + desc.getId() + " Descriptor Overlay : " + desc.getOverlayId()+ " Number of Index Entries: " + desc.getNumberOfIndexEntries() + " Age: " + desc.getAge());
////
//                logger.warn("=========== END ==========================");
//                logger.warn("");
            }

            currentConvergedRounds = 0;
		}
        if (currentConvergedRounds > convergenceTestRounds) {
            if (!converged) {
                this.changed = true;
            }
            converged = true;
        } else {

//            if(self.getId() == 4041837 && self.getPartitioningType() == VodAddress.PartitioningType.ONCE_BEFORE)
//                logger.warn("OldSize : " + oldSize + " Current Entries Size: " + entries.size() + " Remaining Old Entries: " + oldEntries.size());
            converged = false;
        }
	}

    protected void adjustViewToNewPartitions() {
        PartitionId myPartitionId = new PartitionId(self.getPartitioningType(),
                self.getPartitionIdDepth(), self.getPartitionId());
        PartitionHelper.adjustDescriptorsToNewPartitionId(myPartitionId, entries);
    }

	/**
	 * Return the number most preferred nodes for the given searchDescriptor.
	 * 
	 * @param searchDescriptor
	 *            the searchDescriptor to compare with
	 * @param number
	 *            the maximum number of entries to return
	 * @return a collection of the most preferred nodes
	 */
	protected SortedSet<SearchDescriptor> getExchangeDescriptors(SearchDescriptor searchDescriptor, int number) {
		SortedSet<SearchDescriptor> set = getClosestNodes(searchDescriptor, number);

        // NOTE : remove contract is tied up with the comparator used in the treeset, so in case the {@link SearchDescriptor} has updated utility and the node has a stored one with same address but an outdated utility,
        // so it fails to remove the corresponding descriptor and breaks the normal functioning by duplicacy.


        // Keeping it for safety.
        set.remove(searchDescriptor);

        // DO NOT REMOVE BELOW CHECK  =================
        SearchDescriptor duplicateDescriptor =null;

        for(SearchDescriptor desc : set){
            // Remove The check once the overlay address is removed from the VodAddress.
            if(desc.getVodAddress().getPeerAddress().equals(searchDescriptor.getVodAddress().getPeerAddress())) {
                duplicateDescriptor = desc;
                break;
            }
        }

        // Found the node.
        if(duplicateDescriptor !=null){
            set.remove(duplicateDescriptor);
        }

        // =============================================

        // Even the contains contract also involves the compare method in the Comparator used and therefore in case the search descriptor is present in the entries and we send in the query with same address
        // but updated utility, it is not able to detect any duplicacy and hence problem is created.
        try {
            assert !set.contains(searchDescriptor);
        } catch (AssertionError e) {
            StringBuilder builder = new StringBuilder();
            builder.append(self.getAddress().toString() + " should not include searchDescriptor of the exchange partner " + searchDescriptor.toString());
            builder.append("\n exchange set content:");
            for (SearchDescriptor a : set) {
                builder.append("\n" + a.toString());
            }
            AssertionError error = new AssertionError(builder);
            error.setStackTrace(e.getStackTrace());
            throw error;
        }

        //number - 1 because the source node will be later later
        while (set.size() > (number - 1)) {
            set.remove(set.first());
        }

        //as part of the protocol, the source node should also be added in the set, otherwise
        // message will be discarded on the receiving node

//        if(self.getId() == 726089965 && self.getPartitioningType() == VodAddress.PartitioningType.ONCE_BEFORE){
//            logger.warn(" ========== Pushing Self with number of index entries: "+ self.getNumberOfIndexEntries());
//        }
        set.add(new SearchDescriptor(self.getDescriptor()));

		return set;
	}

	/**
	 * @return all nodes with a higher preference value than self in ascending order
	 */
	protected SortedSet<SearchDescriptor> getHigherUtilityNodes() {
		return entries.tailSet(new SearchDescriptor(self.getDescriptor()));
	}

	/**
	 * @return all nodes with a lower preference value than self in ascending order
	 */
	protected SortedSet<SearchDescriptor> getLowerUtilityNodes() {
		return entries.headSet(new SearchDescriptor(self.getDescriptor()));
	}

	/**
	 * @return a list of all entries in the view
	 */
	protected SortedSet<SearchDescriptor> getAll() {
		return entries;
	}

	/**
	 * @return true if the view is empty
	 */
	public boolean isEmpty() {
		return entries.isEmpty();
	}

	/**
	 * @return the size of the view
	 */
	public int getSize() {
		return entries.size();
	}

	/**
	 * @return true if the convergence criteria are reached for this view
	 */
	public boolean isConverged() {
		return converged;
	}

	/**
	 * @return true if the size limit was reached
	 */
	public boolean isFull() {
		return this.size <= entries.size();
	}

    public boolean isChanged() {
        return changed;
    }

    @Override
	public String toString() {
		StringBuilder builder = new StringBuilder();
		for (SearchDescriptor node : entries) {
			builder.append(node.getVodAddress().getId() + " ");
		}
		return builder.toString();
	};
	
	/**
	 * Increment the age of all descriptors in the view
	 */
	private void incrementDescriptorAges() {
		for (SearchDescriptor descriptor : entries) {
			descriptor.incrementAndGetAge();
		}
	}

	/**
	 * Compare nodes according to their utility. Nodes with smaller IDs but
	 * closer to the base are the best once. Closer nodes are preferred to nodes
	 * further away.
	 */
	private class PreferenceComparator implements Comparator<SearchDescriptor> {
		private SearchDescriptor base;

		public PreferenceComparator(SearchDescriptor base) {
			super();
			this.base = base;
		}

		@Override
		public int compare(SearchDescriptor o1, SearchDescriptor o2) {

            if (utilityComparator.compare(o1, o2) == 0) {
                return 0;
            }

            if (utilityComparator.compare(o1, base) == 0) {
                return 1;
            }

            if (utilityComparator.compare(o2, base) == 0) {
                return -1;
            }

            if (utilityComparator.compare(o1, base) == 1 && utilityComparator.compare(o2, base) == -1) {
				return 1;
			}

            if (utilityComparator.compare(o1, base) == 1 && utilityComparator.compare(o2, base) == 1 && utilityComparator.compare(o1, o2) == 1) {
				return 1;
			}

            if (utilityComparator.compare(o1, base) == -1 && utilityComparator.compare(o2, base) == -1 && utilityComparator.compare(o1, o2) == 1) {
				return 1;
			}
			return -1;
		}
	}
	
	/**
	 * Get a sorted list of the nodes that are the closest to self.
	 * 
	 * @param number
	 *            the maximum number of nodes to return
	 * @return a sorted list of the closest nodes to self
	 */
	private SortedSet<SearchDescriptor> getClosestNodes(int number) {
        // As the number of index entries in self can also change therefore create a new object every time, when doing comparison.
		return getClosestNodes(number, new PreferenceComparator(new SearchDescriptor(self.getDescriptor())));
	}

	/**
	 * Get a sorted list of the nodes that are the closest to the given address.
	 * 
	 * @param address
	 *            the address to compare with
	 * @param number
	 *            the maximum number of nodes to return
	 * @return a sorted list of the closest nodes to the given address
	 */
	private SortedSet<SearchDescriptor> getClosestNodes(SearchDescriptor address, int number) {
		return getClosestNodes(number, new PreferenceComparator(address));
	}

	/**
	 * Get a sorted set of the nodes that are the closest to the given address.
	 *
	 * @param number
	 *            the maximum number of nodes to return
	 * @param c the comparator used for sorting
	 *            the comparator to use
	 * @return a sorted list of the closest nodes to the given address
	 */
	private SortedSet<SearchDescriptor> getClosestNodes(int number, Comparator<SearchDescriptor> c) {
		SortedSet<SearchDescriptor> set = new TreeSet<SearchDescriptor>(c);
        set.addAll(getAll());
        while (set.size() > number) {
            set.remove(set.first());
        }
		return set;
	}
}
