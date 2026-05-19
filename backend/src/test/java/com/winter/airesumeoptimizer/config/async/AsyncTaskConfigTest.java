package com.winter.airesumeoptimizer.config.async;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.ThreadPoolExecutor;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

class AsyncTaskConfigTest {

    @Test
    void applicationTaskExecutorShouldUseConfiguredBoundedPool() {
        AsyncTaskProperties properties = new AsyncTaskProperties();
        properties.setCorePoolSize(2);
        properties.setMaxPoolSize(5);
        properties.setQueueCapacity(20);
        properties.setThreadNamePrefix("test-task-");
        properties.setAwaitTerminationSeconds(7);

        ThreadPoolTaskExecutor executor = new AsyncTaskConfig().applicationTaskExecutor(properties);
        executor.initialize();

        try {
            assertThat(executor.getCorePoolSize()).isEqualTo(2);
            assertThat(executor.getMaxPoolSize()).isEqualTo(5);
            assertThat(executor.getThreadNamePrefix()).isEqualTo("test-task-");
            assertThat(executor.getThreadPoolExecutor().getQueue().remainingCapacity()).isEqualTo(20);
            assertThat(executor.getThreadPoolExecutor().getRejectedExecutionHandler())
                    .isInstanceOf(ThreadPoolExecutor.AbortPolicy.class);
        } finally {
            executor.shutdown();
        }
    }
}
