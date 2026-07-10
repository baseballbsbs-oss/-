import express from 'express';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { query, initDb } from './db.js';
import { AUTH_ENABLED, checkPassword, setAuthCookie, clearAuthCookie, isAuthed, requireAuth } from './auth.js';

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

/* ------------------------------ 인증 ------------------------------ */
app.get('/api/session', (req, res) => {
  res.json({ auth_enabled: AUTH_ENABLED, authed: isAuthed(req) });
});
app.post('/api/login', (req, res) => {
  if (!checkPassword(req.body?.password)) {
    return res.status(401).json({ error: '비밀번호가 올바르지 않습니다.' });
  }
  setAuthCookie(res);
  res.json({ ok: true });
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
const TASK_FIELDS = ['title', 'order_number', 'functional_location', 'fme_grade', 'hazard_factors', 'note', 'status'];

app.post('/api/menus/:id/tasks', h(async (req, res) => {
  const b = req.body;
  if (!b.title || !b.title.trim()) throw new Error('제목을 입력하세요.');
  const max = (await query(
    'SELECT COALESCE(MAX(sort_order), 0) AS m FROM tasks WHERE menu_id = $1', [req.params.id])).rows[0].m;
  const { rows } = await query(
    `INSERT INTO tasks (menu_id, title, order_number, functional_location, fme_grade, hazard_factors, note, status, sort_order)
     VALUES ($1, $2, $3, $4, $5, $6, $7, $8, $9) RETURNING *`,
    [req.params.id, b.title.trim(), b.order_number || '', b.functional_location || '',
     b.fme_grade || '', b.hazard_factors || '', b.note || '', b.status || 'todo', max + 1]);
  res.json(rows[0]);
}));

app.put('/api/tasks/:id', h(async (req, res) => {
  if (!(await assertUnlocked(req.params.id, getCid(req), res))) return;
  const existing = (await query('SELECT * FROM tasks WHERE id = $1', [req.params.id])).rows[0];
  if (!existing) throw new Error('작업오더를 찾을 수 없습니다.');
  const m = { ...existing };
  for (const f of TASK_FIELDS) if (req.body[f] !== undefined) m[f] = req.body[f];
  if (!m.title || !String(m.title).trim()) throw new Error('제목을 입력하세요.');
  const { rows } = await query(
    `UPDATE tasks SET title=$1, order_number=$2, functional_location=$3, fme_grade=$4,
       hazard_factors=$5, note=$6, status=$7 WHERE id=$8 RETURNING *`,
    [m.title, m.order_number, m.functional_location, m.fme_grade, m.hazard_factors, m.note, m.status, req.params.id]);
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
  const { rows } = await query(
    'INSERT INTO logs (task_id, log_date, content, author) VALUES ($1, $2, $3, $4) RETURNING *',
    [req.params.id, log_date, content.trim(), author || '']);
  res.json(rows[0]);
}));

app.put('/api/logs/:id', h(async (req, res) => {
  const existing = (await query('SELECT * FROM logs WHERE id = $1', [req.params.id])).rows[0];
  if (!existing) throw new Error('작업사항을 찾을 수 없습니다.');
  if (!(await assertUnlocked(existing.task_id, getCid(req), res))) return;
  const { log_date, content, author } = req.body;
  const { rows } = await query(
    `UPDATE logs SET log_date=$1, content=$2, author=$3, updated_at=now() WHERE id=$4 RETURNING *`,
    [log_date || existing.log_date, (content ?? existing.content).trim(),
     author ?? existing.author, req.params.id]);
  res.json(rows[0]);
}));

app.delete('/api/logs/:id', h(async (req, res) => {
  const existing = (await query('SELECT task_id FROM logs WHERE id = $1', [req.params.id])).rows[0];
  if (existing && !(await assertUnlocked(existing.task_id, getCid(req), res))) return;
  await query('DELETE FROM logs WHERE id = $1', [req.params.id]);
  res.json({ ok: true });
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
