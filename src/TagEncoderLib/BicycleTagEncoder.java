/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package TagEncoderLib;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.HashMap;

/**
 *
 * @author ovoloshchuk
 */
public class BicycleTagEncoder {
    
    public static class UnknownFormatException extends Exception {
        
    }

    public static int BUFFER_SIZE = 4096;

    public enum Tag {
        Artist,
        Album,
        Title,
        Year,
        Comment,
        Genre;
    }

    public enum TagVersion {
        ID3V1,
        ID3V2
    }
    
    public static class TagData {
        public TagVersion version;
        public byte[] data;
        
        public TagData(TagVersion version, byte[] data) {
            this.version = version;
            this.data = data;
        }
    }

    public static byte[] readFile(InputStream is) throws IOException {
        int nFileSize = is.available();
        byte[] buf = new byte[nFileSize];
        is.read(buf);
        return buf;
    }

    private static TagData parseTagVersion(InputStream is) throws IOException {
        byte[] first_3 = new byte[3];
        
        is.read(first_3);
        if (new String(first_3).equals("ID3")) {
            is.skip(3);
            byte[] baFrames = ID3V2Encoder.getFrames(is);
            return new TagData(TagVersion.ID3V2, baFrames);
        }
        //stream should be at byte 2 now, counting from 0
        
        int nBytesLeft = is.available();
        byte[] baHeader = new byte[128];
        is.skip(nBytesLeft - 128);
        is.read(baHeader);
        if (new String(baHeader).substring(0, 3).equals("TAG"))
            return new TagData(TagVersion.ID3V1, baHeader);
        return null;
    }

    public static void updateTagValue(InputStream is, OutputStream os, Tag tag, String value) throws IOException, UnknownFormatException {
        updateTags(is, os, new Tag[]{tag}, new String[]{value});
    }
    
    
    public static void updateTags(InputStream is, OutputStream os, Tag[] tags, String[] values) throws IOException {;
        ID3V2Encoder.updateTags(is, os, tags, values);
    }
    
    public static HashMap<Tag, String> getTags(InputStream is, String sCharsetName) throws IOException, UnknownFormatException {
        TagData data = parseTagVersion(is);
        switch(data.version) {
            case ID3V1:
                return ID3V1Encoder.getTags(data.data, sCharsetName);
            case ID3V2:
                return ID3V2Encoder.getTags(data.data, sCharsetName);
        }
        throw new UnknownFormatException();
    }
    
    public static void convertV1ToV2(InputStream is, OutputStream os, String sCharsetName) throws IOException {
        File fTemp = File.createTempFile("tagEncoder", "dataOnly");
        OutputStream tmpOs = new FileOutputStream(fTemp);
        
        int nBufferSize = 1<<20;
        int nReadCount = 0;
        byte[] buf = new byte[nBufferSize];
        while (is.available() > 128) {  //While we haven't reached the last 128 bytes, the header
            //If the last 128 bytes are closer then buffer size, read until them
            nReadCount = is.read(buf, 0, Math.min(nBufferSize, is.available()-128));
            tmpOs.write(buf, 0, nReadCount);
        }
        tmpOs.close();
        
        byte[] baHeader = new byte[125];    //ID3V1 header
        is.skip(3); //Skip "TAG"
        is.read(baHeader);
        
        HashMap<Tag, String> tags = ID3V1Encoder.getTags(baHeader, sCharsetName);
        baHeader = ID3V2Encoder.createHeader(tags);
        
        os.write(baHeader);
        
        InputStream tmpIs = new FileInputStream(fTemp);
        while ((nReadCount = tmpIs.read(buf)) != -1)
            os.write(buf);
        tmpIs.close();
        fTemp.delete();
    }
}
