public class RdfXmlConvertException extends RuntimeException {

    private final RdfXmlConvertError errorType;

    public RdfXmlConvertException(RdfXmlConvertError errorType) {
        this.errorType = errorType;
    }
}