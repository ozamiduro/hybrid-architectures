package com.techstore.product;
import jakarta.validation.Valid; import jakarta.validation.constraints.*; import org.springframework.http.HttpStatus; import org.springframework.web.bind.annotation.*; import org.springframework.web.server.ResponseStatusException; import java.math.BigDecimal; import java.util.List;
@RestController @RequestMapping("/api/products") public class ProductController {
 private final ProductRepository repo; public ProductController(ProductRepository repo){this.repo=repo;}
 @GetMapping public List<Product> all(){seedIfEmpty();return repo.findAll();}
 @GetMapping("/{id}") public Product get(@PathVariable Long id){seedIfEmpty();return repo.findById(id).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Producto no encontrado"));}
 @PostMapping @ResponseStatus(HttpStatus.CREATED) public Product create(@Valid @RequestBody ProductRequest r){if(r.price().signum()<0||r.stock()<0)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Precio y stock deben ser no negativos");return repo.save(new Product(r.name(),r.price(),r.stock()));}
 @PostMapping("/{id}/decrease-stock") public Product decrease(@PathVariable Long id,@Valid @RequestBody StockRequest r){if(r.quantity()<1)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"quantity debe ser mayor que cero");seedIfEmpty();int n=repo.decreaseStockIfAvailable(id,r.quantity());if(n==0){Product p=repo.findById(id).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Producto no encontrado"));throw new ResponseStatusException(HttpStatus.CONFLICT,"Stock insuficiente. Stock disponible: "+p.getStock());}return repo.findById(id).orElseThrow();}
 private void seedIfEmpty(){if(repo.count()==0){repo.save(new Product("Teclado mecánico",new BigDecimal("120.00"),20));repo.save(new Product("Mouse inalámbrico",new BigDecimal("65.00"),30));repo.save(new Product("Monitor 24 pulgadas",new BigDecimal("750.00"),10));}}
 public record ProductRequest(@NotBlank String name,@NotNull BigDecimal price,@Min(0) int stock){}
 public record StockRequest(@Min(1) int quantity){}
}