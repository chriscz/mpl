package za.co.percipio.mpl.codec;

/**
 * Created by Chris Coetzee on 2016/07/29.
 */
public class Codec {
    public final Encoder encoder;
    public final Decoder decoder;

    public Codec(Encoder encoder, Decoder decoder) {
        this.encoder = encoder;
        this.decoder = decoder;
    }
}
