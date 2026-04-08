package com.imzhizi.breeze.devtools.navigation;

import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.impl.FakePsiElement;
import com.intellij.psi.impl.light.LightElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class BreezeJumpNavigationElement extends FakePsiElement {
    private final Project project;
    private final PsiElement sourceElement;
    private final String presentableText;
    private final BreezeJumpResolvedTarget resolvedTarget;

    public BreezeJumpNavigationElement(Project project,
                                       PsiElement sourceElement,
                                       String presentableText,
                                       BreezeJumpResolvedTarget resolvedTarget) {
        this.project = project;
        this.sourceElement = sourceElement;
        this.presentableText = presentableText;
        this.resolvedTarget = resolvedTarget;
    }

    @Override
    public PsiElement getParent() {
        return sourceElement;
    }

    @Override
    public PsiFile getContainingFile() {
        return sourceElement.getContainingFile();
    }

    @Override
    public @NotNull Project getProject() {
        return project;
    }

    @Override
    public PsiElement getNavigationElement() {
        return resolvedTarget.getElement();
    }

    @Override
    public boolean canNavigate() {
        return true;
    }

    @Override
    public void navigate(boolean requestFocus) {
        BreezeJumpNavigator.navigate(project, resolvedTarget, requestFocus);
    }

    @Override
    public @Nullable String getName() {
        return presentableText;
    }

    @Override
    public String toString() {
        return presentableText;
    }
}