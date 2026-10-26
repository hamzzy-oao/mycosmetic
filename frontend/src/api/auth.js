import { api } from './client';

// 로그인·회원 API (설계 5.1)

// 첫 POST(로그인·가입) 전에 XSRF-TOKEN 쿠키를 받아 둔다. 앱 시작 때 부른다
export function ensureCsrf() {
	return api.get('/api/auth/csrf');
}

export function signup({ email, password, nickname }) {
	return api.post('/api/auth/signup', { email, password, nickname }).then((res) => res.data);
}

// 가입 화면의 이메일 중복 확인 → { available }. 형식이 틀리면 400, 너무 자주 부르면 429
export function checkEmailAvailable(email) {
	return api.get('/api/auth/email-available', { params: { email } }).then((res) => res.data);
}

export function login({ email, password }) {
	return api.post('/api/auth/login', { email, password }).then((res) => res.data);
}

export function logout() {
	return api.post('/api/auth/logout');
}

export function me() {
	return api.get('/api/users/me').then((res) => res.data);
}

// 닉네임 변경 → 바뀐 내 정보(MeResponse). 가입과 같은 규칙: 앞뒤 공백 제외 1~20자
export function updateNickname(nickname) {
	return api.patch('/api/users/me', { nickname }).then((res) => res.data);
}

export function withdraw() {
	return api.delete('/api/users/me');
}

export function unlinkKakao() {
	return api.delete('/api/auth/social/kakao');
}

// 카카오 로그인은 화면 이동이라 CRA 개발 프록시를 거치지 않는다.
// 개발에서는 백엔드 주소로 직접, 운영에서는 같은 서버의 상대 경로로 보낸다
const OAUTH_BASE = process.env.NODE_ENV === 'development' ? 'http://localhost:8080' : '';

export function kakaoLoginUrl(link = false) {
	return OAUTH_BASE + '/oauth2/authorization/kakao' + (link ? '?link=true' : '');
}
