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
        hint2.insets = new Insets(0, 0, 0, 0);
        root.add(hintLabel("示例：spec-jump://com.example.MyClass#myMethod"), hint2);

        // vertical glue
        GridBagConstraints glue = new GridBagConstraints();
        glue.gridx = 0; glue.gridy = 6; glue.gridwidth = 2;
        glue.weighty = 1.0; glue.fill = GridBagConstraints.VERTICAL;
        root.add(Box.createVerticalGlue(), glue);

        reset();
        return root;
    }

    @Override
    public boolean isModified() {
        BreezeSettings s = BreezeSettings.getInstance();
        return !specKeywordField.getText().equals(s.specAnnotationKeyword)
                || !breezeSchemeField.getText().equals(s.breezeJumpScheme);
    }

    @Override
    public void apply() {
        BreezeSettings s = BreezeSettings.getInstance();
        s.specAnnotationKeyword = specKeywordField.getText().trim();
        s.breezeJumpScheme      = breezeSchemeField.getText().trim();
    }

    @Override
    public void reset() {
        BreezeSettings s = BreezeSettings.getInstance();
        specKeywordField.setText(s.specAnnotationKeyword);
        breezeSchemeField.setText(s.breezeJumpScheme);
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
