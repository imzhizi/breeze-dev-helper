package com.imzhizi.breeze.devtools.mcp.tools;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.intellij.execution.RunManager;
import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.execution.remote.RemoteConfiguration;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.imzhizi.breeze.devtools.mcp.McpJsonUtil;
import com.imzhizi.breeze.devtools.mcp.McpTool;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * MCP tool: edit_remote_debug_config
 *
 * Edits an existing remote debug configuration. When host or port changes,
 * a new SSH tunnel is opened automatically and the Run Config is updated
 * to use the new local tunnel port. The old terminal tab must be closed manually.
 */
public final class RemoteDebugConfigEditTool implements McpTool {

    @Override public String getName() { return "edit_remote_debug_config"; }

    @Override
    public String getDescription() {
        return "Edit an existing remote debug configuration (change host or port). " +
                "If host or port changes, a new SSH tunnel is automatically established " +
                "and the config is updated. Use list_run_configurations to find the name.";
    }

    @Override
    public JsonObject getInputSchema() {
        JsonObject schema = new JsonObject();
        schema.addProperty("type", "object");
        JsonObject props = new JsonObject();
        addProp(props, "name",    "string",  "Current name of the configuration");
        addProp(props, "newName", "string",  "Rename to this name (optional)");
        addProp(props, "host",    "string",  "New real remote host (optional)");
        addProp(props, "port",    "integer", "New real remote debug port (optional)");
        schema.add("properties", props);
        JsonArray required = new JsonArray();
        required.add("name");
        schema.add("required", required);
        return schema;
    }

