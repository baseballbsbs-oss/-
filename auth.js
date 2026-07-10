import crypto from 'node:crypto';

const PASSWORD = process.env.APP_PASSWORD || '';
const ADMIN_PASSWORD = process.env.ADMIN_PASSWORD || '';
const SECRET = process.env.APP_SECRET || PASSWORD || 'dev-secret';

export const AUTH_ENABLED = PASSWORD.length > 0;
export const ADMIN_ENABLED = ADMIN_PASSWORD.length > 0;

// 역할별 결정적 토큰 (쿠키에 저장). 비밀번호가 바뀌면 토큰도 바뀜.
const tokenFor = (role, pw) => crypto.createHmac('sha256', SECRET).update(`scheduler:${role}:${pw}`).digest('hex');
const USER_TOKEN = tokenFor('user', PASSWORD);
const ADMIN_TOKEN = tokenFor('admin', ADMIN_PASSWORD);
const COOKIE = 'sched_auth';

function parseCookies(req) {
  const out = {};
  const raw = req.headers.cookie;
  if (!raw) return out;
  for (const part of raw.split(';')) {
    const i = part.indexOf('=');
    if (i > -1) out[part.slice(0, i).trim()] = decodeURIComponent(part.slice(i + 1).trim());
  }
  return out;
}

function eq(a, b) {
  const x = Buffer.from(String(a || ''));
  const y = Buffer.from(String(b || ''));
  return x.length === y.length && crypto.timingSafeEqual(x, y);
}

// 비밀번호 검증 → 부여할 역할 반환 ('admin' | 'user' | null)
export function roleForPassword(input) {
  if (ADMIN_ENABLED && eq(input, ADMIN_PASSWORD)) return 'admin';
  if (AUTH_ENABLED && eq(input, PASSWORD)) return 'user';
  return null;
}

export function setAuthCookie(res, role) {
  const token = role === 'admin' ? ADMIN_TOKEN : USER_TOKEN;
  const attrs = [
    `${COOKIE}=${token}`, 'Path=/', 'HttpOnly', 'SameSite=Lax',
    'Max-Age=' + 60 * 60 * 24 * 30, // 30일
  ];
  if (process.env.NODE_ENV === 'production') attrs.push('Secure');
  res.setHeader('Set-Cookie', attrs.join('; '));
}

export function clearAuthCookie(res) {
  res.setHeader('Set-Cookie', `${COOKIE}=; Path=/; HttpOnly; Max-Age=0`);
}

export function isAdmin(req) {
  if (!AUTH_ENABLED) return true;            // 로컬(무인증) 모드에서는 관리자 취급
  if (!ADMIN_ENABLED) return false;
  return parseCookies(req)[COOKIE] === ADMIN_TOKEN;
}

export function isAuthed(req) {
  if (!AUTH_ENABLED) return true;
  const c = parseCookies(req)[COOKIE];
  return c === USER_TOKEN || c === ADMIN_TOKEN;
}

// /api/* 보호 미들웨어
export function requireAuth(req, res, next) {
  if (isAuthed(req)) return next();
  res.status(401).json({ error: '인증이 필요합니다.', auth_required: true });
}

// 관리자 전용 미들웨어
export function requireAdmin(req, res, next) {
  if (isAdmin(req)) return next();
  res.status(403).json({ error: '관리자 권한이 필요합니다.', admin_required: true });
}
