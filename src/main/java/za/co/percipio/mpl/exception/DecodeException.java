package za.co.percipio.mpl.exception;


public class DecodeException extends Exception{
    private static final long serialVersionUID = -6323085790773077824L;

    public DecodeException(Exception e) {
        super("Message object could not be deserialized.", e);
    }
}
