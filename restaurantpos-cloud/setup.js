import pg from 'pg';
import {scryptSync,randomBytes} from 'node:crypto';
const keys=['OWNER_EMAIL','OWNER_PASSWORD','OWNER_NAME','RESTAURANT_NAME'];
const filled=keys.filter(k=>process.env[k]);
if(!filled.length){console.log('Owner bootstrap not configured; no user or credentials created.');process.exit(0);}
if(filled.length!==4)throw new Error('All OWNER_* and RESTAURANT_NAME values required for bootstrap');
const email=process.env.OWNER_EMAIL.trim().toLowerCase(),pw=process.env.OWNER_PASSWORD;
if(!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email)||pw.length<12)throw new Error('Invalid owner email or password');
const pool=new pg.Pool({connectionString:process.env.DATABASE_URL,ssl:{rejectUnauthorized:true}});
const cx=await pool.connect();
try{await cx.query('BEGIN');const already=await cx.query('SELECT count(*)::int AS n FROM restaurantpos.staff');
 if(already.rows[0].n){await cx.query('COMMIT');console.log('Owner/staff already exist; initial bootstrap skipped.');}
 else{const salt=randomBytes(16).toString('hex'),secret='scrypt$'+salt+'$'+scryptSync(pw,salt,64).toString('hex');
 const r=await cx.query('INSERT INTO restaurantpos.restaurants(name) VALUES($1) RETURNING id',[process.env.RESTAURANT_NAME]);
 await cx.query('INSERT INTO restaurantpos.staff(restaurant_id,full_name,email,password_hash,role) VALUES($1,$2,$3,$4,$5)',[r.rows[0].id,process.env.OWNER_NAME,email,secret,'owner']);
 await cx.query('COMMIT');console.log('First owner created. Remove OWNER_PASSWORD from Render immediately.');}
}catch(e){await cx.query('ROLLBACK');throw e;}finally{cx.release();await pool.end();}
