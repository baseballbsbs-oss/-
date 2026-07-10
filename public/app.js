'use strict';

/* ============================ 클라이언트 식별자 ============================ */
let clientId = localStorage.getItem('client_id');
if (!clientId) {
  clientId = (crypto.randomUUID && crypto.randomUUID()) || (Date.now() + '-' + Math.random().toString(36).slice(2));
  localStorage.setItem('client_id', clientId);
}

/* ============================ API 헬퍼 ============================ */
const api = {
  async req(method, url, body) {
    const opt = { method, headers: { 'Content-Type': 'application/json', 'X-Client-Id': clientId } };
    if (body !== undefined) opt.body = JSON.stringify(body);
    const res = await fetch(url, opt);
    if (res.status === 401) {
      showLogin();
      throw new Error('인증이 필요합니다.');
    }
    if (!res.ok) {
      const e = await res.json().catch(() => ({ error: '요청 실패' }));
      throw new Error(e.error || '요청 실패');
    }
    return res.status === 204 ? null : res.json();
  },
  get: (u) => api.req('GET', u),
  post: (u, b) => api.req('POST', u, b),
  put: (u, b) => api.req('PUT', u, b),
  del: (u) => api.req('DELETE', u),
};

/* ============================ 상태 ============================ */
const state = {
  projects: [],
  currentProjectId: null,
  menus: [],
  members: [],
  me: localStorage.getItem('me_name') || '',
  swipe: null, // { menu, tasks, index }
  isAdmin: false,
  adminEnabled: false,
  reportRows: [],
  reportGroup: 'date',
  reportFrom: '',
  reportTo: '',
};

const MEMBER_COLORS = ['#4f8cff', '#38bdf8', '#22c55e', '#f59e0b', '#ef4444', '#a855f7', '#ec4899', '#14b8a6'];

// 작업오더 선택 옵션
const SAFETY_FACTORS = ['고온/고압', '유해/위험물질', '고소작업', '분진/비산', '밀폐공간', '화재폭발', '소음지역', '중량물'];
const FME_GRADES = ['ZONE 1', 'ZONE 2', 'ZONE 3', 'STEP 1', 'STEP 2', 'STEP 3'];
const QUALITY_GRADES = ['Q', 'A', 'S'];
const QUALITY_WITNESS = ['V-INSP', 'KV-INSP', 'KPS-INSP', 'KPS-DOC', 'K-DOC2'];
const HEAVY = '중량물';

// 일별 기록: 당일 위험요인 (고소작업=비계) + 안전등급 산정
const LOG_HAZARDS = ['고소작업', '고온/고압', '분진/비산', '밀폐공간', '화재폭발'];
const LOG_HAZARD_LABEL = { '고소작업': '고소작업 (비계)' };
const parseTons = (s) => { const m = String(s || '').match(/([\d.]+)/); if (!m) return 0; let n = parseFloat(m[1]) || 0; if (/kg/i.test(String(s))) n /= 1000; return n; };
function computeGrade(hazardsSet, heavyItems) {
  const maxW = (heavyItems || []).reduce((mx, it) => Math.max(mx, parseTons(it && it.weight)), 0);
  if (maxW >= 3) return 'A';
  if (hazardsSet.has('고온/고압') || hazardsSet.has('분진/비산') || hazardsSet.has('밀폐공간') || (maxW >= 1 && maxW < 3)) return 'B';
  if (hazardsSet.has('고소작업') || hazardsSet.has('화재폭발') || (maxW > 0 && maxW < 1)) return 'C';
  return '';
}
const parseHandled = (s) => { try { const a = JSON.parse(s || '[]'); return Array.isArray(a) ? a : []; } catch { return []; } };

// hazard_factors(콤마 문자열) ↔ 배열
const parseFactors = (s) => (s ? String(s).split(',').map((x) => x.trim()).filter(Boolean) : []);
// lifting_gear(JSON 배열 문자열) ↔ 배열  (구버전 호환)
const parseGear = (s) => { try { const a = JSON.parse(s || '[]'); return Array.isArray(a) ? a : []; } catch { return []; } };
// heavy_items(JSON: [{name,weight,gear}]) ↔ 배열
const parseHeavyItems = (s) => { try { const a = JSON.parse(s || '[]'); return Array.isArray(a) ? a : []; } catch { return []; } };
// 구버전(heavy_weight/lifting_gear) → 신버전 항목으로 변환
function migrateHeavy(t) {
  const items = parseHeavyItems(t.heavy_items);
  if (items.length) return items;
  const g = parseGear(t.lifting_gear);
  if (t.heavy_weight || g.length) return [{ name: '', weight: t.heavy_weight || '', gear: g.join(', ') }];
  return [];
}

/* ============================ 유틸 ============================ */
const $ = (id) => document.getElementById(id);
const el = (tag, cls, html) => {
  const e = document.createElement(tag);
  if (cls) e.className = cls;
  if (html !== undefined) e.innerHTML = html;
  return e;
};
const esc = (s) => String(s ?? '').replace(/[&<>"']/g, (c) =>
  ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));
const todayStr = () => {
  const d = new Date();
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
};

function toast(msg) {
  const t = $('toast');
  t.textContent = msg;
  t.classList.remove('hidden');
  clearTimeout(toast._t);
  toast._t = setTimeout(() => t.classList.add('hidden'), 2200);
}

/* ============================ 모달 ============================ */
function openModal(html) {
  $('modal').innerHTML = html;
  $('modal-backdrop').classList.remove('hidden');
}
function closeModal() { $('modal-backdrop').classList.add('hidden'); }
$('modal-backdrop').addEventListener('click', (e) => {
  if (e.target === $('modal-backdrop')) closeModal();
});

/* ============================ 로그인 ============================ */
function showLogin() {
  $('login-gate').classList.remove('hidden');
  const pw = $('login-pw');
  pw.value = '';
  pw.focus();
}
function hideLogin() { $('login-gate').classList.add('hidden'); }
function bindLogin() {
  const submit = async () => {
    const err = $('login-err');
    err.textContent = '';
    try {
      const res = await fetch('/api/login', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ password: $('login-pw').value }),
      });
      if (!res.ok) {
        const e = await res.json().catch(() => ({}));
        err.textContent = e.error || '로그인 실패';
        return;
      }
      const data = await res.json().catch(() => ({}));
      hideLogin();
      await refreshSession();
      await startApp();
      if (data.role === 'admin') toast('관리자로 로그인했습니다.');
    } catch {
      err.textContent = '네트워크 오류';
    }
  };
  $('login-btn').onclick = submit;
  $('login-pw').addEventListener('keydown', (e) => { if (e.key === 'Enter') submit(); });
}

