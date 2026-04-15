package com.imzhizi.breeze.devtools.spec;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.Nullable;

/**
 * Represents a resolved spec file target with file and offset information
 */
public final class SpecJumpResolvedTarget {
    private final VirtualFile file;
    private final int offset;
    private final PsiElement element;

    public SpecJumpResolvedTarget(VirtualFile file, int offset, PsiElement element) {
        this.file = file;
        this.offset = offset;
        this.element = element;
    }

    public VirtualFile getFile() {
        return file;
    }

    public int getOffset() {
        return offset;
    }

    public PsiElement getElement() {
        return element;
    }

    public static SpecJumpResolvedTarget fromVirtualFile(VirtualFile file, PsiElement element) {
        return new SpecJumpResolvedTarget(file, 0, element);
    }
}
