package com.imzhizi.breeze.devtools.mcp.tools;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
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

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * MCP tool: remove_breakpoint
 *
 * Removes a Java line breakpoint identified by className and line number,
 * using the same parameter convention as add_breakpoint.
 *
 * Arguments:
 *   className  (required) – fully-qualified class name
 *   line       (required) – 1-based line number
 */
public final class BreakpointRemoveTool implements McpTool {

    @Override
    public String getName() {
        return "remove_breakpoint";
    }

    @Override
    public String getDescription() {
        return "Remove or disable a Java breakpoint. Two modes: " +
                "(1) Line breakpoint: provide className + line — the breakpoint is deleted. " +
                "(2) Exception breakpoint: provide exceptionClass (as shown by list_breakpoints, e.g. " +
                "'java.lang.NullPointerException' or '<any exception>') — the breakpoint is DISABLED " +
                "(IDEA auto-recreates exception breakpoints on deletion, so disabling is the reliable action). " +
                "Returns a confirmation if successful, or an error if not found.";
    }

    @Override
    public JsonObject getInputSchema() {
        JsonObject schema = new JsonObject();
        schema.addProperty("type", "object");

        JsonObject props = new JsonObject();

        JsonObject classNameProp = new JsonObject();
        classNameProp.addProperty("type", "string");
        classNameProp.addProperty("description", "Fully-qualified class name for a line breakpoint, e.g. 'com.example.MyClass'. Required when removing a line breakpoint.");
        props.add("className", classNameProp);

        JsonObject lineProp = new JsonObject();
        lineProp.addProperty("type", "integer");
        lineProp.addProperty("description", "1-based line number of the line breakpoint to remove. Required when removing a line breakpoint.");
        props.add("line", lineProp);

        JsonObject exClassProp = new JsonObject();
        exClassProp.addProperty("type", "string");
        exClassProp.addProperty("description", "Exception class name for an exception breakpoint, e.g. 'java.lang.NullPointerException' or '<any exception>'. Use this instead of className+line when removing an exception breakpoint.");
        props.add("exceptionClass", exClassProp);

        schema.add("properties", props);
        return schema;
    }

    @Override
    public String execute(JsonObject args, JsonElement id) {
        String exceptionClass = McpJsonUtil.getString(args, "exceptionClass");

        // Route to exception breakpoint removal if exceptionClass is provided
        if (exceptionClass != null && !exceptionClass.isBlank()) {
            return removeExceptionBreakpoint(args, id, exceptionClass.trim());
        }

        // Otherwise, remove a line breakpoint
        String className = McpJsonUtil.getString(args, "className");
        int line = McpJsonUtil.getInt(args, "line", 0);

        if (className == null || className.isBlank()) {
            return McpJsonUtil.toolErrorResponse(id,
                    "Missing argument: provide either exceptionClass (for exception breakpoints) " +
                    "or className + line (for line breakpoints).");
        }
        if (line <= 0) {
            return McpJsonUtil.toolErrorResponse(id, "Missing or invalid argument: line (must be >= 1)");
        }

        XLineBreakpointType<JavaLineBreakpointProperties> bpType = BreakpointHelper.javaLineBreakpointType();
        if (bpType == null) {
            return McpJsonUtil.toolErrorResponse(id, "Java line breakpoint type not found.");
        }

        CompletableFuture<String> future = new CompletableFuture<>();

        ApplicationManager.getApplication().invokeLater(() -> {
            try {
                Project project = BreakpointHelper.getActiveProject();
                if (project == null) {
                    future.complete(McpJsonUtil.toolErrorResponse(id, "No open project found."));
                    return;
                }

                VirtualFile file = BreakpointHelper.resolveSourceFile(project, className);
                if (file == null) {
                    future.complete(McpJsonUtil.toolErrorResponse(id,
                            "Source file not found for class: " + className));
                    return;
                }

                XBreakpointManager bpManager = XDebuggerManager.getInstance(project)
                        .getBreakpointManager();

                XLineBreakpoint<JavaLineBreakpointProperties> target = null;
                for (XLineBreakpoint<JavaLineBreakpointProperties> bp :
                        bpManager.getBreakpoints(bpType)) {
                    if (file.getUrl().equals(bp.getFileUrl()) && bp.getLine() == line - 1) {
                        target = bp;
                        break;
                    }
                }

                if (target == null) {
                    future.complete(McpJsonUtil.toolErrorResponse(id,
                            "No line breakpoint found at " + className + ":" + line +
                            ". If this is an exception breakpoint, use the exceptionClass parameter instead."));
                    return;
                }

                XLineBreakpoint<JavaLineBreakpointProperties> finalTarget = target;
                WriteAction.run(() -> bpManager.removeBreakpoint(finalTarget));
                future.complete(McpJsonUtil.successTextResponse(id,
                        "Line breakpoint removed at " + className + ":" + line));
            } catch (Exception e) {
                future.complete(McpJsonUtil.toolErrorResponse(id,
                        "Failed to remove breakpoint: " + e.getMessage()));
            }
        });

        try {
            return future.get(10, TimeUnit.SECONDS);
        } catch (Exception e) {
            return McpJsonUtil.toolErrorResponse(id, "Remove breakpoint timed out: " + e.getMessage());
        }
    }

