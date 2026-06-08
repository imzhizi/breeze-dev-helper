package com.imzhizi.breeze.devtools.mcp.tools;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.imzhizi.breeze.devtools.mcp.McpTool;

/**
 * MCP tool: step_into
 *
 * Steps into the method call on the current line (equivalent to F7 in the IDE).
 * Waits for the session to pause again and returns the new location.
 */
public final class StepIntoTool implements McpTool {

    @Override
    public String getName() {
        return "step_into";
    }

    @Override
    public String getDescription() {
        return "Step into the method call on the current line (equivalent to F7). " +
                "Enters the method body and pauses at its first executable line. " +
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
        return DebugStepHelper.executeStep("step_into",
                session -> session.stepInto(), id);
    }
}
