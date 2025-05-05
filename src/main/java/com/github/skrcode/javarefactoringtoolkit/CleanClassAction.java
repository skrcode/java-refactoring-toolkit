package com.github.skrcode.javarefactoringtoolkit;

import com.intellij.codeInspection.*;
import com.intellij.codeInspection.dataFlow.UnreachableCodeInspection;
import com.intellij.ide.BrowserUtil;
import com.intellij.notification.*;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.LocalSearchScope;
import com.intellij.psi.search.searches.ClassInheritorsSearch;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.PsiTreeUtil;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.intellij.openapi.actionSystem.PlatformCoreDataKeys.PSI_ELEMENT_ARRAY;

/**
 * Safely cleans Java code <em>only</em> in classes selected by the user (or the
 * class under the caret when nothing is selected). The action stops at the
 * first compilation unit boundary, so the rest of the code‑base is untouched.
 * <p>
 * Each class is cleaned repeatedly until no further edits are possible. A final
 * IDE notification summarises how many <strong>lines</strong> were deleted.
 */
public class CleanClassAction extends AnAction {

    // ────────────────────────────────────────────────────────────────────────
    // UI entry points
    // ────────────────────────────────────────────────────────────────────────

    @Override
    public void actionPerformed(AnActionEvent e) {
        Project project = e.getProject();
        if (project == null) return;

        List<PsiClass> targets = collectTargetClasses(e, project);
        if (targets.isEmpty()) return;

        WriteCommandAction.runWriteCommandAction(project, () -> {
            int totalDeleted = 0;
            for (PsiClass cls : targets) totalDeleted += cleanRecursively(cls);
            notifyResult(project, totalDeleted);
        });

    }

    /** Lightweight enable/disable logic; safe to run in a background thread. */
    @Override
    public ActionUpdateThread getActionUpdateThread() {
        return ActionUpdateThread.BGT;   // or EDT if you need UI/PSI read locks on EDT
    }

    @Override
    public void update(AnActionEvent e) {
        // Enable the action only on Java files
        PsiFile file = e.getData(CommonDataKeys.PSI_FILE);
        e.getPresentation().setEnabledAndVisible(file instanceof PsiJavaFile);
    }

    private void notifyResult(Project project, int linesDeleted) {
        String title = "Safe Cleaner";
        String msg;
        if (linesDeleted > 0) {
            msg = "<html>Cleanup complete — <b>removed " + linesDeleted + " lines</b>!<br>If the plugin helped you, please ⭐️ rate it.</html>";
        } else {
            msg = "Cleanup complete — nothing to clean.";
        }

        NotificationGroup group = NotificationGroupManager.getInstance()
                .getNotificationGroup("Safe Dead Code Cleaner Feedback");

        Notification n = group.createNotification(title, msg, NotificationType.INFORMATION);

        if (linesDeleted > 0) {
            n.addAction(NotificationAction.createSimple("Rate in Marketplace",
                    () -> BrowserUtil.browse("https://plugins.jetbrains.com/intellij/com.github.skrcode.javarefactoringtoolkit/review/new")));
            n.addAction(NotificationAction.createSimpleExpiring("Later", () -> {}));
        }
        n.notify(project);
    }

    // ────────────────────────────────────────────────────────────────────────
    // Recursive pass driver
    // ────────────────────────────────────────────────────────────────────────

    private int cleanRecursively(PsiClass cls) {
        int totalDeleted = 0;
        int deleted;
        do {
            deleted  = 0;
            deleted += cleanUnusedMembers(cls);
            deleted += cleanUnusedStatic(cls);
            totalDeleted += deleted;
        } while (deleted > 0);   // fix‑point loop
        tidyUp(cls);
        return totalDeleted;
    }

    /**
     * Deletes all truly‑unreferenced static fields & methods inside {@code cls}.
     *
     * @param cls the class to clean
     * @return the total number of source‑lines deleted
     */
    private int cleanUnusedStatic(PsiClass cls) {
        int linesDeleted = 0;
        Project project = cls.getProject();
        GlobalSearchScope scope = GlobalSearchScope.projectScope(project);

        /* --- static methods -------------------------------------------------- */
        for (PsiMethod m : cls.getMethods()) {
            if (m.hasModifierProperty(PsiModifier.STATIC)
                    && isSafeToDelete(m, scope)) {
                linesDeleted += countLines(m);
                m.delete();
            }
        }

        /* --- static fields --------------------------------------------------- */
        for (PsiField f : cls.getFields()) {
            if (f.hasModifierProperty(PsiModifier.STATIC)
                    && isSafeToDelete(f, scope)) {
                linesDeleted += countLines(f);
                f.delete();
            }
        }
        return linesDeleted;
    }

