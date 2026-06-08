package com.imzhizi.breeze.devtools.settings;

import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.util.NlsContexts;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;

/**
 * 插件设置页面，位于：设置 → 工具 → Breeze Dev Helper
 */
public final class BreezeSettingsConfigurable implements Configurable {

    private JTextField specKeywordField;
    private JTextField breezeSchemeField;
    private JCheckBox  mcpEnabledBox;
    private JTextField mcpPortField;
    private JCheckBox  jumperEnabledBox;
    private JTextField jumperUserField;
    private JTextField jumperHostField;
    private JTextField jumperProxyScriptPathField;
    private JTextField jumperUserKeyPathField;
    private JTextField jumperSshKeyPathField;
    private JPanel     root;

    @Override
    public @NlsContexts.ConfigurableName String getDisplayName() {
        return "Breeze Dev Helper";
    }

    @Override
    public @Nullable JComponent createComponent() {
        root = new JPanel(new GridBagLayout());
        root.setBorder(new EmptyBorder(8, 8, 8, 8));

        GridBagConstraints label = new GridBagConstraints();
        label.anchor = GridBagConstraints.WEST;
        label.insets = new Insets(4, 0, 4, 8);
        label.gridx = 0;

        GridBagConstraints field = new GridBagConstraints();
        field.fill = GridBagConstraints.HORIZONTAL;
        field.weightx = 1.0;
        field.insets = new Insets(4, 0, 4, 0);
        field.gridx = 1;

        // Row 0 – 跳转到文档 标题
        GridBagConstraints title = new GridBagConstraints();
        title.gridx = 0; title.gridwidth = 2;
        title.anchor = GridBagConstraints.WEST;
        title.insets = new Insets(0, 0, 8, 0);
        title.gridy = 0;
        root.add(boldLabel("跳转到文档（代码注释 → Markdown 文档）"), title);

        // Row 1 – 关键词字段
        label.gridy = 1;
        root.add(new JLabel("注释关键词："), label);
        specKeywordField = new JTextField(20);
        specKeywordField.setToolTipText(
                "代码注释中用于触发跳转的关键词，默认：@spec");
        field.gridy = 1;
        root.add(specKeywordField, field);

        // Row 2 – hint
        GridBagConstraints hint = new GridBagConstraints();
        hint.gridx = 1; hint.gridy = 2; hint.anchor = GridBagConstraints.WEST;
        hint.insets = new Insets(0, 0, 12, 0);
        root.add(hintLabel("示例：@spec docs/specs/foo.md"), hint);

        // Row 3 – 跳转到代码 标题
        GridBagConstraints title2 = new GridBagConstraints();
        title2.gridx = 0; title2.gridwidth = 2;
        title2.anchor = GridBagConstraints.WEST;
        title2.insets = new Insets(0, 0, 8, 0);
        title2.gridy = 3;
        root.add(boldLabel("跳转到代码（Markdown 文档 → 源代码）"), title2);

        // Row 4 – URI scheme 字段
        label.gridy = 4;
        root.add(new JLabel("链接协议前缀："), label);
        breezeSchemeField = new JTextField(20);
        breezeSchemeField.setToolTipText(
                "Markdown 链接中使用的 URI 协议前缀，默认：spec-jump://");
        field.gridy = 4;
        root.add(breezeSchemeField, field);

        // Row 5 – hint
        GridBagConstraints hint2 = new GridBagConstraints();
        hint2.gridx = 1; hint2.gridy = 5; hint2.anchor = GridBagConstraints.WEST;
        hint2.insets = new Insets(0, 0, 12, 0);
        root.add(hintLabel("示例：spec-jump://com.example.MyClass#myMethod"), hint2);

        // Row 6 – MCP Server 标题
        GridBagConstraints title3 = new GridBagConstraints();
        title3.gridx = 0; title3.gridwidth = 2;
        title3.anchor = GridBagConstraints.WEST;
        title3.insets = new Insets(0, 0, 8, 0);
        title3.gridy = 6;
        root.add(boldLabel("MCP Server（AI 调试控制）"), title3);

        // Row 7 – 启用 MCP 开关
        label.gridy = 7;
        root.add(new JLabel("启用 MCP Server："), label);
        mcpEnabledBox = new JCheckBox();
        mcpEnabledBox.setToolTipText("启用后，插件在本地启动 MCP HTTP 服务，AI 可通过 MCP 协议控制断点和调试");
        field.gridy = 7;
        root.add(mcpEnabledBox, field);

        // Row 8 – 端口
        label.gridy = 8;
        root.add(new JLabel("监听端口："), label);
        mcpPortField = new JTextField(8);
        mcpPortField.setToolTipText("MCP Server 监听的本地端口，默认：19876");
        field.gridy = 8;
        root.add(mcpPortField, field);

        // Row 9 – hint
        GridBagConstraints hint3 = new GridBagConstraints();
        hint3.gridx = 1; hint3.gridy = 9; hint3.anchor = GridBagConstraints.WEST;
        hint3.insets = new Insets(0, 0, 12, 0);
        root.add(hintLabel("在 MCP 客户端中配置：http://localhost:<端口>/mcp"), hint3);

        // Row 10 – 跳板机 标题
        GridBagConstraints title4 = new GridBagConstraints();
        title4.gridx = 0; title4.gridwidth = 2;
        title4.anchor = GridBagConstraints.WEST;
        title4.insets = new Insets(0, 0, 8, 0);
        title4.gridy = 10;
        root.add(boldLabel("跳板机（Jumper SSH Tunnel）"), title4);

        // Row 11 – 自动跳板机开关
        label.gridy = 11;
        root.add(new JLabel("自动建立 SSH 隧道："), label);
        jumperEnabledBox = new JCheckBox();
        jumperEnabledBox.setToolTipText("启用后，创建 Remote Debug 配置时自动通过跳板机建立 SSH 本地端口转发");
        field.gridy = 11;
        root.add(jumperEnabledBox, field);

        // Row 12 – MIS ID
        label.gridy = 12;
        root.add(new JLabel("MIS ID："), label);
        jumperUserField = new JTextField(20);
        jumperUserField.setToolTipText("公司员工 ID（MIS 号），用于登录跳板机，如 zhangsan");
        field.gridy = 12;
        root.add(jumperUserField, field);

        // Row 13 – 跳板机地址
        label.gridy = 13;
        root.add(new JLabel("跳板机地址："), label);
        jumperHostField = new JTextField(20);
        field.gridy = 13;
        root.add(jumperHostField, field);

        // Row 14 – jumper_proxy.sh 路径
        label.gridy = 14;
        root.add(new JLabel("jumper_proxy.sh："), label);
        jumperProxyScriptPathField = new JTextField(40);
        field.gridy = 14;
        root.add(jumperProxyScriptPathField, field);

        // Row 15 – 用户 SSH 私钥路径
        label.gridy = 15;
        root.add(new JLabel("用户 SSH 私钥："), label);
        jumperUserKeyPathField = new JTextField(30);
        jumperUserKeyPathField.setToolTipText("用户个人密钥，留空则传 -");
        field.gridy = 15;
        root.add(jumperUserKeyPathField, field);

        // Row 16 – Moa SSH 私钥路径
        label.gridy = 16;
        root.add(new JLabel("Moa SSH 私钥："), label);
        jumperSshKeyPathField = new JTextField(30);
        field.gridy = 16;
        root.add(jumperSshKeyPathField, field);

        // vertical glue
        GridBagConstraints glue = new GridBagConstraints();
        glue.gridx = 0; glue.gridy = 17; glue.gridwidth = 2;
        glue.weighty = 1.0; glue.fill = GridBagConstraints.VERTICAL;
        root.add(Box.createVerticalGlue(), glue);

        reset();
        return root;
    }

