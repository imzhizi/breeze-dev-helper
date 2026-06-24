package com.imzhizi.breeze.devtools.mcp.tools;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.project.Project;
import com.intellij.xdebugger.XDebuggerManager;
import com.intellij.xdebugger.breakpoints.XBreakpointManager;
import com.intellij.xdebugger.breakpoints.XLineBreakpoint;
import com.intellij.xdebugger.breakpoints.XLineBreakpointType;
import com.imzhizi.breeze.devtools.mcp.McpJsonUtil;
import com.imzhizi.breeze.devtools.mcp.McpTool;
import org.jetbrains.java.debugger.breakpoints.properties.JavaLineBreakpointProperties;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * MCP tool: set_breakpoint_condition
 *
 * Adds, updates, or removes the condition expression of an EXISTING line
 * breakpoint identified by className + line.
 *
 * Arguments:
 *   className  (required) – fully-qualified class name
 *   line       (required) – 1-based line number
 *   condition  (optional) – the condition expression. When omitted, empty,
 *                            or blank, the existing condition is CLEARED.
 *
 * The underlying IntelliJ API is {@code XBreakpoint.setCondition(String)}:
 *   - a non-null value installs/overwrites the condition;
 *   - null removes it.
 *
 * Implementation note: the breakpoint lookup AND the setCondition write are
 * performed inside a single {@link WriteAction} (a write lock). This avoids
 * the race condition where an interleaving EDT task (e.g. another MCP call or
 * an IDE-internal breakpoint revalidation) could mutate the breakpoint
 * between the read and the write, which previously caused the condition to
 * appear "stuck" or revert to a stale value. After writing, the condition is
 * re-read from the breakpoint to confirm the new state before returning.
 */
public final class BreakpointConditionTool implements McpTool {

    @Override
    public String getName() {
        return "set_breakpoint_condition";
    }

    @Override
    public String getDescription() {
        return "Add, update, or remove the condition of an EXISTING Java line breakpoint. " +
                "Provide className + line to identify the breakpoint, and `condition` with the " +
                "new expression (e.g. 'x > 10' or 'user != null && user.isActive()') to set or " +
                "overwrite the condition. To CLEAR an existing condition, call this tool with " +
                "className + line and either omit `condition` or pass an empty string. " +
                "Use list_breakpoints to inspect current conditions. " +
                "Note: the breakpoint must already exist; use add_breakpoint to create one.";
    }

    @Override
    public JsonObject getInputSchema() {
        JsonObject schema = new JsonObject();
        schema.addProperty("type", "object");

        JsonObject props = new JsonObject();

        JsonObject classNameProp = new JsonObject();
        classNameProp.addProperty("type", "string");
        classNameProp.addProperty("description", "Fully-qualified class name of the existing breakpoint, e.g. 'com.example.MyClass'");
        props.add("className", classNameProp);

        JsonObject lineProp = new JsonObject();
        lineProp.addProperty("type", "integer");
        lineProp.addProperty("description", "1-based line number of the existing breakpoint");
        props.add("line", lineProp);

        JsonObject condProp = new JsonObject();
        condProp.addProperty("type", "string");
        condProp.addProperty("description", "The condition expression to set, e.g. 'x > 10'. " +
                "To CLEAR an existing condition, omit this field or pass an empty string.");
        props.add("condition", condProp);

        schema.add("properties", props);

        JsonArray required = new JsonArray();
        required.add("className");
        required.add("line");
        schema.add("required", required);

        return schema;
    }

