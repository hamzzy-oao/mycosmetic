import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { useAuth } from '../../AuthContext';
import { api } from '../../api/client';
import { errorMessage } from '../../lib/errorMessage';
import './AdminPage.css';

function ProductManager() {
 const [query,setQuery]=useState('');
 const [filter,setFilter]=useState('');
 const [page,setPage]=useState(0);
 const [products,setProducts]=useState([]);
 const [selected,setSelected]=useState(null);
 const [text,setText]=useState('');
 const [error,setError]=useState('');
 const [notice,setNotice]=useState('');
 const [loading,setLoading]=useState(false);
 const [busy,setBusy]=useState(false);
 const [info,setInfo]=useState({brand:'',name:'',volume:'',category:'SKINCARE'});
 const [revision,setRevision]=useState(0);
 const [productCount,setProductCount]=useState(null);
 const [countError,setCountError]=useState(false);
 useEffect(() => {
  let active=true;
  setProductCount(null);setCountError(false);
  api.get('/api/admin/products/count').then(({data})=>{if(active)setProductCount(data.count);})
   .catch(()=>{if(active)setCountError(true);});
  return ()=>{active=false;};
 },[revision]);
 useEffect(() => {
  let active=true;
  setLoading(true); setError('');
  api.get('/api/admin/products',{params:{q:filter,page}}).then(({data})=>{if(active)setProducts(data);})
   .catch(e=>{if(active){setError(errorMessage(e));setProducts([]);}}).finally(()=>{if(active)setLoading(false);});
  return ()=>{active=false;};
 },[filter,page,revision]);
 const select=async(id)=>{
  setBusy(true);setError('');setNotice('');setSelected(null);
  try { const {data}=await api.get(`/api/admin/products/${id}`);setSelected(data);setInfo({brand:data.product.brand,name:data.product.name,volume:data.product.volume||'',category:data.product.category});setText((data.representative?.items||[]).map(i=>i.name).join('\n')); }
  catch(e){setError(errorMessage(e));}finally{setBusy(false);}
 };
 const saveInfo=async(e)=>{
  e.preventDefault();setBusy(true);setError('');setNotice('');
  try { const {data}=await api.put(`/api/admin/products/${selected.product.id}`,{...info,brand:info.brand.trim(),name:info.name.trim(),volume:info.volume.trim()});setSelected(data);setRevision(r=>r+1);setNotice('제품 정보를 저장했습니다.'); }
  catch(e){setError(errorMessage(e));}finally{setBusy(false);}
 };
 const remove=async()=>{
  if(!window.confirm(`${selected.product.brand} ${selected.product.name}\n이 제품을 삭제할까요? 검색·관리 목록에서 제외되며 기존 화장대 기록은 유지됩니다.`))return;
  setBusy(true);setError('');setNotice('');
  try {await api.delete(`/api/admin/products/${selected.product.id}`);setSelected(null);setRevision(r=>r+1);setNotice('제품을 삭제했습니다.');}
  catch(e){setError(errorMessage(e));}finally{setBusy(false);}
 };
 const save=async(e)=>{
  e.preventDefault();setError('');setNotice('');
  const ingredients=text.split('\n').map(s=>s.trim()).filter(Boolean);
  if(!ingredients.length||ingredients.length>200||ingredients.some(s=>s.length>500)) {setError('성분은 한 줄에 하나씩, 최대 200개까지 입력하세요.');return;}
  setBusy(true);
  try { const {data}=await api.put(`/api/admin/products/${selected.product.id}/ingredients`,{baseListId:selected.representative?.id??null,ingredients});setSelected(data);setText(data.representative.items.map(i=>i.name).join('\n'));setNotice('대표 성분을 저장했습니다.'); }
  catch(e){setError(errorMessage(e));}finally{setBusy(false);}
 };
 return <section className="admin-page" aria-labelledby="admin-title">
  <div className="admin-page-heading"><h1 id="admin-title">제품·성분 관리</h1>
   <p className="admin-product-count" aria-live="polite">{countError?'등록 제품 수를 불러오지 못했습니다.':<>현재 등록된 제품 <strong>{productCount===null?'불러오는 중...':`${productCount.toLocaleString('ko-KR')}개`}</strong></>}</p>
  </div>
  <form className="admin-search" onSubmit={e=>{e.preventDefault();setFilter(query.trim());setPage(0);}}>
   <label htmlFor="admin-query">제품 검색</label><div><input id="admin-query" value={query} maxLength={100} onChange={e=>setQuery(e.target.value)} placeholder="브랜드 또는 제품명"/><button type="submit" disabled={busy}>검색</button></div>
  </form>
  {error&&<p role="alert" className="auth-error">{error}</p>}{notice&&<p role="status" className="auth-notice">{notice}</p>}
  <div className="admin-grid"><div className="admin-products"><h2>제품 목록</h2>
   {loading?<p aria-busy="true">불러오는 중...</p>:products.length?<ul>{products.map(p=><li key={p.id}><button type="button" disabled={busy} aria-pressed={selected?.product.id===p.id} onClick={()=>select(p.id)}><span>{p.brand}</span><strong>{p.name}</strong></button></li>)}</ul>:<p>제품이 없습니다.</p>}
   <nav aria-label="제품 목록 페이지"><button disabled={page===0||loading||busy} onClick={()=>setPage(p=>p-1)}>이전</button><span>{page+1}</span><button disabled={products.length<20||loading||busy} onClick={()=>setPage(p=>p+1)}>다음</button></nav>
  </div><div className="admin-editor">
   {selected?<><form onSubmit={saveInfo} className="admin-info-form"><h2>제품 정보</h2>
    <label htmlFor="admin-brand">브랜드</label><input id="admin-brand" required maxLength={100} value={info.brand} disabled={busy} onChange={e=>setInfo({...info,brand:e.target.value})}/>
    <label htmlFor="admin-name">제품명</label><input id="admin-name" required maxLength={200} value={info.name} disabled={busy} onChange={e=>setInfo({...info,name:e.target.value})}/>
    <div className="admin-info-row"><div><label htmlFor="admin-volume">용량</label><input id="admin-volume" maxLength={50} value={info.volume} disabled={busy} onChange={e=>setInfo({...info,volume:e.target.value})}/></div>
    <div><label htmlFor="admin-category">분류</label><select id="admin-category" value={info.category} disabled={busy} onChange={e=>setInfo({...info,category:e.target.value})}><option value="SKINCARE">스킨케어</option><option value="MAKEUP">메이크업</option></select></div></div>
    <div className="admin-actions"><button type="button" className="admin-delete" disabled={busy} onClick={remove}>제품 삭제</button><button type="submit" disabled={busy}>제품 정보 저장</button></div>
   </form><form onSubmit={save}><h2>성분 수정</h2><p>수정 내용은 새 대표 버전으로 저장됩니다. 기존 화장대 기록은 유지됩니다.</p>
    <label htmlFor="admin-ingredients">전성분 · 한 줄에 하나</label><textarea id="admin-ingredients" rows={18} value={text} onChange={e=>setText(e.target.value)} disabled={busy}/>
    <div className="admin-actions"><Link to={`/products/${selected.product.id}`}>제품 상세</Link><button type="submit" disabled={busy}>{busy?'저장 중...':'대표 성분 저장'}</button></div>
   </form></>:<p>{busy?'불러오는 중...':'수정할 제품을 선택하세요.'}</p>}
  </div></div>
 </section>;
}
export default function AdminPage() {
 const {user}=useAuth();
 return user?.role==='ADMIN'?<ProductManager/>:<section className="admin-page"><h1>접근 권한이 없습니다.</h1><Link to="/shelf">내 화장대</Link></section>;
}
