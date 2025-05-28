package com.github.skrcode.javaautounittests;

import com.intellij.execution.Executor;
import com.intellij.execution.RunManager;
import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.execution.executors.DefaultRunExecutor;
import com.intellij.execution.junit.JUnitConfiguration;
import com.intellij.execution.junit.JUnitConfigurationType;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiJavaFile;
import org.jetbrains.annotations.NotNull;
import com.openai.client.OpenAIClient;
import com.openai.client.okhttp.OpenAIOkHttpClient;
import com.openai.models.ChatModel;
import com.openai.models.responses.Response;
import com.openai.models.responses.ResponseCreateParams;

import static com.github.skrcode.javaautounittests.PromptBuilder.buildPrompt;

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
        System.out.println(buildPrompt(project,testClass));
//        invokeAI();
//        runJUnitTestForClass(project, testClass);
    }

    private void invokeAI() {
        OpenAIClient client = OpenAIOkHttpClient.builder().apiKey(AISettings.getInstance().getOpenAiKey()).build();

        ResponseCreateParams params = ResponseCreateParams.builder()
                .input("Say this is a test")
                .model(ChatModel.GPT_4_1_NANO)
                .build();
        Response response = client.responses().create(params);
    }

    private void runJUnitTestForClass(Project project, PsiClass psiClass) {
        JUnitConfigurationType type = JUnitConfigurationType.getInstance();
        RunManager runManager = RunManager.getInstance(project);

        RunnerAndConfigurationSettings settings = runManager.createConfiguration(
                psiClass.getName(), type.getConfigurationFactories()[0]);

        JUnitConfiguration configuration = (JUnitConfiguration) settings.getConfiguration();
        configuration.setModule(configuration.getConfigurationModule().getModule());
        configuration.setMainClass(psiClass);

        runManager.addConfiguration(settings);
        runManager.setSelectedConfiguration(settings);
        Executor executor = DefaultRunExecutor.getRunExecutorInstance();




//        ProgramRunnerUtil.executeConfiguration(settings, executor);
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
