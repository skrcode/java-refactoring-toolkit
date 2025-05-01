package com.github.skrcode.javarefactoringtoolkit;

import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.search.LocalSearchScope;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.PsiTreeUtil;

/**
 * Removes unused **private** members only in the class the user has selected
 * (or where the caret currently resides). Nothing outside that class will be
 * touched, guaranteeing strictly local, safe edits.
 */
public class CleanClassAction extends AnAction {

    @Override
    public void actionPerformed(AnActionEvent e) {
        Project project = e.getProject();
        if (project == null) return;

        PsiClass targetClass = findTargetClass(e);
        if (targetClass == null) return;

        WriteCommandAction.runWriteCommandAction(project, () -> cleanClass(project, targetClass));
    }

    /**
     * Returns the PsiClass that is either explicitly selected in the UI or that
     * encloses the caret position in the editor.
     */
    private PsiClass findTargetClass(AnActionEvent e) {
        // 1) A class selected in the Project/Structure view
        PsiElement element = e.getData(CommonDataKeys.PSI_ELEMENT);
        if (element instanceof PsiClass) {
            return (PsiClass) element;
        }

        // 2) The class at caret in an open editor
        Editor editor = e.getData(CommonDataKeys.EDITOR);
        PsiFile psiFile = e.getData(CommonDataKeys.PSI_FILE);
        if (editor != null && psiFile instanceof PsiJavaFile) {
            int offset = editor.getCaretModel().getOffset();
            PsiElement atCaret = psiFile.findElementAt(offset);
            return PsiTreeUtil.getParentOfType(atCaret, PsiClass.class, /* strict = */ false);
        }
        return null; // nothing sensible selected
    }

    private void cleanClass(Project project, PsiClass psiClass) {
        // --- Remove unused private methods ---
        for (PsiMethod method : psiClass.getMethods()) {
            if (method.hasModifierProperty(PsiModifier.PRIVATE) &&
                    ReferencesSearch.search(method, new LocalSearchScope(psiClass)).findFirst() == null) {
                method.delete();
            }
        }

        // --- Remove unused private fields ---
        for (PsiField field : psiClass.getFields()) {
            if (field.hasModifierProperty(PsiModifier.PRIVATE) &&
                    ReferencesSearch.search(field, new LocalSearchScope(psiClass)).findFirst() == null) {
                field.delete();
            }
        }

        // --- Remove unused private inner classes ---
        for (PsiClass innerClass : psiClass.getInnerClasses()) {
            if (innerClass.hasModifierProperty(PsiModifier.PRIVATE) &&
                    ReferencesSearch.search(innerClass, new LocalSearchScope(psiClass)).findFirst() == null) {
                innerClass.delete();
            }
        }

        // --- Remove unused local variables ---
        psiClass.accept(new JavaRecursiveElementVisitor() {
            @Override
            public void visitLocalVariable(PsiLocalVariable variable) {
                super.visitLocalVariable(variable);
                if (ReferencesSearch.search(variable, new LocalSearchScope(psiClass)).findFirst() == null) {
                    variable.delete();
                }
            }
        });

        // Tidy up imports and formatting
        JavaCodeStyleManager.getInstance(project).shortenClassReferences(psiClass);
        CodeStyleManager.getInstance(project).reformat(psiClass);
    }

    @Override
    public void update(AnActionEvent e) {
        // Enable only when a target class is resolvable.
        e.getPresentation().setEnabledAndVisible(findTargetClass(e) != null);
    }
}
