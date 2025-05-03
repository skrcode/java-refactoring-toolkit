package com.github.skrcode.javarefactoringtoolkit;

import com.intellij.notification.Notification;
import com.intellij.notification.NotificationType;
import com.intellij.notification.Notifications;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.search.LocalSearchScope;
import com.intellij.psi.search.searches.ClassInheritorsSearch;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.PsiTreeUtil;

import java.util.*;

import static com.intellij.openapi.actionSystem.PlatformCoreDataKeys.PSI_ELEMENT_ARRAY;

/**
 * Safely cleans Java code <em>only</em> in classes selected by the user (or the
 * class under the caret when nothing is selected). The action stops at the
 * first compilation unit boundary, so the rest of the code‑base is untouched.
 * <p>
 * Each class is cleaned repeatedly until no further edits are possible. A final
 * IDE notification summarises whether anything changed.
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
            boolean anythingChanged = false;
            for (PsiClass cls : targets) anythingChanged |= cleanRecursively(cls);
            notifyResult(project, anythingChanged);
        });
    }

    @Override
    public void update(AnActionEvent e) {
        Project project = e.getProject();
        e.getPresentation().setEnabledAndVisible(project != null && !collectTargetClasses(e, project).isEmpty());
    }

    private void notifyResult(Project project, boolean changed) {
        String title = "Safe Cleaner";
        String msg   = changed ? "Cleanup complete — changes applied." : "Cleanup complete — nothing to clean.";
        Notifications.Bus.notify(new Notification(title, title, msg, NotificationType.INFORMATION), project);
    }

    // ────────────────────────────────────────────────────────────────────────
    // Recursive pass driver
    // ────────────────────────────────────────────────────────────────────────

    private boolean cleanRecursively(PsiClass cls) {
        boolean anyChange = false;
        boolean changed;
        do {
            changed  = false;
            changed |= cleanUnusedMembers(cls);
//            changed |= deleteEmptyPrivateMembers(cls);
//            changed |= deleteUnreachableCode(cls);
//            changed |= mergeSingleImplInterfaces(cls);
            anyChange |= changed;
        } while (changed);   // fix‑point loop
//        tidyUp(cls);
        return anyChange;
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
    // Individual passes — each returns whether it made a change
    // ────────────────────────────────────────────────────────────────────────

    private boolean cleanUnusedMembers(PsiClass cls) {
        boolean changed = false;
        LocalSearchScope scope = new LocalSearchScope(cls);

        // private methods
        for (PsiMethod m : cls.getMethods())
            if (m.hasModifierProperty(PsiModifier.PRIVATE) && ReferencesSearch.search(m, scope).findFirst() == null) {
                m.delete(); changed = true;
            }
        // private fields
//        for (PsiField f : cls.getFields())
//            if (f.hasModifierProperty(PsiModifier.PRIVATE) && ReferencesSearch.search(f, scope).findFirst() == null) {
//                f.delete(); changed = true;
//            }
        // private inner classes
        for (PsiClass inner : cls.getInnerClasses())
            if (inner.hasModifierProperty(PsiModifier.PRIVATE) && ReferencesSearch.search(inner, scope).findFirst() == null) {
                inner.delete(); changed = true;
            }
        // local vars
        List<PsiLocalVariable> locals = new ArrayList<>();
        cls.accept(new JavaRecursiveElementVisitor() {
            @Override public void visitLocalVariable(PsiLocalVariable v) {
                super.visitLocalVariable(v); locals.add(v); }
        });
        for (PsiLocalVariable v : locals)
            if (ReferencesSearch.search(v, scope).findFirst() == null) { v.delete(); changed = true; }

        return changed;
    }

    private boolean deleteEmptyPrivateMembers(PsiClass cls) {
        boolean changed = false;
        LocalSearchScope scope = new LocalSearchScope(cls);

        // empty private methods
        for (PsiMethod m : cls.getMethods()) {
            if (!m.hasModifierProperty(PsiModifier.PRIVATE)) continue;
            PsiCodeBlock body = m.getBody();
            if (body == null || body.getStatements().length > 0) continue;
            changed |= removeUsagesAndDelete(m, scope);
        }
        // empty private inner classes / enums
        for (PsiClass inner : cls.getInnerClasses()) {
            if (!inner.hasModifierProperty(PsiModifier.PRIVATE)) continue;
            if (inner.getFields().length > 0 || inner.getMethods().length > 0 || inner.getInnerClasses().length > 0) continue;
            changed |= removeUsagesAndDelete(inner, scope);
        }
        return changed;
    }

    /**
     * Removes all references to {@code target} inside {@code scope}. For
     * <code>new</code> expressions it deletes the surrounding statement; for
     * variable/field declarations it drops the declaration; finally the target
     * itself is deleted.
     */
    private boolean removeUsagesAndDelete(PsiElement target, LocalSearchScope scope) {
        boolean changed = false;
        for (PsiReference ref : ReferencesSearch.search(target, scope).findAll()) {
            PsiElement el = ref.getElement();

            // 1)  "new X()" → delete whole expression statement
            PsiNewExpression newExpr = PsiTreeUtil.getParentOfType(el, PsiNewExpression.class, false);
            if (newExpr != null && newExpr.getParent() instanceof PsiExpressionStatement) {
                newExpr.getParent().delete();
                changed = true;
                continue;
            }

            // 2)  Variable / field declarations of the target type
            PsiVariable var = PsiTreeUtil.getParentOfType(el, PsiVariable.class, false);
            if (var != null) {
                if (var instanceof PsiField) {
                    // delete private fields only (to stay on the safe side)
                    if (((PsiField) var).hasModifierProperty(PsiModifier.PRIVATE)) {
                        var.delete();
                        changed = true;
                    }
                } else if (var instanceof PsiLocalVariable) {
                    PsiDeclarationStatement decl = (PsiDeclarationStatement) var.getParent();
                    // if declaration has single element -> remove whole stmt, else just the var
                    if (decl.getDeclaredElements().length == 1) decl.delete(); else var.delete();
                    changed = true;
                }
                continue;
            }
        }
        target.delete();
        return true | changed; // deletion always implies change
    }

    private boolean deleteUnreachableCode(PsiClass cls) {
        final boolean[] changed = {false};
        cls.accept(new JavaRecursiveElementVisitor() {
            @Override public void visitCodeBlock(PsiCodeBlock block) {
                boolean unreachable = false;
                for (PsiStatement s : block.getStatements()) {
                    if (unreachable) { s.delete(); changed[0] = true; continue; }
                    if (s instanceof PsiReturnStatement || s instanceof PsiThrowStatement ||
                            s instanceof PsiBreakStatement  || s instanceof PsiContinueStatement) unreachable = true;
                }
                super.visitCodeBlock(block);
            }
        });
        return changed[0];
    }

    private boolean mergeSingleImplInterfaces(PsiClass cls) {
        boolean changed = false;
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
            ifc.delete();
            changed = true;
        }
        return changed;
    }

    // ────────────────────────────────────────────────────────────────────────
    // Tidy‑up (imports, references, formatting)
    // ────────────────────────────────────────────────────────────────────────

    private void tidyUp(PsiClass cls) {
        Project project = cls.getProject();
        JavaCodeStyleManager style = JavaCodeStyleManager.getInstance(project);
        style.shortenClassReferences(cls);
        style.optimizeImports(cls.getContainingFile());
        CodeStyleManager.getInstance(project).reformat(cls);
    }
}
