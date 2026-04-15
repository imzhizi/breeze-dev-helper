package com.imzhizi.breeze.devtools.settings;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.util.xmlb.XmlSerializerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Application-level persistent settings for Breeze Dev Helper.
 * Stored in: ~/Library/Application Support/JetBrains/<product>/options/BreezeDevHelper.xml
 */
@State(
        name = "BreezeDevHelperSettings",
        storages = @Storage("BreezeDevHelper.xml")
)
public final class BreezeSettings implements PersistentStateComponent<BreezeSettings> {

    // ---------- 跳转到文档（Code → Markdown）----------
    /** 触发跳转到文档的注释关键词，默认：@spec */
    public String specAnnotationKeyword = "@spec";

    // ---------- 跳转到代码（Markdown → Code）----------
    /** Markdown 链接中使用的 URI scheme 前缀，默认：spec-jump:// */
    public String breezeJumpScheme = "spec-jump://";

    // -----------------------------------------------------------------------

    public static BreezeSettings getInstance() {
        return ApplicationManager.getApplication().getService(BreezeSettings.class);
    }

    @Override
    public @Nullable BreezeSettings getState() {
        return this;
    }

    @Override
    public void loadState(@NotNull BreezeSettings state) {
        XmlSerializerUtil.copyBean(state, this);
    }

    /** 恢复默认值 */
    public void reset() {
        specAnnotationKeyword = "@spec";
        breezeJumpScheme      = "spec-jump://";
    }
}
