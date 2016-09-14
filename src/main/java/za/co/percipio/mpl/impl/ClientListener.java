package za.co.percipio.mpl.impl;

import za.co.percipio.mpl.connection.Connection;
import za.co.percipio.mpl.listener.ConnectionListener;

/**
 * Created by Chris Coetzee on 2016/07/29.
 */
public class ClientListener implements ConnectionListener {
    @Override public void onMessageReceived(Connection connection, Object message) {

    }

    @Override public void onConnect(Connection connection) {
        
    }

    @Override public void onDisconnect(Connection connection) {

    }

    @Override public void onConnectionError(Connection connection, Throwable error) {

    }

    @Override public void onDeserializationError(Connection connection, Throwable error) {

    }

    @Override public void onSerializationError(Connection connection, Throwable error) {

    }
}
