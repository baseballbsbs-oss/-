import pg from 'pg';

const { Pool } = pg;

const connectionString = process.env.DATABASE_URL;
if (!connectionString) {
  console.error('환경변수 DATABASE_URL 이 설정되지 않았습니다. (예: Neon Postgres 연결 문자열)');
  process.exit(1);
}

// 로컬(localhost/127.0.0.1) 이 아니면 SSL 사용 (Neon 등 관리형 DB)
const isLocal = /localhost|127\.0\.0\.1/.test(connectionString);
export const pool = new Pool({
  connectionString,
  ssl: isLocal ? false : { rejectUnauthorized: false },
  max: 10,
});

export const query = (text, params) => pool.query(text, params);

export async function initDb() {
  await query(`
    CREATE TABLE IF NOT EXISTS members (
      id         SERIAL PRIMARY KEY,
      name       TEXT NOT NULL,
      color      TEXT NOT NULL DEFAULT '#4f8cff',
      created_at TIMESTAMPTZ NOT NULL DEFAULT now()
    );

    CREATE TABLE IF NOT EXISTS projects (
      id         SERIAL PRIMARY KEY,
      name       TEXT NOT NULL,
      created_at TIMESTAMPTZ NOT NULL DEFAULT now()
    );

    -- 업무 메뉴 (개별 지정). kind: 'general' = 일반 업무, 'work_order' = OH 작업오더
    CREATE TABLE IF NOT EXISTS menus (
      id         SERIAL PRIMARY KEY,
      project_id INTEGER NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
      name       TEXT NOT NULL,
      kind       TEXT NOT NULL DEFAULT 'general',
      sort_order INTEGER NOT NULL DEFAULT 0,
      created_at TIMESTAMPTZ NOT NULL DEFAULT now()
    );

    -- 하부 업무 / 작업오더. work_order 메뉴에서는 아래 세부 항목을 사용
    CREATE TABLE IF NOT EXISTS tasks (
      id                  SERIAL PRIMARY KEY,
      menu_id             INTEGER NOT NULL REFERENCES menus(id) ON DELETE CASCADE,
      title               TEXT NOT NULL,
      order_number        TEXT NOT NULL DEFAULT '',
      functional_location TEXT NOT NULL DEFAULT '',
      fme_grade           TEXT NOT NULL DEFAULT '',
      hazard_factors      TEXT NOT NULL DEFAULT '',
      note                TEXT NOT NULL DEFAULT '',
      status              TEXT NOT NULL DEFAULT 'todo',
      sort_order          INTEGER NOT NULL DEFAULT 0,
      created_at          TIMESTAMPTZ NOT NULL DEFAULT now()
    );

    -- 일별 작업사항
    CREATE TABLE IF NOT EXISTS logs (
      id         SERIAL PRIMARY KEY,
      task_id    INTEGER NOT NULL REFERENCES tasks(id) ON DELETE CASCADE,
      log_date   TEXT NOT NULL,
      content    TEXT NOT NULL,
      author     TEXT NOT NULL DEFAULT '',
      created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
      updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
    );

    -- 동시 접근 방지용 잠금 (한 번에 한 명만 개별 업무 접근)
    ALTER TABLE tasks ADD COLUMN IF NOT EXISTS locked_by    TEXT;
    ALTER TABLE tasks ADD COLUMN IF NOT EXISTS locked_by_id TEXT;
    ALTER TABLE tasks ADD COLUMN IF NOT EXISTS locked_at    TIMESTAMPTZ;

    -- 산업안전/품질/비계/중량물/설계·감독 세부 항목
    ALTER TABLE tasks ADD COLUMN IF NOT EXISTS quality_grade   TEXT NOT NULL DEFAULT '';  -- 품질등급 Q/A/S
    ALTER TABLE tasks ADD COLUMN IF NOT EXISTS quality_witness TEXT NOT NULL DEFAULT '';  -- 품질입회
    ALTER TABLE tasks ADD COLUMN IF NOT EXISTS scaffold        TEXT NOT NULL DEFAULT '';  -- 비계설치 Y/N
    ALTER TABLE tasks ADD COLUMN IF NOT EXISTS scaffold_height TEXT NOT NULL DEFAULT '';  -- 비계 높이
    ALTER TABLE tasks ADD COLUMN IF NOT EXISTS heavy_weight    TEXT NOT NULL DEFAULT '';  -- (구)중량물 무게
    ALTER TABLE tasks ADD COLUMN IF NOT EXISTS lifting_gear    TEXT NOT NULL DEFAULT '';  -- (구)인양장구(JSON 배열)
    ALTER TABLE tasks ADD COLUMN IF NOT EXISTS heavy_items     TEXT NOT NULL DEFAULT '';  -- 중량물 목록(JSON: [{name,weight,gear}])
    ALTER TABLE tasks ADD COLUMN IF NOT EXISTS designer        TEXT NOT NULL DEFAULT '';  -- 설계자
    ALTER TABLE tasks ADD COLUMN IF NOT EXISTS supervisor      TEXT NOT NULL DEFAULT '';  -- 감독자

    -- 일별 작업사항의 당일 위험요인/취급중량물/안전등급
    ALTER TABLE logs ADD COLUMN IF NOT EXISTS hazards       TEXT NOT NULL DEFAULT '';  -- 당일 위험요인(콤마)
    ALTER TABLE logs ADD COLUMN IF NOT EXISTS heavy_handled TEXT NOT NULL DEFAULT '';  -- 당일 취급 중량물(JSON)
    ALTER TABLE logs ADD COLUMN IF NOT EXISTS grade         TEXT NOT NULL DEFAULT '';  -- 안전등급 A/B/C
    ALTER TABLE logs ADD COLUMN IF NOT EXISTS work_height   TEXT NOT NULL DEFAULT '';  -- 고소작업 높이(당일)
    ALTER TABLE logs ADD COLUMN IF NOT EXISTS actual        TEXT NOT NULL DEFAULT '';  -- 실제 작업 사항(계획 대비)

    CREATE INDEX IF NOT EXISTS idx_menus_project ON menus(project_id);
    CREATE INDEX IF NOT EXISTS idx_tasks_menu    ON tasks(menu_id);
    CREATE INDEX IF NOT EXISTS idx_logs_task     ON logs(task_id, log_date);
  `);
  console.log('DB 초기화 완료');
}
