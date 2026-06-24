package com.imzhizi.breeze.devtools.mcp.tools;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.xdebugger.XDebuggerManager;
import com.intellij.xdebugger.XDebuggerUtil;
import com.intellij.xdebugger.breakpoints.XBreakpointManager;
import com.intellij.xdebugger.breakpoints.XLineBreakpoint;
import com.intellij.xdebugger.breakpoints.XLineBreakpointType;
import com.imzhizi.breeze.devtools.mcp.McpJsonUtil;
import com.imzhizi.breeze.devtools.mcp.McpTool;
import org.jetbrains.java.debugger.breakpoints.properties.JavaLineBreakpointProperties;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * MCP tool: add_breakpoint
 *
 * Uses XDebuggerUtil.toggleLineBreakpoint() – the same code path as clicking
 * the editor gutter – so the gutter icon updates in real-time without restart.
 *
 * Note: IDEA creates a breakpoint entry at any line; whether the JVM can resolve
 * the line (⊘ vs solid-red icon) is only determined at debug-session time.
 * Call list_breakpoints after a session starts to see the verified/unverified state.
 */
public final class BreakpointAddTool implements McpTool {

    @Override
    public String getName() {
        return "add_breakpoint";
    }

    @Override
    public String getDescription() {
        return "Add a Java line breakpoint at the specified class and line number. " +
                "The class must be present in the project sources. " +
                "Optionally accepts a condition expression. " +
                "Tip: place breakpoints on the first EXECUTABLE line of a method body, " +
                "not on method signatures, annotations, blank lines, or imports — " +
                "those show the ⊘ (unresolvable) icon once the debugger connects. " +
                "Use list_breakpoints to verify the current breakpoint list.";
    }

    @Override
    public JsonObject getInputSchema() {
        JsonObject schema = new JsonObject();
        schema.addProperty("type", "object");

        JsonObject props = new JsonObject();

        JsonObject classNameProp = new JsonObject();
        classNameProp.addProperty("type", "string");
        classNameProp.addProperty("description", "Fully-qualified class name, e.g. 'com.example.MyClass'");
        props.add("className", classNameProp);

        JsonObject lineProp = new JsonObject();
        lineProp.addProperty("type", "integer");
        lineProp.addProperty("description", "1-based line number in the source file");
        props.add("line", lineProp);

        JsonObject condProp = new JsonObject();
        condProp.addProperty("type", "string");
        condProp.addProperty("description", "Optional condition expression, e.g. 'x > 10'.");
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

                // Duplicate check
                XBreakpointManager bpManager = XDebuggerManager.getInstance(project).getBreakpointManager();
                for (XLineBreakpoint<JavaLineBreakpointProperties> existing :
                        bpManager.getBreakpoints(bpType)) {
                    if (file.getUrl().equals(existing.getFileUrl()) && existing.getLine() == line - 1) {
                        future.complete(McpJsonUtil.successTextResponse(id,
                                "Breakpoint already exists at " + className + ":" + line));
                        return;
                    }
                }

                String condition = McpJsonUtil.getString(args, "condition");
                String finalCondition = (condition != null && !condition.isBlank()) ? condition.trim() : null;

                // Create the breakpoint via bpManager.addLineBreakpoint() so we get a direct
                // reference to the XLineBreakpoint object. This lets us set the condition
                // synchronously on the SAME EDT cycle, inside a single WriteAction, with no
                // async gap. (toggleLineBreakpoint registers the breakpoint asynchronously,
                // which previously caused the condition to be lost when set right after.)
                JavaLineBreakpointProperties props = new JavaLineBreakpointProperties();
                final String cond = finalCondition;
                String[] holder = new String[2]; // [0]=created?, [1]=verifiedCondition
                WriteAction.run(() -> {
                    XLineBreakpoint<JavaLineBreakpointProperties> bp =
                            bpManager.addLineBreakpoint(bpType, file.getUrl(), line - 1, props);
                    if (bp != null) {
                        holder[0] = "created";
                        if (cond != null) {
                            bp.setCondition(cond);
                            holder[1] = bp.getConditionExpression() != null
                                    ? bp.getConditionExpression().getExpression()
                                    : null;
                        }
                    }
                });

                if (holder[0] == null) {
                    future.complete(McpJsonUtil.toolErrorResponse(id,
                            "Failed to create breakpoint at " + className + ":" + line +
                            " (addLineBreakpoint returned null)."));
                    return;
                }

                String msg = "Breakpoint added at " + className + ":" + line;
                if (cond != null) {
                    msg += " (condition: " + cond + ")";
                    String verified = holder[1];
                    if (!cond.equals(verified)) {
                        msg += " ⚠ verified reads back as: " + verified;
                    }
                }
                future.complete(McpJsonUtil.successTextResponse(id, msg));

            } catch (Exception e) {
                future.complete(McpJsonUtil.toolErrorResponse(id,
                        "Failed to add breakpoint: " + e.getMessage()));
            }
        });

        try {
            return future.get(10, TimeUnit.SECONDS);
        } catch (Exception e) {
            return McpJsonUtil.toolErrorResponse(id, "Add breakpoint timed out: " + e.getMessage());
        }
    }
}
