package com.shipit.backend.build.model;

/**
 * Identifies the detected project layout and packaging mechanism for automated builds.
 */
public enum ProjectType {

    /**
     * Standard Java / Spring Boot application using Maven (pom.xml).
     */
    MAVEN_SPRING_BOOT,

    /**
     * Application containing a user-provided Dockerfile in the repository.
     */
    DOCKERFILE_CUSTOM,

    /**
     * Unrecognized or currently unsupported project layout.
     */
    UNKNOWN
}
