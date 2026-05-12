package com.amalitech.hilfe.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

@EnableCaching
@Configuration
public class CacheConfig {

    // Cache name constants — imported by services to avoid string literals
    public static final String STATUSES          = "statuses";
    public static final String SEVERITIES        = "severities";
    public static final String LOCATIONS         = "locations";
    public static final String CATEGORIES        = "incidentCategories";
    public static final String TOPICS            = "incidentTopics";
    public static final String AGENT_DASHBOARD   = "agentDashboard";
    public static final String ADMIN_DASHBOARD   = "adminDashboard";

    @Bean
    public CacheManager cacheManager() {
        CaffeineCacheManager manager = new CaffeineCacheManager();

        // Reference data: changes only on admin action — long TTL, tiny size
        manager.registerCustomCache(STATUSES,
                Caffeine.newBuilder().expireAfterWrite(6, TimeUnit.HOURS).maximumSize(1).build());
        manager.registerCustomCache(SEVERITIES,
                Caffeine.newBuilder().expireAfterWrite(6, TimeUnit.HOURS).maximumSize(1).build());
        manager.registerCustomCache(LOCATIONS,
                Caffeine.newBuilder().expireAfterWrite(6, TimeUnit.HOURS).maximumSize(1).build());
        manager.registerCustomCache(CATEGORIES,
                Caffeine.newBuilder().expireAfterWrite(6, TimeUnit.HOURS).maximumSize(1).build());

        // Topics keyed by categoryId — evicted on category/topic mutation
        manager.registerCustomCache(TOPICS,
                Caffeine.newBuilder().expireAfterWrite(6, TimeUnit.HOURS).maximumSize(200).build());

        // Dashboard aggregates: short TTL, evicted on incident mutations
        manager.registerCustomCache(AGENT_DASHBOARD,
                Caffeine.newBuilder().expireAfterWrite(5, TimeUnit.MINUTES).maximumSize(500).build());
        manager.registerCustomCache(ADMIN_DASHBOARD,
                Caffeine.newBuilder().expireAfterWrite(5, TimeUnit.MINUTES).maximumSize(1).build());

        return manager;
    }
}
