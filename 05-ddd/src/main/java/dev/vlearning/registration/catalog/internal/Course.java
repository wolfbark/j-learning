package dev.vlearning.registration.catalog.internal;

import org.jmolecules.ddd.annotation.AggregateRoot;
import org.jmolecules.ddd.annotation.Identity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * The catalog context's aggregate root — internal to the catalog. The catalog
 * team did their tactical DDD homework: identity is explicit, there are no
 * setters, and nobody outside {@code catalog.internal} should ever hold one of
 * these. (The enrollment context currently does. That is the point of this
 * lesson.)
 */
@Entity
@Table(name = "course")
@AggregateRoot
public class Course {

    @Id
    @Identity
    private String code;

    private String title;

    private int capacity;

    protected Course() {
        // JPA
    }

    public Course(String code, String title, int capacity) {
        this.code = code;
        this.title = title;
        this.capacity = capacity;
    }

    public String getCode() {
        return code;
    }

    public String getTitle() {
        return title;
    }

    public int getCapacity() {
        return capacity;
    }
}
