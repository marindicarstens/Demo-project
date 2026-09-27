package com.demobooking.config;

import org.springframework.cache.annotation.CachingConfigurer;
import org.springframework.cache.interceptor.CacheErrorHandler;
import org.springframework.cache.interceptor.LoggingCacheErrorHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;

/**
 * Cached values (branches, service types) are plain records, not java.io.Serializable - and
 * shouldn't need to be just to be cacheable. Redis's default cache serializer is JDK native
 * serialization, which requires exactly that.
 *
 * Each cache gets its own serializer bound to its exact generic type (JacksonJsonRedisSerializer
 * built from a JavaType, not a Class), rather than one generic Object-typed serializer relying on
 * Jackson's "default typing" to embed class metadata for reconstruction. Default typing exists
 * for exactly this generic-collection-caching case, but it's an opt-in-only, explicitly-named
 * "unsafe" setting in this Jackson version for good reason (embedding arbitrary class names in
 * serialized data is a deserialization-gadget attack surface if that data could ever come from
 * somewhere untrusted). Binding each cache to its one known type sidesteps that trade-off
 * entirely - there's never any ambiguity to resolve with embedded type metadata, so there's
 * nothing "unsafe" to opt into.
 *
 * The cache is an optimisation only: if Redis is unreachable, a failed read or write is logged at
 * WARN and the call falls through to the database (see {@link #errorHandler()}).
 */
@Configuration
class CacheConfig implements CachingConfigurer {

	// Spring's default handler rethrows, which would turn a Redis outage into a 500 on every
	// cached read; this one logs at WARN and lets the cached method run as if uncached.
	@Bean
	@Override
	public CacheErrorHandler errorHandler() {
		return new LoggingCacheErrorHandler();
	}

	// Boot's cache manager takes this bean as every cache's defaults. Each cache's own typed
	// serializer is layered on top by the package that owns the cached type (e.g.
	// branch.BranchCacheConfig), so config doesn't depend on those packages.
	@Bean
	RedisCacheConfiguration cacheDefaults(AppProperties appProperties) {
		return RedisCacheConfiguration.defaultCacheConfig().entryTtl(appProperties.cache().ttl());
	}

}
