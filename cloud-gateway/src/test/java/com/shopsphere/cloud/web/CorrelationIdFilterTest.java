package com.shopsphere.cloud.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Collections;

import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import jakarta.servlet.http.HttpServletRequest;

class CorrelationIdFilterTest {

    private final CorrelationIdFilter filter = new CorrelationIdFilter();

    private HttpServletRequest forwardedRequest(MockHttpServletRequest in, MockHttpServletResponse out)
            throws Exception {
        MockFilterChain chain = new MockFilterChain();
        filter.doFilter(in, out, chain);
        return (HttpServletRequest) chain.getRequest();
    }

    @Test
    void wellFormedClientIdIsKeptAndEchoed() throws Exception {
        MockHttpServletRequest in = new MockHttpServletRequest("GET", "/api/products");
        in.addHeader("X-Request-Id", "client-req-12345");
        MockHttpServletResponse out = new MockHttpServletResponse();

        HttpServletRequest forwarded = forwardedRequest(in, out);

        assertThat(forwarded.getHeader("X-Request-Id")).isEqualTo("client-req-12345");
        assertThat(out.getHeader("X-Request-Id")).isEqualTo("client-req-12345");
    }

    @Test
    void malformedClientIdIsReplaced() throws Exception {
        MockHttpServletRequest in = new MockHttpServletRequest("GET", "/api/products");
        in.addHeader("X-Request-Id", "bad id\r\ninjected");
        MockHttpServletResponse out = new MockHttpServletResponse();

        HttpServletRequest forwarded = forwardedRequest(in, out);

        assertThat(forwarded.getHeader("X-Request-Id")).matches("[0-9a-f\\-]{36}");
        assertThat(out.getHeader("X-Request-Id")).isEqualTo(forwarded.getHeader("X-Request-Id"));
    }

    @Test
    void missingIdIsGenerated() throws Exception {
        MockHttpServletResponse out = new MockHttpServletResponse();
        HttpServletRequest forwarded = forwardedRequest(new MockHttpServletRequest("GET", "/x"), out);
        assertThat(forwarded.getHeader("X-Request-Id")).isNotBlank();
    }

    @Test
    void internalOnlyHeadersNeverReachDownstreamServices() throws Exception {
        MockHttpServletRequest in = new MockHttpServletRequest("POST", "/api/inventory/1/reduce");
        in.addHeader("X-Service-Token", "stolen-or-guessed");
        in.addHeader("Authorization", "Bearer abc");
        MockHttpServletResponse out = new MockHttpServletResponse();

        HttpServletRequest forwarded = forwardedRequest(in, out);

        assertThat(forwarded.getHeader("X-Service-Token")).isNull();
        assertThat(Collections.list(forwarded.getHeaders("X-Service-Token"))).isEmpty();
        assertThat(Collections.list(forwarded.getHeaderNames())).doesNotContain("X-Service-Token");
        assertThat(forwarded.getHeader("Authorization")).isEqualTo("Bearer abc");
    }

    @Test
    void logContextIsCleanedUp() throws Exception {
        forwardedRequest(new MockHttpServletRequest("GET", "/x"), new MockHttpServletResponse());
        assertThat(MDC.get("requestId")).isNull();
    }
}
