package com.imzhizi.breeze.devtools.mcp.tools;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.xdebugger.XDebuggerManager;
import com.intellij.xdebugger.breakpoints.XBreakpoint;
import com.intellij.xdebugger.breakpoints.XBreakpointManager;
import com.intellij.xdebugger.breakpoints.XBreakpointType;
import com.intellij.xdebugger.breakpoints.XLineBreakpoint;
import com.intellij.xdebugger.breakpoints.XLineBreakpointType;
import com.imzhizi.breeze.devtools.mcp.McpJsonUtil;
import com.imzhizi.breeze.devtools.mcp.McpTool;
import org.jetbrains.java.debugger.breakpoints.properties.JavaExceptionBreakpointProperties;
import org.jetbrains.java.debugger.breakpoints.properties.JavaLineBreakpointProperties;

import java.util.Collection;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * MCP tool: list_breakpoints
 *
 * Returns all currently registered Java line breakpoints.
 * Output uses className:line format (human-readable) rather than raw fileUrl.
 */
public final class BreakpointListTool implements McpTool {

    @Override
    public String getName() {
        return "list_breakpoints";
    }

    @Override
    public String getDescription() {
        return "List all Java breakpoints currently set in the project: both line breakpoints " +
                "(type=line) and exception breakpoints (type=exception). " +
                "For line breakpoints, use className + line with remove_breakpoint. " +
                "For exception breakpoints, use exceptionClass with remove_breakpoint.";
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

                XLineBreakpointType<JavaLineBreakpointProperties> bpType =
                        BreakpointHelper.javaLineBreakpointType();
                XBreakpointType<XBreakpoint<JavaExceptionBreakpointProperties>, JavaExceptionBreakpointProperties> exBpType =
                        BreakpointHelper.javaExceptionBreakpointType();

                XBreakpointManager bpManager = XDebuggerManager.getInstance(project).getBreakpointManager();

                Collection<? extends XLineBreakpoint<JavaLineBreakpointProperties>> lineBreakpoints =
                        bpType != null ? bpManager.getBreakpoints(bpType) : java.util.Collections.emptyList();
                Collection<? extends XBreakpoint<JavaExceptionBreakpointProperties>> exBreakpoints =
                        exBpType != null ? bpManager.getBreakpoints(exBpType) : java.util.Collections.emptyList();

                if (lineBreakpoints.isEmpty() && exBreakpoints.isEmpty()) {
                    future.complete(McpJsonUtil.successTextResponse(id,
                            "No Java breakpoints are currently set in project '" +
                                    project.getName() + "'."));
                    return;
                }

                StringBuilder sb = new StringBuilder();
                sb.append("Java breakpoints in project '").append(project.getName()).append("':\n");

                for (XLineBreakpoint<JavaLineBreakpointProperties> bp : lineBreakpoints) {
                    String className = BreakpointHelper.resolveClassName(project, bp.getFileUrl());
                    String location = className != null
                            ? className + ":" + (bp.getLine() + 1)
                            : bp.getFileUrl() + ":" + (bp.getLine() + 1);

                    sb.append("  [line] ").append(location)
                            .append(" [").append(bp.isEnabled() ? "enabled" : "disabled").append("]");

                    String condition = bp.getConditionExpression() != null
                            ? bp.getConditionExpression().getExpression()
                            : null;
                    if (condition != null && !condition.isBlank()) {
                        sb.append(" condition: ").append(condition);
                    }
                    sb.append("\n");
                }

                for (XBreakpoint<JavaExceptionBreakpointProperties> bp : exBreakpoints) {
                    String exClass = bp.getProperties() != null ? bp.getProperties().myQualifiedName : null;
                    sb.append("  [exception] ")
                            .append(exClass != null ? exClass : "<any exception>")
                            .append(" [").append(bp.isEnabled() ? "enabled" : "disabled").append("]")
                            .append(" — use remove_breakpoint(exceptionClass=...)\n");
                }

                future.complete(McpJsonUtil.successTextResponse(id, sb.toString().trim()));
            } catch (Exception e) {
                future.complete(McpJsonUtil.toolErrorResponse(id,
                        "Failed to list breakpoints: " + e.getMessage()));
            }
        });

        try {
            return future.get(10, TimeUnit.SECONDS);
        } catch (Exception e) {
            return McpJsonUtil.toolErrorResponse(id, "List breakpoints timed out: " + e.getMessage());
        }
    }
}
