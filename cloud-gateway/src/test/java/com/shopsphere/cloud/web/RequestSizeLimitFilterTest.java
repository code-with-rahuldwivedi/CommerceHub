package com.shopsphere.cloud.web;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import com.fasterxml.jackson.databind.ObjectMapper;

class RequestSizeLimitFilterTest {

    private MockHttpServletResponse run(boolean rejectChunked, MockHttpServletRequest request) throws Exception {
        RequestSizeLimitFilter filter =
                new RequestSizeLimitFilter(50, rejectChunked, new ProblemWriter(new ObjectMapper()));
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, new MockFilterChain());
        return response;
    }

    @Test
    void oversizedBodyIsRejectedWith413() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/orders");
        request.setContent(new byte[100]);
        assertThat(run(true, request).getStatus()).isEqualTo(413);
    }

    @Test
    void bodyWithinLimitPasses() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/orders");
        request.setContent(new byte[10]);
        assertThat(run(true, request).getStatus()).isEqualTo(200);
    }

    @Test
    void chunkedBodyWithoutLengthIsRejectedWith411() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/orders");
        request.addHeader("Transfer-Encoding", "chunked");
        assertThat(run(true, request).getStatus()).isEqualTo(411);
    }

    @Test
    void chunkedBodyCanBeAllowedByConfiguration() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/orders");
        request.addHeader("Transfer-Encoding", "chunked");
        assertThat(run(false, request).getStatus()).isEqualTo(200);
    }

    @Test
    void getRequestsAreNotChecked() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/products");
        request.addHeader("Transfer-Encoding", "chunked");
        assertThat(run(true, request).getStatus()).isEqualTo(200);
    }
}