    /* ---------- helpers ------------------------------------------------------- */

    private boolean isSafeToDelete(PsiModifierListOwner member, GlobalSearchScope scope) {
        // skip anything annotated (possible framework/reflective use)
        PsiModifierList ml = member.getModifierList();
        if (ml != null && ml.getAnnotations().length > 0) return false;

        // absolutely no references anywhere in the project
        return ReferencesSearch.search(member, scope).findFirst() == null;
    }

    // ────────────────────────────────────────────────────────────────────────
    // Target discovery helpers
    // ────────────────────────────────────────────────────────────────────────

    private List<PsiClass> collectTargetClasses(AnActionEvent e, Project project) {
        Set<PsiClass> out = new LinkedHashSet<>();

        PsiElement[] selection = e.getData(PSI_ELEMENT_ARRAY);
        if (selection != null && selection.length > 0) {
            for (PsiElement el : selection) addFromElement(el, project, out);
        } else {
            Editor editor = e.getData(CommonDataKeys.EDITOR);
            PsiFile  file  = e.getData(CommonDataKeys.PSI_FILE);
            if (editor != null && file instanceof PsiJavaFile) {
                PsiElement element = file.findElementAt(editor.getCaretModel().getOffset());
                PsiClass cls = PsiTreeUtil.getParentOfType(element, PsiClass.class, false);
                if (cls != null) out.add(cls);
            }
        }
        return new ArrayList<>(out);
    }

    private void addFromElement(PsiElement el, Project project, Set<PsiClass> out) {
        if (el instanceof PsiClass) {
            out.add((PsiClass) el);
        } else if (el instanceof PsiJavaFile) {
            Collections.addAll(out, ((PsiJavaFile) el).getClasses());
        } else if (el instanceof PsiDirectory) {
            addFromDirectory((PsiDirectory) el, out);
        } else if (el instanceof PsiPackage) {
            for (PsiDirectory dir : ((PsiPackage) el).getDirectories()) addFromDirectory(dir, out);
        } else if (el instanceof Module) {
            Module m = (Module) el;
            for (VirtualFile root : ModuleRootManager.getInstance(m).getSourceRoots()) {
                PsiDirectory dir = PsiManager.getInstance(project).findDirectory(root);
                if (dir != null) addFromDirectory(dir, out);
            }
        }
    }

    private void addFromDirectory(PsiDirectory dir, Set<PsiClass> out) {
        for (PsiFile f : dir.getFiles()) if (f instanceof PsiJavaFile) Collections.addAll(out, ((PsiJavaFile) f).getClasses());
        for (PsiDirectory sub : dir.getSubdirectories()) addFromDirectory(sub, out);
    }

    // ────────────────────────────────────────────────────────────────────────
    // Individual passes — each returns number of lines deleted
    // ────────────────────────────────────────────────────────────────────────

    private int cleanUnusedMembers(PsiClass cls) {
        int deletedLines = 0;
        LocalSearchScope scope = new LocalSearchScope(cls);

        // private methods
        for (PsiMethod m : cls.getMethods())
            if (m.hasModifierProperty(PsiModifier.PRIVATE) && ReferencesSearch.search(m, scope).findFirst() == null) {
                deletedLines += countLines(m);
                m.delete();
            }
        // private inner classes
        for (PsiClass inner : cls.getInnerClasses())
            if (inner.hasModifierProperty(PsiModifier.PRIVATE) && ReferencesSearch.search(inner, scope).findFirst() == null) {
                deletedLines += countLines(inner);
                inner.delete();
            }
        // local vars
        List<PsiLocalVariable> locals = new ArrayList<>();
        cls.accept(new JavaRecursiveElementVisitor() {
            @Override public void visitLocalVariable(PsiLocalVariable v) {
                super.visitLocalVariable(v); locals.add(v); }
        });
        for (PsiLocalVariable v : locals)
            if (ReferencesSearch.search(v, scope).findFirst() == null) {
                deletedLines += countLines(v);
                v.delete();
            }

        return deletedLines;
    }

