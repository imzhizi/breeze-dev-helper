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

    // ---------- MCP Server ----------
    /** 是否启用 MCP Server，默认：true */
    public boolean mcpEnabled = true;
    /** MCP Server 监听端口，默认：19876 */
    public int mcpPort = 19876;

    // ---------- 跳板机（Jumper）----------
    /** 是否自动通过跳板机建立 SSH 隧道，默认：true */
    public boolean jumperEnabled = true;
    /** 跳板机用户名（MIS 号），默认取系统登录名 */
    public String jumperUser = System.getProperty("user.name", "");
    /** 跳板机地址 */
    public String jumperHost = "jumper.sankuai.com";
    /** jumper_proxy.sh 脚本路径 */
    public String jumperProxyScriptPath = System.getProperty("user.home") +
            "/.sankuai/MCopilot/components/com.sankuai.idekit-tetris-components-jumper/classes/jumper_proxy.sh";
    /** 用户个人 SSH 私钥路径（无则留空，脚本传 -） */
    public String jumperUserKeyPath = System.getProperty("user.home") + "/.ssh/id_rsa_jumper";
    /** Moa 托管 SSH 私钥路径 */
    public String jumperSshKeyPath = System.getProperty("user.home") + "/.moa/ssh/id_rsa_jumper";

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
        mcpEnabled            = true;
        mcpPort               = 19876;
        jumperEnabled         = true;
        jumperUser            = System.getProperty("user.name", "");
        jumperHost            = "jumper.sankuai.com";
        jumperProxyScriptPath = System.getProperty("user.home") +
                "/.sankuai/MCopilot/components/com.sankuai.idekit-tetris-components-jumper/classes/jumper_proxy.sh";
        jumperUserKeyPath     = System.getProperty("user.home") + "/.ssh/id_rsa_jumper";
        jumperSshKeyPath      = System.getProperty("user.home") + "/.moa/ssh/id_rsa_jumper";
    }
}
