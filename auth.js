import crypto from 'node:crypto';

const PASSWORD = process.env.APP_PASSWORD || '';
const SECRET = process.env.APP_SECRET || PASSWORD || 'dev-secret';
export const AUTH_ENABLED = PASSWORD.length > 0;

// 비밀번호로부터 결정적 토큰 생성 (쿠키에 저장). 비밀번호가 바뀌면 토큰도 바뀜.
const TOKEN = crypto.createHmac('sha256', SECRET).update('scheduler-auth:' + PASSWORD).digest('hex');
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

export function checkPassword(input) {
  if (!AUTH_ENABLED) return true;
  const a = Buffer.from(String(input || ''));
  const b = Buffer.from(PASSWORD);
  return a.length === b.length && crypto.timingSafeEqual(a, b);
}

export function setAuthCookie(res) {
  const attrs = [
    `${COOKIE}=${TOKEN}`,
    'Path=/',
    'HttpOnly',
    'SameSite=Lax',
    'Max-Age=' + 60 * 60 * 24 * 30, // 30일
  ];
  if (process.env.NODE_ENV === 'production') attrs.push('Secure');
  res.setHeader('Set-Cookie', attrs.join('; '));
}

export function clearAuthCookie(res) {
  res.setHeader('Set-Cookie', `${COOKIE}=; Path=/; HttpOnly; Max-Age=0`);
}

export function isAuthed(req) {
  if (!AUTH_ENABLED) return true;
  return parseCookies(req)[COOKIE] === TOKEN;
}

// /api/* 보호 미들웨어 (로그인/세션 엔드포인트는 예외)
export function requireAuth(req, res, next) {
  if (isAuthed(req)) return next();
  res.status(401).json({ error: '인증이 필요합니다.', auth_required: true });
}