    /**
     * Removes all references to {@code target} inside {@code scope} and returns
     * the number of lines deleted (including the target itself).
     */
    private int removeUsagesAndDelete(PsiElement target, LocalSearchScope scope) {
        int deletedLines = 0;
        for (PsiReference ref : ReferencesSearch.search(target, scope).findAll()) {
            PsiElement el = ref.getElement();

            // 1)  "new X()" → delete whole expression statement
            PsiNewExpression newExpr = PsiTreeUtil.getParentOfType(el, PsiNewExpression.class, false);
            if (newExpr != null && newExpr.getParent() instanceof PsiExpressionStatement) {
                deletedLines += countLines(newExpr.getParent());
                newExpr.getParent().delete();
                continue;
            }

            // 2)  Variable / field declarations of the target type
            PsiVariable var = PsiTreeUtil.getParentOfType(el, PsiVariable.class, false);
            if (var != null) {
                if (var instanceof PsiField) {
                    // delete private fields only (to stay on the safe side)
                    if (((PsiField) var).hasModifierProperty(PsiModifier.PRIVATE)) {
                        deletedLines += countLines(var);
                        var.delete();
                    }
                } else if (var instanceof PsiLocalVariable) {
                    PsiDeclarationStatement decl = (PsiDeclarationStatement) var.getParent();
                    if (decl.getDeclaredElements().length == 1) {
                        deletedLines += countLines(decl); decl.delete();
                    } else {
                        deletedLines += countLines(var); var.delete();
                    }
                }
                continue;
            }
        }
        deletedLines += countLines(target);
        target.delete();
        return deletedLines;
    }

    private int deleteUnreachableCode(PsiClass cls) {
        Project  project    = cls.getProject();
        PsiFile  file       = cls.getContainingFile();
        InspectionManager im = InspectionManager.getInstance(project);

        LocalInspectionTool tool = new UnreachableCodeInspection();
        List<ProblemDescriptor> problems = tool.processFile(file, im);
        if (problems.isEmpty()) return 0;

        AtomicBoolean changed = new AtomicBoolean(false);
        AtomicBoolean countLines = new AtomicBoolean(false);
        int[] deleted = {0};

        WriteCommandAction.runWriteCommandAction(project, () -> {
            for (ProblemDescriptor pd : problems) {
                PsiElement problemElement = pd.getPsiElement();
                if (!PsiTreeUtil.isAncestor(cls, problemElement, false)) continue;

                for (QuickFix<?> fix : pd.getFixes()) {
                    if (fix instanceof LocalQuickFix localFix) {
                        deleted[0] += countLines(problemElement);
                        localFix.applyFix(project, pd);
                        changed.set(true);
                    }
                }
            }
        });
        return deleted[0];
    }

    private int mergeSingleImplInterfaces(PsiClass cls) {
        int deletedLines = 0;
        LocalSearchScope scope = new LocalSearchScope(cls);
        PsiElementFactory factory = JavaPsiFacade.getElementFactory(cls.getProject());

        for (PsiClass ifc : cls.getInnerClasses()) {
            if (!ifc.isInterface()) continue;
            Collection<PsiClass> impls = ClassInheritorsSearch.search(ifc, scope, false).findAll();
            if (impls.size() != 1) continue;
            PsiClass impl = impls.iterator().next();
            for (PsiReference ref : ReferencesSearch.search(ifc, scope)) {
                PsiElement el = ref.getElement();
                if (el instanceof PsiJavaCodeReferenceElement) el.replace(factory.createClassReferenceElement(impl));
            }
            deletedLines += countLines(ifc);
            ifc.delete();
        }
        return deletedLines;
    }

    // ────────────────────────────────────────────────────────────────────────
    // Tidy‑up (imports, references, formatting)
    // ────────────────────────────────────────────────────────────────────────

    private void tidyUp(PsiClass cls) {
        Project project = cls.getProject();
        JavaCodeStyleManager style = JavaCodeStyleManager.getInstance(project);
        style.optimizeImports(cls.getContainingFile());
    }

    // ────────────────────────────────────────────────────────────────────────
    // Helper
    // ────────────────────────────────────────────────────────────────────────

    private int countLines(PsiElement element) {
        return StringUtil.countNewLines(element.getText()) + 1;
    }
}