/* ============================ 초기화 ============================ */
async function refreshSession() {
  const s = await fetch('/api/session').then((r) => r.json())
    .catch(() => ({ auth_enabled: false, authed: true, is_admin: true, admin_enabled: false }));
  state.isAdmin = !!s.is_admin;
  state.adminEnabled = !!s.admin_enabled;
  updateWhoami();
  return s;
}
async function init() {
  bindLogin();
  bindStaticEvents();
  const s = await refreshSession();
  if (s.auth_enabled && !s.authed) {
    showLogin();
    return;
  }
  await startApp();
}

async function startApp() {
  updateWhoami();
  await loadMembers();
  await loadProjects();
  if (!state.me) openMemberModal();
}

function bindStaticEvents() {
  $('whoami').onclick = openMemberModal;
  $('add-project').onclick = openProjectModal;
  $('del-project').onclick = deleteCurrentProject;
  $('project-select').onchange = (e) => selectProject(Number(e.target.value));
  $('add-menu').onclick = () => openMenuModal();
  $('report-btn').onclick = openReport;
  $('report-back').onclick = closeReport;
  $('report-export').onclick = () => {
    if (!state.currentProjectId) return;
    const q = new URLSearchParams({ group: state.reportGroup });
    if (state.reportFrom) q.set('from', state.reportFrom);
    if (state.reportTo) q.set('to', state.reportTo);
    downloadUrl('/api/projects/' + state.currentProjectId + '/daily.xlsx?' + q.toString());
  };
  document.querySelectorAll('#report-tabs button').forEach((b) => {
    b.onclick = () => {
      state.reportGroup = b.dataset.g;
      document.querySelectorAll('#report-tabs button').forEach((x) => x.classList.toggle('active', x === b));
      renderReport();
    };
  });
  $('report-from').onchange = (e) => { state.reportFrom = e.target.value; renderReport(); };
  $('report-to').onchange = (e) => { state.reportTo = e.target.value; renderReport(); };
  $('report-clear').onclick = () => {
    state.reportFrom = ''; state.reportTo = '';
    $('report-from').value = ''; $('report-to').value = '';
    renderReport();
  };
  $('swipe-back').onclick = closeSwipe;
  $('swipe-edit').onclick = () => {
    const s = state.swipe;
    if (s) openTaskModal(s.menu, s.tasks[s.index]);
  };
}

/* ============================ 멤버(사용자 8명) ============================ */
async function loadMembers() {
  state.members = await api.get('/api/members');
}
function updateWhoami() {
  $('whoami-name').textContent = (state.isAdmin ? '🛡 ' : '') + (state.me || '미지정');
}
function openMemberModal() {
  const rows = state.members.map((m) => `
    <div class="member-item ${m.name === state.me ? 'active' : ''}" data-name="${esc(m.name)}">
      <span class="member-dot" style="background:${esc(m.color)}"></span>
      <span class="member-name">${esc(m.name)}</span>
      <button class="mini-btn danger" data-del="${m.id}">🗑</button>
    </div>`).join('');
  openModal(`
    <h2>사용자 선택 (파트원)</h2>
    <p style="color:var(--muted);font-size:13px;margin-top:-8px">기록 시 작성자로 표시됩니다. 최대 8명.</p>
    <div class="member-list">${rows || '<div class="empty-hint">등록된 파트원이 없습니다.</div>'}</div>
    ${state.members.length < 8 ? `
    <div class="field">
      <label>파트원 추가</label>
      <input id="new-member" placeholder="이름 입력 후 추가" />
    </div>
    <button class="btn btn-ghost" id="add-member-btn">＋ 파트원 추가</button>` : ''}
    ${state.isAdmin ? `
    <div class="admin-panel">
      <div class="admin-title">🛡 관리자 메뉴</div>
      <button class="btn btn-ghost" id="export-btn">📥 데이터 내보내기 (엑셀/CSV)</button>
    </div>` : (state.adminEnabled ? '<p style="color:var(--muted);font-size:12px;text-align:center">관리자는 로그아웃 후 관리자 비밀번호로 로그인하세요.</p>' : '')}
    <div class="modal-actions">
      <button class="btn btn-ghost" id="logout-btn" style="flex:0 0 auto">로그아웃</button>
      <button class="btn btn-primary" id="close-member">확인</button>
    </div>
  `);
  $('close-member').onclick = closeModal;
  const exp = $('export-btn');
  if (exp) exp.onclick = exportData;
  $('logout-btn').onclick = async () => {
    await fetch('/api/logout', { method: 'POST' });
    state.isAdmin = false;
    closeModal();
    showLogin();
  };
  const addBtn = $('add-member-btn');
  if (addBtn) addBtn.onclick = addMember;
  document.querySelectorAll('.member-item').forEach((it) => {
    it.onclick = (e) => {
      if (e.target.dataset.del) return;
      state.me = it.dataset.name;
      localStorage.setItem('me_name', state.me);
      updateWhoami();
      closeModal();
      toast(`${state.me} 님으로 설정되었습니다.`);
    };
  });
  document.querySelectorAll('[data-del]').forEach((b) => {
    b.onclick = async (e) => {
      e.stopPropagation();
      await api.del('/api/members/' + b.dataset.del);
      await loadMembers();
      openMemberModal();
    };
  });
}
async function addMember() {
  const name = $('new-member').value.trim();
  if (!name) return;
  const color = MEMBER_COLORS[state.members.length % MEMBER_COLORS.length];
  await api.post('/api/members', { name, color });
  await loadMembers();
  openMemberModal();
}

/* ============================ 프로젝트 ============================ */
async function loadProjects() {
  state.projects = await api.get('/api/projects');
  const sel = $('project-select');
  sel.innerHTML = state.projects.length
    ? state.projects.map((p) => `<option value="${p.id}">${esc(p.name)}</option>`).join('')
    : '<option value="">프로젝트를 추가하세요</option>';
  if (state.projects.length) {
    const saved = Number(localStorage.getItem('cur_project'));
    const found = state.projects.find((p) => p.id === saved);
    await selectProject(found ? saved : state.projects[0].id);
  } else {
    state.currentProjectId = null;
    state.menus = [];
    renderMenus();
  }
}
function openProjectModal() {
  openModal(`
    <h2>프로젝트 추가</h2>
    <div class="field">
      <label>프로젝트명</label>
      <input id="proj-name" placeholder="예: 한빛 2호기 28차 계획예방정비" />
    </div>
    <div class="modal-actions">
      <button class="btn btn-ghost" id="cancel">취소</button>
      <button class="btn btn-primary" id="save">저장</button>
    </div>
  `);
  $('cancel').onclick = closeModal;
  $('proj-name').focus();
  $('save').onclick = async () => {
    const name = $('proj-name').value.trim();
    if (!name) return toast('프로젝트명을 입력하세요.');
    const p = await api.post('/api/projects', { name });
    closeModal();
    await loadProjects();
    await selectProject(p.id);
    toast('프로젝트가 추가되었습니다.');
  };
}
async function deleteCurrentProject() {
  if (!state.currentProjectId) return;
  const p = state.projects.find((x) => x.id === state.currentProjectId);
  if (!confirm(`'${p.name}' 프로젝트를 삭제할까요?\n하위 업무/작업오더/기록이 모두 삭제됩니다.`)) return;
  await api.del('/api/projects/' + state.currentProjectId);
  localStorage.removeItem('cur_project');
  await loadProjects();
  toast('삭제되었습니다.');
}
async function selectProject(id) {
  state.currentProjectId = id;
  localStorage.setItem('cur_project', id);
  $('project-select').value = id;
  await loadMenus();
}

