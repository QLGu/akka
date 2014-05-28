/**
 * Copyright (C) 2009-2014 Typesafe Inc. <http://www.typesafe.com>
 */

package akka.http.model.japi.headers;

import akka.http.model.japi.DateTime;

/**
 *  Model for the `If-Unmodified-Since` header.
 *  Specification: http://tools.ietf.org/html/draft-ietf-httpbis-p4-conditional-26#section-3.4
 */
public abstract class If_Unmodified_Since extends akka.http.model.HttpHeader {
    public abstract DateTime date();

    public static If_Unmodified_Since create(DateTime date) {
        return new akka.http.model.headers.If$minusUnmodified$minusSince(((akka.http.util.DateTime) date));
    }
}
