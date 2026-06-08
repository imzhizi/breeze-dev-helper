package com.imzhizi.breeze.devtools.mcp.tools;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ex.ApplicationEx;
import com.imzhizi.breeze.devtools.mcp.McpJsonUtil;
import com.imzhizi.breeze.devtools.mcp.McpTool;

/**
 * MCP tool: restart_idea
 *
 * Restarts IntelliJ IDEA. Used after deploying a new plugin jar so the new
 * version is loaded without manual intervention.
 *
 * Returns immediately; the actual restart happens ~1 s later so the HTTP
 * response can be delivered before the server shuts down.
 */
public final class RestartIdeaTool implements McpTool {

    @Override
    public String getName() { return "restart_idea"; }

    @Override
    public String getDescription() {
        return "Restart IntelliJ IDEA. Use after deploying a new plugin jar to load the updated version. " +
                "The MCP server will be unavailable for ~10-15 seconds while IDEA restarts, then recovers automatically.";
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
        // Delay restart slightly so the HTTP response is delivered first
        ApplicationManager.getApplication().executeOnPooledThread(() -> {
            try { Thread.sleep(800); } catch (InterruptedException ignored) {}
            ApplicationManager.getApplication().invokeLater(() ->
                    ((ApplicationEx) ApplicationManager.getApplication()).restart(true));
        });

        return McpJsonUtil.successTextResponse(id,
                "IntelliJ IDEA will restart in ~1 second. " +
                "Poll http://localhost:19876/mcp until it responds to confirm the restart is complete.");
    }
}
