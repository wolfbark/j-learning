package dev.vlearning.library.catalog.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;

@Entity
public class BookCopy {

    public enum Status {
        AVAILABLE, ON_LOAN
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private Book book;

    private String barcode;

    @Enumerated(EnumType.STRING)
    private Status status = Status.AVAILABLE;

    protected BookCopy() {
    }

    public BookCopy(Book book, String barcode) {
        this.book = book;
        this.barcode = barcode;
    }

    public Long getId() {
        return id;
    }

    public Book getBook() {
        return book;
    }

    public String getBarcode() {
        return barcode;
    }

    public Status getStatus() {
        return status;
    }

    public void setStatus(Status status) {
        this.status = status;
    }
}
