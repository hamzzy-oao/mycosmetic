import {
	AddIngredient,
	ConfirmWarning,
	ErrorText,
	IngredientList,
	LinkImport,
	NameCheckPanel,
	linkQuery,
	PasteBox,
	ProductInfoForm,
	SaveButton,
	SectionPicker,
} from './confirmCommon';

// PC: 왼쪽 사진 미리보기, 오른쪽 성분 목록과 제품 정보 (설계 7장)
export default function ConfirmDesktop(props) {
	const { result, pasteMode, previewUrl, restored, items, busy, error, onRetake, onAdd, onPaste, onSave, link, onLinkImport } = props;
	return (
		<div className="confirm-desktop" data-layout="desktop" data-testid="confirm-layout">
			<section className="confirm-photo" aria-label="사진 미리보기">
				{previewUrl ? (
					<img className="add-preview" src={previewUrl} alt={pasteMode ? '상세페이지의 제품 사진' : '올린 전성분 사진'} />
				) : (
					<p className="auth-hint">
						{pasteMode ? '붙여넣은 성분을 확인해 주세요.' : '사진 미리보기가 없어요. 새로고침하면 사진은 다시 보이지 않아요.'}
					</p>
				)}
				<ConfirmWarning result={result} onRetake={onRetake} />
			</section>
			<div className="add-step confirm-main">
				<h2>성분 확인</h2>
				{restored && <p role="status" className="auth-notice">임시 보관한 편집 내용을 불러왔어요.</p>}
				<LinkImport link={link} onImport={onLinkImport} onChange={props.onLinkChange} query={linkQuery(props.form)} />
				<SectionPicker sections={props.sections} onApply={props.onApplySection} onDismiss={props.onDismissSections} />
				{pasteMode && <PasteBox onPaste={onPaste} onUndo={(keys) => keys.forEach(props.onRemove)} busy={props.pasting} initialImage={props.initialImage} />}
				<IngredientList items={items} {...props} />
				<AddIngredient onAdd={onAdd} />
				<ProductInfoForm {...props} />
				<NameCheckPanel {...props} />
				<ErrorText error={error} />
				<SaveButton busy={busy} onSave={onSave} />
			</div>
		</div>
	);
}
