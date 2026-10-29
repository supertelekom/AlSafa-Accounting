const fs=require('fs'),vm=require('vm');
const path=require('path');
const assetDirectory=process.argv[2];
const outputDirectory=process.argv[3];
if(!assetDirectory||!outputDirectory) throw Error('Usage: node render_source_structure.cjs LEGACY_ASSETS OUTPUT_DIRECTORY');
const source=fs.readFileSync(path.join(assetDirectory,'main.js'),'utf8');
const store=new Map();
const empty=()=>{};
const element=()=>({classList:{add:empty,remove:empty,toggle:empty,contains:()=>false},style:{},dataset:{},innerHTML:'',textContent:'',querySelector:()=>null,querySelectorAll:()=>[],addEventListener:empty,setAttribute:empty,appendChild:empty});
const doc={addEventListener:empty,querySelector:()=>null,querySelectorAll:()=>[],getElementById:()=>null,documentElement:element(),body:element(),createElement:element,hidden:true};
// Fixed development fixture: reproducible empty-store layouts, never user data.
const fixtureTime=Date.parse('2026-10-29T00:00:00Z');
class FixtureDate extends Date { constructor(...args){super(...(args.length?args:[fixtureTime]));} static now(){return fixtureTime;} }
let seed=359;
const fixtureMath=Object.create(Math);fixtureMath.random=()=>{seed=(Math.imul(seed,1664525)+1013904223)>>>0;return seed/4294967296;};
const ctx={console,Date:FixtureDate,Math:fixtureMath,JSON,URL,Intl,Map,Set,Promise,Number,String,Object,Array,RegExp,Error,Uint8Array,TextEncoder,TextDecoder,document:doc,localStorage:{getItem:k=>store.get(k)||null,setItem:(k,v)=>store.set(k,String(v)),removeItem:k=>store.delete(k)},sessionStorage:{getItem:()=>null,setItem:empty,removeItem:empty},navigator:{onLine:false,userAgent:'source-audit'},location:{href:'http://localhost/',search:'',hash:'',protocol:'http:'},history:{replaceState:empty,pushState:empty},setTimeout:empty,clearTimeout:empty,setInterval:empty,clearInterval:empty,requestAnimationFrame:empty,addEventListener:empty,fetch:()=>{throw Error('Network disabled for source audit');},atob:s=>Buffer.from(s,'base64').toString('binary'),btoa:s=>Buffer.from(s,'binary').toString('base64')};
ctx.window=ctx;ctx.globalThis=ctx;
vm.createContext(ctx);
try {vm.runInContext(source.replace(/^render\(\);$/m,''),ctx,{timeout:5000});}catch(e){console.error('INIT:',e.stack);process.exit(1)}
// No account data: extract only the original empty-page structure, with no network/timers.
vm.runInContext("data.settings.externalPartners=[]; openModal=(html)=>{window.capturedModal=html};",ctx);
const requests={
 'sale-line-template':'saleLine({},1)', 'variant-row-template':'colorVariantRowHtml()', 'dashboard':'renderDashboard()', 'search':'renderSearch()', 'security':'renderSecurity()', 'shortcut-settings':'modernShortcutSettings()', 'cash-movement-form':'cashMovementModal()', 'discount-rule-form':'modernDiscountModal()', 'product-form':'productForm()', 'product-quick-form':'productQuickForm()', 'today-center':'renderTodayCenter()', 'products':'renderProducts()', 'maintenance-parts':'renderMaintenanceParts()',
 'purchases':'renderPurchases()', 'purchase-form':'renderPurchaseForm()', 'supplier-form':'supplierModal()',
 'maintenance-part-form':'maintenancePartForm()', 'used-watches':'renderUsedWatches()', 'used-watch-purchase':'renderUsedWatchPurchase()',
 'used-watch-exchange':'renderUsedWatchExchange()', 'expenses':'renderExpenses()', 'expense-form':'renderExpenseForm()',
 'debts':'renderDebts()', 'vaults':'renderVaults()', 'daily-close':'renderDailyClose()', 'wallet':'renderWallet()',
 'reports':'renderReports()', 'partners':'renderPartners()', 'settings':'renderSettings()',
 'customer-orders':'renderCustomerOrders()', 'customers':'renderCustomers()', 'warranties':'renderWarranties()',
 'stock-alerts':'renderStockAlerts()', 'inventory-movements':'renderInventoryMovements()', 'inventory-analytics':'renderInventoryAnalytics()',
 'audit-log':'renderAuditLog()', 'printer-settings':'renderPrinterSettings()', 'opening-balances':'renderOpeningBalances()',
 'modern-hub':'renderModernHub()', 'quick-repair-button-form':'quickRepairButtonModal()',
 'partner-withdrawal-form':'partnerWithdrawalModal()', 'customer-order-form':'customerOrderModal()',
 'father-home':'renderFatherHome()', 'advanced-home':'renderAdvancedHome()', 'more':'renderMore()',
 'father-sale':'renderFatherSale()', 'father-repair':'renderFatherRepair()', 'sale-form':'renderSaleForm()',
 'repair-form':'renderRepairForm()', 'shift-handover':'renderShiftHandover()', 'father-deliveries':'renderFatherDeliveries()',
 'sales':'renderSales()', 'repairs':'renderRepairs()', 'returns':'renderReturns()', 'watch-advisor':'renderWatchAdvisor()'
};
const routeKeys=[...source.matchAll(/'([a-z][a-z-]+)'\s*:\s*render[A-Za-z]+/g)].map(x=>x[1]);
for(const key of routeKeys) if(!requests[key]) requests[key]=`(route=${JSON.stringify(key)},bodyForRoute())`;
fs.mkdirSync(outputDirectory,{recursive:true});
for(const [name,expr] of Object.entries(requests)) {
 try { ctx.capturedModal=null;const html=vm.runInContext(expr,ctx,{timeout:3000})||ctx.capturedModal;if(typeof html!=='string')throw Error('No HTML');fs.writeFileSync(path.join(outputDirectory,name+'.html'),html);console.log(name,'OK'); }
 catch(e) { console.error(name,e.message);process.exitCode=1; }
}
