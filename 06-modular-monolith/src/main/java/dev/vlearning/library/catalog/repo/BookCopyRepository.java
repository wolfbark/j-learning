package dev.vlearning.library.catalog.repo;

import java.util.List;
import java.util.Optional;

import org.springframework.data.repository.Repository;

import dev.vlearning.library.catalog.domain.BookCopy;

public interface BookCopyRepository extends Repository<BookCopy, Long> {

    BookCopy save(BookCopy copy);

    Optional<BookCopy> findByBarcode(String barcode);

    List<BookCopy> findByBookIdOrderByBarcodeAsc(Long bookId);
}
