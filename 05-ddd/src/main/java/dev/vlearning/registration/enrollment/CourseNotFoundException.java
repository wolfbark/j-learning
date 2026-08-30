package dev.vlearning.registration.enrollment;

public class CourseNotFoundException extends RuntimeException {

    public CourseNotFoundException(String courseCode) {
        super("no course " + courseCode + " in the catalog");
    }
}
