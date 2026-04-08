package com.imzhizi.breeze.devtools.preview;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.util.Disposer;
import org.intellij.plugins.markdown.extensions.MarkdownBrowserPreviewExtension;
import org.intellij.plugins.markdown.ui.preview.BrowserPipe;
import org.intellij.plugins.markdown.ui.preview.MarkdownHtmlPanel;
import org.intellij.plugins.markdown.ui.preview.ResourceProvider;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public final class BreezeJumpBrowserPreviewExtension implements MarkdownBrowserPreviewExtension, ResourceProvider {
    private static final String EVENT_NAME = "breezeJumpOpenLink";
    private static final String SCRIPT_NAME = "breeze-jump/breezeJumpPreview.js";

    private final MarkdownHtmlPanel panel;
    private final BrowserPipe.Handler handler;
    private final List<String> scripts;

    public BreezeJumpBrowserPreviewExtension(MarkdownHtmlPanel panel) {
        this.panel = panel;
        this.handler = message -> BreezeJumpPreviewHandler.open(panel.getProject(), message);
        this.scripts = List.of(SCRIPT_NAME);

        BrowserPipe browserPipe = panel.getBrowserPipe();
        if (browserPipe != null) {
            browserPipe.subscribe(EVENT_NAME, handler);
        }
        Disposer.register(this, (Disposable)() -> {
            BrowserPipe pipe = panel.getBrowserPipe();
            if (pipe != null) {
                pipe.removeSubscription(EVENT_NAME, handler);
            }
        });
    }

    @Override
    public @NotNull Priority getPriority() {
        return Priority.HIGH;
    }

    @Override
    public @NotNull List<String> getScripts() {
        return scripts;
    }

    @Override
    public @NotNull List<String> getStyles() {
        return List.of();
    }

    @Override
    public @Nullable ResourceProvider getResourceProvider() {
        return this;
    }

    @Override
    public boolean canProvide(@NotNull String resourceName) {
        return scripts.contains(resourceName);
    }

    @Override
    public @Nullable Resource loadResource(@NotNull String resourceName) {
        return ResourceProvider.loadInternalResource(BreezeJumpBrowserPreviewExtension.class, resourceName, null);
    }

    @Override
    public void dispose() {
    }
}