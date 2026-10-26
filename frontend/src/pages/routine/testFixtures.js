// 루틴 테스트용 응답
export function routineStep(shelfItemId, stepType, position, overrides = {}) {
	return {
		shelfItemId,
		stepType,
		position,
		productId: 100 + shelfItemId,
		brand: '브랜드',
		productName: `제품${shelfItemId}`,
		category: 'SKINCARE',
		...overrides,
	};
}

export function routine(overrides = {}) {
	return {
		id: 7,
		name: '저녁 루틴',
		slot: 'EVENING',
		days: [],
		steps: [routineStep(1, 'TONER', 1), routineStep(2, 'SERUM', 2)],
		updatedAt: '2026-10-22T00:00:00Z',
		...overrides,
	};
}
