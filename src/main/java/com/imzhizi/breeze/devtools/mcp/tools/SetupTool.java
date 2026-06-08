package com.imzhizi.breeze.devtools.mcp.tools;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.intellij.openapi.application.ApplicationManager;
import com.imzhizi.breeze.devtools.mcp.McpJsonUtil;
import com.imzhizi.breeze.devtools.mcp.McpTool;
import com.imzhizi.breeze.devtools.settings.BreezeSettings;

import java.io.File;
import java.io.FileWriter;
import java.nio.file.Files;
import java.nio.file.attribute.PosixFilePermission;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * MCP tool: setup
 *
 * One-stop initialization. Detects and configures:
 *   1. idea CLI script (required for opening/focusing IDEA projects)
 *   2. Jumper SSH tunnel settings (MIS ID, host, proxy script, SSH keys)
 *
 * Called with no args: auto-detects everything possible, reports what is
 * still missing and needs to be provided by the user.
 *
 * Called with args: writes the provided values and re-runs detection for
 * anything still missing.
 */
public final class SetupTool implements McpTool {

    // Candidate paths to write the idea CLI script, tried in order
    private static final String[] IDEA_SCRIPT_WRITE_CANDIDATES = {
        "/opt/homebrew/bin/idea",
        System.getProperty("user.home") + "/.local/bin/idea",
        "/usr/local/bin/idea",
    };
    private static final String IDEA_SCRIPT_CONTENT =
            "#!/bin/sh\nopen -na \"IntelliJ IDEA.app\" --args \"$@\"\n";

    private static final String[] IDEA_CLI_LOCATIONS = {
        System.getProperty("user.home") + "/Library/Application Support/JetBrains/Toolbox/scripts/idea",
        "/usr/local/bin/idea",
        "/opt/homebrew/bin/idea",
    };
    private static final String[] SCRIPT_CANDIDATES = {
        "/.sankuai/MCopilot/components/com.sankuai.idekit-tetris-components-jumper/classes/jumper_proxy.sh",
    };
    private static final String[] USER_KEY_CANDIDATES = {
        "/.ssh/id_rsa_jumper",
        "/.ssh/id_rsa",
    };
    private static final String[] MOA_KEY_CANDIDATES = {
        "/.moa/ssh/id_rsa_jumper",
    };

    @Override public String getName() { return "setup"; }

    @Override
    public String getDescription() {
        return "One-stop setup for the plugin. Checks and configures: " +
                "(1) idea CLI script for opening/focusing IDEA projects from the terminal; " +
                "(2) Jumper SSH tunnel settings (MIS ID, jumper host, proxy script, SSH keys). " +
                "Run this first after installing the plugin, or when create_remote_debug_config fails. " +
                "Called with no args: auto-detects everything and reports what is missing. " +
                "Called with args: writes provided values and re-detects the rest.";
    }

    @Override
    public JsonObject getInputSchema() {
        JsonObject schema = new JsonObject();
        schema.addProperty("type", "object");
        JsonObject props = new JsonObject();
        addProp(props, "misId",       "string",
                "MIS ID (company employee ID, e.g. zhangsan) — used to log in to jumper");
        addProp(props, "jumperHost",  "string",
                "Jumper host (default: jumper.sankuai.com)");
        addProp(props, "scriptPath",  "string",
                "Absolute path to jumper_proxy.sh");
        addProp(props, "userKeyPath", "string",
                "Absolute path to personal SSH private key, or '-' if none");
        addProp(props, "moaKeyPath",  "string",
                "Absolute path to Moa-managed SSH private key");
        schema.add("properties", props);
        return schema;
    }

