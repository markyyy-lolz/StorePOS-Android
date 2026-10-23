const $=s=>document.querySelector(s);
const fmt=n=>'₱'+(Number(n||0)/100).toLocaleString('en-PH',{minimumFractionDigits:2,maximumFractionDigits:2});
const e=v=>String(v??'').replace(/[&<>"']/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
const php=minor=>String((Number(minor||0)/100).toFixed(2));
const isoDay=()=>new Intl.DateTimeFormat('en-CA',{timeZone:'Asia/Manila',year:'numeric',month:'2-digit',day:'2-digit'}).format(new Date());
const when=s=>new Intl.DateTimeFormat('en-PH',{timeZone:'Asia/Manila',dateStyle:'medium',timeStyle:'short'}).format(new Date(s));
const nameOrder=o=>'R-'+String(o.order_number).padStart(6,'0');
const state={me:null,page:'dashboard',categories:[],menu:[],tables:[],orders:[],cart:[],category:'all',search:'',service:'dine_in',tableId:'',orderNote:'',discount:'0',clientRequestId:'',loaded:false};
let timer=null,toastTimer=null;
function notify(msg,bad=false){const t=$('#toast');t.textContent=msg;t.className='toast show'+(bad?' bad':'');clearTimeout(toastTimer);toastTimer=setTimeout(()=>t.className='toast',3500);}
async function api(path,opt={}){
 const headers={...(opt.body?{'Content-Type':'application/json'}:{}),...(opt.headers||{})};
 let res;
 try{res=await fetch(path,{credentials:'same-origin',...opt,headers});}
 catch{throw new Error('Unable to reach the cloud server. Check your internet connection.');}
 let data;try{data=await res.json();}catch{throw new Error('Server response unavailable.');}
 if(!res.ok){if(res.status===401 && state.me){state.me=null;showLogin();}throw new Error(data.error||'Request failed.');}
 return data;
}
function showLogin(){state.me=null;$('#app').hidden=true;$('#login-screen').hidden=false;clearInterval(timer);}
function showApp(){const user=state.me.user;$('#login-screen').hidden=true;$('#app').hidden=false;
 $('#sidebar-store').textContent=state.me.restaurant.name;
 $('#staff-name').textContent=user.name;$('#avatar').textContent=user.name.slice(0,1).toUpperCase();$('#role-pill').textContent=user.role.toUpperCase();
 $('#today-date').textContent=new Intl.DateTimeFormat('en-PH',{timeZone:'Asia/Manila',month:'long',day:'numeric',year:'numeric'}).format(new Date());
 const privileged=['owner','manager'].includes(user.role);
 for(const page of ['menu','reports','settings'])document.querySelector(`[data-page="${page}"]`).hidden=!privileged;
 if(user.role==='kitchen')for(const page of ['pos','tables'])document.querySelector(`[data-page="${page}"]`).hidden=true;
}
function pageHeader(title,subtitle,buttons=''){return `<div class="page-title"><div><h1>${e(title)}</h1><p>${e(subtitle)}</p></div><div class="action-row">${buttons}</div></div>`;}
function labelStatus(s){const map={open:'orange',paid:'green',queued:'orange',preparing:'blue',ready:'green',served:'green',cancelled:'red'};return `<span class="tag ${map[s]||''}">${e(s)}</span>`;}
function getCartTotals(){let subtotal=state.cart.reduce((sum,l)=>sum+Number(l.price_minor)*l.qty,0), discount=Math.max(0,Math.min(subtotal,Math.round(Number(state.discount||0)*100)||0));
 let sc=Math.round((subtotal-discount)*Number(state.me.restaurant.service_charge_percent)/100);return {subtotal,discount,sc,total:subtotal-discount+sc};}
async function navigate(p){clearInterval(timer);timer=null;state.page=p;$('#breadcrumb').textContent={dashboard:'Overview',pos:'Point of Sale',orders:'Orders',kitchen:'Kitchen Display',tables:'Dining Tables',menu:'Menu Manager',reports:'Reports',settings:'Settings'}[p]||p;
 document.querySelectorAll('.nav-item').forEach(b=>b.classList.toggle('active',b.dataset.page===p));$('.sidebar').classList.remove('open');
 $('#content').innerHTML='<div class="loading">Loading…</div>';
 try{if(p==='dashboard')await dashboard();if(p==='pos')await pos();if(p==='orders')await orders();if(p==='kitchen')await kitchen();if(p==='tables')await tables();if(p==='menu')await manageMenu();if(p==='reports')await reports();if(p==='settings')settings();}
 catch(err){$('#content').innerHTML=`<div class="panel"><h2>Unable to load</h2><p class="muted">${e(err.message)}</p><button class="secondary" data-action="retry">Try again</button></div>`;notify(err.message,true);}
}
async function preload(){const [menu,tables]=await Promise.all([api('/api/menu'),api('/api/tables')]);state.categories=menu.categories;state.menu=menu.items;state.tables=tables;}
async function dashboard(){const [metrics,list]=await Promise.all([api('/api/dashboard'),api('/api/orders?status=active')]);
 $('#content').innerHTML=pageHeader('Command Center',`Welcome back, ${state.me.user.name}. Here's what's happening in your restaurant.`,`<button class="primary" data-page="pos">+ New Order</button>`)+`
 <section class="metrics">
 ${[["Today's Sales",fmt(metrics.sales_today),'₱'],['Paid Transactions',metrics.transactions_today,'▤'],['Open Orders',metrics.open_orders,'▣'],['Kitchen Queue',metrics.kitchen_pending,'♨']].map(([t,v,icon])=>`<div class="metric"><div class="metric-icon">${e(icon)}</div><small>${e(t)}</small><strong>${e(v)}</strong></div>`).join('')}
 </section>
 <section class="layout-2"><div class="panel"><div class="panel-head"><div><h2>Quick Actions</h2><p>Access your most-used restaurant tools</p></div></div>
 <div class="quick-grid">${[['pos','▣','New Order','Create a dine-in or takeout sale'],['kitchen','♨','Kitchen Queue','Track food preparation'],['tables','▤','Dining Tables','Check table availability'],['orders','≡','Active Orders','Process and collect bills'],['menu','◈','Menu Manager','Organize meals and prices'],['reports','▥','Daily Reports','Review sales performance']].filter(x=>!(['menu','reports'].includes(x[0])&&!['owner','manager'].includes(state.me.user.role))).map(([p,ic,title,subtitle])=>`<button class="quick-card" data-page="${p}"><span class="quick-symbol">${ic}</span><b>${e(title)}</b><span class="hint">${e(subtitle)}</span></button>`).join('')}</div>
 </div><div class="panel"><div class="panel-head"><div><h2>Recent Active Orders</h2><p>Live order activity</p></div><button class="ghost" data-page="orders">View all</button></div>
 ${list.length?list.slice(0,6).map(o=>`<div class="menu-row" style="margin-bottom:9px"><div><b>${e(nameOrder(o))} • ${e(o.table_label||'Takeout')}</b><p>${e(when(o.created_at))}</p></div><div>${labelStatus(o.kitchen_status)}<div class="menu-price" style="margin-top:7px">${fmt(o.total_minor)}</div></div></div>`).join(''):'<div class="empty"><b>All caught up</b>No active restaurant orders right now.</div>'}
 </div></section>`;
}
async function pos(){await preload();renderPos();}
function renderPos(){const canDiscount=['owner','manager'].includes(state.me.user.role);
 const categories=state.categories,items=state.menu.filter(i=>i.active).filter(i=>state.category==='all'||i.category_id===state.category).filter(i=>(i.name+' '+i.description).toLowerCase().includes(state.search.toLowerCase()));
 const total=getCartTotals();
 $('#content').innerHTML=pageHeader('Point of Sale','Fast checkout for dine-in and takeout • StorePOS-inspired workspace',`<button class="secondary" data-page="orders">View Orders</button>`)+`
 <div class="pos-layout"><section class="panel pos-panel"><div class="panel-head"><div><h2>Menu Selection</h2><p>Tap an item to add it to the current order</p></div><span class="tag blue">${state.menu.filter(i=>i.active).length} Items</span></div>
 <div class="pos-search"><input id="pos-search" value="${e(state.search)}" placeholder="Search menu items…" aria-label="Search menu items"></div>
 <div class="category-strip"><button class="category-tab ${state.category==='all'?'active':''}" data-category="all">All Items</button>${categories.map(c=>`<button class="category-tab ${state.category===c.id?'active':''}" data-category="${e(c.id)}">${e(c.name)}</button>`).join('')}</div>
 ${items.length?`<div class="product-grid">${items.map(i=>`<button class="product" data-add="${e(i.id)}"><span class="product-art">${i.category_id?'🍽':'🥘'}</span><span class="product-info"><b>${e(i.name)}</b><small>${e(i.description||'Ready to order')}</small><strong>${fmt(i.price_minor)}</strong></span></button>`).join('')}</div>`:`<div class="empty"><b>No items found</b>${state.menu.length?'Try another category or search.':'Add menu items in Menu Manager first.'}</div>`}</section>
 <aside class="panel pos-panel cart-card"><div class="cart-top"><div><div class="cart-title">Current Order</div><div class="cart-count">${state.cart.reduce((a,b)=>a+b.qty,0)} item(s)</div></div><button class="ghost" data-action="clear-cart">Clear</button></div>
 <div class="cart-config"><label>Service Type<select id="service-type"><option value="dine_in" ${state.service==='dine_in'?'selected':''}>Dine-In</option><option value="takeout" ${state.service==='takeout'?'selected':''}>Takeout</option></select></label>
 ${state.service==='dine_in'?`<label>Dining Table<select id="selected-table"><option value="">Walk-in / no table assigned</option>${state.tables.filter(t=>t.active).map(t=>`<option value="${e(t.id)}" ${state.tableId===t.id?'selected':''} ${t.occupied?'disabled':''}>${e(t.label)} ${t.occupied?'(Occupied)':''}</option>`).join('')}</select></label>`:''}</div>
 <div class="cart-items">${state.cart.length?state.cart.map(l=>`<div class="cart-line"><div><b>${e(l.name)}</b><small>${fmt(l.price_minor)} each</small><small>${fmt(l.price_minor*l.qty)}</small></div><div class="qty-tools"><button data-qty="${e(l.id)}" data-delta="-1" aria-label="Remove one">−</button><span>${l.qty}</span><button data-qty="${e(l.id)}" data-delta="1" aria-label="Add one">+</button></div></div>`).join(''):'<div class="empty">Your order is empty.<br>Choose menu items to begin.</div>'}</div>
 ${canDiscount?`<label style="margin-bottom:14px">Discount (₱)<input type="number" id="discount" min="0" step="0.01" value="${e(state.discount)}"></label>`:''}
 <div class="price-summary"><div><span>Subtotal</span><b>${fmt(total.subtotal)}</b></div><div><span>Discount</span><b>− ${fmt(total.discount)}</b></div><div><span>Service charge</span><b>${fmt(total.sc)}</b></div><div class="grand"><span>Grand Total</span><b>${fmt(total.total)}</b></div></div>
 <div class="checkout-actions"><button class="primary full" data-action="place-order" ${state.cart.length?'':'disabled'}>Place Order →</button><div class="info-note">Orders are saved securely to Neon Cloud. Printing creates an acknowledgment slip, not a BIR sales invoice.</div></div>
 </aside></div>`;
}
function saveDraft(){try{sessionStorage.setItem('rpos-draft',JSON.stringify({cart:state.cart,service:state.service,tableId:state.tableId,discount:state.discount,clientRequestId:state.clientRequestId}));}catch{}}
function addItem(id){const item=state.menu.find(x=>x.id===id&&x.active);if(!item)return;state.clientRequestId='';const found=state.cart.find(x=>x.id===id);if(found)found.qty=Math.min(99,found.qty+1);else state.cart.push({id:item.id,name:item.name,price_minor:item.price_minor,qty:1});saveDraft();renderPos();}
async function placeOrder(){if(!state.cart.length)return;const totals=getCartTotals();state.clientRequestId ||= crypto.randomUUID();saveDraft();const payload={client_request_id:state.clientRequestId,service_type:state.service,table_id:state.service==='dine_in'?(state.tableId||null):null,discount_minor:totals.discount,items:state.cart.map(x=>({menu_item_id:x.id,quantity:x.qty})),notes:''};
 const btn=document.querySelector('[data-action="place-order"]');if(btn)btn.disabled=true;
 try{const doc=await api('/api/orders',{method:'POST',body:JSON.stringify(payload)});state.cart=[];state.discount='0';state.tableId='';state.clientRequestId='';saveDraft();notify('Order saved to Neon Cloud!');await orderModal(doc.order.id);}
 catch(err){notify(err.message,true);if(btn)btn.disabled=false;}
}
async function orders(){const rows=await api('/api/orders?status=all');state.orders=rows;
 $('#content').innerHTML=pageHeader('Orders','View, prepare, collect payments, and print acknowledgment slips',`<button class="primary" data-page="pos">+ New Order</button>`)+`<div class="panel"><div class="panel-head"><div><h2>Order History</h2><p>Latest 150 orders in the cloud</p></div><span class="tag blue">${rows.length} Records</span></div>
 ${rows.length?`<div class="table-wrap"><table class="data-table"><thead><tr><th>Order</th><th>Date</th><th>Service</th><th>Kitchen</th><th>Payment</th><th>Total</th><th></th></tr></thead><tbody>${rows.map(o=>`<tr><td><strong>${e(nameOrder(o))}</strong></td><td>${e(when(o.created_at))}</td><td>${e(o.table_label||'Takeout / walk-in')}</td><td>${labelStatus(o.kitchen_status)}</td><td>${labelStatus(o.status)}</td><td><strong>${fmt(o.total_minor)}</strong></td><td><button class="small-btn" data-order="${e(o.id)}">Open →</button></td></tr>`).join('')}</tbody></table></div>`:'<div class="empty"><b>No orders yet</b>New restaurant orders will appear here.</div>'}</div>`;
}
async function kitchen(){const rows=await api('/api/orders?status=active');const k=rows.filter(o=>o.kitchen_status!=='served'&&o.status!=='cancelled');
 $('#content').innerHTML=pageHeader('Kitchen Display','Live restaurant preparation queue • refreshes automatically',`<button class="secondary" data-action="refresh-kitchen">↻ Refresh</button>`)+`
 <div class="kitchen-grid">${k.length?k.map(o=>`<div class="kitchen-ticket"><div class="panel-head"><div><div class="ticket-number">${e(nameOrder(o))}</div><div class="ticket-meta">${e(o.table_label||'Takeout')} • ${e(when(o.created_at))}</div></div>${labelStatus(o.kitchen_status)}</div>
 <div class="ticket-item"><b>${o.item_lines} menu line(s)</b></div><div class="action-row" style="margin-top:15px"><button class="secondary" data-order="${e(o.id)}">View items</button>
 <button class="primary" data-kitchen="${e(o.id)}" data-next="${o.kitchen_status==='queued'?'preparing':o.kitchen_status==='preparing'?'ready':'served'}">${o.kitchen_status==='queued'?'Start Prep':o.kitchen_status==='preparing'?'Mark Ready':'Mark Served'}</button></div></div>`).join(''):'<div class="empty"><b>Kitchen is clear</b>New orders appear automatically.</div>'}</div>`;
 timer=setInterval(()=>{if(state.page==='kitchen'&&!$('#modal').open)kitchen().catch(err=>notify(err.message,true));},8000);
}
async function tables(){const rows=await api('/api/tables');state.tables=rows;
 $('#content').innerHTML=pageHeader('Dining Tables','See occupied and available tables at a glance',`<button class="primary" data-action="new-table">+ Add Table</button>`)+`
 <div class="table-grid">${rows.length?rows.map(t=>`<div class="table-card"><div class="panel-head"><h3>▤ ${e(t.label)}</h3><span class="tag ${t.occupied?'orange':t.active?'green':'red'}">${t.occupied?'Occupied':t.active?'Available':'Inactive'}</span></div><p>${e(t.seats)} seat(s)</p><button class="small-btn" data-page="pos">New Order →</button></div>`).join(''):'<div class="empty"><b>No tables configured</b>Add dining tables or create takeout orders.</div>'}</div>`;
}
async function manageMenu(){await preload();const privileged=['owner','manager'].includes(state.me.user.role);
 if(!privileged){$('#content').innerHTML='<div class="panel">Only managers can edit the menu.</div>';return;}
 $('#content').innerHTML=pageHeader('Menu Manager','Manage meals, drinks, categories, and pricing',`<button class="secondary" data-action="new-category">+ Category</button><button class="primary" data-action="new-product">+ Add Menu Item</button>`)+`
 <div class="panel"><div class="panel-head"><div><h2>Menu Catalog</h2><p>${state.menu.length} products • ${state.categories.length} categories</p></div></div><div class="menu-list">
 ${state.menu.length?state.menu.map(m=>`<div class="menu-row"><div><b>${e(m.name)}</b><p>${e(state.categories.find(c=>c.id===m.category_id)?.name||'Uncategorized')} • ${e(m.description||'No description')} ${m.active?'':' • Inactive'}</p></div><div class="action-row"><span class="menu-price">${fmt(m.price_minor)}</span><button class="small-btn" data-edit-product="${e(m.id)}">Edit</button></div></div>`).join(''):'<div class="empty"><b>Menu is empty</b>Add a category and your first menu item.</div>'}</div></div>`;
}
async function reports(day=isoDay()){const r=await api('/api/reports?date='+encodeURIComponent(day));
 $('#content').innerHTML=pageHeader('Sales Reports','Cashier summaries and payment method breakdown')+`
 <div class="panel" style="margin-bottom:18px"><label style="max-width:230px">Sales date<input id="report-date" type="date" value="${e(r.date)}"></label></div>
 <section class="metrics">${[['Paid Sales',fmt(r.gross_sales_minor),'₱'],['Transactions',r.transactions,'▥'],['Discounts',fmt(r.discounts_minor),'−'],['Service Charges',fmt(r.service_charge_minor),'+']].map(([t,v,icon])=>`<div class="metric"><div class="metric-icon">${icon}</div><small>${e(t)}</small><strong>${e(v)}</strong></div>`).join('')}</section>
 <div class="panel page-panel"><h2>Payment Breakdown</h2>${r.payments.length?`<div class="table-wrap"><table class="data-table"><thead><tr><th>Method</th><th>Transactions</th><th>Amount Recorded</th></tr></thead><tbody>${r.payments.map(p=>`<tr><td><strong>${e(p.method.toUpperCase())}</strong></td><td>${e(p.count)}</td><td>${fmt(p.total_minor)}</td></tr>`).join('')}</tbody></table></div>`:'<div class="empty">No paid transactions for this date.</div>'}
 <div class="info-note">GCash, Maya, and card entries are manually recorded payments. This version does not charge cards or verify payment gateway settlements.</div></div>`;
}
function settings(){const r=state.me.restaurant;
 $('#content').innerHTML=pageHeader('Restaurant Settings','Business information and checkout settings')+`
 <div class="panel page-panel"><div class="panel-head"><div><h2>Business Profile</h2><p>Shown on non-BIR acknowledgment slips</p></div></div>
 <form id="settings-form" class="form-grid"><label>Name<input name="name" maxlength="140" required value="${e(r.name)}"></label><label>Phone<input name="phone" maxlength="40" value="${e(r.phone)}"></label><label class="wide">Address<input name="address" maxlength="250" value="${e(r.address)}"></label><label>Service Charge (%)<input name="service_charge_percent" min="0" max="20" step="0.01" type="number" value="${e(r.service_charge_percent)}"></label><div class="form-actions"><button class="primary" type="submit">Save Changes</button></div></form>
 <div class="info-note" style="margin-top:20px"><b>Receipt compliance:</b> All printed slips are clearly marked as non-BIR acknowledgments. They are not replacements for BIR-registered sales invoices, and POS/CRM registration obligations must be checked separately.</div></div>`;
}
function openModal(html){$('#modal-body').innerHTML=html;$('#modal').showModal();}
function closeModal(){if($('#modal').open)$('#modal').close();}
function categoryModal(){openModal(`<h2 class="modal-title">New Category</h2><p class="modal-body-text">Create a menu group such as Meals or Beverages.</p><form id="category-form" class="form-grid"><label class="wide">Category Name<input name="name" maxlength="80" required placeholder="e.g., Rice Meals"></label><div class="form-actions"><button class="primary">Save Category</button></div></form>`);}
function productModal(id){const p=id?state.menu.find(x=>x.id===id):null;
 openModal(`<h2 class="modal-title">${p?'Edit Menu Item':'Add Menu Item'}</h2><p class="modal-body-text">Pricing is saved directly to Neon Cloud.</p><form id="product-form" data-id="${e(id||'')}" class="form-grid"><label class="wide">Name<input name="name" maxlength="140" required value="${e(p?.name||'')}" placeholder="e.g., Chicken Inasal"></label><label>Price (₱)<input name="price" type="number" step="0.01" min="0" required value="${p?php(p.price_minor):''}"></label>
 ${p?'':`<label>Category<select name="category_id"><option value="">Uncategorized</option>${state.categories.map(c=>`<option value="${e(c.id)}">${e(c.name)}</option>`).join('')}</select></label>`}
 <label class="wide">Description<input name="description" maxlength="240" value="${e(p?.description||'')}" placeholder="Short menu description"></label>
 ${p?`<label class="wide"><span>Active <input type="checkbox" name="active" ${p.active?'checked':''} style="width:auto"></span></label>`:''}<div class="form-actions"><button class="primary">${p?'Save Changes':'Add Item'}</button></div></form>`);
}
function tableModal(){openModal(`<h2 class="modal-title">Add Dining Table</h2><p class="modal-body-text">Set up a table for dine-in service.</p><form id="table-form" class="form-grid"><label>Table Name<input name="label" maxlength="40" placeholder="T-01" required></label><label>Seats<input name="seats" type="number" min="1" max="40" value="4"></label><div class="form-actions"><button class="primary">Save Table</button></div></form>`);}
async function orderModal(id){const d=await api('/api/orders/'+id);const o=d.order;
 openModal(`<h2 class="modal-title">Order ${e(nameOrder(o))}</h2><p class="modal-body-text">${e(when(o.created_at))} • ${e(o.table_label||'Takeout / walk-in')} • ${e(o.cashier_name)}</p>
 <div style="display:flex;gap:8px;margin-bottom:13px">${labelStatus(o.status)} ${labelStatus(o.kitchen_status)}</div>
 <div class="table-wrap"><table class="data-table"><thead><tr><th>Menu Item</th><th>Quantity</th><th>Amount</th></tr></thead><tbody>${d.items.map(i=>`<tr><td><strong>${e(i.item_name)}</strong>${i.notes?`<br><small>${e(i.notes)}</small>`:''}</td><td>${e(i.quantity)}</td><td>${fmt(i.line_total_minor)}</td></tr>`).join('')}</tbody></table></div>
 <div class="price-summary" style="margin-top:20px"><div><span>Subtotal</span><b>${fmt(o.subtotal_minor)}</b></div><div><span>Discount</span><b>${fmt(o.discount_minor)}</b></div><div><span>Service Charge</span><b>${fmt(o.service_charge_minor)}</b></div><div class="grand"><span>Total</span><b>${fmt(o.total_minor)}</b></div></div>
 ${o.status==='open'&&state.me.user.role!=='kitchen'?`<div style="margin:18px 0"><h3 style="font-size:14px">Collect Payment</h3><form id="payment-form" data-id="${e(id)}" class="form-grid"><label>Method<select id="pay-method" name="method"><option value="cash">Cash</option><option value="gcash">GCash (manual)</option><option value="maya">Maya (manual)</option><option value="card">Card (manual)</option></select></label><label id="tender-label">Cash Tendered (₱)<input id="tendered" name="tendered" type="number" step="0.01" min="${php(o.total_minor)}" value="${php(o.total_minor)}" required></label><label class="wide">Payment Reference (optional)<input name="reference" maxlength="100" placeholder="Reference / confirmation"></label><div class="form-actions"><button class="primary">Confirm Payment</button></div></form><div class="info-note">Non-cash methods are recorded only; no payment gateway verification is performed.</div></div>`:''}
 <div class="modal-actions"><button class="secondary" id="close-order">Close</button><button class="primary" data-receipt="${e(id)}">${o.status==='paid'?'View Acknowledgment':'Print Order Slip'}</button></div>`);
}
async function receiptModal(id){const d=await api('/api/orders/'+id+'/receipt');
 openModal(`<h2 class="modal-title">${d.order.status==='paid'?'Payment Acknowledgment':'Order Slip'}</h2><p class="modal-body-text">80mm thermal-style printable layout • Not a tax invoice</p><pre id="receipt-preview"></pre><div class="modal-actions"><button class="secondary" id="receipt-close">Close</button><button class="primary" id="receipt-print-btn">Print / Save PDF</button></div>`);
 $('#receipt-preview').textContent=d.text;$('#receipt-print').textContent=d.text;
}
function currentVals(form){return Object.fromEntries(new FormData(form).entries());}
$('#login-form').addEventListener('submit',async ev=>{
 ev.preventDefault();const btn=$('#login-submit');btn.disabled=true;$('#login-error').textContent='';
 try{await api('/api/login',{method:'POST',body:JSON.stringify({email:$('#login-email').value,password:$('#login-password').value})});state.me=await api('/api/me');$('#login-password').value='';showApp();await navigate('dashboard');}
 catch(err){$('#login-error').textContent=err.message;}finally{btn.disabled=false;}
});
$('#logout').addEventListener('click',async()=>{try{await api('/api/logout',{method:'POST',body:'{}'});}catch{}showLogin();});
$('#menu-toggle').addEventListener('click',()=>$('.sidebar').classList.toggle('open'));
$('#modal-close').addEventListener('click',closeModal);
$('#modal').addEventListener('click',ev=>{if(ev.target===$('#modal'))closeModal();});
document.addEventListener('click',async ev=>{
 const btn=ev.target.closest('button');if(!btn)return;
 const page=btn.dataset.page;if(page){navigate(page);return;}
 const a=btn.dataset.action;
 try{
  if(btn.dataset.add){addItem(btn.dataset.add);return;}
  if(btn.dataset.category){state.category=btn.dataset.category;renderPos();return;}
  if(btn.dataset.qty){const line=state.cart.find(x=>x.id===btn.dataset.qty);if(line){state.clientRequestId='';line.qty+=Number(btn.dataset.delta);if(line.qty<1)state.cart=state.cart.filter(x=>x!==line);if(line.qty>99)line.qty=99;saveDraft();renderPos();}return;}
  if(btn.dataset.order){await orderModal(btn.dataset.order);return;}
  if(btn.dataset.receipt){await receiptModal(btn.dataset.receipt);return;}
  if(btn.dataset.editProduct){productModal(btn.dataset.editProduct);return;}
  if(btn.dataset.kitchen){btn.disabled=true;await api('/api/orders/'+btn.dataset.kitchen+'/kitchen',{method:'PATCH',body:JSON.stringify({status:btn.dataset.next})});notify('Kitchen order updated.');await kitchen();return;}
  if(a==='clear-cart'){state.cart=[];state.discount='0';state.clientRequestId='';saveDraft();renderPos();return;}
  if(a==='place-order'){await placeOrder();return;}
  if(a==='retry'){await navigate(state.page);return;}
  if(a==='refresh-kitchen'){await kitchen();return;}
  if(a==='new-category'){categoryModal();return;}
  if(a==='new-product'){productModal();return;}
  if(a==='new-table'){tableModal();return;}
  if(btn.id==='close-order'||btn.id==='receipt-close'){closeModal();return;}
  if(btn.id==='receipt-print-btn'){window.print();return;}
 }catch(err){notify(err.message,true);btn.disabled=false;}
});
document.addEventListener('change',ev=>{
 const t=ev.target;
 if(t.id==='service-type'){state.clientRequestId='';state.service=t.value;if(t.value==='takeout')state.tableId='';saveDraft();renderPos();}
 if(t.id==='selected-table'){state.clientRequestId='';state.tableId=t.value;saveDraft();}
 if(t.id==='discount'){state.clientRequestId='';state.discount=t.value;saveDraft();renderPos();}
 if(t.id==='pay-method'){const cash=t.value==='cash',label=$('#tender-label');if(label)label.hidden=!cash;const inp=$('#tendered');if(inp)inp.required=cash;}
 if(t.id==='report-date')reports(t.value).catch(err=>notify(err.message,true));
});
document.addEventListener('input',ev=>{
 const t=ev.target;if(t.id==='pos-search'){state.search=t.value;const caret=t.selectionStart;renderPos();const i=$('#pos-search');i?.focus();i?.setSelectionRange(caret,caret);}
});
document.addEventListener('submit',async ev=>{
 const form=ev.target;if(!['category-form','product-form','table-form','settings-form','payment-form'].includes(form.id))return;
 ev.preventDefault();const values=currentVals(form);const btn=form.querySelector('button[type=submit],button.primary');if(btn)btn.disabled=true;
 try{
  if(form.id==='category-form'){await api('/api/categories',{method:'POST',body:JSON.stringify(values)});closeModal();await manageMenu();}
  if(form.id==='product-form'){const id=form.dataset.id;
   const payload={name:values.name,price:values.price,description:values.description,category_id:values.category_id||null,active:!!form.querySelector('input[name=active]:checked')};
   await api(id?'/api/menu/'+id:'/api/menu',{method:id?'PATCH':'POST',body:JSON.stringify(payload)});closeModal();await manageMenu();}
  if(form.id==='table-form'){await api('/api/tables',{method:'POST',body:JSON.stringify(values)});closeModal();await tables();}
  if(form.id==='settings-form'){const restaurant=await api('/api/settings',{method:'PATCH',body:JSON.stringify(values)});state.me.restaurant={...state.me.restaurant,...restaurant};$('#sidebar-store').textContent=restaurant.name;settings();}
  if(form.id==='payment-form'){
   const tendered_minor=Math.round(Number(values.tendered)*100);
   const d=await api('/api/orders/'+form.dataset.id+'/payment',{method:'POST',body:JSON.stringify({method:values.method,tendered_minor,reference:values.reference||''})});
   await receiptModal(d.order.id);
  }
  notify('Saved successfully to Neon Cloud.');
 }catch(err){notify(err.message,true);if(btn)btn.disabled=false;}
});
(async()=>{
 try{state.me=await api('/api/me');showApp();try{const draft=JSON.parse(sessionStorage.getItem('rpos-draft')||'{}');
  if(Array.isArray(draft.cart))state.cart=draft.cart.filter(x=>x&&typeof x.id==='string'&&Number.isInteger(x.qty)&&x.qty>0).slice(0,60);
  if(['takeout','dine_in'].includes(draft.service))state.service=draft.service;
  if(typeof draft.tableId==='string')state.tableId=draft.tableId;
  if(typeof draft.discount==='string')state.discount=draft.discount;
  if(typeof draft.clientRequestId==='string' && /^[a-f0-9-]{36}$/i.test(draft.clientRequestId))state.clientRequestId=draft.clientRequestId;
 }catch{}await navigate('dashboard');}
 catch{showLogin();}
})();
