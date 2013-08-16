/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package se.sics.ms.messages;

import java.util.logging.Level;
import java.util.logging.Logger;
import io.netty.buffer.ByteBuf;
import se.sics.gvod.common.VodDescriptor;
import se.sics.gvod.common.msgs.MessageEncodingException;
import se.sics.gvod.common.msgs.DirectMsgNetty;
import se.sics.gvod.net.VodAddress;
import se.sics.gvod.net.msgs.RewriteableMsg;
import se.sics.gvod.net.msgs.RewriteableRetryTimeout;
import se.sics.gvod.net.msgs.ScheduleRetryTimeout;
import se.sics.gvod.net.util.UserTypesEncoderFactory;
import se.sics.gvod.timer.ScheduleTimeout;
import se.sics.gvod.timer.TimeoutId;
import se.sics.ms.exceptions.IllegalSearchString;
import se.sics.ms.net.ApplicationTypesEncoderFactory;
import se.sics.ms.net.MessageFrameDecoder;
import se.sics.ms.timeout.IndividualTimeout;
import se.sics.ms.types.IndexEntry;
import se.sics.ms.types.SearchPattern;

/**
 *
 * @author jdowling
 */
public class SearchMessage {
    public static class Request extends DirectMsgNetty.Request {
        private final SearchPattern pattern;
        private final TimeoutId searchTimeoutId;

        public Request(VodAddress source, VodAddress destination,TimeoutId timeoutId, TimeoutId searchTimeoutId, SearchPattern pattern) {
            super(source, destination, timeoutId);

            if(pattern == null)
                throw new NullPointerException("pattern can't be null");

            this.pattern = pattern;
//            if (query.length() > 255) {
//                throw new IllegalSearchString("Search string is too long. Max length is 255 chars.");
//            }

            this.searchTimeoutId = searchTimeoutId;
        }

        public SearchPattern getPattern() {
            return pattern;
        }

        public TimeoutId getSearchTimeoutId() {
            return searchTimeoutId;
        }

        @Override
        public int getSize() {
            return getHeaderSize() + 30; // guess at length of query
        }

        @Override
        public RewriteableMsg copy() {
            SearchMessage.Request r = null;
            r = new Request(vodSrc, vodDest, timeoutId, searchTimeoutId, pattern);
            return r;
        }

        @Override
        public ByteBuf toByteArray() throws MessageEncodingException {
            ByteBuf buffer = createChannelBufferWithHeader();
            ApplicationTypesEncoderFactory.writeSearchPattern(buffer, pattern);
            UserTypesEncoderFactory.writeTimeoutId(buffer, searchTimeoutId);
            return buffer;
        }

        @Override
        public byte getOpcode() {
            return MessageFrameDecoder.SEARCH_REQUEST;
        }
    }

    public static class Response extends DirectMsgNetty.Response {
        
        public static final int MAX_RESULTS_STR_LEN = 1400;

        private final IndexEntry[] results;
        private final int numResponses;
        private final int responseNumber;
        private final TimeoutId searchTimeoutId;
        
        public Response(VodAddress source, VodAddress destination, TimeoutId timeoutId, TimeoutId searchTimeoutId, int numResponses, int responseNumber, IndexEntry[] results) throws IllegalSearchString {
            super(source, destination, timeoutId);

            if(results == null)
                throw new NullPointerException("results can't be null");

            this.numResponses = numResponses;
            this.responseNumber = responseNumber;
            this.results = results;
            this.searchTimeoutId = searchTimeoutId;
        }

        public IndexEntry[] getResults() {
            return results;
        }

        public int getResponseNumber() {
            return responseNumber;
        }

        public int getNumResponses() {
            return numResponses;
        }

        public TimeoutId getSearchTimeoutId() {
            return searchTimeoutId;
        }

        @Override
        public int getSize() {
            return getHeaderSize()
                    + 4 // numResponses
                    + 4 // responseNum
                    + MAX_RESULTS_STR_LEN; 
        }

        @Override
        public RewriteableMsg copy() {
            try {
                return new SearchMessage.Response(vodSrc, vodDest, timeoutId, searchTimeoutId, numResponses, responseNumber, results);
            } catch (IllegalSearchString ex) {
                // we can swallow the exception because the original object should 
                // have been correctly constructed.
                Logger.getLogger(SearchMessage.class.getName()).log(Level.SEVERE, null, ex);
            }
            // shouldn't get here.
            return null;
        }

        @Override
        public ByteBuf toByteArray() throws MessageEncodingException {
            ByteBuf buffer = createChannelBufferWithHeader();
            UserTypesEncoderFactory.writeUnsignedintAsOneByte(buffer, numResponses);
            UserTypesEncoderFactory.writeUnsignedintAsOneByte(buffer, responseNumber);
            ApplicationTypesEncoderFactory.writeIndexEntryArray(buffer, results);
            UserTypesEncoderFactory.writeTimeoutId(buffer, searchTimeoutId);
            return buffer;
        }

        @Override
        public byte getOpcode() {
            return MessageFrameDecoder.SEARCH_RESPONSE;

        }
    }

    /**
     * Timeout for active {@link se.sics.ms.messages.SearchMessage.Request}s.
     */
    public static class RequestTimeout extends IndividualTimeout {
        private final VodDescriptor vodDescriptor;

        /**
         * @param request
         *            the ScheduleTimeout that holds the Timeout
         */
        public RequestTimeout(ScheduleTimeout request, int id, VodDescriptor vodDescriptor) {
            super(request, id);
            this.vodDescriptor = vodDescriptor;
        }

        public VodDescriptor getVodDescriptor() {
            return vodDescriptor;
        }
    }
}