package com.imzhizi.breeze.devtools.preview;

import org.intellij.plugins.markdown.extensions.MarkdownBrowserPreviewExtension;
import org.intellij.plugins.markdown.ui.preview.MarkdownHtmlPanel;
import org.jetbrains.annotations.NotNull;

public final class BreezeJumpBrowserPreviewExtensionProvider implements MarkdownBrowserPreviewExtension.Provider {
    @Override
    public MarkdownBrowserPreviewExtension createBrowserExtension(@NotNull MarkdownHtmlPanel panel) {
        return new BreezeJumpBrowserPreviewExtension(panel);
    }
}