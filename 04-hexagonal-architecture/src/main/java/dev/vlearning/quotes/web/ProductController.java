package dev.vlearning.quotes.web;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import dev.vlearning.quotes.persistence.ProductEntity;
import dev.vlearning.quotes.persistence.ProductJpaRepository;

@RestController
@RequestMapping("/products")
public class ProductController {

    private final ProductJpaRepository productRepository;

    public ProductController(ProductJpaRepository productRepository) {
        this.productRepository = productRepository;
    }

    @GetMapping
    public List<ProductEntity> listProducts() {
        return productRepository.findAll();
    }

    public record CreateProductRequest(String code, String name) {
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ProductEntity createProduct(@RequestBody CreateProductRequest request) {
        return productRepository.save(new ProductEntity(request.code(), request.name()));
    }
}