/* ============================ 업무 메뉴 ============================ */
async function loadMenus() {
  if (!state.currentProjectId) { state.menus = []; return renderMenus(); }
  state.menus = await api.get(`/api/projects/${state.currentProjectId}/menus`);
  renderMenus();
}
function renderMenus() {
  const wrap = $('menu-list');
  wrap.innerHTML = '';
  if (!state.currentProjectId) {
    wrap.appendChild(el('div', 'empty-hint', '상단에서 프로젝트를 추가하세요.'));
    return;
  }
  if (!state.menus.length) {
    wrap.appendChild(el('div', 'empty-hint', '업무 메뉴가 없습니다.<br>아래 버튼으로 추가하세요.'));
    return;
  }
  for (const m of state.menus) wrap.appendChild(renderMenuCard(m));
}
function renderMenuCard(m) {
  const card = el('div', 'menu-card');
  const isWO = m.kind === 'work_order';
  const head = el('div', 'menu-head');
  head.innerHTML = `
    <span class="menu-name">${esc(m.name)}</span>
    <span class="menu-badge ${isWO ? 'badge-wo' : 'badge-gen'}">${isWO ? 'OH 작업오더' : '일반 업무'}</span>
    <div class="menu-actions">
      <button class="mini-btn" data-act="edit">✎</button>
      <button class="mini-btn danger" data-act="del">🗑</button>
    </div>`;
  head.querySelector('[data-act="edit"]').onclick = () => openMenuModal(m);
  head.querySelector('[data-act="del"]').onclick = async () => {
    if (!confirm(`'${m.name}' 메뉴를 삭제할까요?`)) return;
    await api.del('/api/menus/' + m.id);
    await loadMenus();
  };
  card.appendChild(head);

  const list = el('div', 'task-list');
  for (const t of m.tasks) list.appendChild(renderTaskRow(m, t, isWO));
  const addRow = el('div', 'add-task-row', isWO ? '＋ 작업오더 추가' : '＋ 하부 업무 추가');
  addRow.onclick = () => openTaskModal(m, null);
  list.appendChild(addRow);
  if (isWO) {
    const impRow = el('div', 'add-task-row import-row', '⬆ 엑셀로 여러 개 추가');
    impRow.onclick = () => openImportModal(m);
    list.appendChild(impRow);
  }
  card.appendChild(list);
  return card;
}

function openImportModal(menu) {
  openModal(`
    <h2>엑셀로 작업오더 추가</h2>
    <p style="color:var(--muted);font-size:13px;margin-top:-8px">
      엑셀(.xlsx) 또는 CSV 파일로 여러 작업오더를 한 번에 추가합니다.
      먼저 양식을 내려받아 채운 뒤 업로드하세요.</p>
    <button class="btn btn-ghost" id="tmpl-btn">📄 입력 양식(엑셀) 다운로드</button>
    <div class="import-help">
      · <b>제목</b>은 필수입니다.<br>
      · 산업안전/화재방호는 쉼표로 구분 (예: <code>고온/고압, 중량물</code>)<br>
      · 비계설치는 <code>Y</code>/<code>N</code>, 중량물은 <code>품명:무게:인양장구</code> 형식, 여러 개는 <code>;</code>로 구분<br>
      &nbsp;&nbsp;(예: <code>로터:1t:체인블록; 베어링:0.03t</code>)
    </div>
    <div class="field" style="margin-top:14px">
      <label>파일 선택</label>
      <input type="file" id="import-file" accept=".xlsx,.xls,.csv" />
    </div>
    <div id="import-result"></div>
    <div class="modal-actions">
      <button class="btn btn-ghost" id="cancel">닫기</button>
      <button class="btn btn-primary" id="do-import">업로드</button>
    </div>
  `);
  $('cancel').onclick = closeModal;
  $('tmpl-btn').onclick = () => downloadUrl('/api/tasks-template');
  $('do-import').onclick = async () => {
    const f = $('import-file').files[0];
    if (!f) return toast('파일을 선택하세요.');
    const btn = $('do-import');
    btn.disabled = true; btn.textContent = '업로드 중…';
    try {
      const res = await fetch('/api/menus/' + menu.id + '/tasks/import', {
        method: 'POST',
        headers: { 'Content-Type': 'application/octet-stream', 'X-Client-Id': clientId },
        body: f,
      });
      if (res.status === 401) { showLogin(); return; }
      const data = await res.json().catch(() => ({}));
      if (!res.ok) throw new Error(data.error || '업로드 실패');
      const errHtml = (data.errors && data.errors.length)
        ? `<div class="import-err">건너뜀 ${data.errors.length}건:<br>${data.errors.map(esc).join('<br>')}</div>` : '';
      $('import-result').innerHTML = `<div class="import-ok">✅ ${data.count}건 추가되었습니다.</div>${errHtml}`;
      await loadMenus();
      toast(`${data.count}건 추가되었습니다.`);
    } catch (e) {
      toast(e.message);
    } finally {
      btn.disabled = false; btn.textContent = '업로드';
    }
  };
}
function renderTaskRow(menu, t, isWO) {
  const row = el('div', 'task-row');
  const dot = el('span', 'status-dot st-' + (t.status || 'todo'));
  const main = el('div', 'task-main');
  let sub = '';
  if (isWO) {
    const chips = [];
    if (t.order_number) chips.push(`<span class="chip">오더 ${esc(t.order_number)}</span>`);
    if (t.functional_location) chips.push(`<span class="chip">위치 ${esc(t.functional_location)}</span>`);
    if (t.fme_grade) chips.push(`<span class="chip fme">FME ${esc(t.fme_grade)}</span>`);
    if (t.log_count) chips.push(`<span class="chip log">기록 ${t.log_count}</span>`);
    sub = chips.join('');
  } else if (t.note) {
    sub = esc(t.note);
  }
  const lockedByOther = t.locked_active && t.locked_by_id !== clientId;
  const lockTag = lockedByOther ? `<span class="task-lock">🔒 ${esc(t.locked_by)} 사용중</span>` : '';
  main.innerHTML = `<div class="task-title">${esc(t.title)}${lockTag}</div>${sub ? `<div class="task-sub">${sub}</div>` : ''}`;
  if (lockedByOther) row.classList.add('locked-row');
  row.appendChild(dot);
  row.appendChild(main);
  // 탭 시 잠금을 획득한 뒤 스와이프(일별 작업사항) 뷰로 진입
  row.onclick = () => openTask(menu, t.id);
  return row;
}
function openMenuModal(menu) {
  const editing = !!menu;
  openModal(`
    <h2>${editing ? '업무 메뉴 수정' : '업무 메뉴 추가'}</h2>
    <div class="field">
      <label>메뉴명</label>
      <input id="menu-name" placeholder="예: OH 사전 준비 / OH 패키지 준비 / OH 작업오더" value="${editing ? esc(menu.name) : ''}" />
    </div>
    ${editing ? '' : `
    <div class="field">
      <label>업무 유형</label>
      <div class="seg" id="kind-seg">
        <button data-kind="general" class="active">일반 업무<br><small style="font-weight:400;font-size:11px">현장/패키지 등</small></button>
        <button data-kind="work_order">OH 작업오더<br><small style="font-weight:400;font-size:11px">오더번호·FME 등</small></button>
      </div>
    </div>`}
    <div class="modal-actions">
      <button class="btn btn-ghost" id="cancel">취소</button>
      <button class="btn btn-primary" id="save">${editing ? '수정' : '추가'}</button>
    </div>
  `);
  let kind = 'general';
  if (!editing) {
    document.querySelectorAll('#kind-seg button').forEach((b) => {
      b.onclick = () => {
        document.querySelectorAll('#kind-seg button').forEach((x) => x.classList.remove('active'));
        b.classList.add('active');
        kind = b.dataset.kind;
      };
    });
  }
  $('cancel').onclick = closeModal;
  $('save').onclick = async () => {
    const name = $('menu-name').value.trim();
    if (!name) return toast('메뉴명을 입력하세요.');
    if (editing) await api.put('/api/menus/' + menu.id, { name });
    else await api.post(`/api/projects/${state.currentProjectId}/menus`, { name, kind });
    closeModal();
    await loadMenus();
    toast(editing ? '수정되었습니다.' : '메뉴가 추가되었습니다.');
  };
}

