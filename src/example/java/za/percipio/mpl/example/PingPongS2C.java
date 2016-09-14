package za.percipio.mpl.example;

import java.io.IOException;
import java.io.Serializable;
import za.co.percipio.mpl.MPLClient;
import za.co.percipio.mpl.MPLServer;
import za.co.percipio.mpl.connection.Connection;
import za.co.percipio.mpl.impl.ClientListener;
import za.co.percipio.mpl.impl.ServerListener;

/**
 * A simple Ping Pong example, but instead of the client sending the ping, the server sends it first!
 */
public class PingPongS2C {

    static class Message implements Serializable {
        public final String msg;

        public Message(String msg) {
            this.msg = msg;
        }

        public void printMessage() {
            System.out.printf("[MESSAGE] %s\n", msg);
        }
    }

    public static class Server extends ServerListener {
        private MPLServer server;
        int msgCounter = 3;

        @Override public void onServerStart(MPLServer server) {
            this.server = server;
        }

        @Override public void onMessageReceived(Connection connection, Object message) {
            ((Message) message).printMessage();
            if (msgCounter > 0) {
                connection.queueMessage(new Message("ping"));
                msgCounter--;
            } else {
                connection.disconnect();
            }
        }

        @Override public void onConnect(Connection connection) {
            connection.queueMessage(new Message("ping"));
            msgCounter--;
        }

        @Override public void onDisconnect(Connection connection) {
            System.out.println("<Server> Client disconnected");
            server.disconnect();
        }

        @Override public void onServerStop(MPLServer server) {
            System.out.println("<Server> Stopped");
        }
    }

    public static class Client extends ClientListener {

        @Override public void onMessageReceived(Connection connection, Object message) {
            ((Message) message).printMessage();
            connection.queueMessage(new Message("pong"));
        }

        @Override public void onDisconnect(Connection connection) {
            System.out.println("<Client> Server closed the connection");
        }
    }

    public static void main(String args[]) throws IOException {
        String hostname = "localhost";
        int port = 6777;

        MPLClient c = new MPLClient(hostname, port, new Client());
        MPLServer s = new MPLServer(hostname, port, new Server(), 1);

        s.connectSynchronous();
        c.connect();
    }
}