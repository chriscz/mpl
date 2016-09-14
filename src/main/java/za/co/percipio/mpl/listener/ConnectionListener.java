package za.co.percipio.mpl.listener;

import za.co.percipio.mpl.connection.Connection;

/**
 * Callback interface that should be implemented by any classes
 * that wishes to handle events that occur during messaging.
 *
 * @author Chris Coetzee <chriscz93@gmail.com>
 */
public interface ConnectionListener {
    /**
     * Called when a Object was received on the provided connection
     *
     * @param connection on which the Object was received
     * @param message    the Object that was received
     */
    public void onMessageReceived(Connection connection, Object message);

    /**
     * Called when a  Messageconnection is connected to one on the
     * other end.
     */
    public void onConnect(Connection connection);

    /**
     * Called when the connection is disconnected. This may be preceded by a
     * call to {@link this#onDeserializationError(Connection, Throwable)} if a serialization error
     * caused the connection to shut down.
     *
     * @param connection the connection that
     */
    public void onDisconnect(Connection connection);

    /**
     * Called when an error occurs during the use of the connection. The connection
     * may be null for the MPLClient, if an error occured while establishing the
     * connection to the server
     *
     * @param connection
     * @param error
     */
    public void onConnectionError(Connection connection, Throwable error);

    /**
     * Called when an error occurs during the parsing of an incoming message.
     *
     * @param connection on which the error occurred.
     * @param error      type of the error
     */
    public void onDeserializationError(Connection connection, Throwable error);

    public void onSerializationError(Connection connection, Throwable error);
}
