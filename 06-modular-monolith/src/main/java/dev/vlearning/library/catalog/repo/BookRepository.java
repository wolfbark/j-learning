package dev.vlearning.library.catalog.repo;

import java.util.List;

import org.springframework.data.repository.Repository;

import dev.vlearning.library.catalog.domain.Book;

public interface BookRepository extends Repository<Book, Long> {

    Book save(Book book);

    List<Book> findAllByOrderByTitleAsc();
}
