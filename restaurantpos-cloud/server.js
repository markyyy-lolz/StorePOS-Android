import pg from 'pg';
import {createServer} from 'node:http';
import {scryptSync,randomBytes,createHash,timingSafeEqual} from 'node:crypto';
const db=new pg.Pool({connectionString:process.env.DATABASE_URL,ssl:{rejectUnauthorized:true},max:6,connectionTimeoutMillis:8000});
const say=(res,data,code=200)=>{res.writeHead(code,{'Content-Type':'application/json; charset=utf-8','Cache-Control':'no-store','X-Content-Type-Options':'nosniff','X-Frame-Options':'DENY'});res.end(JSON.stringify(data));};
const bad=(m,s=400)=>Object.assign(new Error(m),{status:s});
const uuid=x=>typeof x==='string'&&/^[0-9a-f]{8}-[0-9a-f]{4}-[1-8][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i.test(x);
const hash=x=>createHash('sha256').update(x).digest('hex');
async function requestBody(req){if(!String(req.headers['content-type']||'').startsWith('application/json'))throw bad('Expected JSON',415);let bytes=0,parts=[];for await(const piece of req){bytes+=piece.length;if(bytes>80000)throw bad('Payload too large',413);parts.push(piece);}try{const j=JSON.parse(Buffer.concat(parts).toString('utf8'));if(!j||typeof j!=='object'||Array.isArray(j))throw 0;return j;}catch{throw bad('Invalid JSON');}}
function verify(pw,stored){try{const [alg,salt,key]=String(stored).split('$');return alg==='scrypt'&&/^[0-9a-f]{32}$/.test(salt)&&/^[0-9a-f]{128}$/.test(key)&&timingSafeEqual(scryptSync(pw,salt,64),Buffer.from(key,'hex'));}catch{return false;}}
function cookie(req){const a=String(req.headers.cookie||'').split(';').map(x=>x.trim()).find(x=>x.startsWith('rpos_session='));return a?a.split('=')[1]:'';}
async function auth(req){const token=cookie(req);if(!/^[0-9a-f]{64}$/.test(token))throw bad('Please sign in',401);const q=await db.query("SELECT s.id,s.restaurant_id,s.full_name,s.email,s.role,r.name AS restaurant_name,r.address,r.phone,r.service_charge_percent FROM restaurantpos.sessions t JOIN restaurantpos.staff s ON t.staff_id=s.id JOIN restaurantpos.restaurants r ON r.id=s.restaurant_id WHERE t.token_hash=$1 AND t.expires_at>now() AND s.active=true",[hash(token)]);if(!q.rowCount)throw bad('Session expired',401);return q.rows[0];}
function role(user,allow){if(!allow.includes(user.role))throw bad('Permission denied',403);}
async function detail(user,id){const a=await db.query("SELECT o.*,t.label AS table_label FROM restaurantpos.orders o LEFT JOIN restaurantpos.dining_tables t ON t.id=o.table_id WHERE o.restaurant_id=$1 AND o.id=$2",[user.restaurant_id,id]);if(!a.rowCount)throw bad('Order not found',404);const b=await db.query("SELECT item_name,quantity,unit_price_minor,line_total_minor FROM restaurantpos.order_items WHERE restaurant_id=$1 AND order_id=$2",[user.restaurant_id,id]);const c=await db.query("SELECT method,amount_minor,tendered_minor,change_minor FROM restaurantpos.payments WHERE restaurant_id=$1 AND order_id=$2",[user.restaurant_id,id]);return {order:a.rows[0],items:b.rows,payment:c.rows[0]||null};}
async function handle(req,res,url){
 const path=url.pathname,method=req.method;
 if(path==='/api/health'&&method==='GET'){try{await db.query('SELECT 1');return say(res,{status:'ok',database:'connected',environment:'staging'});}catch{return say(res,{status:'degraded',database:'unavailable'},503);}}
 if(path==='/'&&method==='GET')return say(res,{service:'RestaurantPOS Cloud Staging',status:'testing only',notice:'Not yet production approved; printed acknowledgment is not a BIR invoice.'});
 if(path==='/api/login'&&method==='POST'){
  const b=await requestBody(req),email=String(b.email||'').trim().toLowerCase(),pw=String(b.password||'');
  const found=await db.query('SELECT * FROM restaurantpos.staff WHERE email=$1 AND active=true',[email]);
  if(!found.rowCount||!verify(pw,found.rows[0].password_hash))throw bad('Incorrect credentials',401);
  const token=randomBytes(32).toString('hex');
  await db.query("INSERT INTO restaurantpos.sessions(token_hash,staff_id,expires_at) VALUES($1,$2,now()+interval '12 hours')",[hash(token),found.rows[0].id]);
  res.setHeader('Set-Cookie','rpos_session='+token+'; HttpOnly; Secure; SameSite=Lax; Path=/; Max-Age=43200');
  return say(res,{success:true});
 }
 if(req.headers.origin&&method!=='GET'){try{if(new URL(req.headers.origin).host!==req.headers.host)throw 0;}catch{throw bad('Cross-origin request rejected',403);}}
 const user=await auth(req),tenant=user.restaurant_id;
 if(path==='/api/me'&&method==='GET')return say(res,{user:{name:user.full_name,email:user.email,role:user.role},restaurant:{id:tenant,name:user.restaurant_name,address:user.address,phone:user.phone,service_charge_percent:user.service_charge_percent}});
 if(path==='/api/menu'&&method==='GET'){const a=await db.query('SELECT id,name,price_minor,category_id,active FROM restaurantpos.menu_items WHERE restaurant_id=$1 ORDER BY name',[tenant]);const b=await db.query('SELECT id,name,sort_order FROM restaurantpos.categories WHERE restaurant_id=$1 ORDER BY sort_order,name',[tenant]);return say(res,{items:a.rows,categories:b.rows});}
 if(path==='/api/tables'&&method==='GET'){const a=await db.query('SELECT id,label,seats,active FROM restaurantpos.dining_tables WHERE restaurant_id=$1 ORDER BY label',[tenant]);return say(res,a.rows);}
 if(path==='/api/orders'&&method==='GET'){const filter=url.searchParams.get('status');const expr=filter==='all'?'TRUE':filter==='paid'?"o.status='paid'":"(o.status='open' OR (o.status='paid' AND o.kitchen_status<>'served'))";const q=await db.query('SELECT o.id,o.order_number,o.status,o.service_type,o.kitchen_status,o.total_minor,o.created_at,t.label AS table_label FROM restaurantpos.orders o LEFT JOIN restaurantpos.dining_tables t ON t.id=o.table_id WHERE o.restaurant_id=$1 AND '+expr+' ORDER BY o.created_at DESC LIMIT 150',[tenant]);return say(res,q.rows);}
 if(path==='/api/orders'&&method==='POST'){
  role(user,['owner','manager','cashier']);const b=await requestBody(req);
  if(!uuid(b.client_request_id)||!['dine_in','takeout'].includes(b.service_type)||!Array.isArray(b.items)||b.items.length<1||b.items.length>60)throw bad('Invalid order');
  const table=b.service_type==='dine_in'?(b.table_id||null):null;if(table&&!uuid(table))throw bad('Invalid table');
  const cx=await db.connect();
  try{await cx.query('BEGIN');
   const seen=await cx.query('SELECT id FROM restaurantpos.orders WHERE restaurant_id=$1 AND client_request_id=$2',[tenant,b.client_request_id]);
   if(seen.rowCount){await cx.query('COMMIT');return say(res,await detail(user,seen.rows[0].id));}
   if(table){const t=await cx.query('SELECT id FROM restaurantpos.dining_tables WHERE restaurant_id=$1 AND id=$2 AND active=true',[tenant,table]);if(!t.rowCount)throw bad('Table unavailable',409);}
   const ids=[...new Set(b.items.map(x=>x.menu_item_id))];if(!ids.every(uuid))throw bad('Bad menu IDs');
   const p=await cx.query('SELECT id,name,price_minor,active FROM restaurantpos.menu_items WHERE restaurant_id=$1 AND id=ANY($2::uuid[])',[tenant,ids]);
   const products=new Map(p.rows.map(x=>[x.id,x]));let subtotal=0;const lines=[];
   for(const x of b.items){const product=products.get(x.menu_item_id);if(!product?.active||!Number.isInteger(x.quantity)||x.quantity<1||x.quantity>99)throw bad('Invalid product or quantity',409);const cost=product.price_minor*x.quantity;subtotal+=cost;lines.push({id:product.id,name:product.name,price:product.price_minor,qty:x.quantity,cost});}
   const settings=await cx.query('SELECT service_charge_percent FROM restaurantpos.restaurants WHERE id=$1',[tenant]);const charge=Math.round(subtotal*Number(settings.rows[0].service_charge_percent)/100),total=subtotal+charge;
   if(total>100000000)throw bad('Order too large');
   const o=await cx.query('INSERT INTO restaurantpos.orders(restaurant_id,client_request_id,table_id,service_type,notes,subtotal_minor,discount_minor,service_charge_minor,total_minor,created_by) VALUES($1,$2,$3,$4,$5,$6,0,$7,$8,$9) ON CONFLICT (restaurant_id,client_request_id) DO NOTHING RETURNING id',[tenant,b.client_request_id,table,b.service_type,String(b.notes||'').slice(0,300),subtotal,charge,total,user.id]);
   if(!o.rowCount){const again=await cx.query('SELECT id FROM restaurantpos.orders WHERE restaurant_id=$1 AND client_request_id=$2',[tenant,b.client_request_id]);await cx.query('COMMIT');return say(res,await detail(user,again.rows[0].id));}
   for(const x of lines)await cx.query("INSERT INTO restaurantpos.order_items(restaurant_id,order_id,menu_item_id,item_name,unit_price_minor,quantity,notes,line_total_minor) VALUES($1,$2,$3,$4,$5,$6,'',$7)",[tenant,o.rows[0].id,x.id,x.name,x.price,x.qty,x.cost]);
   await cx.query('COMMIT');return say(res,await detail(user,o.rows[0].id),201);
  }catch(e){await cx.query('ROLLBACK');throw e;}finally{cx.release();}
 }
 const found=path.match(/^\/api\/orders\/([a-f0-9-]+)(?:\/(payment|kitchen|receipt|paymongo-checkout|paymongo-status))?$/i);
 if(found){const id=found[1],action=found[2]||'';if(!uuid(id))throw bad('Invalid order ID');
  if(!action&&method==='GET')return say(res,await detail(user,id));
  if(action==='receipt'&&method==='GET'){const d=await detail(user,id);return say(res,{...d,text:user.restaurant_name+'\n'+(d.payment?'PAYMENT ACKNOWLEDGMENT':'ORDER SLIP - UNPAID')+'\n'+d.items.map(x=>x.quantity+' x '+x.item_name).join('\n')+'\nTOTAL PHP '+(d.order.total_minor/100).toFixed(2)+'\nNON-BIR ACKNOWLEDGMENT\nNOT AN OFFICIAL RECEIPT OR SALES INVOICE'});}
  if(action.startsWith('paymongo-'))throw bad('PayMongo disabled for initial staging; no digital payment capture',503);
  if(action==='kitchen'&&method==='PATCH'){role(user,['owner','manager','kitchen']);const b=await requestBody(req);if(!['queued','preparing','ready','served'].includes(b.status))throw bad('Invalid kitchen status');const q=await db.query('UPDATE restaurantpos.orders SET kitchen_status=$3 WHERE restaurant_id=$1 AND id=$2 RETURNING id',[tenant,id,b.status]);if(!q.rowCount)throw bad('Order not found',404);return say(res,{success:true});}
  if(action==='payment'&&method==='POST'){
   role(user,['owner','manager','cashier']);const b=await requestBody(req);if(b.method!=='cash')throw bad('Only cash in staging; digital payments disabled',409);
   const cx=await db.connect();try{await cx.query('BEGIN');const r=await cx.query('SELECT status,total_minor FROM restaurantpos.orders WHERE restaurant_id=$1 AND id=$2 FOR UPDATE',[tenant,id]);if(!r.rowCount)throw bad('Order not found',404);const o=r.rows[0];if(o.status==='paid'){await cx.query('COMMIT');return say(res,await detail(user,id));}if(o.status!=='open')throw bad('Not payable',409);
    const g=await cx.query('SELECT status FROM restaurantpos.gateway_checkouts WHERE restaurant_id=$1 AND order_id=$2 FOR UPDATE',[tenant,id]);if(g.rowCount&&g.rows[0].status==='pending')throw bad('Pending payment gateway, manual reconciliation required',409);
    if(!Number.isInteger(b.tendered_minor)||b.tendered_minor<o.total_minor||b.tendered_minor>100000000)throw bad('Invalid tendered amount');
    await cx.query("INSERT INTO restaurantpos.payments(restaurant_id,order_id,method,amount_minor,tendered_minor,change_minor,recorded_by) VALUES($1,$2,'cash',$3,$4,$5,$6)",[tenant,id,o.total_minor,b.tendered_minor,b.tendered_minor-o.total_minor,user.id]);await cx.query("UPDATE restaurantpos.orders SET status='paid',paid_at=now() WHERE restaurant_id=$1 AND id=$2",[tenant,id]);await cx.query('COMMIT');return say(res,await detail(user,id));
   }catch(e){await cx.query('ROLLBACK');throw e;}finally{cx.release();}
  }
 }
 throw bad('Endpoint not found',404);
}
const server=createServer(async(req,res)=>{try{const url=new URL(req.url,'http://localhost');await handle(req,res,url);}catch(e){const status=e.status||(e.code==='23505'?409:500);if(status>=500)console.error(e);say(res,{error:status>=500?'Internal server error':e.message},status);}});
server.listen(Number(process.env.PORT||3000),'0.0.0.0',()=>console.log('RestaurantPOS staging listening'));
