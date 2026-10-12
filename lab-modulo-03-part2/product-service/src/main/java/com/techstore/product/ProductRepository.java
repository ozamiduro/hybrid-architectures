package com.techstore.product;
import org.springframework.data.jpa.repository.*; import org.springframework.data.repository.query.Param; import org.springframework.transaction.annotation.Transactional;
public interface ProductRepository extends JpaRepository<Product,Long> {
 @Modifying @Transactional @Query("update Product p set p.stock=p.stock-:quantity where p.id=:id and p.stock>=:quantity")
 int decreaseStockIfAvailable(@Param("id") Long id,@Param("quantity") int quantity);
}