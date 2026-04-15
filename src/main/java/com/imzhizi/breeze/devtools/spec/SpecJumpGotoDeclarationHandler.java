package com.imzhizi.breeze.devtools.spec;

import com.intellij.codeInsight.navigation.actions.GotoDeclarationHandler;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiComment;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.util.PsiTreeUtil;
import com.imzhizi.breeze.devtools.settings.BreezeSettings;
import org.jetbrains.annotations.Nullable;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Handles Cmd/Ctrl+B and Cmd/Ctrl+Click navigation for @spec annotations in code comments.
 * The annotation keyword is configurable via Settings → Tools → Breeze Dev Helper.
 * Default format: @spec docs/specs/xxx-spec.md
 */
public final class SpecJumpGotoDeclarationHandler implements GotoDeclarationHandler {

    /** Build a pattern from the configured keyword. Cached until keyword changes. */
    private static volatile String  cachedKeyword = null;
    private static volatile Pattern cachedPattern = null;

    private static Pattern getPattern() {
        String keyword = BreezeSettings.getInstance().specAnnotationKeyword;
        if (keyword == null || keyword.isBlank()) {
            keyword = "@spec";
        }
        if (!keyword.equals(cachedKeyword)) {
            cachedKeyword = keyword;
            cachedPattern = Pattern.compile(
                    Pattern.quote(keyword) + "\\s+([^*\\s]+\\.md)"
            );
        }
        return cachedPattern;
    }

    @Override
    public PsiElement @Nullable [] getGotoDeclarationTargets(
            @Nullable PsiElement sourceElement, int offset, Editor editor) {

        if (sourceElement == null) {
            return null;
        }

        // Walk up to find a PsiComment ancestor (or the element itself)
        PsiComment comment = findContainingComment(sourceElement);
        if (comment == null) {
            return null;
        }

        // Find which @spec path (if any) the caret is positioned on
        String commentText = comment.getText();
        String keyword = BreezeSettings.getInstance().specAnnotationKeyword;
        if (keyword == null || keyword.isBlank()) keyword = "@spec";
        if (commentText == null || !commentText.contains(keyword)) {
            return null;
        }

        // Offset within the comment element
        int caretInComment = offset - comment.getTextRange().getStartOffset();

        String path = findPathAtCaret(commentText, caretInComment);
        if (path == null) {
            return null;
        }

        Project project = sourceElement.getProject();
        VirtualFile specFile = resolveSpecFile(project, path);
        if (specFile == null) {
            return null;
        }

        PsiFile psiFile = PsiManager.getInstance(project).findFile(specFile);
        if (psiFile == null) {
            return null;
        }

        return new PsiElement[]{psiFile};
    }

    // -----------------------------------------------------------------------
    // helpers
    // -----------------------------------------------------------------------

    /** Returns the PsiComment that contains or IS the given element. */
    private static @Nullable PsiComment findContainingComment(PsiElement element) {
        if (element instanceof PsiComment) {
            return (PsiComment) element;
        }
        return PsiTreeUtil.getParentOfType(element, PsiComment.class, false);
    }

    /**
     * Given the full text of a comment and a caret position within it,
     * returns the @spec path if the caret is positioned over it, or null otherwise.
     */
    private static @Nullable String findPathAtCaret(String commentText, int caretPos) {
        Matcher m = getPattern().matcher(commentText);
        while (m.find()) {
            int pathStart = m.start(1);
            int pathEnd   = m.end(1);
            // Also accept clicks anywhere on the "@spec <path>" token
            if (caretPos >= m.start() && caretPos <= pathEnd) {
                return m.group(1);
            }
        }
        return null;
    }

    /**
     * Resolve a relative path (e.g. "docs/specs/foo.md") against every content root
     * of the project, then against the project base dir as fallback.
     */
    private static @Nullable VirtualFile resolveSpecFile(Project project, String relativePath) {
        // Try each content root (usually the project root in a normal Maven/Gradle project)
        for (VirtualFile root : ProjectRootManager.getInstance(project).getContentRoots()) {
            VirtualFile f = walkPath(root, relativePath);
            if (f != null) return f;
        }

        // Fallback: project base directory
        String basePath = project.getBasePath();
        if (basePath != null) {
            VirtualFile baseDir = LocalFileSystem.getInstance().findFileByPath(basePath);
            if (baseDir != null) {
                VirtualFile f = walkPath(baseDir, relativePath);
                if (f != null) return f;
            }
        }
        return null;
    }

    /**
     * Walk each "/" separated segment starting from {@code root},
     * matching each segment case-insensitively.
     */
    private static @Nullable VirtualFile walkPath(VirtualFile root, String relativePath) {
        String[] parts = relativePath.split("/");
        VirtualFile current = root;
        for (String part : parts) {
            if (part.isEmpty()) continue;
            current = findChildIgnoreCase(current, part);
            if (current == null) return null;
        }
        return (current.isValid() && !current.isDirectory()) ? current : null;
    }

    private static @Nullable VirtualFile findChildIgnoreCase(VirtualFile dir, String name) {
        VirtualFile exact = dir.findChild(name);
        if (exact != null) return exact;
        for (VirtualFile child : dir.getChildren()) {
            if (child.getName().equalsIgnoreCase(name)) return child;
        }
        return null;
    }
}
