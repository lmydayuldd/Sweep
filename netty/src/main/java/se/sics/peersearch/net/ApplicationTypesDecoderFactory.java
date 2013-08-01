/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package se.sics.peersearch.net;

import io.netty.buffer.ByteBuf;
import org.apache.commons.codec.binary.Base64;
import se.sics.gvod.common.msgs.MessageDecodingException;
import se.sics.gvod.net.VodAddress;
import se.sics.gvod.net.util.UserTypesDecoderFactory;
import se.sics.peersearch.types.IndexEntry;
import se.sics.peersearch.types.SearchPattern;
import sun.misc.BASE64Decoder;

import java.io.IOException;
import java.security.KeyFactory;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.X509EncodedKeySpec;
import java.util.Date;

/**
 *
 * @author jdowling
 */
public class ApplicationTypesDecoderFactory {

    public static IndexEntry readIndexEntry(ByteBuf buffer)
            throws MessageDecodingException {
        Long id = buffer.readLong();
        String url = UserTypesDecoderFactory.readStringLength256(buffer);
        String fileName = UserTypesDecoderFactory.readStringLength256(buffer);
        Long fileSize = buffer.readLong();
        Date uploaded =  new Date(buffer.readLong());
        String language = UserTypesDecoderFactory.readStringLength256(buffer);
        IndexEntry.Category category = IndexEntry.Category.values()[buffer.readInt()];
        String description = UserTypesDecoderFactory.readStringLength65536(buffer);
        String hash = UserTypesDecoderFactory.readStringLength256(buffer);
        String leaderId = UserTypesDecoderFactory.readStringLength65536(buffer);
        if (leaderId == null)
            return new IndexEntry(id, url, fileName, fileSize, uploaded, language, category, description, hash, null);

        KeyFactory keyFactory;
        PublicKey pub = null;
        try {
            keyFactory = KeyFactory.getInstance("RSA");
            byte[] decode = Base64.decodeBase64(leaderId.getBytes());
            X509EncodedKeySpec publicKeySpec = new X509EncodedKeySpec(decode);
            pub = keyFactory.generatePublic(publicKeySpec);
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
        } catch (InvalidKeySpecException e) {
            e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
        }

        return new IndexEntry(id, url, fileName, fileSize, uploaded, language, category, description, hash, pub);
    }

    public static IndexEntry[] readIndexEntryArray(ByteBuf buffer) throws MessageDecodingException {
        int len = UserTypesDecoderFactory.readUnsignedIntAsTwoBytes(buffer);
        IndexEntry[] items = new IndexEntry[len];
        for (int i = 0; i < len; i++) {
            items[i] = readIndexEntry(buffer);
        }
        return items;
    }

    public static Long[] readLongArray(ByteBuf buffer) {
        int len = UserTypesDecoderFactory.readUnsignedIntAsTwoBytes(buffer);
        Long[] items = new Long[len];
        for (int i = 0; i < len; i++) {
            items[i] = buffer.readLong();
        }
        return items;
    }

    public static VodAddress[] readVodAddressArray(ByteBuf buffer) throws MessageDecodingException {
        int len = UserTypesDecoderFactory.readUnsignedIntAsTwoBytes(buffer);
        VodAddress[] items = new VodAddress[len];
        for (int i = 0; i < len; i++) {
            items[i] = UserTypesDecoderFactory.readVodAddress(buffer);
        }
        return items;
    }

    public static SearchPattern readSearchPattern(ByteBuf buffer) throws MessageDecodingException {
        String fileNamePattern = UserTypesDecoderFactory.readStringLength256(buffer);
        int minFileSize = buffer.readInt();
        int maxFileSize = buffer.readInt();
        Date minUploadDate = new Date(buffer.readLong());
        Date maxUploadDate = new Date(buffer.readLong());
        String language = UserTypesDecoderFactory.readStringLength256(buffer);
        IndexEntry.Category category = IndexEntry.Category.values()[buffer.readInt()];
        String descriptionPattern = UserTypesDecoderFactory.readStringLength65536(buffer);

        return new SearchPattern(fileNamePattern, minFileSize, maxFileSize, minUploadDate, maxUploadDate, language, category, descriptionPattern);
    }
}
