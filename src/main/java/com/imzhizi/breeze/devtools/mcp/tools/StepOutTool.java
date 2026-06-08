package com.imzhizi.breeze.devtools.mcp.tools;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.imzhizi.breeze.devtools.mcp.McpTool;

/**
 * MCP tool: step_out
 *
 * Steps out of the current method back to its caller (equivalent to Shift+F8 in the IDE).
 * Waits for the session to pause again and returns the new location.
 */
public final class StepOutTool implements McpTool {

    @Override
    public String getName() {
        return "step_out";
    }

    @Override
    public String getDescription() {
        return "Step out of the current method back to the caller (equivalent to Shift+F8). " +
                "Finishes executing the current method and pauses at the return site. " +
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
        return DebugStepHelper.executeStep("step_out",
                session -> session.stepOut(), id);
    }
}
