package cn.com.xuxiaowei;

import org.zeroturnaround.process.PidProcess;
import org.zeroturnaround.process.ProcessUtil;
import org.zeroturnaround.process.Processes;
import org.zeroturnaround.process.WindowsProcess;

import java.util.concurrent.TimeUnit;

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

            // On Windows, also terminate child processes
            if (process instanceof WindowsProcess) {
                ((WindowsProcess) process).setIncludeChildren(true);
            }

            ProcessUtil.destroyGracefullyOrForcefullyAndWait(process,
                    30, TimeUnit.SECONDS, 10, TimeUnit.SECONDS);
            System.out.println("Process " + pid + " terminated");
        } catch (Exception e) {
            System.err.println("Failed to terminate process " + pid + ": " + e.getMessage());
            System.exit(1);
        }
    }

}
