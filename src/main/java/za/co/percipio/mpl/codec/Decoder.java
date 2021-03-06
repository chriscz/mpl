package za.co.percipio.mpl.codec;

import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * Created by Chris Coetzee on 2016/07/29.
 */
public interface Decoder {
    public Object decode(ByteBuffer buffer) throws IOException, ClassNotFoundException;
}
