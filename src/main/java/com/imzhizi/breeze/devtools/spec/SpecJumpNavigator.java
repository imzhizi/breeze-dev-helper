package com.imzhizi.breeze.devtools.spec;

import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.Nullable;

/**
 * Handles navigation to spec markdown files
 */
public final class SpecJumpNavigator {
    private SpecJumpNavigator() {
    }

    /**
     * Open the spec file in the editor
     */
    public static boolean open(Project project, SpecJumpTarget target) {
        SpecJumpResolvedTarget resolvedTarget = resolve(project, target);
        if (resolvedTarget == null) {
            return false;
        }
        navigate(project, resolvedTarget, true);
        return true;
    }

    /**
     * Create a navigation element for the resolved target
     */
    public static @Nullable PsiElement createNavigationElement(Project project, PsiElement sourceElement, SpecJumpTarget target) {
        SpecJumpResolvedTarget resolvedTarget = resolve(project, target);
        if (resolvedTarget == null) {
            return null;
        }
        return new SpecJumpNavigationElement(project, sourceElement, target.getRelativePath(), resolvedTarget);
    }

    static @Nullable SpecJumpResolvedTarget resolve(Project project, SpecJumpTarget target) {
        return SpecJumpResolver.resolve(project, target);
    }

    static void navigate(Project project, SpecJumpResolvedTarget resolvedTarget, boolean requestFocus) {
        OpenFileDescriptor descriptor = new OpenFileDescriptor(
                project,
                resolvedTarget.getFile(),
                resolvedTarget.getOffset()
        );
        descriptor.navigate(requestFocus);
    }
}
