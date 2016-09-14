package za.co.percipio.mpl.codec.impl;

import za.co.percipio.mpl.codec.Encoder;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectOutput;
import java.io.ObjectOutputStream;
import java.nio.ByteBuffer;

public class JavaEncode implements Encoder{

    public ByteBuffer[] encode(Object toWrite) throws IOException {
        // write object
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        ObjectOutput oo;
        try {
            oo = new ObjectOutputStream(bos);
        } catch (SecurityException e) {
            throw new RuntimeException(e);
        }
        oo.writeObject(toWrite);
        oo.flush();
        byte[] bytes = bos.toByteArray();

        ByteBuffer[] buffers = new ByteBuffer[2];

        /* Allocate the buffer  for the length */
        buffers[0] = ByteBuffer.allocate(4); // 4byte length

        /* put data in the buffers */
        buffers[0].putInt(bytes.length);
        buffers[1] = ByteBuffer.wrap(bytes, 0, bytes.length);

        // Rewind them so we can write something out
        buffers[0].rewind();
        return buffers;
    }
}
