package za.co.percipio.mpl;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.Iterator;
import java.util.Set;
import za.co.percipio.minlog.Log;
import za.co.percipio.mpl.connection.ConnectionFactory;
import za.co.percipio.mpl.connection.ConnectionHandler;
import za.co.percipio.mpl.listener.ConnectionListener;
import za.co.percipio.mpl.listener.ServerConnectionListener;

public class MPLServer implements Runnable {
    private String                   hostname;
    private int                      serverPort;
    private ServerSocketChannel      serverChannel;
    private Selector                 selector;
    private ServerConnectionListener serverConnectionListener;
    private Thread                   thread;// this processing thread
    private ConnectionHandler[]      handlers;
    private int                      nextHandler;

    private InetAddress connectedAddress;

    private boolean connectionErrorOccured;
    private boolean isConnected;

    public <T extends ServerConnectionListener & ConnectionListener> MPLServer(String hostname,
            int port, T listener, int handlerCount) {
        this(hostname, port, listener, listener, ConnectionFactory.JAVA_CONNECTION_FACTORY,
             handlerCount);
    }

    public MPLServer(String hostname, int port, ServerConnectionListener serverListener,
            ConnectionListener clientConnectionListener, int handlerCount) {
        this(hostname, port, serverListener, clientConnectionListener,
             ConnectionFactory.JAVA_CONNECTION_FACTORY, handlerCount);
    }

    public MPLServer(String hostname, int port, ServerConnectionListener serverListener,
            ConnectionListener clientConnectionListener, ConnectionFactory factory,
            int handlerCount) {
        if (handlerCount < 0) throw new RuntimeException("Handler Count cannot be negative");
        this.hostname = hostname;
        this.serverPort = port;
        serverConnectionListener = serverListener;
        handlers = new ConnectionHandler[handlerCount];
        for (int i = 0; i < handlerCount; i++) {
            handlers[i] = new ConnectionHandler(clientConnectionListener, factory);
            handlers[i].start();
        }
        thread = new Thread(this);
    }

    public void connect() {
        thread.start();
    }

    /**
     * Creates the server on the current thread instead of creating it asynchronously.
     * In this case the error must be handled by the caller. Otherwise the error must be handled
     * through the ServerConnectionListener used during construction.
     *
     * @throws IOException
     */
    public void connectSynchronous() throws IOException {
        if (!isConnected) {
            initializeConnectionInternal();
            thread.start();
        } else {
            throw new RuntimeException("Already Connected");
        }

    }

    private synchronized void setConnected(boolean connected) {
        this.isConnected = connected;
    }

    private synchronized boolean isConnected() {
        return this.isConnected;
    }

    private void initializeConnection() {
        InetSocketAddress address;
        try {
            initializeConnectionInternal();
        } catch (IOException e) {
            Log.debug("Connection error occured while connecting to the service", e);
            internalOnErrorOccured(e);
        }
    }

    private void initializeConnectionInternal() throws IOException {
        InetSocketAddress address;
        address = new InetSocketAddress(hostname, serverPort);
        serverChannel = ServerSocketChannel.open();
        serverChannel.socket().setReuseAddress(true);
        serverChannel.socket().bind(address);
        serverChannel.configureBlocking(false);
        selector = Selector.open();
        serverChannel.register(selector, SelectionKey.OP_ACCEPT);

        connectedAddress = serverChannel.socket().getInetAddress();
        setConnected(true);
        serverConnectionListener.onServerStart(this);
    }

    @Override public void run() {
        if (!isConnected()) {
            /* might have started on the creating thread */
            initializeConnection();
        }
        if (connectionErrorOccured) return;
        while (isConnected) {
            try {
                SocketChannel clientSocket;
                selector.select();
                Set<SelectionKey> keys = selector.selectedKeys();
                Iterator<SelectionKey> iter = keys.iterator();
                while (iter.hasNext()) {
                    SelectionKey key = iter.next();
                    iter.remove();
                    if (key.isAcceptable()) {
                        clientSocket = serverChannel.accept();
                        getNextHandler().manageSocketChannel(clientSocket);
                    }
                }
            } catch (IOException e) {
                Log.debug("[Server] ConnectionError during run.", e);
                internalOnErrorOccured(e);
            }
        }
        disconnectInternal();
    }

    private ConnectionHandler getNextHandler() {
        return handlers[(nextHandler++) % handlers.length];
    }

    public void disconnect() {
        if (isConnected()) {
            setConnected(false);
            thread.interrupt();
        }
    }

    private void disconnectInternal() {
        setConnected(false);
        try {
            // stop all the handlers
            for (ConnectionHandler h : handlers) {
                h.stop();
            }
            serverChannel.close();
        } catch (IOException e) {
            Log.debug("Error while closing server connection", e);
        }
        serverConnectionListener.onServerStop(this);
    }

    private void internalOnErrorOccured(Throwable t) {
        connectionErrorOccured = true;
        serverConnectionListener.onServerError(this, t);
        disconnect();
    }

    public InetAddress address() {
        InetAddress address = connectedAddress;
        if (!isConnected()) {
            throw new RuntimeException("Server is not connected yet!");
        }
        return address;
    }

}
