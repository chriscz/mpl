package za.co.percipio.mpl.exception;

public class EncodeException extends Exception{
    private static final long serialVersionUID = 4776159698059251966L;
    private Object message;
    public EncodeException(Object message, Exception e) {
        super("Object object could not be serialized.", e);
        this.message = message;
    }

    public Object getMessageObject(){
        return message;
    }
}
