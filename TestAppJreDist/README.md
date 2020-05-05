# Description
This project downloads a Java 8 JRE and Java 11 and 14 JDKs for the current platform. For Java 11 and 14 jlink is executed to create minimized JREs. These JDKs and JREs are then placed into a configuration named "jdksAndCurrentPlatformJlinkedJres" for consumption by other Gradle projects such as PackrAllTestApp

# Note
This is an odd flow and mostly for testing of Java 8. What a normal app should do is build your Application, then run jlink passing the application module as input, publish this custom JRE to an artifact server, then in a Distribution project download that custom JRE and bundle it with platform independent resource and publish it into the world.
