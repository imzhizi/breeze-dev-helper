package com.imzhizi.breeze.devtools.mcp.tools;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.intellij.execution.RunManager;
import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.execution.remote.RemoteConfiguration;
import com.intellij.execution.remote.RemoteConfigurationType;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindowManager;
import com.imzhizi.breeze.devtools.mcp.McpJsonUtil;
import com.imzhizi.breeze.devtools.mcp.McpTool;
import com.imzhizi.breeze.devtools.settings.BreezeSettings;
import org.jetbrains.plugins.terminal.LocalTerminalDirectRunner;
import org.jetbrains.plugins.terminal.TerminalTabState;
import org.jetbrains.plugins.terminal.TerminalView;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * MCP tool: create_remote_debug_config
 *
 * Creates a Remote JVM Debug run configuration.
 * Internally establishes an SSH tunnel through the jumper host so that:
 *   - The AI passes the real remote IP:port
 *   - The IntelliJ config internally uses 127.0.0.1:localPort
 *   - A new Terminal tab is opened with the SSH port-forwarding command
 */
public final class RemoteDebugConfigCreateTool implements McpTool {

    @Override public String getName() { return "create_remote_debug_config"; }

    @Override
    public String getDescription() {
        return "Create a Remote JVM Debug run configuration. " +
                "Pass the real remote host and port – the plugin automatically establishes " +
                "an SSH tunnel through the jumper host and creates the config pointing to " +
                "the local tunnel endpoint. The AI never needs to know about the jumper.";
    }

    @Override
    public JsonObject getInputSchema() {
        JsonObject schema = new JsonObject();
        schema.addProperty("type", "object");
        JsonObject props = new JsonObject();
        addProp(props, "name", "string", "Display name, e.g. 'Remote-10.232.28.200:5900'");
        addProp(props, "host", "string", "Real remote host, e.g. '10.232.28.200'");
        addProp(props, "port", "integer", "Real remote debug port, e.g. 5900");
        schema.add("properties", props);
        JsonArray required = new JsonArray();
        required.add("name"); required.add("host"); required.add("port");
        schema.add("required", required);
        return schema;
    }

    @Override
    public String execute(JsonObject args, JsonElement id) {
        String name = McpJsonUtil.getString(args, "name");
        String host = McpJsonUtil.getString(args, "host");
        int port    = McpJsonUtil.getInt(args, "port", 0);

        if (name == null || name.isBlank()) return McpJsonUtil.toolErrorResponse(id, "Missing: name");
        if (host == null || host.isBlank()) return McpJsonUtil.toolErrorResponse(id, "Missing: host");
        if (port <= 0 || port > 65535)      return McpJsonUtil.toolErrorResponse(id, "Invalid port: " + port);

        CompletableFuture<String> future = new CompletableFuture<>();

        ApplicationManager.getApplication().invokeLater(() -> {
            try {
                Project project = BreakpointHelper.getActiveProject();
                if (project == null) { future.complete(McpJsonUtil.toolErrorResponse(id, "No open project.")); return; }

                RunManager runManager = RunManager.getInstance(project);
                if (runManager.findConfigurationByName(name) != null) {
                    future.complete(McpJsonUtil.toolErrorResponse(id,
                            "Configuration '" + name + "' already exists. Use edit_remote_debug_config to modify it."));
                    return;
                }

                BreezeSettings breezeSettings = BreezeSettings.getInstance();
                RunnerAndConfigurationSettings settings = runManager.createConfiguration(
                        name, RemoteConfigurationType.getInstance().getConfigurationFactories()[0]);
                RemoteConfiguration cfg = (RemoteConfiguration) settings.getConfiguration();
                cfg.USE_SOCKET_TRANSPORT = true;
                cfg.SERVER_MODE = false;

                if (breezeSettings.jumperEnabled) {
                    String configErr = validateJumperSettings(breezeSettings);
                    if (configErr != null) {
                        future.complete(McpJsonUtil.toolErrorResponse(id, configErr));
                        return;
                    }
                    int localPort;
                    try {
                        localPort = JumperTunnelManager.findFreePort();
                    } catch (Exception e) {
                        future.complete(McpJsonUtil.toolErrorResponse(id, "Could not find a free local port: " + e.getMessage()));
                        return;
                    }
                    String resultFile = openJumperTerminal(project, host, port, localPort);
                    cfg.HOST = "127.0.0.1";
                    cfg.PORT = String.valueOf(localPort);
                    JumperTunnelManager.getInstance(project).register(name, host, port, localPort);
                    runManager.addConfiguration(settings);

                    // Wait for SSH tunnel on a background thread.
                    // First tunnel of the day may require phone approval (jumper_proxy.exp timeout: 60 s).
                    // Subsequent tunnels reuse the existing jumper session and connect fast.
                    int finalLocalPort = localPort;
                    JumperTunnelManager tunnelManager = JumperTunnelManager.getInstance(project);
                    boolean firstOfDay = tunnelManager.isFirstTunnelOfDay();
                    String finalResultFile = resultFile;
                    RunManager finalRunManager = runManager;
                    RunnerAndConfigurationSettings finalSettings = settings;
                    int timeoutMs = firstOfDay
                            ? JumperTunnelManager.FIRST_TUNNEL_TIMEOUT_MS
                            : JumperTunnelManager.REUSE_TUNNEL_TIMEOUT_MS;
                    ApplicationManager.getApplication().executeOnPooledThread(() -> {
                        JumperTunnelManager.TunnelWaitResult result =
                                JumperTunnelManager.waitForTunnel(finalLocalPort, finalResultFile, timeoutMs);
                        switch (result) {
                            case SUCCESS -> {
                                tunnelManager.markTunnelEstablished();
                                future.complete(McpJsonUtil.successTextResponse(id,
                                        "Created remote debug config '" + name + "' -> " + host + ":" + port +
                                        " (SSH tunnel ready on 127.0.0.1:" + finalLocalPort + ")"));
                            }
                            case AUTH_FAILED -> {
                                removeBrokenConfig(project, finalRunManager, finalSettings, name, tunnelManager);
                                future.complete(McpJsonUtil.toolErrorResponse(id,
                                        "SSH tunnel authentication failed (phone approval rejected or SSH key invalid). " +
                                        "Check Terminal tab 'Jumper: " + host + ":" + port + "' for details. " +
                                        "Call create_remote_debug_config again to retry."));
                            }
                            case TIMED_OUT -> {
                                removeBrokenConfig(project, finalRunManager, finalSettings, name, tunnelManager);
                                String reason = firstOfDay
                                        ? "Phone approval was not confirmed within 120s (or SSH process failed to start)."
                                        : "SSH tunnel did not connect within 20s.";
                                future.complete(McpJsonUtil.toolErrorResponse(id,
                                        "SSH tunnel timed out. " + reason +
                                        " Check Terminal tab 'Jumper: " + host + ":" + port + "' for details. " +
                                        "Call create_remote_debug_config again to retry."));
                            }
                        }
                    });
                } else {
                    cfg.HOST = host;
                    cfg.PORT = String.valueOf(port);
                    runManager.addConfiguration(settings);
                    future.complete(McpJsonUtil.successTextResponse(id,
                            "Created remote debug config '" + name + "' -> " + host + ":" + port +
                            " (direct connection, jumper disabled)"));
                }
            } catch (Exception e) {
                future.complete(McpJsonUtil.toolErrorResponse(id, "Failed: " + e.getMessage()));
            }
        });

        // Outer timeout: FIRST_TUNNEL_TIMEOUT_MS (120s) + 5s buffer.
        try { return future.get(125, TimeUnit.SECONDS); }
        catch (Exception e) { return McpJsonUtil.toolErrorResponse(id, "Timed out: " + e.getMessage()); }
    }

