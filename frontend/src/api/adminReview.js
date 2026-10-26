import { api } from './client';

// 관리자 점검함 API (2026-10-24). 관리자가 아니면 403 { code: 'FORBIDDEN' }
// ReviewResponse: { checkedProducts, counts: { NAME_FORMAT, BRAND_VARIANT, DUPLICATE_SUSPECT, INGREDIENT_SPACING,
//   INGREDIENT_NOT_LIST, INGREDIENT_UNMATCHED }, spacingLines, dismissed, issues: [ReviewIssue] }
// ReviewIssue: { productId, brand, name, volume, category, type, reasons: [string], examples: [string],
//   suggestion: { brand, name, volume } | null, signature,
//   members(중복 의심만): [{ productId, brand, name, volume, createdAt, shelfCount, ingredientLines }] }

export function getReview() {
	return api.get('/api/admin/review').then((res) => res.data);
}

// "괜찮음": 같은 값(signature)이면 다시 보여주지 않는다
export function dismissIssue(issue) {
	return api.post('/api/admin/review/dismissals', {
		productId: issue.productId,
		type: issue.type,
		signature: issue.signature,
	});
}

// 사용자가 등록한 제품 확인 완료(NEW_USER_PRODUCT) → 204
export function verifyProduct(productId) {
	return api.post(`/api/admin/review/products/${productId}/verify`);
}

// 모든 성분 버전의 띄어쓰기 고치기 → { lists, items, skipped }
export function fixSpacing() {
	return api.post('/api/admin/review/fix-spacing').then((res) => res.data);
}

// 제안대로 고치기: 제안에 없는 값은 지금 값 그대로 보낸다(PUT /api/admin/products/{id})
export function applySuggestion(issue) {
	const s = issue.suggestion || {};
	return api.put(`/api/admin/products/${issue.productId}`, {
		brand: s.brand ?? issue.brand,
		name: s.name ?? issue.name,
		volume: s.volume ?? issue.volume ?? '',
		category: issue.category,
	});
}

// 중복 의심 제품 보관(검색·관리 목록에서 빠지고 기존 화장대 기록은 그대로)
export function archiveProduct(productId) {
	return api.delete(`/api/admin/products/${productId}`);
}
