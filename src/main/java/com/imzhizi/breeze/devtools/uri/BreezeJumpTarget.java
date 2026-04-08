package com.imzhizi.breeze.devtools.uri;

import java.util.Objects;

public final class BreezeJumpTarget {
    private final String classFqn;
    private final String memberName;
    private final Integer lineNumber;

    public BreezeJumpTarget(String classFqn, String memberName, Integer lineNumber) {
        this.classFqn = Objects.requireNonNull(classFqn, "classFqn");
        this.memberName = memberName;
        this.lineNumber = lineNumber;
    }

    public String getClassFqn() {
        return classFqn;
    }

    public String getMemberName() {
        return memberName;
    }

    public Integer getLineNumber() {
        return lineNumber;
    }

    public String getShortClassName() {
        int index = classFqn.lastIndexOf('.');
        return index >= 0 ? classFqn.substring(index + 1) : classFqn;
    }

    public String getPackageName() {
        int index = classFqn.lastIndexOf('.');
        return index >= 0 ? classFqn.substring(0, index) : "";
    }

    public String getNormalizedMemberName() {
        if (memberName == null || memberName.isBlank()) {
            return null;
        }
        int parameterStart = memberName.indexOf('(');
        String rawName = parameterStart >= 0 ? memberName.substring(0, parameterStart) : memberName;
        return rawName.trim();
    }
}