    /** Returns null if settings are OK, or an error message if something is missing. */
    static String validateJumperSettings(BreezeSettings s) {
        if (s.jumperUser == null || s.jumperUser.isBlank())
            return "Jumper MIS ID not configured. Run setup() to auto-detect or provide it.";
        if (s.jumperHost == null || s.jumperHost.isBlank())
            return "Jumper host not configured. Run setup() to configure.";
        if (!new java.io.File(s.jumperProxyScriptPath).exists())
            return "jumper_proxy.sh not found at: " + s.jumperProxyScriptPath +
                    ". Run setup() to auto-detect or provide the correct path.";
        boolean hasKey = (s.jumperSshKeyPath != null && new java.io.File(s.jumperSshKeyPath).exists())
                || (s.jumperUserKeyPath != null && !"-".equals(s.jumperUserKeyPath)
                    && new java.io.File(s.jumperUserKeyPath).exists());
        if (!hasKey)
            return "No SSH key found. Run setup(moaKeyPath=...) or setup(userKeyPath=...) to configure.";
        return null;
    }

    static String openJumperTerminal(Project project, String remoteHost, int remotePort, int localPort) {
        BreezeSettings s = BreezeSettings.getInstance();

        // Resolve key paths: pass "-" if file does not exist
        String userKey = (s.jumperUserKeyPath != null && !s.jumperUserKeyPath.isBlank()
                && new java.io.File(s.jumperUserKeyPath.replace("~", System.getProperty("user.home"))).exists())
                ? s.jumperUserKeyPath : "-";
        String moaKey  = (s.jumperSshKeyPath != null && !s.jumperSshKeyPath.isBlank()
                && new java.io.File(s.jumperSshKeyPath.replace("~", System.getProperty("user.home"))).exists())
                ? s.jumperSshKeyPath : "-";

        // Temp result file (jumper_proxy.sh writes exit code here)
        String resultFile;
        try {
            resultFile = java.io.File.createTempFile("jumper_proxy", ".idekit").getAbsolutePath();
        } catch (Exception e) {
            resultFile = "/tmp/jumper_proxy_" + localPort + ".idekit";
        }

        String cmd = String.format("sh '%s' %s %s %s %s %d:%s:%d '%s'",
                s.jumperProxyScriptPath,
                s.jumperUser, s.jumperHost,
                userKey, moaKey,
                localPort, remoteHost, remotePort,
                resultFile);

        String tabName = "Jumper: " + remoteHost + ":" + remotePort;

        // Show Terminal tool window so the tab is visible
        var terminalWindow = ToolWindowManager.getInstance(project).getToolWindow("Terminal");
        if (terminalWindow != null) terminalWindow.activate(null, true);

        TerminalTabState tabState = new TerminalTabState();
        tabState.myShellCommand = List.of("/bin/sh", "-c", cmd);
        tabState.myTabName = tabName;
        TerminalView.getInstance(project).createNewSession(
                new LocalTerminalDirectRunner(project), tabState);
        return resultFile;
    }

    /** Removes a run config whose tunnel failed, so the AI can retry with a clean slate. */
    static void removeBrokenConfig(Project project, RunManager runManager,
                                   RunnerAndConfigurationSettings settings,
                                   String configName, JumperTunnelManager tunnelManager) {
        tunnelManager.unregister(configName);
        ApplicationManager.getApplication().invokeLater(() -> runManager.removeConfiguration(settings));
    }

    private static void addProp(JsonObject props, String key, String type, String desc) {
        JsonObject p = new JsonObject(); p.addProperty("type", type); p.addProperty("description", desc);
        props.add(key, p);
    }
}
