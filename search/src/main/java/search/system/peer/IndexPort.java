package search.system.peer;

import se.sics.kompics.Event;
import se.sics.kompics.PortType;

import common.entities.IndexEntry;

public class IndexPort extends PortType {
	{
		positive(AddIndexSimulated.class);
	}

	public static final class AddIndexSimulated extends Event {
		private final IndexEntry entry;

		public AddIndexSimulated(IndexEntry entry) {
			this.entry = entry;
		}

		public IndexEntry getEntry() {
			return entry;
		}
	}
}
