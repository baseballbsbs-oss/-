'use strict';

/* ============================ API 헬퍼 ============================ */
const api = {
  async req(method, url, body) {
    const opt = { method, headers: { 'Content-Type': 'application/json' } };
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
};

const MEMBER_COLORS = ['#4f8cff', '#38bdf8', '#22c55e', '#f59e0b', '#ef4444', '#a855f7', '#ec4899', '#14b8a6'];

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
      hideLogin();
      await startApp();
    } catch {
      err.textContent = '네트워크 오류';
    }
  };
  $('login-btn').onclick = submit;
  $('login-pw').addEventListener('keydown', (e) => { if (e.key === 'Enter') submit(); });
}

/* ============================ 초기화 ============================ */
async function init() {
  bindLogin();
  bindStaticEvents();
  const s = await fetch('/api/session').then((r) => r.json()).catch(() => ({ auth_enabled: false, authed: true }));
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
  $('whoami-name').textContent = state.me || '미지정';
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
    <div class="modal-actions">
      <button class="btn btn-ghost" id="logout-btn" style="flex:0 0 auto">로그아웃</button>
      <button class="btn btn-primary" id="close-member">확인</button>
    </div>
  `);
  $('close-member').onclick = closeModal;
  $('logout-btn').onclick = async () => {
    await fetch('/api/logout', { method: 'POST' });
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
  card.appendChild(list);
  return card;
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
  main.innerHTML = `<div class="task-title">${esc(t.title)}</div>${sub ? `<div class="task-sub">${sub}</div>` : ''}`;
  row.appendChild(dot);
  row.appendChild(main);
  // 작업오더는 탭 시 스와이프(일별 작업사항) 뷰로, 일반업무도 로그 기록 가능
  row.onclick = () => openSwipe(menu, t.id);
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
  const woFields = isWO ? `
    <div class="field"><label>오더번호</label><input id="f-order_number" value="${esc(t.order_number)}" placeholder="예: 10012345" /></div>
    <div class="field"><label>기능위치</label><input id="f-functional_location" value="${esc(t.functional_location)}" placeholder="예: 2-MFW-P-001A" /></div>
    <div class="field"><label>FME 등급</label><input id="f-fme_grade" value="${esc(t.fme_grade)}" placeholder="예: A / B / C" /></div>
    <div class="field"><label>유해위험요소</label><textarea id="f-hazard_factors" placeholder="예: 고온·고압, 중량물, 밀폐공간 등">${esc(t.hazard_factors)}</textarea></div>
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
  const mark = () => document.querySelectorAll('#status-seg button').forEach((b) =>
    b.classList.toggle('active', b.dataset.st === status));
  document.querySelectorAll('#status-seg button').forEach((b) => {
    b.onclick = () => { status = b.dataset.st; mark(); };
  });
  mark();
  $('cancel').onclick = closeModal;
  const getVal = (id) => { const e = $(id); return e ? e.value : ''; };
  $('save').onclick = async () => {
    const title = $('f-title').value.trim();
    if (!title) return toast('제목을 입력하세요.');
    const payload = {
      title,
      order_number: getVal('f-order_number'),
      functional_location: getVal('f-functional_location'),
      fme_grade: getVal('f-fme_grade'),
      hazard_factors: getVal('f-hazard_factors'),
      note: getVal('f-note'),
      status,
    };
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

/* ============================ 스와이프 뷰 (일별 작업사항) ============================ */
async function openSwipe(menu, focusTaskId) {
  const fresh = state.menus.find((m) => m.id === menu.id) || menu;
  const tasks = fresh.tasks;
  if (!tasks.length) { openTaskModal(menu, null); return; }
  const index = Math.max(0, tasks.findIndex((t) => t.id === focusTaskId));
  state.swipe = { menu: fresh, tasks, index };

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

  // 스크롤 스냅으로 현재 인덱스 감지
  track.onscroll = () => {
    const w = track.clientWidth;
    const idx = Math.round(track.scrollLeft / w);
    if (idx !== state.swipe.index) {
      state.swipe.index = idx;
      updateSwipeCounter();
      renderDots();
    }
  };
  // 포커스 페이지로 이동
  requestAnimationFrame(() => {
    track.scrollLeft = index * track.clientWidth;
  });
  await refreshLogs(tasks[index].id);
}
function updateSwipeCounter() {
  const s = state.swipe;
  $('swipe-counter').textContent = `${s.index + 1} / ${s.tasks.length} · ${s.tasks[s.index].title}`;
  refreshLogs(s.tasks[s.index].id);
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
      ['진행상태', { todo: '예정', doing: '진행중', done: '완료' }[t.status] || '예정'],
    ];
    fields = '<div class="wo-grid">' + f.map(([l, v]) =>
      `<div class="wo-field"><div class="wo-label">${l}</div><div class="wo-value">${esc(v) || '-'}</div></div>`).join('')
      + (t.hazard_factors ? `<div class="wo-field full"><div class="wo-label">유해위험요소</div><div class="wo-value">${esc(t.hazard_factors)}</div></div>` : '')
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
  sec.querySelector('[data-add]').onclick = () => openLogModal(t.id, null);
  return sec;
}
async function refreshLogs(taskId) {
  const box = $('loglist-' + taskId);
  if (!box) return;
  let logs;
  try { logs = await api.get('/api/tasks/' + taskId + '/logs'); }
  catch { box.innerHTML = '<div class="empty-hint">불러오기 실패</div>'; return; }
  if (!logs.length) { box.innerHTML = '<div class="empty-hint">기록된 작업사항이 없습니다.</div>'; return; }
  box.innerHTML = '';
  for (const lg of logs) {
    const item = el('div', 'log-item');
    item.innerHTML = `
      <div class="log-top">
        <span class="log-date">${esc(lg.log_date)}</span>
        ${lg.author ? `<span class="log-author">✍ ${esc(lg.author)}</span>` : ''}
      </div>
      <div class="log-content">${esc(lg.content)}</div>
      <div class="log-actions">
        <button data-edit="${lg.id}">수정</button>
        <button class="danger" data-del="${lg.id}">삭제</button>
      </div>`;
    item.querySelector('[data-edit]').onclick = () => openLogModal(taskId, lg);
    item.querySelector('[data-del]').onclick = async () => {
      if (!confirm('이 작업사항을 삭제할까요?')) return;
      await api.del('/api/logs/' + lg.id);
      await refreshLogs(taskId);
      await loadMenus();
    };
    box.appendChild(item);
  }
}
function openLogModal(taskId, log) {
  const editing = !!log;
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
    <div class="modal-actions">
      <button class="btn btn-ghost" id="cancel">취소</button>
      <button class="btn btn-primary" id="save">${editing ? '저장' : '기록'}</button>
    </div>
  `);
  $('cancel').onclick = closeModal;
  $('log-content').focus();
  $('save').onclick = async () => {
    const payload = {
      log_date: $('log-date').value,
      author: $('log-author').value,
      content: $('log-content').value.trim(),
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
function closeSwipe() {
  state.swipe = null;
  $('view-swipe').classList.add('hidden');
  $('view-menus').classList.remove('hidden');
  $('add-menu').classList.remove('hidden');
  loadMenus();
}

/* ============================ 시작 ============================ */
init().catch((e) => { console.error(e); toast('초기화 오류: ' + e.message); });
