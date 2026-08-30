package dev.vlearning.registration.enrollment;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/enrollments")
class EnrollmentController {

    record EnrollRequest(String courseCode, String attendeeEmail, int seats) {
    }

    private final EnrollmentService service;

    EnrollmentController(EnrollmentService service) {
        this.service = service;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    EnrollmentResponse enroll(@RequestBody EnrollRequest request) {
        return EnrollmentResponse.from(
                service.create(request.courseCode(), request.attendeeEmail(), request.seats()));
    }

    @PostMapping("/{id}/reserve-seat")
    EnrollmentResponse reserveSeat(@PathVariable long id) {
        return EnrollmentResponse.from(service.reserveSeat(id));
    }

    @PostMapping("/{id}/confirm")
    EnrollmentResponse confirm(@PathVariable long id) {
        return EnrollmentResponse.from(service.confirm(id));
    }

    @PostMapping("/{id}/cancel")
    EnrollmentResponse cancel(@PathVariable long id) {
        return EnrollmentResponse.from(service.cancel(id));
    }

    @GetMapping("/{id}")
    EnrollmentResponse get(@PathVariable long id) {
        return EnrollmentResponse.from(service.get(id));
    }
}
