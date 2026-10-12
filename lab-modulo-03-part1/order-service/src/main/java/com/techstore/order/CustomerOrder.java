package com.techstore.order;
import jakarta.persistence.*; import java.math.BigDecimal; import java.time.Instant;
@Entity @Table(name="customer_orders") public class CustomerOrder {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id; @Column(nullable=false) private Long productId; @Column(nullable=false) private int quantity; @Column(nullable=false,precision=12,scale=2) private BigDecimal unitPrice; @Column(nullable=false,precision=12,scale=2) private BigDecimal total; @Column(nullable=false) private Instant createdAt;
 protected CustomerOrder(){} public CustomerOrder(Long p,int q,BigDecimal price){productId=p;quantity=q;unitPrice=price;total=price.multiply(BigDecimal.valueOf(q));createdAt=Instant.now();}
 public Long getId(){return id;} public Long getProductId(){return productId;} public int getQuantity(){return quantity;} public BigDecimal getUnitPrice(){return unitPrice;} public BigDecimal getTotal(){return total;} public Instant getCreatedAt(){return createdAt;}
}