/* ============================ 작업오더 / 하부업무 편집 ============================ */
function openTaskModal(menu, task) {
  const editing = !!task;
  const isWO = menu.kind === 'work_order';
  const t = task || {};
  const factors = new Set(parseFactors(t.hazard_factors));
  let heavyItems = migrateHeavy(t);
  let scaf = t.scaffold || '';

  const optList = (list, cur) =>
    ['<option value="">선택 안 함</option>',
     ...list.map((v) => `<option value="${esc(v)}" ${v === cur ? 'selected' : ''}>${esc(v)}</option>`)].join('');

  const woFields = isWO ? `
    <div class="field"><label>오더번호</label><input id="f-order_number" value="${esc(t.order_number)}" placeholder="예: 10012345" /></div>
    <div class="field"><label>기능위치</label><input id="f-functional_location" value="${esc(t.functional_location)}" placeholder="예: 2-MFW-P-001A" /></div>
    <div class="field"><label>FME 등급</label><select id="f-fme_grade">${optList(FME_GRADES, t.fme_grade)}</select></div>
    <div class="field"><label>품질등급</label><select id="f-quality_grade">${optList(QUALITY_GRADES, t.quality_grade)}</select></div>
    <div class="field"><label>품질입회</label><select id="f-quality_witness">${optList(QUALITY_WITNESS, t.quality_witness)}</select></div>
    <div class="field">
      <label>산업안전 / 화재방호 (해당 항목 선택)</label>
      <div class="chk-grid" id="factor-grid">
        ${SAFETY_FACTORS.map((f) => `<button type="button" class="chk ${factors.has(f) ? 'active' : ''}" data-factor="${esc(f)}">${esc(f)}</button>`).join('')}
      </div>
    </div>
    <div class="field heavy-box ${factors.has(HEAVY) ? '' : 'hidden'}" id="heavy-section">
      <label>중량물 목록 (여러 개 추가 가능)</label>
      <div class="heavy-add">
        <input id="hv-name" placeholder="품명 (예: 로터)" />
        <div class="heavy-add-row2">
          <input id="hv-weight" placeholder="무게 (예: 1t)" />
          <input id="hv-gear" placeholder="인양장구 (예: 체인블록)" />
          <button type="button" class="btn btn-ghost" id="hv-add" style="flex:0 0 auto">추가</button>
        </div>
      </div>
      <div id="heavy-list" class="gear-list"></div>
    </div>
    <div class="field">
      <label>비계설치 여부</label>
      <div class="seg" id="scaf-seg">
        <button type="button" data-scaf="">미지정</button>
        <button type="button" data-scaf="Y">설치(Y)</button>
        <button type="button" data-scaf="N">미설치(N)</button>
      </div>
      <div id="scaf-height-wrap" class="${scaf === 'Y' ? '' : 'hidden'}" style="margin-top:10px">
        <input id="f-scaffold_height" value="${esc(t.scaffold_height)}" placeholder="비계 높이 (예: 12 m)" />
      </div>
    </div>
    <div class="field"><label>설계자</label><input id="f-designer" value="${esc(t.designer)}" placeholder="설계자 이름" /></div>
    <div class="field"><label>감독자</label><input id="f-supervisor" value="${esc(t.supervisor)}" placeholder="감독자 이름" /></div>
  ` : '';
  openModal(`
    <h2>${editing ? (isWO ? '작업오더 수정' : '하부 업무 수정') : (isWO ? '작업오더 추가' : '하부 업무 추가')}</h2>
    <div class="field">
      <label>제목</label>
      <input id="f-title" value="${esc(t.title)}" placeholder="${isWO ? '예: MFWP A 분해점검' : '예: 현장 사전 확인'}" />
    </div>
    ${woFields}
    <div class="field"><label>추가 세부사항 / 비고</label><textarea id="f-note" placeholder="기타 기재사항">${esc(t.note)}</textarea></div>
    <div class="field">
      <label>진행 상태</label>
      <div class="seg" id="status-seg">
        <button data-st="todo">예정</button>
        <button data-st="doing">진행중</button>
        <button data-st="done">완료</button>
      </div>
    </div>
    <div class="modal-actions">
      ${editing ? '<button class="btn btn-danger" id="del" style="flex:0 0 auto">삭제</button>' : ''}
      <button class="btn btn-ghost" id="cancel">취소</button>
      <button class="btn btn-primary" id="save">${editing ? '저장' : '추가'}</button>
    </div>
  `);
  let status = t.status || 'todo';
  const markStatus = () => document.querySelectorAll('#status-seg button').forEach((b) =>
    b.classList.toggle('active', b.dataset.st === status));
  document.querySelectorAll('#status-seg button').forEach((b) => {
    b.onclick = () => { status = b.dataset.st; markStatus(); };
  });
  markStatus();

  if (isWO) {
    // 산업안전 체크 토글 (중량물 선택 시 중량물 상세 표시)
    document.querySelectorAll('#factor-grid .chk').forEach((b) => {
      b.onclick = () => {
        const f = b.dataset.factor;
        if (factors.has(f)) factors.delete(f); else factors.add(f);
        b.classList.toggle('active');
        if (f === HEAVY) $('heavy-section').classList.toggle('hidden', !factors.has(HEAVY));
      };
    });
    // 중량물 목록 (품명 : 무게 / 인양장구, 여러 개)
    const renderHeavy = () => {
      const box = $('heavy-list');
      box.innerHTML = heavyItems.length ? '' : '<div class="empty-hint" style="padding:6px">등록된 중량물 없음</div>';
      heavyItems.forEach((it, i) => {
        const row = el('div', 'gear-item');
        const gearTxt = it.gear ? `, 인양장구 ${esc(it.gear)}` : '';
        row.innerHTML = `<span><b>${esc(it.name || '-')}</b> : ${esc(it.weight || '-')}${gearTxt}</span><button type="button" class="mini-btn danger">✕</button>`;
        row.querySelector('button').onclick = () => { heavyItems.splice(i, 1); renderHeavy(); };
        box.appendChild(row);
      });
    };
    renderHeavy();
    const addHeavy = () => {
      const name = $('hv-name').value.trim();
      const weight = $('hv-weight').value.trim();
      const gearv = $('hv-gear').value.trim();
      if (!name && !weight && !gearv) return;
      heavyItems.push({ name, weight, gear: gearv });
      $('hv-name').value = ''; $('hv-weight').value = ''; $('hv-gear').value = '';
      renderHeavy();
      $('hv-name').focus();
    };
    $('hv-add').onclick = addHeavy;
    ['hv-name', 'hv-weight', 'hv-gear'].forEach((id) =>
      $(id).addEventListener('keydown', (e) => { if (e.key === 'Enter') { e.preventDefault(); addHeavy(); } }));
    // 비계 세그먼트 (Y 선택 시 높이 입력)
    const markScaf = () => document.querySelectorAll('#scaf-seg button').forEach((b) =>
      b.classList.toggle('active', b.dataset.scaf === scaf));
    document.querySelectorAll('#scaf-seg button').forEach((b) => {
      b.onclick = () => { scaf = b.dataset.scaf; markScaf(); $('scaf-height-wrap').classList.toggle('hidden', scaf !== 'Y'); };
    });
    markScaf();
  }

  $('cancel').onclick = closeModal;
  const getVal = (id) => { const e = $(id); return e ? e.value : ''; };
  $('save').onclick = async () => {
    const title = $('f-title').value.trim();
    if (!title) return toast('제목을 입력하세요.');
    const payload = { title, note: getVal('f-note'), status };
    if (isWO) {
      const hasHeavy = factors.has(HEAVY);
      Object.assign(payload, {
        order_number: getVal('f-order_number'),
        functional_location: getVal('f-functional_location'),
        fme_grade: getVal('f-fme_grade'),
        quality_grade: getVal('f-quality_grade'),
        quality_witness: getVal('f-quality_witness'),
        hazard_factors: SAFETY_FACTORS.filter((f) => factors.has(f)).join(','),
        scaffold: scaf,
        scaffold_height: scaf === 'Y' ? getVal('f-scaffold_height') : '',
        heavy_items: hasHeavy ? JSON.stringify(heavyItems) : '',
        heavy_weight: '',
        lifting_gear: '',
        designer: getVal('f-designer'),
        supervisor: getVal('f-supervisor'),
      });
    }
    let saved;
    if (editing) saved = await api.put('/api/tasks/' + task.id, payload);
    else saved = await api.post('/api/menus/' + menu.id + '/tasks', payload);
    closeModal();
    await loadMenus();
    // 스와이프 뷰에서 수정한 경우 갱신
    if (state.swipe && state.swipe.menu.id === menu.id) {
      await openSwipe(menu, saved.id);
    }
    toast('저장되었습니다.');
  };
  if (editing) $('del').onclick = async () => {
    if (!confirm('이 항목과 모든 일별 기록을 삭제할까요?')) return;
    await api.del('/api/tasks/' + task.id);
    closeModal();
    if (state.swipe && state.swipe.menu.id === menu.id) closeSwipe();
    await loadMenus();
    toast('삭제되었습니다.');
  };
}

