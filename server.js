import express from 'express';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import xlsx from 'xlsx';
import { query, initDb } from './db.js';
import { AUTH_ENABLED, ADMIN_ENABLED, roleForPassword, setAuthCookie, clearAuthCookie,
         isAuthed, isAdmin, requireAuth, requireAdmin } from './auth.js';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const app = express();
const PORT = process.env.PORT || 3000;

app.use(express.json());

// 비동기 핸들러 에러 래퍼
const h = (fn) => (req, res) => Promise.resolve(fn(req, res)).catch((err) => {
  console.error(err);
  res.status(400).json({ error: err.message });
});

// 잠금 유효 시간 (초). 이 시간 동안 신호가 없으면 자동 해제.
const LOCK_TTL = 40;
const LOCK_ACTIVE = `(locked_by_id IS NOT NULL AND locked_at > now() - interval '${LOCK_TTL} seconds')`;
const getCid = (req) => req.headers['x-client-id'] || req.query.cid || '';

// 다른 사용자가 잠금 중이면 그 사람 이름을 반환, 아니면 false, 없으면 null
async function lockedByOther(taskId, cid) {
  const r = await query(
    `SELECT locked_by, (locked_by_id IS NOT NULL AND locked_by_id <> $2 AND
       locked_at > now() - interval '${LOCK_TTL} seconds') AS blocked
       FROM tasks WHERE id = $1`, [taskId, cid]);
  if (!r.rows[0]) return null;
  return r.rows[0].blocked ? (r.rows[0].locked_by || '다른 사용자') : false;
}
async function assertUnlocked(taskId, cid, res) {
  const who = await lockedByOther(taskId, cid);
  if (who) { res.status(409).json({ error: `${who} 님이 사용 중입니다.`, locked: true, locked_by: who }); return false; }
  return true;
}

// 무게 문자열 → 톤(t). 'kg' 표기는 1/1000 환산.
function parseTons(s) {
  const m = String(s || '').match(/([\d.]+)/);
  if (!m) return 0;
  let n = parseFloat(m[1]) || 0;
  if (/kg/i.test(String(s))) n /= 1000;
  return n;
}
// 당일 위험요인 + 취급중량물 → 안전등급 A/B/C (판단기준)
function computeGrade(hazards, heavyItems) {
  const hz = new Set(hazards || []);
  const maxW = (heavyItems || []).reduce((mx, it) => Math.max(mx, parseTons(it && it.weight)), 0);
  if (maxW >= 3) return 'A';                                              // 중량물 3t 이상
  if (hz.has('고온/고압') || hz.has('분진/비산') || hz.has('밀폐공간') || (maxW >= 1 && maxW < 3)) return 'B';
  if (hz.has('고소작업') || hz.has('화재폭발') || (maxW > 0 && maxW < 1)) return 'C';
  return '';
}
// 요청 본문에서 위험요인/취급중량물을 정규화하고 등급을 계산
function safetyFromBody(b) {
  const hazards = Array.isArray(b.hazards) ? b.hazards
    : (typeof b.hazards === 'string' && b.hazards ? b.hazards.split(',').map((x) => x.trim()).filter(Boolean) : []);
  let heavy = [];
  try { const a = typeof b.heavy_handled === 'string' ? JSON.parse(b.heavy_handled || '[]') : b.heavy_handled; if (Array.isArray(a)) heavy = a; } catch { /* noop */ }
  return { hazardsStr: hazards.join(','), heavyStr: JSON.stringify(heavy), grade: computeGrade(hazards, heavy) };
}

/* ------------------------------ 인증 ------------------------------ */
app.get('/api/session', (req, res) => {
  res.json({
    auth_enabled: AUTH_ENABLED,
    admin_enabled: ADMIN_ENABLED,
    authed: isAuthed(req),
    is_admin: isAdmin(req),
  });
});
app.post('/api/login', (req, res) => {
  const role = roleForPassword(req.body?.password);
  if (!role) return res.status(401).json({ error: '비밀번호가 올바르지 않습니다.' });
  setAuthCookie(res, role);
  res.json({ ok: true, role });
});
app.post('/api/logout', (req, res) => {
  clearAuthCookie(res);
  res.json({ ok: true });
});

