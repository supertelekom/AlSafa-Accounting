from html.parser import HTMLParser
from pathlib import Path
import json,re,hashlib,sys,gzip
class Parse(HTMLParser):
 def __init__(self):
  super().__init__(convert_charrefs=True);self.root={'tag':'root','a':{},'c':[]};self.stack=[self.root]
 def handle_starttag(self,t,a):
  n={'tag':t,'a':dict(a),'c':[]};self.stack[-1]['c'].append(n)
  if t not in ['input','img','br','hr','meta','link','source','wbr']: self.stack.append(n)
 def handle_startendtag(self,t,a): self.handle_starttag(t,a);self.handle_endtag(t)
 def handle_endtag(self,t):
  for i in range(len(self.stack)-1,0,-1):
   if self.stack[i]['tag']==t:self.stack=self.stack[:i];break
 def handle_data(self,d):
  if d.strip():self.stack[-1]['c'].append(d)
def txt(n):
 if isinstance(n,str):return n
 return ' '.join(txt(x) for x in n['c']).strip()
classes={
 'mode-main-grid':'modeGrid','advisor-options-lux':'choiceGrid','advisor-budget-lux':'choiceGrid','advisor-lux-actions':'actions','modern-shortcuts-grid':'actionGrid','modern-shortcut':'flow','page-heading':'heading','father-head':'heading','simple-home-head':'heading','modal-head':'heading',
 'form-grid':'formGrid','report-filter':'formGrid','summary-grid':'summaryGrid','report-grid':'reportGrid','manage-grid':'manageGrid',
 'modern-feature-grid':'modernGrid','modern-grid':'manageGrid','wallet-grid':'reportGrid','wallet-methods':'summaryGrid',
 'father-main-actions':'simpleGrid','simple-actions':'actionGrid','quick-service-grid':'repairGrid',
 'form-actions':'actions','row-actions':'flow','photo-actions':'column','photo-picker':'photoRow',
 'field':'field','section-card':'section','form-card':'form','father-form':'form','form-section':'sectionHeading',
 'section-title':'sectionHeading','product-details-title':'sectionHeading',
 'summary-card':'stat','report-tile':'stat','photo-preview':'picture','father-selected':'stat',
 'help':'help','barcode-help':'help','empty':'empty','search-box':'searchRow',
 'father-payment':'radioGrid','father-delivery-choice-simple':'radioGrid','father-currency':'radioGrid',
 'father-step':'step','handover-money-grid':'summaryGrid','handover-money-currency':'section',
 'opening-vault-box':'section','opening-currency-grid':'summaryGrid','currency-home-block':'section',
 'home-currency-ledger':'summaryGrid','currency-home-values':'summaryGrid',
 'purchase-line':'section','purchase-line-head':'heading','line-item':'section','line-items':'formGrid',
 'barcode-input':'inputRow','barcode-lookup':'inputRow','custom-amount-row':'inputRow',
 'advisor-profile-chips':'flow','checkbox-field':'checkField','calculation-box':'stat'
}
# Only structural previews; no values are copied from a user's backup.
def convert(n,path='0'):
 if isinstance(n,str):
  value=re.sub(r'\s+',' ',n).strip()
  return {'kind':'text','text':value,'key':path} if value else None
 tag=n['tag'];a=n['a'];cs=a.get('class','').split();text=txt(n)
 if 'app-version' in cs:return None
 if tag=='div' and 'field' in cs and any(isinstance(x,dict) and x['tag']=='label' and txt(x) in ['مصدر رأس المال','نسبة الشريك من الربح/الخسارة %','مصدر دفع الفاتورة','مصدر تمويل الصنف'] for x in n['c']):return None
 if tag=='div' and 'product-details-title' in cs and 'الشراكة الخارجية' in text:return None
 if tag=='p' and any(x in text for x in ['الشريك الخارجي','الشريكين الخارجيين','الشركاء الخارجيين']):return None
 if tag in ['script','style'] or 'sync-status' in cs or 'data-safety-banner' in cs:return None
 if tag=='input' and a.get('type')=='hidden':return None
 if tag in ['section','div'] and any(label in text for label in ['الشريكان الخارجيان','التسوية الشهرية للشريكين الخارجيين']):
  # Omit the individual external-partner section, not its ancestors.
  direct_heading=[txt(x) for x in n['c'] if isinstance(x,dict) and (x['tag'] in ['h3','h4'] or 'section-title' in x['a'].get('class','').split())]
  if any('الشريكان الخارجيان' in x or 'التسوية الشهرية للشريكين الخارجيين' in x for x in direct_heading):return None
 if str(a.get('data-action','')).startswith('safe-union-'):return None
 if a.get('data-action') in ['test-connection','refresh-rates','cloud-pair-create','cloud-pair-join','cloud-sync-now','daily-balance-audit','daily-cloud-audit']:return None
 if tag=='button' and ('مزامنة' in text or 'فحص'==text.strip()):return None
 if tag in ['p','small'] and ('آخر مزامنة:' in text or 'حالة الإنترنت:' in text or 'التحديث يحاول اتصال أندرويد' in text):return None
 kind=next((classes[c] for c in cs if c in classes),'column')
 if tag=='root':kind='column'
 elif tag in ['h1','h2','h3','h4']:kind='title'
 elif tag in ['p','small','b','strong','span','em','label','summary','time']:
  if not any(isinstance(x,dict) and x['tag'] in ['input','select','textarea','button','div'] for x in n['c']):
   kind='text'
  elif tag=='label' and any(isinstance(x,dict) and x['tag']=='input' and x['a'].get('type') in ['checkbox','radio'] for x in n['c']):kind='toggle'
 elif tag=='button':kind='button'
 elif tag=='input':kind='input'
 elif tag=='textarea':kind='input'
 elif tag=='select':kind='select'
 elif tag=='img':kind='picture'
 elif tag in ['hr','br']:kind='line'
 elif tag=='details':kind='fold'
 d={'kind':kind,'key':path}
 if cs:d['sourceClasses']=cs
 if 'full' in cs:d['full']=True
 if 'hidden' in cs or 'hidden' in a or 'display:none' in a.get('style','').replace(' ',''):d['hidden']=True
 if 'id' in a:d['sourceId']=a['id']
 data={k:v or '' for k,v in a.items() if k.startswith('data-')}
 if data:d['data']=data
 if kind in ['text','title','button','toggle']:d['text']=re.sub(r'\s+',' ',text).strip()
 if kind=='text' and d.get('text','').startswith('معرّف:'):d['text']='معرّف: —'
 if kind in ['text','title']:
  d['weight']='bold' if tag in ['h1','h2','h3','h4','b','strong'] else 'normal'
  d['size']=20 if tag in ['h1','h2'] else 16 if tag in ['h3','h4'] else 11 if tag in ['small','label'] else 13
  if tag in ['p','small','label']:d['muted']=True
 if kind=='button':
  rich=any(x in cs for x in ['manage-card','modern-feature-card','simple-action','father-main-card','mode-card','mode-advisor-card','advisor-option-lux','advisor-budget-tile','quick-service'])
  if rich:d['children']=[x for i,c in enumerate(n['c']) if (x:=convert(c,path+'.'+str(i))) is not None]
  for attr,out in [('data-route','route'),('data-action','action'),('data-target-route','targetRoute')]:
   if attr in a:d[out]=a[attr]
  if a.get('type')=='submit' or 'data-save-form' in a:d['submit']=True
  d['primary']='primary-button' in cs or 'father-complete' in cs
  d['disabled']='disabled' in a
 if kind=='toggle':
  control=next(x for x in n['c'] if isinstance(x,dict) and x['tag']=='input')
  d['inputType']=control['a'].get('type');d['name']=control['a'].get('name','');d['value']=control['a'].get('value','');d['checked']='checked' in control['a']
 if kind=='input':
  for attr in ['name','type','placeholder','min','max','step','inputmode','maxlength']:
   if attr in a:d[attr]=a[attr]
  if tag=='textarea':d['type']='multiline'
  d['value']=txt(n) if tag=='textarea' else a.get('value','')
  d['disabled']='disabled' in a;d['required']='required' in a
  if d.get('type')=='date':d['value']='@monthStart' if (a.get('name')=='from' or a.get('id','').endswith('-from')) and a.get('value','').endswith('-01') else '@today'
  if d.get('type')=='time':d['value']='@time'
  if d.get('type')=='month':d['value']='@month'
 if kind=='select':
  opts=[x for x in n['c'] if isinstance(x,dict) and x['tag']=='option']
  d['name']=a.get('name','');d['options']=[{'label':txt(x),'value':x['a'].get('value',txt(x)),'disabled':'disabled' in x['a']} for x in opts]
  d['selected']=next((i for i,x in enumerate(opts) if 'selected' in x['a']),0)
 if kind=='picture':d['text']=a.get('alt',text or 'لا توجد صورة')
 if kind not in ['text','title','button','input','select','toggle','picture','line']:
  children=[convert(x,path+'.'+str(i)) for i,x in enumerate(n['c'])]
  d['children']=[x for x in children if x is not None]
  if not d['children'] and not any(k in a for k in ['id','data-color-variants']):return None
 return d
