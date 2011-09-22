/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package TagEncoderLib;

import TagEncoderLib.BicycleTagEncoder.Tag;
import java.io.IOException;
import java.io.OutputStream;
import java.util.HashMap;

/**
 *
 * @author ovoloshchuk
 */
public class ID3V1Encoder extends AbstractTagEncoder{
    
    public ID3V1Encoder(byte[] data) {
        super(data);
    }

    @Override
    public HashMap<String, String> getTags(String sCharsetName) throws IOException{
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public void updateTagValue(OutputStream os, Tag tag, String value) throws IOException{
        throw new UnsupportedOperationException("Not supported yet.");
    }
    
}
