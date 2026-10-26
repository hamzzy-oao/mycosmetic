package com.mycosmetic.service.product;

import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.*;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import com.mycosmetic.entity.*;
import com.mycosmetic.repository.*;
import com.mycosmetic.exception.BusinessException;

class AdminProductServiceTest {
 private final UserRepository users=mock(UserRepository.class);
 private final ProductRepository products=mock(ProductRepository.class);
 private final IngredientListRepository lists=mock(IngredientListRepository.class);
 private final IngredientListItemRepository items=mock(IngredientListItemRepository.class);
 private final IngredientRepository ingredients=mock(IngredientRepository.class);
 private final ProductQueryService query=mock(ProductQueryService.class);
 private final AdminProductService service=new AdminProductService(users,products,ingredients,lists,items,query);
 @Test void ordinaryUserCannotReadOrWriteAdminData() {
  when(users.findById(1L)).thenReturn(Optional.of(User.ofEmail("u@x.com","hash","user")));
  assertThatThrownBy(()->service.search(1L,"",0)).isInstanceOf(BusinessException.class);
  assertThatThrownBy(()->service.count(1L)).isInstanceOf(BusinessException.class);
  assertThatThrownBy(()->service.detail(1L,2L)).isInstanceOf(BusinessException.class);
  assertThatThrownBy(()->service.save(1L,2L,null,List.of("정제수"))).isInstanceOf(BusinessException.class);
  assertThatThrownBy(()->service.deleteProduct(1L,2L)).isInstanceOf(BusinessException.class);
  assertThatThrownBy(()->service.editProduct(1L,2L,"b","n",null,Category.SKINCARE)).isInstanceOf(BusinessException.class);
  verifyNoInteractions(products,lists,items,query);
 }
 @Test void adminMayReadAndRevokedRoleTakesEffectImmediately() {
  User admin=User.ofEmail("a@x.com","hash","admin");
  ReflectionTestUtils.setField(admin,"role",UserRole.ADMIN);
  when(users.findById(1L)).thenReturn(Optional.of(admin));
  service.detail(1L,2L);
  verify(query).detail(2L);
  ReflectionTestUtils.setField(admin,"role",UserRole.USER);
  assertThatThrownBy(()->service.detail(1L,2L)).isInstanceOf(BusinessException.class);
  verifyNoMoreInteractions(query);
 }
 @Test void adminMayCountCurrentProductsAndRevokedRoleTakesEffectImmediately() {
  User admin=User.ofEmail("a@x.com","hash","admin");
  ReflectionTestUtils.setField(admin,"role",UserRole.ADMIN);
  when(users.findById(1L)).thenReturn(Optional.of(admin));
  when(products.countByArchivedFalse()).thenReturn(123L);
  assertThat(service.count(1L)).isEqualTo(123L);
  verify(products).countByArchivedFalse();
  verify(products,never()).count();
  ReflectionTestUtils.setField(admin,"role",UserRole.USER);
  assertThatThrownBy(()->service.count(1L)).isInstanceOf(BusinessException.class);
  verifyNoMoreInteractions(products);
 }
 @Test void staleEditDoesNotChangeIngredients() {
  User admin=User.ofEmail("a@x.com","hash","admin");
  ReflectionTestUtils.setField(admin,"role",UserRole.ADMIN);
  when(users.findById(1L)).thenReturn(Optional.of(admin));
  when(products.findAdminWithLockById(2L)).thenReturn(Optional.of(Product.create(null,"brand","name",null,Category.SKINCARE,admin)));
  when(lists.findByProductId(2L)).thenReturn(List.of());
  assertThatThrownBy(()->service.save(1L,2L,99L,List.of("정제수"))).isInstanceOf(BusinessException.class);
  verifyNoInteractions(items,ingredients);
 }
 @Test void adminCanEditAndArchiveWithoutDeletingShelfHistory() {
  User admin=User.ofEmail("a@x.com","hash","admin");
  ReflectionTestUtils.setField(admin,"role",UserRole.ADMIN);
  when(users.findById(1L)).thenReturn(Optional.of(admin));
  Product product=Product.create(null,"old","old",null,Category.SKINCARE,admin);
  when(products.findAdminWithLockById(2L)).thenReturn(Optional.of(product));
  service.editProduct(1L,2L," brand "," name "," 50ml ",Category.MAKEUP);
  assertThat(product.getBrand()).isEqualTo("brand");
  assertThat(product.getName()).isEqualTo("name");
  assertThat(product.getVolume()).isEqualTo("50ml");
  assertThat(product.getCategory()).isEqualTo(Category.MAKEUP);
  service.deleteProduct(1L,2L);
  assertThat(product.isArchived()).isTrue();
  verify(products,never()).delete(any());
  verifyNoInteractions(items,ingredients);
  assertThatThrownBy(()->service.editProduct(1L,2L,"b","n",null,Category.SKINCARE)).isInstanceOf(BusinessException.class);
 }
}
