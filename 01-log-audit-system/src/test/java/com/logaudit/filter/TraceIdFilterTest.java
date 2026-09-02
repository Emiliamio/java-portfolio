package com.logaudit.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TraceIdFilterTest {

    private TraceIdFilter traceIdFilter;

    @BeforeEach
    void setUp() {
        traceIdFilter = new TraceIdFilter();
    }

    @AfterEach
    void tearDown() {
        MDC.clear();
    }

    @Test
    void testTraceIdGeneratedWhenMissing() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain filterChain = mock(FilterChain.class);

        traceIdFilter.doFilterInternal(request, response, filterChain);

        verify(filterChain, times(1)).doFilter(request, response);
        String traceIdHeader = response.getHeader(TraceIdFilter.TRACE_ID_HEADER);
        assertNotNull(traceIdHeader);
        assertFalse(traceIdHeader.isBlank());
        // 确保执行完毕后 MDC 已被清理，防止线程污染
        assertNull(MDC.get(TraceIdFilter.MDC_TRACE_ID_KEY));
    }

    @Test
    void testTraceIdPreservedWhenProvidedInHeader() throws ServletException, IOException {
        String existingTraceId = "custom-trace-id-998877";
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(TraceIdFilter.TRACE_ID_HEADER, existingTraceId);
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain filterChain = mock(FilterChain.class);

        traceIdFilter.doFilterInternal(request, response, filterChain);

        verify(filterChain, times(1)).doFilter(request, response);
        assertEquals(existingTraceId, response.getHeader(TraceIdFilter.TRACE_ID_HEADER));
        assertNull(MDC.get(TraceIdFilter.MDC_TRACE_ID_KEY));
    }

    @Test
    void testW3cTraceparentExtraction() throws ServletException, IOException {
        String w3cHeader = "00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01";
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(TraceIdFilter.W3C_TRACEPARENT_HEADER, w3cHeader);
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain filterChain = mock(FilterChain.class);

        traceIdFilter.doFilterInternal(request, response, filterChain);

        verify(filterChain, times(1)).doFilter(request, response);
        assertEquals("4bf92f3577b34da6a3ce929d0e0e4736", response.getHeader(TraceIdFilter.TRACE_ID_HEADER));
        assertNotNull(response.getHeader(TraceIdFilter.W3C_TRACEPARENT_HEADER));
        assertNull(MDC.get(TraceIdFilter.MDC_TRACE_ID_KEY));
    }
}
