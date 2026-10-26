import { useId } from 'react';
import { Link } from 'react-router-dom';
import HomeLink from '../../components/HomeLink';
import { OCR_IMAGE_TYPES, isOcrImage } from '../../lib/ocr';
import { PhotoCrop, StepPanel, photoAiEnabled } from './addCommon';

// 오른쪽 칸 바로 시작(2026-10-26): 찾는 제품이 없을 때 여기서 바로 전성분 글을 붙여넣거나 캡처 이미지를 올린다.
// 이미지는 끌어다 놓기·Ctrl+V도 된다. 어느 쪽이든 붙여넣기 확인 화면으로 가고, 이미지는 거기서 브라우저 안에서 글자를 읽는다
function PasteStart({ busy, onPasteDirect }) {
	const fileId = useId();
	const start = (image) => {
		if (!busy) onPasteDirect(image || null);
	};
	const pickImage = (items) => Array.from(items || []).map((i) => (i.getAsFile ? i.getAsFile() : i)).find(isOcrImage);
	return (
		<div
			className="add-paste-start"
			onDragOver={(e) => e.preventDefault()}
			onDrop={(e) => {
				const image = pickImage(e.dataTransfer && e.dataTransfer.files);
				if (image) {
					e.preventDefault();
					start(image);
				}
			}}
			onPaste={(e) => {
				const image = pickImage(e.clipboardData && e.clipboardData.items);
				if (image) {
					e.preventDefault();
					start(image);
				}
			}}
		>
			<h2>찾는 제품이 없나요?</h2>
			<p className="auth-hint">
				상세페이지의 전성분 글을 붙여넣거나, 전성분 캡처 이미지를 올리면 글자를 읽어 채워 드려요. 캡처 이미지는 이 칸에 끌어다 놓거나
				클릭한 뒤 Ctrl+V로 붙여넣어도 돼요. 이미지는 서버로 보내지 않아요.
			</p>
			<div className="add-paste-actions">
				<button type="button" disabled={busy} onClick={() => start(null)}>
					전성분 글 붙여넣기
				</button>
				<input
					id={fileId}
					type="file"
					accept={OCR_IMAGE_TYPES.join(',')}
					className="visually-hidden"
					disabled={busy}
					onChange={(e) => {
						const file = e.target.files && e.target.files[0];
						e.target.value = '';
						if (isOcrImage(file)) start(file);
					}}
				/>
				<label htmlFor={fileId} className="add-paste-image" aria-disabled={busy}>
					캡처 이미지로 읽기
				</label>
			</div>
		</div>
	);
}

// PC: 왼쪽은 제품 찾기·파일 업로드 단계, 오른쪽은 사진 미리보기·자르기 (설계 7장).
// 성분 확인 단계는 확인 화면이 직접 사진과 성분 목록을 좌우로 놓는다
export default function AddProductDesktop(props) {
	const headingId = useId();
	const { step, previewUrl, onCrop } = props;
	return (
		<section data-layout="desktop" className="add-desktop" aria-labelledby={headingId}>
			<HomeLink />
			<div className="add-head">
				<h1 id={headingId}>제품 추가</h1>
				<Link to="/shelf">내 화장대</Link>
			</div>
			{step === 'confirm' ? (
				<StepPanel {...props} />
			) : (
				<div className="add-columns">
					<div className="add-left">
						<StepPanel {...props} />
					</div>
					<section className="add-right" aria-label={step === 'photo' || previewUrl ? '사진 미리보기' : '전성분으로 등록하기'}>
						{step === 'photo' && <PhotoCrop previewUrl={previewUrl} onCrop={onCrop} />}
						{step !== 'photo' && previewUrl && <img className="add-preview" src={previewUrl} alt="고른 사진" />}
						{step !== 'photo' && !previewUrl && (
							<>
								<PasteStart busy={props.busy} onPasteDirect={props.onPasteDirect} />
								{photoAiEnabled() && <p className="auth-hint">전성분 사진(AI)을 고르면 사진은 여기에 보여요.</p>}
							</>
						)}
					</section>
				</div>
			)}
		</section>
	);
}
