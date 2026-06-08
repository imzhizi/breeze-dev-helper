package com.imzhizi.breeze.devtools.mcp.tools;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.project.Project;
import com.imzhizi.breeze.devtools.mcp.McpJsonUtil;
import com.imzhizi.breeze.devtools.mcp.McpTool;
import com.imzhizi.breeze.devtools.navigation.BreezeJumpResolvedTarget;
import com.imzhizi.breeze.devtools.resolve.BreezeJumpResolver;
import com.imzhizi.breeze.devtools.uri.BreezeJumpTarget;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * MCP tool: navigate_to_code
 *
 * Opens the specified class (and optionally a member / line) in the editor.
 *
 * Arguments:
 *   className  (required) – fully-qualified class name, e.g. "com.example.MyClass"
 *   member     (optional) – method or field name, e.g. "myMethod"
 *   line       (optional) – 1-based line offset relative to the member (or class)
 *
 * Note: no Project is captured at construction time.  The active project is
 * resolved dynamically each time execute() is called via
 * {@link BreakpointHelper#getActiveProject()}.
 */
public final class NavigateTool implements McpTool {

    public NavigateTool() {
    }

    @Override
    public String getName() {
        return "navigate_to_code";
    }

    @Override
    public String getDescription() {
        return "Open a Java/Kotlin/Scala class (and optionally a specific method or line) in the editor. " +
                "Use this to jump to the source code location before setting breakpoints or inspecting code.";
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

        JsonObject memberProp = new JsonObject();
        memberProp.addProperty("type", "string");
        memberProp.addProperty("description", "Optional method or field name to jump to within the class");
        props.add("member", memberProp);

        JsonObject lineProp = new JsonObject();
        lineProp.addProperty("type", "integer");
        lineProp.addProperty("description", "Optional 1-based line number (absolute in the file, or offset from the member)");
        props.add("line", lineProp);

        schema.add("properties", props);

        com.google.gson.JsonArray required = new com.google.gson.JsonArray();
        required.add("className");
        schema.add("required", required);

        return schema;
    }

    @Override
    public String execute(JsonObject args, JsonElement id) {
        String className = McpJsonUtil.getString(args, "className");
        if (className == null || className.isBlank()) {
            return McpJsonUtil.toolErrorResponse(id, "Missing required argument: className");
        }

        String member = McpJsonUtil.getString(args, "member");
        int line = McpJsonUtil.getInt(args, "line", 0);

        BreezeJumpTarget target = new BreezeJumpTarget(className, member, line > 0 ? line : null);

        CompletableFuture<String> future = new CompletableFuture<>();

        ApplicationManager.getApplication().invokeLater(() -> {
            try {
                // Resolve the active project at call time, not at construction time.
                Project project = BreakpointHelper.getActiveProject();
                if (project == null) {
                    future.complete(McpJsonUtil.toolErrorResponse(id,
                            "No open project found."));
                    return;
                }

                BreezeJumpResolvedTarget resolved = BreezeJumpResolver.resolve(project, target);
                if (resolved == null) {
                    future.complete(McpJsonUtil.toolErrorResponse(id,
                            "Could not resolve class: " + className +
                                    " (active project: " + project.getName() + ")"));
                    return;
                }
                OpenFileDescriptor descriptor = new OpenFileDescriptor(
                        project, resolved.getFile(), resolved.getOffset());
                descriptor.navigate(true);
                future.complete(McpJsonUtil.successTextResponse(id,
                        "Navigated to " + className +
                                (member != null ? "#" + member : "") +
                                (line > 0 ? ":" + line : "")));
            } catch (Exception e) {
                future.complete(McpJsonUtil.toolErrorResponse(id,
                        "Navigation failed: " + e.getMessage()));
            }
        });

        try {
            return future.get(10, TimeUnit.SECONDS);
        } catch (Exception e) {
            return McpJsonUtil.toolErrorResponse(id, "Navigation timed out: " + e.getMessage());
        }
    }
}
