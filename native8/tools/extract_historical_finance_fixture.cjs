// Run against the authorized v3.9.359 main.js and original backup; no app writes.
// node tools/extract_historical_sales_fixture.cjs MAIN_JS BACKUP_JSON OUTPUT_JSON
const fs=require('fs'),vm=require('vm');
const [sourcePath,backupPath,out]=process.argv.slice(2);
if(!out) throw Error('Expected MAIN_JS BACKUP_JSON OUTPUT_JSON');
const source=fs.readFileSync(sourcePath,'utf8');
const backup=JSON.parse(fs.readFileSync(backupPath,'utf8'));
const data=backup.data;
function fn(name) {
 const start=source.indexOf(`function ${name}(`);
 if(start<0) throw Error(`Missing source function ${name}`);
 const next=source.indexOf('\nfunction ',start+1);
 return source.slice(start,next<0?source.length:next);
}
const context=vm.createContext({data});
const currencyStart=source.indexOf("const CURRENCIES = {");
vm.runInContext(source.slice(currencyStart,source.indexOf("\n};",currencyStart)+3),context);
for(const name of ['number','normalizedCurrency','rateFor','usdValue','repairReceiptEvents','repairReceiptCost',
 'repairPartsInvoiceTotal','repairLaborPrice','repairInvoiceTotal','repairSelectedParts','taxNetAmount']) vm.runInContext(fn(name),context);
const expected=vm.runInContext(`(()=>{
 const events=[];
 const initialCount=data.repairs.filter(r=>number(r.received)>0).length;
 const later=data.payments.filter(p=>p.referenceType==='repair' && p.paymentType!=='repair-exchange-difference' && number(p.amount)>0 && data.repairs.some(r=>r.id===p.referenceId));
 let index=0;
 // Invoke the original receipt and cost functions; retain event identity/date.
 for(const e of repairReceiptEvents()) {
  const total=Math.max(0,repairInvoiceTotal(e.record)),ratio=total>0?Math.min(1,number(e.amount)/total):0;
  events.push({key:"legacy:repair:"+e.record.id+":"+(index<initialCount?"initial":"payment:"+later[index-initialCount].id),date:e.date,repairId:e.record.id,revenue:usdValue(taxNetAmount(repairInvoiceTotal(e.record),e.record.tax)*ratio,e.record.currency,e.rateSnapshot),
   cost:usdValue(repairReceiptCost(e),e.record.currency,e.rateSnapshot)});index++;
 }
 const returns=data.returns.filter(x=>x.referenceType==='repair').map(x=>{
  const taxRate=Math.max(0,number(data.repairs.find(r=>r.id===x.referenceId)?.taxRate||0))/100;
  const refundNet=taxRate>0?number(x.refundAmount)/(1+taxRate):number(x.refundAmount),extraNet=taxRate>0?number(x.extraCollected)/(1+taxRate):number(x.extraCollected);
  const before=x.costRecognitionBefore==null?1:Math.max(0,Math.min(1,number(x.costRecognitionBefore))),after=x.costRecognitionAfter==null?before:Math.max(0,Math.min(1,number(x.costRecognitionAfter)));
  return {id:x.id,date:x.date,profit:usdValue((-refundNet+extraNet)+number(x.returnedCost)*before-number(x.replacementCost)*after,x.currency,x.rateSnapshot)};
 });
 const expenses=data.expenses.map(x=>({id:x.id,date:x.date,usd:usdValue(x.amount,x.currency,x.rateSnapshot)}));
 const withdrawals=data.partnerWithdrawals.filter(x=>x.partnerType!=='external').map(x=>({id:x.id,date:x.date,partnerId:x.partnerId,usd:usdValue(x.amount,x.currency,x.rateSnapshot)}));
 return {events,returns,expenses,withdrawals};
})()`,context);
function pick(row,keys){return Object.fromEntries(keys.filter(k=>Object.hasOwn(row,k)).map(k=>[k,row[k]]));}
const common=['id','date','time','currency','rateSnapshot'];
const fixture={sourceSha256:require('crypto').createHash('sha256').update(source).digest('hex'),
 repairs:data.repairs.map(r=>({...pick(r,[...common,'price','laborPrice','taxRate','tax','cost','received','quickServiceName','isSimplifiedQuick']),
 usedParts:(r.usedParts||[]).map(p=>pick(p,['qty','price','cost']))})),
 payments:data.payments.filter(p=>p.referenceType==='repair').map(p=>pick(p,[...common,'referenceType','referenceId','amount','paymentType'])),
 returns:data.returns.filter(p=>p.referenceType==='repair').map(p=>pick(p,[...common,'referenceType','referenceId','refundAmount','extraCollected','returnedCost','replacementCost','costRecognitionBefore','costRecognitionAfter'])),
 expenses:data.expenses.map(p=>pick(p,[...common,'category','paymentMethod','vault','amount'])),
 withdrawals:data.partnerWithdrawals.map(p=>pick(p,[...common,'partnerId','partnerType','amount','vault'])),expected};
fs.writeFileSync(out,JSON.stringify(fixture,null,2)+'\n');
console.log(JSON.stringify({repairs:fixture.repairs.length,receipts:expected.events.length,
 repairProfit:expected.events.reduce((s,e)=>s+e.revenue-e.cost,0),returns:expected.returns.reduce((s,e)=>s+e.profit,0),
 expenses:expected.expenses.reduce((s,e)=>s+e.usd,0),withdrawals:expected.withdrawals.reduce((s,e)=>s+e.usd,0)}));
