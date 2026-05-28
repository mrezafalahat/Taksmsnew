let screen = "dashboard";
let wizardStep = 0;
let editing = null;
let expanded = {};
let historyFilter = "ALL";
const app = document.getElementById("app");

const hasBridge = () => typeof TakBridge !== "undefined";

function getRules(){
  try { return hasBridge() ? JSON.parse(TakBridge.getRules()) : []; }
  catch(e){ return []; }
}

function getHistory(){
  try { return hasBridge() ? JSON.parse(TakBridge.getHistory()) : []; }
  catch(e){ return []; }
}

function saveRuleToKotlin(rule){
  if(!hasBridge()){
    alert("Bridge فعال نیست");
    return;
  }
  TakBridge.saveRule(
    String(rule.id || ""),
    rule.name || "",
    rule.sender || "",
    rule.keyword || "",
    rule.target || "",
    rule.suffix || "",
    !!rule.enabled,
    !!rule.allowOtp
  );
}

function deleteRuleFromKotlin(id){
  if(hasBridge()) TakBridge.deleteRule(String(id));
}

function toggleRuleInKotlin(id, enabled){
  if(hasBridge()) TakBridge.toggleRule(String(id), !!enabled);
}

function clearHistoryInKotlin(){
  if(hasBridge()) TakBridge.clearHistory();
}

function render(){
  if(screen === "dashboard") renderDashboard();
  if(screen === "filters") renderFilters();
  if(screen === "edit") renderWizard();
  if(screen === "history") renderHistory();
  if(screen === "settings") renderSettings();
}

function shell(title, sub, content, fab=true){
  app.innerHTML = `
    <div class="screen">
      <div class="header">
        <div class="header-row">
          <div>
            <div class="hello">${sub || ""}</div>
            <div class="h-title">${title}</div>
          </div>
          <div style="display:flex;gap:10px">
            <button class="icon-btn">🔔</button>
            <div class="avatar">تک</div>
          </div>
        </div>
      </div>
      <div class="content">${content}</div>
    </div>

    ${fab ? `<button class="fab" onclick="newRule()">+</button>` : ""}

    <div class="bottom-nav">
      <button class="nav-item ${screen==="dashboard"?"active":""}" onclick="screen='dashboard';render()"><div class="ico">⊞</div><span>داشبورد</span></button>
      <button class="nav-item ${screen==="filters"?"active":""}" onclick="screen='filters';render()"><div class="ico">⚡</div><span>فیلترها</span></button>
      <button class="nav-item ${screen==="history"?"active":""}" onclick="screen='history';render()"><div class="ico">↺</div><span>History</span></button>
      <button class="nav-item ${screen==="settings"?"active":""}" onclick="screen='settings';render()"><div class="ico">⚙</div><span>تنظیمات</span></button>
    </div>
  `;
}

function renderDashboard(){
  const rules = getRules();
  const history = getHistory();
  const success = history.filter(x => x.success).length;
  const fail = history.filter(x => !x.success).length;
  const enabled = rules.filter(x => x.enabled).length;

  const html = `
    <div class="hero">
      <div class="hero-top">
        <div class="status-pill">● فعال</div>
        <div style="font-size:30px">📡</div>
      </div>
      <div class="hero-num">${fa(success)}</div>
      <div class="hero-sub">ارسال موفق ثبت‌شده</div>
      <div class="hero-stats">
        <div class="hero-stat"><b>${fa(enabled)}</b><span>فیلتر فعال</span></div>
        <div class="hero-stat"><b>${fa(fail)}</b><span>ناموفق</span></div>
        <div class="hero-stat"><b>${fa(history.length)}</b><span>کل رویداد</span></div>
      </div>
    </div>

    <div class="stat-row">
      ${statCard("فیلترها", rules.length, "کل قوانین", "⚡", "#6366F1")}
      ${statCard("فعال", enabled, "در حال کار", "✓", "#10B981")}
      ${statCard("خطا", fail, "بررسی شود", "!", "#EF4444")}
    </div>

    <div class="section-head">
      <div class="section-title">آخرین عملیات</div>
      <button class="link-btn" onclick="screen='history';render()">مشاهده همه</button>
    </div>

    ${history.slice(0,3).map(historyCard).join("") || `<div class="card empty">هنوز History ثبت نشده</div>`}
  `;

  shell("TAK SMS", "کنترل پنل فوروارد پیامک", html);
}

function statCard(label,value,sub,icon,color){
  return `
    <div class="stat-card">
      <div class="stat-icon" style="background:${color}18">${icon}</div>
      <div class="stat-value">${fa(value)}</div>
      <div class="stat-label">${label}</div>
      <div class="stat-sub" style="color:${color}">${sub}</div>
    </div>
  `;
}

