package za.co.percipio.mpl;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.util.Iterator;
import java.util.Set;
import za.co.percipio.minlog.Log;
import za.co.percipio.mpl.connection.Connection;
import za.co.percipio.mpl.connection.ConnectionFactory;
import za.co.percipio.mpl.connection.ConnectionHandler;
import za.co.percipio.mpl.impl.ConnectionListenerWrapper;
import za.co.percipio.mpl.listener.ConnectionListener;

public class MPLClient implements Runnable {

    private final Thread thread;

    private final String hostname;

    private final int                port;
    private       Selector           selector;
    private       SocketChannel      channel;
    private final ConnectionListener listener;

    private InetSocketAddress serverAddress = null;

    private          boolean connectionErrorOccured;
    private volatile boolean isActive;
    private final Object initializationLock = new Object();

    private ConnectionHandler handler;

    public MPLClient(String hostname, int port, ConnectionListener listener) {
        this(hostname, port, listener, ConnectionFactory.JAVA_CONNECTION_FACTORY);
    }

    public MPLClient(String hostname, int port, ConnectionListener listener,
            ConnectionFactory factory) {

        listener = new ConnectionListenerWrapper(listener) {
            @Override public void onDisconnect(Connection connection) {
                try {
                    super.onDisconnect(connection);
                } finally {
                    MPLClient.this.disconnect();
                }
            }
        };

        this.thread = new Thread(this);
        this.listener = listener;
        this.handler = new ConnectionHandler(listener, factory);
        this.hostname = hostname;
        this.port = port;
    }

    private void initializeConnection() {
        try {
            selector = Selector.open();
            channel = SocketChannel.open();
            channel.configureBlocking(false);
        } catch (IOException e) {
            Log.error("Error occured while connecting", e);
            internalOnErrorOccurred(null, e);
        }
    }

    ;

    private void connectToServer() {
        boolean connected = false;
        try {
            InetSocketAddress connectedAddress = new InetSocketAddress(hostname, port);
            channel.connect(connectedAddress);
            channel.register(selector, SelectionKey.OP_CONNECT);
            while (!connected) {
                selector.select();
                Set<SelectionKey> selected = selector.selectedKeys();
                Iterator<SelectionKey> iter = selected.iterator();

                while (iter.hasNext()) {
                    SelectionKey key = iter.next();
                    SocketChannel socket = ((SocketChannel) key.channel());
                    iter.remove();
                    if (!key.isValid()) {
                        continue;
                    }
                    if (key.isConnectable()) {
                        if (socket.isConnectionPending()) {
                            socket.finishConnect();
                            connected = true;
                        }
                        break;
                    }
                }
            }
            serverAddress = connectedAddress;
        } catch (IOException e) {
            Log.error("Error occured while connecting", e);
            if (!isActive) {
                /* This was disconnected externally, don't send out a notification */
                Log.trace("Was disconnected from an external source.");
            } else {
                internalOnErrorOccurred(null, e);
            }
            return;
        } catch (Exception e) {
            if (!isActive) {
                /* This was disconnected externally, don't send out a notification */
                Log.trace("Was disconnected from an external source.", e);
            } else {
                Log.error("This should never have happened!", e);
                internalOnErrorOccurred(null, e);
            }
        }

    }

    @Override public void run() {
        initializeConnection();
        if (connectionErrorOccured) return;
        connectToServer();
        if (connectionErrorOccured) return;
        handler.manageSocketChannel(channel);
        while (isActive) {
            try {
                Thread.sleep(Long.MAX_VALUE);
            } catch (InterruptedException e) {}
        }
        disconnectInternal();
    }

    public void connect() {
        synchronized (initializationLock) {
            if (!isActive) {
                isActive = true;
                handler.start();
                thread.start();
            }
        }

    }

    public void disconnect() {
        /* Stop the handler and clean up*/
        synchronized (initializationLock) {
            if (isActive) {
                isActive = false;
                thread.interrupt();
            }
        }

    }

    private void disconnectInternal() {
        handler.stop();
        if (selector != null) {
            try {
                selector.close();
            } catch (IOException e) {
                Log.debug("[Client] Exception while closing selector");
            }
            selector = null;
        }
    }

    private void internalOnErrorOccurred(Connection c, Throwable t) {
        connectionErrorOccured = true;
        listener.onConnectionError(c, t);
        disconnectInternal();
    }

}
