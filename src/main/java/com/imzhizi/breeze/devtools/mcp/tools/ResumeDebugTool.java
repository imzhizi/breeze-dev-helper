package com.imzhizi.breeze.devtools.mcp.tools;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.xdebugger.XDebugSession;
import com.intellij.xdebugger.XDebuggerManager;
import com.imzhizi.breeze.devtools.mcp.McpJsonUtil;
import com.imzhizi.breeze.devtools.mcp.McpTool;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * MCP tool: resume_debug
 *
 * Resumes a paused debug session (equivalent to F9 in the IDE).
 */
public final class ResumeDebugTool implements McpTool {

    @Override
    public String getName() {
        return "resume_debug";
    }

    @Override
    public String getDescription() {
        return "Resume a paused debug session (equivalent to F9 / Resume Program). " +
                "Requires an active session that is currently paused at a breakpoint. " +
                "Returns immediately after resuming; call get_debug_status to check when " +
                "the program pauses again at the next breakpoint.";
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
                    future.complete(McpJsonUtil.toolErrorResponse(id, "No open project found."));
                    return;
                }
                XDebugSession session = XDebuggerManager.getInstance(project).getCurrentSession();
                if (session == null) {
                    future.complete(McpJsonUtil.toolErrorResponse(id,
                            "No active debug session. Start a debug session first."));
                    return;
                }
                if (!session.isPaused()) {
                    future.complete(McpJsonUtil.toolErrorResponse(id,
                            "Debug session '" + session.getSessionName() + "' is not paused."));
                    return;
                }
                String sessionName = session.getSessionName();
                session.resume();
                future.complete(McpJsonUtil.successTextResponse(id,
                        "Resumed debug session '" + sessionName + "'. Program is now running."));
            } catch (Exception e) {
                future.complete(McpJsonUtil.toolErrorResponse(id,
                        "Failed to resume: " + e.getMessage()));
            }
        });

        try {
            return future.get(5, TimeUnit.SECONDS);
        } catch (Exception e) {
            return McpJsonUtil.toolErrorResponse(id, "Resume timed out: " + e.getMessage());
        }
    }
}
