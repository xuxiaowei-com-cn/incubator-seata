import java.lang.foreign.*;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class KillProcessByPidHandle {

    public static void main(String[] args) {
        if (args.length != 1) {
            System.err.println("Usage: java --enable-native-access=ALL-UNNAMED KillProcessByPidHandle.java <PID>");
            System.exit(1);
        }

        try {
            int pid = Integer.parseInt(args[0]);
            boolean killed = killProcess(pid);
            if (killed) {
                System.out.println("Process " + pid + " terminated successfully.");
            } else {
                System.err.println(
                        "Failed to terminate process " + pid + " (process may not exist or insufficient permissions).");
            }
        } catch (NumberFormatException e) {
            System.err.println("Invalid PID: " + args[0]);
        }
    }

    /**
     * Terminate a process by PID using the ProcessHandle API.
     * <ul>
     * <li>On Linux/macOS: sends SIGTERM via {@code destroy()}, falls back to
     * SIGKILL via {@code destroyForcibly()}.</li>
     * <li>On Windows: attempts graceful termination via
     * {@code GenerateConsoleCtrlEvent(CTRL_BREAK_EVENT)} first, then falls back
     * to {@code destroyForcibly()} (TerminateProcess). This provides behavior
     * similar to SIGTERM on Unix — the JVM triggers its shutdown hooks in
     * response to CTRL_BREAK_EVENT.</li>
     * </ul>
     */
    public static boolean killProcess(int pid) {
        Optional<ProcessHandle> opt = ProcessHandle.of(pid);
        if (opt.isEmpty()) {
            System.err.println("No process found with PID " + pid);
            return false;
        }

        ProcessHandle processHandle = opt.get();

        // Check if the process is alive
        if (!processHandle.isAlive()) {
            System.err.println("Process " + pid + " is already terminated");
            return false;
        }

        String osName = System.getProperty("os.name").toLowerCase();
        if (osName.contains("win")) {
            return killProcessWindows(processHandle, pid);
        } else {
            return killProcessUnix(processHandle);
        }
    }

    // --- Windows: graceful (CTRL_BREAK_EVENT) → force (TerminateProcess) ---

    private static boolean killProcessWindows(ProcessHandle processHandle, int pid) {
        // Step 1: try graceful termination via GenerateConsoleCtrlEvent
        boolean gracefulSent = gracefulKillWindows(pid);
        if (gracefulSent) {
            // Give the process time to shut down cleanly (trigger shutdown hooks, etc.)
            boolean exited = waitForProcess(processHandle, 30_000);
            if (exited) {
                return true;
            }
            System.err.println("Process did not exit after CTRL_BREAK_EVENT, forcing termination...");
        }

        // Step 2: fall back to forced termination (TerminateProcess on Windows)
        processHandle.destroyForcibly();
        return true;
    }

    /**
     * Attempt graceful termination on Windows by sending CTRL_BREAK_EVENT via
     * the Windows Console API. The JVM treats CTRL_BREAK_EVENT similarly to
     * SIGTERM — it runs shutdown hooks before exiting.
     * <p>
     * Uses the Java Foreign Function &amp; Memory API (standard since Java 22)
     * to call:
     * <ol>
     * <li>{@code SetConsoleCtrlHandler(ignoreHandler, TRUE)} — register a
     * handler that ignores console control events for our own process, so we
     * don't get killed by the CTRL_BREAK_EVENT we're about to send.</li>
     * <li>{@code FreeConsole()} — detach from the current console</li>
     * <li>{@code AttachConsole(pid)} — attach to the target process's console</li>
     * <li>{@code GenerateConsoleCtrlEvent(CTRL_BREAK_EVENT, 0)} — send the
     * signal to all processes sharing the target console</li>
     * <li>{@code FreeConsole()} — detach from the target console</li>
     * </ol>
     *
     * @return true if the signal was sent successfully, false otherwise
     */
    private static boolean gracefulKillWindows(int pid) {
        try {
            SymbolLookup kernel32 = SymbolLookup.libraryLookup("kernel32", Arena.global());
            Linker linker = Linker.nativeLinker();

            // BOOL FreeConsole(void)
            MethodHandle freeConsole = linker.downcallHandle(
                    kernel32.find("FreeConsole").orElseThrow(), FunctionDescriptor.of(ValueLayout.JAVA_INT));

            // BOOL AttachConsole(DWORD dwProcessId)
            MethodHandle attachConsole = linker.downcallHandle(
                    kernel32.find("AttachConsole").orElseThrow(),
                    FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.JAVA_INT));

            // BOOL GenerateConsoleCtrlEvent(DWORD dwCtrlEvent, DWORD dwProcessGroupId)
            MethodHandle generateEvent = linker.downcallHandle(
                    kernel32.find("GenerateConsoleCtrlEvent").orElseThrow(),
                    FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT));

            // BOOL SetConsoleCtrlHandler(PHANDLER_ROUTINE HandlerRoutine, BOOL Add)
            MethodHandle setConsoleCtrlHandler = linker.downcallHandle(
                    kernel32.find("SetConsoleCtrlHandler").orElseThrow(),
                    FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_INT));

            // Create an upcall stub that acts as a console control handler.
            // The handler returns TRUE (1) for all events, meaning "I handled it,
            // don't terminate me." This protects our own process from the
            // CTRL_BREAK_EVENT we are about to send via GenerateConsoleCtrlEvent.
            // Without this, AttachConsole + GenerateConsoleCtrlEvent would also
            // kill us because we now share the target's console.
            //
            // Signature: BOOL WINAPI HandlerRoutine(DWORD dwCtrlType)
            // dropArguments adapts constant ()->int to (int)->int (ignores the arg).
            MemorySegment ignoreHandler = linker.upcallStub(
                    MethodHandles.dropArguments(
                            MethodHandles.constant(int.class, 1), // Always return TRUE
                            0,
                            int.class),
                    FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.JAVA_INT),
                    Arena.ofAuto());

            // Register the ignore handler (add = 1 / TRUE)
            setConsoleCtrlHandler.invoke(ignoreHandler, 1);

            // 1. Detach from current console
            freeConsole.invoke();

            // 2. Attach to the target process's console
            int attached = (int) attachConsole.invoke(pid);
            if (attached == 0) {
                System.err.println("AttachConsole failed: target process " + pid + " may not be a console process");
                return false;
            }

            // 3. Send CTRL_BREAK_EVENT (1) to all processes sharing this console (group 0).
            // Our ignoreHandler (registered above) protects us from being killed.
            int result = (int) generateEvent.invoke(1, 0);

            // 4. Detach from target console
            freeConsole.invoke();

            if (result == 0) {
                System.err.println("GenerateConsoleCtrlEvent failed for process " + pid);
                return false;
            }

            System.out.println("CTRL_BREAK_EVENT sent to process " + pid);
            return true;
        } catch (Throwable e) {
            System.err.println("Graceful Windows termination via GenerateConsoleCtrlEvent failed: " + e.getMessage());
            return false;
        }
    }

    // --- Unix: SIGTERM → SIGKILL ---

    private static boolean killProcessUnix(ProcessHandle processHandle) {
        // On Linux/macOS, destroy() sends SIGTERM (graceful)
        boolean destroyed = processHandle.destroy();
        if (destroyed) {
            try {
                processHandle.onExit().get(); // Block until the process exits
            } catch (Exception e) {
                // Ignore wait exceptions
            }
            return true;
        } else {
            // Fall back to destroyForcibly() (SIGKILL) if SIGTERM fails
            processHandle.destroyForcibly();
            return true;
        }
    }

    // --- Utility ---

    /**
     * Wait up to {@code timeoutMs} milliseconds for the process to exit.
     *
     * @return true if the process exited within the timeout, false otherwise
     */
    private static boolean waitForProcess(ProcessHandle processHandle, long timeoutMs) {
        try {
            processHandle.onExit().get(timeoutMs, TimeUnit.MILLISECONDS);
            return !processHandle.isAlive();
        } catch (TimeoutException e) {
            return false;
        } catch (Exception e) {
            // Process already exited or interrupted — treat as success
            return !processHandle.isAlive();
        }
    }
}