function renderFilters(){
  const rules = getRules();
  let html = "";

  if(!rules.length){
    html = `<div class="card empty">هنوز فیلتری ثبت نشده<br><br><button class="btn" onclick="newRule()">افزودن اولین فیلتر</button></div>`;
  } else {
    html = rules.map(ruleCard).join("");
  }

  shell("فیلترها", "ساخت، ویرایش و کنترل قوانین", html);
}

function ruleCard(r){
  const open = !!expanded[r.id];

  return `
    <div class="card ${open ? "" : "collapsed"}">
      <div class="row">
        <div class="badge">${r.enabled ? "ON" : "OFF"}</div>
        <div class="grow">
          <div class="title">${esc(r.name || "Filter")}</div>
          <div class="sub">از: ${esc(r.sender || "همه")}</div>
        </div>
        <div class="switch ${r.enabled ? "on" : ""}" onclick="toggleRule('${r.id}', ${!r.enabled})"></div>
      </div>

      <div class="info"><div class="k">کلمات</div><div>${esc(r.keyword || "همه پیام‌ها")}</div></div>
      <div class="info"><div class="k">مقصد</div><div>${esc(r.target || "---")}</div></div>

      <div class="extra">
        <div class="info"><div class="k">متن اضافه</div><div>${esc(r.suffix || "هیچ")}</div></div>
        <div class="info"><div class="k">OTP</div><div>${r.allowOtp ? "مجاز" : "مسدود"}</div></div>
        <div class="info"><div class="k">شناسه</div><div>${r.id}</div></div>
      </div>

      <div class="actions">
        <button class="btn light" onclick="expanded['${r.id}'] = !expanded['${r.id}']; render()">جزئیات</button>
        <button class="btn gray" onclick='editRule(${JSON.stringify(r)})'>ویرایش</button>
        <button class="btn danger" onclick="deleteRule('${r.id}')">حذف</button>
      </div>
    </div>
  `;
}

function newRule(){
  editing = {id:"", name:"", sender:"", keyword:"", target:"", suffix:"", enabled:true, allowOtp:false};
  wizardStep = 0;
  screen = "edit";
  render();
}

function editRule(r){
  editing = {...r};
  wizardStep = 0;
  screen = "edit";
  render();
}

function renderWizard(){
  const titles = ["نام و فرستنده", "شرط پیام", "مقصد و متن", "خلاصه"];
  const dots = [0,1,2,3].map(i => `<div class="dot ${i===wizardStep ? "active" : ""}"></div>`).join("");

  let body = `<div class="wizardTop">${dots}</div><div class="card"><div class="title">${titles[wizardStep]}</div><br>`;

  if(wizardStep === 0){
    body += `
      <input class="input" placeholder="نام فیلتر، مثلا بانک صنعت" value="${attr(editing.name)}" oninput="editing.name=this.value">
      <textarea class="input" placeholder="فرستنده/سرشماره؛ مثلا 100099 یا MALIAT یا خالی" oninput="editing.sender=this.value">${esc(editing.sender)}</textarea>
    `;
  }

  if(wizardStep === 1){
    body += `
      <textarea class="input" placeholder="کلمات کلیدی؛ مثلا بانک صنعت معدن 58004 واریز" oninput="editing.keyword=this.value">${esc(editing.keyword)}</textarea>
      <p class="sub">همه کلمات باید در متن پیام باشند.</p>
    `;
  }

  if(wizardStep === 2){
    body += `
      <textarea class="input" placeholder="مقصدها؛ هر شماره در یک خط یا با کاما" oninput="editing.target=this.value">${esc(editing.target)}</textarea>
      <input class="input" placeholder="متن اضافه آخر پیام، اختیاری" value="${attr(editing.suffix)}" oninput="editing.suffix=this.value">
      <label class="checkline"><input type="checkbox" ${editing.allowOtp ? "checked" : ""} onchange="editing.allowOtp=this.checked"> اجازه فوروارد OTP</label>
    `;
  }

  if(wizardStep === 3){
    body += `
      <div class="info"><div class="k">نام</div><div>${esc(editing.name || "Filter")}</div></div>
      <div class="info"><div class="k">از</div><div>${esc(editing.sender || "همه")}</div></div>
      <div class="info"><div class="k">کلمات</div><div>${esc(editing.keyword || "همه")}</div></div>
      <div class="info"><div class="k">به</div><div>${esc(editing.target || "---")}</div></div>
      <div class="info"><div class="k">OTP</div><div>${editing.allowOtp ? "مجاز" : "مسدود"}</div></div>
    `;
  }

  body += `</div>
    <div class="footerActions">
      <button class="btn out" onclick="${wizardStep===0 ? "screen='filters';render()" : "wizardStep--;render()"}">${wizardStep===0 ? "لغو" : "قبلی"}</button>
      <button class="btn" onclick="${wizardStep===3 ? "saveWizard()" : "wizardStep++;render()"}">${wizardStep===3 ? "ذخیره" : "بعدی"}</button>
    </div>
  `;

  shell(editing.id ? "ویرایش فیلتر" : "افزودن فیلتر", "Wizard ساخت فیلتر", body, false);
}

