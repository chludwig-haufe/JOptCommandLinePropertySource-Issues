# JOptCommandLinePropertySource Issue Demo #

This repository contains a JUnit test class 
[JOptCommandLinePropertySourceTest](src/test/java/com/haufe/bugreports/spring/wrongpropertynamedemo/JOptCommandLinePropertySourceTests.java)
that demonstrates that the implementation of Spring's `JOptCommandLinePropertySource` selects 
the option alias names enumerated by `getPropertyNames()` in an inconsistent way. The test class 
also shows different property name enumeration strategies that, in the author's opinion, produce
more helpful results.

The Spring Boot application 
[WrongPropertyNameDemoApplication](src/main/java/com/haufe/bugreports/spring/wrongpropertynamedemo/WrongPropertyNameDemoApplication.java)
demonstrates how command line arguments processed by JOpt can be injected into the environment of
a Spring Boot application (and thus be referenced in `@Configuration` classes).

  