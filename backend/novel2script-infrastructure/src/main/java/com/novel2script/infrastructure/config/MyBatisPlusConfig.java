package com.novel2script.infrastructure.config;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Configuration;

/**
 * MyBatis-Plus configuration — mapper scanning.
 * <p>
 * Pagination and other inner interceptors are auto-configured by
 * {@code MybatisPlusInnerInterceptorAutoConfiguration} in MyBatis-Plus 3.5.9+.
 * Configure via application.yml: {@code mybatis-plus.pagination.db-type=mysql}.
 */
@Configuration
@MapperScan(basePackages = "com.novel2script.infrastructure.persistence.mapper")
public class MyBatisPlusConfig {
}
