package se.sics.ms.gradient;

import se.sics.gvod.common.VodDescriptor;
import java.util.Comparator;

/**
 * Compare nodes according to their utility.
 *
 * @author: Steffen Grohsschmiedt
 */
public class UtilityComparator implements Comparator<VodDescriptor> {

    @Override
    public int compare(VodDescriptor o1, VodDescriptor o2) {

            if (o1.getId() == o2.getId()) {
                return 0;
            } else if (o1.getId() < o2.getId()) {
                return 1;
            }
            return -1;
//        if (o1.getNumberOfIndexEntries() == o2.getNumberOfIndexEntries()) {
//            if (o1.getId() == o2.getId()) {
//                return 0;
//            } else if (o1.getId() < o2.getId()) {
//                return 1;
//            }
//            return -1;
//        } else if (o1.getNumberOfIndexEntries() > o2.getNumberOfIndexEntries()) {
//            return 1;
//        }
//        return -1;
    }
}