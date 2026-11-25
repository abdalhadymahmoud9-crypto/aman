const msg = document.getElementById('msg');

function say(text, good) { msg.textContent = text; msg.className = good ? 'ok' : 'bad'; }

// Accept "domain.tld", optionally with scheme/path, strip to registrable-looking host.
function normalizeDomain(raw) {
  let d = raw.trim().toLowerCase();
  if (!d) return null;
  d = d.replace(/^[a-z][a-z0-9+.-]*:\/\//, ''); // strip scheme
  d = d.split(/[\/?#]/)[0];                     // strip path/query
  d = d.replace(/:\d+$/, '');                   // strip port
  if (d.startsWith('www.')) d = d.slice(4);
  if (!/^(?!-)[a-z0-9-]{1,63}(?<!-)(\.(?!-)[a-z0-9-]{1,63}(?<!-))+$/.test(d)) return null;
  return d;
}

function renderList(ul, items, listName) {
  ul.innerHTML = '';
  items.slice().sort().forEach(d => {
    const li = document.createElement('li');
    const span = document.createElement('span'); span.textContent = d;
    const btn = document.createElement('button'); btn.textContent = 'Remove';
    btn.addEventListener('click', () => {
      chrome.runtime.sendMessage({ type: 'removeCustom', list: listName, domain: d }, (r) => {
        if (!r || !r.ok) return say('Could not remove. No changes made.', false);
        say(d + ' removed.', true); refresh();
      });
    });
    li.append(span, btn); ul.appendChild(li);
  });
}

function refresh() {
  chrome.runtime.sendMessage({ type: 'getStatus' }, (r) => {
    if (!r || !r.ok) return say('Could not load your lists. Reopen this page.', false);
    renderList(document.getElementById('blockList'), r.customBlocked, 'blocked');
    renderList(document.getElementById('allowList'), r.customAllowed, 'allowed');
  });
}

function add(inputId, listName) {
  const input = document.getElementById(inputId);
  const d = normalizeDomain(input.value);
  if (!d) return say('That does not look like a domain (for example: example.com). Nothing was added.', false);
  chrome.runtime.sendMessage({ type: 'addCustom', list: listName, domain: d }, (r) => {
    if (!r || !r.ok) return say('Could not add ' + d + '. No changes made.', false);
    input.value = '';
    say(d + (listName === 'allowed' ? ' is now allowed.' : ' is now blocked.'), true);
    refresh();
  });
}

document.getElementById('addBlock').addEventListener('click', () => add('blockInput', 'blocked'));
document.getElementById('addAllow').addEventListener('click', () => add('allowInput', 'allowed'));
document.getElementById('blockInput').addEventListener('keydown', e => { if (e.key === 'Enter') add('blockInput', 'blocked'); });
document.getElementById('allowInput').addEventListener('keydown', e => { if (e.key === 'Enter') add('allowInput', 'allowed'); });
refresh();
