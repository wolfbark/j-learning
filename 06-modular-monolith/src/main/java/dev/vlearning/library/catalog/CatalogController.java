package dev.vlearning.library.catalog;

import java.util.ArrayList;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import dev.vlearning.library.catalog.domain.Book;
import dev.vlearning.library.catalog.domain.BookCopy;
import dev.vlearning.library.catalog.repo.BookCopyRepository;
import dev.vlearning.library.catalog.repo.BookRepository;

@RestController
public class CatalogController {

    private final BookRepository books;
    private final BookCopyRepository copies;

    CatalogController(BookRepository books, BookCopyRepository copies) {
        this.books = books;
        this.copies = copies;
    }

    record AddBookRequest(String isbn, String title, String author, int copies) {
    }

    record BookCreatedResponse(Long id, String isbn, String title, String author, List<String> copyBarcodes) {
    }

    record CopyView(String barcode, BookCopy.Status status) {
    }

    record BookView(String isbn, String title, String author, List<CopyView> copies) {
    }

    @PostMapping("/catalog/books")
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    BookCreatedResponse addBook(@RequestBody AddBookRequest request) {
        if (request.isbn() == null || request.isbn().isBlank()) {
            throw new IllegalArgumentException("isbn must not be blank");
        }
        if (request.title() == null || request.title().isBlank()) {
            throw new IllegalArgumentException("title must not be blank");
        }
        if (request.copies() < 1) {
            throw new IllegalArgumentException("a book needs at least one copy");
        }

        Book book = books.save(new Book(request.isbn(), request.title(), request.author()));
        List<String> barcodes = new ArrayList<>();
        for (int i = 1; i <= request.copies(); i++) {
            String barcode = request.isbn() + "-" + i;
            copies.save(new BookCopy(book, barcode));
            barcodes.add(barcode);
        }
        return new BookCreatedResponse(book.getId(), book.getIsbn(), book.getTitle(), book.getAuthor(), barcodes);
    }

    @GetMapping("/catalog/books")
    @Transactional(readOnly = true)
    List<BookView> listBooks() {
        return books.findAllByOrderByTitleAsc().stream()
                .map(book -> new BookView(book.getIsbn(), book.getTitle(), book.getAuthor(),
                        copies.findByBookIdOrderByBarcodeAsc(book.getId()).stream()
                                .map(copy -> new CopyView(copy.getBarcode(), copy.getStatus()))
                                .toList()))
                .toList();
    }
}
