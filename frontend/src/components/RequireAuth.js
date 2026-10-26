import { Navigate, useLocation } from 'react-router-dom';
import { useAuth } from '../AuthContext';

// 로그인이 필요한 화면. 로그인하지 않았으면 원래 주소를 기억하고 로그인 화면으로 보낸다
export default function RequireAuth({ children }) {
	const { user, loading } = useAuth();
	const location = useLocation();

	if (loading) {
		return <p aria-busy="true">불러오는 중...</p>;
	}
	if (!user) {
		return <Navigate to="/login" replace state={{ from: location }} />;
	}
	return children;
}
