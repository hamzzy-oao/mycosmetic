// 피부 일기·새 제품 테스트 공통 (설계 2026-10-24 A·B)

export const MOODS = ['GOOD', 'OK', 'BAD'];

export const MOOD_LABELS = {
	GOOD: '좋음',
	OK: '보통',
	BAD: '나쁨',
};

export const TAGS = ['TROUBLE', 'DRYNESS', 'OILINESS', 'REDNESS', 'ITCHINESS'];

export const TAG_LABELS = {
	TROUBLE: '트러블',
	DRYNESS: '건조',
	OILINESS: '번들거림',
	REDNESS: '붉어짐',
	ITCHINESS: '가려움',
};

export const WEEKDAYS = ['일', '월', '화', '수', '목', '금', '토'];

function pad(n) {
	return String(n).padStart(2, '0');
}

export function monthOf(date) {
	return date.slice(0, 7);
}

// 'YYYY-MM'에 n개월을 더한다
export function addMonths(month, n) {
	const [y, m] = month.split('-').map(Number);
	const d = new Date(Date.UTC(y, m - 1 + n, 1));
	return `${d.getUTCFullYear()}-${pad(d.getUTCMonth() + 1)}`;
}

// 달력 칸: 첫 주 앞은 null로 채운다. 날짜는 'YYYY-MM-DD'
export function calendarCells(month) {
	const [y, m] = month.split('-').map(Number);
	const firstWeekday = new Date(Date.UTC(y, m - 1, 1)).getUTCDay();
	const days = new Date(Date.UTC(y, m, 0)).getUTCDate();
	const cells = Array(firstWeekday).fill(null);
	for (let d = 1; d <= days; d++) {
		cells.push(`${month}-${pad(d)}`);
	}
	return cells;
}

export function dayLabel(date) {
	const [, m, d] = date.split('-').map(Number);
	return `${m}월 ${d}일`;
}

// 새 제품 테스트 기간인 날짜('YYYY-MM-DD') 모음. 일찍 끝낸 테스트는 끝낸 날까지만
export function trialDates(trials) {
	const dates = new Set();
	(trials || []).forEach((t) => {
		const end = t.finishedOn && t.finishedOn < t.endsOn ? t.finishedOn : t.endsOn;
		(t.days || []).forEach((d) => {
			if (d.date <= end) dates.add(d.date);
		});
	});
	return dates;
}

// 테스트 기간 요약 한 줄: "기록 5일 · 좋음 2 · 보통 2 · 나쁨 1 · 트러블 2일"
export function summaryText(summary) {
	if (!summary || summary.recordedDays === 0) return '아직 이 기간에 쓴 일기가 없어요.';
	const moods = MOODS.map((m) => `${MOOD_LABELS[m]} ${summary.moods[m] || 0}`);
	const tags = TAGS.filter((t) => summary.tags[t] > 0).map((t) => `${TAG_LABELS[t]} ${summary.tags[t]}일`);
	return [`기록 ${summary.recordedDays}일`, ...moods, ...tags].join(' · ');
}
