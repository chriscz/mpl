package za.co.percipio.mpl.impl;

import za.co.percipio.mpl.MPLServer;
import za.co.percipio.mpl.connection.Connection;
import za.co.percipio.mpl.listener.ConnectionListener;
import za.co.percipio.mpl.listener.ServerConnectionListener;

/**
 * A convenience class for implementing a server connection listener
 * Created by Chris Coetzee on 2016/07/29.
 */
public class ServerListener implements ServerConnectionListener, ConnectionListener {
    @Override public void onMessageReceived(Connection connection, Object message) {

    }

    @Override public void onConnect(Connection connection) {

    }

    @Override public void onDisconnect(Connection connection) {

    }

    @Override public void onConnectionError(Connection connection, Throwable error) {

    }

    @Override public void onDeserializationError(Connection connection, Throwable t) {

    }

    @Override public void onSerializationError(Connection connection, Throwable t) {

    }

    @Override public void onServerStart(MPLServer server) {

    }

    @Override public void onServerStop(MPLServer server) {

    }

    @Override public void onServerError(MPLServer server, Throwable t) {

    }
}
