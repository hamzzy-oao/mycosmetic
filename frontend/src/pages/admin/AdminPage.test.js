import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { useAuth } from '../../AuthContext';
import { api } from '../../api/client';
import AdminPage from './AdminPage';

jest.mock('../../AuthContext', () => ({ useAuth: jest.fn() }));
jest.mock('../../api/client', () => ({
 api: { get: jest.fn(), delete: jest.fn() },
}));

const PRODUCT = {
 id: 7, brand: '아비브', name: '수분 크림', volume: '50ml', category: 'SKINCARE',
};
const OTHER_PRODUCT = { ...PRODUCT, id: 8, name: '수분 토너' };

function renderAdmin() {
 return render(
  <MemoryRouter future={{ v7_startTransition: true, v7_relativeSplatPath: true }}>
   <AdminPage />
  </MemoryRouter>
 );
}

beforeEach(() => {
 jest.resetAllMocks();
 useAuth.mockReturnValue({ user: { role: 'ADMIN' } });
});

test('검색 결과 개수와 별개로 전체 등록 제품 수를 표시한다', async () => {
 api.get.mockImplementation(url => Promise.resolve({
  data: url.endsWith('/count') ? { count: 1234 } : [PRODUCT],
 }));
 renderAdmin();

 expect(await screen.findByText('1,234개')).toBeInTheDocument();
 expect(screen.getByText(PRODUCT.name)).toBeInTheDocument();
 fireEvent.change(screen.getByLabelText('제품 검색'), { target: { value: '수분' } });
 fireEvent.click(screen.getByRole('button', { name: '검색' }));

 await waitFor(() => expect(api.get).toHaveBeenCalledWith('/api/admin/products', {
  params: { q: '수분', page: 0 },
 }));
 expect(screen.getByText('1,234개')).toBeInTheDocument();
 expect(api.get.mock.calls.filter(([url]) => url.endsWith('/count'))).toHaveLength(1);
});

test('등록된 제품이 없으면 0개를 표시한다', async () => {
 api.get.mockImplementation(url => Promise.resolve({
  data: url.endsWith('/count') ? { count: 0 } : [],
 }));
 renderAdmin();

 expect(await screen.findByText('0개')).toBeInTheDocument();
 expect(screen.getByText('제품이 없습니다.')).toBeInTheDocument();
});

test('개수 조회 실패를 0개로 표시하지 않고 제품 목록은 유지한다', async () => {
 api.get.mockImplementation(url => url.endsWith('/count')
  ? Promise.reject(new Error('count unavailable'))
  : Promise.resolve({ data: [PRODUCT] }));
 renderAdmin();

 expect(await screen.findByText('등록 제품 수를 불러오지 못했습니다.')).toBeInTheDocument();
 expect(screen.queryByText('0개')).not.toBeInTheDocument();
 expect(screen.getByText(PRODUCT.name)).toBeInTheDocument();
});

test('제품을 삭제하면 전체 등록 수를 다시 조회한다', async () => {
 let products = [PRODUCT, OTHER_PRODUCT];
 api.get.mockImplementation(url => {
  if (url.endsWith('/count')) return Promise.resolve({ data: { count: products.length } });
  if (url.endsWith(`/${PRODUCT.id}`)) {
   return Promise.resolve({ data: { product: PRODUCT, representative: null } });
  }
  return Promise.resolve({ data: products });
 });
 api.delete.mockImplementation(() => {
  products = [OTHER_PRODUCT];
  return Promise.resolve();
 });
 const confirm = jest.spyOn(window, 'confirm').mockReturnValue(true);
 try {
  renderAdmin();
  expect(await screen.findByText('2개')).toBeInTheDocument();
  fireEvent.click(screen.getByRole('button', { name: `${PRODUCT.brand} ${PRODUCT.name}` }));
  fireEvent.click(await screen.findByRole('button', { name: '제품 삭제' }));

  expect(await screen.findByText('1개')).toBeInTheDocument();
  expect(api.delete).toHaveBeenCalledWith(`/api/admin/products/${PRODUCT.id}`);
  expect(screen.queryByText(PRODUCT.name)).not.toBeInTheDocument();
 } finally {
  confirm.mockRestore();
 }
});
