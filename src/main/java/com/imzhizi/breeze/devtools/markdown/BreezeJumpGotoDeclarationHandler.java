package com.imzhizi.breeze.devtools.markdown;

import com.intellij.codeInsight.navigation.actions.GotoDeclarationHandler;
import com.intellij.openapi.editor.Editor;
import com.intellij.psi.PsiElement;
import com.imzhizi.breeze.devtools.navigation.BreezeJumpNavigator;
import com.imzhizi.breeze.devtools.uri.BreezeJumpUriParser;
import org.intellij.plugins.markdown.lang.psi.impl.MarkdownLinkDestination;
import org.jetbrains.annotations.Nullable;

/**
 * Handles Cmd/Ctrl+B and Cmd/Ctrl+Click for breeze-jump:// links in Markdown files.
 * The URI scheme prefix is configurable via Settings → Tools → Breeze Dev Helper.
 */
public final class BreezeJumpGotoDeclarationHandler implements GotoDeclarationHandler {

    @Override
    public PsiElement @Nullable [] getGotoDeclarationTargets(
            @Nullable PsiElement sourceElement, int offset, Editor editor) {

        if (sourceElement == null) {
            return null;
        }

        // The link destination text lives on the leaf element itself or its parent
        MarkdownLinkDestination destination = findLinkDestination(sourceElement);
        if (destination == null) {
            return null;
        }

        String text = destination.getText();
        if (!BreezeJumpUriParser.isBreezeJumpUri(text)) {
            return null;
        }

        PsiElement target = BreezeJumpNavigator.createNavigationElement(
                sourceElement.getProject(), destination, text);
        if (target == null) {
            return null;
        }
        return new PsiElement[]{target};
    }

    private static @Nullable MarkdownLinkDestination findLinkDestination(PsiElement element) {
        if (element instanceof MarkdownLinkDestination dest) {
            return dest;
        }
        PsiElement parent = element.getParent();
        if (parent instanceof MarkdownLinkDestination dest) {
            return dest;
        }
        return null;
    }
}
