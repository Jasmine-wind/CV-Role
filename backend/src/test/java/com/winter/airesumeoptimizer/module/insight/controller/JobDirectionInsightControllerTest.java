package com.winter.airesumeoptimizer.module.insight.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.winter.airesumeoptimizer.module.insight.service.JobDirectionInsightService;
import com.winter.airesumeoptimizer.module.insight.vo.JobDirectionInsightsVO;
import com.winter.airesumeoptimizer.security.AuthenticatedUser;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.Authentication;

class JobDirectionInsightControllerTest {

    @Test
    void alwaysUsesTheAuthenticatedUsersIdentity() {
        JobDirectionInsightService service = mock(JobDirectionInsightService.class);
        JobDirectionInsightsVO expected = JobDirectionInsightsVO.builder().cohorts(java.util.List.of()).build();
        when(service.getInsights(42L)).thenReturn(expected);
        Authentication authentication = mock(Authentication.class);
        when(authentication.getPrincipal()).thenReturn(new AuthenticatedUser(42L, "owner"));

        assertThat(new JobDirectionInsightController(service).get(authentication).getData()).isSameAs(expected);

        verify(service).getInsights(42L);
    }
}
