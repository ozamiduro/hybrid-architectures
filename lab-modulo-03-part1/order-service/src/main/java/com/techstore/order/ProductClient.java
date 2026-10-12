package com.techstore.order;
import org.springframework.beans.factory.annotation.Value; import org.springframework.http.MediaType; import org.springframework.stereotype.Component; import org.springframework.web.client.RestClient; import java.math.BigDecimal; import java.util.Map;
@Component public class ProductClient {private final RestClient client; public ProductClient(@Value("${product.service.url}") String url){client=RestClient.builder().baseUrl(url).build();}
 public ProductDto decreaseStock(Long id,int q){return client.post().uri("/api/products/{id}/decrease-stock",id).contentType(MediaType.APPLICATION_JSON).body(Map.of("quantity",q)).retrieve().body(ProductDto.class);}
 public record ProductDto(Long id,String name,BigDecimal price,int stock){}
}