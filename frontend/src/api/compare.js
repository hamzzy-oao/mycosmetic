import { api } from './client';

// 제품 비교 API (no-ai-features 설계 B)
// 각 쪽은 내 화장대 항목(a, b) 또는 제품의 대표 버전(aProduct, bProduct) 중 하나
// 응답: { a: { product, listId }, b: { product, listId }, common: [name], onlyA: [name], onlyB: [name],
//   groupsA: [RETINOID|AHA|BHA|VITAMIN_C|BENZOYL_PEROXIDE|NIACINAMIDE], groupsB: [...] }
export function compareProducts({ a, b, aProduct, bProduct }) {
	const params = {};
	if (a != null) params.a = a;
	if (b != null) params.b = b;
	if (aProduct != null) params.aProduct = aProduct;
	if (bProduct != null) params.bProduct = bProduct;
	return api.get('/api/compare', { params }).then((res) => res.data);
}
