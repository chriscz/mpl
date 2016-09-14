package za.co.percipio.mpl.connection;

import java.io.IOException;
import java.net.InetAddress;
import java.nio.ByteBuffer;
import java.nio.channels.CancelledKeyException;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import za.co.percipio.minlog.Log;
import za.co.percipio.mpl.codec.Codec;
import za.co.percipio.mpl.exception.ConnectionException;
import za.co.percipio.mpl.exception.DecodeException;
import za.co.percipio.mpl.exception.EncodeException;
import za.co.percipio.mpl.listener.ConnectionListener;

/**
 * A messaging channel between two hosts
 *
 * @author Chris Coetzee
 */
public class Connection {
    private static final ByteBuffer[] EMPTY_BUFFER_ARRAY = new ByteBuffer[0];
    private       ConnectionHandler  parent;
    private       ConnectionListener currentListener;
    private final Codec              codec;

    private SocketChannel channel;
    private SelectionKey  key;
    private AtomicBoolean hasSetWriteReady;

    private List<Object>     queuedMessages;
    private List<ByteBuffer> pendingOutputBytes;
    private List<ByteBuffer> pendingInputBytes;

    private ByteBuffer nextObjectSizeBuffer;
    private int        nextObjectSize;
    private boolean    hasReadNextSize;

    private volatile boolean connected;
    private volatile boolean hasCleaned;

    private final CountDownLatch disconnectLatch;

    /**
     * Create a new Connection using the given handler as parent / manager of this connection and the given
     * SelectionKey to represent the connection.
     *
     * @param parent
     * @param key
     */
    public Connection(ConnectionHandler parent, SelectionKey key, Codec codec) {
        disconnectLatch = new CountDownLatch(1);
        queuedMessages = new LinkedList<Object>();
        hasSetWriteReady = new AtomicBoolean();
        pendingOutputBytes = new LinkedList<ByteBuffer>();
        pendingInputBytes = new LinkedList<ByteBuffer>();
        nextObjectSizeBuffer = ByteBuffer.allocate(4);

        this.parent = parent;
        this.key = key;
        this.channel = (SocketChannel) key.channel();
        currentListener = parent.getConnectionEventListener();

        key.attach(this);
        this.codec = codec;
        possiblyUpdateConnectionState();
    }

    /**
     * Add a Objectfor sending over ths connection
     *
     * @param m the Object to send
     */
    public void queueMessage(Object m) {
        Log.trace("Sending Message: " + m);
        synchronized (queuedMessages) {
            queuedMessages.add(m);
            possiblySetWriteReady();
            possiblyUpdateConnectionState();
        }
    }

    /**
     * Mark the channel as being write ready
     */
    private void possiblySetWriteReady() {
        if (hasSetWriteReady.compareAndSet(false, true)) {
            try {
                key.interestOps(SelectionKey.OP_WRITE | SelectionKey.OP_READ);
            } catch (CancelledKeyException e) {
                Log.debug(
                        "[Connection] Key has been cancelled while setting `OP_WRITE`, continuing anyway");
            }
            key.selector().wakeup();
        }
    }

    /**
     * Unmark the channel as write ready. This is done to prevent unnecessary waste of CPU cycles when we
     * don't have any data to write.
     */
    private void possiblyUnsetWriteReady() {
        synchronized (queuedMessages) {
            if (pendingOutputBytes.isEmpty() && queuedMessages.isEmpty()
                    && hasSetWriteReady.compareAndSet(true, false)) {
                try {
                    key.interestOps(SelectionKey.OP_READ);
                } catch (CancelledKeyException e) {
                    Log.debug(
                            "[Connection] Key has been cancelled while setting `OP_READ`, continuing anyway");
                }
                key.selector().wakeup();
            }
        }
    }

    /**
     * Method used by the handler of this connection to perform reading from the channel
     */
    void read() throws ConnectionException {
        if (!checkIsConnected()) throw new ConnectionException("Connection already disconnected");
        readNewMessages();
        processMessageByteBuffers();
    }

