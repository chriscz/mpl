package za.co.percipio.mpl.listener;

import za.co.percipio.mpl.MPLServer;

public interface ServerConnectionListener {
    public void onServerStart(MPLServer server);
    public void onServerStop(MPLServer server);
    public void onServerError(MPLServer server, Throwable t);
}
