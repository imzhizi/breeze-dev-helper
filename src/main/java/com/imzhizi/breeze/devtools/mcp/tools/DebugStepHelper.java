package com.imzhizi.breeze.devtools.mcp.tools;

import com.google.gson.JsonElement;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.xdebugger.XDebugSession;
import com.intellij.xdebugger.XDebugSessionListener;
import com.intellij.xdebugger.XDebuggerManager;
import com.imzhizi.breeze.devtools.mcp.McpJsonUtil;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;

/**
 * Shared logic for step-based debug tools (step_over, step_into, step_out).
 *
 * All IDE API calls run on the EDT. A session listener completes a CompletableFuture
 * when the session pauses again, which the calling (HTTP handler) thread awaits.
 */
final class DebugStepHelper {

    private static final int STEP_TIMEOUT_SECONDS = 15;

    private DebugStepHelper() {
    }

    static String executeStep(String toolName, Consumer<XDebugSession> stepAction, JsonElement id) {
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
                    future.complete(McpJsonUtil.toolErrorResponse(id,
                            "No active debug session. Start a debug session first."));
                    return;
                }
                if (!session.isPaused()) {
                    future.complete(McpJsonUtil.toolErrorResponse(id,
                            "Debug session '" + session.getSessionName() + "' is not paused."));
                    return;
                }

                String sessionName = session.getSessionName();

                // Register listener before calling step to avoid missing the pause event
                session.addSessionListener(new XDebugSessionListener() {
                    @Override
                    public void sessionPaused() {
                        session.removeSessionListener(this);
                        String loc = DebugStatusTool.resolveLocation(project, session);
                        future.complete(McpJsonUtil.successTextResponse(id,
                                toolName + " in session '" + sessionName + "'. Now at " + loc));
                    }

                    @Override
                    public void sessionStopped() {
                        session.removeSessionListener(this);
                        future.complete(McpJsonUtil.successTextResponse(id,
                                toolName + " in session '" + sessionName + "'. Session has stopped."));
                    }
                });

                // Step actions are safe to call on EDT directly
                stepAction.accept(session);

            } catch (Exception e) {
                future.complete(McpJsonUtil.toolErrorResponse(id,
                        toolName + " failed: " + e.getMessage()));
            }
        });

        try {
            return future.get(STEP_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            return McpJsonUtil.successTextResponse(id,
                    toolName + " initiated, but session did not pause within " +
                            STEP_TIMEOUT_SECONDS + "s (program may still be running).");
        } catch (Exception e) {
            return McpJsonUtil.toolErrorResponse(id, toolName + " error: " + e.getMessage());
        }
    }
}
