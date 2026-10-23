import './config.js';
import {createServer} from 'node:http';
import {readFile} from 'node:fs/promises';
import {resolve, extname} from 'node:path';
import {randomUUID} from 'node:crypto';
import {verifyWebhook,paymongo,paidPayment} from './paymongo.js';
import {readRawBody} from './request-body.js';
import {cashSettlementDecision} from './payment-policy.js';
import pg from 'pg';
import {verifyPassword,sessionToken,tokenHash,requiredString,minorFromPesos,calculateTotals,receiptText} from './lib.js';
const {Pool}=pg;
const db=new Pool({connectionString:process.env.DATABASE_URL,ssl:{rejectUnauthorized:true},max:10,connectionTimeoutMillis:8000});
const PORT=Number(process.env.PORT||3000);
const ROOT=resolve('public');
const attempts=new Map();
const bad=(message,status=400)=>Object.assign(new Error(message),{status});
const uuid=x=>typeof x==='string' && /^[a-f0-9]{8}-[a-f0-9]{4}-[1-8][a-f0-9]{3}-[89ab][a-f0-9]{3}-[a-f0-9]{12}$/i.test(x);
function json(res,data,status=200){res.writeHead(status,{'Content-Type':'application/json; charset=utf-8','Cache-Control':'no-store'});res.end(JSON.stringify(data));}
function cookieValue(req,name){const all=(req.headers.cookie||'').split(';').map(s=>s.trim());return all.find(s=>s.startsWith(name+'='))?.slice(name.length+1)||'';}
async function body(req){
 if(!String(req.headers['content-type']||'').startsWith('application/json'))throw bad('JSON body required.',415);
 const s=await readRawBody(req,80000);
 try{const v=JSON.parse(s);if(!v||Array.isArray(v)||typeof v!=='object')throw 0;return v;}
 catch{throw bad('Invalid JSON.');}
}
async function auth(req){const tok=cookieValue(req,'rpos_session');if(!/^[a-f0-9]{64}$/.test(tok))throw bad('Please sign in.',401);
 const q=await db.query(`SELECT s.id,s.restaurant_id,s.full_name,s.email,s.role,r.name AS restaurant_name,r.address,r.phone,r.service_charge_percent
  FROM restaurantpos.sessions x JOIN restaurantpos.staff s ON x.staff_id=s.id
  JOIN restaurantpos.restaurants r ON r.id=s.restaurant_id
  WHERE x.token_hash=$1 AND x.expires_at>now() AND s.active=true`,[tokenHash(tok)]);
 if(!q.rowCount)throw bad('Session expired. Please sign in again.',401);return q.rows[0];
}
function guard(user,roles){if(!roles.includes(user.role))throw bad('Your role does not have permission.',403);}
async function menu(user){const c=await db.query('SELECT id,name,sort_order FROM restaurantpos.categories WHERE restaurant_id=$1 ORDER BY sort_order,name',[user.restaurant_id]);
 const m=await db.query('SELECT id,name,description,category_id,price_minor,active FROM restaurantpos.menu_items WHERE restaurant_id=$1 ORDER BY name',[user.restaurant_id]);
 return {categories:c.rows,items:m.rows};}
