package com.imzhizi.breeze.devtools.mcp.tools;

import com.intellij.openapi.components.Service;
import com.intellij.openapi.project.Project;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.time.LocalDate;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Project-level service that tracks SSH tunnel state for jumper-based remote debug configs.
 *
 * Each MCP-created remote debug config that uses the jumper has an entry here:
 *   configName → TunnelInfo(realHost, realPort, localPort)
 *
 * The IntelliJ Run Config internally points to 127.0.0.1:localPort.
 * The AI always sees/uses realHost:realPort.
 * This service holds the mapping so we can translate between the two.
 */
@Service(Service.Level.PROJECT)
public final class JumperTunnelManager {

    /** configName → tunnel info */
    private final Map<String, TunnelInfo> tunnels = new ConcurrentHashMap<>();

    /** Date of the last successfully established tunnel. Null = never established today. */
    private final AtomicReference<LocalDate> lastSuccessDate = new AtomicReference<>(null);

    /**
     * Total wait for first-of-day tunnel:
     *   terminal startup (~30 s) + sh overhead (~10 s) + jumper_proxy.exp timeout (60 s) + buffer (20 s).
     */
    public static final int FIRST_TUNNEL_TIMEOUT_MS = 120_000;
    /** Reusing an existing jumper session: should complete in well under 20 s. */
    public static final int REUSE_TUNNEL_TIMEOUT_MS = 20_000;

    public record TunnelInfo(String realHost, int realPort, int localPort) {
        public String displayAddress() { return realHost + ":" + realPort; }
    }

    // ------------------------------------------------------------------

    public static JumperTunnelManager getInstance(Project project) {
        return project.getService(JumperTunnelManager.class);
    }

    public void register(String configName, String realHost, int realPort, int localPort) {
        tunnels.put(configName, new TunnelInfo(realHost, realPort, localPort));
    }

    public void unregister(String configName) {
        tunnels.remove(configName);
    }

    public TunnelInfo get(String configName) {
        return tunnels.get(configName);
    }

    public boolean hasJumperTunnel(String configName) {
        return tunnels.containsKey(configName);
    }

    public Map<String, TunnelInfo> all() {
        return Map.copyOf(tunnels);
    }

    /**
     * True if no tunnel has been successfully established today.
     * First-of-day connections require phone approval (up to 180 s).
     */
    public boolean isFirstTunnelOfDay() {
        LocalDate last = lastSuccessDate.get();
        return last == null || !last.equals(LocalDate.now());
    }

    /** Call this after a tunnel port becomes connectable to reset the daily counter. */
    public void markTunnelEstablished() {
        lastSuccessDate.set(LocalDate.now());
    }

    /** Finds a free local port by binding to port 0 and reading the assigned port. */
    public static int findFreePort() throws IOException {
        try (ServerSocket ss = new ServerSocket(0)) {
            ss.setReuseAddress(true);
            return ss.getLocalPort();
        }
    }

    /** Returns true if 127.0.0.1:port accepts a TCP connection within 1 second. */
    public static boolean isPortOpen(int port) {
        try (Socket s = new Socket()) {
            s.connect(new InetSocketAddress("127.0.0.1", port), 1000);
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    /**
     * Polls 127.0.0.1:port every 500 ms until it accepts a connection or timeoutMs elapses.
     * Must be called from a background thread (blocks).
     */
    public static boolean waitForPort(int port, int timeoutMs) {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline) {
            if (isPortOpen(port)) return true;
            try { Thread.sleep(500); } catch (InterruptedException e) { return false; }
        }
        return false;
    }

    /**
     * Waits for the SSH tunnel to complete by polling the jumper_proxy result file.
     *
     * <p>The result file is written by {@code jumper_proxy.exp} and the shell wrapper:
     * <ul>
     *   <li>"0" (written by expect on success) → {@link TunnelWaitResult#SUCCESS}</li>
     *   <li>non-zero code (written by shell on failure) → {@link TunnelWaitResult#AUTH_FAILED}</li>
     *   <li>{@code timeoutMs} elapsed with no result → {@link TunnelWaitResult#TIMED_OUT}</li>
     * </ul>
     *
     * <p>Must be called from a background thread (blocks).
     */
    public static TunnelWaitResult waitForTunnel(int port, String resultFile, int timeoutMs) {
        java.io.File rf = new java.io.File(resultFile);
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline) {
            TunnelWaitResult r = readResultFile(rf);
            if (r != null) return r;
            try { Thread.sleep(500); } catch (InterruptedException e) { return TunnelWaitResult.TIMED_OUT; }
        }
        // Final check at deadline boundary.
        TunnelWaitResult r = readResultFile(rf);
        return r != null ? r : TunnelWaitResult.TIMED_OUT;
    }

    private static TunnelWaitResult readResultFile(java.io.File rf) {
        if (!rf.exists() || rf.length() == 0) return null;
        try {
            String code = java.nio.file.Files.readString(rf.toPath()).trim();
            if ("0".equals(code))   return TunnelWaitResult.SUCCESS;
            if (!code.isEmpty())    return TunnelWaitResult.AUTH_FAILED;
        } catch (Exception ignored) {}
        return null;
    }

    public enum TunnelWaitResult { SUCCESS, AUTH_FAILED, TIMED_OUT }
}