    /**
     * Attempts to read messages from the socketChannel
     */
    private void readNewMessages() throws ConnectionException {
        while (key.isReadable()) {
            ByteBuffer last = null;
            if (!pendingInputBytes.isEmpty()) {
                last = pendingInputBytes.get(pendingInputBytes.size() - 1);
            }
            if (last != null && last.remaining() > 0) {
                /* The buffer still has some data pending */
                try {
                    int readCount = channel.read(last);
                    if (readCount < 0) throw new IOException("Could not read from channel");
                    if (readCount == 0) return;
                } catch (IOException e) {
                    processConnectionError(e);
                }
            } else {
                try {
                    readNextObjectSize();
                    if (hasReadNextSize) {
                        /* Great, we know the size of the next object! */
                        ByteBuffer buffer = ByteBuffer.allocate(nextObjectSize);
                        pendingInputBytes.add(buffer);
                        continue;
                    } else
                        /* Couldn't fill up the size, leaving it until later! */
                        return;
                } catch (IOException e) {
                    processConnectionError(e);
                }
            }
        }
    }

    /**
     * Performs necessary callbacks to notify the listener of the connection error, then proceeds
     * to raise a ConnectionException.
     *
     * @param e
     * @throws ConnectionException
     */
    private void processConnectionError(Throwable e) throws ConnectionException {
        disconnectInternal();
                    /* TODO Cleanup and return */
        throw new ConnectionException(e);
    }

    /**
     * Processes the pendingByteBuffers
     */
    private void processMessageByteBuffers() {
        int available = pendingInputBytes.size();

        if (available > 0) {
            int readUntil = available;
            if (pendingInputBytes.get(available - 1).hasRemaining()) {
                /* We still need to fill the last Object up, so we don't process it yet */
                readUntil--;
            }
            for (int i = 0; i < readUntil; i++) {
                Object m;
                ByteBuffer buffer = pendingInputBytes.remove(0);
                buffer.rewind();
                try {
                    m = deserialize(buffer);
                } catch (DecodeException e) {
                    Log.error("Message could not be deserialized", e);
                    onMessageDeserializationError(e);
                    continue;
                }
                onHandleNewMessage(m);
            }
            /* Done deserializing */
        }
    }

    private void readNextObjectSize() throws IOException {
        hasReadNextSize = false;
        int readBytes = channel.read(nextObjectSizeBuffer);
        if (readBytes < 0) throw new IOException("Could not read from channel");
        if (!nextObjectSizeBuffer.hasRemaining()) {
            /* We have read the size! */
            nextObjectSizeBuffer.rewind();
            nextObjectSize = nextObjectSizeBuffer.getInt();
            nextObjectSizeBuffer.rewind();
            hasReadNextSize = true;
        }
    }

    /**
     * Method used by the handler of this connection, when data is to be written to the client
     *
     * @throws EncodeException
     * @throws ConnectionException
     */
    void write() throws EncodeException, ConnectionException {
        if (!checkIsConnected()) throw new ConnectionException("Connection already disconnected");
        currentListener = parent.getConnectionEventListener();
        if (!key.isValid()) {
            /* TODO call the listener callback if we have not already done so! */
            Log.debug("Found invalid key:" + key);
            return;
        }
        if (key.isWritable()) {
            while (!pendingOutputBytes.isEmpty() || !queuedMessages.isEmpty()) {
                if (pendingOutputBytes.isEmpty()) {
                    try {
                        serializeMessages();
                    } catch (EncodeException e) {
                        Log.error("Message could not be serialized", e);
                        onMessageSerializationError(e);
                        disconnectInternal();
                        return;
                    }
                }
                ByteBuffer buf = pendingOutputBytes.get(0);
                try {
                    channel.write(buf);
                    if (buf.remaining() > 0) {
                        /* Could not write anymore */
                        synchronized (queuedMessages) {
                            possiblySetWriteReady();
                        }
                        return;
                    } else {
                        pendingOutputBytes.remove(0);
                    }
                } catch (IOException e) {
                    Log.debug("Error while writing a message", e);
                    disconnectInternal();
                    throw new ConnectionException(e);
                }
            }
            possiblyUnsetWriteReady();
        }
    }

