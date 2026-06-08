package com.imzhizi.breeze.devtools.mcp;

import com.intellij.openapi.diagnostic.Logger;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

/**
 * Handles POST /mcp requests (JSON-RPC 2.0 over HTTP).
 * Each request is a JSON-RPC call; the response is returned synchronously.
 */
public final class McpHttpHandler implements HttpHandler {

    private static final Logger LOG = Logger.getInstance(McpHttpHandler.class);

    private final McpToolDispatcher dispatcher;

    public McpHttpHandler(McpToolDispatcher dispatcher) {
        this.dispatcher = dispatcher;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        // Add CORS headers so browser-based MCP clients can connect
        exchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
        exchange.getResponseHeaders().add("Access-Control-Allow-Methods", "POST, OPTIONS");
        exchange.getResponseHeaders().add("Access-Control-Allow-Headers", "Content-Type");

        String method = exchange.getRequestMethod();

        if ("OPTIONS".equalsIgnoreCase(method)) {
            exchange.sendResponseHeaders(204, -1);
            return;
        }

        if (!"POST".equalsIgnoreCase(method)) {
            sendError(exchange, 405, "Method Not Allowed");
            return;
        }

        String requestBody;
        try (InputStream is = exchange.getRequestBody()) {
            requestBody = new String(is.readAllBytes(), StandardCharsets.UTF_8);
        }

        LOG.debug("MCP request: " + requestBody);

        String responseBody;
        try {
            responseBody = dispatcher.dispatch(requestBody);
        } catch (Exception e) {
            LOG.warn("MCP dispatch error", e);
            responseBody = McpJsonUtil.errorResponse(null, -32603, "Internal error: " + e.getMessage());
        }

        LOG.debug("MCP response: " + responseBody);

        byte[] responseBytes = responseBody.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json; charset=utf-8");
        exchange.sendResponseHeaders(200, responseBytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(responseBytes);
        }
    }

    private static void sendError(HttpExchange exchange, int code, String message) throws IOException {
        byte[] body = message.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(code, body.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(body);
        }
    }
}