    @Override
    public boolean isModified() {
        BreezeSettings s = BreezeSettings.getInstance();
        return !specKeywordField.getText().equals(s.specAnnotationKeyword)
                || !breezeSchemeField.getText().equals(s.breezeJumpScheme)
                || mcpEnabledBox.isSelected() != s.mcpEnabled
                || !mcpPortField.getText().equals(String.valueOf(s.mcpPort))
                || jumperEnabledBox.isSelected() != s.jumperEnabled
                || !jumperUserField.getText().equals(s.jumperUser)
                || !jumperHostField.getText().equals(s.jumperHost)
                || !jumperProxyScriptPathField.getText().equals(s.jumperProxyScriptPath)
                || !jumperUserKeyPathField.getText().equals(s.jumperUserKeyPath)
                || !jumperSshKeyPathField.getText().equals(s.jumperSshKeyPath);
    }

    @Override
    public void apply() {
        BreezeSettings s = BreezeSettings.getInstance();
        s.specAnnotationKeyword = specKeywordField.getText().trim();
        s.breezeJumpScheme      = breezeSchemeField.getText().trim();
        s.mcpEnabled            = mcpEnabledBox.isSelected();
        try {
            int port = Integer.parseInt(mcpPortField.getText().trim());
            if (port > 0 && port < 65536) s.mcpPort = port;
        } catch (NumberFormatException ignored) {
        }
        s.jumperEnabled          = jumperEnabledBox.isSelected();
        s.jumperUser             = jumperUserField.getText().trim();
        s.jumperHost             = jumperHostField.getText().trim();
        s.jumperProxyScriptPath  = jumperProxyScriptPathField.getText().trim();
        s.jumperUserKeyPath      = jumperUserKeyPathField.getText().trim();
        s.jumperSshKeyPath       = jumperSshKeyPathField.getText().trim();
    }

    @Override
    public void reset() {
        BreezeSettings s = BreezeSettings.getInstance();
        specKeywordField.setText(s.specAnnotationKeyword);
        breezeSchemeField.setText(s.breezeJumpScheme);
        mcpEnabledBox.setSelected(s.mcpEnabled);
        mcpPortField.setText(String.valueOf(s.mcpPort));
        jumperEnabledBox.setSelected(s.jumperEnabled);
        jumperUserField.setText(s.jumperUser);
        jumperHostField.setText(s.jumperHost);
        jumperProxyScriptPathField.setText(s.jumperProxyScriptPath);
        jumperUserKeyPathField.setText(s.jumperUserKeyPath);
        jumperSshKeyPathField.setText(s.jumperSshKeyPath);
    }

    // -----------------------------------------------------------------------

    private static JLabel boldLabel(String text) {
        JLabel l = new JLabel(text);
        l.setFont(l.getFont().deriveFont(Font.BOLD));
        return l;
    }

    private static JLabel hintLabel(String text) {
        JLabel l = new JLabel(text);
        l.setForeground(Color.GRAY);
        l.setFont(l.getFont().deriveFont(l.getFont().getSize() - 1f));
        return l;
    }
}
