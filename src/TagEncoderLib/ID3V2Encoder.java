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

/**
 *
 * @author ovoloshchuk
 */
public class ID3V2Encoder extends AbstractTagEncoder {

    public ID3V2Encoder(byte[] data) {
        super(data);
    }

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

    @Override
    public void updateTagValue(OutputStream os, Tag tag, String value) throws IOException {
        InputStream is = new ByteArrayInputStream(data);
        byte[] baHeaderHeader = getHeaderHeaderBytes(is);
        DataInputStream dis = new DataInputStream(new ByteArrayInputStream(baHeaderHeader, 6, 4));
        byte[] baHeaderLength = new byte[4];
        dis.read(baHeaderLength);
        int nOldHeaderLength = desynchronizeIntegerValue(baHeaderLength);

        byte[] baHeader = getHeaderBytes(is, nOldHeaderLength);

        int nTagNameIndex = 0;
        boolean bTagFound = false;
        int nTagStartIndex = 0;
        for (nTagStartIndex = 0; nTagStartIndex < baHeader.length; nTagStartIndex++) {
            if ((char) baHeader[nTagStartIndex] == getCode(tag).charAt(nTagNameIndex)) {
                nTagNameIndex++;
            } else {
                nTagNameIndex = 0;
            }
            if (nTagNameIndex >= getCode(tag).length()) {
                nTagStartIndex++;
                bTagFound = true;
                break;
            }
        }
        if (bTagFound) {
            System.out.println("Tag found at position " + Integer.toString(nTagStartIndex));
        } else {
            System.out.println("Tag not found!");
        }
        ByteArrayInputStream bis = new ByteArrayInputStream(baHeader, nTagStartIndex, nOldHeaderLength - nTagStartIndex);

        byte[] baOldTagLength = new byte[4];
        bis.read(baOldTagLength);
        int nOldTagLength = desynchronizeIntegerValue(baOldTagLength);
        byte[] baTagFlags = new byte[2];
        bis.read(baTagFlags);
        byte[] baNewTagValue = value.getBytes("UTF-16");
        int nNewTagLength = baNewTagValue.length + 1;

        int nNewHeaderLength = baHeader.length + (nNewTagLength - nOldTagLength);

        //4 - tag length, 3 - tag flags
        int nNextTagIndex = nTagStartIndex + 4 + 2 + nOldTagLength;

        ByteArrayOutputStream bos = new ByteArrayOutputStream(nNewHeaderLength);

        bos.write(baHeaderHeader, 0, 6);
        bos.write(synchronizeIntegerValue(nNewHeaderLength));
        bos.write(baHeader, 0, nTagStartIndex);
        bos.write(synchronizeIntegerValue(nNewTagLength));
        bos.write(baTagFlags);
        //UTF-16 mark
        bos.write(1);
        bos.write(baNewTagValue);
        bos.write(baHeader, nNextTagIndex, nOldHeaderLength - nNextTagIndex - 10);
        bos.writeTo(os);

        byte[] buf = new byte[1048576];
        int nReadCount = 0;
        while ((nReadCount = is.read(buf)) != -1) {
            os.write(buf, 0, nReadCount);
        }
        is.close();
        os.close();
    }

    public static byte[] updateTags(byte[] data, Tag[] tags, String[] values) throws IOException {

        byte[] baEmpty = new byte[]{0, 0, 0, 0};

        ByteArrayInputStream bis = new ByteArrayInputStream(data);
        ByteArrayOutputStream bos = new ByteArrayOutputStream(data.length);
        //ID3, version, flags
        bos.write(data, 0, 10);
        
        byte[] baHeaderSize = new byte[4];        
        bis.skip(6);
        bis.read(baHeaderSize);
        int nHeaderSize = desynchronizeIntegerValue(baHeaderSize);

        byte[] baFrameName = new byte[4];

        bis.read(baFrameName);
        //headerHeader + first frame name
        int nPosition = 10 + 4;
        byte[] baFrameLength = new byte[4];
        byte[] baFrameFlags = new byte[2];

        while (nPosition < nHeaderSize && !Arrays.equals(baFrameName, baEmpty)) {
            bos.write(baFrameName);
            String sTagName = new String(baFrameName);
            Tag tag = getTagType(sTagName);
            String sTagValue = null;
            for (int i = 0; i < tags.length; i++) {
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
                byte[] baFrameValue = new byte[nFrameLength];
                bis.read(baFrameValue);
                nPosition += nFrameLength;
                bos.write(baFrameValue);
            }

            bis.read(baFrameName);
            nPosition += 4;
        }
        
        byte[] baHeader = bos.toByteArray();
        baHeaderSize = synchronizeIntegerValue(baHeader.length - 10);
        
        bos.write(data, nPosition, data.length-nPosition);
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
            byte[] baValue2 = value.getBytes("UTF-16");
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

    public static HashMap<Tag, String> getTags(byte[] baTags, String sCharsetName) throws IOException {
        HashMap<Tag, String> hmResult = new HashMap<Tag, String>();

        DataInputStream dis = new DataInputStream(new ByteArrayInputStream(baTags));
        while (true) {
            if (dis.available() == 0) {
                break;
            }
            byte[] baTagName = new byte[4];
            byte[] baTagLength = new byte[4];
            dis.read(baTagName);
            dis.read(baTagLength);

            //Technical value - some flags and encoding specification
            dis.skip(3);
            int nTagLength = desynchronizeIntegerValue(baTagLength) - 1;
            if (nTagLength <= 0) {
                break;
            }
            byte[] baTagValue = new byte[nTagLength];
            dis.read(baTagValue);

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

    //moves input stream to the end of the header
    private static byte[] getHeaderBytes(InputStream is) throws IOException {
        byte[] baHeader = getHeaderHeaderBytes(is);

        //Get last 4 bytes of the header - ID3 header length.
        DataInputStream dis = new DataInputStream(new ByteArrayInputStream(baHeader, 6, 4));
        byte[] baHeaderLength = new byte[4];
        dis.read(baHeaderLength);
        //Parse synchronized int.
        int nHeaderLength = desynchronizeIntegerValue(baHeaderLength);
        //Read the whole header.
        return getHeaderBytes(is, nHeaderLength);
    }

    @Override
    public HashMap<Tag, String> getTags(String sCharsetName) throws IOException {
        InputStream is = new ByteArrayInputStream(data);
        byte[] baTags = getHeaderBytes(is);
        return getTags(baTags, sCharsetName);
    }
}
