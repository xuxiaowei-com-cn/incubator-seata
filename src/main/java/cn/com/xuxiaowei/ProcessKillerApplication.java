package cn.com.xuxiaowei;

import org.zeroturnaround.process.PidProcess;
import org.zeroturnaround.process.Processes;
import org.zeroturnaround.process.WindowsProcess;

public class ProcessKillerApplication {

    public static void main(String[] args) {

        // Validate arguments
        if (args.length == 0) {
            System.err.println("Usage: java -jar process-killer.jar <pid>");
            System.err.println("Example: java -jar process-killer.jar 12345");
            System.exit(1);
        }

        int pid;
        try {
            pid = Integer.parseInt(args[0]);
        } catch (NumberFormatException e) {
            System.err.println("Error: PID must be an integer, got: " + args[0]);
            System.exit(1);
            return;
        }

        try {
            // Create process object
            PidProcess process = Processes.newPidProcess(pid);

            // Check if the process is alive
            if (!process.isAlive()) {
                System.out.println("Process " + pid + " does not exist");
                return;
            }

            // On Windows, configure process termination behavior:
            // - setIncludeChildren(true): also terminate child processes
            // - setGracefulDestroyEnabled(true): send CTRL_BREAK_EVENT first for graceful shutdown,
            //   then fall back to forceful termination if the process does not exit in time
            if (process instanceof WindowsProcess) {
                ((WindowsProcess) process).setIncludeChildren(true);
                ((WindowsProcess) process).setGracefulDestroyEnabled(true);
            }

            process.destroy(true);
            System.out.println("Process " + pid + " terminated");
        } catch (Exception e) {
            System.err.println("Failed to terminate process " + pid + ": " + e.getMessage());
            System.exit(1);
        }
    }

}
