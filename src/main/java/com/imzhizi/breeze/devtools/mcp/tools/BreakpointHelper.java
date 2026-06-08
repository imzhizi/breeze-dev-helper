package com.imzhizi.breeze.devtools.mcp.tools;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiFile;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.xdebugger.breakpoints.XBreakpoint;
import com.intellij.xdebugger.breakpoints.XBreakpointType;
import com.intellij.xdebugger.breakpoints.XLineBreakpointType;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.java.debugger.breakpoints.properties.JavaExceptionBreakpointProperties;
import org.jetbrains.java.debugger.breakpoints.properties.JavaLineBreakpointProperties;

import java.awt.*;

/**
 * Shared helper for breakpoint tools: locates the Java line breakpoint type,
 * resolves the active project, and maps between className and VirtualFile.
 */
final class BreakpointHelper {

    private BreakpointHelper() {
    }

    /**
     * Returns the {@code XLineBreakpointType} for Java line breakpoints,
     * or {@code null} if not found (shouldn't happen in an IDE with Java support).
     */
    @SuppressWarnings("unchecked")
    static @Nullable XLineBreakpointType<JavaLineBreakpointProperties> javaLineBreakpointType() {
        for (XBreakpointType<?, ?> type : XBreakpointType.EXTENSION_POINT_NAME.getExtensionList()) {
            if (type instanceof XLineBreakpointType && "java-line".equals(type.getId())) {
                return (XLineBreakpointType<JavaLineBreakpointProperties>) type;
            }
        }
        return null;
    }

    /**
     * Returns the {@code XBreakpointType} for Java exception breakpoints (type id "java-exception"),
     * or {@code null} if not found.
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    static @Nullable XBreakpointType<XBreakpoint<JavaExceptionBreakpointProperties>, JavaExceptionBreakpointProperties> javaExceptionBreakpointType() {
        for (XBreakpointType<?, ?> type : XBreakpointType.EXTENSION_POINT_NAME.getExtensionList()) {
            if ("java-exception".equals(type.getId())) {
                return (XBreakpointType) type;
            }
        }
        return null;
    }

    /**
     * Returns the currently focused/active project. Falls back to the first open project.
     */
    static @Nullable Project getActiveProject() {
        WindowManager wm = WindowManager.getInstance();
        for (Project p : ProjectManager.getInstance().getOpenProjects()) {
            Window frame = wm.suggestParentWindow(p);
            if (frame != null && frame.isFocused()) {
                return p;
            }
        }
        Project[] open = ProjectManager.getInstance().getOpenProjects();
        return open.length > 0 ? open[0] : null;
    }

    /**
     * Resolves a fully-qualified class name to the VirtualFile of its source.
     * Searches within project scope only (not libraries).
     */
    static @Nullable VirtualFile resolveSourceFile(Project project, String className) {
        PsiClass[] classes = JavaPsiFacade.getInstance(project)
                .findClasses(className, GlobalSearchScope.projectScope(project));
        for (PsiClass psiClass : classes) {
            PsiFile psiFile = psiClass.getContainingFile();
            if (psiFile != null && psiFile.getVirtualFile() != null) {
                return psiFile.getVirtualFile();
            }
        }
        return null;
    }

    /**
     * Resolves a VirtualFile back to the fully-qualified class name of the top-level class.
     * Safe to call from any thread (wraps PSI access in a read action).
     * Returns null if not resolvable (e.g. Kotlin or Scala files).
     */
    static @Nullable String resolveClassName(Project project, String fileUrl) {
        return ApplicationManager.getApplication().runReadAction((Computable<String>) () -> {
            try {
                com.intellij.openapi.vfs.VirtualFile vf =
                        com.intellij.openapi.vfs.VirtualFileManager.getInstance().findFileByUrl(fileUrl);
                if (vf == null) return null;
                PsiFile psiFile = com.intellij.psi.PsiManager.getInstance(project).findFile(vf);
                if (psiFile instanceof com.intellij.psi.PsiJavaFile javaFile) {
                    PsiClass[] classes = javaFile.getClasses();
                    if (classes.length > 0) return classes[0].getQualifiedName();
                }
            } catch (Exception ignored) {
            }
            return null;
        });
    }
}
