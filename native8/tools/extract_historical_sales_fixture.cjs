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
for(const name of ['number','normalizedCurrency','rateFor','convertCurrencyAmount','usdValue','saleCost',
 'onlineSaleReceiptEvents','onlineSaleReceiptCost','taxNetAmount']) vm.runInContext(fn(name),context);
const expected=vm.runInContext(`(()=>{
 const result=[];
 for(const s of data.sales.filter(s=>(s.saleType||'store')!=='online'))result.push({
 key:'legacy:store:'+s.id,date:s.date,stream:'LEGACY_STORE',
 revenue:usdValue(taxNetAmount(s.total,s.tax),s.currency,s.rateSnapshot),
 cost:usdValue(saleCost(s),s.currency,s.rateSnapshot),adjustment:0});
 for(const e of onlineSaleReceiptEvents()){
 const total=Math.max(0,number(e.sale.total)),ratio=total>0?Math.min(1,number(e.amount)/total):0;
 result.push({key:'legacy:online:'+e.sale.id+':'+(e.receiptSource==='initial'?'initial':'payment:'+e.paymentId),
 date:e.date,stream:'LEGACY_ONLINE',revenue:usdValue(taxNetAmount(e.sale.total,e.sale.tax)*ratio,e.sale.currency,e.rateSnapshot),
 cost:usdValue(onlineSaleReceiptCost(e),e.sale.currency,e.rateSnapshot),adjustment:0});
 }
 for(const r of data.returns.filter(r=>r.referenceType==='sale')){
 const ratio=r.recognizedRatio==null?1:Math.max(0,Math.min(1,number(r.recognizedRatio)));
 result.push({key:'legacy:sale-return:'+r.id,date:r.date,stream:'RETURN',revenue:0,cost:0,
 adjustment:usdValue((-number(r.netCreditAmount!=null?r.netCreditAmount:r.creditAmount)+number(r.returnedCost)-number(r.replacementCost))*ratio,r.currency,r.rateSnapshot)});
 }
 return result;
})()`,context);
function pick(row,keys){return Object.fromEntries(keys.filter(k=>Object.hasOwn(row,k)).map(k=>[k,row[k]]));}
const common=['id','referenceId','referenceType','date','currency','rateSnapshot'];
const fixture={sourceSha256:require('crypto').createHash('sha256').update(source).digest('hex'),
 sales:data.sales.map(s=>({...pick(s,[...common,'saleType','total','paid','tax','taxRate',
 'paymentMetadataCorrectedAt','paymentCurrencyCorrectedAt','financialFootprintRepairedAt']),
 items:s.items.map(i=>pick(i,['qty','cost']))})),
 payments:data.payments.filter(p=>p.referenceType==='sale').map(p=>pick(p,[...common,'amount','onlineCollectionDiscount','returnAdjustment','nonCash','paymentCurrency','paymentAmount'])),
 cash:data.cashTransactions.filter(p=>p.referenceType==='sale').map(p=>pick(p,[...common,'type','amount'])),
 returns:data.returns.filter(p=>p.referenceType==='sale').map(p=>pick(p,[...common,'netCreditAmount','creditAmount','returnedCost','replacementCost','recognizedRatio'])),expected};
fs.writeFileSync(out,JSON.stringify(fixture,null,2)+'\n');
console.log(JSON.stringify({sales:fixture.sales.length,events:expected.length,
 profits:Object.fromEntries(['LEGACY_STORE','LEGACY_ONLINE','RETURN'].map(stream=>[stream,expected.filter(e=>e.stream===stream).reduce((sum,e)=>sum+e.revenue-e.cost+e.adjustment,0)]))}));
