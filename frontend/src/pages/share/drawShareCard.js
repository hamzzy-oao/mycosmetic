import { productTitle } from '../shelf/shelfCommon';

// 화장대 자랑 카드. 1~3개는 정사각형, 4~5개는 세로형 PNG로 그린다.
// 색은 캔버스가 상속한 CSS 토큰을 읽는다(못 읽으면 같은 값의 기본값).
export const CARD_WIDTH = 1080;
export const CARD_HEIGHT = 1350;
export const MAX_ITEMS = 5;

export function getCardHeight(itemCount) {
	return itemCount > 3 ? CARD_HEIGHT : CARD_WIDTH;
}

const FALLBACK = {
	'--rose': '#e71749',
	'--rose-strong': '#d0103c',
	'--rose-deep': '#a92240',
	'--blush': '#ffe2e0',
	'--petal': '#facbd0',
	'--cream': '#fffdf4',
	'--ink': '#463b34',
	'--share-paper': '#ffffff',
	'--share-ink': '#342f2e',
	'--share-muted': '#786f6b',
	'--share-line': '#e5e1de',
	'--share-accent': '#a44850',
};

let logoPromise;

// 홈과 같은 로컬 로고를 사용한다. 로딩이 끝난 이미지로만 PNG를 그린다.
export function loadShareCardLogo() {
	if (!logoPromise) {
		logoPromise = new Promise((resolve, reject) => {
			const logo = new Image();
			logo.onload = () => resolve(logo);
			logo.onerror = () => {
				logoPromise = null;
				reject(new Error('로고를 불러오지 못했어요.'));
			};
			logo.src = `${process.env.PUBLIC_URL || ''}/brandboard/vanity-primary.svg`;
		});
	}
	return logoPromise;
}

function token(name, element = document.documentElement) {
	try {
		const value = window.getComputedStyle(element).getPropertyValue(name).trim();
		return value || FALLBACK[name];
	} catch (e) {
		return FALLBACK[name];
	}
}

// 폭을 넘으면 뒤를 줄이고 …를 붙인다
function fit(ctx, text, maxWidth) {
	if (ctx.measureText(text).width <= maxWidth) return text;
	let cut = text;
	while (cut.length > 1 && ctx.measureText(`${cut}…`).width > maxWidth) {
		cut = cut.slice(0, -1);
	}
	return `${cut}…`;
}

function productLines(ctx, text, maxWidth) {
	const characters = Array.from(text);
	let line = '';
	let index = 0;
	while (index < characters.length && ctx.measureText(line + characters[index]).width <= maxWidth) {
		line += characters[index];
		index += 1;
	}
	if (index === characters.length) return [line];
	// 가능하면 단어 사이에서 줄을 바꾸고, 긴 한글 제품명도 안전하게 나눈다.
	const space = line.lastIndexOf(' ');
	if (space > line.length / 2) {
		index = Array.from(line.slice(0, space + 1)).length;
		line = line.slice(0, space);
	}
	return [line.trim(), fit(ctx, characters.slice(index).join('').trim(), maxWidth)];
}

function roundRect(ctx, x, y, w, h, r) {
	ctx.beginPath();
	ctx.moveTo(x + r, y);
	ctx.arcTo(x + w, y, x + w, y + h, r);
	ctx.arcTo(x + w, y + h, x, y + h, r);
	ctx.arcTo(x, y + h, x, y, r);
	ctx.arcTo(x, y, x + w, y, r);
	ctx.closePath();
}

