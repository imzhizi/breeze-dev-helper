package com.imzhizi.breeze.devtools.mcp.tools;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.intellij.lang.Language;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.project.Project;
import com.intellij.xdebugger.XDebugSession;
import com.intellij.xdebugger.XDebuggerManager;
import com.intellij.xdebugger.XExpression;
import com.intellij.xdebugger.XSourcePosition;
import com.intellij.xdebugger.evaluation.EvaluationMode;
import com.intellij.xdebugger.evaluation.XDebuggerEvaluator;
import com.intellij.xdebugger.frame.XFullValueEvaluator;
import com.intellij.xdebugger.frame.XStackFrame;
import com.intellij.xdebugger.frame.XValue;
import com.intellij.xdebugger.frame.XValueNode;
import com.intellij.xdebugger.frame.XValuePlace;
import com.intellij.xdebugger.frame.presentation.XValuePresentation;
import com.imzhizi.breeze.devtools.mcp.McpJsonUtil;
import com.imzhizi.breeze.devtools.mcp.McpTool;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * MCP tool: evaluate_expressions (batch)
 *
 * Evaluates multiple expressions in a single call, firing all requests
 * concurrently on the DebuggerManagerThread. Results arrive as each
 * setPresentation callback fires; completes when the last one is done.
 *
 * Reduces latency significantly vs calling evaluate_expression N times
 * (each with ~3s DebuggerManagerThread round-trip for remote debug).
 */
public final class EvaluateExpressionsTool implements McpTool {

    @Override public String getName() { return "evaluate_expressions"; }

    @Override
    public String getDescription() {
        return "Evaluate multiple expressions at once in the current debug frame. " +
                "Faster than calling evaluate_expression N times because all requests " +
                "are fired concurrently. Requires an active session paused at a breakpoint. " +
                "Pass an array of expression strings.";
    }

    @Override
    public JsonObject getInputSchema() {
        JsonObject schema = new JsonObject();
        schema.addProperty("type", "object");

        JsonObject props = new JsonObject();
        JsonObject exprsProp = new JsonObject();
        exprsProp.addProperty("type", "array");
        JsonObject items = new JsonObject();
        items.addProperty("type", "string");
        exprsProp.add("items", items);
        exprsProp.addProperty("description",
                "List of expressions to evaluate, e.g. [\"hostId\", \"context.getShopType().name()\"]");
        props.add("expressions", exprsProp);
        schema.add("properties", props);

        JsonArray required = new JsonArray();
        required.add("expressions");
        schema.add("required", required);
        return schema;
    }

