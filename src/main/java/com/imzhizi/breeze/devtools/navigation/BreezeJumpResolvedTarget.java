package com.imzhizi.breeze.devtools.navigation;

import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;

public final class BreezeJumpResolvedTarget {
    private final VirtualFile file;
    private final int offset;
    private final PsiElement element;

    public BreezeJumpResolvedTarget(VirtualFile file, int offset, PsiElement element) {
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
}