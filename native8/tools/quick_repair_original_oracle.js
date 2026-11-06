const fs=require('fs'),vm=require('vm');
const src=fs.readFileSync(process.argv[2],'utf8');
const backup=JSON.parse(fs.readFileSync(process.argv[3])).data;
const settings={quickRepairButtons:backup.settings.quickRepairButtons,quickRepairCosts:backup.settings.quickRepairCosts,quickRepairCostsCurrency:backup.settings.quickRepairCostsCurrency};
const parts=backup.maintenanceParts.map(p=>({id:p.id,name:p.name,stock:100000,currency:p.currency,cost:p.cost,salePrice:p.salePrice}));
const rates={USD:1,TRY:50,SYP:140,TRY_SYP:3};const cases=[];
for(const button of settings.quickRepairButtons)for(const unit of ['USD','TRY','SYP']) {
 const context={data:{settings,maintenanceParts:parts,repairs:[],rates},number:x=>Number(x)||0,normalizedCurrency:x=>x,rateFor:(c,s)=> (s||rates)[c],maintenancePartById:id=>parts.find(p=>p.id===id),
  quickRepairButtonById:id=>settings.quickRepairButtons.find(b=>b.id===id),quickRepairCostUSD:()=>0,
  deepCopy:x=>JSON.parse(JSON.stringify(x)),id:()=> 'test',nextInvoiceNumber:()=>1,TODAY:()=> '2026-11-06',NOW_TIME:()=> '12:00',normalizeWhatsAppChannel:()=> 'business',currentSnapshot:()=>rates,
  applyRepairPartsStock:()=>true,recordIncomingPayment:()=>{},commitData:()=>true,setTimeout:()=>{},money:(n,c)=>`${n} ${c}`,localeNumber:n=>n,toast:m=>{throw Error(m)},Date};
 const base=button.priceMode==='manual'?250:button.price;
 const form={quickId:button.id,service:button.label,price:base,workDone:'اختبار',deliverNow:'1',customer:'',phone:'',paymentMethod:'نقدي'};
 context.formData=()=>({get:k=>form[k]});context.fatherConvertedValue=()=>({currency:unit,value:base/rates.TRY*rates[unit]});
 vm.createContext(context);
 vm.runInContext(src.split('\n').filter(l=>l.startsWith('function convertCurrencyAmount(')||l.startsWith('function usdValue(')).join('\n'),context);
 vm.runInContext(src.slice(src.indexOf('function saveFatherRepair(form)'),src.indexOf('\nfunction searchMatches',src.indexOf('function saveFatherRepair(form)'))),context);
 vm.runInContext('saveFatherRepair({})',context);
 const r=context.data.repairs[0];cases.push({id:button.id,currency:unit,price:r.price,laborPrice:r.laborPrice,partsTotal:r.partsTotal,partsCost:r.partsCost,manualCost:r.manualCost,cost:r.cost});
}
fs.writeFileSync(process.argv[4],JSON.stringify({settings,parts,cases},null,2)+'\n');
console.log(cases.length+' original-source cases');