// 이하 모든 /api 라우트는 인증 필요
app.use('/api', requireAuth);

/* ------------------------------ 멤버 (8명) ------------------------------ */
app.get('/api/members', h(async (req, res) => {
  const { rows } = await query('SELECT * FROM members ORDER BY id');
  res.json(rows);
}));

app.post('/api/members', h(async (req, res) => {
  const { name, color } = req.body;
  if (!name || !name.trim()) throw new Error('이름을 입력하세요.');
  const { rows } = await query(
    'INSERT INTO members (name, color) VALUES ($1, $2) RETURNING *',
    [name.trim(), color || '#4f8cff']);
  res.json(rows[0]);
}));

app.delete('/api/members/:id', h(async (req, res) => {
  await query('DELETE FROM members WHERE id = $1', [req.params.id]);
  res.json({ ok: true });
}));

/* ------------------------------ 프로젝트 ------------------------------ */
app.get('/api/projects', h(async (req, res) => {
  const { rows } = await query('SELECT * FROM projects ORDER BY id DESC');
  res.json(rows);
}));

app.post('/api/projects', h(async (req, res) => {
  const { name } = req.body;
  if (!name || !name.trim()) throw new Error('프로젝트명을 입력하세요.');
  const { rows } = await query('INSERT INTO projects (name) VALUES ($1) RETURNING *', [name.trim()]);
  res.json(rows[0]);
}));

app.delete('/api/projects/:id', h(async (req, res) => {
  await query('DELETE FROM projects WHERE id = $1', [req.params.id]);
  res.json({ ok: true });
}));

/* ------------------------------ 업무 메뉴 ------------------------------ */
// 프로젝트의 메뉴 + 각 메뉴의 하부 업무(작업오더) + 로그 개수를 함께 반환
app.get('/api/projects/:id/menus', h(async (req, res) => {
  const menus = (await query(
    'SELECT * FROM menus WHERE project_id = $1 ORDER BY sort_order, id', [req.params.id])).rows;
  if (menus.length === 0) return res.json([]);
  const menuIds = menus.map((m) => m.id);
  const tasks = (await query(
    `SELECT t.*, ${LOCK_ACTIVE} AS locked_active,
       (SELECT COUNT(*)::int FROM logs l WHERE l.task_id = t.id) AS log_count
       FROM tasks t WHERE t.menu_id = ANY($1) ORDER BY t.sort_order, t.id`, [menuIds])).rows;
  const byMenu = new Map(menus.map((m) => [m.id, { ...m, tasks: [] }]));
  for (const t of tasks) byMenu.get(t.menu_id).tasks.push(t);
  res.json(menus.map((m) => byMenu.get(m.id)));
}));

app.post('/api/projects/:id/menus', h(async (req, res) => {
  const { name, kind } = req.body;
  if (!name || !name.trim()) throw new Error('메뉴명을 입력하세요.');
  const k = kind === 'work_order' ? 'work_order' : 'general';
  const max = (await query(
    'SELECT COALESCE(MAX(sort_order), 0) AS m FROM menus WHERE project_id = $1', [req.params.id])).rows[0].m;
  const { rows } = await query(
    'INSERT INTO menus (project_id, name, kind, sort_order) VALUES ($1, $2, $3, $4) RETURNING *',
    [req.params.id, name.trim(), k, max + 1]);
  res.json(rows[0]);
}));