    @Override
    public String execute(JsonObject args, JsonElement id) {
        String name    = McpJsonUtil.getString(args, "name");
        String newName = McpJsonUtil.getString(args, "newName");
        String newHost = McpJsonUtil.getString(args, "host");
        int    newPort = McpJsonUtil.getInt(args, "port", -1);

        if (name == null || name.isBlank()) return McpJsonUtil.toolErrorResponse(id, "Missing: name");
        if (newPort != -1 && (newPort <= 0 || newPort > 65535))
            return McpJsonUtil.toolErrorResponse(id, "Invalid port: " + newPort);

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
                if (!(settings.getConfiguration() instanceof RemoteConfiguration cfg)) {
                    future.complete(McpJsonUtil.toolErrorResponse(id,
                            "'" + name + "' is not a Remote JVM Debug configuration."));
                    return;
                }

                JumperTunnelManager tunnelMgr = JumperTunnelManager.getInstance(project);
                JumperTunnelManager.TunnelInfo existing = tunnelMgr.get(name);

                // Resolve effective host/port (new value or keep existing from tunnel map or raw config)
                String effectiveHost = newHost != null && !newHost.isBlank() ? newHost
                        : (existing != null ? existing.realHost() : cfg.HOST);
                int effectivePort = newPort != -1 ? newPort
                        : (existing != null ? existing.realPort() : Integer.parseInt(cfg.PORT));

                StringBuilder changes = new StringBuilder();
                boolean tunnelChanged = false;

                if (existing != null) {
                    tunnelChanged = (newHost != null && !newHost.isBlank() && !newHost.equals(existing.realHost()))
                            || (newPort != -1 && newPort != existing.realPort());
                }

                if (tunnelChanged || (existing == null && (newHost != null || newPort != -1))) {
                    // Re-create tunnel with updated target
                    int localPort;
                    try {
                        localPort = JumperTunnelManager.findFreePort();
                    } catch (Exception e) {
                        future.complete(McpJsonUtil.toolErrorResponse(id, "Could not find free port: " + e.getMessage()));
                        return;
                    }
                    String resultFile = RemoteDebugConfigCreateTool.openJumperTerminal(project, effectiveHost, effectivePort, localPort);
                    cfg.HOST = "127.0.0.1";
                    cfg.PORT = String.valueOf(localPort);
                    tunnelMgr.register(name, effectiveHost, effectivePort, localPort);
                    changes.append("tunnel → ").append(effectiveHost).append(":").append(effectivePort)
                            .append(" (local: 127.0.0.1:").append(localPort).append("); ");

                    // Apply name change before handing off to background thread
                    String resolvedName = name;
                    if (newName != null && !newName.isBlank() && !newName.equals(name)) {
                        JumperTunnelManager.TunnelInfo info = tunnelMgr.get(name);
                        tunnelMgr.unregister(name);
                        if (info != null) tunnelMgr.register(newName, info.realHost(), info.realPort(), info.localPort());
                        settings.setName(newName);
                        changes.append("name → ").append(newName).append("; ");
                        resolvedName = newName;
                    }

                    String finalChanges = changes.toString().stripTrailing().replaceAll(";$", "");
                    String finalName = resolvedName;
                    int finalLocalPort = localPort;
                    boolean firstOfDay = tunnelMgr.isFirstTunnelOfDay();
                    int timeoutMs = firstOfDay
                            ? JumperTunnelManager.FIRST_TUNNEL_TIMEOUT_MS
                            : JumperTunnelManager.REUSE_TUNNEL_TIMEOUT_MS;
                    ApplicationManager.getApplication().executeOnPooledThread(() -> {
                        JumperTunnelManager.TunnelWaitResult result =
                                JumperTunnelManager.waitForTunnel(finalLocalPort, resultFile, timeoutMs);
                        switch (result) {
                            case SUCCESS -> {
                                tunnelMgr.markTunnelEstablished();
                                future.complete(McpJsonUtil.successTextResponse(id,
                                        "Updated '" + finalName + "': " + finalChanges +
                                        " (SSH tunnel ready on 127.0.0.1:" + finalLocalPort + ")"));
                            }
                            case AUTH_FAILED -> future.complete(McpJsonUtil.toolErrorResponse(id,
                                    "SSH tunnel authentication failed. " +
                                    "Call edit_remote_debug_config again to retry."));
                            case TIMED_OUT -> {
                                String reason = firstOfDay
                                        ? "Phone approval was not confirmed within 95s (or SSH process failed to start)."
                                        : "SSH tunnel did not connect within 20s.";
                                future.complete(McpJsonUtil.toolErrorResponse(id,
                                        "SSH tunnel timed out. " + reason +
                                        " Call edit_remote_debug_config again to retry."));
                            }
                        }
                    });
                    return;
                }

                if (newName != null && !newName.isBlank() && !newName.equals(name)) {
                    // Re-register under new name
                    JumperTunnelManager.TunnelInfo info = tunnelMgr.get(name);
                    tunnelMgr.unregister(name);
                    if (info != null) tunnelMgr.register(newName, info.realHost(), info.realPort(), info.localPort());
                    settings.setName(newName);
                    changes.append("name → ").append(newName).append("; ");
                }

                if (changes.isEmpty()) {
                    future.complete(McpJsonUtil.successTextResponse(id, "No changes for '" + name + "'."));
                    return;
                }
                future.complete(McpJsonUtil.successTextResponse(id,
                        "Updated '" + name + "': " + changes.toString().stripTrailing().replaceAll(";$", "")));
            } catch (Exception e) {
                future.complete(McpJsonUtil.toolErrorResponse(id, "Failed: " + e.getMessage()));
            }
        });

        // Outer timeout: FIRST_TUNNEL_TIMEOUT_MS (120s) + 5s buffer.
        try { return future.get(125, TimeUnit.SECONDS); }
        catch (Exception e) { return McpJsonUtil.toolErrorResponse(id, "Timed out: " + e.getMessage()); }
    }

    private static void addProp(JsonObject props, String key, String type, String desc) {
        JsonObject p = new JsonObject(); p.addProperty("type", type); p.addProperty("description", desc);
        props.add(key, p);
    }
}
