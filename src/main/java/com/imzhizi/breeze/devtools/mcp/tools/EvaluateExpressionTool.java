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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;

/**
 * MCP tool: evaluate_expression
 *
 * Evaluates an arbitrary expression in the context of the currently paused debug frame.
 * Requires an active debug session that is paused at a breakpoint.
 *
 * Arguments:
 *   expression  (required) – expression to evaluate, e.g. "user.getName()" or "list.size()"
 */
public final class EvaluateExpressionTool implements McpTool {

    private static final int EVAL_TIMEOUT_SECONDS = 10;

    @Override
    public String getName() {
        return "evaluate_expression";
    }

    @Override
    public String getDescription() {
        return "Evaluate an arbitrary expression in the context of the currently paused debug frame. " +
                "Requires an active debug session paused at a breakpoint. " +
                "Examples: 'user.getName()', 'items.size()', 'order.totalPrice * 2'.";
    }

    @Override
    public JsonObject getInputSchema() {
        JsonObject schema = new JsonObject();
        schema.addProperty("type", "object");

        JsonObject props = new JsonObject();
        JsonObject exprProp = new JsonObject();
        exprProp.addProperty("type", "string");
        exprProp.addProperty("description",
                "Expression to evaluate in the current debug frame, e.g. 'user.getName()' or 'list.size()'");
        props.add("expression", exprProp);
        schema.add("properties", props);

        JsonArray required = new JsonArray();
        required.add("expression");
        schema.add("required", required);

        return schema;
    }

    @Override
    public String execute(JsonObject args, JsonElement id) {
        String expression = McpJsonUtil.getString(args, "expression");
        if (expression == null || expression.isBlank()) {
            return McpJsonUtil.toolErrorResponse(id, "Missing required argument: expression");
        }

        CompletableFuture<String> future = new CompletableFuture<>();

        // All IDE API access must happen on EDT
        ApplicationManager.getApplication().invokeLater(() -> {
            try {
                Project project = BreakpointHelper.getActiveProject();
                if (project == null) {
                    future.complete(McpJsonUtil.toolErrorResponse(id, "No open project found."));
                    return;
                }
                XDebugSession session = XDebuggerManager.getInstance(project).getCurrentSession();
                if (session == null) {
                    future.complete(McpJsonUtil.toolErrorResponse(id,
                            "No active debug session. Start a debug session and pause at a breakpoint first."));
                    return;
                }
                if (!session.isPaused()) {
                    future.complete(McpJsonUtil.toolErrorResponse(id,
                            "Debug session '" + session.getSessionName() + "' is not paused."));
                    return;
                }
                XStackFrame frame = session.getCurrentStackFrame();
                if (frame == null) {
                    future.complete(McpJsonUtil.toolErrorResponse(id,
                            "No current stack frame available."));
                    return;
                }
                XDebuggerEvaluator evaluator = frame.getEvaluator();
                if (evaluator == null) {
                    future.complete(McpJsonUtil.toolErrorResponse(id,
                            "The current debug session does not support expression evaluation."));
                    return;
                }

                XSourcePosition position = frame.getSourcePosition();
                XExpression xExpr = buildExpression(expression);

                // evaluate() fires its callback on DebuggerManagerThread.
                // computePresentation inside that callback ALSO fires on DebuggerManagerThread,
                // so we must NOT block DebuggerManagerThread with CountDownLatch.await().
                // Instead, complete the future directly from the setPresentation callback.
                evaluator.evaluate(xExpr, new XDebuggerEvaluator.XEvaluationCallback() {
                    @Override
                    public void evaluated(@NotNull XValue value) {
                        value.computePresentation(new XValueNode() {
                            @Override
                            @SuppressWarnings("deprecation")
                            public void setPresentation(@Nullable Icon icon, @Nullable String type,
                                                        @NotNull String v, boolean hasChildren) {
                                String display = v.isBlank() && type != null ? type : v;
                                future.complete(McpJsonUtil.successTextResponse(id,
                                        "Expression: " + expression + "\nResult: " + display +
                                                (type != null && !type.isBlank() && !display.equals(type)
                                                        ? "  [" + type + "]" : "")));
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
                                String display = rendered.isBlank() && type != null ? type : rendered;
                                future.complete(McpJsonUtil.successTextResponse(id,
                                        "Expression: " + expression + "\nResult: " + display +
                                                (type != null && !type.isBlank() && !display.equals(type)
                                                        ? "  [" + type + "]" : "")));
                            }

                            @Override public void setFullValueEvaluator(@NotNull XFullValueEvaluator e) {}
                            @Override public boolean isObsolete() { return future.isDone(); }
                        }, XValuePlace.TREE);
                    }

                    @Override
                    public void errorOccurred(@NotNull String errorMessage) {
                        future.complete(McpJsonUtil.toolErrorResponse(id,
                                "Evaluation error: " + errorMessage));
                    }
                }, position);

            } catch (Exception e) {
                future.complete(McpJsonUtil.toolErrorResponse(id,
                        "evaluate_expression failed: " + e.getMessage()));
            }
        });

        try {
            return future.get(EVAL_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            return McpJsonUtil.toolErrorResponse(id,
                    "Expression evaluation timed out after " + EVAL_TIMEOUT_SECONDS + "s.");
        } catch (Exception e) {
            return McpJsonUtil.toolErrorResponse(id, "evaluate_expression error: " + e.getMessage());
        }
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
