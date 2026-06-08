package com.imzhizi.breeze.devtools.mcp.tools;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.imzhizi.breeze.devtools.mcp.McpTool;

/**
 * MCP tool: step_over
 *
 * Executes the current line and moves to the next one, without entering
 * any method calls on that line (equivalent to F8 in the IDE).
 * Waits for the session to pause again and returns the new location.
 */
public final class StepOverTool implements McpTool {

    @Override
    public String getName() {
        return "step_over";
    }

    @Override
    public String getDescription() {
        return "Step over the current line in the debugger (equivalent to F8). " +
                "Executes the current line without entering method calls. " +
                "Requires an active paused debug session. " +
                "Returns the new location after stepping.";
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
        return DebugStepHelper.executeStep("step_over",
                session -> session.stepOver(false), id);
    }
}
