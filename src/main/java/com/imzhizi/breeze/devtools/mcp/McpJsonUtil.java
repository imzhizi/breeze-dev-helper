package com.imzhizi.breeze.devtools.mcp;

import com.google.gson.*;

/**
 * Lightweight helpers for building MCP JSON-RPC 2.0 responses using Gson
 * (bundled with the IntelliJ Platform – no extra dependency needed).
 */
public final class McpJsonUtil {

    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();

    private McpJsonUtil() {
    }

    // ------------------------------------------------------------------
    // Parsing helpers
    // ------------------------------------------------------------------

    public static JsonObject parse(String json) {
        return JsonParser.parseString(json).getAsJsonObject();
    }

    public static String getString(JsonObject obj, String key) {
        JsonElement el = obj.get(key);
        return (el != null && !el.isJsonNull()) ? el.getAsString() : null;
    }

    public static JsonObject getObject(JsonObject obj, String key) {
        JsonElement el = obj.get(key);
        return (el != null && el.isJsonObject()) ? el.getAsJsonObject() : new JsonObject();
    }

    public static int getInt(JsonObject obj, String key, int defaultValue) {
        JsonElement el = obj.get(key);
        if (el == null || el.isJsonNull()) return defaultValue;
        try {
            return el.getAsInt();
        } catch (Exception e) {
            return defaultValue;
        }
    }

    public static boolean getBoolean(JsonObject obj, String key, boolean defaultValue) {
        JsonElement el = obj.get(key);
        if (el == null || el.isJsonNull()) return defaultValue;
        try {
            return el.getAsBoolean();
        } catch (Exception e) {
            return defaultValue;
        }
    }

    // ------------------------------------------------------------------
    // Response builders
    // ------------------------------------------------------------------

    /** Build a successful JSON-RPC 2.0 response with an arbitrary result object. */
    public static String successResponse(JsonElement id, JsonElement result) {
        JsonObject resp = new JsonObject();
        resp.addProperty("jsonrpc", "2.0");
        if (id != null) resp.add("id", id);
        resp.add("result", result);
        return GSON.toJson(resp);
    }

    /** Build a successful JSON-RPC 2.0 response with a plain text result. */
    public static String successTextResponse(JsonElement id, String text) {
        JsonObject content = new JsonObject();
        content.addProperty("type", "text");
        content.addProperty("text", text);
        JsonArray contentArr = new JsonArray();
        contentArr.add(content);
        JsonObject result = new JsonObject();
        result.add("content", contentArr);
        result.addProperty("isError", false);
        return successResponse(id, result);
    }

    /** Build a JSON-RPC 2.0 error response. */
    public static String errorResponse(JsonElement id, int code, String message) {
        JsonObject error = new JsonObject();
        error.addProperty("code", code);
        error.addProperty("message", message);
        JsonObject resp = new JsonObject();
        resp.addProperty("jsonrpc", "2.0");
        if (id != null) resp.add("id", id); else resp.add("id", JsonNull.INSTANCE);
        resp.add("error", error);
        return GSON.toJson(resp);
    }

    /** Build a tool-call result that signals an error at the tool level (not JSON-RPC level). */
    public static String toolErrorResponse(JsonElement id, String errorMessage) {
        JsonObject content = new JsonObject();
        content.addProperty("type", "text");
        content.addProperty("text", errorMessage);
        JsonArray contentArr = new JsonArray();
        contentArr.add(content);
        JsonObject result = new JsonObject();
        result.add("content", contentArr);
        result.addProperty("isError", true);
        return successResponse(id, result);
    }

    public static Gson gson() {
        return GSON;
    }
}
