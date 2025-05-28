package com.github.skrcode.javaautounittests;

import com.intellij.execution.ProgramRunnerUtil;
import com.intellij.execution.RunManager;
import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.execution.configurations.ConfigurationType;
import com.intellij.execution.configurations.ConfigurationTypeUtil;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiJavaFile;
import org.jetbrains.annotations.NotNull;

public class GenerateTestAction extends AnAction {

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        Project project = e.getProject();
        if (project == null) return;

        PsiFile psiFile = e.getData(CommonDataKeys.PSI_FILE);
        if (!(psiFile instanceof PsiJavaFile)) {
            Messages.showErrorDialog(project, "Please select a Java class file.", "JAIPilot");
            return;
        }

        PsiClass[] classes = ((PsiJavaFile) psiFile).getClasses();
        if (classes.length == 0) {
            Messages.showErrorDialog(project, "No class found in selected file.", "JAIPilot");
            return;
        }

        PsiClass testClass = classes[0]; // assume first class is the test class
        runJUnitTestForClass(project, testClass);
    }

    private void runJUnitTestForClass(Project project, PsiClass psiClass) {
        ConfigurationType type = ConfigurationTypeUtil.findConfigurationType("JUnit");

        if (!(type.getConfigurationFactories()[0].createTemplateConfiguration(project) instanceof JUnitConfiguration)) {
            Messages.showErrorDialog(project, "JUnit run configuration not found or not supported.", "JAIPilot");
            return;
        }

        RunManager runManager = RunManager.getInstance(project);

        RunnerAndConfigurationSettings settings = runManager.createConfiguration(
                psiClass.getName(), type.getConfigurationFactories()[0]);

        JUnitConf configuration = (JUnitConfiguration) settings.getConfiguration();

        Module module = configuration.getConfigurationModule().getModule();
        if (module == null) {
            module = JUnitUtil.getModuleForPsiElement(psiClass); // Fallback module detection
            if (module != null) {
                configuration.setModule(module);
            } else {
                Messages.showErrorDialog(project, "Could not resolve module for test class.", "JAIPilot");
                return;
            }
        }

        configuration.setMainClass(psiClass);

        runManager.addConfiguration(settings);
        runManager.setSelectedConfiguration(settings);
        ProgramRunnerUtil.executeConfiguration(settings, false, true);
    }



}
//
//private void notifyResult(Project project, int linesDeleted) {
//    String title = "JAIPilot - AI Unit Test Generator<";
//    String msg = "";
//
//    NotificationGroup group = NotificationGroupManager.getInstance()
//            .getNotificationGroup("JAIPilot - AI Unit Test Generator Feedback");
//
//    Notification n = group.createNotification(title, msg, NotificationType.INFORMATION);
//
//    if (linesDeleted > 0) {
//        n.addAction(NotificationAction.createSimple("Rate in Marketplace",
//                () -> BrowserUtil.browse("https://plugins.jetbrains.com/intellij/com.github.skrcode.javaautounittests/review/new")));
//        n.addAction(NotificationAction.createSimpleExpiring("Later", () -> {}));
//    }
//    n.notify(project);
//}
