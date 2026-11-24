package com.qaryati.integration.adapter;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import com.qaryati.integration.client.GeoapifyHttpResponse;
import com.qaryati.integration.client.GeoapifyHttpTransport;
import com.qaryati.integration.config.GeoapifyProperties;
import com.qaryati.integration.dto.GeoapifyGeocodingResponse;
import com.qaryati.integration.dto.GeocodingResult;
import com.qaryati.integration.dto.GeocodingProviderAttribution;
import com.qaryati.integration.exception.GeocodingProviderException;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.List;

@Component
public class GeoapifyGeocodingProvider implements GeocodingProvider {

    static final String PROVIDER_NAME = "geoapify";

    private final GeoapifyHttpTransport transport;
    private final ObjectMapper objectMapper;
    private final GeoapifyProperties properties;

    public GeoapifyGeocodingProvider(GeoapifyHttpTransport transport, ObjectMapper objectMapper,
                                     GeoapifyProperties properties) {
        this.transport = transport;
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    @Override
    public String providerName() {
        return PROVIDER_NAME;
    }

    @Override
    public GeocodingProviderAttribution attribution() {
        return new GeocodingProviderAttribution(PROVIDER_NAME, "Powered by Geoapify", "https://www.geoapify.com/");
    }

    @Override
    public List<GeocodingResult> forward(String query) {
        return request("/search?text=" + encode(query));
    }

    @Override
    public List<GeocodingResult> reverse(BigDecimal latitude, BigDecimal longitude) {
        return request("/reverse?lat=" + encode(latitude.toPlainString())
                + "&lon=" + encode(longitude.toPlainString()));
    }

    private List<GeocodingResult> request(String endpoint) {
        String apiKey = properties.getApiKey();
        if (apiKey == null || apiKey.isBlank()) {
            throw new GeocodingProviderException(GeocodingProviderException.Reason.CONFIGURATION);
        }

        URI uri = URI.create(stripTrailingSlash(properties.getBaseUrl()) + endpoint
                + "&format=json&apiKey=" + encode(apiKey));
        int attempts = Math.max(0, Math.min(properties.getMaxRetries(), 1)) + 1;

        for (int attempt = 0; attempt < attempts; attempt++) {
            try {
                GeoapifyHttpResponse response = transport.get(uri, properties.getRequestTimeout());
                if (response.statusCode() >= 200 && response.statusCode() < 300) {
                    return parse(response.body());
                }
                if (response.statusCode() == 429) {
                    throw new GeocodingProviderException(GeocodingProviderException.Reason.RATE_LIMITED);
                }
                if (response.statusCode() >= 400 && response.statusCode() < 500) {
                    throw new GeocodingProviderException(GeocodingProviderException.Reason.UPSTREAM);
                }
                if (response.statusCode() >= 500 && attempt + 1 < attempts) {
                    continue;
                }
                throw new GeocodingProviderException(GeocodingProviderException.Reason.UPSTREAM);
            } catch (HttpTimeoutException ex) {
                throw new GeocodingProviderException(GeocodingProviderException.Reason.TIMEOUT, ex);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                throw new GeocodingProviderException(GeocodingProviderException.Reason.NETWORK, ex);
            } catch (IOException ex) {
                if (attempt + 1 >= attempts) {
                    throw new GeocodingProviderException(GeocodingProviderException.Reason.NETWORK, ex);
                }
            }
        }
        throw new GeocodingProviderException(GeocodingProviderException.Reason.UPSTREAM);
    }

    private List<GeocodingResult> parse(String body) {
        try {
            JsonNode root = objectMapper.readTree(body);
            JsonNode results = root == null ? null : root.get("results");
            if (results == null || !results.isArray()) {
                throw new GeocodingProviderException(GeocodingProviderException.Reason.MALFORMED_RESPONSE);
            }
            GeoapifyGeocodingResponse response = new GeoapifyGeocodingResponse(
                    results.valueStream().map(this::toProviderResult).toList());
            return response.results().stream().map(this::toResult).toList();
        } catch (JacksonException ex) {
            throw new GeocodingProviderException(GeocodingProviderException.Reason.MALFORMED_RESPONSE, ex);
        }
    }

    private GeoapifyGeocodingResponse.Result toProviderResult(JsonNode result) {
        if (result == null || !result.isObject() || !number(result, "lat") || !number(result, "lon")) {
            throw new GeocodingProviderException(GeocodingProviderException.Reason.MALFORMED_RESPONSE);
        }
        BigDecimal latitude = result.get("lat").decimalValue();
        BigDecimal longitude = result.get("lon").decimalValue();
        if (latitude.compareTo(BigDecimal.valueOf(-90)) < 0 || latitude.compareTo(BigDecimal.valueOf(90)) > 0
                || longitude.compareTo(BigDecimal.valueOf(-180)) < 0 || longitude.compareTo(BigDecimal.valueOf(180)) > 0) {
            throw new GeocodingProviderException(GeocodingProviderException.Reason.MALFORMED_RESPONSE);
        }
        JsonNode datasource = result.get("datasource");
        if (datasource != null && !datasource.isNull() && !datasource.isObject()) {
            throw new GeocodingProviderException(GeocodingProviderException.Reason.MALFORMED_RESPONSE);
        }
        return new GeoapifyGeocodingResponse.Result(latitude, longitude,
                text(result, "formatted"), text(result, "address_line1"), text(result, "address_line2"),
                text(result, "city"), text(result, "country"), new GeoapifyGeocodingResponse.Datasource(
                        text(datasource, "sourcename"), text(datasource, "attribution")));
    }

    private GeocodingResult toResult(GeoapifyGeocodingResponse.Result result) {
        return new GeocodingResult(result.lat(), result.lon(), result.formatted(), result.addressLine1(),
                result.addressLine2(), result.city(), result.country(), result.datasource().sourcename(),
                result.datasource().attribution());
    }

    private boolean number(JsonNode object, String field) {
        JsonNode value = object.get(field);
        return value != null && value.isNumber();
    }

    private String text(JsonNode object, String field) {
        if (object == null) {
            return null;
        }
        JsonNode value = object.get(field);
        if (value == null || value.isNull()) {
            return null;
        }
        if (!value.isString()) {
            throw new GeocodingProviderException(GeocodingProviderException.Reason.MALFORMED_RESPONSE);
        }
        return value.asString();
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private String stripTrailingSlash(String value) {
        return value == null ? "" : value.replaceAll("/+$", "");
    }
}
