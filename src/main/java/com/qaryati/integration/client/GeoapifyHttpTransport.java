package com.qaryati.integration.client;

import java.io.IOException;
import java.net.URI;
import java.time.Duration;

public interface GeoapifyHttpTransport {
    GeoapifyHttpResponse get(URI uri, Duration requestTimeout) throws IOException, InterruptedException;
}
