package com.imzhizi.breeze.devtools.mcp;

import com.intellij.notification.NotificationGroupManager;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.imzhizi.breeze.devtools.settings.BreezeSettings;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.concurrent.Executors;

/**
 * Application-level service that manages the embedded MCP HTTP server.
 * Changed from project-level to application-level so that:
 *   1. Only one server runs regardless of how many projects are open.
 *   2. Tool calls always operate on the "current active project" (resolved
 *      dynamically via WindowManager), not on the project that happened to
 *      open first and eagerly created this service.
 */
public final class McpServerService implements Disposable {

    private static final Logger LOG = Logger.getInstance(McpServerService.class);

    private HttpServer httpServer;

    public McpServerService() {
        startIfEnabled();
    }

    /** Returns the application-wide McpServerService instance. */
    public static McpServerService getInstance() {
        return ApplicationManager.getApplication().getService(McpServerService.class);
    }

    /** Start the HTTP server if MCP is enabled in settings. */
    public void startIfEnabled() {
        BreezeSettings settings = BreezeSettings.getInstance();
        if (!settings.mcpEnabled) {
            LOG.info("Breeze MCP Server is disabled in settings.");
            return;
        }
        start(settings.mcpPort);
    }

    /** Start the HTTP server on the given port. Stops any previously running server first. */
    public synchronized void start(int port) {
        stop();
        try {
            httpServer = HttpServer.create(new InetSocketAddress("127.0.0.1", port), 0);
            McpToolDispatcher dispatcher = new McpToolDispatcher();
            httpServer.createContext("/mcp", new McpHttpHandler(dispatcher));
            httpServer.createContext("/sse", new McpSseHandler(dispatcher));
            httpServer.setExecutor(Executors.newCachedThreadPool());
            httpServer.start();
            LOG.info("Breeze MCP Server started on port " + port);
            notifyStarted(port);
        } catch (IOException e) {
            LOG.warn("Breeze MCP Server failed to start on port " + port + ": " + e.getMessage());
            notifyFailed(port, e.getMessage());
        }
    }

    /** Stop the HTTP server if running. */
    public synchronized void stop() {
        if (httpServer != null) {
            httpServer.stop(0);
            httpServer = null;
            LOG.info("Breeze MCP Server stopped.");
        }
    }

    /** Returns true if the server is currently running. */
    public synchronized boolean isRunning() {
        return httpServer != null;
    }

    /** Returns the port the server is listening on, or -1 if not running. */
    public synchronized int getPort() {
        if (httpServer == null) return -1;
        return httpServer.getAddress().getPort();
    }

    @Override
    public void dispose() {
        stop();
    }

    // ------------------------------------------------------------------
    // Notifications
    // ------------------------------------------------------------------

    private void notifyStarted(int port) {
        ApplicationManager.getApplication().invokeLater(() ->
                NotificationGroupManager.getInstance()
                        .getNotificationGroup("Breeze Dev Helper")
                        .createNotification(
                                "Breeze Dev Helper: MCP Server 监听端口：" + port,
                                NotificationType.INFORMATION)
                        .notify(null));
    }

    private void notifyFailed(int port, String reason) {
        ApplicationManager.getApplication().invokeLater(() ->
                NotificationGroupManager.getInstance()
                        .getNotificationGroup("Breeze Dev Helper")
                        .createNotification(
                                "Breeze Dev Helper: MCP Server 启动失败（端口 " + port + "）：" + reason,
                                NotificationType.WARNING)
                        .notify(null));
    }
}
