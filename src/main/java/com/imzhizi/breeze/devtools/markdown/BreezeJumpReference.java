package com.imzhizi.breeze.devtools.markdown;

import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReferenceBase;
import com.imzhizi.breeze.devtools.navigation.BreezeJumpNavigator;
import org.intellij.plugins.markdown.lang.psi.impl.MarkdownLinkDestination;
import org.jetbrains.annotations.Nullable;

public final class BreezeJumpReference extends PsiReferenceBase<MarkdownLinkDestination> {
    private final String uri;

    public BreezeJumpReference(MarkdownLinkDestination element) {
        super(element, TextRange.from(0, element.getTextLength()), true);
        this.uri = element.getText();
    }

    @Override
    public @Nullable PsiElement resolve() {
        return BreezeJumpNavigator.createNavigationElement(getElement().getProject(), getElement(), uri);
    }
}