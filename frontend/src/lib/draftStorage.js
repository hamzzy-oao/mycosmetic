// 성분 확인 화면의 편집 내용 임시 보관 (설계 4.1-8, 6.4)
// - localStorage 키: mycosmetic:draft:<jobId>
// - 사생활 보호 모드·저장소 차단에서는 접근 자체가 throw할 수 있어 모든 접근을 try/catch로 감싼다.
//   보관에 실패해도 화면은 그대로 동작한다(새로고침하면 편집 내용만 사라진다)
// - 확인하지 않은 job은 서버가 24시간 뒤 지우므로(설계 4.4) 그보다 오래된 보관 내용은 버린다
const PREFIX = 'mycosmetic:draft:';
export const DRAFT_MAX_AGE_MS = 24 * 60 * 60 * 1000;

function keyOf(jobId) {
	return `${PREFIX}${jobId}`;
}

function storage() {
	try {
		return typeof window !== 'undefined' ? window.localStorage : null;
	} catch (e) {
		return null;
	}
}

export function saveDraft(jobId, state) {
	if (jobId == null) return false;
	try {
		const store = storage();
		if (!store) return false;
		store.setItem(keyOf(jobId), JSON.stringify({ savedAt: Date.now(), state }));
		return true;
	} catch (e) {
		return false;
	}
}

export function clearDraft(jobId) {
	if (jobId == null) return;
	try {
		const store = storage();
		if (store) store.removeItem(keyOf(jobId));
	} catch (e) {
		// 지우지 못해도 24시간이 지나면 읽지 않는다
	}
}

export function loadDraft(jobId) {
	if (jobId == null) return null;
	try {
		const store = storage();
		if (!store) return null;
		const raw = store.getItem(keyOf(jobId));
		if (!raw) return null;
		const parsed = JSON.parse(raw);
		if (!parsed || typeof parsed !== 'object' || !parsed.state) return null;
		if (typeof parsed.savedAt !== 'number' || Date.now() - parsed.savedAt > DRAFT_MAX_AGE_MS) {
			clearDraft(jobId);
			return null;
		}
		return parsed.state;
	} catch (e) {
		return null;
	}
}