/* ============================ 잠금 (동시 접근 방지) ============================ */
// 잠금 획득/갱신. 성공 {ok:true}, 다른 사용자 사용 중이면 {ok:false, locked_by}
async function lockTask(taskId) {
  try {
    const res = await fetch('/api/tasks/' + taskId + '/lock', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', 'X-Client-Id': clientId },
      body: JSON.stringify({ name: state.me }),
    });
    if (res.status === 401) { showLogin(); return { ok: false }; }
    if (res.ok) return { ok: true };
    const e = await res.json().catch(() => ({}));
    return { ok: false, locked_by: e.locked_by || '다른 사용자' };
  } catch { return { ok: false }; }
}
function unlockTask(taskId) {
  if (!taskId) return;
  const url = '/api/tasks/' + taskId + '/unlock?cid=' + encodeURIComponent(clientId);
  try {
    if (navigator.sendBeacon) navigator.sendBeacon(url);
    else fetch(url, { method: 'POST', headers: { 'X-Client-Id': clientId }, keepalive: true });
  } catch { /* noop */ }
}
// 관리자 강제 해제
async function forceUnlock(taskId) {
  try { await api.post('/api/tasks/' + taskId + '/force-unlock', {}); return true; }
  catch (e) { toast(e.message); return false; }
}
// 파일 다운로드 (인증 쿠키 포함, 같은 출처)
function downloadUrl(url) {
  const a = document.createElement('a');
  a.href = url;
  a.download = '';
  document.body.appendChild(a);
  a.click();
  a.remove();
}
// 관리자 데이터 내보내기 (CSV 다운로드)
function exportData() {
  downloadUrl('/api/export');
  toast('내보내기를 시작했습니다.');
}

/* ============================ 스와이프 뷰 (일별 작업사항) ============================ */
async function openTask(menu, taskId) {
  // 목록에서 탭할 때 먼저 잠금 시도 → 성공해야 진입
  let r = await lockTask(taskId);
  if (!r.ok && state.isAdmin) {
    if (confirm(`${r.locked_by || '다른 사용자'} 님이 사용 중입니다.\n관리자 권한으로 강제로 여시겠습니까?`)) {
      if (await forceUnlock(taskId)) r = await lockTask(taskId);
    }
  }
  if (!r.ok) {
    toast(`🔒 ${r.locked_by || '다른 사용자'} 님이 사용 중입니다.`);
    await loadMenus();
    return;
  }
  await openSwipe(menu, taskId);
}

