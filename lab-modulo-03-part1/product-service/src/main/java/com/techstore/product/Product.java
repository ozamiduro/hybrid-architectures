package com.techstore.product;
import jakarta.persistence.*; import java.math.BigDecimal;
@Entity @Table(name="products") public class Product {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
 @Column(nullable=false, unique=true) private String name;
 @Column(nullable=false, precision=12, scale=2) private BigDecimal price;
 @Column(nullable=false) private int stock;
 protected Product() {}
 public Product(String name, BigDecimal price, int stock){this.name=name;this.price=price;this.stock=stock;}
 public Long getId(){return id;} public String getName(){return name;} public BigDecimal getPrice(){return price;} public int getStock(){return stock;}
}