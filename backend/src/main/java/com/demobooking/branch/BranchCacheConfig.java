package com.demobooking.branch;

import com.demobooking.branch.dto.BranchResponse;
import com.demobooking.branch.dto.ServiceTypeResponse;
import java.util.List;
import org.springframework.boot.cache.autoconfigure.RedisCacheManagerBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.serializer.JacksonJsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import tools.jackson.databind.JavaType;
import tools.jackson.databind.type.TypeFactory;

/**
 * The branch catalogue's caches, each with a serializer bound to its exact element type - see
 * config.CacheConfig for why that beats Jackson default typing. Lives here, not in config, so
 * config never depends on branch and the packages stay free of cycles.
 */
@Configuration
class BranchCacheConfig {

	@Bean
	RedisCacheManagerBuilderCustomizer branchCacheCustomizer(RedisCacheConfiguration cacheDefaults) {
		return builder -> builder
				.withCacheConfiguration("branches", cacheDefaults.serializeValuesWith(listOf(BranchResponse.class)))
				.withCacheConfiguration("serviceTypes", cacheDefaults.serializeValuesWith(listOf(ServiceTypeResponse.class)));
	}

	private static <T> RedisSerializationContext.SerializationPair<Object> listOf(Class<T> elementType) {
		JavaType listType = TypeFactory.createDefaultInstance().constructCollectionType(List.class, elementType);
		return RedisSerializationContext.SerializationPair.fromSerializer(new JacksonJsonRedisSerializer<Object>(listType));
	}

}
