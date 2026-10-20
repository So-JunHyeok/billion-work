import { useEffect, useState } from 'react';
import { getJson } from './shared/api/client';

type Connection = { state: 'loading' | 'up' | 'down'; message: string };
type Info = { name: string; stage: string };

export default function App() {
  const [connection, setConnection] = useState<Connection>({ state: 'loading', message: '서버 연결 확인 중' });
  const [attempt, setAttempt] = useState(0);

  useEffect(() => {
    const controller = new AbortController();
    let timedOut = false;
    const timer = window.setTimeout(() => {
      timedOut = true;
      controller.abort();
    }, 10_000);
    setConnection({ state: 'loading', message: '서버 연결 확인 중' });
    Promise.all([
      getJson<Info>('/system/info', controller.signal),
      getJson<{ status: string }>('/system/health', controller.signal),
    ])
      .then(([info, health]) => {
        if (!controller.signal.aborted) setConnection(health.status === 'UP'
          ? { state: 'up', message: `${info.name} · 백엔드 및 DB 정상 연결` }
          : { state: 'down', message: 'DB 연결 상태를 확인해 주세요.' });
      })
      .catch(() => {
        if (timedOut) setConnection({ state: 'down', message: '응답 시간이 초과되었습니다. 서버 상태를 확인해 주세요.' });
        else if (!controller.signal.aborted) setConnection({ state: 'down', message: '백엔드에 연결할 수 없습니다. DB와 서버가 실행 중인지 확인해 주세요.' });
      })
      .finally(() => window.clearTimeout(timer));
    return () => { window.clearTimeout(timer); controller.abort(); };
  }, [attempt]);

  return (
    <div className="shell">
      <header><span className="brand">B.</span><strong>업무지원시스템</strong><span className="label">개발 시작 환경</span></header>
      <main>
        <p className="eyebrow">BILLION WORK / FOUNDATION</p>
        <h1>함께 일하는 흐름의 시작</h1>
        <p className="intro">프로젝트 안에서 업무를 전달하고, 처리 결과를 확인하는 내부 업무지원시스템입니다.</p>
        <section className={`connection ${connection.state}`} aria-live="polite">
          <div><span className="status-dot" /><strong>{connection.message}</strong></div>
          <button onClick={() => setAttempt(n => n + 1)} disabled={connection.state === 'loading'}>연결 다시 확인</button>
        </section>
        <div className="cards">
          <article><span>01</span><h2>프로젝트 중심</h2><p>직원이 프로젝트를 만들고 참여자와 업무를 공유합니다.</p></article>
          <article><span>02</span><h2>완료까지 명확하게</h2><p>담당자가 결과를 제출하고 요청자가 승인하면 최종 완료됩니다.</p></article>
          <article><span>03</span><h2>한눈에 보는 현황</h2><p>진행 상황과 검토 대기를 구분해 함께 확인합니다.</p></article>
        </div>
        <section className="next"><h2>현재 구현 범위</h2><p>React 실행 환경 · Spring Boot 연결 · MariaDB 초기 테이블 생성</p><p>로그인, 프로젝트 관리, 업무 등록·승인 화면은 다음 단계에서 구현합니다.</p></section>
        <footer>React + TypeScript · Spring Boot + JPA · MariaDB</footer>
      </main>
    </div>
  );
}
