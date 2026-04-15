package com.imzhizi.breeze.devtools.spec;

import com.intellij.patterns.PlatformPatterns;
import com.intellij.psi.PsiComment;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.psi.PsiReferenceContributor;
import com.intellij.psi.PsiReferenceProvider;
import com.intellij.psi.PsiReferenceRegistrar;
import com.intellij.util.ProcessingContext;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Contributor that registers references for @spec annotations in Java, Scala, and Kotlin comments.
 * Registered in plugin.xml for JAVA / Scala / kotlin languages.
 */
public final class SpecJumpReferenceContributor extends PsiReferenceContributor {

    // Matches @spec followed by a relative path ending in .md
    // Supports ASCII + Chinese (and other CJK) characters in path segments
    private static final Pattern SPEC_PATTERN = Pattern.compile(
            "@spec\\s+([^*\\s]+\\.md)"
    );

    @Override
    public void registerReferenceProviders(@NotNull PsiReferenceRegistrar registrar) {
        // Match PsiComment elements only — this contributor is invoked for JAVA / Scala / kotlin
        // as declared in plugin.xml, so we don't need per-language filtering here.
        registrar.registerReferenceProvider(
                PlatformPatterns.psiElement(PsiComment.class),
                new PsiReferenceProvider() {
                    @Override
                    public PsiReference @NotNull [] getReferencesByElement(
                            @NotNull PsiElement element, @NotNull ProcessingContext context) {
                        if (!(element instanceof PsiComment comment)) {
                            return PsiReference.EMPTY_ARRAY;
                        }
                        return buildReferences(comment);
                    }
                }
        );
    }

    private static PsiReference[] buildReferences(PsiComment comment) {
        String text = comment.getText();
        if (text == null || !text.contains("@spec")) {
            return PsiReference.EMPTY_ARRAY;
        }

        List<SpecJumpReference> refs = new ArrayList<>();
        Matcher m = SPEC_PATTERN.matcher(text);
        while (m.find()) {
            String path = m.group(1);
            int start = m.start(1);
            int end   = m.end(1);
            refs.add(new SpecJumpReference(comment, new SpecJumpTarget(path), path, start, end));
        }
        return refs.isEmpty() ? PsiReference.EMPTY_ARRAY : refs.toArray(PsiReference.EMPTY_ARRAY);
    }
}
