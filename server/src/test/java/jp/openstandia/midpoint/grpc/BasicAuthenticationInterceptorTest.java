package jp.openstandia.midpoint.grpc;

import org.junit.jupiter.api.Test;

import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BasicAuthenticationInterceptorTest {

    @Test
    void extractAndDecodeBasicAuthzHeader() throws Exception {
        String[] s = new BasicAuthenticationInterceptor().extractAndDecodeBasicAuthzHeader("Basic " +
                new String(Base64.getEncoder().encodeToString("foo:bar".getBytes("UTF-8"))));

        assertEquals(2, s.length);
        assertEquals(s[0], "foo");
        assertEquals(s[1], "bar");
    }
}