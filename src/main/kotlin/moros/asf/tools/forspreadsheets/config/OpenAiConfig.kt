package moros.asf.tools.forspreadsheets.config

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ObjectNode
import org.apache.hc.client5.http.auth.AuthScope
import org.apache.hc.client5.http.auth.UsernamePasswordCredentials
import org.apache.hc.client5.http.config.ConnectionConfig
import org.apache.hc.client5.http.config.RequestConfig
import org.apache.hc.client5.http.impl.auth.BasicCredentialsProvider
import org.apache.hc.client5.http.impl.classic.HttpClients
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder
import org.apache.hc.client5.http.impl.routing.DefaultProxyRoutePlanner
import org.apache.hc.core5.http.HttpHost
import org.apache.hc.core5.util.Timeout
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Primary
import org.springframework.http.HttpRequest
import org.springframework.http.client.ClientHttpRequestExecution
import org.springframework.http.client.ClientHttpRequestInterceptor
import org.springframework.http.client.ClientHttpResponse
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory
import org.springframework.web.client.RestClient

// OpenAI API設定クラス
@Configuration
class OpenAiConfig {

    // extra_body削除インターセプター
    class ExtraBodyRemovalInterceptor(private val objectMapper: ObjectMapper) : ClientHttpRequestInterceptor {
        private val logger: Logger = LoggerFactory.getLogger(this::class.java)

        override fun intercept(
            request: HttpRequest,
            body: ByteArray,
            execution: ClientHttpRequestExecution
        ): ClientHttpResponse {
            if (body.isNotEmpty()) {
                try {
                    val bodyString = String(body, Charsets.UTF_8)

                    logger.debug("REQUEST TO: {}", request.uri)
                    logger.debug("REQUEST BODY (length=${body.size}): $bodyString")

                    // extra_bodyフィールドを含むか確認
                    if (bodyString.contains("\"extra_body\"")) {
                        logger.info("Removing 'extra_body' field from request to {}", request.uri)
                        val jsonNode = objectMapper.readTree(body)
                        if (jsonNode.isObject) {
                            (jsonNode as ObjectNode).remove("extra_body")
                            val modifiedBody = objectMapper.writeValueAsBytes(jsonNode)
                            request.headers.contentLength = modifiedBody.size.toLong()

                            logger.debug("Modified REQUEST BODY (length=${modifiedBody.size})")
                            return execution.execute(request, modifiedBody)
                        }
                    } else {
                        logger.debug("No 'extra_body' field detected in request")
                    }
                } catch (e: Exception) {
                    logger.error("Failed to process request body: ${e.message}", e)
                    throw e
                }
            }
            return execution.execute(request, body)
        }
    }

    // RestClient構築
    @Bean
    @Primary
    fun restClientBuilder(
        objectMapper: ObjectMapper,
        @Value("\${proxy.enabled:false}") proxyEnabled: Boolean,
        @Value("\${proxy.host:}") proxyHost: String?,
        @Value("\${proxy.port:}") proxyPortStr: String?,
        @Value("\${proxy.username:}") proxyUser: String?,
        @Value("\${proxy.password:}") proxyPass: String?,
        @Value("\${http-client.connect-timeout-seconds:#{null}}") connectTimeoutSeconds: Int?,
        @Value("\${http-client.read-timeout-seconds:#{null}}") readTimeoutSeconds: Int?,
    ): RestClient.Builder {
        val builder = RestClient.builder()

        // extra_body削除インターセプターを常に追加
        builder.requestInterceptor(ExtraBodyRemovalInterceptor(objectMapper))

        // responseTimeout
        val requestConfig = RequestConfig.custom()
            .apply { readTimeoutSeconds?.let { setResponseTimeout(Timeout.ofSeconds(it.toLong())) } }
            .build()

        // connectTimeout
        val httpClientBuilder = HttpClients.custom()
            .setDefaultRequestConfig(requestConfig)
        connectTimeoutSeconds?.let {
            val connectionConfig = ConnectionConfig.custom()
                .setConnectTimeout(Timeout.ofSeconds(it.toLong()))
                .build()
            val connectionManager = PoolingHttpClientConnectionManagerBuilder.create()
                .setDefaultConnectionConfig(connectionConfig)
                .build()
            httpClientBuilder.setConnectionManager(connectionManager)
        }

        // proxySetting
        val host = proxyHost?.takeIf { it.isNotBlank() }
        val port = proxyPortStr?.takeIf { it.isNotBlank() }?.toIntOrNull() ?: 0
        val username = proxyUser?.takeIf { it.isNotBlank() }
        val password = proxyPass?.takeIf { it.isNotBlank() }

        if (proxyEnabled && !host.isNullOrBlank() && port > 0) {
            val proxy = HttpHost(host, port)
            httpClientBuilder.setRoutePlanner(DefaultProxyRoutePlanner(proxy))

            if (!username.isNullOrBlank() && !password.isNullOrBlank()) {
                val credsProvider = BasicCredentialsProvider().apply {
                    setCredentials(
                        AuthScope(host, port),
                        UsernamePasswordCredentials(username, password.toCharArray())
                    )
                }
                httpClientBuilder.setDefaultCredentialsProvider(credsProvider)
            }
        }

        val httpClient = httpClientBuilder.build()
        val requestFactory = HttpComponentsClientHttpRequestFactory(httpClient)

        return builder.requestFactory(requestFactory)
    }
}