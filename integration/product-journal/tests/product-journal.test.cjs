const {test}=require('node:test');
const assert=require('node:assert/strict');
const fs=require('node:fs');
const vm=require('node:vm');
const path=require('node:path');
const assets=path.join(__dirname,'../assets');
const source=fs.readFileSync(path.join(assets,'main.js'),'utf8');
const journalSource=fs.readFileSync(path.join(assets,'product-journal.js'),'utf8');
const product={id:'p1',name:'ساعة',stock:2,salePrice:25,photo:'original-photo'};
function fixture(initial={products:[],maintenanceParts:[],inventoryMovements:[]}) {
 const disk=new Map([['store',JSON.stringify(initial)]]); let fail=false,n=0;
 const c={console,Uint8Array,crypto:require('node:crypto').webcrypto,ALSAFA_PRODUCT_JOURNAL_TEST:true,
  data:structuredClone(initial),memoryPayload:'',STORE_KEY:'store',SESSION_KEY:'session',WINDOW_PAYLOAD_PREFIX:'payload:',window:{name:''},
  destructivePersistAllowance:0,cloudLastDiagnostic:'',emptyData:{version:1},APP_VERSION:'integration-1',
  number:x=>Number(x)||0,stockValue:p=>Number(p.stock)||0,deepCopy:structuredClone,
  parsePayload:s=>{try{return JSON.parse(s)}catch{return null}},
  nativeBridge:()=>null,securityLoad:()=>({deviceId:'test-device'}),toast:()=>{},
  inventoryShrinkIsExactlyAuthorized:()=>false,
  cloudStampLocalChanges:()=>{throw Error('legacy sync invoked')},
  localStorage:{getItem:k=>disk.get(k)||null,setItem:(k,v)=>{if(fail)throw Error('quota');disk.set(k,v)}},
  sessionStorage:{setItem:(k,v)=>disk.set(k,v),getItem:k=>disk.get(k)||null,removeItem:k=>disk.delete(k)}};
 c.savedPayload=()=>c.memoryPayload||disk.get('store');c.loadData=()=>JSON.parse(c.savedPayload());
 vm.createContext(c);vm.runInContext(journalSource,c);
 const persist=source.slice(source.indexOf('function persistPayload(payload) {'),source.indexOf('function hydrateData(saved) {'));
 const save=source.slice(source.indexOf('function saveData() {'),source.indexOf('function reloadDataFromStorage('));
 vm.runInContext(persist+'\n'+save,c);
 return {c,disk,setFail:v=>fail=v,stage:(a,b)=>c.AlSafaProductJournal.stage(a,b,'test-device',()=>`op-${++n}`,123)};
}
test('new product and journal persist in the same durable payload',()=>{const {c,disk}=fixture();c.data.products.push(product);assert.equal(c.saveData(),true);const saved=JSON.parse(disk.get('store'));assert.equal(saved.products[0].photo,'original-photo');assert.equal(saved.productJournal.entries.length,1);assert.equal(saved.productJournal.entries[0].status,'LOCAL_ONLY')});
test('reopen then save does not duplicate operation',()=>{const f=fixture();f.c.data.products.push(product);f.c.saveData();const reopened=fixture(JSON.parse(f.disk.get('store')));assert.equal(reopened.c.saveData(),true);assert.equal(reopened.c.data.productJournal.entries.length,1)});
test('updates preserve prior event and record before/after',()=>{const f=fixture();f.c.data.products.push({...product});f.c.saveData();f.c.data.products[0].name='new';f.c.saveData();const entries=f.c.data.productJournal.entries;assert.equal(entries.length,2);assert.equal(entries[0].after.name,'ساعة');assert.equal(entries[1].before.name,'ساعة')});
test('same name with distinct color IDs remains two independent products',()=>{const f=fixture();f.c.data.products.push({...product},{...product,id:'p2',dialColor:'black'});assert.equal(f.c.saveData(),true);assert.equal(f.c.data.productJournal.entries.length,2)});
test('storage failure rolls back memory, session and product; retry persists once',()=>{const f=fixture();f.c.data.products.push({...product});f.setFail(true);assert.equal(f.c.saveData(),false);assert.equal(f.c.loadData().products.length,0);assert.equal(f.c.data.products.length,0);assert.equal(f.disk.has('session'),false);assert.equal(f.c.window.name,'');f.setFail(false);f.c.data.products.push({...product});assert.equal(f.c.saveData(),true);assert.equal(f.c.data.productJournal.entries.length,1)});
test('existing journal survives unrelated save without trusting edited journal in RAM',()=>{const f=fixture();f.c.data.products.push({...product});f.c.saveData();f.c.data.productJournal.entries=[];f.c.data.settings={hideHomeAmounts:true};f.c.saveData();assert.equal(f.c.data.productJournal.entries.length,1)});
test('product removal is rejected even when a replacement keeps the count',()=>{const f=fixture({products:[product]});f.c.data.products=[{...product,id:'replacement'}];assert.equal(f.c.saveData(),false);assert.equal(f.c.data.products[0].id,'p1')});
test('invalid product and duplicate IDs cannot be committed',()=>{for(const products of [[{...product,stock:-1}],[{...product,salePrice:NaN}],[product,product]]){const f=fixture();f.c.data.products=products;assert.equal(f.c.saveData(),false);assert.equal(JSON.parse(f.disk.get('store')).products.length,0)}});
test('unexplained stock movement remains blocked',()=>{const f=fixture({products:[product]});f.c.data.products[0].stock=1;assert.equal(f.c.saveData(),false);assert.equal(f.c.data.products[0].stock,2)});
test('future journal versions fail closed',()=>{const f=fixture({products:[],productJournal:{version:2,entries:[]}});f.c.data.products.push(product);assert.equal(f.c.saveData(),false)});
test('Firebase guards stop auth, reads/writes and forced sync before any work',async()=>{const f=fixture();for(const [start,end,name,args] of [
 ['async function firebaseAuthToken() {','function firestoreBase()', 'firebaseAuthToken',[]],
 ['async function firestoreRequest(url, options = {}) {','async function firestorePut', 'firestoreRequest',['url']],
 ]){const a=source.indexOf(start); const opening=source.indexOf("throw Error('LOCAL_ONLY_INTEGRATION');",a);assert.ok(opening>a);vm.runInContext(source.slice(a,opening+"throw Error('LOCAL_ONLY_INTEGRATION');".length)+'\n}',f.c);await assert.rejects(f.c[name](...args),/LOCAL_ONLY/)}
 const start=source.indexOf('async function cloudSyncNow('),end=source.indexOf('  if (cloudSyncRunning)',start);vm.runInContext(source.slice(start,end)+'}',f.c);assert.equal(await f.c.cloudSyncNow({force:true}),false)
});
