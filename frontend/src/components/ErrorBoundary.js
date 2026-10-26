import { Component } from 'react';

// 화면 단위 오류 경계 (설계 6.4). App에서 화면 주소를 key로 줘서 이동하면 오류 상태가 지워진다
export default class ErrorBoundary extends Component {
	constructor(props) {
		super(props);
		this.state = { hasError: false };
	}

	static getDerivedStateFromError() {
		return { hasError: true };
	}

	componentDidCatch(error) {
		// 사용자 데이터가 섞일 수 있어 메시지만 남긴다
		console.error('화면 오류', error && error.message);
	}

	render() {
		if (this.state.hasError) {
			return (
				<div role="alert" className="error-boundary">
					<p>화면을 보여주는 중에 문제가 생겼어요.</p>
					<button type="button" onClick={() => window.location.reload()}>
						다시 불러오기
					</button>
				</div>
			);
		}
		return this.props.children;
	}
}