// items: 화장대 항목(ShelfItemResponse). date: 'YYYY-MM-DD'
export function drawShareCard(ctx, { items, date, nickname, logo }) {
	const font = "'Malgun Gothic', 'Segoe UI', sans-serif";
	const products = items.slice(0, MAX_ITEMS);
	const height = getCardHeight(products.length);
	const color = (name) => token(name, ctx.canvas || document.documentElement);
	ctx.save();
	ctx.textBaseline = 'alphabetic';
	ctx.fillStyle = color('--share-paper');
	ctx.fillRect(0, 0, CARD_WIDTH, height);
	ctx.strokeStyle = color('--share-line');
	ctx.lineWidth = 3;
	roundRect(ctx, 32, 32, CARD_WIDTH - 64, height - 64, 44);
	ctx.stroke();

	if (logo) {
		const width = 330;
		const height = width * (logo.naturalHeight || logo.height) / (logo.naturalWidth || logo.width);
		ctx.drawImage(logo, (CARD_WIDTH - width) / 2, 72, width, height);
	}
	ctx.textAlign = 'center';
	ctx.fillStyle = color('--share-muted');
	ctx.font = '400 34px Georgia, serif';
	ctx.fillText('MY VANITY PICKS', CARD_WIDTH / 2, 246);

	ctx.fillStyle = color('--share-ink');
	ctx.font = `700 56px ${font}`;
	ctx.fillText(nickname ? fit(ctx, `${nickname}님의 인생템`, 856) : '나의 인생템', CARD_WIDTH / 2, 326);

	ctx.textAlign = 'left';
	const gap = 24;
	const bodyTop = products.length > 3 ? 404 : 380;
	const bodyHeight = height - 190 - bodyTop;
	const rowHeight = Math.min(products.length === 1 ? 320 : 250, (bodyHeight - (products.length - 1) * gap) / products.length);
	const listHeight = products.length * rowHeight + (products.length - 1) * gap;
	const top = bodyTop + (bodyHeight - listHeight) / 2;
	products.forEach((item, i) => {
		const y = top + i * (rowHeight + gap);
		const compact = products.length >= 4;
		ctx.fillStyle = color('--share-paper');
		roundRect(ctx, 72, y, CARD_WIDTH - 144, rowHeight, 26);
		ctx.fill();
		ctx.strokeStyle = color('--share-line');
		ctx.lineWidth = 2;
		ctx.stroke();

		ctx.fillStyle = color('--share-accent');
		ctx.font = `400 ${compact ? 40 : 48}px Georgia, serif`;
		ctx.fillText(String(i + 1).padStart(2, '0'), 104, y + rowHeight / 2 + 16);

		const product = item.product || {};
		const textX = 210;
		const textWidth = 758;
		const nameFont = compact ? 36 : 40;
		const lineHeight = compact ? 40 : 52;
		ctx.font = `700 ${nameFont}px ${font}`;
		const lines = productLines(ctx, product.name || productTitle(product), textWidth);
		const brandToName = compact ? 40 : 56;
		const blockHeight = 24 + brandToName + (lines.length - 1) * lineHeight + 10;
		const brandY = y + (rowHeight - blockHeight) / 2 + 24;
		ctx.fillStyle = color('--share-muted');
		ctx.font = `400 34px ${font}`;
		const usage = item.usageDays ? `${item.usageDays}일째` : '';
		const usageWidth = usage ? ctx.measureText(usage).width + 32 : 0;
		ctx.fillText(fit(ctx, product.brand || '', textWidth - usageWidth), textX, brandY);
		if (item.usageDays) {
			ctx.textAlign = 'right';
			ctx.fillText(usage, 968, brandY);
			ctx.textAlign = 'left';
		}
		ctx.fillStyle = color('--share-ink');
		ctx.font = `700 ${nameFont}px ${font}`;
		const nameY = brandY + brandToName;
		lines.forEach((line, lineIndex) => ctx.fillText(line, textX, nameY + lineIndex * lineHeight));
	});

	ctx.strokeStyle = color('--share-line');
	ctx.lineWidth = 2;
	ctx.beginPath();
	ctx.moveTo(96, height - 146);
	ctx.lineTo(CARD_WIDTH - 96, height - 146);
	ctx.stroke();
	ctx.textAlign = 'center';
	ctx.fillStyle = color('--share-muted');
	ctx.font = `400 34px ${font}`;
	ctx.fillText(date, CARD_WIDTH / 2, height - 88);
	ctx.restore();
}
