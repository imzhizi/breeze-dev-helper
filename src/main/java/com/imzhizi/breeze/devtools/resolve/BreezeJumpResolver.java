package com.imzhizi.breeze.devtools.resolve;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.search.FilenameIndex;
import com.intellij.psi.search.GlobalSearchScope;
import com.imzhizi.breeze.devtools.navigation.BreezeJumpResolvedTarget;
import com.imzhizi.breeze.devtools.uri.BreezeJumpTarget;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class BreezeJumpResolver {
    private static final Pattern PACKAGE_PATTERN = Pattern.compile("(?m)^\\s*package\\s+([\\w.]+)");
    private static final String TYPE_TEMPLATE = "(?m)^\\s*(?:final\\s+|abstract\\s+|sealed\\s+|case\\s+)*?(class|object|trait|enum)\\s+%s\\b";
    private static final String SCALA_METHOD_TEMPLATE = "(?m)^\\s*(?:(?:private|protected|final|sealed|abstract|implicit|lazy|override|inline|transparent)\\s+)*def\\s+%s\\b";
    private static final String SCALA_FIELD_TEMPLATE = "(?m)^\\s*(?:(?:private|protected|final|sealed|abstract|implicit|lazy|override|inline|transparent)\\s+)*(?:val|var|case\\s+object|case)\\s+%s\\b";

    private BreezeJumpResolver() {
    }

    public static @Nullable BreezeJumpResolvedTarget resolve(Project project, BreezeJumpTarget target) {
        BreezeJumpResolvedTarget resolvedFromJvmPsi = resolveFromJvmPsi(project, target);
        if (resolvedFromJvmPsi != null) {
            return resolvedFromJvmPsi;
        }
        return resolveFromScalaSource(project, target);
    }

    private static @Nullable BreezeJumpResolvedTarget resolveFromJvmPsi(Project project, BreezeJumpTarget target) {
        PsiClass[] classes = JavaPsiFacade.getInstance(project)
                .findClasses(target.getClassFqn(), GlobalSearchScope.projectScope(project));
        for (PsiClass psiClass : classes) {
            BreezeJumpResolvedTarget resolvedTarget = buildResolvedTarget(psiClass, target);
            if (resolvedTarget != null) {
                return resolvedTarget;
            }
        }
        return null;
    }

    private static @Nullable BreezeJumpResolvedTarget buildResolvedTarget(PsiClass psiClass, BreezeJumpTarget target) {
        PsiElement baseElement = resolveMember(psiClass, target);
        if (baseElement == null) {
            return null;
        }

        PsiFile containingFile = baseElement.getContainingFile();
        if (containingFile == null || containingFile.getVirtualFile() == null) {
            return null;
        }

        VirtualFile file = containingFile.getVirtualFile();
        int offset = resolveOffset(file, target.getLineNumber(), baseElement.getTextOffset());
        return new BreezeJumpResolvedTarget(file, offset, baseElement);
    }

    private static @Nullable PsiElement resolveMember(PsiClass psiClass, BreezeJumpTarget target) {
        String memberName = target.getNormalizedMemberName();
        if (memberName == null) {
            return psiClass.getNavigationElement();
        }

        PsiElement resolvedField = resolveField(psiClass, memberName, target);
        if (resolvedField != null) {
            return resolvedField;
        }

        return resolveMethod(psiClass, memberName, target);
    }

    private static @Nullable PsiElement resolveField(PsiClass psiClass, String memberName, BreezeJumpTarget target) {
        PsiField field = psiClass.findFieldByName(memberName, false);
        if (field != null) {
            return field.getNavigationElement();
        }

        for (PsiField candidate : psiClass.getAllFields()) {
            PsiClass containingClass = candidate.getContainingClass();
            if (containingClass != null
                    && target.getClassFqn().equals(containingClass.getQualifiedName())
                    && memberName.equals(candidate.getName())) {
                return candidate.getNavigationElement();
            }
        }
        return null;
    }

    private static @Nullable PsiElement resolveMethod(PsiClass psiClass, String memberName, BreezeJumpTarget target) {
        List<PsiMethod> methods = new ArrayList<>();
        collectMethods(methods, psiClass.findMethodsByName(memberName, false));
        if (methods.isEmpty()) {
            collectMethods(methods, psiClass.findMethodsByName(memberName, true));
        }
        if (methods.isEmpty()) {
            return null;
        }

        for (PsiMethod method : methods) {
            PsiClass containingClass = method.getContainingClass();
            if (containingClass != null && target.getClassFqn().equals(containingClass.getQualifiedName())) {
                return method.getNavigationElement();
            }
        }
        return methods.get(0).getNavigationElement();
    }

    private static void collectMethods(List<PsiMethod> collector, PsiMethod[] methods) {
        for (PsiMethod method : methods) {
            collector.add(method);
        }
    }

    private static @Nullable BreezeJumpResolvedTarget resolveFromScalaSource(Project project, BreezeJumpTarget target) {
        GlobalSearchScope scope = GlobalSearchScope.projectScope(project);
        Collection<VirtualFile> files = new ArrayList<>();
        files.addAll(FilenameIndex.getVirtualFilesByName(target.getShortClassName() + ".scala", scope));
        files.addAll(FilenameIndex.getVirtualFilesByName(target.getShortClassName() + ".sc", scope));

        PsiManager psiManager = PsiManager.getInstance(project);
        for (VirtualFile file : files) {
            PsiFile psiFile = psiManager.findFile(file);
            if (psiFile == null) {
                continue;
            }

            String text = psiFile.getText();
            if (!matchesPackage(text, target) || !containsTypeDefinition(text, target.getShortClassName())) {
                continue;
            }

            int typeOffset = findTypeOffset(text, target.getShortClassName());
            if (typeOffset < 0) {
                continue;
            }

            String memberName = target.getNormalizedMemberName();
            int memberOffset = findScalaMemberOffset(text, memberName);
            if (memberName != null && memberOffset < 0) {
                continue;
            }

            int sourceOffset = memberOffset >= 0 ? memberOffset : typeOffset;
            PsiElement element = psiFile.findElementAt(sourceOffset);
            if (element == null) {
                element = psiFile;
            }
            int finalOffset = resolveOffset(file, target.getLineNumber(), sourceOffset);
            return new BreezeJumpResolvedTarget(file, finalOffset, element);
        }
        return null;
    }

    private static boolean matchesPackage(String text, BreezeJumpTarget target) {
        Matcher matcher = PACKAGE_PATTERN.matcher(text);
        String packageName = matcher.find() ? StringUtil.notNullize(matcher.group(1)) : "";
        return packageName.equals(target.getPackageName());
    }

    private static boolean containsTypeDefinition(String text, String shortClassName) {
        return findTypeOffset(text, shortClassName) >= 0;
    }

    private static int findTypeOffset(String text, String shortClassName) {
        Pattern pattern = Pattern.compile(String.format(TYPE_TEMPLATE, Pattern.quote(shortClassName)));
        Matcher matcher = pattern.matcher(text);
        return matcher.find() ? matcher.start(1) : -1;
    }

    private static int findScalaMemberOffset(String text, @Nullable String memberName) {
        if (memberName == null) {
            return -1;
        }

        int fieldOffset = findScalaFieldOffset(text, memberName);
        if (fieldOffset >= 0) {
            return fieldOffset;
        }
        return findScalaMethodOffset(text, memberName);
    }

    private static int findScalaFieldOffset(String text, String memberName) {
        Pattern pattern = Pattern.compile(String.format(SCALA_FIELD_TEMPLATE, Pattern.quote(memberName)));
        Matcher matcher = pattern.matcher(text);
        return matcher.find() ? matcher.start() : -1;
    }

    private static int findScalaMethodOffset(String text, String methodName) {
        Pattern pattern = Pattern.compile(String.format(SCALA_METHOD_TEMPLATE, Pattern.quote(methodName)));
        Matcher matcher = pattern.matcher(text);
        return matcher.find() ? matcher.start() : -1;
    }

    private static int resolveOffset(VirtualFile file, Integer lineNumber, int fallbackOffset) {
        if (lineNumber == null || lineNumber < 1) {
            return fallbackOffset;
        }
        Document document = FileDocumentManager.getInstance().getDocument(file);
        if (document == null) {
            return fallbackOffset;
        }
        int lineIndex = lineNumber - 1;
        if (lineIndex >= document.getLineCount()) {
            return fallbackOffset;
        }
        return document.getLineStartOffset(lineIndex);
    }
}
