package com.mycosmetic.service.product;

import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.domain.PageRequest;
import com.mycosmetic.entity.*;
import com.mycosmetic.repository.*;
import com.mycosmetic.exception.*;
import com.mycosmetic.dto.product.*;

@Service
@Transactional(readOnly = true)
public class AdminProductService {
 private final UserRepository users;
 private final ProductRepository products;
 private final IngredientRepository ingredients;
 private final IngredientListRepository lists;
 private final IngredientListItemRepository items;
 private final ProductQueryService query;
 public AdminProductService(UserRepository users, ProductRepository products, IngredientRepository ingredients,
   IngredientListRepository lists, IngredientListItemRepository items, ProductQueryService query) {
  this.users=users; this.products=products; this.ingredients=ingredients; this.lists=lists; this.items=items; this.query=query;
 }
 private User requireAdmin(Long id) {
  User user=users.findById(id).orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED));
  if (user.getRole()!=UserRole.ADMIN) throw new BusinessException(ErrorCode.FORBIDDEN);
  return user;
 }
 public List<ProductSummaryResponse> search(Long userId, String q, int page) {
  requireAdmin(userId);
  if(q.length()>100 || page<0 || page>100000) throw new BusinessException(ErrorCode.INVALID_REQUEST);
  var found=products.searchByBrandOrName(ProductQueryService.escapeLike(q.strip()),PageRequest.of(page,20));
  var reps=query.representativeListIds(found.stream().map(Product::getId).toList());
  return found.stream().map(p -> ProductSummaryResponse.of(p,reps.get(p.getId()))).toList();
 }
 public long count(Long userId) { requireAdmin(userId); return products.countByArchivedFalse(); }
 public ProductDetailResponse detail(Long userId, Long productId) { requireAdmin(userId); return query.detail(productId); }
 @Transactional
 public ProductDetailResponse editProduct(Long userId, Long productId, String brand, String name, String volume, Category category) {
  requireAdmin(userId);
  Product product=activeProduct(productId);
  product.edit(brand.strip(),name.strip(),volume==null||volume.isBlank()?null:volume.strip(),category);
  products.flush();
  return query.detail(productId);
 }
 @Transactional
 public void deleteProduct(Long userId, Long productId) {
  requireAdmin(userId);
  activeProduct(productId).archive();
 }
 private Product activeProduct(Long productId) {
  return products.findAdminWithLockById(productId).filter(p -> !p.isArchived())
   .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
 }
 @Transactional
 public ProductDetailResponse save(Long userId, Long productId, Long baseListId, List<String> names) {
  User admin=requireAdmin(userId);
  Product product=activeProduct(productId);
  var versions=lists.findByProductId(productId);
  Long current=ListVersionRules.representative(versions).map(IngredientList::getId).orElse(null);
  if(!java.util.Objects.equals(baseListId,current)) throw new BusinessException(ErrorCode.CONFIRM_CONFLICT);
  var finalItems=names.stream().map(String::strip).map(name -> new FinalItem(
    ingredients.findByStandardName(name).map(Ingredient::getId).orElse(null),name)).toList();
  String fingerprint=ListFingerprint.of(finalItems);
  IngredientList version=lists.findByProductIdAndFingerprint(productId,fingerprint).orElse(null);
  if(version==null) {
   version=lists.saveAndFlush(IngredientList.create(product,fingerprint,admin));
   for(int i=0;i<finalItems.size();i++) {
    var row=finalItems.get(i);
    Ingredient ingredient=row.ingredientId()==null?null:ingredients.findById(row.ingredientId()).orElseThrow();
    items.save(IngredientListItem.create(version,i+1,ingredient,row.rawText(),ingredient==null?MatchType.UNMATCHED:MatchType.EXACT));
   }
  }
  for(var old:versions) old.clearAdminApproval();
  version.approveByAdmin();
  items.flush(); lists.flush();
  return query.detail(productId);
 }
}