    private String removeExceptionBreakpoint(JsonObject args, JsonElement id, String exceptionClass) {
        XBreakpointType<XBreakpoint<JavaExceptionBreakpointProperties>, JavaExceptionBreakpointProperties> exBpType =
                BreakpointHelper.javaExceptionBreakpointType();
        if (exBpType == null) {
            return McpJsonUtil.toolErrorResponse(id, "Java exception breakpoint type not found.");
        }

        CompletableFuture<String> future = new CompletableFuture<>();

        ApplicationManager.getApplication().invokeLater(() -> {
            try {
                Project project = BreakpointHelper.getActiveProject();
                if (project == null) {
                    future.complete(McpJsonUtil.toolErrorResponse(id, "No open project found."));
                    return;
                }

                XBreakpointManager bpManager = XDebuggerManager.getInstance(project).getBreakpointManager();

                // Match on exception class: null properties means "any exception" breakpoint,
                // shown as "<any exception>" in list_breakpoints.
                boolean matchAny = "<any exception>".equals(exceptionClass);
                XBreakpoint<JavaExceptionBreakpointProperties> target = null;
                for (XBreakpoint<JavaExceptionBreakpointProperties> bp : bpManager.getBreakpoints(exBpType)) {
                    String bpEx = bp.getProperties() != null ? bp.getProperties().myQualifiedName : null;
                    boolean matches = matchAny ? (bpEx == null) : exceptionClass.equals(bpEx);
                    if (matches) {
                        target = bp;
                        break;
                    }
                }

                if (target == null) {
                    future.complete(McpJsonUtil.toolErrorResponse(id,
                            "No exception breakpoint found for: " + exceptionClass +
                            ". Use list_breakpoints to see all active exception breakpoints."));
                    return;
                }

                // IDEA's default exception breakpoints (especially "any exception") are
                // recreated automatically after deletion. Disable is the reliable action.
                XBreakpoint<JavaExceptionBreakpointProperties> finalTarget = target;
                WriteAction.run(() -> finalTarget.setEnabled(false));
                future.complete(McpJsonUtil.successTextResponse(id,
                        "Exception breakpoint disabled: " + exceptionClass +
                        " (exception breakpoints are disabled, not deleted, to avoid IDEA auto-recreation)"));
            } catch (Exception e) {
                future.complete(McpJsonUtil.toolErrorResponse(id,
                        "Failed to remove exception breakpoint: " + e.getMessage()));
            }
        });

        try {
            return future.get(10, TimeUnit.SECONDS);
        } catch (Exception e) {
            return McpJsonUtil.toolErrorResponse(id, "Remove exception breakpoint timed out: " + e.getMessage());
        }
    }
}