    /**
     * Serializes a Object to an object
     *
     * @param m Object object to serialize
     * @return the serialized object
     * @throws IOException
     */
    private ByteBuffer[] serialize(Object m) throws EncodeException {
        try {
            return codec.encoder.encode(m);
        } catch (Exception e) {
            Log.debug("Could not serialize message.", e);
            throw new EncodeException(m, e);
        }
    }

    private Object deserialize(ByteBuffer b) throws DecodeException {
        /* This should return null if the serialization failed! */
        try {
            return codec.decoder.decode(b);
        } catch (Exception e) {
            throw new DecodeException(e);
        }
    }

    private void serializeMessages() throws EncodeException {
        if (!queuedMessages.isEmpty()) {
            synchronized (queuedMessages) {
                Iterator<Object> messages = queuedMessages.iterator();
                while (messages.hasNext()) {
                    Object m = messages.next();

                    ByteBuffer[] serialized = serialize(m);
                    for (ByteBuffer b : serialized) {
                        pendingOutputBytes.add(b);
                    }
                    messages.remove();
                }
            }
        }
    }

    private void onHandleNewMessage(Object message) {
        try {
            currentListener.onMessageReceived(this, message);
        } catch (Exception e) {
            Log.error("Client code threw an exception", e);
        }
    }

    private void onMessageDeserializationError(Throwable t) {
        try {
            currentListener.onDeserializationError(this, t);
        } catch (Exception e) {
            Log.error("Client code threw an exception", e);
        }
    }

    private void onMessageSerializationError(Throwable t) {
        try {
            currentListener.onSerializationError(this, t);
        } catch (Exception e) {
            Log.error("Client code threw an exception", e);
        }
    }

    private void possiblyUpdateConnectionState() {
        synchronized (queuedMessages) {
            if (!channel.isOpen() || !key.isValid()) {
                disconnectInternal();
                return;
            }
            connected = true;
        }
    }

    /**
     * Disconnects this connection ensures that all internal structures have been cleared and that the
     * connection has been closed.
     * <p/>
     * <br/>
     * <b>*NOTE*</b> This should only be invoked from the handler thread.
     */
    void disconnectInternal() {
        if (connected) {
            synchronized (queuedMessages) {
                if (!connected) return;
                parent.notifyDisconnected(this);
                connected = false;
                try {channel.close();} catch (IOException e) {
                    Log.debug("Exception while closing channel", e);
                }
                key.selector().wakeup();
                /* the onDisconnect method is called from the handler */
                cleanUp();
            }

        }

    }

    public boolean checkIsConnected() {
        possiblyUpdateConnectionState();
        return connected;
    }

    public void disconnect() {
        disconnectInternal();
    }

    public InetAddress address() {
        return channel.socket().getInetAddress();
    }

    public int port() {
        return channel.socket().getLocalPort();
    }

    private void cleanUp() {
        if (!hasCleaned) {
            hasCleaned = true;
            pendingInputBytes.clear();
            pendingOutputBytes.clear();
            nextObjectSize = -1;
            nextObjectSizeBuffer = null;
            hasReadNextSize = false;
            parent = null;
        }
        // We don't null current connection listener
        // currentConnectionListener
        // because it may still be used to notify the caller of the error
    }

    public boolean isSameHostAs(Connection c) {
        if (c == null) return false;
        return (address().equals(c.address()));
    }

    @Override public String toString() {
        final StringBuilder sb = new StringBuilder("Connection{");
        sb.append("address=");
        sb.append(address());
        sb.append("port=");
        sb.append(port());
        sb.append('}');
        return sb.toString();
    }

    /* blocks the calling thread until this connection is terminated or until
    the thread is interrupted */
    public void awaitDisconnect() throws InterruptedException {
        disconnectLatch.await();
    }

}