app.put('/api/menus/:id', h(async (req, res) => {
  const { name } = req.body;
  if (!name || !name.trim()) throw new Error('메뉴명을 입력하세요.');
  const { rows } = await query('UPDATE menus SET name = $1 WHERE id = $2 RETURNING *',
    [name.trim(), req.params.id]);
  res.json(rows[0]);
}));

app.delete('/api/menus/:id', h(async (req, res) => {
  await query('DELETE FROM menus WHERE id = $1', [req.params.id]);
  res.json({ ok: true });
}));

/* ------------------------ 하부 업무 / 작업오더 ------------------------ */
// title/status 외 나머지 컬럼은 기본값 ''
const TASK_COLS = ['title', 'order_number', 'functional_location', 'fme_grade', 'hazard_factors',
  'note', 'status', 'quality_grade', 'quality_witness', 'scaffold', 'scaffold_height',
  'heavy_weight', 'lifting_gear', 'heavy_items', 'designer', 'supervisor'];
const taskDefault = (c) => (c === 'status' ? 'todo' : '');

async function createTask(menuId, b, sortOrder) {
  const cols = ['menu_id', ...TASK_COLS, 'sort_order'];
  const vals = [menuId,
    ...TASK_COLS.map((c) => (b[c] !== undefined && b[c] !== null ? b[c] : taskDefault(c))),
    sortOrder];
  vals[1] = String(vals[1]).trim(); // title
  const ph = cols.map((_, i) => '$' + (i + 1)).join(', ');
  const { rows } = await query(
    `INSERT INTO tasks (${cols.join(', ')}) VALUES (${ph}) RETURNING *`, vals);
  return rows[0];
}

app.post('/api/menus/:id/tasks', h(async (req, res) => {
  const b = req.body;
  if (!b.title || !b.title.trim()) throw new Error('제목을 입력하세요.');
  const max = (await query(
    'SELECT COALESCE(MAX(sort_order), 0) AS m FROM tasks WHERE menu_id = $1', [req.params.id])).rows[0].m;
  res.json(await createTask(req.params.id, b, max + 1));
}));

/* ------------------- 엑셀 업로드로 작업오더 일괄 추가 ------------------- */
const IMPORT_HEADERS = ['제목', '오더번호', '기능위치', 'FME등급', '품질등급', '품질입회',
  '산업안전/화재방호', '비계설치', '비계높이', '중량물', '설계자', '감독자', '비고'];

const pick = (row, keys) => {
  for (const k of keys) {
    const v = row[k];
    if (v !== undefined && v !== null && String(v).trim() !== '') return String(v).trim();
  }
  return '';
};
function parseScaffoldCell(v) {
  const s = String(v || '').trim();
  if (!s) return '';
  if (/^(y|o|예|설치|true|1)$/i.test(s)) return 'Y';
  if (/^(n|x|아니오?|미설치|false|0)$/i.test(s)) return 'N';
  return '';
}
// "로터:1t:체인블록; 베어링:0.03t" → [{name,weight,gear}]
function parseHeavyCell(v) {
  if (!v) return [];
  return String(v).split(/[;\n]/).map((part) => {
    const p = part.split(':').map((x) => x.trim());
    if (!p[0] && !p[1]) return null;
    return { name: p[0] || '', weight: p[1] || '', gear: p[2] || '' };
  }).filter(Boolean);
}
function taskFromRow(row) {
  const b = { status: 'todo' };
  b.title = pick(row, ['제목', 'title', '작업오더', '작업오더/업무']);
  b.order_number = pick(row, ['오더번호', 'order_number']);
  b.functional_location = pick(row, ['기능위치', 'functional_location']);
  b.fme_grade = pick(row, ['FME등급', 'FME 등급', 'fme_grade']);
  b.quality_grade = pick(row, ['품질등급', 'quality_grade']);
  b.quality_witness = pick(row, ['품질입회', 'quality_witness']);
  const factors = pick(row, ['산업안전/화재방호', '산업안전', '유해위험요소', '위험요소'])
    .split(',').map((x) => x.trim()).filter(Boolean);
  const heavy = parseHeavyCell(pick(row, ['중량물', '중량물목록']));
  if (heavy.length && !factors.includes('중량물')) factors.push('중량물');
  b.hazard_factors = factors.join(',');
  b.heavy_items = JSON.stringify(heavy);
  b.scaffold = parseScaffoldCell(pick(row, ['비계설치', '비계', 'scaffold']));
  b.scaffold_height = pick(row, ['비계높이', '비계 높이', 'scaffold_height']);
  b.designer = pick(row, ['설계자', 'designer']);
  b.supervisor = pick(row, ['감독자', 'supervisor']);
  b.note = pick(row, ['비고', '추가세부사항', 'note']);
  return b;
}