async function fetchOrder(user,id){if(!uuid(id))throw bad('Invalid order ID.');
 const r=await db.query(`SELECT o.*,t.label AS table_label,st.full_name AS cashier_name FROM restaurantpos.orders o
  LEFT JOIN restaurantpos.dining_tables t ON t.id=o.table_id
  JOIN restaurantpos.staff st ON st.id=o.created_by
  WHERE o.restaurant_id=$1 AND o.id=$2`,[user.restaurant_id,id]);
 if(!r.rowCount)throw bad('Order not found.',404);
 const items=await db.query('SELECT item_name,unit_price_minor,quantity,notes,line_total_minor FROM restaurantpos.order_items WHERE restaurant_id=$1 AND order_id=$2 ORDER BY id',[user.restaurant_id,id]);
 const pay=await db.query('SELECT method,amount_minor,tendered_minor,change_minor,reference,paid_at FROM restaurantpos.payments WHERE restaurant_id=$1 AND order_id=$2',[user.restaurant_id,id]);
 return {order:r.rows[0],items:items.rows,payment:pay.rows[0]||null};
}
async function routes(req,res,url){const path=url.pathname;const method=req.method;
 if(path==='/api/webhooks/paymongo'&&method==='POST'){
  if(!process.env.PAYMONGO_WEBHOOK_SECRET || !process.env.PAYMONGO_SECRET_KEY)throw bad('Webhook not configured.',503);
  const raw=await readRawBody(req,100000);
  let payload;try{payload=JSON.parse(raw);}catch{throw bad('Invalid webhook.',400);}
  const root=payload?.data, a=root?.attributes;
  const eventType=a?.type||root?.type;
  const liveMode=String(process.env.PAYMONGO_SECRET_KEY).startsWith('sk_live_');
  if(!verifyWebhook(raw,req.headers['paymongo-signature'],process.env.PAYMONGO_WEBHOOK_SECRET,liveMode))throw bad('Invalid webhook signature.',403);
  if(a?.livemode!==liveMode)throw bad('Webhook key-mode mismatch.',403);
  if(eventType==='checkout_session.payment.paid'){
   const sessionId=a?.data?.id||root?.data?.id;
   if(typeof sessionId!=='string' || !/^cs_[A-Za-z0-9]+$/.test(sessionId))throw bad('Invalid checkout session.',400);
   await reconcileGatewaySession(sessionId);
  }
  return json(res,{received:true});
 }

 if(path==='/api/health' && method==='GET'){
  try{await db.query('SELECT 1');return json(res,{status:'ok',database:'connected'});}catch{return json(res,{status:'degraded',database:'unavailable'},503);}
 }
 if(path==='/api/login' && method==='POST'){
  if(req.headers.origin){let host;try{host=new URL(req.headers.origin).host;}catch{throw bad('Invalid origin.',403);}
   if(host!==req.headers.host)throw bad('Cross-origin request rejected.',403);}

  const addr=req.socket.remoteAddress||'unknown',bucket=attempts.get(addr)||{count:0,until:0};
  if(attempts.size>10000)for(const [key,entry] of attempts){if(entry.until<Date.now())attempts.delete(key);}
  if(Date.now()>bucket.until){bucket.count=0;bucket.until=Date.now()+15*60*1000;}
  if(bucket.count>=8)throw bad('Too many attempts. Try again in 15 minutes.',429);
  const b=await body(req);const email=String(b.email||'').trim().toLowerCase();
  const u=await db.query('SELECT * FROM restaurantpos.staff WHERE email=$1 AND active=true',[email]);
  if(!u.rowCount || !verifyPassword(String(b.password||''),u.rows[0].password_hash)){
   attempts.set(addr,{count:bucket.count+1,until:bucket.until});throw bad('Incorrect login details.',401);
  }
  attempts.delete(addr);
  const tok=sessionToken();await db.query("INSERT INTO restaurantpos.sessions(token_hash,staff_id,expires_at) VALUES($1,$2,now()+interval '12 hours')",[tokenHash(tok),u.rows[0].id]);
  res.setHeader('Set-Cookie',`rpos_session=${tok}; HttpOnly; SameSite=Lax; Path=/; Max-Age=43200${process.env.NODE_ENV==='production'?'; Secure':''}`);
  return json(res,{success:true});
 }
 if(path.startsWith('/api/') && method!=='GET'){
  const origin=req.headers.origin;
  if(origin){let host;try{host=new URL(origin).host;}catch{throw bad('Invalid origin.',403);}
   if(host!==req.headers.host)throw bad('Cross-origin request rejected.',403);
  }
 }
 if(!path.startsWith('/api/'))return serve(res,path);
 const user=await auth(req),tenant=user.restaurant_id;
 if(path==='/api/me' && method==='GET')return json(res,{user:{name:user.full_name,email:user.email,role:user.role},restaurant:{id:tenant,name:user.restaurant_name,address:user.address,phone:user.phone,service_charge_percent:user.service_charge_percent}});
 if(path==='/api/logout' && method==='POST'){
  await db.query('DELETE FROM restaurantpos.sessions WHERE token_hash=$1',[tokenHash(cookieValue(req,'rpos_session'))]);
  res.setHeader('Set-Cookie','rpos_session=; HttpOnly; SameSite=Lax; Path=/; Max-Age=0');return json(res,{success:true});
 }
 if(path==='/api/menu' && method==='GET')return json(res,await menu(user));
 if(path==='/api/categories' && method==='POST'){
  guard(user,['owner','manager']);const b=await body(req);
  const r=await db.query('INSERT INTO restaurantpos.categories(restaurant_id,name) VALUES($1,$2) RETURNING *',[tenant,requiredString(b.name,80)]);return json(res,r.rows[0],201);
 }
 if(path==='/api/menu' && method==='POST'){
  guard(user,['owner','manager']);const b=await body(req);const price=minorFromPesos(b.price);
  const category=b.category_id||null;if(category&&!uuid(category))throw bad('Invalid category ID.');
  const r=await db.query(`INSERT INTO restaurantpos.menu_items(restaurant_id,category_id,name,description,price_minor)
   SELECT $1,c.id,$3,$4,$5 FROM (SELECT 1) AS base LEFT JOIN restaurantpos.categories c ON c.restaurant_id=$1 AND c.id=$2
   WHERE $2::uuid IS NULL OR c.id IS NOT NULL RETURNING id`,[tenant,category,requiredString(b.name,140),String(b.description||'').slice(0,240),price]);
  if(!r.rowCount)throw bad('Category not found.');return json(res,{id:r.rows[0].id},201);
 }
 if(/^\/api\/menu\/[a-f0-9-]+$/i.test(path)&&method==='PATCH'){
  guard(user,['owner','manager']);const b=await body(req),id=path.split('/')[3];if(!uuid(id))throw bad('Invalid menu ID.');
  const r=await db.query(`UPDATE restaurantpos.menu_items SET name=$3,price_minor=$4,description=$5,active=$6
   WHERE restaurant_id=$1 AND id=$2 RETURNING id`,[tenant,id,requiredString(b.name,140),minorFromPesos(b.price),String(b.description||'').slice(0,240),b.active===true]);
  if(!r.rowCount)throw bad('Item not found.',404);return json(res,{success:true});
 }
 if(path==='/api/tables' && method==='GET'){
  const r=await db.query(`SELECT t.id,t.label,t.seats,t.active,EXISTS(SELECT 1 FROM restaurantpos.orders o WHERE o.restaurant_id=t.restaurant_id AND o.table_id=t.id AND o.status='open') AS occupied
   FROM restaurantpos.dining_tables t WHERE t.restaurant_id=$1 ORDER BY t.label`,[tenant]);return json(res,r.rows);
 }
 if(path==='/api/tables' && method==='POST'){
  guard(user,['owner','manager']);const b=await body(req),seats=Number(b.seats||2);
  if(!Number.isInteger(seats)||seats<1||seats>40)throw bad('Seats must be 1–40.');
  const r=await db.query('INSERT INTO restaurantpos.dining_tables(restaurant_id,label,seats) VALUES($1,$2,$3) RETURNING id',[tenant,requiredString(b.label,40),seats]);return json(res,r.rows[0],201);
 }
 if(path==='/api/dashboard' && method==='GET'){
  const r=await db.query(`SELECT
   (SELECT count(*)::int FROM restaurantpos.orders WHERE restaurant_id=$1 AND status='open') AS open_orders,
   (SELECT count(*)::int FROM restaurantpos.orders WHERE restaurant_id=$1 AND kitchen_status IN('queued','preparing') AND status<>'cancelled') AS kitchen_pending,
   (SELECT count(*)::int FROM restaurantpos.dining_tables WHERE restaurant_id=$1 AND active=true) AS tables,
   (SELECT count(*)::int FROM restaurantpos.orders WHERE restaurant_id=$1 AND status='paid' AND (paid_at AT TIME ZONE 'Asia/Manila')::date=(now() AT TIME ZONE 'Asia/Manila')::date) AS transactions_today,
   (SELECT coalesce(sum(total_minor),0)::bigint FROM restaurantpos.orders WHERE restaurant_id=$1 AND status='paid' AND (paid_at AT TIME ZONE 'Asia/Manila')::date=(now() AT TIME ZONE 'Asia/Manila')::date) AS sales_today`,[tenant]);
  return json(res,r.rows[0]);
 }
 if(path==='/api/orders' && method==='GET'){
  const filter=url.searchParams.get('status')||'active';const clause=filter==='paid'?"o.status='paid'":filter==='all'?"TRUE":"(o.status='open' OR (o.status='paid' AND o.kitchen_status<>'served'))";
  const r=await db.query(`SELECT o.id,o.order_number,o.service_type,o.status,o.kitchen_status,o.total_minor,o.created_at,o.paid_at,t.label AS table_label,
   (SELECT count(*)::int FROM restaurantpos.order_items i WHERE i.restaurant_id=o.restaurant_id AND i.order_id=o.id) AS item_lines
   FROM restaurantpos.orders o LEFT JOIN restaurantpos.dining_tables t ON o.table_id=t.id
   WHERE o.restaurant_id=$1 AND ${clause} ORDER BY o.created_at DESC LIMIT 150`,[tenant]);return json(res,r.rows);
 }
 if(path==='/api/orders' && method==='POST'){
  guard(user,['owner','manager','cashier']);const b=await body(req);
  if(!uuid(b.client_request_id))throw bad('Missing request ID.');
  if(!Array.isArray(b.items)||b.items.length<1||b.items.length>60)throw bad('Add 1–60 menu lines.');
  if(b.service_type!=='takeout'&&b.service_type!=='dine_in')throw bad('Choose dine-in or takeout.');
  const tableId=b.service_type==='dine_in'?(b.table_id||null):null;
  if(tableId&&!uuid(tableId))throw bad('Invalid table.');
  const discount=b.discount_minor??0;
  if(discount!==0)guard(user,['owner','manager']);
  const cx=await db.connect();try{await cx.query('BEGIN');
   const old=await cx.query('SELECT id FROM restaurantpos.orders WHERE restaurant_id=$1 AND client_request_id=$2',[tenant,b.client_request_id]);
   if(old.rowCount){await cx.query('COMMIT');return json(res,await fetchOrder(user,old.rows[0].id));}
   const rest=await cx.query('SELECT service_charge_percent FROM restaurantpos.restaurants WHERE id=$1',[tenant]);
   if(tableId){const table=await cx.query('SELECT id FROM restaurantpos.dining_tables WHERE restaurant_id=$1 AND id=$2 AND active=true',[tenant,tableId]);if(!table.rowCount)throw bad('Dining table not found.');}
   const ids=[...new Set(b.items.map(i=>i.menu_item_id))];if(!ids.every(uuid))throw bad('Invalid menu item ID.');
   const products=await cx.query('SELECT id,name,price_minor,active FROM restaurantpos.menu_items WHERE restaurant_id=$1 AND id=ANY($2::uuid[])',[tenant,ids]);
   const totals=calculateTotals(b.items,new Map(products.rows.map(row=>[row.id,row])),discount,rest.rows[0].service_charge_percent);
   const o=await cx.query(`INSERT INTO restaurantpos.orders(restaurant_id,client_request_id,table_id,service_type,notes,subtotal_minor,discount_minor,service_charge_minor,total_minor,created_by)
    VALUES($1,$2,$3,$4,$5,$6,$7,$8,$9,$10) ON CONFLICT (restaurant_id,client_request_id) DO NOTHING RETURNING id`,
   [tenant,b.client_request_id,tableId,b.service_type,String(b.notes||'').slice(0,300),totals.subtotal_minor,totals.discount_minor,totals.service_charge_minor,totals.total_minor,user.id]);
   if(!o.rowCount){const existing=await cx.query('SELECT id FROM restaurantpos.orders WHERE restaurant_id=$1 AND client_request_id=$2',[tenant,b.client_request_id]);await cx.query('COMMIT');return json(res,await fetchOrder(user,existing.rows[0].id));}
   for(const line of totals.lines) await cx.query(`INSERT INTO restaurantpos.order_items(restaurant_id,order_id,menu_item_id,item_name,unit_price_minor,quantity,notes,line_total_minor) VALUES($1,$2,$3,$4,$5,$6,$7,$8)`,
    [tenant,o.rows[0].id,line.menu_item_id,line.item_name,line.unit_price_minor,line.quantity,line.notes,line.line_total_minor]);
   await cx.query('COMMIT');return json(res,await fetchOrder(user,o.rows[0].id),201);
  }catch(e){await cx.query('ROLLBACK');throw e;}finally{cx.release();}
 }
 // PayMongo sessions are created ONLY on the backend, never on the device.
 const checkout=path.match(/^\/api\/orders\/([a-f0-9-]+)\/(paymongo-checkout|paymongo-status)$/i);
 if(checkout){
  const id=checkout[1];if(!uuid(id))throw bad('Invalid order ID.');
  guard(user,['owner','manager','cashier']);
  if(checkout[2]==='paymongo-checkout' && method==='POST'){
   if(!process.env.PUBLIC_BASE_URL || !/^https:\/\/[^\s]+$/.test(process.env.PUBLIC_BASE_URL))throw bad('Public HTTPS URL not configured.',503);
   const cx=await db.connect();try{
    await cx.query('BEGIN');
    const existingOrder=await cx.query(`SELECT id,status,total_minor,order_number FROM restaurantpos.orders
       WHERE restaurant_id=$1 AND id=$2 FOR UPDATE`,[tenant,id]);
    if(!existingOrder.rowCount)throw bad('Order not found.',404);
    const order=existingOrder.rows[0];
    if(order.status!=='open')throw bad('Order is not open.',409);
    if(order.total_minor<=0)throw bad('Cannot create checkout for a zero-value order.',409);
    const old=await cx.query(`SELECT session_id,checkout_url FROM restaurantpos.gateway_checkouts
       WHERE restaurant_id=$1 AND order_id=$2`,[tenant,id]);
    if(old.rowCount){await cx.query('COMMIT');return json(res,{checkout_url:old.rows[0].checkout_url,reused:true});}
    const attr={line_items:[{name:'Restaurant order #'+order.order_number,amount:order.total_minor,quantity:1,currency:'PHP'}],
       payment_method_types:['card','gcash','paymaya'],
       success_url:process.env.PUBLIC_BASE_URL+'/?payment=success',
       cancel_url:process.env.PUBLIC_BASE_URL+'/?payment=cancel',
       reference_number:id,send_email_receipt:false};
    const result=await paymongo('POST','/v2/checkout_sessions',{data:{attributes:attr}});
    const sessionId=result.id,link=result.attributes?.checkout_url;
    if(!/^cs_[A-Za-z0-9]+$/.test(sessionId)||!/^https:\/\/checkout\.paymongo\.com\//.test(link))throw bad('Invalid PayMongo session.',502);
    await cx.query(`INSERT INTO restaurantpos.gateway_checkouts(restaurant_id,order_id,session_id,checkout_url,amount_minor)
      VALUES($1,$2,$3,$4,$5)`,[tenant,id,sessionId,link,order.total_minor]);
    await cx.query('COMMIT');return json(res,{checkout_url:link,reused:false},201);
   }catch(e){await cx.query('ROLLBACK');throw e;}finally{cx.release();}
  }
  if(checkout[2]==='paymongo-status' && method==='GET'){
    const r=await db.query(`SELECT session_id FROM restaurantpos.gateway_checkouts WHERE restaurant_id=$1 AND order_id=$2`,[tenant,id]);
    if(!r.rowCount)throw bad('No checkout session.',404);
    return json(res,await reconcileGatewaySession(r.rows[0].session_id));
  }
 }
 const matches=path.match(/^\/api\/orders\/([a-f0-9-]+)(?:\/(kitchen|payment|receipt))?$/i);
 if(matches){const id=matches[1],action=matches[2]||'';
  if(!uuid(id))throw bad('Invalid order ID.');
  if(!action && method==='GET')return json(res,await fetchOrder(user,id));
  if(action==='receipt' && method==='GET'){
   const doc=await fetchOrder(user,id);return json(res,{text:receiptText({restaurant:{name:user.restaurant_name,address:user.address},...doc}),...doc});
  }
  if(action==='kitchen' && method==='PATCH'){
   guard(user,['owner','manager','kitchen']);const b=await body(req),desired=b.status;
   if(!['queued','preparing','ready','served'].includes(desired))throw bad('Invalid kitchen status.');
   const stages=['queued','preparing','ready','served'];
   const old=await db.query('SELECT kitchen_status FROM restaurantpos.orders WHERE id=$1 AND restaurant_id=$2 AND status<>\'cancelled\'',[id,tenant]);
   if(!old.rowCount)throw bad('Order not found.',404);
   if(stages.indexOf(desired)<stages.indexOf(old.rows[0].kitchen_status)&&!['owner','manager'].includes(user.role))throw bad('Cannot move kitchen status backward.',403);
   await db.query('UPDATE restaurantpos.orders SET kitchen_status=$3 WHERE id=$1 AND restaurant_id=$2',[id,tenant,desired]);return json(res,{success:true});
  }
  if(action==='payment' && method==='POST'){
   guard(user,['owner','manager','cashier']);const b=await body(req),method=b.method;
   if(method!=='cash')throw bad('Use the verified PayMongo checkout for e-wallet or card payments.');
   const cx=await db.connect();try{await cx.query('BEGIN');
    const order=await cx.query('SELECT status,total_minor FROM restaurantpos.orders WHERE id=$1 AND restaurant_id=$2 FOR UPDATE',[id,tenant]);
    if(!order.rowCount)throw bad('Order not found.',404);
    // Pending PayMongo checkout and cash confirmation must never race to settle the same order.
    const gateway=await cx.query(`SELECT status FROM restaurantpos.gateway_checkouts
       WHERE restaurant_id=$1 AND order_id=$2 FOR UPDATE`,[tenant,id]);
    const decision=cashSettlementDecision(order.rows[0].status,gateway.rows[0]?.status);
    if(decision==='already_paid'){await cx.query('COMMIT');return json(res,await fetchOrder(user,id));}
    if(decision==='gateway_review')throw bad('A PayMongo checkout exists. Verify its payment status and resolve with a manager before collecting cash.',409);
    if(decision!=='allowed')throw bad('Order cannot be paid.',409);
    const total=order.rows[0].total_minor,tendered=method==='cash'?b.tendered_minor:total;
    if(!Number.isInteger(tendered)||tendered<total||tendered>100000000)throw bad('Payment is insufficient or invalid.');
    await cx.query('INSERT INTO restaurantpos.payments(restaurant_id,order_id,method,amount_minor,tendered_minor,change_minor,reference,recorded_by) VALUES($1,$2,$3,$4,$5,$6,$7,$8)',
     [tenant,id,method,total,tendered,tendered-total,String(b.reference||'').slice(0,100),user.id]);
    await cx.query("UPDATE restaurantpos.orders SET status='paid',paid_at=now() WHERE id=$1 AND restaurant_id=$2",[id,tenant]);
    await cx.query('COMMIT');return json(res,await fetchOrder(user,id));
   }catch(e){await cx.query('ROLLBACK');throw e;}finally{cx.release();}
  }
 }
 if(path==='/api/reports'&&method==='GET'){
  guard(user,['owner','manager']);let day=url.searchParams.get('date')||'';
  if(!/^\d{4}-\d{2}-\d{2}$/.test(day))day=new Intl.DateTimeFormat('en-CA',{timeZone:'Asia/Manila',year:'numeric',month:'2-digit',day:'2-digit'}).format(new Date());
  const r=await db.query(`SELECT count(*)::int AS transactions,coalesce(sum(total_minor),0)::bigint AS gross_sales_minor,
   coalesce(sum(discount_minor),0)::bigint AS discounts_minor,coalesce(sum(service_charge_minor),0)::bigint AS service_charge_minor
   FROM restaurantpos.orders WHERE restaurant_id=$1 AND status='paid' AND (paid_at AT TIME ZONE 'Asia/Manila')::date=$2::date`,[tenant,day]);
  const p=await db.query(`SELECT method,sum(amount_minor)::bigint AS total_minor,count(*)::int AS count FROM restaurantpos.payments WHERE restaurant_id=$1 AND (paid_at AT TIME ZONE 'Asia/Manila')::date=$2::date GROUP BY method`,[tenant,day]);
  return json(res,{date:day,...r.rows[0],payments:p.rows});
 }
 if(path==='/api/settings'&&method==='PATCH'){
  guard(user,['owner','manager']);const b=await body(req);
  const pct=Number(b.service_charge_percent);
  if(!/^(?:0|[1-9]|1[0-9]|20)(?:\.\d{1,2})?$/.test(String(b.service_charge_percent))||pct<0||pct>20)throw bad('Service charge must be 0–20%, up to two decimals.');
  const r=await db.query('UPDATE restaurantpos.restaurants SET name=$2,address=$3,phone=$4,service_charge_percent=$5 WHERE id=$1 RETURNING name,address,phone,service_charge_percent',
   [tenant,requiredString(b.name,140),String(b.address||'').slice(0,250),String(b.phone||'').slice(0,40),pct]);return json(res,r.rows[0]);
 }
 throw bad('API route not found.',404);
}
async function serve(res,path){
 const files={'/':'index.html','/app.js':'app.js','/styles.css':'styles.css'};
 const file=files[path];if(!file)throw bad('Not found.',404);
 const buf=await readFile(resolve(ROOT,file));
 const types={'.html':'text/html; charset=utf-8','.css':'text/css; charset=utf-8','.js':'application/javascript; charset=utf-8'};
 res.writeHead(200,{'Content-Type':types[extname(file)],'Cache-Control':'no-store'});res.end(buf);
}
async function reconcileGatewaySession(sessionId){
 const session=await paymongo('GET','/v1/checkout_sessions/'+encodeURIComponent(sessionId));
 if(session.id!==sessionId)throw bad('Checkout ID mismatch.',502);
 const cx=await db.connect();try{
  await cx.query('BEGIN');
  const lookup=await cx.query(`SELECT g.restaurant_id,g.order_id,g.amount_minor,g.status,o.status AS order_status,o.total_minor
    FROM restaurantpos.gateway_checkouts g JOIN restaurantpos.orders o ON o.id=g.order_id AND o.restaurant_id=g.restaurant_id
    WHERE g.session_id=$1 FOR UPDATE OF g,o`,[sessionId]);
  if(!lookup.rowCount){await cx.query('COMMIT');return {status:'ignored'};}
  const g=lookup.rows[0];
  if(g.status==='paid'){await cx.query('COMMIT');return {status:'paid'};}
  if(g.order_status==='paid'){
   await cx.query("UPDATE restaurantpos.gateway_checkouts SET status='conflict' WHERE session_id=$1",[sessionId]);
   await cx.query('COMMIT');return {status:'conflict'};
  }
  if(g.order_status!=='open'||g.total_minor!==g.amount_minor || session.attributes?.reference_number!==g.order_id){
   await cx.query("UPDATE restaurantpos.gateway_checkouts SET status='conflict' WHERE session_id=$1",[sessionId]);
   await cx.query('COMMIT');return {status:'conflict'};
  }
  const payment=paidPayment(session,g.amount_minor);
  if(!payment){await cx.query('COMMIT');return {status:'pending'};}
  const source=payment.attributes?.source?.type || 'card';
  const method=source==='gcash'?'gcash':source==='paymaya'?'maya':'card';
  await cx.query(`INSERT INTO restaurantpos.payments(restaurant_id,order_id,method,amount_minor,tendered_minor,change_minor,reference,recorded_by)
   SELECT $1,$2,$3,$4,$4,0,$5,o.created_by FROM restaurantpos.orders o WHERE o.id=$2 AND o.restaurant_id=$1
   ON CONFLICT(order_id) DO NOTHING`,[g.restaurant_id,g.order_id,method,g.amount_minor,String(payment.id||sessionId).slice(0,100)]);
  await cx.query(`UPDATE restaurantpos.orders SET status='paid',paid_at=now() WHERE restaurant_id=$1 AND id=$2 AND status='open'`,[g.restaurant_id,g.order_id]);
  await cx.query(`UPDATE restaurantpos.gateway_checkouts SET status='paid',paid_reference=$2,paid_at=now() WHERE session_id=$1`,[sessionId,String(payment.id||sessionId)]);
  await cx.query('COMMIT');return {status:'paid'};
 }catch(e){await cx.query('ROLLBACK');throw e;}finally{cx.release();}
}
const server=createServer(async(req,res)=>{
 res.setHeader('X-Content-Type-Options','nosniff');
 res.setHeader('Referrer-Policy','no-referrer');
 res.setHeader('X-Frame-Options','DENY');
 res.setHeader('Content-Security-Policy',"default-src 'self'; script-src 'self'; style-src 'self'; img-src 'self' data:; connect-src 'self'; object-src 'none'; base-uri 'self'; frame-ancestors 'none'; form-action 'self'");
 try{const url=new URL(req.url,'http://localhost');await routes(req,res,url);}catch(e){
  const status=e.status||(e.code==='23505'?409:e.code==='23503'?409:500);
  if(status>=500)console.error('Internal request error:',e);
  json(res,{error:status>=500?'Internal server error.':e.code==='23505'?'This entry already exists.':e.message},status);
 }
});
server.listen(PORT,'0.0.0.0',()=>console.log(`RestaurantPOS listening on port ${PORT}`));
for(const signal of ['SIGTERM','SIGINT'])process.on(signal,()=>{
 console.log(`RestaurantPOS shutting down on ${signal}`);
 server.close(()=>db.end().then(()=>process.exit(0)).catch(()=>process.exit(1)));
 setTimeout(()=>process.exit(1),12000).unref();
});
