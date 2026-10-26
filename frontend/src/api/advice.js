import { api } from './client';

// "이거 사도 돼?" 규칙 판단 API (no-ai-features 설계 F). body는 셋 중 하나:
// { productId } | { ingredientsText }(3000자, 200개까지)
// 응답: { verdict: GOOD_FIT|CAUTION|AVOID|UNKNOWN,
//   reasons: [{ type, level: WARNING|CAUTION|POSITIVE|INFO, message, ingredients: [name], relatedShelfItemIds: [id] }],
//   matchedCount, unmatchedCount, ingredients: [name], product: ProductSummaryResponse|null, alreadyOnShelf,
//   relatedItems: [{ id, productId, brand, name, verdict }] }
export function getAdvice(body) {
	return api.post('/api/advice', body).then((res) => res.data);
}
