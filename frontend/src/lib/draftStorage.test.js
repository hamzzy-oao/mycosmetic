import { DRAFT_MAX_AGE_MS, clearDraft, loadDraft, saveDraft } from './draftStorage';

afterEach(() => {
	jest.restoreAllMocks();
	window.localStorage.clear();
});

test('jobId별로 저장하고 다시 읽고 지운다', () => {
	expect(saveDraft(9, { items: [{ key: 'a', rawText: '정제수' }] })).toBe(true);
	expect(window.localStorage.getItem('mycosmetic:draft:9')).not.toBeNull();
	expect(loadDraft(9)).toEqual({ items: [{ key: 'a', rawText: '정제수' }] });
	expect(loadDraft(10)).toBeNull();
	clearDraft(9);
	expect(loadDraft(9)).toBeNull();
});

test('localStorage가 막혀 있으면 throw하지 않고 없는 것처럼 동작한다', () => {
	jest.spyOn(Storage.prototype, 'getItem').mockImplementation(() => {
		throw new Error('SecurityError');
	});
	jest.spyOn(Storage.prototype, 'setItem').mockImplementation(() => {
		throw new Error('QuotaExceededError');
	});
	jest.spyOn(Storage.prototype, 'removeItem').mockImplementation(() => {
		throw new Error('SecurityError');
	});
	expect(saveDraft(9, { items: [] })).toBe(false);
	expect(loadDraft(9)).toBeNull();
	expect(() => clearDraft(9)).not.toThrow();
});

test('깨진 값이나 24시간 지난 보관 내용은 버린다', () => {
	window.localStorage.setItem('mycosmetic:draft:1', '{not json');
	expect(loadDraft(1)).toBeNull();
	window.localStorage.setItem(
		'mycosmetic:draft:2',
		JSON.stringify({ savedAt: Date.now() - DRAFT_MAX_AGE_MS - 1000, state: { items: [] } })
	);
	expect(loadDraft(2)).toBeNull();
	expect(window.localStorage.getItem('mycosmetic:draft:2')).toBeNull();
});
