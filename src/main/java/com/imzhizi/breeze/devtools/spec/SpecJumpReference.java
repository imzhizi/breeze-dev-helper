package com.imzhizi.breeze.devtools.spec;

import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReferenceBase;
import org.jetbrains.annotations.Nullable;

/**
 * Represents a reference from @spec annotation to a markdown file
 */
public final class SpecJumpReference extends PsiReferenceBase<PsiElement> {
    private final SpecJumpTarget target;
    private final String pathText;

    public SpecJumpReference(PsiElement element, SpecJumpTarget target, String pathText, int startOffset, int endOffset) {
        super(element, TextRange.create(startOffset, endOffset), true);
        this.target = target;
        this.pathText = pathText;
    }

    @Override
    public @Nullable PsiElement resolve() {
        return SpecJumpNavigator.createNavigationElement(getElement().getProject(), getElement(), target);
    }

    @Override
    public @Nullable String getCanonicalText() {
        return pathText;
    }

    public SpecJumpTarget getSpecTarget() {
        return target;
    }
}
