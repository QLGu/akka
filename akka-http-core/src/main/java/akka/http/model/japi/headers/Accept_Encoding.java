package akka.http.model.japi.headers;

/**
 *  Model for the `Accept-Encoding` header.
 *  Specification: http://tools.ietf.org/html/draft-ietf-httpbis-p2-semantics-26#section-5.3.4
 */
public abstract class Accept_Encoding extends akka.http.model.HttpHeader {
    public abstract Iterable<HttpEncodingRange> getEncodings();

    public static Accept_Encoding create(HttpEncodingRange... encodings) {
        return new akka.http.model.headers.Accept$minusEncoding(akka.http.model.japi.Util.<HttpEncodingRange, akka.http.model.headers.HttpEncodingRange>convertArray(encodings));
    }
}
