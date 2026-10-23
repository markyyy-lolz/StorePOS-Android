import './config.js';
import pg from 'pg';
import {hashPassword,requiredString} from './lib.js';
const {Pool}=pg;
const db=new Pool({connectionString:process.env.DATABASE_URL,ssl:{rejectUnauthorized:true}});
const email=(process.env.OWNER_EMAIL||'').trim().toLowerCase();
const password=process.env.OWNER_PASSWORD||'';
const owner=requiredString(process.env.OWNER_NAME||'Restaurant Owner',120);
const restaurant=requiredString(process.env.RESTAURANT_NAME||'My Restaurant',140);
if(!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email))throw new Error('Valid OWNER_EMAIL required.');
if(password.length<12 || password==='replace-with-a-strong-password') throw new Error('Choose a strong OWNER_PASSWORD (12+ characters).');
const conn=await db.connect();
try{
 await conn.query('BEGIN');
 const any=await conn.query('SELECT count(*)::int AS n FROM restaurantpos.staff');
 if(any.rows[0].n>0) throw new Error('Setup blocked: staff already exist. Do not run initial bootstrap twice.');
 const res=await conn.query('INSERT INTO restaurantpos.restaurants(name) VALUES ($1) RETURNING id',[restaurant]);
 await conn.query('INSERT INTO restaurantpos.staff(restaurant_id,full_name,email,password_hash,role) VALUES($1,$2,$3,$4,$5)',[res.rows[0].id,owner,email,hashPassword(password),'owner']);
 await conn.query('COMMIT');
 console.log(`Owner created for ${restaurant} (${email}). Please remove OWNER_PASSWORD from production environment.`);
}catch(error){await conn.query('ROLLBACK');console.error(error.message);process.exitCode=1;}
finally{conn.release();await db.end();}
