// Real templates and scripts; test-only data. No MinIO, credentials or business writes.
const fs = require('node:fs');
const path = require('node:path');
const os = require('node:os');
const http = require('node:http');
const { spawn } = require('node:child_process');
const assert = require('node:assert/strict');
const root = path.resolve('src/main/resources/static');
const projectVersion = fs.readFileSync(path.resolve('pom.xml'), 'utf8').match(/<artifactId>file-manager<\/artifactId>\s*<version>([^<]+)<\/version>/)[1];
const output = path.resolve(process.env.BROWSER_OUTPUT || 'design-preview/precision-build152');
const chrome = process.env.CHROME_PATH || 'C:/Program Files/Google/Chrome/Application/chrome.exe';
const items = [
 {objectName:'交付文件/项目文档/',fileName:'项目文档',isFolder:true,size:0,uploadTime:'2026-11-16T09:24:00'},
 {objectName:'交付文件/产品使用手册.pdf',fileName:'产品使用手册.pdf',isFolder:false,size:8808038,contentType:'application/pdf',uploadTime:'2026-11-16T10:20:00'},
 {objectName:'交付文件/一个用于验证超长文件名与操作区域不会互相遮挡的交付素材归档.zip',fileName:'一个用于验证超长文件名与操作区域不会互相遮挡的交付素材归档.zip',isFolder:false,size:432852172,contentType:'application/zip',uploadTime:'2026-11-16T10:32:00'}
];
const share = {fileName:'产品交付文件',isFolder:true,hasPassword:false,displayPath:'team-documents/交付文件',expireTime:'2026-11-23 18:00',frontendDownloadThresholdMB:100};
let shareVariant = "normal";
const requests = [];
const server = http.createServer((req,res) => {
 const url = new URL(req.url,'http://localhost');
 const name = url.pathname.replace(/^\/fm/, '');
 if(name.startsWith('/api/')) {
  requests.push(name);
  let data = [];
   if(req.method!=='GET'){res.writeHead(200,{'Content-Type':'application/json'});res.end(JSON.stringify({code:400,message:'界面验证环境不执行登录或业务写入'}));return;}
   if(name.startsWith('/api/share/') && shareVariant==='expired'){res.writeHead(200,{'Content-Type':'application/json'});res.end(JSON.stringify({code:410,message:'分享已过期'}));return;}
  if(name==='/api/auth/runtime-config') data={appVersion:projectVersion,forceHttps:false};
  else if(name==='/api/config/ui') data={mode:'full',uploadTransportMode:'DIRECT',downloadTransportMode:'DIRECT',uploadDialogMode:'MANUAL',fileDropMode:'MANUAL',fileRowHiddenActions:['DELETE','MOVE','COPY']};
  else if(name==='/api/file/buckets') data=[{name:'team-documents',creationDate:'2026-09-08 09:15:00',objectCount:3,totalSize:441660210,statsUpdatedAt:'2026-11-16 10:45:00'},{name:'release-deliverables-archive',creationDate:'2026-10-20 15:36:00',objectCount:128,totalSize:19821284000,statsUpdatedAt:'2026-11-16 10:45:00'},{name:'legacy-archive-imports',creationDate:'2026-10-29 11:42:00',compatible:false}];
   else if(name==='/api/messages/inbox') data={items:[{id:501,title:'收到目录分享',content:'内容维护向你分享了「交付文件」',shareContentText:'交付文件 / 验收材料',senderUsername:'editor',relatedShareCode:'visual-test',createTime:'2026-11-16 09:12:00',read:false}],total:1};
   else if(name==='/api/share/list') data={items:[{id:601,fileName:'产品使用手册.pdf',shareCode:'visual-test',targetType:'LINK',createTime:'2026-11-16 10:24:00',expireTime:'2026-11-23 18:00:00',viewCount:12}],total:1};
   else if(name==='/api/audit-logs') data={content:[{id:701,createTime:'2026-11-16 10:32:14',operatorUsername:'editor',module:'文件',action:'上传',targetName:'交付清单.xlsx',bucketName:'team-documents',objectName:'交付文件/交付清单.xlsx',success:true}],totalElements:1};
   else if(name==='/api/workbench/overview') data={defaultWorkbench:true,recentDirectories:[{bucketName:'team-documents',directoryPath:'交付文件/验收材料/',directoryName:'验收材料',pathText:'team-documents / 交付文件 / 验收材料',visitTime:'2026-11-16 10:32:00'},{bucketName:'team-documents',directoryPath:'项目文档/',directoryName:'项目文档',pathText:'team-documents / 项目文档',visitTime:'2026-11-16 09:24:00'}],favoriteDirectories:[{bucketName:'team-documents',directoryPath:'交付文件/',directoryName:'交付文件',pathText:'team-documents / 交付文件'}],shareReminders:[{id:501,messageId:501,title:'收到目录分享',content:'内容维护向你分享了「交付文件」',shareContentText:'交付文件 / 验收材料',createTime:'2026-11-16 09:12:00',read:false}]};
   else if(name==='/api/admin/users') data=[{id:11,username:'demo-admin',nickname:'演示管理员',enabled:true,roles:[{id:1,code:'ADMIN',name:'管理员'}]},{id:12,username:'editor',nickname:'内容维护',enabled:true,roles:[{id:2,code:'USER',name:'普通用户'}]}];
   else if(name==='/api/admin/roles') data=[{id:1,code:'ADMIN',name:'管理员',description:'管理存储、用户与系统配置'},{id:2,code:'USER',name:'普通用户',description:'按授权访问目录与文件'}];
   else if(name==='/api/share/info'||name==='/api/share/access-info') data={...share,hasPassword:shareVariant==='password'};
  else if(name==='/api/share/items') data=shareVariant==='empty'?[]:items;
  else if(name==='/api/file/list') data={records:items,total:items.length,page:1,size:25};
  res.writeHead(200,{'Content-Type':'application/json'});res.end(JSON.stringify({code:200,data}));return;
 }
 const file = path.resolve(root, '.'+(name==='/'?'/index.html':decodeURIComponent(name)));
 if(!file.startsWith(root+path.sep)||!fs.existsSync(file)||!fs.statSync(file).isFile()){res.writeHead(404);res.end();return;}
 const mime={'.html':'text/html; charset=utf-8','.js':'text/javascript; charset=utf-8','.css':'text/css; charset=utf-8','.svg':'image/svg+xml','.ico':'image/x-icon','.woff2':'font/woff2','.jpg':'image/jpeg','.jpeg':'image/jpeg','.png':'image/png','.webp':'image/webp'};
 res.writeHead(200,{'Content-Type':mime[path.extname(file)]||'application/octet-stream'});res.end(fs.readFileSync(file));
});
(async()=>{
 await new Promise(resolve=>server.listen(0,'127.0.0.1',resolve));
 fs.mkdirSync(output,{recursive:true});
 const profile=fs.mkdtempSync(path.join(os.tmpdir(),'workspace-ui-smoke-'));
 const browser=spawn(chrome,['--headless','--disable-gpu','--no-first-run','--no-default-browser-check','--remote-debugging-port=0','--user-data-dir='+profile,'about:blank']);
 let socket;const timer=setTimeout(()=>browser.kill(),60000);
 try {
  const endpoint=await new Promise((resolve,reject)=>{let text='';browser.stderr.on('data',b=>{text+=b;const match=text.match(/DevTools listening on (ws:\/\/[^\s]+)/);if(match)resolve(match[1]);});browser.once('error',reject);browser.once('exit',code=>reject(Error('Chrome exit '+code)));});
  socket=new WebSocket(endpoint);await new Promise((resolve,reject)=>{socket.onopen=resolve;socket.onerror=reject;});
  let id=0;const pending=new Map(),errors=[];
  socket.onmessage=e=>{const m=JSON.parse(e.data);if(m.method==='Runtime.exceptionThrown')errors.push(m.params.exceptionDetails);if(m.method==='Runtime.consoleAPICalled'&&m.params.type==='warning'){const text=m.params.args.map(a=>a.value||'').join(' ');if(/Failed to resolve component|Invalid vnode type/.test(text))errors.push({text});}if(pending.has(m.id)){const p=pending.get(m.id);pending.delete(m.id);m.error?p.reject(Error(m.error.message)):p.resolve(m.result);}};
  const send=(method,params={},sessionId)=>new Promise((resolve,reject)=>{const n=++id;pending.set(n,{resolve,reject});socket.send(JSON.stringify({id:n,method,params,...(sessionId?{sessionId}:{})}));});
  const {targetId}=await send('Target.createTarget',{url:'about:blank'});
  const {sessionId}=await send('Target.attachToTarget',{targetId,flatten:true});
  const cmd=(method,params)=>send(method,params,sessionId);
  const evaluate=async expression=>{const r=await cmd('Runtime.evaluate',{expression,awaitPromise:true,returnByValue:true});if(r.exceptionDetails)throw Error(JSON.stringify(r.exceptionDetails));return r.result.value;};
  const waitFor=expression=>evaluate('new Promise((resolve,reject)=>{const end=Date.now()+8000;function check(){if(('+expression+'))return resolve(true);if(Date.now()>end)return reject(Error("Condition timeout"));requestAnimationFrame(check)}check()})');
  const settle=()=>evaluate('Promise.resolve(typeof Vue==="undefined"?undefined:Vue.nextTick()).then(()=>new Promise(r=>requestAnimationFrame(()=>requestAnimationFrame(r)))).then(()=>Promise.all(document.getAnimations().filter(a=>a.effect && a.effect.getTiming().iterations!==Infinity).map(a=>a.finished.catch(()=>{}))))');
  const navigate=async file=>{await cmd('Page.navigate',{url:'http://127.0.0.1:'+server.address().port+'/fm/'+file});};
  const click=async selector=>{const b=await evaluate('document.querySelector('+JSON.stringify(selector)+').getBoundingClientRect().toJSON()');await cmd('Input.dispatchMouseEvent',{type:'mousePressed',x:b.x+b.width/2,y:b.y+b.height/2,button:'left',clickCount:1});await cmd('Input.dispatchMouseEvent',{type:'mouseReleased',x:b.x+b.width/2,y:b.y+b.height/2,button:'left',clickCount:1});};
  const toggleTheme=async (selector,theme)=>{await click(selector);await settle();assert.equal(await evaluate('document.documentElement.dataset.theme'),theme);};
  const screenshot=async name=>{
    const snapshot=await evaluate('(() => {const doc=document.documentElement.cloneNode(true);doc.querySelectorAll("script").forEach(n=>n.remove());const live=document.querySelectorAll("input,textarea,select");doc.querySelectorAll("input,textarea,select").forEach((n,i)=>{const v=live[i];if(n.tagName==="INPUT"){n.setAttribute("value",v.type==="password"?"•".repeat(v.value.length):v.value);n.toggleAttribute("checked",v.checked)}else if(n.tagName==="TEXTAREA")n.textContent=v.value;else Array.from(n.options).forEach((o,j)=>o.toggleAttribute("selected",v.options[j].selected))});doc.classList.remove("m-enter");doc.classList.add("m-enter-done");doc.querySelectorAll(".hero").forEach(n=>{n.classList.remove("is-entering");n.classList.add("is-entered","is-still")});const base=document.createElement("base");base.setAttribute("href","../../src/main/resources/static/");doc.querySelector("head").prepend(base);return "<!doctype html>\\n"+doc.outerHTML})()');
    fs.writeFileSync(path.join(output,name.replace(/\.png$/,'.html')),snapshot);
    if(process.env.BROWSER_CAPTURE==='0')return;const r=await cmd('Page.captureScreenshot',{format:'png',captureBeyondViewport:false});fs.writeFileSync(path.join(output,name),Buffer.from(r.data,'base64'));};
  const dimensions=()=>evaluate('({viewport:innerWidth,document:document.documentElement.scrollWidth,body:document.body.scrollWidth,height:document.documentElement.scrollHeight})');
   const toolbarProbe=()=>evaluate('(()=>{const strip=document.querySelector(".path-strip"), field=strip.querySelector(".path-input-wrapper"), primary=strip.querySelector(".file-toolbar-upload"), more=strip.querySelector(".file-toolbar-more > summary");const box=e=>e?.getBoundingClientRect()?.toJSON();return {strip:box(strip),address:box(field),primary:box(primary),more:box(more),nav:[...strip.querySelectorAll(".nav-buttons button")].map(box).filter(x=>x.width>0),actions:[...strip.querySelectorAll(".action-buttons button")].map(box).filter(x=>x.width>0)}})()');
   const assertToolbar=async width=>{const p=await toolbarProbe();assert.ok(p.address.width>=66,JSON.stringify(p));assert.ok(p.primary.width>0,JSON.stringify(p));const center=p.address.top+p.address.height/2;const sameLine=x=>Math.abs(center-x.top-x.height/2)<=5;assert.ok(sameLine(p.primary),'Address and upload must share one baseline at '+width+'px '+JSON.stringify(p));assert.ok(p.strip.right<=width && p.primary.right<=width,'Toolbar or upload overflow');if(width===1440){assert.ok(p.nav.length>=4&&p.actions.length>=7);assert.ok([...p.nav,...p.actions].every(sameLine),'Desktop toolbar controls must all be in one row');}if(width===390){assert.ok(p.more?.width>=40&&sameLine(p.more)&&p.more.right<=width,'Address, upload and more must be in one row');}console.log('Toolbar layout at '+width+'px',JSON.stringify({address:p.address.width,actions:p.actions.length,center}));};
   const responsiveSnapshots=async name=>{
    for(const width of [1024,768,390]){
     await cmd('Emulation.setDeviceMetricsOverride',{width,height:width===390?844:width===768?1024:900,deviceScaleFactor:1,mobile:false});await settle();
     assert.ok((await dimensions()).document<=width,name+' overflow '+width);
      if(name==='buckets-desktop.png' && width===390){
       const grid=await evaluate('(()=>{const panel=document.querySelector(".page-content--buckets .el-table"),row=panel.querySelector(".el-table__body tbody tr");return {cells:[...row.cells].map(e=>e.getBoundingClientRect().toJSON()), sort:[...panel.querySelectorAll("thead th.is-sortable")].filter(e=>e.getBoundingClientRect().width>0).length}})()');
       assert.equal(grid.cells.length,6);assert.ok(grid.cells.every(c=>c.width>0&&c.height>0&&c.left>=0&&c.right<=390),JSON.stringify(grid));assert.ok(grid.sort>=4,JSON.stringify(grid));
      }
      if(name==='official-files.png'){
       await assertToolbar(width);
       if(width===768){const v=await evaluate('(()=>{const box=s=>document.querySelector(s).getBoundingClientRect().toJSON();return {title:box(".header-left h2"), header:box(".header"), right:box(".header-right")}})()');assert.ok(v.title.top>=v.header.top && v.right.bottom<=v.header.bottom,'Tablet header should not clip or overflow '+JSON.stringify(v));}
       if(width===390){const y=await evaluate('[...document.querySelectorAll(".drive-context .overview-card")].slice(1).map(e=>e.getBoundingClientRect().top)');assert.equal(new Set(y).size,1,'Mobile file metrics must occupy the same row');}
      }
     await screenshot(name.replace('.png','-'+width+'.png'));
    }
    await cmd('Emulation.setDeviceMetricsOverride',{width:1440,height:1000,deviceScaleFactor:1,mobile:false});await settle();
   };
  await cmd('Page.enable');await cmd('Runtime.enable');
  await cmd('Emulation.setDeviceMetricsOverride',{width:1440,height:1000,deviceScaleFactor:1,mobile:false});
  await navigate('index.html');await waitFor('document.querySelector(".login-card input")');
  assert.equal(await evaluate('document.body.classList.contains("workspace-app")'),true);
  assert.equal(await evaluate('document.documentElement.dataset.theme'),'light');
  assert.doesNotMatch(await evaluate('getComputedStyle(document.querySelector(".login-container")).backgroundImage+" "+getComputedStyle(document.body).backgroundImage'),/ground\.svg/,'Login must not use ground.svg');
  assert.match(await evaluate('getComputedStyle(document.querySelector(".login-container")).backgroundImage'),/workspace-bg-light\.jpg/,'Light login background');
  await evaluate('document.fonts.ready');
  assert.equal(await evaluate("document.fonts.check('14px \"Host Grotesk\"')"),true,'Local font must load');
  assert.ok((await dimensions()).height <= 1000, 'Desktop login should not have a redundant vertical scrollbar');
   await settle();await screenshot('login-desktop.png');
   await toggleTheme('.theme-toggle-btn','dark');
   assert.equal(await evaluate('document.documentElement.classList.contains("dark")'),true);
   assert.match(await evaluate('getComputedStyle(document.querySelector(".login-container")).backgroundImage'),/workspace-bg-dark\.jpg/,'Dark login background');
   await screenshot('login-desktop-dark.png');
   await toggleTheme('.theme-toggle-btn','light');
   await cmd('Emulation.setDeviceMetricsOverride',{width:2560,height:1440,deviceScaleFactor:1,mobile:false});await settle();
   await screenshot('login-4k.png');
   await toggleTheme('.theme-toggle-btn','dark');
   await screenshot('login-4k-dark.png');
   await toggleTheme('.theme-toggle-btn','light');
   await cmd('Emulation.setDeviceMetricsOverride',{width:1440,height:1000,deviceScaleFactor:1,mobile:false});await settle();
   await responsiveSnapshots('login-desktop.png');
   await cmd('Emulation.setDeviceMetricsOverride',{width:390,height:844,deviceScaleFactor:1,mobile:false});await settle();
   assert.ok((await dimensions()).document<=390,'Login page overflows');
    const loginBottom=await evaluate('document.querySelector(".login-submit").getBoundingClientRect().bottom');assert.ok(loginBottom<=844&&loginBottom>0,'Login button must be in the first viewport: '+loginBottom);
    await screenshot('login-mobile.png');
   await cmd('Emulation.setDeviceMetricsOverride',{width:1440,height:1000,deviceScaleFactor:1,mobile:false});await settle();
  await evaluate('window.ui = document.querySelector("#app").__vue_app__._instance.proxy');
  const state={isLoggedIn:true,currentMenu:'files',userInfo:{username:'visual-test',nickname:'视觉测试',roles:['ADMIN']},permissionInfo:{superAdmin:true,menuPermissions:[],apiPermissions:[],bucketPermissions:[]},bucketList:[{name:'team-documents',objectCount:3,totalSize:441660210}],currentBucket:'team-documents',currentFolder:'交付文件/',breadcrumb:[{name:'team-documents',path:''},{name:'交付文件',path:'交付文件/'}],fileList:items,fileTotal:items.length,fileLoading:false};
  await evaluate('Object.assign(ui,'+JSON.stringify(state)+'); Object.assign(ui.fileConfig,{uploadDialogMode:"MANUAL",maxFileSizeMB:500,allowedExtensionsText:"pdf,zip"})');await settle();
  await waitFor('document.querySelectorAll(".el-table__body-wrapper tbody tr").length===3');
  assert.equal(await evaluate('document.querySelectorAll(".action-buttons .el-button").length>=6'),true);
  const controls = await evaluate('({button:getComputedStyle(document.querySelector(".file-toolbar-upload")).borderRadius,input:getComputedStyle(document.querySelector(".el-input__wrapper")).borderRadius,secondary:getComputedStyle(document.querySelector(".action-buttons .el-button--success")).backgroundColor})');
  assert.ok(parseFloat(controls.button)>0 && parseFloat(controls.input)>0);
   assert.notEqual(controls.secondary,await evaluate('getComputedStyle(document.querySelector(".file-toolbar-upload")).backgroundColor'));
   await assertToolbar(1440);
  const shell=await evaluate('({side:document.querySelector(".sidebar").getBoundingClientRect().toJSON(),header:document.querySelector(".header").getBoundingClientRect().toJSON()})');
  assert.equal(shell.side.x,0);assert.equal(shell.side.y,0);assert.equal(shell.side.height,1000);assert.ok(Math.abs(shell.header.x-shell.side.right)<=1);
  for(const name of ['View','Download','Share','Edit','Folder','ChatCircle'])assert.equal(await evaluate('!!ElementPlusIconsVue['+JSON.stringify(name)+']'),true,name);
  assert.equal(await evaluate('!!document.querySelector(".file-row-more-trigger svg")'),true,'More operations icon must actually render');
  assert.equal(await evaluate('!!document.querySelector(".message-center-trigger__icon svg")'),true,'Message icon must actually render');
  await cmd('DOM.enable');await cmd('CSS.enable');
  const {root:dom}=await cmd('DOM.getDocument');const {nodeId}=await cmd('DOM.querySelector',{nodeId:dom.nodeId,selector:'.header-left h2'});
  console.log('Rendered heading fonts:',JSON.stringify(await cmd('CSS.getPlatformFontsForNode',{nodeId})));
  const flat=await evaluate('(() => {const result={};for(const name of ["message-bubble","message-bubble message-bubble--unread","dialog-panel--sidebar","diagnostics-panel__toolbar","version-item active"]){const node=document.createElement("div");node.className=name;node.style.position="fixed";node.style.left="-10000px";document.body.appendChild(node);result[name]=getComputedStyle(node).backgroundImage;node.remove()}return result})()');
  assert.ok(Object.values(flat).every(value=>value==='none'),'Legacy surfaces must have flat computed backgrounds');
  console.log('Legacy computed surfaces:',JSON.stringify(flat));
   const pressTab=async()=>{await cmd('Input.dispatchKeyEvent',{type:'keyDown',key:'Tab',code:'Tab',windowsVirtualKeyCode:9});await cmd('Input.dispatchKeyEvent',{type:'keyUp',key:'Tab',code:'Tab',windowsVirtualKeyCode:9});};
   await evaluate("(()=>{const c=document.createElement('div');c.id='__focus_probes__';c.style.cssText='position:fixed;left:-10000px;top:0;width:40px;height:40px';c.innerHTML='<button id=\"__probe_start__\" tabindex=\"0\"></button><table class=\"el-date-table\"><tr><td id=\"probe-date\" tabindex=\"0\"></td></tr></table><table class=\"el-month-table\"><tr><td id=\"probe-month\" tabindex=\"0\"></td></tr></table><table class=\"el-year-table\"><tr><td id=\"probe-year\" tabindex=\"0\"></td></tr></table><div id=\"probe-dialog\" class=\"el-dialog\" tabindex=\"0\"></div><div id=\"__probe_negative__\" tabindex=\"-1\"></div>';document.body.appendChild(c);document.getElementById('__probe_start__').focus({preventScroll:true});})()");
   const negative=await evaluate("(()=>{const n=document.getElementById('__probe_negative__');const css=getComputedStyle(n);return {visible:n.matches(':focus-visible'),style:css.outlineStyle,outline:css.outlineWidth}})()");
   assert.equal(negative.visible,false,'Negative probe must not match :focus-visible');
   assert.equal(negative.style,'none','Negative probe must have no outline');
   const focus={};
   for(const [name,id] of [['el-date-table','probe-date'],['el-month-table','probe-month'],['el-year-table','probe-year'],['el-dialog','probe-dialog']]){
     await pressTab();
     const p=await evaluate("(()=>{const n=document.getElementById('"+id+"');const css=getComputedStyle(n);return {active:document.activeElement===n,visible:n.matches(':focus-visible'),outline:css.outlineWidth,style:css.outlineStyle,color:css.outlineColor}})()");
     assert.ok(p.active,name+' must receive focus on Tab');
     assert.ok(p.visible,name+' must match :focus-visible');
     assert.equal(p.style,'solid',name+' must have solid outline');
     assert.ok(parseFloat(p.outline)>0,name+' outline width must be positive: '+p.outline);
     focus[name]=p;
   }
   await evaluate("(()=>{document.getElementById('__focus_probes__')?.remove();})()");
   console.log('Keyboard focus probes:',JSON.stringify(focus),'Negative probe:',JSON.stringify(negative));
  assert.match(await evaluate('getComputedStyle(document.querySelector(".main-content")).backgroundImage'),/workspace-bg-light\.jpg/,'Light files background');
  await screenshot('official-files.png');
  await toggleTheme('.header .theme-toggle-btn','dark');
  assert.match(await evaluate('getComputedStyle(document.querySelector(".main-content")).backgroundImage'),/workspace-bg-dark\.jpg/,'Dark files background');
  await screenshot('official-files-dark.png');
  await evaluate('ui.showUploadDialog=true');await settle();
  const darkUploadBackground=await evaluate('(()=>{const dialog=Array.from(document.querySelectorAll(".el-dialog")).find(e=>e.offsetHeight>0 && e.textContent.includes("上传文件"));return dialog ? getComputedStyle(dialog).backgroundColor : null})()');
  assert.ok(darkUploadBackground,'Dark upload dialog must be visible');
  assert.notEqual(darkUploadBackground,'rgb(255, 255, 255)','Dark upload dialog must not have a white background');
  await screenshot('official-upload-dark.png');
  await evaluate('ui.showUploadDialog=false');await settle();
  await waitFor('(()=>{const b=document.querySelector(".header .theme-toggle-btn")?.getBoundingClientRect();if(!b)return false;const el=document.elementFromPoint(b.left+b.width/2,b.top+b.height/2);return el && !el.closest(".el-overlay")})()');
  await toggleTheme('.header .theme-toggle-btn','light');
  await cmd('Emulation.setDeviceMetricsOverride',{width:2560,height:1440,deviceScaleFactor:1,mobile:false});await settle();
  assert.ok(await evaluate('document.querySelector("#main-content .page-content").getBoundingClientRect().width >= 2200'),'File page content must be at least 2200px wide at 2560px');
  await screenshot('files-4k.png');
  await toggleTheme('.header .theme-toggle-btn','dark');
  await screenshot('files-4k-dark.png');
  await toggleTheme('.header .theme-toggle-btn','light');
  await cmd('Emulation.setDeviceMetricsOverride',{width:1440,height:1000,deviceScaleFactor:1,mobile:false});await settle();
  await responsiveSnapshots('official-files.png');
  const fixtureConfig=await evaluate('JSON.parse(JSON.stringify(ui.fileConfig))');
   await evaluate('ui.loadInbox()');await settle();
   for(const menu of ['workbench','buckets','shares','audit-logs','users','roles','minio-keys','config']){
   await evaluate('ui.handleMenuSelect('+JSON.stringify(menu)+')');await settle();
   assert.equal(await evaluate('document.querySelector("#main-content .page-content").getBoundingClientRect().width>0'),true,menu);
   if(menu==='workbench'){
     await waitFor('document.querySelector(".workbench-directory") && document.querySelector(".workbench-reminder")');
     const sections=await evaluate('({dirs:document.querySelector(".workbench-directories").getBoundingClientRect().toJSON(), reminders:document.querySelector(".workbench-reminders").getBoundingClientRect().toJSON()})');
     assert.ok(sections.reminders.left>=sections.dirs.right && Math.abs(sections.reminders.top-sections.dirs.top)<=1,'Workbench reminders must occupy the right column '+JSON.stringify(sections));
    }
    if(menu==='buckets')await waitFor('document.body.innerText.includes("release-deliverables-archive")');
    await screenshot(menu+'-desktop.png');
    if(menu==='workbench'){
     await toggleTheme('.header .theme-toggle-btn','dark');
     await screenshot('workbench-desktop-dark.png');
     await toggleTheme('.header .theme-toggle-btn','light');
     await cmd('Emulation.setDeviceMetricsOverride',{width:2560,height:1440,deviceScaleFactor:1,mobile:false});await settle();
     await screenshot('workbench-4k.png');
     await cmd('Emulation.setDeviceMetricsOverride',{width:1440,height:1000,deviceScaleFactor:1,mobile:false});await settle();
    }
    await responsiveSnapshots(menu+'-desktop.png');
  }
  await evaluate('Object.assign(ui.fileConfig,'+JSON.stringify(fixtureConfig)+');ui.currentMenu="files"');await settle();
  await evaluate('ui.showUploadDialog=true');await settle();
  await waitFor('Array.from(document.querySelectorAll(".el-dialog")).some(e=>e.offsetHeight>0 && e.textContent.includes("上传文件"))');
  assert.equal(await evaluate('document.querySelectorAll(".upload-rename-toolbar .el-input").length===0'),true);
  await evaluate('ui.pendingUploadFiles=[{id:"test",name:"交付清单.xlsx",targetName:"交付清单.xlsx",size:120000}]');await settle();
  assert.equal(await evaluate('document.querySelectorAll(".upload-rename-toolbar .el-input").length'),4);
  await screenshot('official-upload.png');
  await evaluate('ui.showUploadDialog=false; ui.showShareDialog=true; ui.shareForm.targetType="LINK"');await settle();
  assert.equal(await evaluate('Array.from(document.querySelectorAll(".el-dialog")).some(e=>e.offsetHeight>0 && e.textContent.includes("提取码"))'),true);
  await screenshot('share-link-desktop.png');
   await evaluate('ui.shareForm.targetType="USER"');await settle();
  assert.equal(await evaluate('Array.from(document.querySelectorAll(".el-dialog")).some(e=>e.offsetHeight>0 && e.textContent.includes("接收用户"))'),true);
  await screenshot('share-user-desktop.png');
   await evaluate('ui.showShareDialog=false; ui.downloadCenterVisible=true; ui.downloadCenterMinimized=false; ui.downloadTasks=[{id:"test",name:"交付素材.zip",type:"single-file",status:"running",progress:68,downloadedBytes:280000,totalBytes:412000,step:"正在下载"}]');await settle();
  assert.equal(await evaluate('document.querySelector(".download-task-center__dialog").offsetWidth>0'),true);
  assert.equal(await evaluate('!!document.querySelector(".download-task-center__action[title=暂停下载]")'),true);
  assert.equal(await evaluate('!!document.querySelector(".download-task-center__action[title=取消下载]")'),true);
  await screenshot('downloads-desktop.png');
  await evaluate(`Object.assign(ui, {
    downloadCenterVisible: false,
    transferUploadTasks: [{id:'smoke-upload',name:'交付素材分片.zip',status:'running',progress:50,uploadedBytes:33554432,totalBytes:67108864,completedParts:2,totalParts:4,step:'正在上传'}],
    downloadTasks: [{id:'smoke-download',name:'产品使用手册.pdf',type:'single-file',status:'running',progress:50,downloadedBytes:4404019,totalBytes:8808038,step:'正在下载'}]
  })`);await settle();
  const transferTrigger='.transfer-center-trigger';
  const triggers=await evaluate(`(() => {
    const probe=selector=>{
      const el=document.querySelector(selector);
      return el && {rect:el.getBoundingClientRect().toJSON(),visibility:getComputedStyle(el).visibility};
    };
    return {transfer:probe('${transferTrigger}'),message:probe('.message-center-trigger')};
  })()`);
  for(const [name,trigger] of Object.entries(triggers)){
    assert.ok(trigger?.rect.width>0 && trigger.rect.height>0 && trigger.visibility==='visible',name+' trigger must be visible');
  }
  const {transfer:{rect:transfer},message:{rect:message}}=triggers;
  assert.ok(transfer.right<=message.left || message.right<=transfer.left || transfer.bottom<=message.top || message.bottom<=transfer.top,'Transfer and message triggers must not overlap');
  await click(transferTrigger);await settle();
  for(const [theme,name] of [['light','transfer-center-desktop.png'],['dark','transfer-center-dark.png']]){
    if(theme==='dark')await toggleTheme('.header .theme-toggle-btn','dark');
    const panel=await evaluate(`(() => {
      const el=document.querySelector('.transfer-center-panel');
      const content=el.querySelector('.transfer-center-content');
      const rect=el.getBoundingClientRect();
      return {width:rect.width,left:rect.left,right:rect.right,viewport:innerWidth,
        overflow:el.scrollWidth>el.clientWidth || content.scrollWidth>content.clientWidth,
        directions:[...el.querySelectorAll('.transfer-center-direction')].map(node=>node.textContent.trim()),
        text:el.innerText};
    })()`);
    assert.ok(panel.width>0 && panel.left>=0 && panel.right<=panel.viewport && !panel.overflow,'Transfer panel must fit without horizontal overflow in '+theme+' theme');
    assert.deepEqual(panel.directions,['上传','下载']);
    assert.match(panel.text,/分片 2\/4/);
    await screenshot(name);
  }
  await toggleTheme('.header .theme-toggle-btn','light');
  await click(transferTrigger);
  await evaluate('ui.transferUploadTasks=[]; ui.downloadTasks=[]');await settle();
  assert.equal(await evaluate('document.querySelector(".transfer-center-panel").offsetWidth'),0,'Transfer panel must be closed before mobile checks');
  await cmd('Emulation.setDeviceMetricsOverride',{width:390,height:844,deviceScaleFactor:1,mobile:false});await settle();
  const mobile=await dimensions();console.log('Mobile file layout:',mobile);assert.ok(mobile.document<=390,'File page overflows');
  const primary=await evaluate('document.querySelector(".file-toolbar-upload").getBoundingClientRect().toJSON()');assert.ok(primary.bottom<=844,'Primary upload action must be visible');
  await screenshot('files-mobile.png');
   const more='.file-toolbar-more > summary';
   await click(more);await waitFor('document.querySelector(".file-toolbar-more[open]")');await settle();
   const visibleTools=await evaluate('[...document.querySelectorAll("#file-toolbar-overflow button")].filter(e=>e.getBoundingClientRect().width>0).map(e=>e.textContent.trim())');
   for(const name of ['新建','分享','下载','移动','复制','删除'])assert.ok(visibleTools.includes(name), 'Mobile menu lacks '+name+' '+JSON.stringify(visibleTools));
   assert.ok(visibleTools.some(name=>name==='收藏目录'||name==='已收藏'),JSON.stringify(visibleTools));
   await screenshot('files-toolbar-more-mobile.png');
   await cmd('Input.dispatchKeyEvent',{type:'keyDown',key:'Escape',code:'Escape',windowsVirtualKeyCode:27});await cmd('Input.dispatchKeyEvent',{type:'keyUp',key:'Escape',code:'Escape',windowsVirtualKeyCode:27});
   await waitFor('!document.querySelector(".file-toolbar-more[open]")');
   assert.equal(await evaluate('document.activeElement.matches(".file-toolbar-more > summary")'),true);
   await click(more);await waitFor('document.querySelector(".file-toolbar-more[open]")');
   await evaluate('document.querySelector(".workspace-nav-toggle").focus()');
   await waitFor('!document.querySelector(".file-toolbar-more[open]")');
   assert.equal(await evaluate('document.activeElement.matches(".workspace-nav-toggle")'),true,'Moving focus out must close the toolbar without taking focus back');
   await click('.path-edit-hotspot');await waitFor('document.activeElement.matches(".path-edit-input-native")');
   await cmd('Input.dispatchKeyEvent',{type:'keyDown',key:'Escape',code:'Escape',windowsVirtualKeyCode:27});await cmd('Input.dispatchKeyEvent',{type:'keyUp',key:'Escape',code:'Escape',windowsVirtualKeyCode:27});await waitFor('!document.querySelector(".path-edit-input-native")');
   console.log('Mobile file toolbar: hidden actions, expanded state, Escape, and address focus verified');
  assert.equal(await evaluate('document.querySelector(".page-content--files .el-table .el-scrollbar__wrap").scrollLeft'),0,'Mobile row actions must not require table scroll');
   const rowReach=await evaluate('document.querySelector(".page-content--files .file-row-more-trigger").getBoundingClientRect().toJSON()');
   assert.ok(rowReach.x>=0 && rowReach.right<=390 && rowReach.bottom<=844,JSON.stringify(rowReach));
   await click('.page-content--files .file-row-more-trigger');await settle();
   const rowMenu='Array.from(document.querySelectorAll(".el-dropdown__popper")).some(e=>e.offsetHeight>0 && e.textContent.includes("移动"))';
   assert.equal(await evaluate(rowMenu),true,'Mobile row menu must be reachable without horizontal scrolling');
   const rowEscape=async()=>{await cmd('Input.dispatchKeyEvent',{type:'keyDown',key:'ArrowDown',code:'ArrowDown',windowsVirtualKeyCode:40});await cmd('Input.dispatchKeyEvent',{type:'keyUp',key:'ArrowDown',code:'ArrowDown',windowsVirtualKeyCode:40});await settle();await cmd('Input.dispatchKeyEvent',{type:'keyDown',key:'Escape',code:'Escape',windowsVirtualKeyCode:27});await cmd('Input.dispatchKeyEvent',{type:'keyUp',key:'Escape',code:'Escape',windowsVirtualKeyCode:27});await settle();await waitFor('!('+rowMenu+')')};
   await rowEscape();await evaluate('document.querySelector(".page-content--files .file-row-more-trigger").focus()');
   assert.equal(await evaluate('document.activeElement.classList.contains("file-row-more-trigger")'),true);
   await cmd('Input.dispatchKeyEvent',{type:'keyDown',key:'Enter',code:'Enter',windowsVirtualKeyCode:13});await cmd('Input.dispatchKeyEvent',{type:'keyUp',key:'Enter',code:'Enter',windowsVirtualKeyCode:13});await settle();
   await waitFor(rowMenu);assert.equal(await evaluate(rowMenu),true,'Row menu must support keyboard Enter');await rowEscape();
   await evaluate('document.querySelector(".page-content--files .el-table .el-scrollbar__wrap").scrollLeft=0');await settle();
   console.log('Mobile row operations: no horizontal scroll, click and keyboard Enter/Escape passed',rowReach);
   await click('.workspace-nav-toggle');await waitFor('ui.navigationOpen && document.querySelector(".sidebar").getBoundingClientRect().left===0');
  await waitFor('document.activeElement?.classList.contains("workspace-nav-close")');
  assert.equal(await evaluate('document.activeElement.classList.contains("workspace-nav-close")'),true);
  await screenshot('navigation-mobile.png');
  await cmd('Input.dispatchKeyEvent',{type:'keyDown',key:'Escape',code:'Escape',windowsVirtualKeyCode:27});await cmd('Input.dispatchKeyEvent',{type:'keyUp',key:'Escape',code:'Escape',windowsVirtualKeyCode:27});await settle();
  assert.equal(await evaluate('ui.navigationOpen'),false);assert.equal(await evaluate('document.activeElement.classList.contains("workspace-nav-toggle")'),true);
  await click('.workspace-nav-toggle');await settle();await evaluate('ui.handleMenuSelect("files")');await waitFor('!ui.navigationOpen && !ui.fileLoading');
  await evaluate('ui.showUploadDialog=true');await settle();
  const upload=await evaluate('Array.from(document.querySelectorAll(".el-dialog")).filter(e=>e.offsetHeight>0).map(e=>({width:e.getBoundingClientRect().width,height:e.getBoundingClientRect().height}))');
  assert.ok(upload.every(d=>d.width<=390 && d.height<=844),'Upload dialog does not fit');
  await evaluate('ui.showUploadDialog=false; ui.permissionInfo={superAdmin:false,menuPermissions:["MENU_FILES"],apiPermissions:["API_FILE_BROWSE"],bucketPermissions:[{bucketName:"team-documents",actions:["READ"]}]}');await settle();
  assert.equal(await evaluate('!!document.querySelector(".file-toolbar-upload")'),false);
   await click('.file-toolbar-more > summary');await waitFor('document.querySelector(".file-toolbar-more[open]")');
   const restricted=await evaluate('[...document.querySelectorAll("#file-toolbar-overflow button")].filter(e=>e.getBoundingClientRect().width>0).map(e=>e.textContent.trim())');
   assert.ok(restricted.every(name=>!['上传','新建','分享','下载','移动','复制','删除'].includes(name)),JSON.stringify(restricted));
  await navigate('share.html?shareCode=visual-test');await waitFor('document.querySelectorAll("#itemList .item").length===3');
  const publicMobile=await dimensions();console.log('Mobile public share layout:',publicMobile);assert.ok(publicMobile.document<=390,'Public share overflows');
  assert.doesNotMatch(await evaluate('getComputedStyle(document.body).backgroundImage'),/ground\.svg/,'Public share must not use ground.svg');
  await screenshot('official-share-mobile.png');
  await cmd('Emulation.setDeviceMetricsOverride',{width:1280,height:860,deviceScaleFactor:1,mobile:false});await settle();
  assert.match(await evaluate('getComputedStyle(document.querySelector(".workspace-share")).backgroundImage'),/workspace-bg-light\.jpg/,'Light public share background');
  await screenshot('official-share-desktop.png');
  await toggleTheme('#shareThemeToggle','dark');
  assert.match(await evaluate('getComputedStyle(document.querySelector(".workspace-share")).backgroundImage'),/workspace-bg-dark\.jpg/,'Dark public share background');
  const publicDark=await dimensions();
  assert.ok(publicDark.document<=publicDark.viewport && publicDark.body<=publicDark.viewport,'Dark public share overflows');
  await screenshot('official-share-dark.png');
  await toggleTheme('#shareThemeToggle','light');
  await cmd('Emulation.setDeviceMetricsOverride',{width:2560,height:1440,deviceScaleFactor:1,mobile:false});await settle();
  const public4k=await dimensions();
  assert.ok(public4k.document<=public4k.viewport && public4k.body<=public4k.viewport,'4K public share overflows');
  await screenshot('share-4k.png');
  await cmd('Emulation.setDeviceMetricsOverride',{width:390,height:844,deviceScaleFactor:1,mobile:false});await settle();
  for(const variant of ['password','empty','expired']){
    shareVariant=variant;await navigate('share.html?shareCode=visual-'+variant);
    await waitFor(variant==='password'?'!document.querySelector("#verifyBlock").classList.contains("hidden")':variant==='empty'?'!document.querySelector("#emptyState").classList.contains("hidden")':'document.querySelector("#error").textContent.includes("分享已过期")');
    await settle();await screenshot('public-share-'+variant+'.png');
   }
   assert.equal(errors.length,0,JSON.stringify(errors));
  console.log('PASS: real template login, files, manual upload/rename, LINK/USER share, download panel, all management views, restricted permissions, local fonts, mobile navigation/Escape/focus, 390px, /fm context path, public share');
  await send('Browser.close');
 } finally {
  clearTimeout(timer);if(socket)socket.close();
  if(browser.exitCode===null)await new Promise(resolve=>{browser.once('exit',resolve);browser.kill();});
  await new Promise(resolve=>server.close(resolve));
  try{fs.rmSync(profile,{recursive:true,force:true,maxRetries:3,retryDelay:100});}catch{}
 }
})().catch(e=>{console.error(e.stack);process.exitCode=1;server.close();});
