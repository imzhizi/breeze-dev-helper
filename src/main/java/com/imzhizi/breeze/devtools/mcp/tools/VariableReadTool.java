package com.imzhizi.breeze.devtools.mcp.tools;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.project.Project;
import com.intellij.xdebugger.XDebugSession;
import com.intellij.xdebugger.XDebuggerManager;
import com.intellij.xdebugger.XSourcePosition;
import com.intellij.xdebugger.frame.*;
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
 * MCP tool: read_variables
 *
 * Threading model (learned from diagnose_variables):
 *   - computeChildren() must be called from EDT.
 *   - addChildren() callback fires on DebuggerManagerThread (~3s for remote debug).
 *   - computePresentation() callback ALSO fires on DebuggerManagerThread.
 *   - Therefore: NEVER block DebuggerManagerThread with CountDownLatch.await().
 *     Use a fully async pattern: atomic pending counter + complete future when done.
 */
public final class VariableReadTool implements McpTool {

    @Override public String getName() { return "read_variables"; }

    @Override
    public String getDescription() {
        return "Read local variables and their values from the currently paused debug frame. " +
                "The debug session must be paused at a breakpoint. " +
                "Returns variable names, values, and types, plus the current stack frame location.";
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
        CompletableFuture<String> future = new CompletableFuture<>();

        // Phase 1: all XDebugger API access on EDT
        ApplicationManager.getApplication().invokeLater(() -> {
            try {
                Project project = BreakpointHelper.getActiveProject();
                if (project == null) {
                    future.complete(McpJsonUtil.toolErrorResponse(id, "No open project found."));
                    return;
                }
                XDebugSession session = XDebuggerManager.getInstance(project).getCurrentSession();
                if (session == null) {
                    future.complete(McpJsonUtil.toolErrorResponse(id, "No active debug session."));
                    return;
                }
                if (!session.isPaused()) {
                    future.complete(McpJsonUtil.toolErrorResponse(id,
                            "Debug session '" + session.getSessionName() + "' is not paused."));
                    return;
                }
                XStackFrame frame = session.getCurrentStackFrame();
                if (frame == null) {
                    future.complete(McpJsonUtil.toolErrorResponse(id, "No current stack frame."));
                    return;
                }

                String sessionName = session.getSessionName();
                String frameLocation = getFrameLocation(frame);
                List<VariableEntry> vars = new ArrayList<>();

                // Tracks outstanding computePresentation() calls.
                // Starts at 1; decremented by each presentation callback and by markLastBatch().
                // When it hits 0, all presentations are collected → complete the future.
                AtomicInteger pending = new AtomicInteger(1);

                // Phase 2: computeChildren from EDT; callbacks fire on DebuggerManagerThread.
                // NEVER block DebuggerManagerThread – use fully async callbacks.
                frame.computeChildren(new XCompositeNode() {

                    @Override
                    public void addChildren(@NotNull XValueChildrenList children, boolean last) {
                        // Add children.size() to pending BEFORE launching presentations,
                        // so the counter stays > 0 until all callbacks are registered.
                        pending.addAndGet(children.size());

                        for (int i = 0; i < children.size(); i++) {
                            final String name = children.getName(i);
                            final XValue xValue = children.getValue(i);
                            final int idx = vars.size();
                            vars.add(new VariableEntry(name, "<pending>", null));

                            xValue.computePresentation(new XValueNode() {
                                @Override
                                @SuppressWarnings("deprecation")
                                public void setPresentation(@Nullable Icon icon, @Nullable String type,
                                                            @NotNull String value, boolean hasChildren) {
                                    // For complex objects IntelliJ may use this legacy form
                                    String display = value.isBlank() && type != null ? type : value;
                                    vars.set(idx, new VariableEntry(name, display, type));
                                    decrementAndMaybeComplete(pending, future, id, sessionName, frameLocation, vars);
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
                                    // For complex objects, rendered may be empty; fall back to type
                                    String display = rendered.isBlank() && type != null ? type : rendered;
                                    vars.set(idx, new VariableEntry(name, display, type));
                                    decrementAndMaybeComplete(pending, future, id, sessionName, frameLocation, vars);
                                }

                                @Override public void setFullValueEvaluator(@NotNull XFullValueEvaluator e) {}
                                @Override public boolean isObsolete() { return future.isDone(); }
                            }, XValuePlace.TREE);
                        }

                        if (last) {
                            // Decrement the initial +1 guard; if no children were added this completes immediately
                            decrementAndMaybeComplete(pending, future, id, sessionName, frameLocation, vars);
                        }
                    }

                    @Override public void tooManyChildren(int r) {
                        decrementAndMaybeComplete(pending, future, id, sessionName, frameLocation, vars);
                    }

                    @Override public void setAlreadySorted(boolean s) {}

                    @Override public void setErrorMessage(@NotNull String msg) {
                        future.complete(McpJsonUtil.toolErrorResponse(id, "Variable read error: " + msg));
                    }

                    @Override public void setErrorMessage(@NotNull String msg,
                                                          @Nullable XDebuggerTreeNodeHyperlink l) {
                        future.complete(McpJsonUtil.toolErrorResponse(id, "Variable read error: " + msg));
                    }

                    @Override public void setMessage(@NotNull String msg, @Nullable Icon icon,
                                                     @NotNull com.intellij.ui.SimpleTextAttributes a,
                                                     @Nullable XDebuggerTreeNodeHyperlink l) {}

                    @Override public boolean isObsolete() { return future.isDone(); }
                });

            } catch (Exception e) {
                future.complete(McpJsonUtil.toolErrorResponse(id, "read_variables failed: " + e.getMessage()));
            }
        });

        try {
            return future.get(15, TimeUnit.SECONDS);
        } catch (Exception e) {
            return McpJsonUtil.toolErrorResponse(id, "read_variables timed out: " + e.getMessage());
        }
    }

