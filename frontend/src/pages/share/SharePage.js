import { useEffect, useMemo, useRef, useState } from 'react';
import { Link } from 'react-router-dom';
import { getShelf } from '../../api/shelf';
import { useAuth } from '../../AuthContext';
import { errorMessage } from '../../lib/errorMessage';
import { productTitle, todayString } from '../shelf/shelfCommon';
import { CARD_WIDTH, MAX_ITEMS, drawShareCard, getCardHeight, loadShareCardLogo } from './drawShareCard';
import './SharePage.css';

// 화장대 자랑 카드 (설계 2026-10-24 D): 맞음 제품을 골라 이미지로 저장·공유한다. 서버에 올리지 않는다
function canShareFiles() {
	return typeof navigator !== 'undefined' && typeof navigator.share === 'function' && typeof navigator.canShare === 'function';
}

function toBlob(canvas) {
	return new Promise((resolve) => {
		if (!canvas || typeof canvas.toBlob !== 'function') {
			resolve(null);
			return;
		}
		canvas.toBlob((blob) => resolve(blob), 'image/png');
	});
}

export default function SharePage() {
	const { user } = useAuth();
	const [goods, setGoods] = useState(null);
	const [selected, setSelected] = useState([]);
	const [error, setError] = useState('');
	const [notice, setNotice] = useState('');
	const [cardReady, setCardReady] = useState(false);
	const [cardError, setCardError] = useState('');
	const [cardAttempt, setCardAttempt] = useState(0);
	const canvasRef = useRef(null);
	const today = todayString();

	useEffect(() => {
		let cancelled = false;
		getShelf()
			.then((items) => {
				if (cancelled) return;
				const good = (items || []).filter((i) => i.verdict === 'GOOD');
				setGoods(good);
				setSelected(good.slice(0, MAX_ITEMS).map((i) => i.id));
			})
			.catch((e) => !cancelled && setError(errorMessage(e)));
		return () => {
			cancelled = true;
		};
	}, []);

	const picked = useMemo(
		() => (goods || []).filter((i) => selected.includes(i.id)),
		[goods, selected]
	);

	useEffect(() => {
		let cancelled = false;
		setCardReady(false);
		setCardError('');
		const canvas = canvasRef.current;
		const ctx = canvas && canvas.getContext ? canvas.getContext('2d') : null;
		if (!ctx || picked.length === 0) return;
		Promise.all([loadShareCardLogo(), document.fonts && document.fonts.ready])
			.then(([logo]) => {
				if (cancelled) return;
				drawShareCard(ctx, { items: picked, date: today, nickname: user && user.nickname, logo });
				setCardReady(true);
			})
			.catch(() => {
				if (!cancelled) setCardError('로고를 불러오지 못했어요. 잠시 후 다시 시도해 주세요.');
			});
		return () => { cancelled = true; };
	}, [picked, today, user, cardAttempt]);

	const toggle = (id) => {
		setCardReady(false);
		setNotice('');
		setSelected((s) => (s.includes(id) ? s.filter((x) => x !== id) : s.length < MAX_ITEMS ? [...s, id] : s));
	};

	const fileName = `vanity-card-${today}.png`;

	const save = async () => {
		if (!cardReady) return;
		setNotice('');
		const blob = await toBlob(canvasRef.current);
		if (!blob) {
			setError('이미지를 만들지 못했어요. 다른 브라우저에서 다시 시도해 주세요.');
			return;
		}
		const url = URL.createObjectURL(blob);
		const a = document.createElement('a');
		a.href = url;
		a.download = fileName;
		a.click();
		URL.revokeObjectURL(url);
		setNotice('이미지를 저장했어요.');
	};

	const share = async () => {
		if (!cardReady) return;
		setNotice('');
		const blob = await toBlob(canvasRef.current);
		if (!blob) return;
		const file = new File([blob], fileName, { type: 'image/png' });
		if (!navigator.canShare({ files: [file] })) {
			setError('이 브라우저에서는 이미지 공유를 할 수 없어요. 이미지 저장을 써 주세요.');
			return;
		}
		try {
			await navigator.share({ files: [file], title: '나의 인생템' });
		} catch (e) {
			// 사용자가 공유 창을 닫은 경우는 알리지 않는다
		}
	};

	return (
		<section className="share-page" aria-labelledby="share-title">
			<h1 id="share-title" className="visually-hidden">
				화장대 자랑 카드
			</h1>
			{(error || cardError) && (
				<p role="alert" className="share-error">
					{error || cardError}
				</p>
			)}
			{!goods && !error && <p aria-busy="true">불러오는 중...</p>}
			{goods && goods.length === 0 && (
				<p className="share-empty">
					아직 맞음으로 평가한 제품이 없어요. <Link to="/shelf">화장대에서 평가하기</Link>
				</p>
			)}
			{goods && goods.length > 0 && (
				<div className="share-layout">
					<fieldset className="share-card">
						<legend>
							카드에 넣을 제품 <span className="share-count">{selected.length}/{MAX_ITEMS}</span>
						</legend>
						<ul className="share-picks">
							{goods.map((item) => {
								const checked = selected.includes(item.id);
								return (
									<li key={item.id} className={checked ? 'is-selected' : ''}>
										<label>
											<input
												type="checkbox"
												checked={checked}
												disabled={!checked && selected.length >= MAX_ITEMS}
												onChange={() => toggle(item.id)}
											/>
											{productTitle(item.product)}
										</label>
									</li>
								);
							})}
						</ul>
						<p className="share-hint">나에게 잘 맞는 제품을 최대 5개까지 골라 담아 보세요.</p>
					</fieldset>
					<div className="share-card share-preview">
						{picked.length === 0 ? (
							<p className="share-hint">제품을 하나 이상 골라 주세요.</p>
						) : (
							<canvas
								ref={canvasRef}
								width={CARD_WIDTH}
								height={getCardHeight(picked.length)}
								role="img"
								aria-busy={!cardReady}
								aria-label={`나의 인생템 카드: ${picked.map((i) => productTitle(i.product)).join(', ')}`}
							/>
						)}
						{picked.length > 0 && !cardReady && !cardError && <p role="status" className="share-hint">카드를 만들고 있어요…</p>}
						{cardError && <button type="button" onClick={() => setCardAttempt((attempt) => attempt + 1)}>다시 시도</button>}
						{notice && (
							<p role="status" className="share-notice">
								{notice}
							</p>
						)}
						<div className="share-actions">
							<button type="button" className="share-primary" disabled={!cardReady} onClick={save}>
								이미지 저장
							</button>
							{canShareFiles() && (
								<button type="button" disabled={!cardReady} onClick={share}>
									공유하기
								</button>
							)}
						</div>
					</div>
				</div>
			)}
		</section>
	);
}
