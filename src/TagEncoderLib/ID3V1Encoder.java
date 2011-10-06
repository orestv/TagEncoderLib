/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package TagEncoderLib;

import TagEncoderLib.BicycleTagEncoder.Tag;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.HashMap;

/**
 *
 * @author ovoloshchuk
 */
public class ID3V1Encoder {
    
    public static HashMap<Tag, String> getTags(byte[] data) throws IOException {
        return getTags(data, "ISO8859-1");
    }
    
    //data should be the last 125 bytes of the file
    public static HashMap<Tag, String> getTags(byte[] data, String sCharsetName) throws IOException {
        HashMap<Tag, String> tags = new HashMap<Tag, String>();
        byte[] baTags = getTagBytes(data);

        InputStream is = new ByteArrayInputStream(baTags);        

        tags.put(Tag.Title, readTag(is, Tag.Title, sCharsetName));
        tags.put(Tag.Artist, readTag(is, Tag.Artist, sCharsetName));
        tags.put(Tag.Album, readTag(is, Tag.Album, sCharsetName));
        tags.put(Tag.Year, readTag(is, Tag.Year, sCharsetName));
        tags.put(Tag.Comment, readTag(is, Tag.Comment, sCharsetName));

        return tags;
    }
    
    private static int getTagLength(Tag tag) {
        switch(tag) {
            case Artist:
            case Album:
            case Title:
            case Comment:
                return 30;
            case Year:
                return 4;
        }
        return 30;
    }
    
    private static String readTag(InputStream is, Tag tag, String sCharsetName) throws IOException {
        int nLength = getTagLength(tag);
        byte[] buf = new byte[nLength];
        is.read(buf);
        String sResult = new String(buf, sCharsetName).trim(); 
        return sResult;
    }
    
    private static byte[] getTagBytes(byte[] data) throws IOException {
        InputStream is = new ByteArrayInputStream(data, data.length - 128 + 3, 128);
        byte[] baTags = new byte[125];
        is.read(baTags);
        return baTags;
    }
    
    public static byte[] stripTag(byte[] data) {
        ByteArrayOutputStream bos = new ByteArrayOutputStream(data.length-128);
        bos.write(data, 0, data.length-128);
        return bos.toByteArray();
    }
}
