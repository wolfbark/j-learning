package dev.vlearning.library.lending.domain;

import java.time.Instant;
import java.time.LocalDate;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;

@Entity
public class Loan {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String copyBarcode;
    private String bookTitle;
    private String memberEmail;
    private LocalDate dueDate;
    private Instant returnedAt;

    protected Loan() {
    }

    public Loan(String copyBarcode, String bookTitle, String memberEmail, LocalDate dueDate) {
        this.copyBarcode = copyBarcode;
        this.bookTitle = bookTitle;
        this.memberEmail = memberEmail;
        this.dueDate = dueDate;
    }

    public Long getId() {
        return id;
    }

    public String getCopyBarcode() {
        return copyBarcode;
    }

    public String getBookTitle() {
        return bookTitle;
    }

    public String getMemberEmail() {
        return memberEmail;
    }

    public LocalDate getDueDate() {
        return dueDate;
    }

    public Instant getReturnedAt() {
        return returnedAt;
    }

    public void setReturnedAt(Instant returnedAt) {
        this.returnedAt = returnedAt;
    }
}
