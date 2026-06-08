package com.imzhizi.breeze.devtools.mcp.tools;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.intellij.execution.ProgramRunnerUtil;
import com.intellij.execution.RunManager;
import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.execution.executors.DefaultDebugExecutor;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.imzhizi.breeze.devtools.mcp.McpJsonUtil;
import com.imzhizi.breeze.devtools.mcp.McpTool;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * MCP tool: launch_remote_debug
 *
 * Finds a Run Configuration by name and launches it with the Debug executor.
 * The configuration must be a "Remote JVM Debug" type (or any other debuggable
 * configuration type) already set up in the IDE.
 *
 * Arguments:
 *   configurationName (required) – exact name of the Run Configuration to launch
 *
 * Note: no Project is captured at construction time.  The active project is
 * resolved dynamically each time execute() is called via
 * {@link BreakpointHelper#getActiveProject()}.
 */
public final class RemoteDebugLaunchTool implements McpTool {

    public RemoteDebugLaunchTool() {
    }

    @Override
    public String getName() {
        return "launch_debug";
    }

    @Override
    public String getDescription() {
        return "Launch a named Run Configuration in Debug mode (equivalent to clicking the Debug button in the IDE). " +
                "Works with any debuggable configuration type (Remote JVM Debug, Application, JUnit, etc.). " +
                "The configuration must already exist in the IDE (Run → Edit Configurations). " +
                "Call list_run_configurations first if you don't know the exact name.";
    }

    @Override
    public JsonObject getInputSchema() {
        JsonObject schema = new JsonObject();
        schema.addProperty("type", "object");

        JsonObject props = new JsonObject();

        JsonObject configProp = new JsonObject();
        configProp.addProperty("type", "string");
        configProp.addProperty("description",
                "Exact name of the Run Configuration to launch, e.g. 'Remote Debug'");
        props.add("configurationName", configProp);

        schema.add("properties", props);

        JsonArray required = new JsonArray();
        required.add("configurationName");
        schema.add("required", required);

        return schema;
    }

    @Override
    public String execute(JsonObject args, JsonElement id) {
        String configName = McpJsonUtil.getString(args, "configurationName");
        if (configName == null || configName.isBlank()) {
            return McpJsonUtil.toolErrorResponse(id, "Missing required argument: configurationName");
        }

        CompletableFuture<String> future = new CompletableFuture<>();

        ApplicationManager.getApplication().invokeLater(() -> {
            try {
                Project project = BreakpointHelper.getActiveProject();
                if (project == null) {
                    future.complete(McpJsonUtil.toolErrorResponse(id, "No open project found."));
                    return;
                }

                RunManager runManager = RunManager.getInstance(project);
                RunnerAndConfigurationSettings settings = runManager.findConfigurationByName(configName);

                if (settings == null) {
                    List<String> names = runManager.getAllSettings().stream()
                            .map(s -> s.getName())
                            .collect(Collectors.toList());
                    future.complete(McpJsonUtil.toolErrorResponse(id,
                            "Run Configuration not found: '" + configName + "'. " +
                            "Available configurations: " + names));
                    return;
                }

                JumperTunnelManager.TunnelInfo tunnel = JumperTunnelManager.getInstance(project).get(configName);

                if (tunnel == null) {
                    // No jumper tunnel - launch directly
                    launchAndComplete(settings, configName, future, id);
                } else {
                    // Has tunnel - check health on background thread
                    ApplicationManager.getApplication().executeOnPooledThread(() -> {
                        if (!JumperTunnelManager.isPortOpen(tunnel.localPort())) {
                            // Tunnel is dead - re-establish
                            ApplicationManager.getApplication().invokeLater(() ->
                                    RemoteDebugConfigCreateTool.openJumperTerminal(
                                            project, tunnel.realHost(), tunnel.realPort(), tunnel.localPort()));
                            if (!JumperTunnelManager.waitForPort(tunnel.localPort(), 30_000)) {
                                future.complete(McpJsonUtil.toolErrorResponse(id,
                                        "SSH tunnel to " + tunnel.displayAddress() +
                                        " could not be re-established. Check Terminal tab for errors."));
                                return;
                            }
                        }
                        // Tunnel is alive - launch on EDT
                        ApplicationManager.getApplication().invokeLater(() ->
                                launchAndComplete(settings, configName, future, id));
                    });
                }
            } catch (Exception e) {
                future.complete(McpJsonUtil.toolErrorResponse(id,
                        "Failed to launch debug configuration: " + e.getMessage()));
            }
        });

        try {
            return future.get(35, TimeUnit.SECONDS);
        } catch (Exception e) {
            return McpJsonUtil.toolErrorResponse(id, "Launch debug timed out: " + e.getMessage());
        }
    }

    private static void launchAndComplete(RunnerAndConfigurationSettings settings,
                                          String configName,
                                          CompletableFuture<String> future,
                                          JsonElement id) {
        try {
            ProgramRunnerUtil.executeConfiguration(settings, DefaultDebugExecutor.getDebugExecutorInstance());
            future.complete(McpJsonUtil.successTextResponse(id,
                    "Launched Run Configuration '" + configName + "' in Debug mode."));
        } catch (Exception e) {
            future.complete(McpJsonUtil.toolErrorResponse(id,
                    "Failed to launch: " + e.getMessage()));
        }
    }
}
