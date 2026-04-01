package com.rag.infrastructure.config;

import java.net.URI;
import org.apache.hc.client5.http.auth.AuthScope;
import org.apache.hc.client5.http.auth.UsernamePasswordCredentials;
import org.apache.hc.client5.http.impl.auth.BasicCredentialsProvider;
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
        var builder = ApacheHttpClient5TransportBuilder.builder(host);

        if (props.getUsername() != null && props.getPassword() != null) {
            builder.setHttpClientConfigCallback(httpClientBuilder -> {
                var credentialsProvider = new BasicCredentialsProvider();
                credentialsProvider.setCredentials(
                    new AuthScope(host),
                    new UsernamePasswordCredentials(
                        props.getUsername(), props.getPassword().toCharArray()));
                return httpClientBuilder.setDefaultCredentialsProvider(credentialsProvider);
            });
        }

        var transport = builder.build();
        return new OpenSearchClient(transport);
    }
}
