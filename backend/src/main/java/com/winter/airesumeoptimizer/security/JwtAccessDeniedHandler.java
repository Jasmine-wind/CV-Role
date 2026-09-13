package com.winter.airesumeoptimizer.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.winter.airesumeoptimizer.common.logging.RequestIdFilter;
import com.winter.airesumeoptimizer.common.result.Result;
import com.winter.airesumeoptimizer.common.result.ResultCode;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

@Component
public class JwtAccessDeniedHandler implements AccessDeniedHandler {

    private final ObjectMapper objectMapper;

    public JwtAccessDeniedHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void handle(
            HttpServletRequest request,
            HttpServletResponse response,
            AccessDeniedException accessDeniedException) throws IOException, ServletException {
        String requestId = RequestIdFilter.getOrCreateRequestId(request);
        response.setStatus(HttpStatus.FORBIDDEN.value());
        response.setHeader(RequestIdFilter.REQUEST_ID_HEADER, requestId);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write(objectMapper.writeValueAsString(Result.failure(
                HttpStatus.FORBIDDEN.value(),
                ResultCode.FORBIDDEN.getMessage(),
                request.getRequestURI(),
                requestId)));
    }
}
