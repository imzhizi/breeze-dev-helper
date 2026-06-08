package com.imzhizi.breeze.devtools.mcp.tools;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.xdebugger.XDebugSession;
import com.intellij.xdebugger.XDebuggerManager;
import com.imzhizi.breeze.devtools.mcp.McpJsonUtil;
import com.imzhizi.breeze.devtools.mcp.McpTool;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * MCP tool: stop_debug
 *
 * Stops the currently active debug session(s).
 * No arguments required.
 *
 * Note: no Project is captured at construction time.  The active project is
 * resolved dynamically each time execute() is called via
 * {@link BreakpointHelper#getActiveProject()}.
 */
public final class RemoteDebugStopTool implements McpTool {

    public RemoteDebugStopTool() {
    }

    @Override
    public String getName() {
        return "stop_debug";
    }

    @Override
    public String getDescription() {
        return "Stop all active debug sessions in the current project. " +
                "Equivalent to clicking the Stop button in the Debug tool window. " +
                "Returns a confirmation if any session was stopped, or a message if no session was active.";
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
                // Resolve the active project at call time, not at construction time.
                Project project = BreakpointHelper.getActiveProject();
                if (project == null) {
                    future.complete(McpJsonUtil.successTextResponse(id,
                            "No open project found."));
                    return;
                }

                XDebuggerManager debuggerManager = XDebuggerManager.getInstance(project);
                XDebugSession[] sessions = debuggerManager.getDebugSessions();

                if (sessions == null || sessions.length == 0) {
                    future.complete(McpJsonUtil.successTextResponse(id,
                            "No active debug sessions found."));
                    return;
                }

                List<String> stoppedNames = new ArrayList<>();
                for (XDebugSession session : sessions) {
                    stoppedNames.add(session.getSessionName());
                    session.stop();
                }

                future.complete(McpJsonUtil.successTextResponse(id,
                        "Stopped debug session(s): " + String.join(", ", stoppedNames)));
            } catch (Exception e) {
                future.complete(McpJsonUtil.toolErrorResponse(id,
                        "Failed to stop debug session: " + e.getMessage()));
            }
        });

        try {
            return future.get(10, TimeUnit.SECONDS);
        } catch (Exception e) {
            return McpJsonUtil.toolErrorResponse(id, "Stop debug timed out: " + e.getMessage());
        }
    }
}
