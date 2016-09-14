package za.co.percipio.mpl.impl;

import za.co.percipio.mpl.connection.Connection;
import za.co.percipio.mpl.listener.ConnectionListener;

/**
 * Allows one to intercept calls to a ConnectionListener
 */
public class ConnectionListenerWrapper implements ConnectionListener {
    private ConnectionListener listener;

    public ConnectionListenerWrapper(ConnectionListener listener) {
        this.listener = listener;
    }

    @Override public void onConnect(Connection connection) {
        listener.onConnect(connection);
    }

    @Override public void onConnectionError(Connection connection, Throwable error) {
        listener.onConnectionError(connection, error);
    }

    @Override public void onDeserializationError(Connection connection, Throwable error) {
        listener.onDeserializationError(connection, error);
    }

    @Override public void onDisconnect(Connection connection) {
        listener.onDisconnect(connection);
    }

    @Override public void onMessageReceived(Connection connection, Object message) {
        listener.onMessageReceived(connection, message);
    }

    @Override public void onSerializationError(Connection connection, Throwable error) {
        listener.onSerializationError(connection, error);
    }
}