function saveWizard(){
  if(!editing.target || !editing.target.trim()){
    alert("مقصد را وارد کن");
    return;
  }

  saveRuleToKotlin({
    id: editing.id || "",
    name: editing.name || "",
    sender: editing.sender || "",
    keyword: editing.keyword || "",
    target: editing.target || "",
    suffix: editing.suffix || "",
    enabled: true,
    allowOtp: !!editing.allowOtp
  });

  screen = "filters";
  render();
}

function renderHistory(){
  let items = getHistory();
  if(historyFilter === "FAIL") items = items.filter(x => !x.success);

  let html = `
    <div class="chips">
      <button class="chip ${historyFilter==="ALL" ? "active" : ""}" onclick="historyFilter='ALL';render()">All</button>
      <button class="chip ${historyFilter==="FAIL" ? "active" : ""}" onclick="historyFilter='FAIL';render()">Fail</button>
      <button class="chip" onclick="clearHistoryInKotlin();render()">پاک کردن</button>
    </div>
  `;

  html += items.length ? items.map(historyCard).join("") : `<div class="card empty">History خالی است</div>`;

  shell("History", "نتیجه ارسال‌ها و خطاها", html, false);
}

function historyCard(it){
  const open = !!expanded["h"+it.id];

  return `
    <div class="card history-card ${open ? "open" : ""}" onclick="expanded['h${it.id}'] = !expanded['h${it.id}']; render()">
      <div class="row">
        <div class="grow">
          <div class="senderBlock">${esc(it.sender || "---")}</div>
          <div class="targetBlock"><span class="arrow">↪</span> ${esc(it.target || "---")}</div>
        </div>
        <div class="badge ${it.success ? "success" : "fail"}">${it.success ? "Success" : "Fail"}</div>
      </div>
      <div class="msg">${esc(it.message || "")}</div>
      <div class="row" style="margin-top:10px">
        <div class="sub grow">${esc(it.time || "")}</div>
        <div class="sub">${open ? "بستن" : "جزئیات"}</div>
      </div>
      ${open ? `
        <div class="info"><div class="k">فیلتر</div><div>${esc(it.filterName || "")}</div></div>
        <div class="info"><div class="k">نتیجه</div><div>${esc(it.result || "")}</div></div>
      ` : ""}
    </div>
  `;
}

function renderSettings(){
  const html = `
    <div class="card">
      <div class="title">تنظیمات مهم</div>
      <p class="sub">برای کار در پس‌زمینه، Battery را روی Unrestricted بگذار.</p>
      <br>
      <button class="btn" onclick="TakBridge.requestBattery()">تنظیم Battery</button>
    </div>

    <div class="card">
      <div class="title">راهنما</div>
      <div class="info"><div class="k">کلمات</div><div>با شرط AND بررسی می‌شوند.</div></div>
      <div class="info"><div class="k">مقصد</div><div>چند شماره با کاما یا خط جدید.</div></div>
      <div class="info"><div class="k">فرستنده</div><div>شماره، سرشماره یا نام مثل MALIAT.</div></div>
    </div>
  `;

  shell("تنظیمات", "مجوزها و راهنما", html, false);
}

function toggleRule(id, enabled){
  toggleRuleInKotlin(id, enabled);
  render();
}

function deleteRule(id){
  if(confirm("فیلتر حذف شود؟")){
    deleteRuleFromKotlin(id);
    render();
  }
}

function fa(n){ return String(n ?? 0).replace(/\d/g, d => "۰۱۲۳۴۵۶۷۸۹"[d]); }
function esc(s){ return String(s ?? "").replace(/[&<>"']/g, m => ({"&":"&amp;","<":"&lt;",">":"&gt;",'"':"&quot;","'":"&#39;"}[m])); }
function attr(s){ return esc(s).replace(/"/g, "&quot;"); }

render();
