package com.imzhizi.breeze.devtools.mcp;

import com.intellij.openapi.diagnostic.Logger;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

/**
 * Handles GET /sse requests (Server-Sent Events endpoint for MCP discovery).
 *
 * According to the MCP HTTP+SSE transport spec:
 * - The client connects to /sse to receive the server's endpoint URL.
 * - The server sends a single "endpoint" event pointing to /mcp.
 * - The connection then stays open (for server-initiated messages).
 *
 * For our use-case (synchronous tool calls over POST /mcp), we keep the
 * connection alive with periodic keep-alive comments.
 */
public final class McpSseHandler implements HttpHandler {

    private static final Logger LOG = Logger.getInstance(McpSseHandler.class);
    private static final long KEEPALIVE_INTERVAL_MS = 15_000;

    private final McpToolDispatcher dispatcher;

    public McpSseHandler(McpToolDispatcher dispatcher) {
        this.dispatcher = dispatcher;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        exchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
        exchange.getResponseHeaders().add("Cache-Control", "no-cache");
        exchange.getResponseHeaders().add("Content-Type", "text/event-stream; charset=utf-8");
        exchange.getResponseHeaders().add("Connection", "keep-alive");
        // -1 means chunked / streaming
        exchange.sendResponseHeaders(200, 0);

        // Determine the base URL from the request (e.g. http://localhost:19876)
        String host = exchange.getRequestHeaders().getFirst("Host");
        if (host == null || host.isBlank()) {
            host = "localhost:" + exchange.getLocalAddress().getPort();
        }
        String mcpEndpoint = "http://" + host + "/mcp";

        try (OutputStream os = exchange.getResponseBody()) {
            // Send the endpoint event so the MCP client knows where to POST
            sendEvent(os, "endpoint", mcpEndpoint);

            // Keep the connection alive until the client disconnects
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    Thread.sleep(KEEPALIVE_INTERVAL_MS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
                // SSE comment keeps the connection alive without confusing the client
                sendComment(os, "keep-alive");
            }
        } catch (IOException e) {
            // Client disconnected – normal
            LOG.debug("MCP SSE client disconnected: " + e.getMessage());
        }
    }

    private static void sendEvent(OutputStream os, String eventType, String data) throws IOException {
        String payload = "event: " + eventType + "\ndata: " + data + "\n\n";
        os.write(payload.getBytes(StandardCharsets.UTF_8));
        os.flush();
    }

    private static void sendComment(OutputStream os, String comment) throws IOException {
        String payload = ": " + comment + "\n\n";
        os.write(payload.getBytes(StandardCharsets.UTF_8));
        os.flush();
    }
}