    @Override
    public String execute(JsonObject args, JsonElement id) {
        CompletableFuture<String> future = new CompletableFuture<>();
        ApplicationManager.getApplication().invokeLater(() -> {
            try {
                BreezeSettings s = BreezeSettings.getInstance();
                String home = System.getProperty("user.home", "");
                StringBuilder report = new StringBuilder();

                // ── Apply any user-supplied values first ──────────────────────
                if (args != null) {
                    applyArg(args, "misId",      v -> s.jumperUser            = v);
                    applyArg(args, "jumperHost",  v -> s.jumperHost            = v);
                    applyArg(args, "scriptPath",  v -> s.jumperProxyScriptPath = v);
                    applyArg(args, "userKeyPath", v -> s.jumperUserKeyPath     = v);
                    applyArg(args, "moaKeyPath",  v -> s.jumperSshKeyPath      = v);
                }

                // ── 1. idea CLI ───────────────────────────────────────────────
                report.append("── idea CLI ──\n");
                String ideaCli = findIdeaCli();
                if (ideaCli != null) {
                    report.append("  ✓ found at ").append(ideaCli).append("\n");
                } else {
                    String created = createIdeaScript();
                    if (created != null) {
                        report.append("  ✓ created at ").append(created).append("\n");
                    } else {
                        report.append("  ✗ not found and could not auto-create (no writable bin dir)\n");
                        report.append("    Create it manually (run in terminal):\n");
                        report.append("      printf '#!/bin/sh\\nopen -na \"IntelliJ IDEA.app\" --args \"$@\"\\n'")
                              .append(" > /opt/homebrew/bin/idea\n");
                        report.append("      chmod +x /opt/homebrew/bin/idea\n");
                    }
                }

                // ── 2. Jumper settings ────────────────────────────────────────
                report.append("\n── Jumper SSH Tunnel ──\n");
                autoDetectJumper(s, home);
                ApplicationManager.getApplication().saveSettings();

                report.append("  MIS ID:       ").append(misStatus(s.jumperUser)).append("\n");
                report.append("  jumper host:  ").append(val(s.jumperHost)).append("\n");
                report.append("  script:       ").append(fileStatus(s.jumperProxyScriptPath)).append("\n");
                report.append("  user SSH key: ").append("-".equals(s.jumperUserKeyPath) ? "(none)"
                        : fileStatus(s.jumperUserKeyPath)).append("\n");
                report.append("  Moa SSH key:  ").append(fileStatus(s.jumperSshKeyPath)).append("\n");

                // ── Missing items requiring user input ────────────────────────
                StringBuilder missing = new StringBuilder();
                if (s.jumperUser == null || s.jumperUser.isBlank())
                    missing.append("  - misId: your MIS ID (company login, e.g. zhangsan)\n");
                if (!fileExists(s.jumperProxyScriptPath))
                    missing.append("  - scriptPath: path to jumper_proxy.sh (usually ~/.sankuai/MCopilot/...)\n");
                if (!fileExists(s.jumperSshKeyPath) && !fileExists(s.jumperUserKeyPath))
                    missing.append("  - moaKeyPath or userKeyPath: path to your SSH private key\n");

                if (missing.length() > 0) {
                    report.append("\nSetup incomplete. Call setup() with the missing values:\n");
                    report.append(missing);
                } else {
                    report.append("\nAll settings configured. Ready to use create_remote_debug_config. ✓");
                }

                future.complete(McpJsonUtil.successTextResponse(id, report.toString().trim()));
            } catch (Exception e) {
                future.complete(McpJsonUtil.toolErrorResponse(id, "Setup failed: " + e.getMessage()));
            }
        });

        try { return future.get(10, TimeUnit.SECONDS); }
        catch (Exception e) { return McpJsonUtil.toolErrorResponse(id, "Timed out: " + e.getMessage()); }
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private static String findIdeaCli() {
        // Check PATH first
        for (String dir : System.getenv("PATH").split(":")) {
            File f = new File(dir, "idea");
            if (f.exists() && f.canExecute()) return f.getAbsolutePath();
        }
        // Check known locations
        for (String loc : IDEA_CLI_LOCATIONS) {
            if (new File(loc).exists()) return loc;
        }
        return null;
    }

    private static String createIdeaScript() {
        for (String path : IDEA_SCRIPT_WRITE_CANDIDATES) {
            try {
                File f = new File(path);
                f.getParentFile().mkdirs();
                try (FileWriter fw = new FileWriter(f)) { fw.write(IDEA_SCRIPT_CONTENT); }
                Set<PosixFilePermission> perms = Files.getPosixFilePermissions(f.toPath());
                perms.add(PosixFilePermission.OWNER_EXECUTE);
                perms.add(PosixFilePermission.GROUP_EXECUTE);
                perms.add(PosixFilePermission.OTHERS_EXECUTE);
                Files.setPosixFilePermissions(f.toPath(), perms);
                return path;
            } catch (Exception ignored) {}
        }
        return null;
    }

    private static void autoDetectJumper(BreezeSettings s, String home) {
        if (s.jumperUser == null || s.jumperUser.isBlank())
            s.jumperUser = System.getProperty("user.name", "");
        if (s.jumperHost == null || s.jumperHost.isBlank())
            s.jumperHost = "jumper.sankuai.com";
        if (!fileExists(s.jumperProxyScriptPath)) {
            String f = firstExisting(home, SCRIPT_CANDIDATES);
            if (f != null) s.jumperProxyScriptPath = f;
        }
        if (!"-".equals(s.jumperUserKeyPath) && !fileExists(s.jumperUserKeyPath)) {
            String f = firstExisting(home, USER_KEY_CANDIDATES);
            s.jumperUserKeyPath = (f != null) ? f : "-";
        }
        if (!fileExists(s.jumperSshKeyPath)) {
            String f = firstExisting(home, MOA_KEY_CANDIDATES);
            if (f != null) s.jumperSshKeyPath = f;
        }
    }

    private static String misStatus(String v) {
        if (v == null || v.isBlank()) return "(empty — required)";
        return v + "  ← MIS ID used to authenticate with jumper";
    }

    private static String val(String v) { return (v == null || v.isBlank()) ? "(empty)" : v; }

    private static String fileStatus(String path) {
        if (path == null || path.isBlank()) return "(empty)";
        return path + (new File(path).exists() ? " ✓" : " ✗ NOT FOUND");
    }

    private static boolean fileExists(String path) {
        return path != null && !path.isBlank() && new File(path).exists();
    }

    private static String firstExisting(String home, String[] suffixes) {
        for (String s : suffixes) {
            String full = home + s;
            if (new File(full).exists()) return full;
        }
        return null;
    }

    @FunctionalInterface interface Setter { void set(String v); }

    private static void applyArg(JsonObject args, String key, Setter setter) {
        if (args.has(key)) {
            String v = McpJsonUtil.getString(args, key);
            if (v != null) setter.set(v);
        }
    }

    private static void addProp(JsonObject props, String key, String type, String desc) {
        JsonObject p = new JsonObject(); p.addProperty("type", type); p.addProperty("description", desc);
        props.add(key, p);
    }
}
