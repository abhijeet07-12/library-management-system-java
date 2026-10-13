/* Library app inside the book: /api/books, /api/members and /api/loans. */
(() => {
  'use strict';
  const API_URL = '/api/books', MEMBERS_URL = '/api/members', LOANS_URL = '/api/loans', FINES_URL = '/api/fines';
  const $ = s => document.querySelector(s);
  const $$ = s => [...document.querySelectorAll(s)];
  const el = (tag, cls, text) => { const n = document.createElement(tag); if (cls) n.className = cls; if (text != null) n.textContent = text; return n; };

  let books = [], state = 'loading', errMsg = '';

  async function apiRequest(url, options = {}) {
    const res = await fetch(url, options);
    if (!res.ok) {
      const details = await res.text();
      let msg = details;
      try { const j = JSON.parse(details); msg = j.error || j.message || details; } catch (e) { /* plain text */ }
      throw new Error(msg || `Request failed (${res.status})`);
    }
    if (res.status === 204) return null;
    return (res.headers.get('content-type') || '').includes('application/json') ? res.json() : null;
  }

  function toast(msg, type) {
    const t = el('div', 'toast' + (type === 'err' ? ' err' : ''), msg);
    $('#toasts').append(t);
    setTimeout(() => { t.classList.add('gone'); setTimeout(() => t.remove(), 320); }, type === 'err' ? 5200 : 3200);
  }

  function stateBox(parent, kind, msg, label, fn) {
    const d = el('div', 'state');
    if (kind === 'loading') d.append(el('div', 'spin'));
    d.append(el('p', '', msg));
    if (label) { const b = el('button', 'ghost', label); b.type = 'button'; b.addEventListener('click', fn); d.append(b); }
    parent.replaceChildren(d);
  }

  const idOf = b => b.id ?? b.bookId;
  const isIn = b => b.available !== false;
  const hue = t => 195 + ([...String(t || '')].reduce((a, c) => a + c.charCodeAt(0), 0) % 50);
  const initials = t => String(t || '?').split(/\s+/).filter(Boolean).slice(0, 2).map(w => w[0]).join('').toUpperCase();

  function row(b, withDelete) {
    const li = el('li', 'card');
    const mini = el('div', 'mini', initials(b.title));
    mini.style.setProperty('--h', hue(b.title));
    mini.setAttribute('aria-hidden', 'true');
    const info = el('div', 'info');
    info.append(el('strong', '', b.title ?? 'Untitled'), el('span', '', b.author ?? 'Unknown author'));
    const extra = [b.isbn, b.publicationYear ?? b.year].filter(Boolean).join(' · ');
    if (extra) info.append(el('small', '', extra));      // shown only if the backend ever returns these fields
    li.append(mini, info, el('span', 'pill ' + (isIn(b) ? 'in' : 'out'), isIn(b) ? 'Available' : 'Unavailable'));
    if (withDelete) {
      const del = el('button', 'ghost danger', 'Delete');
      del.type = 'button';
      del.disabled = idOf(b) == null;
      del.setAttribute('aria-label', `Delete ${b.title ?? 'book'}`);
      del.addEventListener('click', () => remove(idOf(b), b.title));
      li.append(del);
    }
    return li;
  }

  function renderSummary() {
    const ready = state === 'ready';
    const total = books.length, avail = books.filter(isIn).length;
    $('#sTotal').textContent = ready ? total : '–';
    $('#sAvail').textContent = ready ? avail : '–';
    $('#sOut').textContent = ready ? total - avail : '–';
    const api = $('#apiState');
    api.className = 'api ' + (ready ? 'ok' : state === 'error' ? 'bad' : '');
    api.textContent = ready ? `Connected · ${API_URL}` : state === 'error' ? 'Backend unreachable' : 'Connecting…';

    const pct = ready && total ? Math.round(avail / total * 100) : 0;
    $('#meterFill').style.width = pct + '%';
    $('#meter').setAttribute('aria-label', ready ? `${pct}% of books available` : 'Availability unknown');
    $('#ovLead').textContent = state === 'loading' ? 'Loading the collection…'
      : state === 'error' ? 'The collection could not be loaded.'
      : total === 0 ? 'The catalogue is empty. Add the first book to begin.'
      : `${total} ${total === 1 ? 'title is' : 'titles are'} catalogued, ${avail} currently available on the shelves.`;
  }

  function renderRecent() {
    const box = $('#recent');
    if (state === 'loading') return stateBox(box, 'loading', 'Loading…');
    if (state === 'error') return stateBox(box, 'error', errMsg || 'Could not load books.', 'Try again', load);
    if (!books.length) return stateBox(box, 'empty', 'Nothing here yet.', 'Add a book', openAdd);
    const ul = el('ul', 'cards');
    [...books].sort((a, b) => (idOf(b) ?? 0) - (idOf(a) ?? 0)).slice(0, 5).forEach(b => ul.append(row(b, false)));
    box.replaceChildren(ul);
  }

  function renderShelf() {
    const box = $('#shelf'), q = $('#q').value.trim().toLowerCase(), f = $('#filter').value;
    const count = $('#count');
    if (state === 'loading') { count.textContent = ''; return stateBox(box, 'loading', 'Loading books…'); }
    if (state === 'error') { count.textContent = ''; return stateBox(box, 'error', 'Could not load books. ' + errMsg, 'Try again', load); }
    const list = books.filter(b =>
      (String(b.title ?? '') + ' ' + String(b.author ?? '')).toLowerCase().includes(q) &&
      (f === 'all' || (f === 'in') === isIn(b)));
    count.textContent = `Showing ${list.length} of ${books.length}`;
    if (!books.length) return stateBox(box, 'empty', 'No books found. Add your first book.', 'Add book', openAdd);
    if (!list.length) return stateBox(box, 'empty', 'No books match your search.');
    const ul = el('ul', 'cards');
    list.forEach(b => ul.append(row(b, true)));
    box.replaceChildren(ul);
  }

  const render = () => { renderSummary(); renderRecent(); renderShelf(); };

  async function load() {
    state = 'loading'; render();
    $('#refresh').disabled = true;
    try {
      const d = await apiRequest(API_URL);
      books = Array.isArray(d) ? d : (d && Array.isArray(d.content) ? d.content : []);
      state = 'ready';
    } catch (e) { books = []; state = 'error'; errMsg = e.message; }
    $('#refresh').disabled = false;
    render();
  }

  /* ---- add ---- */
  const addDlg = $('#addDlg'), form = $('#bookForm'), formStatus = $('#formStatus');
  function openAdd() { formStatus.textContent = ''; addDlg.showModal(); $('#title').focus(); }
  $('#addCancel').addEventListener('click', () => addDlg.close());
  $('#addOpen').addEventListener('click', openAdd);

  form.addEventListener('submit', async ev => {
    ev.preventDefault();
    const title = $('#title').value.trim(), author = $('#author').value.trim();
    if (!title || !author) { formStatus.textContent = 'Please enter both the book title and author.'; return; }
    const btn = $('#addBtn');
    btn.disabled = true; btn.textContent = 'Adding…'; formStatus.textContent = '';
    try {
      await apiRequest(API_URL, {
        method: 'POST', headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ title, author, available: $('#avail').checked })
      });
      form.reset(); addDlg.close();
      toast('Book added to the catalogue');
      await load();
    } catch (e) { formStatus.textContent = 'Could not add book: ' + e.message; }
    finally { btn.disabled = false; btn.textContent = 'Add book'; }
  });

  /* ---- delete (with confirmation) ---- */
  function confirmDelete(message, heading = 'Remove this book?', okLabel = 'Delete') {
    return new Promise(resolve => {
      const d = $('#delDlg');
      $('#delT').textContent = heading;
      $('#delOk').textContent = okLabel;
      $('#delMsg').textContent = message;
      d.returnValue = '';
      d.addEventListener('close', () => resolve(d.returnValue === 'ok'), { once: true });
      d.showModal();
    });
  }
  async function remove(id, title) {
    if (!(await confirmDelete(`“${title || 'This book'}” will be permanently removed from the catalogue.`))) return;
    try {
      await apiRequest(`${API_URL}/${encodeURIComponent(id)}`, { method: 'DELETE' });
      toast(`“${title || 'Book'}” removed`);
      await load();
    } catch (e) { toast('Could not delete book: ' + e.message, 'err'); }
  }

  /* ---- chapters ---- */
  function setChapter(i) {
    $$('.ch').forEach((s, k) => { s.hidden = k !== i; });
    $$('#nav button').forEach((b, k) => b.setAttribute('aria-current', k === i ? 'page' : 'false'));
    const r = $('#right');
    r.classList.remove('turn'); void r.offsetWidth; r.classList.add('turn');
  }
  addEventListener('chapter', e => setChapter(e.detail));
  $$('#nav button').forEach(b => b.addEventListener('click', () => window.LB.goto(Number(b.dataset.ch), true)));

  $('#q').addEventListener('input', renderShelf);
  $('#filter').addEventListener('change', renderShelf);
  $('#refresh').addEventListener('click', load);


  /* ================= OPERATIONS: loans, members, history ================= */
  let members = [], loans = [], offset = 0, opsState = 'loading', opsErr = '';
  const pad = n => String(n).padStart(2, '0');
  const fmt = iso => new Date(iso).toLocaleString([], { dateStyle: 'medium', timeStyle: 'short' });
  const localInput = d => `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}T${pad(d.getHours())}:${pad(d.getMinutes())}`;
  const nowMs = () => Date.now() + offset;       // corrected by the server clock; display only

  /* The server stores issue/due/return timestamps. This only formats the time left. */
  function countdown(l) {
    if (l.returnedAt) return l.lateReturn ? { text: 'Returned late', cls: 'out' } : { text: 'Returned', cls: 'in' };
    const ms = new Date(l.dueAt).getTime() - nowMs(), a = Math.abs(ms);
    const d = Math.floor(a / 864e5), h = Math.floor(a % 864e5 / 36e5), m = Math.floor(a % 36e5 / 6e4);
    if (ms < 0) return { text: d ? `${pad(d)}d ${pad(h)}h OVERDUE` : h ? `${pad(h)}h ${pad(m)}m OVERDUE` : `${pad(m)}m OVERDUE`, cls: 'bad' };
    if (ms < 36e5) return { text: `DUE TODAY · ${pad(m)}m remaining`, cls: 'out' };
    if (ms < 864e5) return { text: `${pad(h)}h ${pad(m)}m remaining`, cls: 'out' };
    return { text: `${pad(d)}d ${pad(h)}h ${pad(m)}m remaining`, cls: 'in' };
  }
  function paint(n) {
    const c = countdown(n._loan);
    n.className = 'pill cd ' + c.cls;
    if (n.textContent !== c.text) n.textContent = c.text;
  }
  /* Status label (ACTIVE / DUE SOON; OVERDUE is shown by the countdown pill) for loans still out; repainted every second like the countdown. */
  function paintSt(n) {
    const ms = new Date(n._loan.dueAt).getTime() - nowMs();
    const s = ms < 864e5 ? ['DUE SOON', 'out'] : ['ACTIVE', 'in'];
    n.className = 'pill st ' + s[1];
    n.style.display = ms < 0 ? 'none' : '';          // once overdue, the countdown pill already says OVERDUE
    if (n.textContent !== s[0]) n.textContent = s[0];
  }
  setInterval(() => { $$('.cd').forEach(paint); $$('.st').forEach(paintSt); }, 1000);

  /* ---- fines: amounts always come from the server; this only formats them ---- */
  let fineRate = 5;
  const rupee = n => '₹' + Number(n ?? 0).toLocaleString('en-IN', { minimumFractionDigits: Number.isInteger(Number(n ?? 0)) ? 0 : 2, maximumFractionDigits: 2 });
  const days = n => `${n} ${n === 1 ? 'day' : 'days'}`;
  const updateRateBtn = () => { $('#rateOpen').textContent = `Fine ${rupee(fineRate)}/day`; };
  function finePill(l) {
    if (l.paymentStatus === 'PENDING') return el('span', 'pill bad', `FINE PENDING · ${rupee(l.fineAmount)}`);
    if (l.paymentStatus === 'PAID_DEMO') return el('span', 'pill in', `PAID (DEMO) · ${rupee(l.fineAmount)}`);
    return null;
  }

  const openLoans = () => loans.filter(l => !l.returnedAt);

  function loanRow(l, withReturn) {
    const li = el('li', 'card loan');
    const mini = el('div', 'mini', initials(l.bookTitle));
    mini.style.setProperty('--h', hue(l.bookTitle));
    mini.setAttribute('aria-hidden', 'true');
    const info = el('div', 'info');
    info.append(el('strong', '', l.bookTitle), el('span', '', `${l.memberName} · ${l.membershipId}`),
      el('small', '', l.returnedAt ? `Issued ${fmt(l.issuedAt)} · Returned ${fmt(l.returnedAt)}` : `Issued ${fmt(l.issuedAt)} · Due ${fmt(l.dueAt)}`));
    if (!l.returnedAt) { const st = el('span', 'pill st'); st._loan = l; paintSt(st); info.append(st); }
    const cd = el('span', 'pill cd'); cd._loan = l; paint(cd);
    info.append(cd);
    const fp = finePill(l);
    if (fp) info.append(fp);
    if (l.overdueDays > 0 && Number(l.fineAmount) > 0)
      info.append(el('small', '', `${days(l.overdueDays)} overdue × ${rupee(l.finePerDay)} = ${rupee(l.fineAmount)}`));
    li.append(mini, info);
    if (withReturn) {
      const b = el('button', 'ghost', 'Return');
      b.type = 'button';
      b.setAttribute('aria-label', `Return ${l.bookTitle}`);
      b.addEventListener('click', () => giveBack(l, b));
      li.append(b);
    } else if (l.returnedAt && l.paymentStatus === 'PENDING') {   // fine from a loan returned before fines existed
      const b = el('button', 'ghost', 'Pay fine');
      b.type = 'button';
      b.setAttribute('aria-label', `Pay fine for ${l.bookTitle}`);
      b.addEventListener('click', () => openPay(l, 'history'));
      li.append(b);
    }
    return li;
  }

  function opsBlock(box) {
    if (opsState === 'loading') { stateBox(box, 'loading', 'Loading…'); return true; }
    if (opsState === 'error') { stateBox(box, 'error', opsErr || 'Could not load data.', 'Try again', loadOps); return true; }
    return false;
  }

  function renderLoans() {
    const box = $('#loanList'), list = openLoans().sort((a, b) => new Date(a.dueAt) - new Date(b.dueAt));
    $('#loanCount').textContent = opsState === 'ready' ? `${list.length} active · ${list.filter(l => countdown(l).cls === 'bad').length} overdue` : '';
    if (opsBlock(box)) return;
    if (!list.length) return stateBox(box, 'empty', 'No books are currently on loan.', 'Issue a book', openIssue);
    const ul = el('ul', 'cards'); list.forEach(l => ul.append(loanRow(l, true))); box.replaceChildren(ul);
  }

  function renderMembers() {
    const box = $('#memberList'), q = $('#mq').value.trim().toLowerCase();
    if (opsBlock(box)) return;
    const list = members.filter(m => [m.name, m.membershipId, m.email, m.phone].some(v => String(v ?? '').toLowerCase().includes(q)));
    if (!members.length) return stateBox(box, 'empty', 'No members registered yet.', 'Add a member', openMember);
    if (!list.length) return stateBox(box, 'empty', 'No members match your search.');
    const ul = el('ul', 'cards');
    list.forEach(m => {
      const li = el('li', 'card mem'), mini = el('div', 'mini', initials(m.name));
      mini.style.setProperty('--h', hue(m.name)); mini.setAttribute('aria-hidden', 'true');
      const info = el('div', 'info');
      info.append(el('strong', '', m.name), el('span', '', m.membershipId));
      const contact = [m.email, m.phone].filter(Boolean).join(' · ');
      if (contact) info.append(el('small', '', contact));
      const n = openLoans().filter(l => l.memberId === m.id).length;
      li.append(mini, info, n ? el('span', 'pill out', `${n} on loan`) : el('span'));
      const del = el('button', 'ghost danger', 'Delete');
      del.type = 'button'; del.setAttribute('aria-label', `Delete member ${m.name}`);
      del.addEventListener('click', () => removeMember(m));
      li.append(del); ul.append(li);
    });
    box.replaceChildren(ul);
  }

  function renderHistory() {
    const box = $('#histList'), q = $('#hq').value.trim().toLowerCase(), f = $('#hf').value;
    if (opsBlock(box)) return;
    const kind = l => l.returnedAt ? 'returned' : countdown(l).cls === 'bad' ? 'overdue' : 'active';
    const list = loans.filter(l => (f === 'all' || kind(l) === f) &&
      [l.bookTitle, l.memberName, l.membershipId].some(v => String(v ?? '').toLowerCase().includes(q)));
    if (!loans.length) return stateBox(box, 'empty', 'No borrowing records yet.');
    if (!list.length) return stateBox(box, 'empty', 'No records match your filters.');
    const ul = el('ul', 'cards'); list.forEach(l => ul.append(loanRow(l, false))); box.replaceChildren(ul);
  }

  const renderOps = () => { renderLoans(); renderMembers(); renderHistory(); };

  async function loadOps() {
    opsState = 'loading'; renderOps();
    try {
      const [m, l, fs] = await Promise.all([apiRequest(MEMBERS_URL), apiRequest(LOANS_URL),
        apiRequest(FINES_URL + '/settings').catch(() => null)]);
      members = Array.isArray(m) ? m : []; loans = Array.isArray(l) ? l : [];
      if (fs && fs.finePerDay != null) { fineRate = Number(fs.finePerDay); updateRateBtn(); }
      if (loans.length) offset = new Date(loans[0].serverNow).getTime() - Date.now();
      opsState = 'ready';
    } catch (e) { members = []; loans = []; opsState = 'error'; opsErr = e.message; }
    renderOps();
  }
  const refreshAll = () => Promise.all([load(), loadOps()]);

  /* Return button: opens the fine summary; the book is only returned when the librarian confirms. */
  const giveBack = l => openReturn(l.id);

  const retDlg = $('#retDlg'), payDlg = $('#payDlg'), rateDlg = $('#rateDlg');
  let retLoan = null, payLoan = null, payFrom = null, busy = false;

  async function openReturn(id) {
    let l;
    try { l = await apiRequest(`${LOANS_URL}/${encodeURIComponent(id)}`); }
    catch (e) { toast('Could not load the loan: ' + e.message, 'err'); return; }
    offset = new Date(l.serverNow).getTime() - Date.now();
    retLoan = l;
    $('#retErr').textContent = '';
    paintReturn(l);
    if (!retDlg.open) retDlg.showModal();
  }

  function paintReturn(l) {
    $('#retBook').textContent = `“${l.bookTitle}”${l.bookAuthor ? ' — ' + l.bookAuthor : ''}`;
    const dl = $('#retKv'); dl.replaceChildren();
    const row = (k, v, cls) => dl.append(el('dt', '', k), el('dd', cls || '', v));
    row('Member', `${l.memberName} · ${l.membershipId}`);
    row('Due date', fmt(l.dueAt));
    row('Current date', fmt(l.serverNow));
    row('Overdue', l.overdueDays ? days(l.overdueDays) : 'Not overdue');
    row('Fine rate', `${rupee(l.finePerDay)} / day`);
    row('Total fine', rupee(l.fineAmount), 'big');
    row('Return', l.returnedAt ? 'RETURNED' : 'NOT RETURNED YET');
    row('Payment', l.paymentStatus === 'NONE' ? 'NO FINE' : l.paymentStatus === 'PENDING' ? 'FINE PENDING'
      : `PAID (DEMO) · ${fmt(l.finePaidAt)}`);
    $('#retMsg').textContent = l.returnedAt ? 'This book has already been returned.'
      : l.paymentStatus === 'NONE' ? 'No fine is due. You can confirm the return.'
      : l.paymentStatus === 'PENDING' ? `A fine of ${rupee(l.fineAmount)} is pending. Collect it with the demo payment, then confirm the return.`
      : 'The fine has been paid (demo). You can now confirm the return.';
    const pay = $('#retPay');
    pay.hidden = l.paymentStatus !== 'PENDING';
    pay.className = 'primary'; pay.textContent = `Pay fine ${rupee(l.fineAmount)}`;
    $('#retOk').disabled = !!l.returnedAt || l.paymentStatus === 'PENDING';
    $('#retOk').className = l.paymentStatus === 'PENDING' ? 'ghost' : 'primary';
  }

  $('#retCancel').addEventListener('click', () => retDlg.close());
  $('#retPay').addEventListener('click', () => { const l = retLoan; retDlg.close(); openPay(l, 'return'); });
  $('#retOk').addEventListener('click', async () => {
    if (busy || !retLoan) return;
    busy = true; $('#retOk').disabled = true;
    const l = retLoan;
    try {
      await apiRequest(`${LOANS_URL}/${encodeURIComponent(l.id)}/return`, { method: 'POST' });
      retDlg.close();
      toast(`“${l.bookTitle}” returned`);
      await refreshAll();
    } catch (e) {
      await openReturn(l.id);                       // show the server's current state, then the reason
      $('#retErr').textContent = 'Could not return book: ' + e.message;
    } finally { busy = false; }
  });

  /* ---- demo payment: nothing is paid until the librarian clicks Simulate Successful Payment ---- */
  function openPay(l, from) {
    payLoan = l; payFrom = from;
    $('#payErr').textContent = '';
    $('#payAmt').textContent = rupee(l.fineAmount);
    $('#payBook').textContent = `“${l.bookTitle}” · ${l.memberName} (${l.membershipId}) · ${days(l.overdueDays)} overdue`;
    $('#payQr').innerHTML = window.DemoQR.svg(`DEMO PAYMENT - NO REAL MONEY | Loan #${l.id} | INR ${Number(l.fineAmount).toFixed(2)}`);
    $('#payOk').disabled = false;
    payDlg.showModal();
  }
  // Cancel, Esc and success all end up here; a cancelled payment leaves the fine pending.
  payDlg.addEventListener('close', () => {
    const l = payLoan, from = payFrom; payLoan = null;
    if (l && from === 'return') openReturn(l.id);
  });
  $('#payCancel').addEventListener('click', () => payDlg.close());
  $('#payOk').addEventListener('click', async () => {
    if (busy || !payLoan) return;
    busy = true; $('#payOk').disabled = true; $('#payErr').textContent = '';
    const l = payLoan;
    try {
      await apiRequest(`${LOANS_URL}/${encodeURIComponent(l.id)}/pay-fine`, { method: 'POST' });
      toast(`Fine ${rupee(l.fineAmount)} marked PAID (DEMO) — no real money moved`);
      payDlg.close();
      await loadOps();
    } catch (e) {
      $('#payErr').textContent = 'Could not record payment: ' + e.message;
      $('#payOk').disabled = false;
    } finally { busy = false; }
  });

  /* ---- fine rate ---- */
  $('#rateOpen').addEventListener('click', () => { $('#rateErr').textContent = ''; $('#rateIn').value = fineRate; rateDlg.showModal(); $('#rateIn').focus(); });
  $('#rateCancel').addEventListener('click', () => rateDlg.close());
  $('#rateForm').addEventListener('submit', async ev => {
    ev.preventDefault();
    const v = $('#rateIn').value.trim();
    if (v === '' || isNaN(Number(v))) { $('#rateErr').textContent = 'Enter the fine per day, for example 5.'; return; }
    const btn = $('#rateBtn'); btn.disabled = true; $('#rateErr').textContent = '';
    try {
      const r = await apiRequest(FINES_URL + '/settings', { method: 'PUT', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ finePerDay: Number(v) }) });
      fineRate = Number(r.finePerDay); updateRateBtn();
      rateDlg.close();
      toast(`Fine rate set to ${rupee(fineRate)} per day`);
      await loadOps();
    } catch (e) { $('#rateErr').textContent = 'Could not save: ' + e.message; }
    finally { btn.disabled = false; }
  });

  async function removeMember(m) {
    if (!(await confirmDelete(`${m.name} (${m.membershipId}) will be removed from the member list.`, 'Remove this member?'))) return;
    try {
      await apiRequest(`${MEMBERS_URL}/${encodeURIComponent(m.id)}`, { method: 'DELETE' });
      toast(`${m.name} removed`);
      await loadOps();
    } catch (e) { toast('Could not delete member: ' + e.message, 'err'); }
  }

  /* ---- issue dialog ---- */
  const issueDlg = $('#issueDlg'), issueErr = $('#issueErr');
  function updateDue() {
    const v = $('#iDate').value, days = Number($('#iDays').value);
    const start = v ? new Date(v) : new Date();
    $('#iDue').textContent = isNaN(start) ? '' : `Due ${fmt(new Date(start.getTime() + days * 864e5))} (confirmed by the server on issue)`;
  }
  function fillSelect(sel, items, placeholder) {
    sel.replaceChildren(new Option(placeholder, ''), ...items.map(([v, t]) => new Option(t, v)));
  }
  function openIssue() {
    issueErr.textContent = '';
    const onLoan = new Set(openLoans().map(l => l.bookId));
    const free = books.filter(b => isIn(b) && !onLoan.has(idOf(b)));
    fillSelect($('#iBook'), free.map(b => [idOf(b), `${b.title} — ${b.author}`]), free.length ? 'Select a book' : 'No books available');
    fillSelect($('#iMember'), members.map(m => [m.id, `${m.name} (${m.membershipId})`]), members.length ? 'Select a member' : 'No members registered');
    const now = new Date();
    $('#iDate').value = localInput(now); $('#iDate').max = localInput(new Date(now.getTime() + 36e5));
    updateDue(); issueDlg.showModal();
  }
  $('#issueOpen').addEventListener('click', openIssue);
  $('#issueCancel').addEventListener('click', () => issueDlg.close());
  $('#iDate').addEventListener('input', updateDue);
  $('#iDays').addEventListener('change', updateDue);
  $('#issueForm').addEventListener('submit', async ev => {
    ev.preventDefault();
    const bookId = $('#iBook').value, memberId = $('#iMember').value;
    if (!bookId || !memberId) { issueErr.textContent = 'Please select both a book and a member.'; return; }
    const payload = { bookId: Number(bookId), memberId: Number(memberId), days: Number($('#iDays').value) };
    if ($('#iDate').value) payload.issuedAt = new Date($('#iDate').value).toISOString();
    const btn = $('#issueBtn'); btn.disabled = true; btn.textContent = 'Issuing…'; issueErr.textContent = '';
    try {
      const l = await apiRequest(LOANS_URL, { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(payload) });
      issueDlg.close();
      toast(`Issued — due ${fmt(l.dueAt)}`);
      await refreshAll();
    } catch (e) { issueErr.textContent = 'Could not issue book: ' + e.message; }
    finally { btn.disabled = false; btn.textContent = 'Issue book'; }
  });

  /* ---- member dialog ---- */
  const memberDlg = $('#memberDlg'), memberErr = $('#memberErr');
  function openMember() { memberErr.textContent = ''; memberDlg.showModal(); $('#mName').focus(); }
  $('#memberOpen').addEventListener('click', openMember);
  $('#memberCancel').addEventListener('click', () => memberDlg.close());
  $('#memberForm').addEventListener('submit', async ev => {
    ev.preventDefault();
    const body = { name: $('#mName').value.trim(), membershipId: $('#mId').value.trim(), email: $('#mEmail').value.trim(), phone: $('#mPhone').value.trim() };
    if (!body.name || !body.membershipId) { memberErr.textContent = 'Please enter the member name and ID.'; return; }
    const btn = $('#memberBtn'); btn.disabled = true; btn.textContent = 'Adding…'; memberErr.textContent = '';
    try {
      await apiRequest(MEMBERS_URL, { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(body) });
      $('#memberForm').reset(); memberDlg.close();
      toast('Member registered');
      await loadOps();
    } catch (e) { memberErr.textContent = 'Could not add member: ' + e.message; }
    finally { btn.disabled = false; btn.textContent = 'Add member'; }
  });

  /* ---- sub-views and filters ---- */
  $$('.seg button').forEach(b => b.addEventListener('click', () => {
    $$('.seg button').forEach(x => x.setAttribute('aria-pressed', String(x === b)));
    $$('.opv').forEach(p => { p.hidden = p.id !== 'v-' + b.dataset.v; });
  }));
  $('#mq').addEventListener('input', renderMembers);
  $('#hq').addEventListener('input', renderHistory);
  $('#hf').addEventListener('change', renderHistory);

  setChapter(window.LB.chapter);
  load();
  loadOps();
})();
