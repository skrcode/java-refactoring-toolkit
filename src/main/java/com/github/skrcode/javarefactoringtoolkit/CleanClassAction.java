package com.github.skrcode.javarefactoringtoolkit;

import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.search.FileTypeIndex;
import com.intellij.psi.search.LocalSearchScope;
import com.intellij.psi.search.ProjectScope;
import com.intellij.psi.search.searches.ReferencesSearch;

/**
 * Removes unused **private** members across **all** Java files in the project.
 * Only strictly private symbols inside their declaring class are considered,
 * keeping the change absolutely safe w.r.t. external callers.
 */
public class CleanClassAction extends AnAction {

    @Override
    public void actionPerformed(AnActionEvent e) {
        Project project = e.getProject();
        if (project == null) return;

        PsiManager psiManager = PsiManager.getInstance(project);

        WriteCommandAction.runWriteCommandAction(project, () -> {
            for (VirtualFile vFile : FileTypeIndex.getFiles(JavaFileType.INSTANCE, ProjectScope.getProjectScope(project))) {
                PsiFile psiFile = psiManager.findFile(vFile);
                if (!(psiFile instanceof PsiJavaFile)) continue;

                for (PsiClass psiClass : ((PsiJavaFile) psiFile).getClasses()) {
                    cleanClass(project, psiClass);
                }
            }
        });
    }

    private void cleanClass(Project project, PsiClass psiClass) {
        // Remove unused private methods
        for (PsiMethod method : psiClass.getMethods()) {
            if (method.hasModifierProperty(PsiModifier.PRIVATE) &&
                    ReferencesSearch.search(method, new LocalSearchScope(psiClass)).findFirst() == null) {
                method.delete();
            }
        }

        // Remove unused private fields
        for (PsiField field : psiClass.getFields()) {
            if (field.hasModifierProperty(PsiModifier.PRIVATE) &&
                    ReferencesSearch.search(field, new LocalSearchScope(psiClass)).findFirst() == null) {
                field.delete();
            }
        }

        // Remove unused private inner classes
        for (PsiClass innerClass : psiClass.getInnerClasses()) {
            if (innerClass.hasModifierProperty(PsiModifier.PRIVATE) &&
                    ReferencesSearch.search(innerClass, new LocalSearchScope(psiClass)).findFirst() == null) {
                innerClass.delete();
            }
        }

        // Remove unused local variables
        psiClass.accept(new JavaRecursiveElementVisitor() {
            @Override
            public void visitLocalVariable(PsiLocalVariable variable) {
                super.visitLocalVariable(variable);
                if (ReferencesSearch.search(variable, new LocalSearchScope(psiClass)).findFirst() == null) {
                    variable.delete();
                }
            }
        });

        // Reformat & shorten imports after mutation
        JavaCodeStyleManager.getInstance(project).shortenClassReferences(psiClass);
        CodeStyleManager.getInstance(project).reformat(psiClass);
    }

    @Override
    public void update(AnActionEvent e) {
        // Always visible; it operates on the whole project, not just the current editor file.
        e.getPresentation().setEnabledAndVisible(e.getProject() != null);
    }
}
