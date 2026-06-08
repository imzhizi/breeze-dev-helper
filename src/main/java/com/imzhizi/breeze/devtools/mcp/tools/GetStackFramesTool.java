package com.imzhizi.breeze.devtools.mcp.tools;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.xdebugger.XDebugSession;
import com.intellij.xdebugger.XDebuggerManager;
import com.intellij.xdebugger.XSourcePosition;
import com.intellij.xdebugger.frame.XExecutionStack;
import com.intellij.xdebugger.frame.XStackFrame;
import com.intellij.xdebugger.frame.XSuspendContext;
import com.imzhizi.breeze.devtools.mcp.McpJsonUtil;
import com.imzhizi.breeze.devtools.mcp.McpTool;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * MCP tool: get_stack_frames
 *
 * Returns the full call stack for the currently paused debug session.
 * computeStackFrames fires its callback on DebuggerManagerThread, so we
 * complete the future directly from the callback (no blocking latches).
 */
public final class GetStackFramesTool implements McpTool {

    @Override public String getName() { return "get_stack_frames"; }

    @Override
    public String getDescription() {
        return "Get the full call stack for the currently paused debug session. " +
                "Returns each frame with its method name and source location (file:line). " +
                "Frame 0 is the current (innermost) frame. " +
                "Requires an active session paused at a breakpoint.";
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
                XSuspendContext suspendContext = session.getSuspendContext();
                if (suspendContext == null) {
                    future.complete(McpJsonUtil.toolErrorResponse(id, "No suspend context available."));
                    return;
                }
                XExecutionStack activeStack = suspendContext.getActiveExecutionStack();
                if (activeStack == null) {
                    future.complete(McpJsonUtil.toolErrorResponse(id, "No active execution stack."));
                    return;
                }

                String sessionName = session.getSessionName();
                List<String> frameLines = new ArrayList<>();

                // computeStackFrames fires on DebuggerManagerThread – complete future directly
                activeStack.computeStackFrames(0, new XExecutionStack.XStackFrameContainer() {
                    @Override
                    public void addStackFrames(@NotNull List<? extends XStackFrame> frames, boolean last) {
                        for (XStackFrame frame : frames) {
                            int idx = frameLines.size();
                            String method = getFrameLabel(frame);
                            String location = getFrameLocation(frame);
                            frameLines.add(String.format("  [%d] %s  (%s)", idx, method, location));
                        }
                        if (last) {
                            future.complete(buildResult(id, sessionName, frameLines));
                        }
                    }

                    @Override
                    public void errorOccurred(@NotNull String msg) {
                        future.complete(McpJsonUtil.toolErrorResponse(id, "Stack frame error: " + msg));
                    }

                    @Override
                    public boolean isObsolete() { return future.isDone(); }
                });

            } catch (Exception e) {
                future.complete(McpJsonUtil.toolErrorResponse(id,
                        "get_stack_frames failed: " + e.getMessage()));
            }
        });

        try {
            return future.get(15, TimeUnit.SECONDS);
        } catch (Exception e) {
            return McpJsonUtil.toolErrorResponse(id, "get_stack_frames timed out: " + e.getMessage());
        }
    }

    private static String getFrameLabel(XStackFrame frame) {
        StringBuilder sb = new StringBuilder();
        frame.customizePresentation(new com.intellij.ui.ColoredTextContainer() {
            @Override
            public void append(@NotNull String fragment, @NotNull SimpleTextAttributes attributes) {
                sb.append(fragment);
            }
            @Override public void setIcon(@Nullable Icon icon) {}
            @Override public void setToolTipText(@Nullable String text) {}
        });
        String label = sb.toString().trim();
        return label.isEmpty() ? frame.getClass().getSimpleName() : label;
    }

    private static String getFrameLocation(XStackFrame frame) {
        try {
            XSourcePosition pos = frame.getSourcePosition();
            if (pos != null) return pos.getFile().getName() + ":" + (pos.getLine() + 1);
        } catch (Exception ignored) {}
        return "unknown";
    }

    private static String buildResult(JsonElement id, String sessionName, List<String> frames) {
        StringBuilder sb = new StringBuilder();
        sb.append("Debug session: ").append(sessionName).append("\n");
        sb.append("Call stack (").append(frames.size()).append(" frames):\n");
        for (String line : frames) sb.append(line).append("\n");
        return McpJsonUtil.successTextResponse(id, sb.toString().trim());
    }
}
