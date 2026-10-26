import { BrowserRouter, Navigate, Route, Routes, useLocation } from 'react-router-dom';
import { AuthProvider } from './AuthContext';
import ErrorBoundary from './components/ErrorBoundary';
import NetworkBanner from './components/NetworkBanner';
import RequireAuth from './components/RequireAuth';
import LoginPage from './pages/auth/LoginPage';
import SignupPage from './pages/auth/SignupPage';
import MePage from './pages/me/MePage';
import AddProductPage from './pages/add/AddProductPage';
import AdvicePage from './pages/advice/AdvicePage';
import ComparePage from './pages/compare/ComparePage';
import HomePage from './pages/home/HomePage';
import ProductDetailPage from './pages/product/ProductDetailPage';
import RoutineEditPage from './pages/routine/RoutineEditPage';
import RoutineListPage from './pages/routine/RoutineListPage';
import ShelfPage from './pages/shelf/ShelfPage';
import SkinAnalysisPage from './pages/skin/SkinAnalysisPage';
import PersonalColorPage from './pages/color/PersonalColorPage';
import VanityAnalysisPage from './pages/vanity/VanityAnalysisPage';
import StatsPage from './pages/stats/StatsPage';
import BeautyShell from './components/BeautyShell';
import AdminPage from './pages/admin/AdminPage';
import AdminReviewPage from './pages/admin/AdminReviewPage';
import DiaryPage from './pages/diary/DiaryPage';
import NotificationsPage from './pages/notifications/NotificationsPage';
import SharePage from './pages/share/SharePage';
import TrialsPage from './pages/trials/TrialsPage';

function protectedRoute(element) {
	return <RequireAuth>{element}</RequireAuth>;
}

export function AppRoutes() {
	const location = useLocation();
	return (
		<ErrorBoundary key={location.pathname}>
			<BeautyShell>
			<Routes>
				<Route path="/" element={<HomePage />} />
				<Route path="/login" element={<LoginPage />} />
				<Route path="/signup" element={<SignupPage />} />
				<Route path="/shelf" element={protectedRoute(<ShelfPage />)} />
				<Route path="/add" element={protectedRoute(<AddProductPage />)} />
				<Route path="/products/:id" element={protectedRoute(<ProductDetailPage />)} />
				<Route path="/skin" element={protectedRoute(<SkinAnalysisPage />)} />
				<Route path="/skin/analysis" element={<Navigate to="/skin" replace />} />
				<Route path="/personal-color" element={protectedRoute(<PersonalColorPage />)} />
				<Route path="/vanity/analysis" element={protectedRoute(<VanityAnalysisPage />)} />
				<Route path="/me" element={protectedRoute(<MePage />)} />
				<Route path="/routines" element={protectedRoute(<RoutineListPage />)} />
				<Route path="/routines/new" element={protectedRoute(<RoutineEditPage />)} />
				<Route path="/routines/:id" element={protectedRoute(<RoutineEditPage />)} />
				<Route path="/compare" element={protectedRoute(<ComparePage />)} />
				<Route path="/stats" element={protectedRoute(<StatsPage />)} />
				<Route path="/admin" element={protectedRoute(<AdminPage />)} />
				<Route path="/admin/review" element={protectedRoute(<AdminReviewPage />)} />
				<Route path="/advice" element={protectedRoute(<AdvicePage />)} />
				<Route path="/diary" element={protectedRoute(<DiaryPage />)} />
				<Route path="/notifications" element={protectedRoute(<NotificationsPage />)} />
				<Route path="/share" element={protectedRoute(<SharePage />)} />
				<Route path="/trials" element={protectedRoute(<TrialsPage />)} />
				<Route path="*" element={<Navigate to="/" replace />} />
			</Routes>
			</BeautyShell>
		</ErrorBoundary>
	);
}

function App() {
	return (
		<BrowserRouter future={{ v7_startTransition: true, v7_relativeSplatPath: true }}>
			<AuthProvider>
				<NetworkBanner />
				<main>
					<AppRoutes />
				</main>
			</AuthProvider>
		</BrowserRouter>
	);
}

export default App;
