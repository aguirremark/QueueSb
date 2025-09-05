@echo off
echo Attempting to build with Maven...
if exist "%MAVEN_HOME%\bin\mvn.cmd" (
    "%MAVEN_HOME%\bin\mvn.cmd" clean compile
) else if exist "C:\Program Files\Apache\maven\bin\mvn.cmd" (
    "C:\Program Files\Apache\maven\bin\mvn.cmd" clean compile
) else if exist "C:\apache-maven\bin\mvn.cmd" (
    "C:\apache-maven\bin\mvn.cmd" clean compile
) else (
    echo Maven not found. Please install Maven or use your IDE to build the project.
    echo The Azure Service Bus dependency needs to be downloaded.
)