import { readFileSync, existsSync } from 'node:fs';
// Simple local .env loader; deployers should configure real environment variables on server.
if(existsSync('.env')) for(const line of readFileSync('.env','utf8').split(/\r?\n/)) {
  const trimmed=line.trim();
  if(!trimmed||trimmed.startsWith('#')) continue;
  const idx=trimmed.indexOf('='); if(idx<1)continue;
  const key=trimmed.slice(0,idx).trim(),val=trimmed.slice(idx+1).trim().replace(/^(['"])(.*)\1$/,'$2');
  if(!process.env[key])process.env[key]=val;
}
if (!process.env.DATABASE_URL) throw new Error('DATABASE_URL required (set in environment or .env).');
