package com.qaryati.integration.client;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

public class JdkGeoapifyHttpTransport implements GeoapifyHttpTransport {

    private final HttpClient client;

    public JdkGeoapifyHttpTransport(HttpClient client) {
        this.client = client;
    }

    @Override
    public GeoapifyHttpResponse get(URI uri, Duration requestTimeout) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(requestTimeout)
                .header("Accept", "application/json")
                .GET()
                .build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        return new GeoapifyHttpResponse(response.statusCode(), response.body());
    }
}
