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
public abstract class AbstractTagEncoder {
    
    protected byte[] data = null;
    
    public AbstractTagEncoder(byte[] data) {
        this.data = data;
    }
    
    public abstract HashMap<String, String> getTags(String sCharsetName) throws IOException;
    public abstract void updateTagValue(OutputStream os, Tag tag, String value) throws IOException;
}