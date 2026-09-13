package com.winter.airesumeoptimizer.common.exception;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.winter.airesumeoptimizer.common.logging.RequestIdFilter;
import com.winter.airesumeoptimizer.infra.ai.AiFailureCode;
import com.winter.airesumeoptimizer.infra.ai.AiGatewayException;
import com.winter.airesumeoptimizer.security.JwtAccessDeniedHandler;
import com.winter.airesumeoptimizer.security.JwtAuthenticationEntryPoint;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class GlobalExceptionHandlerTest {

    private static final String REQUEST_ID = "test-request-123";

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new FailureController())
                .setControllerAdvice(new GlobalExceptionHandler())
                .addFilters(new RequestIdFilter())
                .build();
    }

    @Test
    void businessStatusCodeBodyCodeAndRequestIdMustMatch() throws Exception {
        mockMvc.perform(get("/failure/business").header(RequestIdFilter.REQUEST_ID_HEADER, REQUEST_ID))
                .andExpect(status().isConflict())
                .andExpect(header().string(RequestIdFilter.REQUEST_ID_HEADER, REQUEST_ID))
                .andExpect(jsonPath("$.code").value(409))
                .andExpect(jsonPath("$.path").value("/failure/business"))
                .andExpect(jsonPath("$.requestId").value(REQUEST_ID));
    }

    @Test
    void aiFailureKeepsPublicMappingWithRealHttpStatus() throws Exception {
        mockMvc.perform(get("/failure/ai"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.code").value(502))
                .andExpect(jsonPath("$.message").value("PROVIDER_UNAVAILABLE"))
                .andExpect(jsonPath("$.requestId").isNotEmpty());
    }

    @Test
    void frameworkStatusIsPreservedWithoutLeakingItsReason() throws Exception {
        mockMvc.perform(get("/failure/framework"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.code").value(405))
                .andExpect(jsonPath("$.message").value("请求方法不支持"))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("sensitive"))));
    }

    @Test
    void unexpectedAndBusiness5xxExceptionsReturnOpaqueInternalErrors() throws Exception {
        mockMvc.perform(get("/failure/internal"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.message").value("服务器内部错误"))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("database-password"))));

        mockMvc.perform(get("/failure/business-internal"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.message").value("服务器内部错误"))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("storage-key"))));
    }

    @Test
    void securityHandlersUseSameStatusCodeAndRequestIdEnvelope() throws Exception {
        JwtAuthenticationEntryPoint entryPoint = new JwtAuthenticationEntryPoint(objectMapper);
        MockHttpServletRequest unauthorizedRequest = request("/api/private");
        MockHttpServletResponse unauthorizedResponse = new MockHttpServletResponse();

        entryPoint.commence(
                unauthorizedRequest,
                unauthorizedResponse,
                new BadCredentialsException("sensitive authentication detail"));

        assertEnvelope(unauthorizedResponse, 401, "/api/private");

        JwtAccessDeniedHandler deniedHandler = new JwtAccessDeniedHandler(objectMapper);
        MockHttpServletRequest forbiddenRequest = request("/api/admin");
        MockHttpServletResponse forbiddenResponse = new MockHttpServletResponse();

        deniedHandler.handle(
                forbiddenRequest,
                forbiddenResponse,
                new AccessDeniedException("sensitive authorization detail"));

        assertEnvelope(forbiddenResponse, 403, "/api/admin");
    }

    private MockHttpServletRequest request(String path) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", path);
        request.addHeader(RequestIdFilter.REQUEST_ID_HEADER, REQUEST_ID);
        return request;
    }

    private void assertEnvelope(MockHttpServletResponse response, int status, String path) throws Exception {
        assertThat(response.getStatus()).isEqualTo(status);
        assertThat(response.getHeader(RequestIdFilter.REQUEST_ID_HEADER)).isEqualTo(REQUEST_ID);
        JsonNode body = objectMapper.readTree(response.getContentAsByteArray());
        assertThat(body.path("code").asInt()).isEqualTo(status);
        assertThat(body.path("path").asText()).isEqualTo(path);
        assertThat(body.path("requestId").asText()).isEqualTo(REQUEST_ID);
        assertThat(response.getContentAsString()).doesNotContain("sensitive");
    }

    @RestController
    static class FailureController {

        @GetMapping("/failure/business")
        void business() {
            throw new BusinessException(409, "内容已更新");
        }

        @GetMapping("/failure/business-internal")
        void businessInternal() {
            throw new BusinessException(500, "storage-key=private-object");
        }

        @GetMapping("/failure/ai")
        void ai() {
            throw new AiGatewayException(AiFailureCode.PROVIDER_UNAVAILABLE, "raw provider detail");
        }

        @GetMapping("/failure/framework")
        void framework() {
            throw new ResponseStatusException(HttpStatus.METHOD_NOT_ALLOWED, "sensitive framework reason");
        }

        @GetMapping("/failure/internal")
        void internal() {
            throw new IllegalStateException("database-password=secret");
        }
    }
}
