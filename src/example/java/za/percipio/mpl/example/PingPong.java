package za.percipio.mpl.example;

import java.io.IOException;
import java.io.Serializable;
import za.co.percipio.mpl.MPLClient;
import za.co.percipio.mpl.MPLServer;
import za.co.percipio.mpl.connection.Connection;
import za.co.percipio.mpl.impl.ClientListener;
import za.co.percipio.mpl.impl.ServerListener;

/**
 * A simple Ping Pong example, where the client sends the ping and the server sends the pong
 */
public class PingPong {

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

        @Override public void onServerStart(MPLServer server) {
            this.server = server;
        }

        @Override public void onMessageReceived(Connection connection, Object message) {
            connection.queueMessage(new Message("pong"));
            ((Message) message).printMessage();
        }

        @Override public void onConnect(Connection connection) {
            System.out.println("<Server> Client Connected!");
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
        int msgCounter = 3;

        @Override public void onConnect(Connection connection) {
            System.out.println("<Client> Connected!");
            connection.queueMessage(new Message("ping"));
            msgCounter--;
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

        @Override public void onDisconnect(Connection connection) {
            System.out.println("<Client> Closed connection to server");
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