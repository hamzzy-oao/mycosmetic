import { api } from './client';

// 내 화장대 API (설계 4.6, 계획 Task 19, no-ai-features 설계 C·D·E)
// ShelfItemResponse: { id, product, ingredientListId, listIsRepresentative, verdict, memo, startedOn, endedOn,
//   openedOn, paoMonths, expiresOn, expiryStatus: EXPIRED|EXPIRING|OK|UNKNOWN, usageDays,
//   shadeName, shadeHex(#RRGGBB), shadeTone: WARM|COOL|NEUTRAL, shadeSeason: SPRING|SUMMER|AUTUMN|WINTER,
//   shadeMatchesMe(퍼스널컬러가 없으면 null), updatedAt, matchedIngredients(성분으로 찾기에서만) }

export function getShelf() {
	return api.get('/api/shelf').then((res) => res.data);
}

// 성분으로 찾기: 내가 담은 버전의 성분에 q가 들어 있는 항목 + matchedIngredients
export function searchShelfByIngredient(q) {
	return api.get('/api/shelf', { params: { ingredient: q } }).then((res) => res.data);
}

// 내 톤에 맞는 색조: MAKEUP 항목 중 내 퍼스널컬러와 웜/쿨이 같은 것.
// 퍼스널컬러를 고르지 않았으면 400 { code: 'PERSONAL_COLOR_REQUIRED' }
export function getToneMatchedShelf() {
	return api.get('/api/shelf', { params: { toneMatch: true } }).then((res) => res.data);
}

// { total, inUse, finished, byCategory: {SKINCARE, MAKEUP}, byVerdict: {GOOD, BAD, UNRATED},
//   expiring: [ShelfItemResponse], expired: [ShelfItemResponse], avgUsageDaysFinished }
export function getShelfStats() {
	return api.get('/api/shelf/stats').then((res) => res.data);
}

export function addToShelf(ingredientListId) {
	return api.post('/api/shelf', { ingredientListId }).then((res) => res.data);
}

// 보낸 필드만 바뀐다(null·빠진 필드는 그대로)
export function updateShelfItem(id, changes) {
	return api.patch(`/api/shelf/${id}`, changes).then((res) => res.data);
}

export function removeShelfItem(id) {
	return api.delete(`/api/shelf/${id}`);
}
