package dev.vlearning.registration.enrollment;

public class EnrollmentNotFoundException extends RuntimeException {

    public EnrollmentNotFoundException(long enrollmentId) {
        super("no enrollment with id " + enrollmentId);
    }
}
