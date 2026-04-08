package com.imzhizi.breeze.devtools.navigation;

import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.imzhizi.breeze.devtools.resolve.BreezeJumpResolver;
import com.imzhizi.breeze.devtools.uri.BreezeJumpTarget;
import com.imzhizi.breeze.devtools.uri.BreezeJumpUriParser;
import org.jetbrains.annotations.Nullable;

public final class BreezeJumpNavigator {
    private BreezeJumpNavigator() {
    }

    public static boolean open(Project project, String uri) {
        BreezeJumpResolvedTarget resolvedTarget = resolve(project, uri);
        if (resolvedTarget == null) {
            return false;
        }
        navigate(project, resolvedTarget, true);
        return true;
    }

    public static @Nullable PsiElement createNavigationElement(Project project, PsiElement sourceElement, String uri) {
        BreezeJumpResolvedTarget resolvedTarget = resolve(project, uri);
        if (resolvedTarget == null) {
            return null;
        }
        return new BreezeJumpNavigationElement(project, sourceElement, uri, resolvedTarget);
    }

    static @Nullable BreezeJumpResolvedTarget resolve(Project project, String uri) {
        BreezeJumpTarget target = BreezeJumpUriParser.parse(uri);
        if (target == null) {
            return null;
        }
        return BreezeJumpResolver.resolve(project, target);
    }

    static void navigate(Project project, BreezeJumpResolvedTarget resolvedTarget, boolean requestFocus) {
        OpenFileDescriptor descriptor = new OpenFileDescriptor(project, resolvedTarget.getFile(), resolvedTarget.getOffset());
        descriptor.navigate(requestFocus);
    }
}