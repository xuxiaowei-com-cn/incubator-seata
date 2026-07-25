@echo off
if [%1]==[] goto usage

set AGENTJAR=org.everit.jdk.javaagent.shutdown-1.0.0.jar
set RUNJAR=gracefulkilljava-1.0.jar

for %%I in (.) do set CURDIR=%%~sI

if exist %~dp0%RUNJAR% (
    set RUNFILE=%~dp0%RUNJAR%
) else if exist %~dp0target\%RUNJAR% (
    set RUNFILE=%~dp0target\%RUNJAR%
)else if exist %~dp0target\classes (
    set RUNFILE=%~dp0target\classes
) else (
    ECHO ERROR:  Could not find %RUNJAR% in current directory nor in .\target directory.
    goto end
)

if exist %~dp0%AGENTJAR% (
set AGENTFILE=%~dp0%AGENTJAR%
) else if exist %~dp0target\%AGENTJAR% (
set AGENTFILE=%~dp0target\%AGENTJAR%
) else (
ECHO ERROR:  Could not find %AGENTJAR% in current directory nor in .\target directory.
goto end
)

REM use short path to avoid dealing with spaces.
pushd %JAVA_HOME%
 for %%I in (.) do set JAVA_HOME=%%~sI
popd

REM This does not do graceful shutdown.  Cannot use this.
REM WMIC PROCESS where "Name='JAVA.EXE' AND COMMANDLINE LIKE '%%NodeDaemon.poolName=%1%%'" CALL TERMINATE

REM argument of 300000 will wait 5 minutes for graceful shutdown, then force kill.
ECHO This will execute the following wmic command:
ECHO    wmic process where "caption like 'java.exe' and commandline like '%%%1%%'" get processid
ECHO Then load the agentjar in each process to start graceful shutdown.
echo java -cp %JAVA_HOME%\lib\tools.jar;%RUNFILE% com.jda.gracefulkilljava.GracefulKill %AGENTFILE% %1 300000
%JAVA_HOME%\bin\java -cp %JAVA_HOME%\lib\tools.jar;%RUNFILE% com.jda.gracefulkilljava.GracefulKill %AGENTFILE% %1 300000

goto end
:usage
ECHO USAGE:  %0 [Command Line Partial Snippet To Query]
goto end
:end
