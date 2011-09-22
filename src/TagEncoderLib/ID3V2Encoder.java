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
import java.util.HashMap;

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
            case ARTIST:
                return "TPE1";
            case ALBUM:
                return "TALB";
            case TITLE:
                return "TIT2";
        }
        return "";
    }
    
    private static Tag getTagType(String code) {
        if (code.equals("TPE1"))
            return Tag.ARTIST;
        else if (code.equals("TALB"))
            return Tag.ALBUM;
        else if (code.equals("TIT2"))
            return Tag.TITLE;
        else
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
        byte[] baTagFlags = new byte[3];
        bis.read(baTagFlags);
        baTagFlags[2] = 0x01;  //set encoding to UTF-16
        byte[] baNewTagValue = value.getBytes("UTF-16");
        int nNewTagLength = baNewTagValue.length + 1;

        int nNewHeaderLength = baHeader.length + (nNewTagLength - nOldTagLength);

        //4 - tag length, 3 - tag flags
        int nNextTagIndex = nTagStartIndex + 4 + 3 + nOldTagLength - 1;

        ByteArrayOutputStream bos = new ByteArrayOutputStream(nNewHeaderLength);

        bos.write(baHeaderHeader, 0, 6);
        bos.write(synchronizeIntegerValue(nNewHeaderLength));
        bos.write(baHeader, 0, nTagStartIndex);
        bos.write(synchronizeIntegerValue(nNewTagLength));
        bos.write(baTagFlags);
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

    private static HashMap<Tag, String> getTags(byte[] baTags, String sCharsetName) throws IOException {
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
