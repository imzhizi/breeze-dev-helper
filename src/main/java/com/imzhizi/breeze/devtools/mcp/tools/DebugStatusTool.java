package com.imzhizi.breeze.devtools.mcp.tools;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.xdebugger.XDebugSession;
import com.intellij.xdebugger.XDebuggerManager;
import com.intellij.xdebugger.XSourcePosition;
import com.imzhizi.breeze.devtools.mcp.McpJsonUtil;
import com.imzhizi.breeze.devtools.mcp.McpTool;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * MCP tool: get_debug_status
 *
 * Returns the current debug session state: NO_SESSION, RUNNING, or PAUSED.
 * When paused, also returns the current location (className:line).
 */
public final class DebugStatusTool implements McpTool {

    @Override
    public String getName() {
        return "get_debug_status";
    }

    @Override
    public String getDescription() {
        return "Get the current debug session status: NO_SESSION, RUNNING, or PAUSED. " +
                "When paused, returns the current location (className:line). " +
                "Call this before read_variables or step/resume tools to verify session state.";
    }

    @Override
    public JsonObject getInputSchema() {
        JsonObject schema = new JsonObject();
        schema.addProperty("type", "object");
        schema.add("properties", new JsonObject());
        return schema;
    }

    @Override
    public String execute(JsonObject args, JsonElement id) {
        CompletableFuture<String> future = new CompletableFuture<>();

        ApplicationManager.getApplication().invokeLater(() -> {
            try {
                Project project = BreakpointHelper.getActiveProject();
                if (project == null) {
                    future.complete(McpJsonUtil.successTextResponse(id,
                            "NO_SESSION: No open project found."));
                    return;
                }

                XDebugSession session = XDebuggerManager.getInstance(project).getCurrentSession();
                if (session == null) {
                    future.complete(McpJsonUtil.successTextResponse(id,
                            "NO_SESSION: No active debug session."));
                    return;
                }

                String name = session.getSessionName();
                if (!session.isPaused()) {
                    future.complete(McpJsonUtil.successTextResponse(id,
                            "RUNNING: Debug session '" + name + "' is running."));
                    return;
                }

                String location = resolveLocation(project, session);
                future.complete(McpJsonUtil.successTextResponse(id,
                        "PAUSED: Debug session '" + name + "' is paused at " + location));
            } catch (Exception e) {
                future.complete(McpJsonUtil.toolErrorResponse(id,
                        "Failed to get debug status: " + e.getMessage()));
            }
        });

        try {
            return future.get(5, TimeUnit.SECONDS);
        } catch (Exception e) {
            return McpJsonUtil.toolErrorResponse(id, "get_debug_status timed out: " + e.getMessage());
        }
    }

    static String resolveLocation(Project project, XDebugSession session) {
        try {
            XSourcePosition pos = session.getCurrentPosition();
            if (pos == null) return "(unknown location)";
            String className = BreakpointHelper.resolveClassName(project, pos.getFile().getUrl());
            if (className != null) return className + ":" + (pos.getLine() + 1);
            return pos.getFile().getName() + ":" + (pos.getLine() + 1);
        } catch (Exception e) {
            return "(unknown location)";
        }
    }
}
