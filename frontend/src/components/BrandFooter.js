import { Link } from 'react-router-dom';
import './BrandFooter.css';

export default function BrandFooter() {
 return <footer className="vanity-brand-footer">
  <Link to="/" aria-label="Vanity Check 홈"><img src={`${process.env.PUBLIC_URL}/brandboard/vanity-badge.svg`} alt="Vanity Check 원형 배지" width="600" height="600" /></Link>
  <div className="vanity-brand-footer-copy">
   <p>나를 알아가는 뷰티 습관</p>
   <small>문의 · 개인정보처리방침 · 이용약관 · © VANITY CHECK</small>
  </div>
 </footer>;
}
