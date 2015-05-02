package se.sics.ms.data;

import org.apache.lucene.document.*;
import se.sics.ms.types.IndexEntry;

/**
 * Composite Object keeping track of entries added in the system and other important metadata associated
 * with the entry added.
 *
 * @author babbar
 */
public class ApplicationEntry {

    public static String EPOCH_ID = "epochId";
    public static String LEADER_ID = "leaderId";
    public static String ENTRY_ID = "entryId";

    private long epochId;
    private int leaderId;
    private long entryId;

    private IndexEntry entry;


    public ApplicationEntry(long epochId, int leaderId, long entryId){

        this.epochId = epochId;
        this.leaderId =leaderId;
        this.entryId = entryId;
        this.entry = IndexEntry.DEFAULT_ENTRY;
    }

    public ApplicationEntry(long epochId, int leaderId, long entryId, IndexEntry entry){

        this.epochId = epochId;
        this.leaderId = leaderId;
        this.entryId = entryId;
        this.entry = entry;
    }

    @Override
    public String toString() {
        return "ApplicationEntry{" +
                "epochId=" + epochId +
                ", leaderId=" + leaderId +
                ", entryId=" + entryId +
                ", entry=" + entry +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ApplicationEntry)) return false;

        ApplicationEntry that = (ApplicationEntry) o;

        if (entryId != that.entryId) return false;
        if (epochId != that.epochId) return false;
        if (leaderId != that.leaderId) return false;
        if (entry != null ? !entry.equals(that.entry) : that.entry != null) return false;

        return true;
    }

    @Override
    public int hashCode() {
        int result = (int) (epochId ^ (epochId >>> 32));
        result = 31 * result + leaderId;
        result = 31 * result + (int) (entryId ^ (entryId >>> 32));
        result = 31 * result + (entry != null ? entry.hashCode() : 0);
        return result;
    }

    public long getEpochId() {
        return epochId;
    }

    public int getLeaderId() {
        return leaderId;
    }

    public long getEntryId() {
        return entryId;
    }

    public IndexEntry getEntry() {
        return entry;
    }



    public static class ApplicationEntryHelper {


        /**
         * Given an instance of document, generate an instance of Entry.
         * @param d Document
         * @return Application Entry.
         */
        public static ApplicationEntry createApplicationEntryFromDocument(Document d){

            long epochId = Long.valueOf(d.get(ApplicationEntry.EPOCH_ID));
            int leaderId = Integer.valueOf(d.get(ApplicationEntry.LEADER_ID));
            long entryId = Long.valueOf(d.get(ApplicationEntry.ENTRY_ID));
            IndexEntry entry = IndexEntry.IndexEntryHelper.createIndexEntry(d);

            return new ApplicationEntry(epochId, leaderId, entryId, entry);
        }


        /**
         * Given an instance of Document, write the application entry to that document and return the
         * instance.
         *
         * @param doc Document
         * @param applicationEntry Application Entry.
         * @return Document
         */
        public static Document createDocumentFromEntry(Document doc, ApplicationEntry applicationEntry) {

            IndexEntry entry = applicationEntry.getEntry();

            // Storing Application Entry Data.
            doc.add(new LongField(ApplicationEntry.EPOCH_ID, applicationEntry.getEpochId(), Field.Store.YES));
            doc.add(new IntField(ApplicationEntry.LEADER_ID, applicationEntry.getLeaderId(), Field.Store.YES));
            doc.add(new LongField(ApplicationEntry.ENTRY_ID, applicationEntry.getEntryId(), Field.Store.YES));

            // Storing Index Entry Data.
            doc = IndexEntry.IndexEntryHelper.addIndexEntryToDocument(doc, entry);
            return doc;
        }
    }

}
