package com.imzhizi.breeze.devtools.markdown;

import com.intellij.patterns.PlatformPatterns;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.psi.PsiReferenceContributor;
import com.intellij.psi.PsiReferenceProvider;
import com.intellij.psi.PsiReferenceRegistrar;
import com.intellij.util.ProcessingContext;
import com.imzhizi.breeze.devtools.uri.BreezeJumpUriParser;
import org.intellij.plugins.markdown.lang.psi.impl.MarkdownLinkDestination;
import org.jetbrains.annotations.NotNull;

public final class BreezeJumpReferenceContributor extends PsiReferenceContributor {
    @Override
    public void registerReferenceProviders(@NotNull PsiReferenceRegistrar registrar) {
        registrar.registerReferenceProvider(
                PlatformPatterns.psiElement(MarkdownLinkDestination.class),
                new PsiReferenceProvider() {
                    @Override
                    public PsiReference @NotNull [] getReferencesByElement(@NotNull PsiElement element,
                                                                           @NotNull ProcessingContext context) {
                        if (!(element instanceof MarkdownLinkDestination destination)) {
                            return PsiReference.EMPTY_ARRAY;
                        }
                        if (!BreezeJumpUriParser.isBreezeJumpUri(destination.getText())) {
                            return PsiReference.EMPTY_ARRAY;
                        }
                        return new PsiReference[]{new BreezeJumpReference(destination)};
                    }
                }
        );
    }
}