package com.mycosmetic.controller;

import java.util.List;
import org.springframework.web.bind.annotation.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import com.mycosmetic.security.CurrentUserId;
import com.mycosmetic.service.product.AdminProductService;
import com.mycosmetic.dto.product.*;

@RestController
@RequestMapping("/api/admin/products")
public class AdminProductController {
 private final AdminProductService service;
 public AdminProductController(AdminProductService service) { this.service=service; }
 public record EditRequest(Long baseListId, @NotEmpty @Size(max=200) List<@NotBlank @Size(max=500) String> ingredients) {}
 public record ProductEditRequest(@NotBlank @Size(max=100) String brand, @NotBlank @Size(max=200) String name,
  @Size(max=50) String volume, @NotNull com.mycosmetic.entity.Category category) {}
 public record CountResponse(long count) {}
 @PutMapping("/{id}")
 public ProductDetailResponse editProduct(@CurrentUserId Long userId, @PathVariable Long id, @Valid @RequestBody ProductEditRequest request) {
  return service.editProduct(userId,id,request.brand(),request.name(),request.volume(),request.category());
 }
 @DeleteMapping("/{id}")
 @ResponseStatus(org.springframework.http.HttpStatus.NO_CONTENT)
 public void deleteProduct(@CurrentUserId Long userId, @PathVariable Long id) { service.deleteProduct(userId,id); }
 @GetMapping
 public List<ProductSummaryResponse> search(@CurrentUserId Long userId, @RequestParam(defaultValue="") String q, @RequestParam(defaultValue="0") int page) {
  return service.search(userId,q,page);
 }
 @GetMapping("/count")
 public CountResponse count(@CurrentUserId Long userId) { return new CountResponse(service.count(userId)); }
 @GetMapping("/{id}")
 public ProductDetailResponse detail(@CurrentUserId Long userId,@PathVariable Long id) { return service.detail(userId,id); }
 @PutMapping("/{id}/ingredients")
 public ProductDetailResponse save(@CurrentUserId Long userId,@PathVariable Long id,@Valid @RequestBody EditRequest request) {
  return service.save(userId,id,request.baseListId(),request.ingredients());
 }
}
