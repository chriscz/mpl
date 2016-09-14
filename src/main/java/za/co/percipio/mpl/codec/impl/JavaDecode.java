package za.co.percipio.mpl.codec.impl;

import za.co.percipio.mpl.codec.Decoder;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectInputStream;
import java.nio.ByteBuffer;

public class JavaDecode implements Decoder {

    public Object decode(ByteBuffer buffer) throws IOException, ClassNotFoundException {
        ByteArrayInputStream bis = new ByteArrayInputStream(buffer.array());
        ObjectInput in = new ObjectInputStream(bis);
        return in.readObject();
    }
}
