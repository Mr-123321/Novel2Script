package com.novel2script.infrastructure.config;

import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * MyBatis-Plus configuration.
 *
 * <p>Configures:
 * <ul>
 *   <li>Mapper scanning for the infrastructure mapper package</li>
 *   <li>MybatisPlusInterceptor (pagination is built-in since MP 3.5.5+)</li>
 * </ul>
 *
 * <p>JSON column handling is automatic: MyBatis-Plus detects Jackson on the classpath
 * and uses {@code JacksonTypeHandler} for fields annotated with
 * {@code @TableField(typeHandler = JacksonTypeHandler.class)}.
 */
@Configuration
@MapperScan("com.novel2script.infrastructure.mapper")
public class MyBatisPlusConfig {

    /**
     * MyBatis-Plus interceptor chain.
     * In MP 3.5.5+, pagination is built into MybatisPlusInterceptor —
     * no separate PaginationInnerInterceptor needed.
     */
    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        return new MybatisPlusInterceptor();
    }
}
