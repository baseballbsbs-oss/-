import express from 'express';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import db from './db.js';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const app = express();
const PORT = process.env.PORT || 3000;

app.use(express.json());
app.use(express.static(path.join(__dirname, 'public')));

// 간단한 에러 래퍼
const h = (fn) => (req, res) => {
  try {
    fn(req, res);
  } catch (err) {
    console.error(err);
    res.status(400).json({ error: err.message });
  }
};

/* ------------------------------ 멤버 (8명) ------------------------------ */
app.get('/api/members', h((req, res) => {
  res.json(db.prepare('SELECT * FROM members ORDER BY id').all());
}));

app.post('/api/members', h((req, res) => {
  const { name, color } = req.body;
  if (!name || !name.trim()) throw new Error('이름을 입력하세요.');
  const info = db.prepare('INSERT INTO members (name, color) VALUES (?, ?)')
    .run(name.trim(), color || '#4f8cff');
  res.json(db.prepare('SELECT * FROM members WHERE id = ?').get(info.lastInsertRowid));
}));

app.delete('/api/members/:id', h((req, res) => {
  db.prepare('DELETE FROM members WHERE id = ?').run(req.params.id);
  res.json({ ok: true });
}));

/* ------------------------------ 프로젝트 ------------------------------ */
app.get('/api/projects', h((req, res) => {
  res.json(db.prepare('SELECT * FROM projects ORDER BY id DESC').all());
}));

app.post('/api/projects', h((req, res) => {
  const { name } = req.body;
  if (!name || !name.trim()) throw new Error('프로젝트명을 입력하세요.');
  const info = db.prepare('INSERT INTO projects (name) VALUES (?)').run(name.trim());
  res.json(db.prepare('SELECT * FROM projects WHERE id = ?').get(info.lastInsertRowid));
}));

app.delete('/api/projects/:id', h((req, res) => {
  db.prepare('DELETE FROM projects WHERE id = ?').run(req.params.id);
  res.json({ ok: true });
}));

/* ------------------------------ 업무 메뉴 ------------------------------ */
// 프로젝트의 메뉴 + 각 메뉴의 하부 업무(작업오더)를 함께 반환
app.get('/api/projects/:id/menus', h((req, res) => {
  const menus = db.prepare('SELECT * FROM menus WHERE project_id = ? ORDER BY sort_order, id')
    .all(req.params.id);
  const taskStmt = db.prepare('SELECT * FROM tasks WHERE menu_id = ? ORDER BY sort_order, id');
  const cntStmt = db.prepare('SELECT COUNT(*) AS c FROM logs WHERE task_id = ?');
  for (const m of menus) {
    m.tasks = taskStmt.all(m.id);
    for (const t of m.tasks) t.log_count = cntStmt.get(t.id).c;
  }
  res.json(menus);
}));

app.post('/api/projects/:id/menus', h((req, res) => {
  const { name, kind } = req.body;
  if (!name || !name.trim()) throw new Error('메뉴명을 입력하세요.');
  const k = kind === 'work_order' ? 'work_order' : 'general';
  const max = db.prepare('SELECT COALESCE(MAX(sort_order), 0) AS m FROM menus WHERE project_id = ?')
    .get(req.params.id).m;
  const info = db.prepare('INSERT INTO menus (project_id, name, kind, sort_order) VALUES (?, ?, ?, ?)')
    .run(req.params.id, name.trim(), k, max + 1);
  res.json(db.prepare('SELECT * FROM menus WHERE id = ?').get(info.lastInsertRowid));
}));

app.put('/api/menus/:id', h((req, res) => {
  const { name } = req.body;
  if (!name || !name.trim()) throw new Error('메뉴명을 입력하세요.');
  db.prepare('UPDATE menus SET name = ? WHERE id = ?').run(name.trim(), req.params.id);
  res.json(db.prepare('SELECT * FROM menus WHERE id = ?').get(req.params.id));
}));

app.delete('/api/menus/:id', h((req, res) => {
  db.prepare('DELETE FROM menus WHERE id = ?').run(req.params.id);
  res.json({ ok: true });
}));

/* ------------------------ 하부 업무 / 작업오더 ------------------------ */
const TASK_FIELDS = ['title', 'order_number', 'functional_location', 'fme_grade', 'hazard_factors', 'note', 'status'];