    private static void decrementAndMaybeComplete(AtomicInteger pending,
                                                   CompletableFuture<String> future,
                                                   JsonElement id,
                                                   String sessionName,
                                                   String frameLocation,
                                                   List<VariableEntry> vars) {
        if (pending.decrementAndGet() == 0 && !future.isDone()) {
            future.complete(buildResult(id, sessionName, frameLocation, vars));
        }
    }

    private static String buildResult(JsonElement id, String sessionName,
                                      String location, List<VariableEntry> vars) {
        // Filter out placeholder entries that never resolved
        List<VariableEntry> resolved = new ArrayList<>();
        for (VariableEntry v : vars) {
            if (!"<pending>".equals(v.value)) resolved.add(v);
        }

        StringBuilder sb = new StringBuilder();
        sb.append("Debug session: ").append(sessionName).append("\n");
        sb.append("Paused at: ").append(location).append("\n");
        sb.append("Variables (").append(resolved.size()).append(" total):\n");
        if (resolved.isEmpty()) {
            sb.append("  (no local variables in the current frame)");
        } else {
            for (VariableEntry v : resolved) {
                sb.append("  ").append(v.name).append(" = ").append(v.value);
                if (v.type != null && !v.type.isBlank() && !v.value.equals(v.type)) {
                    sb.append("  [").append(v.type).append("]");
                }
                sb.append("\n");
            }
        }
        return McpJsonUtil.successTextResponse(id, sb.toString().trim());
    }

    private static String getFrameLocation(XStackFrame frame) {
        try {
            XSourcePosition pos = frame.getSourcePosition();
            if (pos != null) return pos.getFile().getName() + ":" + (pos.getLine() + 1);
        } catch (Exception ignored) {}
        return "(unknown location)";
    }

    private static final class VariableEntry {
        final String name;
        final String value;
        final String type;
        VariableEntry(String name, String value, String type) {
            this.name  = name;
            this.value = value != null ? value : "null";
            this.type  = type;
        }
    }
}
