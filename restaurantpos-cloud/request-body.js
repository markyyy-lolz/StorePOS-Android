/** Read at most maxBytes without converting individual UTF-8 chunks separately. */
export async function readRawBody(req, maxBytes){
 const parts=[];let size=0;
 for await(const chunk of req){
  const data=Buffer.isBuffer(chunk)?chunk:Buffer.from(chunk);
  size+=data.byteLength;
  if(size>maxBytes)throw Object.assign(new Error('Request too large.'),{status:413});
  parts.push(data);
 }
 return Buffer.concat(parts).toString('utf8');
}