app.post('/api/menus/:id/tasks', h((req, res) => {
  const b = req.body;
  if (!b.title || !b.title.trim()) throw new Error('제목을 입력하세요.');
  const max = db.prepare('SELECT COALESCE(MAX(sort_order), 0) AS m FROM tasks WHERE menu_id = ?')
    .get(req.params.id).m;
  const info = db.prepare(`
    INSERT INTO tasks (menu_id, title, order_number, functional_location, fme_grade, hazard_factors, note, status, sort_order)
    VALUES (@menu_id, @title, @order_number, @functional_location, @fme_grade, @hazard_factors, @note, @status, @sort_order)
  `).run({
    menu_id: req.params.id,
    title: b.title.trim(),
    order_number: b.order_number || '',
    functional_location: b.functional_location || '',
    fme_grade: b.fme_grade || '',
    hazard_factors: b.hazard_factors || '',
    note: b.note || '',
    status: b.status || 'todo',
    sort_order: max + 1,
  });
  res.json(db.prepare('SELECT * FROM tasks WHERE id = ?').get(info.lastInsertRowid));
}));

app.put('/api/tasks/:id', h((req, res) => {
  const existing = db.prepare('SELECT * FROM tasks WHERE id = ?').get(req.params.id);
  if (!existing) throw new Error('작업오더를 찾을 수 없습니다.');
  const merged = { ...existing };
  for (const f of TASK_FIELDS) if (req.body[f] !== undefined) merged[f] = req.body[f];
  if (!merged.title || !String(merged.title).trim()) throw new Error('제목을 입력하세요.');
  db.prepare(`
    UPDATE tasks SET title=@title, order_number=@order_number, functional_location=@functional_location,
      fme_grade=@fme_grade, hazard_factors=@hazard_factors, note=@note, status=@status WHERE id=@id
  `).run({ ...merged, id: req.params.id });
  res.json(db.prepare('SELECT * FROM tasks WHERE id = ?').get(req.params.id));
}));

app.delete('/api/tasks/:id', h((req, res) => {
  db.prepare('DELETE FROM tasks WHERE id = ?').run(req.params.id);
  res.json({ ok: true });
}));

/* ------------------------- 일별 작업사항 로그 ------------------------- */
app.get('/api/tasks/:id/logs', h((req, res) => {
  res.json(db.prepare('SELECT * FROM logs WHERE task_id = ? ORDER BY log_date DESC, id DESC')
    .all(req.params.id));
}));

app.post('/api/tasks/:id/logs', h((req, res) => {
  const { log_date, content, author } = req.body;
  if (!log_date) throw new Error('작업 일자를 선택하세요.');
  if (!content || !content.trim()) throw new Error('작업 내용을 입력하세요.');
  const info = db.prepare('INSERT INTO logs (task_id, log_date, content, author) VALUES (?, ?, ?, ?)')
    .run(req.params.id, log_date, content.trim(), author || '');
  res.json(db.prepare('SELECT * FROM logs WHERE id = ?').get(info.lastInsertRowid));
}));

app.put('/api/logs/:id', h((req, res) => {
  const { log_date, content, author } = req.body;
  const existing = db.prepare('SELECT * FROM logs WHERE id = ?').get(req.params.id);
  if (!existing) throw new Error('작업사항을 찾을 수 없습니다.');
  db.prepare("UPDATE logs SET log_date=?, content=?, author=?, updated_at=datetime('now') WHERE id=?")
    .run(log_date || existing.log_date, (content ?? existing.content).trim(), author ?? existing.author, req.params.id);
  res.json(db.prepare('SELECT * FROM logs WHERE id = ?').get(req.params.id));
}));

app.delete('/api/logs/:id', h((req, res) => {
  db.prepare('DELETE FROM logs WHERE id = ?').run(req.params.id);
  res.json({ ok: true });
}));

// SPA fallback
app.get('*', (req, res) => {
  res.sendFile(path.join(__dirname, 'public', 'index.html'));
});

app.listen(PORT, () => {
  console.log(`파트별 업무 공유 스케줄러: http://localhost:${PORT}`);
});
