package com.webvideoplayer.player.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

@Configuration
@EnableAsync
public class AsyncConfig {

    @Bean(name = "previewExecutor")
    public Executor previewExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(2);
        executor.setQueueCapacity(20);
        executor.setThreadNamePrefix("preview-");
        executor.setRejectedExecutionHandler(
                new ThreadPoolExecutor.AbortPolicy()
        );
        executor.initialize();
        return executor;
    }

    @Bean(name = "catalogSyncExecutor")
    public Executor catalogSyncExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(1);
        executor.setQueueCapacity(20);
        executor.setThreadNamePrefix("catalog-sync-");
        executor.setRejectedExecutionHandler(
                new ThreadPoolExecutor.AbortPolicy()
        );
        executor.initialize();
        return executor;
    }

    @Bean(name = "technicalInfoExecutor")
    public Executor technicalInfoExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(1);
        executor.setQueueCapacity(20);
        executor.setThreadNamePrefix("technical-info-");
        executor.setRejectedExecutionHandler(
                new ThreadPoolExecutor.AbortPolicy()
        );
        executor.initialize();
        return executor;
    }
}