    @Override
    public String execute(JsonObject args, JsonElement id) {
        JsonElement exprEl = args != null ? args.get("expressions") : null;
        if (exprEl == null || !exprEl.isJsonArray() || exprEl.getAsJsonArray().isEmpty()) {
            return McpJsonUtil.toolErrorResponse(id, "Missing or empty: expressions (must be a non-empty array)");
        }

        List<String> expressions = new ArrayList<>();
        for (JsonElement e : exprEl.getAsJsonArray()) {
            String s = e.isJsonPrimitive() ? e.getAsString() : null;
            if (s != null && !s.isBlank()) expressions.add(s);
        }
        if (expressions.isEmpty()) {
            return McpJsonUtil.toolErrorResponse(id, "No valid expressions provided.");
        }

        CompletableFuture<String> future = new CompletableFuture<>();

        ApplicationManager.getApplication().invokeLater(() -> {
            try {
                Project project = BreakpointHelper.getActiveProject();
                if (project == null) { future.complete(McpJsonUtil.toolErrorResponse(id, "No open project.")); return; }

                XDebugSession session = XDebuggerManager.getInstance(project).getCurrentSession();
                if (session == null) { future.complete(McpJsonUtil.toolErrorResponse(id, "No active debug session.")); return; }
                if (!session.isPaused()) { future.complete(McpJsonUtil.toolErrorResponse(id, "Session not paused.")); return; }

                XStackFrame frame = session.getCurrentStackFrame();
                if (frame == null) { future.complete(McpJsonUtil.toolErrorResponse(id, "No current stack frame.")); return; }

                XDebuggerEvaluator evaluator = frame.getEvaluator();
                if (evaluator == null) { future.complete(McpJsonUtil.toolErrorResponse(id, "Session does not support evaluation.")); return; }

                XSourcePosition position = frame.getSourcePosition();
                int count = expressions.size();
                String[] results = new String[count];
                AtomicInteger pending = new AtomicInteger(count);

                // Fire all evaluations concurrently — each completes independently on DebuggerManagerThread
                for (int i = 0; i < count; i++) {
                    final int idx = i;
                    final String expr = expressions.get(i);
                    results[idx] = "<pending>";

                    evaluator.evaluate(buildExpression(expr), new XDebuggerEvaluator.XEvaluationCallback() {
                        @Override
                        public void evaluated(@NotNull XValue value) {
                            value.computePresentation(new XValueNode() {
                                @Override @SuppressWarnings("deprecation")
                                public void setPresentation(@Nullable Icon icon, @Nullable String type,
                                                            @NotNull String v, boolean hasChildren) {
                                    results[idx] = v.isBlank() && type != null ? type : v;
                                    maybeComplete();
                                }

                                @Override
                                public void setPresentation(@Nullable Icon icon,
                                                            @NotNull XValuePresentation presentation,
                                                            boolean hasChildren) {
                                    StringBuilder sb = new StringBuilder();
                                    presentation.renderValue(new XValuePresentation.XValueTextRenderer() {
                                        @Override public void renderValue(@NotNull String v) { sb.append(v); }
                                        @Override public void renderStringValue(@NotNull String v) { sb.append('"').append(v).append('"'); }
                                        @Override public void renderNumericValue(@NotNull String v) { sb.append(v); }
                                        @Override public void renderKeywordValue(@NotNull String v) { sb.append(v); }
                                        @Override public void renderValue(@NotNull String v, @NotNull TextAttributesKey a) { sb.append(v); }
                                        @Override public void renderStringValue(@NotNull String v, @Nullable String x, int max) {
                                            sb.append('"').append(v.length() <= max ? v : v.substring(0, max) + "...").append('"');
                                        }
                                        @Override public void renderComment(@NotNull String c) { sb.append(" // ").append(c); }
                                        @Override public void renderSpecialSymbol(@NotNull String s) { sb.append(s); }
                                        @Override public void renderError(@NotNull String e) { sb.append("<error: ").append(e).append('>'); }
                                    });
                                    String rendered = sb.toString();
                                    String type = presentation.getType();
                                    results[idx] = rendered.isBlank() && type != null ? type : rendered;
                                    maybeComplete();
                                }

                                @Override public void setFullValueEvaluator(@NotNull XFullValueEvaluator e) {}
                                @Override public boolean isObsolete() { return future.isDone(); }

                                private void maybeComplete() {
                                    if (pending.decrementAndGet() == 0 && !future.isDone()) {
                                        future.complete(buildResult(id, expressions, results));
                                    }
                                }
                            }, XValuePlace.TREE);
                        }

                        @Override
                        public void errorOccurred(@NotNull String errorMessage) {
                            results[idx] = "<error: " + errorMessage + ">";
                            if (pending.decrementAndGet() == 0 && !future.isDone()) {
                                future.complete(buildResult(id, expressions, results));
                            }
                        }
                    }, position);
                }

            } catch (Exception e) {
                future.complete(McpJsonUtil.toolErrorResponse(id, "evaluate_expressions failed: " + e.getMessage()));
            }
        });

        try {
            return future.get(15, TimeUnit.SECONDS);
        } catch (Exception e) {
            return McpJsonUtil.toolErrorResponse(id, "evaluate_expressions timed out: " + e.getMessage());
        }
    }

    private static String buildResult(JsonElement id, List<String> expressions, String[] results) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < expressions.size(); i++) {
            sb.append(expressions.get(i)).append(" = ").append(results[i]).append("\n");
        }
        return McpJsonUtil.successTextResponse(id, sb.toString().trim());
    }

    private static XExpression buildExpression(String text) {
        return new XExpression() {
            @Override public @NotNull String getExpression() { return text; }
            @Override public @Nullable Language getLanguage() { return null; }
            @Override public @Nullable String getCustomInfo() { return null; }
            @Override public @NotNull EvaluationMode getMode() { return EvaluationMode.EXPRESSION; }
        };
    }
}
