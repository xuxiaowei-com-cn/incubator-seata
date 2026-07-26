import java.util.Optional;

public class KillProcessByPidHandle {

    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java KillProcessByPidHandle <PID>");
            System.exit(1);
        }

        try {
            int pid = Integer.parseInt(args[0]);
            boolean killed = killProcess(pid);
            if (killed) {
                System.out.println("Process " + pid + " terminated successfully.");
            } else {
                System.err.println("Failed to terminate process " + pid + " (process may not exist or insufficient permissions).");
            }
        } catch (NumberFormatException e) {
            System.err.println("Invalid PID: " + args[0]);
        }
    }

    /**
     * Terminate a process by PID using the ProcessHandle API.
     * On Windows, destroy() actually calls TerminateProcess (forced termination) [reference:6],
     * but compared to directly invoking taskkill /F, it provides a more unified API and
     * better error handling.
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

        // Attempt to terminate the process (forced termination on Windows)
        boolean destroyed = processHandle.destroy();
        if (destroyed) {
            // Optionally wait for the process to actually exit
            try {
                processHandle.onExit().get(); // Block until the process exits
            } catch (Exception e) {
                // Ignore wait exceptions
            }
            return true;
        } else {
            // Fall back to destroyForcibly() if destroy() fails
            processHandle.destroyForcibly();
            return true;
        }
    }
}