pages={}
input_directory=Path(sys.argv[1]) if len(sys.argv)>1 else None
output_file=Path(sys.argv[2]) if len(sys.argv)>2 else None
if input_directory is None or output_file is None:raise SystemExit('Usage: python3 derive_structure.py RENDERED_DIRECTORY OUTPUT_JSON_GZ')
for path in sorted(input_directory.glob('*.html')):
 parser=Parse();parser.feed(path.read_text());pages[path.stem]=convert(parser.root,path.stem)
# Prevent default family names/shares and rates from masquerading as restored settings.
def clear_settings(node,page,field=False):
 if not isinstance(node,dict):return
 if node.get('kind')=='input':
  name=node.get('name','')
  if page=='settings' and (name.startswith(('name','share')) or name in ['SYP','TRY','TRY_SYP','whatsappNumber']):node['value']=''
  if node.get('disabled'):node['value']='—'
 for x in node.get('children',[]):clear_settings(x,page)
for key,page in pages.items():
 clear_settings(page,key)
 if key.endswith('-template') and len(page.get('children',[]))==1:pages[key]=page['children'][0]
result={'sourceVersion':'3.9.359','purpose':'Native UI structure only; no accounting operations','pages':pages}
output_file.parent.mkdir(parents=True,exist_ok=True)
output_file.write_bytes(gzip.compress(json.dumps(result,ensure_ascii=False,separators=(',',':')).encode(),mtime=0))
print(len(pages),'source page/form structures;',output_file.stat().st_size,'compressed bytes')
