package za.co.percipio.mpl.connection;

import java.io.IOException;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import za.co.percipio.minlog.Log;
import za.co.percipio.mpl.exception.ConnectionException;
import za.co.percipio.mpl.exception.EncodeException;
import za.co.percipio.mpl.listener.ConnectionListener;

public class ConnectionHandler implements Runnable {

    private List<SocketChannel> pendingChannels;

    private HashMap<Connection, Connection> clientConnections;
    private ArrayList<Connection>           disconnected;

    private Thread             thread;
    private ConnectionListener listener;
    private Selector           selector;

    private ConnectionFactory factory;

    private volatile boolean active;

    private Object initializationLock = new Object();

    /**
     * Creates a ConnectionHandler, if the a selector cannot be created an exception is thrown
     *
     * @throws RuntimeException
     **/
    public ConnectionHandler(ConnectionListener listener, ConnectionFactory factory) {
        pendingChannels = new LinkedList<SocketChannel>();
        clientConnections = new HashMap<Connection, Connection>();
        disconnected = new ArrayList<>();

        thread = new Thread(this, "ConnectionHandler Thread");
        this.listener = listener;
        try {
            selector = Selector.open();
        } catch (IOException e) {
            Log.error("[ConnectionHandler] Couldn't open selector!!", e);
            throw new RuntimeException(e);
        }
        this.factory = factory;
    }

    public void start() {
        if (!active) {
            synchronized (initializationLock) {
                if (!active) {
                    active = true;
                    thread.start();
                } else {
                    Log.warn("Handler already active");
                }
            }
        }
    }

    public void stop() {
        if (active) {
            synchronized (initializationLock) {
                if (active) {
                    active = false;
                    selector.wakeup();
                }
            }
        }
    }

    /**
     * Sets this ConnectionHandler to manage the given socket.
     *
     * @param socket
     */
    public void manageSocketChannel(SocketChannel socket) {
        // XXX Should have been initialized by now!!!!
        try {
            synchronized (pendingChannels) {
                socket.configureBlocking(false);
                pendingChannels.add(socket);//
                if (selector != null) {
                    selector.wakeup();
                }
            }
        } catch (IOException e) {
            Log.error("[SocketHandler] Could not manage socket");
        }
    }

    @Override public void run() {
        try {
            while (active) {
                try {
                    registerNewConnections();
                    selector.select();
                    processDisconnected();

                    Set<SelectionKey> selected = selector.selectedKeys();
                    Iterator<SelectionKey> iter = selected.iterator();

                    while (iter.hasNext()) {
                        SelectionKey key = iter.next();
                        iter.remove();

                        if (!key.isValid()) {
                            continue;
                        }
                        Connection c = (Connection) key.attachment();

                        if (key.isReadable()) {
                            try {
                                c.read();
                            } catch (ConnectionException e) {
                                processConnectionError(key, c, e);
                                continue;
                            }
                        }
                        if (key.isWritable()) {
                            try {
                                c.write();
                            } catch (EncodeException e) {
                                Log.error(String.format("Encoding error on connection %s",
                                                        c.toString()));
                                e.printStackTrace();
                                /* FIXME this is quite a fatal error */
                                throw new RuntimeException(e);
                            } catch (ConnectionException e) {
                                processConnectionError(key, c, e);
                                continue;
                            }
                        }
                    }
                } catch (IOException e) {
                    if (!active) {
                        /* Thread stopped before error */
                        Log.debug("Thread already stopped");
                    } else {
                        Log.error("Selector threw an exception", e);
                    }
                } catch (Exception e) {
                    Log.debug("Other exception occurred", e);
                }
            }
        } finally {
        /* When done executing */
            internalCleanup();
        }
    }

    private void processDisconnected() {
        synchronized (clientConnections) {
            Iterator<Connection> iter = disconnected.iterator();
            while (iter.hasNext()) {
                Connection c = iter.next();
                iter.remove();
                onConnectionDisconnect(c);
                clientConnections.remove(c);
            }
        }
    }

    void notifyDisconnected(Connection c) {
        synchronized (clientConnections) {
            disconnected.add(c);
        }
    }

    private void processConnectionError(SelectionKey key, Connection c, Throwable e) {
        key.cancel();
        Log.debug("Connection error occurred", e);
        clientConnections.remove(c);
        onConnectionError(c, e);
    }

    private void onConnectionError(Connection c, Throwable t) {
        try {
            listener.onConnectionError(c, t);
        } catch (Exception e) {
            Log.error("Client code threw an exception", e);
        }
    }

    private void onConnectionDisconnect(Connection c) {
        try {
            listener.onDisconnect(c);
        } catch (Exception e) {
            Log.error("Client code threw an exception", e);
        }
    }

    private void internalCleanup() {
        try {
            selector.close();
        } catch (IOException e) {
            Log.error("[ConnectionHandler] Error while closing selector");
        }
        selector = null;
        for (Connection c : clientConnections.keySet()) {
            /* Call the disconnect function of all the connections */
            c.disconnect();
        }
        processDisconnected();
        pendingChannels.clear();
        clientConnections.clear();
    }

    private void registerNewConnections() {
        if (pendingChannels.isEmpty()) return;
        ArrayList<SocketChannel> nextChannels;
        synchronized (pendingChannels) {
            nextChannels = new ArrayList<SocketChannel>(pendingChannels);
            pendingChannels.clear();
        }
        for (SocketChannel s : nextChannels) {
            try {
                SelectionKey key = s.register(selector, SelectionKey.OP_READ);
                Connection c = factory.newConnection(this, key);
                Connection oldConnection = clientConnections.put(c, c);
                if (oldConnection != null && oldConnection.checkIsConnected()) {
                    /* This shouldn't ever happen, since we don't reuse connections */
                    oldConnection.disconnect();
                }
                listener.onConnect(c);
            } catch (ClosedChannelException e) {
                Log.error("Could not register socket with selector");
            }
        }
    }

    public ConnectionListener getConnectionEventListener() {
        return listener;
    }
}
