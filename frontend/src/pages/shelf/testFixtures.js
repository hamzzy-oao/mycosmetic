// 내 화장대 테스트용 ShelfItemResponse
export function shelfItem(overrides = {}) {
	const id = overrides.id || 1;
	return {
		id,
		product: {
			id: 100 + id,
			barcode: null,
			brand: '브랜드',
			name: `제품${id}`,
			volume: '50ml',
			category: 'SKINCARE',
			representativeListId: 200 + id,
		},
		ingredientListId: 200 + id,
		listIsRepresentative: true,
		verdict: 'UNRATED',
		memo: null,
		startedOn: null,
		endedOn: null,
		updatedAt: '2026-10-01T00:00:00Z',
		...overrides,
	};
}
