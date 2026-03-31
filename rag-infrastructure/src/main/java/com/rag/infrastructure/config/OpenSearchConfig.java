package com.rag.infrastructure.config;

import java.net.URI;
import org.apache.hc.core5.http.HttpHost;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.transport.httpclient5.ApacheHttpClient5TransportBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenSearchConfig {

    @Bean
    public OpenSearchClient openSearchClient(ServiceRegistryConfig.VectorStoreProperties props) {
        HttpHost host = HttpHost.create(URI.create(props.getUrl()));
        var transport = ApacheHttpClient5TransportBuilder
            .builder(host)
            .build();
        return new OpenSearchClient(transport);
    }
}
