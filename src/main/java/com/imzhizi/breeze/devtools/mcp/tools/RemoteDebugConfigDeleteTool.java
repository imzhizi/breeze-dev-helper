package com.imzhizi.breeze.devtools.mcp.tools;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.intellij.execution.RunManager;
import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.imzhizi.breeze.devtools.mcp.McpJsonUtil;
import com.imzhizi.breeze.devtools.mcp.McpTool;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * MCP tool: delete_remote_debug_config
 *
 * Deletes a run configuration. If it has a jumper tunnel, the tunnel mapping
 * is removed from JumperTunnelManager. The terminal tab must be closed manually.
 */
public final class RemoteDebugConfigDeleteTool implements McpTool {

    @Override public String getName() { return "delete_remote_debug_config"; }

    @Override
    public String getDescription() {
        return "Delete a run configuration by name. If it was created with a jumper tunnel, " +
                "the tunnel state is cleared (close the Terminal tab manually). " +
                "Use list_run_configurations to find the exact name.";
    }

    @Override
    public JsonObject getInputSchema() {
        JsonObject schema = new JsonObject();
        schema.addProperty("type", "object");
        JsonObject props = new JsonObject();
        JsonObject nameProp = new JsonObject();
        nameProp.addProperty("type", "string");
        nameProp.addProperty("description", "Exact name of the configuration to delete");
        props.add("name", nameProp);
        schema.add("properties", props);
        JsonArray required = new JsonArray();
        required.add("name");
        schema.add("required", required);
        return schema;
    }

    @Override
    public String execute(JsonObject args, JsonElement id) {
        String name = McpJsonUtil.getString(args, "name");
        if (name == null || name.isBlank()) return McpJsonUtil.toolErrorResponse(id, "Missing: name");

        CompletableFuture<String> future = new CompletableFuture<>();

        ApplicationManager.getApplication().invokeLater(() -> {
            try {
                Project project = BreakpointHelper.getActiveProject();
                if (project == null) { future.complete(McpJsonUtil.toolErrorResponse(id, "No open project.")); return; }

                RunManager runManager = RunManager.getInstance(project);
                RunnerAndConfigurationSettings settings = runManager.findConfigurationByName(name);
                if (settings == null) {
                    future.complete(McpJsonUtil.toolErrorResponse(id,
                            "Configuration not found: '" + name + "'"));
                    return;
                }

                runManager.removeConfiguration(settings);

                JumperTunnelManager tunnelMgr = JumperTunnelManager.getInstance(project);
                boolean hadTunnel = tunnelMgr.hasJumperTunnel(name);
                tunnelMgr.unregister(name);

                String msg = "Deleted run configuration: '" + name + "'";
                if (hadTunnel) msg += " (SSH tunnel terminal tab can now be closed manually)";
                future.complete(McpJsonUtil.successTextResponse(id, msg));
            } catch (Exception e) {
                future.complete(McpJsonUtil.toolErrorResponse(id, "Failed: " + e.getMessage()));
            }
        });

        try { return future.get(10, TimeUnit.SECONDS); }
        catch (Exception e) { return McpJsonUtil.toolErrorResponse(id, "Timed out: " + e.getMessage()); }
    }
}
