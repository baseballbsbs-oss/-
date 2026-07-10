import Database from 'better-sqlite3';
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const dataDir = path.join(__dirname, 'data');
if (!fs.existsSync(dataDir)) fs.mkdirSync(dataDir, { recursive: true });

const db = new Database(path.join(dataDir, 'scheduler.db'));
db.pragma('journal_mode = WAL');
db.pragma('foreign_keys = ON');

db.exec(`
  CREATE TABLE IF NOT EXISTS members (
    id         INTEGER PRIMARY KEY AUTOINCREMENT,
    name       TEXT NOT NULL,
    color      TEXT NOT NULL DEFAULT '#4f8cff',
    created_at TEXT NOT NULL DEFAULT (datetime('now'))
  );

  CREATE TABLE IF NOT EXISTS projects (
    id         INTEGER PRIMARY KEY AUTOINCREMENT,
    name       TEXT NOT NULL,
    created_at TEXT NOT NULL DEFAULT (datetime('now'))
  );

  -- 업무 메뉴 (개별 지정). kind: 'general' = 일반 업무, 'work_order' = OH 작업오더
  CREATE TABLE IF NOT EXISTS menus (
    id         INTEGER PRIMARY KEY AUTOINCREMENT,
    project_id INTEGER NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
    name       TEXT NOT NULL,
    kind       TEXT NOT NULL DEFAULT 'general',
    sort_order INTEGER NOT NULL DEFAULT 0,
    created_at TEXT NOT NULL DEFAULT (datetime('now'))
  );

  -- 하부 업무 / 작업오더. work_order 메뉴에서는 아래 세부 항목을 사용
  CREATE TABLE IF NOT EXISTS tasks (
    id                  INTEGER PRIMARY KEY AUTOINCREMENT,
    menu_id             INTEGER NOT NULL REFERENCES menus(id) ON DELETE CASCADE,
    title               TEXT NOT NULL,
    order_number        TEXT NOT NULL DEFAULT '',   -- 오더번호
    functional_location TEXT NOT NULL DEFAULT '',   -- 기능위치
    fme_grade           TEXT NOT NULL DEFAULT '',   -- FME 등급
    hazard_factors      TEXT NOT NULL DEFAULT '',   -- 유해위험요소
    note                TEXT NOT NULL DEFAULT '',   -- 추가 세부사항
    status              TEXT NOT NULL DEFAULT 'todo', -- todo | doing | done
    sort_order          INTEGER NOT NULL DEFAULT 0,
    created_at          TEXT NOT NULL DEFAULT (datetime('now'))
  );

  -- 일별 작업사항
  CREATE TABLE IF NOT EXISTS logs (
    id         INTEGER PRIMARY KEY AUTOINCREMENT,
    task_id    INTEGER NOT NULL REFERENCES tasks(id) ON DELETE CASCADE,
    log_date   TEXT NOT NULL,
    content    TEXT NOT NULL,
    author     TEXT NOT NULL DEFAULT '',
    created_at TEXT NOT NULL DEFAULT (datetime('now')),
    updated_at TEXT NOT NULL DEFAULT (datetime('now'))
  );

  CREATE INDEX IF NOT EXISTS idx_menus_project ON menus(project_id);
  CREATE INDEX IF NOT EXISTS idx_tasks_menu    ON tasks(menu_id);
  CREATE INDEX IF NOT EXISTS idx_logs_task     ON logs(task_id, log_date);
`);

export default db;
