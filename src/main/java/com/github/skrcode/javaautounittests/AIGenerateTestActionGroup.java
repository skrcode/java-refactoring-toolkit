package com.github.skrcode.javaautounittests;

import com.intellij.openapi.actionSystem.DefaultActionGroup;

public class AIGenerateTestActionGroup extends DefaultActionGroup {
    public AIGenerateTestActionGroup() {
        super("JAIPilot - Auto Unit Tests with AI", true); // true = isPopup
    }
}
