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

// 폰: 성분을 한 줄씩 목록으로, 저장 버튼은 화면 아래에 고정 (설계 7장)
export default function ConfirmMobile(props) {
	const { result, pasteMode, restored, items, busy, error, onRetake, onAdd, onPaste, onSave, link, onLinkImport } = props;
	return (
		<div className="add-step confirm-mobile" data-layout="mobile" data-testid="confirm-layout">
			<h2>성분 확인</h2>
			{restored && <p role="status" className="auth-notice">임시 보관한 편집 내용을 불러왔어요.</p>}
			<ConfirmWarning result={result} onRetake={onRetake} />
			<LinkImport link={link} onImport={onLinkImport} onChange={props.onLinkChange} query={linkQuery(props.form)} />
			{props.productImageUrl && <img className="add-preview" src={props.productImageUrl} alt="상세페이지의 제품 사진" />}
			<SectionPicker sections={props.sections} onApply={props.onApplySection} onDismiss={props.onDismissSections} />
			{pasteMode && <PasteBox onPaste={onPaste} onUndo={(keys) => keys.forEach(props.onRemove)} busy={props.pasting} initialImage={props.initialImage} />}
			<IngredientList items={items} {...props} />
			<AddIngredient onAdd={onAdd} />
			<ProductInfoForm {...props} />
			<div className="confirm-savebar" data-testid="confirm-savebar">
				<NameCheckPanel {...props} />
				<ErrorText error={error} />
				<SaveButton busy={busy} onSave={onSave} />
			</div>
		</div>
	);
}