async function openSwipe(menu, focusTaskId) {
  const fresh = state.menus.find((m) => m.id === menu.id) || menu;
  const tasks = fresh.tasks;
  if (!tasks.length) { openTaskModal(menu, null); return; }
  const index = Math.max(0, tasks.findIndex((t) => t.id === focusTaskId));
  state.swipe = { menu: fresh, tasks, index, activeTaskId: null, settleTimer: null };

  $('view-menus').classList.add('hidden');
  $('add-menu').classList.add('hidden');
  $('view-swipe').classList.remove('hidden');
  $('swipe-menu-name').textContent = fresh.name;

  const track = $('swipe-track');
  track.innerHTML = '';
  for (const t of tasks) {
    const page = el('div', 'swipe-page');
    page.dataset.taskId = t.id;
    page.appendChild(renderWOCard(fresh, t));
    page.appendChild(renderLogSection(t));
    track.appendChild(page);
  }
  renderDots();
  updateSwipeCounter();

  // 스크롤 스냅으로 현재 인덱스 감지 → 멈추면 해당 작업오더 잠금 전환
  track.onscroll = () => {
    const w = track.clientWidth;
    const idx = Math.round(track.scrollLeft / w);
    if (idx !== state.swipe.index) {
      state.swipe.index = idx;
      updateSwipeCounter();
      renderDots();
    }
    clearTimeout(state.swipe.settleTimer);
    state.swipe.settleTimer = setTimeout(() => setActiveTask(state.swipe.index), 180);
  };
  // 포커스 페이지로 이동
  requestAnimationFrame(() => {
    track.scrollLeft = index * track.clientWidth;
  });
  await setActiveTask(index);

  // 잠금 유지용 하트비트 (15초)
  clearInterval(swipeHeartbeat);
  swipeHeartbeat = setInterval(() => {
    const s = state.swipe;
    if (s && s.activeTaskId) lockTask(s.activeTaskId);
  }, 15000);
}
let swipeHeartbeat = null;

// 현재 중앙 작업오더의 잠금을 확보하고, 이전 것은 해제. 실패 시 잠금 오버레이 표시.
async function setActiveTask(i) {
  const s = state.swipe;
  if (!s || !s.tasks[i]) return;
  const task = s.tasks[i];
  if (s.activeTaskId && s.activeTaskId !== task.id) {
    unlockTask(s.activeTaskId);
    s.activeTaskId = null;
  }
  const r = await lockTask(task.id);
  const pageEl = $('swipe-track').children[i];
  if (r.ok) {
    s.activeTaskId = task.id;
    setPageLocked(pageEl, null);
    $('swipe-edit').disabled = false;
    refreshLogs(task.id);
  } else {
    s.activeTaskId = null;
    setPageLocked(pageEl, r.locked_by || '다른 사용자');
    $('swipe-edit').disabled = true;
  }
}

function setPageLocked(pageEl, holderName) {
  if (!pageEl) return;
  let ov = pageEl.querySelector('.lock-overlay');
  if (holderName) {
    if (!ov) { ov = el('div', 'lock-overlay'); pageEl.appendChild(ov); }
    ov.innerHTML = `<div class="lock-msg">🔒<div class="lock-who">${esc(holderName)} 님이<br>사용 중입니다</div>
      <button class="btn btn-ghost" id="lock-retry">다시 시도</button>
      ${state.isAdmin ? '<button class="btn btn-danger" id="lock-force" style="margin-top:8px">🛡 관리자 강제 해제</button>' : ''}</div>`;
    ov.querySelector('#lock-retry').onclick = () => setActiveTask(state.swipe.index);
    const force = ov.querySelector('#lock-force');
    if (force) force.onclick = async () => {
      if (await forceUnlock(state.swipe.tasks[state.swipe.index].id)) setActiveTask(state.swipe.index);
    };
  } else if (ov) {
    ov.remove();
  }
}

