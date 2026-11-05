// Execute the authorized original calculation AND save function with isolated in-memory data.
// UI/storage/cash-balance boundaries are stubbed; all settlement arithmetic is original JS.
// node tools/extract_maintenance_return_fixture.cjs MAIN_JS BACKUP_JSON OUTPUT_JSON
const fs=require('fs'),vm=require('vm'),crypto=require('crypto');
const [path,backupPath,out]=process.argv.slice(2);if(!out)throw Error('Expected MAIN_JS BACKUP_JSON OUTPUT_JSON');
const source=fs.readFileSync(path,'utf8'),backup=JSON.parse(fs.readFileSync(backupPath,'utf8')).data;
function fn(name){const start=source.indexOf(`function ${name}(`);if(start<0)throw Error(name);const next=source.indexOf('\nfunction ',start+1);return source.slice(start,next<0?source.length:next);}
const rates={USD:1,TRY:50,SYP:140,TRY_SYP:3};
const base={id:'fixture-repair',invoiceNo:432,status:'تم التسليم',date:'2026-11-01',deliveredDate:'2026-11-01',currency:'USD',price:100,laborPrice:60,tax:0,taxRate:0,received:100,cost:20,manualCost:0,rateSnapshot:rates,usedParts:[{partId:'old-part',name:'قطعة',qty:2,price:20,cost:10,currency:'USD',costSnapshotVerified:true}]};
const form={id:base.id,kind:'return',repairPartIndex:'0',returnedPartQty:'1',creditAmount:'0',replacementPartId:'',replacementPartQty:'1',replacementAmount:'0',extraPaymentNow:'0',vault:'drawer',date:'2026-11-02',time:'12:00',notes:'مطابقة السورس'};
const replacement={id:'replacement',name:'بديل',stock:5,currency:'USD',salePrice:50,cost:15};
const specs=[
 {name:'damaged-part',f:{}}, {name:'restock-part',f:{restoreReturnedPart:'on'}},
 {name:'full-manual-return',f:{repairPartIndex:'',creditAmount:'100'}},
 {name:'exchange-part-partial-cash',f:{kind:'exchange',replacementPartId:'replacement',extraPaymentNow:'10',restoreReturnedPart:'on'}},
 {name:'exchange-labour-unpaid',f:{kind:'exchange',repairPartIndex:'',creditAmount:'0',replacementAmount:'50'}},
 {name:'exchange-cheaper',f:{kind:'exchange',replacementPartId:'replacement',restoreReturnedPart:'on'},replacement:{...replacement,salePrice:5}},
 {name:'partial-paid-debt-reduction',f:{},repair:{...base,received:50}},
 {name:'taxed-exchange',f:{kind:'exchange',replacementPartId:'replacement',extraPaymentNow:'100'},repair:{...base,taxRate:10,tax:10,price:110,received:110}},
 {name:'historical-fx-replacement',f:{kind:'exchange',replacementPartId:'replacement',extraPaymentNow:'2'},replacement:{...replacement,currency:'TRY',salePrice:1500,cost:500}},
 {name:'direct-try-syp',f:{kind:'exchange',replacementPartId:'replacement',extraPaymentNow:'100'},repair:{...base,currency:'SYP',usedParts:base.usedParts.map(p=>({...p,currency:'SYP'}))},replacement:{...replacement,currency:'TRY',salePrice:10,cost:3}},
 {name:'prior-return-and-payment',f:{},returns:[{id:'prior',referenceType:'repair',referenceId:base.id,repairPartIndex:0,returnedPartQty:1,creditAmount:-20,extraCollected:10,refundAmount:0,date:'2026-11-01'}],payments:[{id:'later',referenceType:'repair',referenceId:base.id,amount:5,date:'2026-11-01'}]}
];
const original=backup.repairs.find(r=>r.status==='تم التسليم' && r.usedParts?.length && r.received>0);
if(original) {
 const keys=['id','invoiceNo','status','date','currency','price','laborPrice','tax','taxRate','received','cost','manualCost','rateSnapshot'];
 const repair=Object.fromEntries(keys.filter(k=>Object.hasOwn(original,k)).map(k=>[k,original[k]]));
 repair.deliveredDate=original.date;
 repair.usedParts=original.usedParts.map(p=>Object.fromEntries(['partId','name','qty','price','cost','currency'].filter(k=>Object.hasOwn(p,k)).map(k=>[k,p[k]])));
 specs.push({name:'backup-original-part',repair,f:{id:original.id,date:'2026-11-02'},sourceRecordId:original.id});
}
const cases=specs.map(spec=>{
 const repair=structuredClone(spec.repair||base),replacementPart=structuredClone(spec.replacement||replacement),f={...form,...spec.f};
 const previous=structuredClone(spec.returns||[]),payments=structuredClone(spec.payments||[]);
 const data={repairs:[repair],maintenanceParts:[{id:'old-part',stock:8},replacementPart],returns:previous,payments:[...payments,...previous.filter(r=>r.extraCollected>0).map(r=>({referenceType:'repair',referenceId:repair.id,amount:r.extraCollected,paymentType:'repair-exchange-difference'}))],rates};
 // Ensure original backup part IDs have a stock row when restocking is selected.
 const cash=[],inventory=[];let counter=0;
 const context=vm.createContext({data,form:f,formData:x=>({get:k=>x[k]||''}),VAULTS:{drawer:{currencies:['USD','TRY','SYP'],name:'الدرج'}},
 vaultBalance:()=>1e9,currentSnapshot:()=>structuredClone(rates),deepCopy:structuredClone,id:()=>`fixture-return-${++counter}`,
 addInventoryMovement:x=>inventory.push(x),addCashTransaction:x=>cash.push(x),commitData:()=>true,toast:x=>{throw Error(x)},
 setTimeout:()=>{},invoiceNumber:r=>r.invoiceNo,money:n=>String(n),localeNumber:n=>String(n),TODAY:()=>f.date,NOW_TIME:()=>f.time});
 const currencyStart=source.indexOf('const CURRENCIES = {');vm.runInContext(source.slice(currencyStart,source.indexOf('\n};',currencyStart)+3),context);
 for(const name of ['number','normalizedCurrency','rateFor','convertCurrencyAmount','repairSelectedParts','repairPartsInvoiceTotal','repairLaborPrice','repairInvoiceTotal','onlineCollectionDiscountFor','paidFor','returnsFor','returnCreditFor','refundedFor','effectiveTotalFor','netPaidFor','balanceFor','maintenancePartById','repairReturnedPartQty','repairReturnCalculation','saveRepairReturn'])vm.runInContext(fn(name),context);
 const input=structuredClone(repair);input.nativeReturns=structuredClone(spec.returns||[]);input.nativePayments=payments;
 vm.runInContext('saveRepairReturn(form)',context);
 const expected=data.returns[0];delete expected.createdAt;
 return {name:spec.name,sourceRecordId:spec.sourceRecordId,input,form:f,replacement:spec.f.replacementPartId?structuredClone(spec.replacement||replacement):null,rates,expected,cash,inventory};
});
fs.writeFileSync(out,JSON.stringify({sourceSha256:crypto.createHash('sha256').update(source).digest('hex'),cases},null,2)+'\n');
console.log(`Original-source settlement cases: ${cases.length}`);
