package za.co.percipio.mpl.exception;

public class ConnectionException extends Exception {

    private static final long serialVersionUID = 2085008681158838238L;

    public ConnectionException(String message, Throwable t) {
        super(message, t);
    }

    public ConnectionException(Throwable t) {
        super(t);
    }

    public ConnectionException(String message) {
        super(message);
    }

    public ConnectionException() {
        super();
    }
}
