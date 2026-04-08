package com.imzhizi.breeze.devtools.preview;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.imzhizi.breeze.devtools.navigation.BreezeJumpNavigator;
import com.imzhizi.breeze.devtools.uri.BreezeJumpUriParser;

public final class BreezeJumpPreviewHandler {
    private BreezeJumpPreviewHandler() {
    }

    public static boolean open(Project project, String uri) {
        if (project == null || uri == null || !BreezeJumpUriParser.isBreezeJumpUri(uri)) {
            return false;
        }
        ApplicationManager.getApplication().invokeLater(() -> BreezeJumpNavigator.open(project, uri));
        return true;
    }
}