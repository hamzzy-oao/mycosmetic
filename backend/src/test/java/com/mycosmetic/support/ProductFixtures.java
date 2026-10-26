package com.mycosmetic.support;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.mycosmetic.dto.product.FinalItem;
import com.mycosmetic.entity.Category;
import com.mycosmetic.entity.Ingredient;
import com.mycosmetic.entity.IngredientList;
import com.mycosmetic.entity.IngredientListItem;
import com.mycosmetic.entity.MatchType;
import com.mycosmetic.entity.Product;
import com.mycosmetic.repository.IngredientListItemRepository;
import com.mycosmetic.repository.IngredientListRepository;
import com.mycosmetic.repository.IngredientRepository;
import com.mycosmetic.repository.ProductRepository;
import com.mycosmetic.service.product.ListFingerprint;

/**
 * 제품·성분 버전 테스트 데이터. 테스트끼리 DB를 같이 쓰므로 이름·바코드는 매번 고유하게 만든다.
 */
@Component
public class ProductFixtures {

	private final ProductRepository productRepository;
	private final IngredientRepository ingredientRepository;
	private final IngredientListRepository listRepository;
	private final IngredientListItemRepository itemRepository;

	public ProductFixtures(ProductRepository productRepository, IngredientRepository ingredientRepository,
			IngredientListRepository listRepository, IngredientListItemRepository itemRepository) {
		this.productRepository = productRepository;
		this.ingredientRepository = ingredientRepository;
		this.listRepository = listRepository;
		this.itemRepository = itemRepository;
	}

	public static String unique() {
		return UUID.randomUUID().toString().replace("-", "").substring(0, 12);
	}

	public static String newBarcode() {
		return "880" + unique();
	}

	public Product product(String barcode, String brand, String name) {
		return productRepository.save(Product.create(barcode, brand, name, "50ml", Category.SKINCARE, null));
	}

	/** 색조(MAKEUP) 제품 */
	public Product makeupProduct() {
		return productRepository
				.save(Product.create(newBarcode(), "브랜드", "립 " + unique(), "3g", Category.MAKEUP, null));
	}

	public Product product() {
		return product(newBarcode(), "브랜드", "토너 " + unique());
	}

	public Ingredient ingredient(String prefix) {
		return ingredientRepository.save(Ingredient.create(prefix + " " + unique(), null, null));
	}

	/** 표준명을 그대로 쓰는 성분. 이름이 고유한지는 부르는 쪽이 맞춘다 */
	public Ingredient ingredientNamed(String standardName) {
		return ingredientRepository.save(Ingredient.create(standardName, null, null));
	}

	/**
	 * 성분 버전을 만든다. 각 줄은 Ingredient(매칭됨) 또는 String(매칭 안 된 원문)이다.
	 * confirmations만큼 확인을 더한다(2 이상이면 VERIFIED).
	 */
	public IngredientList list(Product product, int confirmations, Object... lines) {
		List<FinalItem> finalItems = new ArrayList<>();
		for (Object line : lines) {
			if (line instanceof Ingredient i) {
				finalItems.add(new FinalItem(i.getId(), i.getStandardName()));
			} else {
				finalItems.add(new FinalItem(null, (String) line));
			}
		}
		IngredientList list = IngredientList.create(product, ListFingerprint.of(finalItems), null);
		for (int c = 0; c < confirmations; c++) {
			list.addConfirmation();
		}
		list = listRepository.saveAndFlush(list);
		int position = 1;
		for (Object line : lines) {
			if (line instanceof Ingredient i) {
				itemRepository.save(IngredientListItem.create(list, position++, i, i.getStandardName(), MatchType.EXACT));
			} else {
				itemRepository.save(IngredientListItem.create(list, position++, null, (String) line, MatchType.UNMATCHED));
			}
		}
		return list;
	}
}
