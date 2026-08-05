package com.logaudit.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.ThreadPoolExecutor;

@Configuration
@EnableAsync
public class ThreadPoolConfig {

    @Value("${app.thread-pool.core-pool-size}")
    private int corePoolSize;

    @Value("${app.thread-pool.max-pool-size}")
    private int maxPoolSize;

    @Value("${app.thread-pool.queue-capacity}")
    private int queueCapacity;

    @Value("${app.thread-pool.keep-alive-seconds}")
    private int keepAliveSeconds;

    @Bean("logImportExecutor")
    public ThreadPoolTaskExecutor logImportExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(corePoolSize);     // 核心线程数：4
        executor.setMaxPoolSize(maxPoolSize);        // 最大线程数：8
        executor.setQueueCapacity(queueCapacity);    // 阻塞队列容量：100
        executor.setKeepAliveSeconds(keepAliveSeconds); // 空闲线程存活时间
        executor.setThreadNamePrefix("log-import-"); // 线程名前缀
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.initialize();
        return executor;
    }
}