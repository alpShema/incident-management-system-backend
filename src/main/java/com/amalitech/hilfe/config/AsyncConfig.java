package com.amalitech.hilfe.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

@Configuration
public class AsyncConfig {

    @Bean(name = "applicationTaskExecutor")
    public Executor applicationTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setThreadNamePrefix("hilfe-async-");
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(200);
        // Default AbortPolicy throws RejectedExecutionException straight back to whatever thread
        // is submitting the task -- for an @Async method, that means the exception surfaces
        // synchronously inside the calling request instead of politely staying in the background.
        // CallerRunsPolicy runs the task on the caller's own thread instead of rejecting it once
        // the queue is full, trading a brief slowdown for never dropping work or blowing up an
        // unrelated request just because the background queue was momentarily saturated.
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.initialize();
        return executor;
    }
}