// 업로드 양식(엑셀) 다운로드
app.get('/api/tasks-template', h(async (req, res) => {
  const example = ['MFWP A 분해점검', '10012345', '2-MFW-P-001A', 'ZONE 2', 'A', 'KPS-INSP',
    '고온/고압, 중량물', 'Y', '12 m', '로터:1t:체인블록; 베어링:0.03t', '김설계', '박감독', '예시 비고'];
  const ws = xlsx.utils.aoa_to_sheet([IMPORT_HEADERS, example]);
  ws['!cols'] = IMPORT_HEADERS.map(() => ({ wch: 16 }));
  const wb = xlsx.utils.book_new();
  xlsx.utils.book_append_sheet(wb, ws, '작업오더');
  const buf = xlsx.write(wb, { type: 'buffer', bookType: 'xlsx' });
  res.setHeader('Content-Type', 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet');
  res.setHeader('Content-Disposition', 'attachment; filename="work-order-template.xlsx"');
  res.send(buf);
}));

// 엑셀/CSV 파일 본문(raw)으로 작업오더 일괄 추가
app.post('/api/menus/:id/tasks/import', express.raw({ type: '*/*', limit: '10mb' }), h(async (req, res) => {
  if (!req.body || !req.body.length) throw new Error('업로드된 파일이 없습니다.');
  let rows;
  try {
    const wb = xlsx.read(req.body, { type: 'buffer' });
    const ws = wb.Sheets[wb.SheetNames[0]];
    rows = xlsx.utils.sheet_to_json(ws, { defval: '' });
  } catch {
    throw new Error('엑셀 파일을 읽을 수 없습니다. (.xlsx 또는 .csv)');
  }
  if (!rows.length) throw new Error('데이터 행이 없습니다.');
  let max = (await query(
    'SELECT COALESCE(MAX(sort_order), 0) AS m FROM tasks WHERE menu_id = $1', [req.params.id])).rows[0].m;
  let count = 0;
  const errors = [];
  for (let i = 0; i < rows.length; i++) {
    const b = taskFromRow(rows[i]);
    if (!b.title) { errors.push(`${i + 2}행: 제목이 비어 있어 건너뜀`); continue; }
    try { await createTask(req.params.id, b, ++max); count += 1; }
    catch (e) { errors.push(`${i + 2}행: ${e.message}`); }
  }
  res.json({ ok: true, count, errors });
}));

app.put('/api/tasks/:id', h(async (req, res) => {
  if (!(await assertUnlocked(req.params.id, getCid(req), res))) return;
  const existing = (await query('SELECT * FROM tasks WHERE id = $1', [req.params.id])).rows[0];
  if (!existing) throw new Error('작업오더를 찾을 수 없습니다.');
  const m = { ...existing };
  for (const f of TASK_COLS) if (req.body[f] !== undefined) m[f] = req.body[f];
  if (!m.title || !String(m.title).trim()) throw new Error('제목을 입력하세요.');
  const set = TASK_COLS.map((c, i) => `${c}=$${i + 1}`).join(', ');
  const vals = [...TASK_COLS.map((c) => m[c]), req.params.id];
  const { rows } = await query(
    `UPDATE tasks SET ${set} WHERE id=$${TASK_COLS.length + 1} RETURNING *`, vals);
  res.json(rows[0]);
}));

app.delete('/api/tasks/:id', h(async (req, res) => {
  if (!(await assertUnlocked(req.params.id, getCid(req), res))) return;
  await query('DELETE FROM tasks WHERE id = $1', [req.params.id]);
  res.json({ ok: true });
}));

/* ------------------------------ 잠금 ------------------------------ */
// 개별 업무(작업오더) 접근 잠금 획득/갱신 (한 번에 한 명만)
app.post('/api/tasks/:id/lock', h(async (req, res) => {
  const cid = getCid(req);
  if (!cid) throw new Error('클라이언트 식별자가 없습니다.');
  const name = (req.body?.name || '').toString().slice(0, 40);
  const upd = await query(
    `UPDATE tasks SET locked_by = $1, locked_by_id = $2, locked_at = now()
       WHERE id = $3 AND (locked_by_id IS NULL OR locked_by_id = $2
                          OR locked_at < now() - interval '${LOCK_TTL} seconds')
       RETURNING id`, [name, cid, req.params.id]);
  if (upd.rowCount === 1) return res.json({ ok: true });
  const cur = (await query('SELECT locked_by FROM tasks WHERE id = $1', [req.params.id])).rows[0];
  res.status(409).json({ error: `${cur?.locked_by || '다른 사용자'} 님이 사용 중입니다.`, locked: true, locked_by: cur?.locked_by || '다른 사용자' });
}));

// 잠금 해제 (소유자만). sendBeacon 대응: 본문 없이 ?cid= 로도 허용
app.post('/api/tasks/:id/unlock', h(async (req, res) => {
  const cid = getCid(req);
  await query('UPDATE tasks SET locked_by=NULL, locked_by_id=NULL, locked_at=NULL WHERE id=$1 AND locked_by_id=$2',
    [req.params.id, cid]);
  res.json({ ok: true });
}));

// 잠금 강제 해제 (관리자 전용)
app.post('/api/tasks/:id/force-unlock', requireAdmin, h(async (req, res) => {
  await query('UPDATE tasks SET locked_by=NULL, locked_by_id=NULL, locked_at=NULL WHERE id=$1', [req.params.id]);
  res.json({ ok: true });
}));

/* ------------------------- 일별 작업사항 로그 ------------------------- */
app.get('/api/tasks/:id/logs', h(async (req, res) => {
  const { rows } = await query(
    'SELECT * FROM logs WHERE task_id = $1 ORDER BY log_date DESC, id DESC', [req.params.id]);
  res.json(rows);
}));

app.post('/api/tasks/:id/logs', h(async (req, res) => {
  if (!(await assertUnlocked(req.params.id, getCid(req), res))) return;
  const { log_date, content, author } = req.body;
  if (!log_date) throw new Error('작업 일자를 선택하세요.');
  if (!content || !content.trim()) throw new Error('작업 내용을 입력하세요.');
  const s = safetyFromBody(req.body);
  const { rows } = await query(
    `INSERT INTO logs (task_id, log_date, content, author, hazards, heavy_handled, grade, work_height)
     VALUES ($1, $2, $3, $4, $5, $6, $7, $8) RETURNING *`,
    [req.params.id, log_date, content.trim(), author || '', s.hazardsStr, s.heavyStr, s.grade, String(req.body.work_height || '').trim()]);
  res.json(rows[0]);
}));

app.put('/api/logs/:id', h(async (req, res) => {
  const existing = (await query('SELECT * FROM logs WHERE id = $1', [req.params.id])).rows[0];
  if (!existing) throw new Error('작업사항을 찾을 수 없습니다.');
  if (!(await assertUnlocked(existing.task_id, getCid(req), res))) return;
  const { log_date, content, author } = req.body;
  const s = safetyFromBody(req.body);
  const { rows } = await query(
    `UPDATE logs SET log_date=$1, content=$2, author=$3, hazards=$4, heavy_handled=$5, grade=$6, work_height=$7, updated_at=now()
     WHERE id=$8 RETURNING *`,
    [log_date || existing.log_date, (content ?? existing.content).trim(), author ?? existing.author,
     s.hazardsStr, s.heavyStr, s.grade, String(req.body.work_height ?? existing.work_height ?? '').trim(), req.params.id]);
  res.json(rows[0]);
}));

app.delete('/api/logs/:id', h(async (req, res) => {
  const existing = (await query('SELECT task_id FROM logs WHERE id = $1', [req.params.id])).rows[0];
  if (existing && !(await assertUnlocked(existing.task_id, getCid(req), res))) return;
  await query('DELETE FROM logs WHERE id = $1', [req.params.id]);
  res.json({ ok: true });
}));

/* ------------------------- 일별 작업사항 정리 (프로젝트) ------------------------- */
const fmtHandled = (v) => {
  try { const a = JSON.parse(v || '[]'); return Array.isArray(a) ? a.map((it) => `${it.name || '-'}:${it.weight || '-'}`).join(' | ') : ''; }
  catch { return ''; }
};
async function dailyRows(projectId) {
  return (await query(`
    SELECT l.log_date, m.name AS menu_name, t.title AS task_title, l.author, l.content,
           l.grade, l.hazards, l.heavy_handled, l.work_height
    FROM logs l
    JOIN tasks t ON t.id = l.task_id
    JOIN menus m ON m.id = t.menu_id
    WHERE m.project_id = $1
    ORDER BY l.log_date DESC, m.sort_order, m.id, t.sort_order, t.id, l.id
  `, [projectId])).rows;
}

// JSON (표 렌더링용)
app.get('/api/projects/:id/daily', h(async (req, res) => {
  res.json(await dailyRows(req.params.id));
}));

// 엑셀(.xlsx) 내보내기
app.get('/api/projects/:id/daily.xlsx', h(async (req, res) => {
  const proj = (await query('SELECT name FROM projects WHERE id = $1', [req.params.id])).rows[0];
  const rows = await dailyRows(req.params.id);
  const header = ['작업일자', '업무메뉴', '작업오더', '작성자', '안전등급', '위험요인', '취급중량물', '고소작업높이', '작업내용'];
  const aoa = [header, ...rows.map((r) => [
    r.log_date || '', r.menu_name || '', r.task_title || '', r.author || '', r.grade || '',
    r.hazards || '', fmtHandled(r.heavy_handled), r.work_height || '', r.content || '',
  ])];
  const ws = xlsx.utils.aoa_to_sheet(aoa);
  ws['!cols'] = [{ wch: 12 }, { wch: 16 }, { wch: 20 }, { wch: 10 }, { wch: 9 }, { wch: 18 }, { wch: 20 }, { wch: 12 }, { wch: 40 }];
  const wb = xlsx.utils.book_new();
  xlsx.utils.book_append_sheet(wb, ws, '일별작업사항');
  const buf = xlsx.write(wb, { type: 'buffer', bookType: 'xlsx' });
  const stamp = new Date().toISOString().slice(0, 10);
  // 파일명은 ASCII만 (한글 프로젝트명은 헤더에 넣을 수 없음)
  res.setHeader('Content-Type', 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet');
  res.setHeader('Content-Disposition', `attachment; filename="daily-report-${stamp}.xlsx"`);
  res.send(buf);
}));

/* ------------------------------ 데이터 내보내기 (관리자) ------------------------------ */
const csvCell = (v) => {
  const s = String(v ?? '');
  return /[",\n\r]/.test(s) ? '"' + s.replace(/"/g, '""') + '"' : s;
};
const KIND_KO = { work_order: 'OH 작업오더', general: '일반 업무' };
const STATUS_KO = { todo: '예정', doing: '진행중', done: '완료' };

app.get('/api/export', requireAdmin, h(async (req, res) => {
  const { rows } = await query(`
    SELECT p.name AS project, m.name AS menu, m.kind, t.*,
      l.log_date, l.author, l.content, l.hazards, l.grade, l.heavy_handled, l.work_height
    FROM projects p
    JOIN menus m ON m.project_id = p.id
    JOIN tasks t ON t.menu_id = m.id
    LEFT JOIN logs l ON l.task_id = t.id
    ORDER BY p.id, m.sort_order, m.id, t.sort_order, t.id, l.log_date, l.id
  `);
  const oldGear = (v) => { try { const a = JSON.parse(v || '[]'); return Array.isArray(a) ? a.join(', ') : ''; } catch { return ''; } };
  const heavy = (r) => {
    let items = [];
    try { const a = JSON.parse(r.heavy_items || '[]'); if (Array.isArray(a)) items = a; } catch { /* noop */ }
    if (!items.length && (r.heavy_weight || oldGear(r.lifting_gear))) items = [{ name: '', weight: r.heavy_weight, gear: oldGear(r.lifting_gear) }];
    return items.map((it) => `${it.name || '-'} : ${it.weight || '-'}${it.gear ? ' / 인양장구 ' + it.gear : ''}`).join(' | ');
  };
  const scaffold = (r) => r.scaffold === 'Y' ? `설치(높이 ${r.scaffold_height || '-'})` : (r.scaffold === 'N' ? '미설치' : '');
  const handled = (r) => {
    let items = [];
    try { const a = JSON.parse(r.heavy_handled || '[]'); if (Array.isArray(a)) items = a; } catch { /* noop */ }
    return items.map((it) => `${it.name || '-'} : ${it.weight || '-'}`).join(' | ');
  };
  const header = ['프로젝트', '업무메뉴', '유형', '작업오더/업무', '오더번호', '기능위치', 'FME등급',
    '품질등급', '품질입회', '산업안전/화재방호', '비계설치', '중량물(품명:무게/인양장구)',
    '설계자', '감독자', '비고', '상태', '작업일자', '작성자', '작업내용',
    '당일안전등급', '당일위험요인', '당일취급중량물', '당일고소작업높이'];
  const lines = [header.join(',')];
  for (const r of rows) {
    lines.push([
      r.project, r.menu, KIND_KO[r.kind] || r.kind, r.title, r.order_number, r.functional_location, r.fme_grade,
      r.quality_grade, r.quality_witness, r.hazard_factors, scaffold(r), heavy(r),
      r.designer, r.supervisor, r.note, STATUS_KO[r.status] || r.status,
      r.log_date || '', r.author || '', r.content || '',
      r.grade || '', r.hazards || '', handled(r), r.work_height || '',
    ].map(csvCell).join(','));
  }
  const csv = '﻿' + lines.join('\r\n'); // BOM: 엑셀 한글 깨짐 방지
  const stamp = new Date().toISOString().slice(0, 10);
  res.setHeader('Content-Type', 'text/csv; charset=utf-8');
  res.setHeader('Content-Disposition', `attachment; filename="scheduler-export-${stamp}.csv"`);
  res.send(csv);
}));

/* ------------------------------ 정적 / SPA ------------------------------ */
app.use(express.static(path.join(__dirname, 'public')));
app.get('*', (req, res) => {
  res.sendFile(path.join(__dirname, 'public', 'index.html'));
});

initDb()
  .then(() => {
    app.listen(PORT, () => {
      console.log(`파트별 업무 공유 스케줄러: http://localhost:${PORT} (인증 ${AUTH_ENABLED ? '사용' : '미사용'})`);
    });
  })
  .catch((err) => {
    console.error('DB 초기화 실패:', err.message);
    process.exit(1);
  });
