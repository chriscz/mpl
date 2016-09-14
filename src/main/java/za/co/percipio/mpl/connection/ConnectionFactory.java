package za.co.percipio.mpl.connection;

import java.nio.channels.SelectionKey;
import za.co.percipio.mpl.codec.Codec;
import za.co.percipio.mpl.codec.impl.JavaDecode;
import za.co.percipio.mpl.codec.impl.JavaEncode;

/**
 *
 */
public class ConnectionFactory {
    public static final ConnectionFactory JAVA_CONNECTION_FACTORY = new ConnectionFactory(new Codec(
            new JavaEncode(), new JavaDecode()));

    private Codec codec;

    public ConnectionFactory(Codec codec) {
        this.codec = codec;
    }

    public Connection newConnection(ConnectionHandler parent, SelectionKey key) {
        return new Connection(parent, key, codec);
    }

}
