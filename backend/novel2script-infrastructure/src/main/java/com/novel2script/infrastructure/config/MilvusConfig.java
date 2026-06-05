package com.novel2script.infrastructure.config;

import io.milvus.client.MilvusServiceClient;
import io.milvus.param.ConnectParam;
import io.milvus.param.IndexType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Milvus vector database client configuration.
 */
@Slf4j
@Configuration
public class MilvusConfig {

    @Value("${milvus.host:localhost}")
    private String host;

    @Value("${milvus.port:19530}")
    private int port;

    @Value("${milvus.database:novel2script}")
    private String database;

    @Value("${milvus.connect-timeout-ms:10000}")
    private long connectTimeoutMs;

    @Value("${milvus.index-type:IVF_FLAT}")
    private String defaultIndexType;

    @Bean
    public MilvusServiceClient milvusServiceClient() {
        ConnectParam connectParam = ConnectParam.newBuilder()
                .withHost(host)
                .withPort(port)
                .withDatabaseName(database)
                .withConnectTimeout(connectTimeoutMs, java.util.concurrent.TimeUnit.MILLISECONDS)
                .build();

        MilvusServiceClient client = new MilvusServiceClient(connectParam);
        log.info("Milvus client connected to {}:{}, database={}", host, port, database);
        return client;
    }

    @Bean
    public IndexType defaultIndexType() {
        try {
            return IndexType.valueOf(defaultIndexType.toUpperCase());
        } catch (IllegalArgumentException e) {
            log.warn("Unknown index type '{}', falling back to IVF_FLAT", defaultIndexType);
            return IndexType.IVF_FLAT;
        }
    }
}
