package com.winter.airesumeoptimizer.infra.ai;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

/**
 * Explicit non-production adapter for Demo and browser/integration tests. It
 * never opens a network connection or reads a credential, and its only
 * stateful behavior is a deterministic one-time failure marker for recovery
 * tests. Production continues to use the pinned OpenAI-compatible adapter.
 */
@Service
@Profile({"demo", "phase9-e2e"})
public class DeterministicFakeAiProviderAdapter implements AiProviderAdapter {

    private static final String FAILURE_MARKER = "[[FAKE_PROVIDER_FAIL_ONCE]]";
    private static final Pattern ORIGINAL_BULLET = Pattern.compile(
            "<<<ORIGINAL_BULLET\\s*(.*?)\\s*ORIGINAL_BULLET", Pattern.DOTALL);

    private final ObjectMapper objectMapper;
    private final Set<String> failedRecoveryRequests = ConcurrentHashMap.newKeySet();

    public DeterministicFakeAiProviderAdapter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public AiProviderResponse complete(AiProviderRequest request) {
        String policyId = policyId(request);
        String untrusted = untrustedData(request);
        if (("JOB_DESCRIPTION_PARSE".equals(policyId) || "EVIDENCE_MATCH".equals(policyId))
                && untrusted.contains(FAILURE_MARKER)
                && failedRecoveryRequests.add(policyId + ':' + Integer.toHexString(untrusted.hashCode()))) {
            // Non-retryable on purpose: the persisted OptimizationTask must expose
            // the same-task Retry path rather than hiding this recovery scenario.
            throw new AiGatewayException(AiFailureCode.PROVIDER_UNAVAILABLE, "演示 Provider 暂时不可用");
        }
        return new AiProviderResponse(responseFor(policyId, untrusted), 12L, 8L);
    }

    private String responseFor(String policyId, String untrusted) {
        return switch (policyId) {
            case "JOB_DESCRIPTION_PARSE" -> """
                    {"jobTitle":"Java 后端开发工程师","requiredSkills":["Java"],
                    "bonusSkills":[],"experienceSignals":[],"responsibilities":[],
                    "keywords":["Java"],"summary":"用于非生产演示的确定性岗位解析结果"}
                    """;
            case "EVIDENCE_MATCH" -> """
                    {"requirements":[{"requirement":"Java","importance":"REQUIRED",
                    "matchLevel":"MATCHED","conclusion":"","suggestion":"",
                    "evidences":[{"section":"技能","quote":"Java","supportLevel":"SUFFICIENT"}]}]}
                    """;
            case "BULLET_REWRITE" -> bulletRewrite(untrusted);
            case "CREDENTIAL_TEST" -> "{\"ok\":true}";
            default -> "{\"items\":[]}";
        };
    }

    private String bulletRewrite(String untrusted) {
        Matcher matcher = ORIGINAL_BULLET.matcher(untrusted == null ? "" : untrusted);
        String original = matcher.find() ? matcher.group(1).strip() : "";
        try {
            // Returning the frozen original is intentionally conservative: the
            // real fact-closure validator still runs and no fake fact is created.
            return objectMapper.writeValueAsString(Map.of(
                    "suggestedText", original,
                    "reason", "演示环境仅返回不新增事实的确定性建议。"));
        } catch (JsonProcessingException exception) {
            throw new AiGatewayException(AiFailureCode.CONFIGURATION_INVALID, "演示 Provider 配置异常");
        }
    }

    private String policyId(AiProviderRequest request) {
        if (request == null || request.messages().isEmpty()) {
            return "";
        }
        String system = request.messages().getFirst().content();
        if (system == null) {
            return "";
        }
        for (String candidate : new String[]{
                "JOB_DESCRIPTION_PARSE", "EVIDENCE_MATCH", "BULLET_REWRITE", "CREDENTIAL_TEST"}) {
            if (system.contains("Policy ID: " + candidate)) {
                return candidate;
            }
        }
        return "";
    }

    private String untrustedData(AiProviderRequest request) {
        if (request == null || request.messages().size() < 2) {
            return "";
        }
        String content = request.messages().get(1).content();
        return content == null ? "" : content;
    }
}
