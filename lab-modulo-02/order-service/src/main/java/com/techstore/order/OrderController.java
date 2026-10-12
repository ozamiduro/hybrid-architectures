package com.techstore.order;

import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.util.List;

@RestController
@RequestMapping("/api/orders")
@CrossOrigin(origins = "*")
public class OrderController {

    private final OrderRepository repository;
    private final RestClient productClient;

    public OrderController(
            OrderRepository repository,
            @Value("${product.service.url}") String productServiceUrl) {
        this.repository = repository;
        this.productClient = RestClient.builder()
                .baseUrl(productServiceUrl)
                .build();
    }

    @GetMapping
    public List<Order> findAll() {
        return repository.findAll();
    }

    @PostMapping
    public ResponseEntity<?> create(@Valid @RequestBody OrderRequest request) {

        ProductResponse product;

        try {
            product = productClient.get()
                    .uri("/api/products/{id}", request.productId())
                    .retrieve()
                    .body(ProductResponse.class);
        } catch (Exception ex) {
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                    .body("product-service no disponible");
        }

        if (product == null) {
            return ResponseEntity.notFound().build();
        }

        if (product.stock() < request.quantity()) {
            return ResponseEntity.badRequest()
                    .body("Stock insuficiente");
        }

        BigDecimal total = product.price()
                .multiply(BigDecimal.valueOf(request.quantity()));

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(repository.save(
                        new Order(request.productId(), request.quantity(), total)
                ));
    }
}
