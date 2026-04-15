package com.imzhizi.breeze.devtools.spec;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiManager;
import org.jetbrains.annotations.Nullable;

/**
 * Resolves @spec annotation paths to actual markdown files.
 * Paths are resolved relative to the project base directory.
 */
public final class SpecJumpResolver {

    private SpecJumpResolver() {
    }

    public static @Nullable SpecJumpResolvedTarget resolve(Project project, SpecJumpTarget target) {
        String normalizedPath = target.getNormalizedPath();

        // Try every content root (usually the project root)
        VirtualFile[] contentRoots = ProjectRootManager.getInstance(project).getContentRoots();
        for (VirtualFile root : contentRoots) {
            SpecJumpResolvedTarget result = resolveUnder(project, root, normalizedPath);
            if (result != null) {
                return result;
            }
        }

        // Fall back: use project base path (covers projects with no module content roots)
        String basePath = project.getBasePath();
        if (basePath != null) {
            VirtualFile baseDir = LocalFileSystem.getInstance().findFileByPath(basePath);
            if (baseDir != null) {
                SpecJumpResolvedTarget result = resolveUnder(project, baseDir, normalizedPath);
                if (result != null) {
                    return result;
                }
            }
        }

        return null;
    }

    /**
     * Walk down each path segment from {@code root} and return a resolved target if found.
     */
    private static @Nullable SpecJumpResolvedTarget resolveUnder(
            Project project, VirtualFile root, String relativePath) {
        VirtualFile file = walkPath(root, relativePath);
        if (file != null) {
            return createResolvedTarget(project, file);
        }
        return null;
    }

    /**
     * Walk down each "/" separated segment from {@code root}.
     * Each segment is matched case-insensitively.
     */
    private static @Nullable VirtualFile walkPath(VirtualFile root, String relativePath) {
        String[] parts = relativePath.split("/");
        VirtualFile current = root;
        for (String part : parts) {
            if (part.isEmpty()) continue;
            current = findChildIgnoreCase(current, part);
            if (current == null) {
                return null;
            }
        }
        return current.isValid() && !current.isDirectory() ? current : null;
    }

    private static @Nullable VirtualFile findChildIgnoreCase(VirtualFile dir, String name) {
        // Exact match first (faster)
        VirtualFile exact = dir.findChild(name);
        if (exact != null) {
            return exact;
        }
        // Case-insensitive fallback
        for (VirtualFile child : dir.getChildren()) {
            if (child.getName().equalsIgnoreCase(name)) {
                return child;
            }
        }
        return null;
    }

    private static @Nullable SpecJumpResolvedTarget createResolvedTarget(Project project, VirtualFile file) {
        PsiManager psiManager = PsiManager.getInstance(project);
        com.intellij.psi.PsiFile psiFile = psiManager.findFile(file);
        if (psiFile == null) {
            return null;
        }
        PsiElement element = psiFile.getFirstChild();
        if (element == null) {
            element = psiFile;
        }
        return SpecJumpResolvedTarget.fromVirtualFile(file, element);
    }
}
