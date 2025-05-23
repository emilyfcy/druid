package com.alibaba.druid.support.http.filter;

import com.alibaba.druid.support.http.util.ClientIpContext;

import javax.servlet.*;
import javax.servlet.http.HttpServletRequest;
import java.io.IOException;

public class ClientIpCaptureFilter implements Filter {

    @Override
    public void init(FilterConfig filterConfig) throws ServletException {
        // No initialization needed
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        
        String clientIp = null; // Store client IP to ensure it's the same one used for clearing context
        if (request instanceof HttpServletRequest) {
            clientIp = ((HttpServletRequest) request).getRemoteAddr();
            ClientIpContext.setCurrentClientIp(clientIp);
        }

        try {
            chain.doFilter(request, response);
        } finally {
            // Clear context based on the IP that was set for this thread,
            // effectively "popping" this request's IP context.
            // This handles nested filter calls or async request dispatching correctly
            // by ensuring we only clear the IP set by *this* filter instance for *this* request.
            if (clientIp != null) { // Only clear if an IP was set by this filter instance
                 ClientIpContext.clearCurrentClientIp();
            }
        }
    }

    @Override
    public void destroy() {
        // No cleanup needed
    }
}
