package com.github.skrcode.javaautounittests;

import com.intellij.openapi.options.Configurable;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

public class AISettingsConfigurable implements Configurable {

    private JTextField apiKeyField;
    private Component modelField;
    private JComboBox<String> modelCombo;
    private JPanel panel;

    @Override
    public @Nls(capitalization = Nls.Capitalization.Title) String getDisplayName() {
        return "JAIPilot - AI Unit Test Generator";
    }

    @Override
    public @Nullable JComponent createComponent() {
        panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));

        // API Key input field
        apiKeyField = new JTextField();
        apiKeyField.setAlignmentX(Component.LEFT_ALIGNMENT);
        apiKeyField.setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));

        // Model dropdown
        modelCombo = new JComboBox<>(new String[]{"gpt-3.5-turbo", "gpt-4", "gpt-4o"});
        modelCombo.setAlignmentX(Component.LEFT_ALIGNMENT);
        modelCombo.setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));

        // Add components
        panel.add(Box.createVerticalStrut(8));
        panel.add(new JLabel("OpenAI API Key:"));
        panel.add(Box.createVerticalStrut(4));
        panel.add(apiKeyField);
        panel.add(Box.createVerticalStrut(12));
        panel.add(new JLabel("Select Model:"));
        panel.add(Box.createVerticalStrut(4));
        panel.add(modelCombo);
        panel.add(Box.createVerticalGlue());

        return panel;
    }



    @Override
    public boolean isModified() {
        AISettings.State settings = AISettings.getInstance().getState();
        return !apiKeyField.getText().equals(settings.openAiKey)
                || !modelCombo.getSelectedItem().equals(settings.model);
    }

    @Override
    public void apply() {
        AISettings.getInstance().setOpenAiKey(apiKeyField.getText());
        AISettings.getInstance().setModel((String) modelCombo.getSelectedItem());
    }

    @Override
    public void reset() {
        AISettings.State settings = AISettings.getInstance().getState();
        apiKeyField.setText(settings.openAiKey);
        modelCombo.setSelectedItem(settings.model);
    }
}
