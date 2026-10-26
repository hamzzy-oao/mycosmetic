import { createContext, useCallback, useContext, useEffect, useMemo, useState } from 'react';
import { ensureCsrf, logout as logoutApi, me } from './api/auth';
import { setAuthFailureHandler } from './api/client';

const AuthContext = createContext(null);

// 로그인한 사용자 상태. 앱 시작 때 CSRF 쿠키를 받고 내 정보를 불러온다.
// 토큰 갱신이 실패하면 user를 비우고, RequireAuth가 로그인 화면으로 보낸다
export function AuthProvider({ children }) {
	const [user, setUser] = useState(null);
	const [loading, setLoading] = useState(true);

	const reloadUser = useCallback(async () => {
		try {
			const data = await me();
			setUser(data);
			return data;
		} catch (e) {
			setUser(null);
			return null;
		}
	}, []);

	useEffect(() => {
		setAuthFailureHandler(() => setUser(null));
		let cancelled = false;
		(async () => {
			try {
				await ensureCsrf();
			} catch (e) {
				// CSRF 쿠키를 못 받아도 화면은 띄운다. 이후 POST가 403으로 실패한다
			}
			try {
				const data = await me();
				if (!cancelled) setUser(data);
			} catch (e) {
				if (!cancelled) setUser(null);
			} finally {
				if (!cancelled) setLoading(false);
			}
		})();
		return () => {
			cancelled = true;
			setAuthFailureHandler(null);
		};
	}, []);

	const logout = useCallback(async () => {
		try {
			await logoutApi();
		} finally {
			setUser(null);
		}
	}, []);

	const value = useMemo(
		() => ({ user, loading, setUser, reloadUser, logout }),
		[user, loading, reloadUser, logout]
	);

	return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth() {
	const ctx = useContext(AuthContext);
	if (!ctx) {
		throw new Error('useAuth는 AuthProvider 안에서만 쓸 수 있어요');
	}
	return ctx;
}
