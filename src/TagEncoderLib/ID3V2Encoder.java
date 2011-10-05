/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package TagEncoderLib;

import TagEncoderLib.BicycleTagEncoder.Tag;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.logging.StreamHandler;

/**
 *
 * @author ovoloshchuk
 */
public class ID3V2Encoder {


    private static String getCode(Tag tag) {
        switch (tag) {
            case Artist:
                return "TPE1";
            case Album:
                return "TALB";
            case Title:
                return "TIT2";
            case Year:
                return "TDRC";
            case Comment:
                return "COMM";
        }
        return "";
    }

    private static byte[] getTagBytes(String value, Tag tag) throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        bos.write(3);
        switch (tag) {
            case Artist:
            case Album:
            case Title:
                bos.write(value.getBytes("UTF-8"));
                break;
            case Comment:
                bos.write("eng".getBytes());
                bos.write(0);
                bos.write(value.getBytes("UTF-8"));
                break;
            case Year:
                bos.write(value.getBytes("UTF-8"));
                break;
        }
        return bos.toByteArray();
    }

    private static Tag getTagType(String code) {
        Tag[] tags = Tag.values();
        for (int i = 0; i < tags.length; i++) {
            if (code.equals(getCode(tags[i]))) {
                return tags[i];
            }
        }
        return null;
    }

    
    public static byte[] updateTags(InputStream bis, Tag[] tags, String[] values) throws IOException {

        byte[] baEmpty = new byte[]{0, 0, 0, 0};

        //ByteArrayInputStream bis = new ByteArrayInputStream(data);
        ByteArrayOutputStream bos = new ByteArrayOutputStream(bis.available());
        //ID3, version, flags
        for (int i = 0; i < 6; i++) 
            bos.write(bis.read()); 
        
        byte[] baHeaderSize = new byte[4];
        bis.read(baHeaderSize);
        int nHeaderSize = desynchronizeIntegerValue(baHeaderSize);

        byte[] baFrameName = new byte[4];

        bis.read(baFrameName);
        //headerHeader + first frame name
        int nPosition = 10 + 4;
        byte[] baFrameLength = new byte[4];
        byte[] baFrameFlags = new byte[2];
        
        bos.write(baEmpty);

        while (nPosition < nHeaderSize && !Arrays.equals(baFrameName, baEmpty)) {
            bos.write(baFrameName);
            String sTagName = new String(baFrameName);
            Tag tag = getTagType(sTagName);
            String sTagValue = null;
            for (int i = 0; tag != null && i < tags.length; i++) {
                if (tags[i] == tag)
                    sTagValue = values[i];
            }
            bis.read(baFrameLength);
            bis.read(baFrameFlags);
            //length + flags + encoding
            nPosition += 4 + 2 + 1;
            int nFrameLength = desynchronizeIntegerValue(baFrameLength);
            nPosition += nFrameLength;
            if (sTagValue != null) {
                byte[] baFrameValue = getTagBytes(sTagValue, getTagType(sTagName));
                bis.skip(nFrameLength);
                nPosition += nFrameLength;

                nFrameLength = baFrameValue.length;
                bos.write(synchronizeIntegerValue(nFrameLength));
                bos.write(0);
                bos.write(0);
                bos.write(baFrameValue);
            } else {
                bos.write(baFrameLength);
                bos.write(baFrameFlags);
                
                //Read/write the frame value
                for (int i = 0; i < nFrameLength; i++) {
                    bos.write(bis.read());
                }                
                nPosition += nFrameLength;
            }

            bis.read(baFrameName);
            nPosition += 4;
        }
        bos.write(baFrameName);
        
        int nReadValue = 0;
        //Move through padding
        while (nReadValue == 0)
            nReadValue = bis.read();
        //The first byte that's not padding
        bos.write(nReadValue);
        
        byte[] baHeader = bos.toByteArray();
        baHeaderSize = synchronizeIntegerValue(baHeader.length - 11);        
        
        byte[] buf = new byte[8192];
        int nReadCount = 0;
        while ((nReadCount = bis.read(buf)) != -1)
            bos.write(buf, 0, nReadCount);
        //bos.write(data, nPosition, data.length-nPosition-1);
        byte[] ret = bos.toByteArray();
        for (int i = 0; i < 4; i++) {
            ret[i + 6] = baHeaderSize[i];
        }

        return ret;
    }

    public static byte[] appendHeader(byte[] data, HashMap<Tag, String> tags) throws UnsupportedEncodingException, IOException {
        byte[] header = createHeader(tags);
        ByteArrayOutputStream bos = new ByteArrayOutputStream(header.length + data.length);
        bos.write(header);
        bos.write(data);
        return bos.toByteArray();
    }

    private static byte[] createHeader(HashMap<Tag, String> tags) throws UnsupportedEncodingException, IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();

        //Tag header - ID3v2.3.0 and flags (none set)
        byte[] baHeaderHeader = new byte[]{'I', 'D', '3', 4, 0, 0};

        Iterator<Tag> iter = tags.keySet().iterator();
        while (iter.hasNext()) {
            Tag tag = iter.next();
            String value = tags.get(tag);
            byte[] baValue = getTagBytes(value, tag);
            byte[] baFrameLength = synchronizeIntegerValue(baValue.length);

            //TODO: Find out whether encoding specification is needed here.
            bos.write(getCode(tag).getBytes());
            bos.write(baFrameLength);
            bos.write(new byte[]{0, 0});
            bos.write(baValue);
        }
        int nHeaderSize = bos.size();
        byte[] baHeaderSize = synchronizeIntegerValue(nHeaderSize);
        byte[] baHeader = bos.toByteArray();
        ByteArrayOutputStream bos_result = new ByteArrayOutputStream(nHeaderSize);

        bos_result.write(baHeaderHeader);
        bos_result.write(baHeaderSize);
        bos_result.write(baHeader);
        return bos_result.toByteArray();
    }

    public static byte[] synchronizeIntegerValue(int value) {
        byte[] result = new byte[4];

        for (int i = 0; i < 4; i++) {
            if (i > 0) {
                value = value >> 1 * 7;
            }
            result[3 - i] = (byte) ((byte) value & 127);
        }

        return result;
    }

    public static int desynchronizeIntegerValue(byte[] bt) {
        int nResult = (int) bt[bt.length - 1];

        for (int i = 1; i < bt.length; i++) {
            int nIndex = (bt.length - i - 1);
            if (i > 0) {
                nResult += ((int) bt[nIndex]) << (i * 7);
            }
        }
        return nResult;
    }

    public static HashMap<Tag, String> getTags(byte[] data, String sCharsetName) throws IOException {
        HashMap<Tag, String> hmResult = new HashMap<Tag, String>();
        
        InputStream isTagsLength = new ByteArrayInputStream(data, 6, 4);
        byte[] baHeaderLength = new byte[4];
        isTagsLength.read(baHeaderLength);
        int nHeaderLength = desynchronizeIntegerValue(baHeaderLength);

        InputStream is = new ByteArrayInputStream(data, 10, nHeaderLength);
        while (is.available() > 0) {
            byte[] baTagName = new byte[4];
            byte[] baTagLength = new byte[4];
            is.read(baTagName);
            is.read(baTagLength);

            //Technical value - some flags and encoding specification
            is.skip(3);
            int nTagLength = desynchronizeIntegerValue(baTagLength) - 1;
            if (nTagLength <= 0) {
                break;
            }
            byte[] baTagValue = new byte[nTagLength];
            is.read(baTagValue);

            String sName, sValue;
            sName = new String(baTagName);
            Tag tag = getTagType(sName);
            if (tag != null) {
                sValue = new String(baTagValue, sCharsetName);
                hmResult.put(tag, sValue);
            }
        }

        return hmResult;
    }

    //is is moved by 10 bytes
    private static byte[] getHeaderHeaderBytes(InputStream is) throws IOException {
        //Get ID3 header - 10 bytes
        byte[] baHeader = new byte[10];
        is.read(baHeader);
        return baHeader;
    }

    //nHeaderLength contains FULL header length, including the first 10 bytes of headerheader
    private static byte[] getHeaderBytes(InputStream is, int nHeaderLength) throws IOException {
        byte[] baTags = new byte[nHeaderLength - 10];
        is.read(baTags);
        return baTags;
    }
}
