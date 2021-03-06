package se.sics.ms.gradient.ports;

import se.sics.kompics.Event;
import se.sics.kompics.PortType;
import se.sics.ms.types.PeerDescriptor;

import java.util.SortedSet;

/**
 * This class is a port created for the purpose of broadcasting gradient's view
 */
public class GradientViewChangePort extends PortType {
	{
		negative(GradientViewChanged.class);
	}

	/**
	 * An event that contains the Gradient's view
	 */
	public static class GradientViewChanged extends Event {
        
		private final boolean isConverged;
		private final SortedSet<PeerDescriptor> gradientView;

		/**
		 * Default constructor
		 * 
		 * @param isConverged
		 *            true if the node's view has converged
		 * @param gradientView
		 *            the current view of the gradient
		 */
		public GradientViewChanged(boolean isConverged, SortedSet<PeerDescriptor> gradientView) {
			super();
			this.isConverged = isConverged;
			this.gradientView = gradientView;
		}

		/**
		 * Getter for converged
		 * 
		 * @return true if the view has converged
		 */
		public boolean isConverged() {
			return this.isConverged;
		}

        /**
         * The set is backed by the view of this event so changes to one of them affects the other
         *
         * @return all nodes in the view
         */
        public SortedSet<PeerDescriptor> getGradientView() {
            return gradientView;
        }
	}
}
