package com.imzhizi.breeze.devtools.mcp.tools;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.intellij.execution.RunManager;
import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.imzhizi.breeze.devtools.mcp.McpJsonUtil;
import com.imzhizi.breeze.devtools.mcp.McpTool;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * MCP tool: list_run_configurations
 *
 * Lists all Run Configurations available in the current project.
 * Use this before launch_debug to discover the exact configuration name.
 */
public final class RunConfigListTool implements McpTool {

    @Override
    public String getName() {
        return "list_run_configurations";
    }

    @Override
    public String getDescription() {
        return "List all Run Configurations available in the current project. " +
                "Use this to find the exact name to pass to launch_debug.";
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

                List<RunnerAndConfigurationSettings> all =
                        RunManager.getInstance(project).getAllSettings();

                if (all.isEmpty()) {
                    future.complete(McpJsonUtil.successTextResponse(id,
                            "No Run Configurations found in project '" + project.getName() + "'."));
                    return;
                }

                JumperTunnelManager tunnelMgr = JumperTunnelManager.getInstance(project);
                StringBuilder sb = new StringBuilder();
                sb.append("Run Configurations in project '").append(project.getName())
                        .append("' (").append(all.size()).append(" total):\n");
                for (RunnerAndConfigurationSettings s : all) {
                    sb.append("  • [").append(s.getType().getDisplayName()).append("] ")
                            .append(s.getName());
                    // For jumper-tunnelled configs show real address instead of 127.0.0.1
                    JumperTunnelManager.TunnelInfo tunnel = tunnelMgr.get(s.getName());
                    if (tunnel != null) {
                        sb.append("  [real: ").append(tunnel.displayAddress()).append("]");
                    }
                    sb.append("\n");
                }

                future.complete(McpJsonUtil.successTextResponse(id, sb.toString().trim()));
            } catch (Exception e) {
                future.complete(McpJsonUtil.toolErrorResponse(id,
                        "Failed to list run configurations: " + e.getMessage()));
            }
        });

        try {
            return future.get(10, TimeUnit.SECONDS);
        } catch (Exception e) {
            return McpJsonUtil.toolErrorResponse(id, "List run configurations timed out: " + e.getMessage());
        }
    }
}
