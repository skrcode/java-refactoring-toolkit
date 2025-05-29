package com.github.skrcode.javaautounittests;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

public class FileWriterUtil {

    public static void writeToFile(Project project, String absolutePath, String fileName, String content) {
        File directory = new File(absolutePath);
        if (!directory.exists()) {
            directory.mkdirs(); // Create directory if it doesn't exist
        }

        VirtualFile virtualDir = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(directory);
        if (virtualDir == null) {
            throw new RuntimeException("Failed to find or create directory: " + absolutePath);
        }

        ApplicationManager.getApplication().invokeLater(() ->
                WriteCommandAction.runWriteCommandAction(project, () -> {
                    try {
                        VirtualFile existingFile = virtualDir.findChild(fileName);
                        if (existingFile != null) {
                            existingFile.setBinaryContent(content.getBytes(StandardCharsets.UTF_8));
                        } else {
                            VirtualFile newFile = virtualDir.createChildData(null, fileName+".java");
                            VfsUtil.saveText(newFile, content);
                        }
                        virtualDir.refresh(false, true);
                    } catch (IOException e) {
                        throw new RuntimeException("Failed to write file: " + fileName, e);
                    }
                })
        );
    }
}