    @Override
    public String execute(JsonObject args, JsonElement id) {
        String className = McpJsonUtil.getString(args, "className");
        int line = McpJsonUtil.getInt(args, "line", 0);

        if (className == null || className.isBlank()) {
            return McpJsonUtil.toolErrorResponse(id, "Missing required argument: className");
        }
        if (line <= 0) {
            return McpJsonUtil.toolErrorResponse(id, "Missing or invalid argument: line (must be >= 1)");
        }

        XLineBreakpointType<JavaLineBreakpointProperties> bpType = BreakpointHelper.javaLineBreakpointType();
        if (bpType == null) {
            return McpJsonUtil.toolErrorResponse(id, "Java line breakpoint type not found.");
        }

        String rawCondition = McpJsonUtil.getString(args, "condition");
        String newCondition = (rawCondition != null && !rawCondition.isBlank())
                ? rawCondition.trim() : null;

        CompletableFuture<String> future = new CompletableFuture<>();

        ApplicationManager.getApplication().invokeLater(() -> {
            try {
                Project project = BreakpointHelper.getActiveProject();
                if (project == null) {
                    future.complete(McpJsonUtil.toolErrorResponse(id, "No open project found."));
                    return;
                }

                XBreakpointManager bpManager = XDebuggerManager.getInstance(project).getBreakpointManager();

                // Resolve the source VirtualFile once (read action) so we can match by URL too.
                com.intellij.openapi.vfs.VirtualFile targetFile =
                        BreakpointHelper.resolveSourceFile(project, className);

                // Perform lookup + write inside ONE WriteAction so no other EDT task
                // can interleave between finding the breakpoint and mutating it.
                // holder[0]=oldCondition, holder[1]=verifiedCondition; null array => not found.
                String[] holder = new String[2];
                boolean[] notFound = new boolean[1];
                WriteAction.run(() -> {
                    XLineBreakpoint<JavaLineBreakpointProperties> target = null;
                    for (XLineBreakpoint<JavaLineBreakpointProperties> bp : bpManager.getBreakpoints(bpType)) {
                        if (bp.getLine() != line - 1) continue;

                        // Prefer className match (friendly); fall back to fileUrl match.
                        String resolved = BreakpointHelper.resolveClassName(project, bp.getFileUrl());
                        boolean nameMatch = className.equals(resolved);
                        boolean urlMatch = targetFile != null
                                && targetFile.getUrl().equals(bp.getFileUrl());
                        if (nameMatch || urlMatch) {
                            target = bp;
                            break;
                        }
                    }

                    if (target == null) {
                        notFound[0] = true;
                        return;
                    }

                    holder[0] = target.getConditionExpression() != null
                            ? target.getConditionExpression().getExpression()
                            : null;

                    target.setCondition(newCondition);

                    // Re-read immediately to confirm the write took effect.
                    holder[1] = target.getConditionExpression() != null
                            ? target.getConditionExpression().getExpression()
                            : null;
                });

                if (notFound[0]) {
                    future.complete(McpJsonUtil.toolErrorResponse(id,
                            "No line breakpoint found at " + className + ":" + line +
                            ". Use add_breakpoint to create one first."));
                    return;
                }

                String oldCondition = holder[0];
                String verifiedCondition = holder[1];

                String msg;
                if (newCondition == null) {
                    msg = "Condition cleared for breakpoint at " + className + ":" + line +
                            (oldCondition != null && !oldCondition.isBlank()
                                    ? " (was: " + oldCondition + ")"
                                    : " (no prior condition)");
                } else {
                    msg = "Condition set for breakpoint at " + className + ":" + line +
                            " → " + newCondition +
                            (oldCondition != null && !oldCondition.isBlank()
                                    ? " (was: " + oldCondition + ")"
                                    : "");
                }

                // Surface a warning if the re-read didn't match what we tried to write.
                // This catches cases where setCondition silently rejected the value
                // (e.g. malformed expression that the IDE normalises).
                if (newCondition != null && !newCondition.equals(verifiedCondition)) {
                    msg += " ⚠ verified condition reads back as: " + verifiedCondition;
                } else if (newCondition == null && verifiedCondition != null
                        && !verifiedCondition.isBlank()) {
                    msg += " ⚠ verified condition still present: " + verifiedCondition;
                }

                future.complete(McpJsonUtil.successTextResponse(id, msg));

            } catch (Exception e) {
                future.complete(McpJsonUtil.toolErrorResponse(id,
                        "Failed to set breakpoint condition: " + e.getMessage()));
            }
        });

        try {
            return future.get(10, TimeUnit.SECONDS);
        } catch (Exception e) {
            return McpJsonUtil.toolErrorResponse(id,
                    "Set breakpoint condition timed out: " + e.getMessage());
        }
    }
}
