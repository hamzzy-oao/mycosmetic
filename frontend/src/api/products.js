import { api } from './client';

// 제품 조회 API (설계 4.6, 계획 Task 18)

// ProductDetailResponse: { product, representative: { id, status, confirmCount, items }, otherLists }
export function getProduct(id) {
	return api.get(`/api/products/${id}`).then((res) => res.data);
}

// 이름으로 제품 검색 (설계 4.1-8 "이 제품인가요?"). ProductSummaryResponse 목록(최대 20개).
// 서버는 q가 2글자 미만이면 빈 목록을 준다
export function searchProducts(q) {
	return api.get('/api/products', { params: { q } }).then((res) => res.data);
}

// 추출 결과 확인·저장 (설계 4.1-9, 계획 Task 17) → 201 ConfirmResponse
// body: { jobId, productId } 또는 { jobId, newProduct: { brand, name, volume, category, sourceUrl, differentProduct } }, 그리고
//       sourceUrl: 올리브영 링크에서 가져왔으면 그 주소, differentProduct: 같은 이름 제품이 있어도 다른 제품이라고 확인함
//       (안 보내고 같은 이름 제품이 있으면 409 PRODUCT_SAME_NAME_EXISTS)
//       items: [{ ingredientId, rawText }] (화면에 보이는 순서 그대로)
export function confirmProduct(body) {
	return api.post('/api/products/confirm', body).then((res) => res.data);
}

// 특정 성분 버전 (화장대의 내 버전 펼치기) → { id, status, confirmCount, items: [{ position, ingredientId, name, matchType }] }
// 그 제품의 버전이 아니면 404
export function getProductList(productId, listId) {
	return api.get(`/api/products/${productId}/lists/${listId}`).then((res) => res.data);
}

// 두 버전 차이 (설계 4.6) → { added: [이름], removed: [이름], orderChanged }
export function getListDiff(productId, from, to) {
	return api.get(`/api/products/${productId}/lists/diff`, { params: { from, to } }).then((res) => res.data);
}

// 올리브영 상품 정보 가져오기 → { name, imageUrl, sourceUrl, notice, ingredientSections, volume, paoMonths }
// ingredientSections: [{ label, items: [MatchResult] }] (전성분을 못 가져왔거나 서버에서 꺼 두면 빈 목록)
// 올리브영 링크가 아니면 400 PRODUCT_LINK_UNSUPPORTED, 못 가져오면 502 PRODUCT_LINK_FETCH_FAILED
export function previewProductLink(url, options = {}) {
	return api.post('/api/product-links/preview', { url }, { timeout: 10000, signal: options.signal }).then((res) => res.data);
}

// 붙여넣은 전성분을 옵션(호수·구성품)별로 나눠 사전과 맞춘다(저장하지 않음) → { sections: [{ label, items: [MatchResult] }] }
export function matchIngredientText(text) {
	return api.post('/api/ingredient-text/match', { text }).then((res) => res.data);
}

// 새 제품 저장 전 이름 점검(2026-10-26, 저장하지 않음) →
// { name(정리한 제품명), volume(이름 속 용량 제안 또는 null), reasons: [string], brandKnown, brandSuggestion(또는 null),
//   similar: [{ product: ProductSummaryResponse, similarity(0~100), sameName }] }
export function checkProductName({ brand, name, volume }) {
	return api.get('/api/products/name-check', { params: { brand, name, volume: volume || undefined } }).then((res) => res.data);
}

// 등록된 브랜드 표기(제품이 많은 순). 브랜드 입력 자동완성
export function getBrands() {
	return api.get('/api/products/brands').then((res) => res.data);
}

// 저장 전 확인 결과에서 사용자에게 보여 줄 게 있는지
export function nameCheckNeedsAttention(result) {
	return Boolean(result && (result.reasons.length > 0 || result.brandSuggestion || result.similar.length > 0));
}