function updateSwipeCounter() {
  const s = state.swipe;
  $('swipe-counter').textContent = `${s.index + 1} / ${s.tasks.length} · ${s.tasks[s.index].title}`;
}
function renderDots() {
  const dots = $('swipe-dots');
  dots.innerHTML = '';
  state.swipe.tasks.forEach((_, i) => {
    const d = el('span', 'dot' + (i === state.swipe.index ? ' active' : ''));
    d.onclick = () => { $('swipe-track').scrollLeft = i * $('swipe-track').clientWidth; };
    dots.appendChild(d);
  });
}
function renderWOCard(menu, t) {
  const card = el('div', 'wo-card');
  const isWO = menu.kind === 'work_order';
  let fields = '';
  if (isWO) {
    const f = [
      ['오더번호', t.order_number],
      ['기능위치', t.functional_location],
      ['FME 등급', t.fme_grade],
      ['품질등급', t.quality_grade],
      ['품질입회', t.quality_witness],
      ['설계자', t.designer],
      ['감독자', t.supervisor],
      ['진행상태', { todo: '예정', doing: '진행중', done: '완료' }[t.status] || '예정'],
    ];
    const scafText = t.scaffold === 'Y' ? `설치 (높이 ${esc(t.scaffold_height) || '-'})` : (t.scaffold === 'N' ? '미설치' : '');
    const hItems = migrateHeavy(t);
    const heavyHtml = hItems.map((it) =>
      `<b>${esc(it.name || '-')}</b> : ${esc(it.weight || '-')}${it.gear ? ` · 인양장구 ${esc(it.gear)}` : ''}`).join('<br>');
    const factorChips = parseFactors(t.hazard_factors)
      .map((x) => `<span class="chip fme" style="margin:2px 4px 2px 0;display:inline-block">${esc(x)}</span>`).join('');
    fields = '<div class="wo-grid">' + f.map(([l, v]) =>
      `<div class="wo-field"><div class="wo-label">${l}</div><div class="wo-value">${esc(v) || '-'}</div></div>`).join('')
      + (scafText ? `<div class="wo-field"><div class="wo-label">비계설치</div><div class="wo-value">${scafText}</div></div>` : '')
      + (factorChips ? `<div class="wo-field full"><div class="wo-label">산업안전 / 화재방호</div><div class="wo-value">${factorChips}</div></div>` : '')
      + (heavyHtml ? `<div class="wo-field full"><div class="wo-label">중량물</div><div class="wo-value">${heavyHtml}</div></div>` : '')
      + (t.note ? `<div class="wo-field full"><div class="wo-label">비고</div><div class="wo-value">${esc(t.note)}</div></div>` : '')
      + '</div>';
  } else {
    fields = t.note ? `<div class="wo-field full"><div class="wo-label">내용</div><div class="wo-value">${esc(t.note)}</div></div>` : '';
  }
  card.innerHTML = `<div class="wo-title">${esc(t.title)}</div>${fields}`;
  return card;
}
function renderLogSection(t) {
  const sec = el('div');
  sec.innerHTML = `
    <div class="log-section-title">
      <h3>📅 일별 작업사항</h3>
      <button class="log-add-btn" data-add="${t.id}">＋ 기록 추가</button>
    </div>
    <div class="log-list" id="loglist-${t.id}"><div class="empty-hint">불러오는 중…</div></div>`;
  sec.querySelector('[data-add]').onclick = () => openLogModal(t, null);
  return sec;
}
async function refreshLogs(taskId) {
  const box = $('loglist-' + taskId);
  if (!box) return;
  let logs;
  try { logs = await api.get('/api/tasks/' + taskId + '/logs'); }
  catch { box.innerHTML = '<div class="empty-hint">불러오기 실패</div>'; return; }
  if (!logs.length) { box.innerHTML = '<div class="empty-hint">기록된 작업사항이 없습니다.</div>'; return; }
  const task = state.swipe && state.swipe.tasks.find((t) => t.id === taskId);
  box.innerHTML = '';
  for (const lg of logs) {
    const item = el('div', 'log-item');
    const gradeTag = lg.grade ? `<span class="grade-badge grade-${lg.grade}">안전 ${lg.grade}등급</span>` : '';
    const hz = lg.hazards ? lg.hazards.split(',').filter(Boolean) : [];
    const handled = parseHandled(lg.heavy_handled);
    const hzChips = hz.map((x) => {
      const label = x === '고소작업' && lg.work_height ? `고소작업 ${lg.work_height}` : x;
      return `<span class="chip fme">${esc(label)}</span>`;
    }).join('');
    const hvChips = handled.map((it) => `<span class="chip">${esc(it.name || '중량물')} ${esc(it.weight || '')}</span>`).join('');
    item.innerHTML = `
      <div class="log-top">
        <span class="log-date">${esc(lg.log_date)}</span>
        ${gradeTag}
        ${lg.author ? `<span class="log-author">✍ ${esc(lg.author)}</span>` : ''}
      </div>
      <div class="log-content">${esc(lg.content)}</div>
      ${(hzChips || hvChips) ? `<div class="log-tags">${hvChips}${hzChips}</div>` : ''}
      <div class="log-actions">
        <button data-edit="${lg.id}">수정</button>
        <button class="danger" data-del="${lg.id}">삭제</button>
      </div>`;
    item.querySelector('[data-edit]').onclick = () => openLogModal(task || { id: taskId }, lg);
    item.querySelector('[data-del]').onclick = async () => {
      if (!confirm('이 작업사항을 삭제할까요?')) return;
      await api.del('/api/logs/' + lg.id);
      await refreshLogs(taskId);
      await loadMenus();
    };
    box.appendChild(item);
  }
}
function openLogModal(task, log) {
  const taskId = task.id;
  const editing = !!log;
  const taskHeavy = migrateHeavy(task);                    // 작업오더에 등록된 중량물 목록
  const hazards = new Set(editing && log.hazards ? log.hazards.split(',').filter(Boolean) : []);
  // 당일 취급 중량물: 저장된 것과 작업오더 목록을 품명+무게로 매칭
  const savedHandled = editing ? parseHandled(log.heavy_handled) : [];
  const key = (it) => `${it.name || ''}|${it.weight || ''}`;
  const handled = new Set(savedHandled.map(key));

  const heavyHtml = taskHeavy.length
    ? `<div class="chk-grid">${taskHeavy.map((it, i) =>
        `<button type="button" class="chk ${handled.has(key(it)) ? 'active' : ''}" data-hv="${i}">${esc(it.name || '중량물')} (${esc(it.weight || '-')})</button>`).join('')}</div>`
    : '<div class="empty-hint" style="padding:8px">이 작업오더에 등록된 중량물이 없습니다.</div>';

  openModal(`
    <h2>${editing ? '작업사항 수정' : '일별 작업사항 기록'}</h2>
    <div class="field"><label>작업 일자</label><input type="date" id="log-date" value="${editing ? esc(log.log_date) : todayStr()}" /></div>
    <div class="field"><label>작성자</label>
      <select id="log-author">
        ${state.members.map((m) => `<option value="${esc(m.name)}" ${(editing ? log.author : state.me) === m.name ? 'selected' : ''}>${esc(m.name)}</option>`).join('')}
        ${state.members.length ? '' : '<option value="">미지정</option>'}
      </select>
    </div>
    <div class="field"><label>작업 내용</label><textarea id="log-content" placeholder="당일 수행한 작업 내용을 기록">${editing ? esc(log.content) : ''}</textarea></div>
    <div class="field"><label>취급할 중량물 선택</label>${heavyHtml}</div>
    <div class="field">
      <label>당일 위험요인 선택</label>
      <div class="chk-grid" id="log-haz">
        ${LOG_HAZARDS.map((f) => `<button type="button" class="chk ${hazards.has(f) ? 'active' : ''}" data-haz="${esc(f)}">${esc(LOG_HAZARD_LABEL[f] || f)}</button>`).join('')}
      </div>
    </div>
    <div class="field ${hazards.has('고소작업') ? '' : 'hidden'}" id="work-height-field">
      <label>고소작업 높이${task.scaffold_height ? ` <span style="color:var(--muted);font-weight:400">(오더 비계 높이: ${esc(task.scaffold_height)})</span>` : ''}</label>
      <input id="log-work-height" value="${esc(editing && log.work_height ? log.work_height : (task.scaffold_height || ''))}" placeholder="예: 12 m" />
    </div>
    <div class="field">
      <label>안전등급 (자동 산정)</label>
      <div id="grade-box" class="grade-box"></div>
    </div>
    <div class="modal-actions">
      <button class="btn btn-ghost" id="cancel">취소</button>
      <button class="btn btn-primary" id="save">${editing ? '저장' : '기록'}</button>
    </div>
  `);

  const selectedHeavy = () => taskHeavy.filter((_, i) => handled.has(key(taskHeavy[i])));
  const refreshGrade = () => {
    const g = computeGrade(hazards, selectedHeavy());
    const box = $('grade-box');
    box.className = 'grade-box' + (g ? ' grade-' + g : '');
    box.textContent = g ? `${g} 등급` : '해당 없음 (위험요인·중량물 미선택)';
  };
  // 중량물 토글
  document.querySelectorAll('[data-hv]').forEach((b) => {
    b.onclick = () => {
      const it = taskHeavy[Number(b.dataset.hv)];
      const k = key(it);
      if (handled.has(k)) handled.delete(k); else handled.add(k);
      b.classList.toggle('active');
      refreshGrade();
    };
  });
  // 위험요인 토글 (고소작업 선택 시 높이 입력칸 표시, 오더 비계 높이 기본 적용)
  document.querySelectorAll('#log-haz .chk').forEach((b) => {
    b.onclick = () => {
      const f = b.dataset.haz;
      const on = !hazards.has(f);
      if (on) hazards.add(f); else hazards.delete(f);
      b.classList.toggle('active');
      if (f === '고소작업') {
        $('work-height-field').classList.toggle('hidden', !on);
        const hi = $('log-work-height');
        if (on && !hi.value.trim() && task.scaffold_height) hi.value = task.scaffold_height;
      }
      refreshGrade();
    };
  });
  refreshGrade();

  $('cancel').onclick = closeModal;
  $('log-content').focus();
  $('save').onclick = async () => {
    const payload = {
      log_date: $('log-date').value,
      author: $('log-author').value,
      content: $('log-content').value.trim(),
      hazards: [...hazards].join(','),
      heavy_handled: JSON.stringify(selectedHeavy()),
      work_height: hazards.has('고소작업') ? $('log-work-height').value.trim() : '',
    };
    if (!payload.log_date) return toast('작업 일자를 선택하세요.');
    if (!payload.content) return toast('작업 내용을 입력하세요.');
    if (editing) await api.put('/api/logs/' + log.id, payload);
    else await api.post('/api/tasks/' + taskId + '/logs', payload);
    closeModal();
    await refreshLogs(taskId);
    await loadMenus();
    toast('기록되었습니다.');
  };
}
/* ============================ 일별 작업사항 정리 (표) ============================ */
const REPORT_GROUPS = {
  date: { label: '📅', key: (r) => r.log_date || '(날짜없음)' },
  order: { label: '작업오더', key: (r) => r.task_title || '(제목없음)' },
  designer: { label: '설계자', key: (r) => r.designer || '(미지정)' },
  supervisor: { label: '감독자', key: (r) => r.supervisor || '(미지정)' },
};
function sortReport(rows, group) {
  const dateDesc = (a, b) => String(b.log_date).localeCompare(String(a.log_date));
  const kf = { order: (r) => r.task_title || '', designer: (r) => r.designer || '￿', supervisor: (r) => r.supervisor || '￿' };
  if (!kf[group]) return rows.slice();
  return rows.slice().sort((a, b) => kf[group](a).localeCompare(kf[group](b), 'ko') || dateDesc(a, b));
}
async function openReport() {
  if (!state.currentProjectId) return toast('먼저 프로젝트를 선택하세요.');
  $('view-menus').classList.add('hidden');
  $('add-menu').classList.add('hidden');
  $('view-report').classList.remove('hidden');
  $('report-body').innerHTML = '<div class="empty-hint">불러오는 중…</div>';
  try { state.reportRows = await api.get('/api/projects/' + state.currentProjectId + '/daily'); }
  catch (e) { $('report-body').innerHTML = `<div class="empty-hint">불러오기 실패: ${esc(e.message)}</div>`; return; }
  renderReport();
}
function renderReport() {
  const group = state.reportGroup;
  const from = state.reportFrom, to = state.reportTo;
  const filtered = state.reportRows.filter((r) => {
    const d = r.log_date || '';
    if (from && d < from) return false;
    if (to && d > to) return false;
    return true;
  });
  const rows = sortReport(filtered, group);
  const ranged = from || to;
  $('report-sub').textContent = `총 ${rows.length}건${ranged ? ` (${from || '처음'} ~ ${to || '끝'})` : ''}`;
  const body = $('report-body');
  if (!rows.length) {
    body.innerHTML = `<div class="empty-hint">${ranged ? '선택한 기간에 기록이 없습니다.' : '기록된 일별 작업사항이 없습니다.'}</div>`;
    return;
  }
  const gradeCell = (g) => g ? `<span class="grade-badge grade-${g}">${esc(g)}</span>` : '-';
  const handledCell = (v) => {
    const a = parseHandled(v);
    return a.length ? a.map((it) => `${esc(it.name || '-')}:${esc(it.weight || '-')}`).join(', ') : '-';
  };
  const gInfo = REPORT_GROUPS[group];
  let html = `<div class="report-scroll"><table class="report-table">
    <thead><tr>
      <th>일자</th><th>업무메뉴</th><th>작업오더</th><th>설계자</th><th>감독자</th><th>작성자</th>
      <th>등급</th><th>위험요인</th><th>취급중량물</th><th>고소높이</th><th>작업내용</th>
    </tr></thead><tbody>`;
  let prevKey = null;
  for (const r of rows) {
    const k = gInfo.key(r);
    if (k !== prevKey) {
      prevKey = k;
      const cnt = rows.filter((x) => gInfo.key(x) === k).length;
      const title = group === 'date' ? `📅 ${esc(k)}` : `${gInfo.label} · ${esc(k)}`;
      html += `<tr class="rp-group"><td colspan="11">${title} <span class="rp-count">${cnt}건</span></td></tr>`;
    }
    html += `<tr>
      <td class="rp-date">${esc(r.log_date)}</td>
      <td>${esc(r.menu_name)}</td>
      <td>${esc(r.task_title)}</td>
      <td>${esc(r.designer) || '-'}</td>
      <td>${esc(r.supervisor) || '-'}</td>
      <td>${esc(r.author) || '-'}</td>
      <td>${gradeCell(r.grade)}</td>
      <td>${esc(r.hazards) || '-'}</td>
      <td>${handledCell(r.heavy_handled)}</td>
      <td>${esc(r.work_height) || '-'}</td>
      <td class="rp-content">${esc(r.content)}</td>
    </tr>`;
  }
  html += '</tbody></table></div>';
  body.innerHTML = html;
}
function closeReport() {
  $('view-report').classList.add('hidden');
  $('view-menus').classList.remove('hidden');
  $('add-menu').classList.remove('hidden');
}

