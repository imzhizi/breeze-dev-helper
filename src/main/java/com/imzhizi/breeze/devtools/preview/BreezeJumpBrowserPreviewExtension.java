package com.imzhizi.breeze.devtools.preview;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.util.Disposer;
import com.imzhizi.breeze.devtools.uri.BreezeJumpUriParser;
import org.intellij.plugins.markdown.extensions.MarkdownBrowserPreviewExtension;
import org.intellij.plugins.markdown.ui.preview.BrowserPipe;
import org.intellij.plugins.markdown.ui.preview.MarkdownHtmlPanel;
import org.intellij.plugins.markdown.ui.preview.ResourceProvider;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.charset.StandardCharsets;
import java.util.List;

public final class BreezeJumpBrowserPreviewExtension implements MarkdownBrowserPreviewExtension, ResourceProvider {
    private static final String EVENT_NAME    = "breezeJumpOpenLink";
    private static final String SCRIPT_NAME   = "breeze-jump/breezeJumpPreview.js";
    /** Virtual resource name for the dynamically generated scheme-injection script. */
    private static final String INIT_SCRIPT   = "breeze-jump/breezeJumpInit.js";

    private final MarkdownHtmlPanel panel;
    private final BrowserPipe.Handler handler;
    private final List<String> scripts;

    public BreezeJumpBrowserPreviewExtension(MarkdownHtmlPanel panel) {
        this.panel = panel;
        this.handler = message -> BreezeJumpPreviewHandler.open(panel.getProject(), message);
        // INIT_SCRIPT must come first so __breezeJumpScheme is defined before breezeJumpPreview.js runs
        this.scripts = List.of(INIT_SCRIPT, SCRIPT_NAME);

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
        if (INIT_SCRIPT.equals(resourceName)) {
            // Dynamically inject the current scheme into window.__breezeJumpScheme
            String scheme = BreezeJumpUriParser.getSchemePrefix()
                    .replace("\\", "\\\\").replace("'", "\\'");
            String js = "window.__breezeJumpScheme = '" + scheme + "';";
            byte[] bytes = js.getBytes(StandardCharsets.UTF_8);
            return new Resource(bytes, "application/javascript");
        }
        return ResourceProvider.loadInternalResource(BreezeJumpBrowserPreviewExtension.class, resourceName, null);
    }

    @Override
    public void dispose() {
    }
}