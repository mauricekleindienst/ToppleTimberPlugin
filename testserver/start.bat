@echo off
set JAVA_HOME=C:\Program Files\Eclipse Adoptium\jdk-25.0.3.9-hotspot
set PATH=%JAVA_HOME%\bin;%PATH%
java -Xms512M -Xmx2G -jar paper-26.1.2-63.jar nogui
pause