function closeSwipe() {
  clearInterval(swipeHeartbeat);
  if (state.swipe && state.swipe.activeTaskId) unlockTask(state.swipe.activeTaskId);
  state.swipe = null;
  $('view-swipe').classList.add('hidden');
  $('view-menus').classList.remove('hidden');
  $('add-menu').classList.remove('hidden');
  loadMenus();
}

// 탭을 닫거나 화면을 벗어나면 잠금 해제 (영구 잠김 방지)
function releaseOnLeave() {
  if (state.swipe && state.swipe.activeTaskId) unlockTask(state.swipe.activeTaskId);
}
window.addEventListener('pagehide', releaseOnLeave);
document.addEventListener('visibilitychange', () => {
  if (document.visibilityState === 'hidden') releaseOnLeave();
  else if (state.swipe && state.swipe.activeTaskId) lockTask(state.swipe.activeTaskId); // 복귀 시 재획득
});

// 목록 화면에서 다른 사람의 사용중(🔒) 표시를 주기적으로 갱신
setInterval(() => {
  if (state.swipe) return;                                   // 스와이프 중이면 skip
  if (!$('modal-backdrop').classList.contains('hidden')) return; // 모달 열려있으면 skip
  if (document.visibilityState !== 'visible') return;
  if (!state.currentProjectId) return;
  loadMenus();
}, 20000);

/* ============================ 시작 ============================ */
init().catch((e) => { console.error(e); toast('초기화 오류: ' + e.message); });
