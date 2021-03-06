package se.sics.ms.common;

import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.Sort;
import org.apache.lucene.search.TopDocsCollector;
import org.apache.lucene.store.Directory;
import se.sics.ms.common.LuceneAdaptorBasic;
import se.sics.ms.common.LuceneAdaptorException;
import se.sics.ms.types.ApplicationEntry;
import se.sics.ms.util.EntryScorePair;
import se.sics.ms.util.IdScorePair;

import java.util.Collection;
import java.util.List;

/**
 * Specific Adaptor Class for dealing with the Application Entries.
 *
 * Created by babbar on 2015-05-02.
 */
public abstract class ApplicationLuceneAdaptor extends LuceneAdaptorBasic {


    public ApplicationLuceneAdaptor(Directory directory, IndexWriterConfig config) {
        super(directory, config);
    }

    /**
     * Search for Index Entries in Lucene, based on the provided query and the collector instance.
     *
     * @param searchQuery query used to search.
     * @param collector Collector supplied for query.
     * @return List of Index Entries
     * @throws se.sics.ms.common.LuceneAdaptorException
     */
    public abstract List<ApplicationEntry> searchApplicationEntriesInLucene(Query searchQuery, TopDocsCollector collector) throws LuceneAdaptorException;


    /**
     * Search for Index Entries in Lucene, based on the provided query and the collector instance.
     *
     * @param searchQuery query used to search.
     * @param sort Sort defined for search
     * @param maxEntries maximum entries returned by Lucene.
     * @return List of Index Entries
     * @throws LuceneAdaptorException
     */
    public abstract List<ApplicationEntry> searchApplicationEntriesInLucene(Query searchQuery, Sort sort , int maxEntries) throws LuceneAdaptorException;


    /**
     * The Application in event of sharding needs to calculate the medianId at which the split occurs.
     * The method is used to calculate the splitting point. Based on the query,
     *
     * @param searchQuery Query to fetch entries
     * @param sort Fields to sort on.
     * @return Middle Entry
     * @throws LuceneAdaptorException
     */
    public abstract ApplicationEntry getMedianEntry(Query searchQuery, Sort sort, int maxEntries) throws LuceneAdaptorException;

    /**
     * In order to calculate the time to shard, we have to estimate the entries in the system.
     * As in addition to the actual index entries, we also have landing entries
     * therefore the size of Instance is inflated. This method allows to capture the 
     * actual size of instance in terms of entries added by users and not by system.
     *
     * @return
     */
    public abstract int getActualSizeOfInstance() throws LuceneAdaptorException;


    /**
     * Count the application entries in the lucene instance.
     *
     * @return Application Entry count.
     * @throws LuceneAdaptorException
     */
    public abstract int getApplicationEntrySize() throws LuceneAdaptorException;


    /**
     * Based on the query, search for the documents. In addition to this construct the
     * collection of the document identifier and the score of the match.
     *
     * @return IdScorePair Collection.
     * @throws LuceneAdaptorException
     */
    public abstract List<IdScorePair> getIdScoreCollection(Query searchQuery, TopDocsCollector collector) throws LuceneAdaptorException;


    /**
     * Get the actual entry from the Data storage based on the identifier of the entry.
     *
     * @param entryId entry identifier.
     * @return Application Entry
     * @throws LuceneAdaptorException
     */
    public abstract ApplicationEntry getApplicationEntry(ApplicationEntry.ApplicationEntryId entryId) throws LuceneAdaptorException;


    /**
     * Based on the collection of the application entry identifiers, fetch the actual entry information
     * from the Lucene
     *
     * @param entryIds entryId collection
     * @return Collection Application Entries.
     *
     * @throws LuceneAdaptorException
     */
    public abstract List<ApplicationEntry> getApplicationEntries(Collection<ApplicationEntry.ApplicationEntryId> entryIds) throws LuceneAdaptorException;


    /**
     * Based on the collection of the id score pairs passed to the application,
     * application will fetch the entries from data store.
     *
     * @param scorePairs
     * @return
     * @throws LuceneAdaptorException
     */
    public abstract List<EntryScorePair> getEntryScorePairs(Collection<IdScorePair> scorePairs) throws LuceneAdaptorException;

}
