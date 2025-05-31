package com.github.skrcode.javaautounittests;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.options.ShowSettingsUtil;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

public class ModelDisplayAction extends AnAction {
    public ModelDisplayAction() {
        super("Model: " + AISettings.getInstance().getModel());
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
        e.getPresentation().setEnabled(false);
        e.getPresentation().setVisible(true);
        e.getPresentation().setText("Model: " + AISettings.getInstance().getModel());
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        // Do nothing
    }
}
