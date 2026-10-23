import { scryptSync, randomBytes, timingSafeEqual, createHash } from 'node:crypto';

export const money = (minor) => `₱${(minor / 100).toLocaleString('en-PH',{minimumFractionDigits:2,maximumFractionDigits:2})}`;
export function hashPassword(password) {
  if (typeof password !== 'string' || password.length < 12 || password.length > 200) throw new Error('Password must be 12–200 characters.');
  const salt = randomBytes(16).toString('hex');
  const key = scryptSync(password,salt,64).toString('hex');
  return `scrypt$${salt}$${key}`;
}
export function verifyPassword(password, stored) {
  try {
    const [algo,salt,hex] = stored.split('$');
    if (algo !== 'scrypt' || !/^[a-f0-9]{32}$/.test(salt) || !/^[a-f0-9]{128}$/.test(hex)) return false;
    return timingSafeEqual(scryptSync(password,salt,64),Buffer.from(hex,'hex'));
  } catch { return false; }
}
export const sessionToken = () => randomBytes(32).toString('hex');
export const tokenHash = token => createHash('sha256').update(token).digest('hex');
export function requiredString(v,max=140) {
  if (typeof v !== 'string' || !v.trim() || v.trim().length > max) throw Object.assign(new Error(`Enter 1–${max} characters.`),{status:400});
  return v.trim();
}
export function minorFromPesos(value) {
  const str=String(value??'');
  if (!/^(0|[1-9]\d{0,7})(\.\d{1,2})?$/.test(str)) throw Object.assign(new Error('Enter a valid PHP price (up to two decimals).'),{status:400});
  const [p,c='']=str.split('.');
  const minor = Number(p)*100+Number(c.padEnd(2,'0'));
  if (minor > 100000000) throw Object.assign(new Error('Menu price must be at most ₱1,000,000.'),{status:400});
  return minor;
}
export function calculateTotals(items, menuById, discount=0, chargePercent=0) {
  let subtotal=0;
  const lines=items.map(row=>{
    const m=menuById.get(row.menu_item_id);
    if(!m || !m.active) throw Object.assign(new Error('A selected menu item is unavailable.'),{status:400});
    if(!Number.isInteger(row.quantity) || row.quantity<1 || row.quantity>99) throw Object.assign(new Error('Item quantity must be 1–99.'),{status:400});
    const item={menu_item_id:m.id,item_name:m.name,unit_price_minor:m.price_minor,quantity:row.quantity,notes:typeof row.notes==='string'?row.notes.trim().slice(0,180):'',line_total_minor:m.price_minor*row.quantity};
    subtotal+=item.line_total_minor;
    return item;
  });
  if(!Number.isInteger(discount)||discount<0||discount>subtotal) throw Object.assign(new Error('Invalid discount.'),{status:400});
  const serviceCharge=Math.round((subtotal-discount)*Number(chargePercent)/100);
  if(subtotal>100000000 || serviceCharge>100000000) throw Object.assign(new Error('Order is too large.'),{status:400});
  return {lines,subtotal_minor:subtotal,discount_minor:discount,service_charge_minor:serviceCharge,total_minor:subtotal-discount+serviceCharge};
}
export function receiptText({restaurant,order,items,payment}) {
  const paid=order.status==='paid';
  const fmt=n=>money(Number(n));
  const date=new Intl.DateTimeFormat('en-PH',{timeZone:'Asia/Manila',dateStyle:'medium',timeStyle:'short'}).format(new Date(order.created_at));
  const lines=[restaurant.name,restaurant.address||'',`#R-${String(order.order_number).padStart(6,'0')} • ${date}`,
    `Mode: ${order.service_type==='dine_in'?'Dine-in':'Takeout'}`,
    paid?'PAYMENT ACKNOWLEDGMENT':'ORDER SLIP - UNPAID',
    '--------------------------------',
    ...items.map(i=>`${i.quantity} x ${i.item_name}  ${fmt(i.line_total_minor)}${i.notes?`\n  ${i.notes}`:''}`),
    '--------------------------------',`Subtotal: ${fmt(order.subtotal_minor)}`,
    `Discount: ${fmt(order.discount_minor)}`,
    `Service charge: ${fmt(order.service_charge_minor)}`,
    `TOTAL: ${fmt(order.total_minor)}`,
    paid?`Payment: ${(payment?.method||'').toUpperCase()}`:'NOT YET PAID',
    paid?`Tendered: ${fmt(payment?.tendered_minor??0)}`:'',
    paid?`Change: ${fmt(payment?.change_minor??0)}`:'',
    '--------------------------------',
    'NON-BIR ORDER / PAYMENT ACKNOWLEDGMENT',
    'NOT AN OFFICIAL RECEIPT OR SALES INVOICE',
    'NOT VALID FOR INPUT TAX CLAIM',
    'A BIR-REGISTERED INVOICE MUST BE ISSUED',
    'WHEN REQUIRED BY LAW. REQUEST IT SEPARATELY.',
    'Thank you!'];
  return lines.filter(Boolean).join('\n');
}
