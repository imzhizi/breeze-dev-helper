package com.imzhizi.breeze.devtools.mcp;

import com.google.gson.*;
import com.imzhizi.breeze.devtools.mcp.tools.*;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Dispatches incoming JSON-RPC 2.0 requests to the appropriate MCP tool handler.
 *
 * Supported methods:
 *   - initialize          – MCP handshake
 *   - tools/list          – return all registered tools with their schemas
 *   - tools/call          – invoke a specific tool
 *
 * Note: no Project is passed at construction time. Each tool resolves the
 * "active project" dynamically at execution time via BreakpointHelper.getActiveProject(),
 * so that MCP calls always operate on the IDE window the user is currently working in.
 */
public final class McpToolDispatcher {

    private static final String SERVER_NAME    = "breeze-dev-helper";
    private static final String SERVER_VERSION = "0.4.2";
    private static final String MCP_VERSION    = "2024-11-05";

    private final Map<String, McpTool> tools = new LinkedHashMap<>();

    public McpToolDispatcher() {
        // One-stop setup (run first on new install)
        register(new SetupTool());
        // Breakpoint management
        register(new BreakpointAddTool());
        register(new BreakpointRemoveTool());
        register(new BreakpointListTool());
        // Debug session lifecycle
        register(new RunConfigListTool());
        register(new RemoteDebugConfigCreateTool());
        register(new RemoteDebugConfigEditTool());
        register(new RemoteDebugConfigDeleteTool());
        register(new RemoteDebugLaunchTool());  // tool name: launch_debug
        register(new DebugStatusTool());
        register(new RemoteDebugStopTool());
        // Execution control
        register(new ResumeDebugTool());
        register(new StepOverTool());
        register(new StepIntoTool());
        register(new StepOutTool());
        // State inspection
        register(new GetStackFramesTool());
        register(new VariableReadTool());
        register(new EvaluateExpressionTool());
        register(new EvaluateExpressionsTool());
        // Navigation
        register(new NavigateTool());
        // IDE control
        register(new RestartIdeaTool());
    }

    private void register(McpTool tool) {
        tools.put(tool.getName(), tool);
    }

    /**
     * Dispatch a raw JSON-RPC 2.0 request string and return the response string.
     */
    public String dispatch(String requestJson) {
        JsonObject req;
        JsonElement id = JsonNull.INSTANCE;
        try {
            req = McpJsonUtil.parse(requestJson);
            id  = req.has("id") ? req.get("id") : JsonNull.INSTANCE;
        } catch (Exception e) {
            return McpJsonUtil.errorResponse(null, -32700, "Parse error: " + e.getMessage());
        }

        String method = McpJsonUtil.getString(req, "method");
        if (method == null) {
            return McpJsonUtil.errorResponse(id, -32600, "Invalid Request: missing method");
        }

        JsonObject params = McpJsonUtil.getObject(req, "params");

        return switch (method) {
            case "initialize"  -> handleInitialize(id, params);
            case "tools/list"  -> handleToolsList(id);
            case "tools/call"  -> handleToolsCall(id, params);
            // Notifications (no response needed per JSON-RPC spec for notifications,
            // but we return an empty result to keep things simple)
            case "notifications/initialized" -> McpJsonUtil.successResponse(id, new JsonObject());
            default -> McpJsonUtil.errorResponse(id, -32601, "Method not found: " + method);
        };
    }

    // ------------------------------------------------------------------
    // Handler implementations
    // ------------------------------------------------------------------

    private String handleInitialize(JsonElement id, JsonObject params) {
        JsonObject serverInfo = new JsonObject();
        serverInfo.addProperty("name", SERVER_NAME);
        serverInfo.addProperty("version", SERVER_VERSION);

        JsonObject capabilities = new JsonObject();
        capabilities.add("tools", new JsonObject());   // we support tools

        JsonObject result = new JsonObject();
        result.addProperty("protocolVersion", MCP_VERSION);
        result.add("capabilities", capabilities);
        result.add("serverInfo", serverInfo);

        return McpJsonUtil.successResponse(id, result);
    }

    private String handleToolsList(JsonElement id) {
        JsonArray toolsArray = new JsonArray();
        for (McpTool tool : tools.values()) {
            JsonObject entry = new JsonObject();
            entry.addProperty("name", tool.getName());
            entry.addProperty("description", tool.getDescription());
            entry.add("inputSchema", tool.getInputSchema());
            toolsArray.add(entry);
        }
        JsonObject result = new JsonObject();
        result.add("tools", toolsArray);
        return McpJsonUtil.successResponse(id, result);
    }

    private String handleToolsCall(JsonElement id, JsonObject params) {
        String toolName = McpJsonUtil.getString(params, "name");
        if (toolName == null) {
            return McpJsonUtil.errorResponse(id, -32602, "Invalid params: missing tool name");
        }

        McpTool tool = tools.get(toolName);
        if (tool == null) {
            return McpJsonUtil.toolErrorResponse(id, "Unknown tool: " + toolName);
        }

        JsonObject args = McpJsonUtil.getObject(params, "arguments");
        try {
            return tool.execute(args, id);
        } catch (Exception e) {
            return McpJsonUtil.toolErrorResponse(id, "Tool execution error: " + e.getMessage());
        }
    }
}
