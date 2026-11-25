const toggle = document.getElementById('toggle');
const dot = document.getElementById('dot');
const err = document.getElementById('err');

function showError(msg) { err.textContent = msg; err.style.display = 'block'; }
function render(s) {
  toggle.className = s.enabled ? 'on' : 'off';
  toggle.textContent = s.enabled ? 'Pause filtering' : 'Resume filtering';
  dot.className = 'dot ' + (s.enabled ? 'on' : 'off');
  document.getElementById('cblocked').textContent = s.customBlocked.length;
  document.getElementById('callowed').textContent = s.customAllowed.length;
  document.getElementById('session').textContent = s.sessionBlocked;
  document.getElementById('sets').textContent = s.rulesetsActive + '/' + s.rulesetsTotal;
}
chrome.runtime.sendMessage({ type: 'getStatus' }, (r) => {
  if (!r || !r.ok) return showError('Could not read status. Try reopening the popup.');
  render(r);
});
toggle.addEventListener('click', () => {
  chrome.runtime.sendMessage({ type: 'getStatus' }, (cur) => {
    if (!cur || !cur.ok) return showError('Could not read status.');
    chrome.runtime.sendMessage({ type: 'setEnabled', enabled: !cur.enabled }, (r) => {
      if (!r || !r.ok) return showError('Could not change state. Filtering was not changed.');
      chrome.runtime.sendMessage({ type: 'getStatus' }, (s) => { if (s && s.ok) render(s); });
    });
  });
});
document.getElementById('openOptions').addEventListener('click', (e) => {
  e.preventDefault(); chrome.runtime.openOptionsPage();
});
