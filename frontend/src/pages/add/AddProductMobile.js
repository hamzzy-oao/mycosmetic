import { useId } from 'react';
import { Link } from 'react-router-dom';
import HomeLink from '../../components/HomeLink';
import { PhotoCrop, StepPanel } from './addCommon';

// 폰: 제품 찾기 → 사진 촬영 → 확인을 단계별 전체 화면으로 (설계 7장)
export default function AddProductMobile(props) {
	const headingId = useId();
	return (
		<section data-layout="mobile" className="add-mobile" aria-labelledby={headingId}>
			<HomeLink />
			<div className="add-head">
				<h1 id={headingId}>제품 추가</h1>
				<Link to="/shelf">내 화장대</Link>
			</div>
			<StepPanel {...props} />
			{props.step === 'photo' && <PhotoCrop previewUrl={props.previewUrl} onCrop={props.onCrop} />}
		</section>
	);
}
