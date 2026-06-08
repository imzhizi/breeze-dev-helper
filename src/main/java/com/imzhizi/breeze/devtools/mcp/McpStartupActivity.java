package com.imzhizi.breeze.devtools.mcp;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.ProjectActivity;
import kotlin.Unit;
import kotlin.coroutines.Continuation;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Runs after the first project is fully opened and forces McpServerService
 * (now an application-level service) to initialize if it hasn't already.
 *
 * Application services are also lazy by default; touching getInstance() here
 * ensures the MCP HTTP server starts as soon as the IDE has a project open.
 */
public final class McpStartupActivity implements ProjectActivity {

    @Nullable
    @Override
    public Object execute(@NotNull Project project, @NotNull Continuation<? super Unit> continuation) {
        // Accessing the application-level service triggers its constructor → startIfEnabled()
        McpServerService.getInstance();
        return Unit.INSTANCE;
    }
}
