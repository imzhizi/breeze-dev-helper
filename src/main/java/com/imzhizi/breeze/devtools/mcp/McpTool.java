package com.imzhizi.breeze.devtools.mcp;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

/**
 * Contract for a single MCP tool exposed by the server.
 */
public interface McpTool {

    /** Unique tool name as advertised in tools/list. */
    String getName();

    /** Human-readable description shown to the AI. */
    String getDescription();

    /**
     * JSON Schema object describing the input parameters.
     * Return an empty JsonObject if the tool takes no parameters.
     */
    JsonObject getInputSchema();

    /**
     * Execute the tool with the given arguments.
     *
     * @param args   parsed JSON arguments from the MCP request
     * @param id     JSON-RPC request id (used to build the response)
     * @return       JSON-RPC 2.0 response string (success or tool-level error)
     */
    String execute(JsonObject args, JsonElement id);
}
