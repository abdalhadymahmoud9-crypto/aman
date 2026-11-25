const fs = require('fs');
const vm = require('vm');

// ---- mock chrome API ----
function makeChrome(opts = {}) {
  const store = { enabled: true, customBlocked: [], customAllowed: [], extendedActive: [] };
  let enabledSets = new Set(['rules_1']);
  let dynRules = [];
  const calls = [];
  const chrome = {
    storage: { local: {
      get: async (defaults) => { const out = {}; for (const k in defaults) out[k] = k in store ? store[k] : defaults[k]; return out; },
      set: async (obj) => Object.assign(store, obj),
    }},
    declarativeNetRequest: {
      getDynamicRules: async () => dynRules.slice(),
      updateDynamicRules: async ({ removeRuleIds, addRules }) => {
        dynRules = dynRules.filter(r => !removeRuleIds.includes(r.id)).concat(addRules || []);
        calls.push(['dyn', removeRuleIds.length, (addRules || []).length]);
      },
      updateEnabledRulesets: async ({ enableRulesetIds = [], disableRulesetIds = [] }) => {
        for (const id of enableRulesetIds) {
          if (opts.failOn === id) throw new Error('quota exceeded');
          enabledSets.add(id);
        }
        for (const id of disableRulesetIds) enabledSets.delete(id);
        calls.push(['sets', [...enabledSets]]);
      },
      getEnabledRulesets: async () => [...enabledSets],
    },
    action: { setBadgeText: async () => {}, setBadgeBackgroundColor: async () => {} },
    runtime: { onInstalled: { addListener: () => {} }, onStartup: { addListener: () => {} },
               onMessage: { addListener(fn) { this._fn = fn; } } },
  };
  return { chrome, store, calls, getDyn: () => dynRules, getSets: () => enabledSets };
}

function loadBackground(chrome) {
  const code = fs.readFileSync('extension/background.js', 'utf8');
  const sandbox = { chrome, console };
  vm.createContext(sandbox);
  vm.runInContext(code, sandbox);
  return chrome.runtime.onMessage._fn;
}

function send(fn, msg) {
  return new Promise((resolve) => fn(msg, null, resolve));
}

(async () => {
  const results = [];
  const t = (name, ok, extra) => { results.push([name, !!ok, extra || '']); };

  // 1. defaults
  let env = makeChrome();
  let fn = loadBackground(env.chrome);
  let r = await send(fn, { type: 'getStatus' });
  t('getStatus defaults: enabled, empty lists, 1/4 sets', r.ok && r.enabled === true && r.customBlocked.length === 0 && r.rulesetsActive === 1 && r.rulesetsTotal === 4, JSON.stringify({a:r.rulesetsActive}));

  // 2. addCustom block generates dynamic rule with correct boundary + priority
  r = await send(fn, { type: 'addCustom', list: 'blocked', domain: 'example.com' });
  let dyn = env.getDyn();
  t('custom block -> dynamic rule ||example.com^ block p1', r.ok && dyn.length === 1 && dyn[0].condition.urlFilter === '||example.com^' && dyn[0].action.type === 'block' && dyn[0].priority === 1);

  // 3. allow wins: adding same domain to allowed removes from blocked, allow rule priority 3
  r = await send(fn, { type: 'addCustom', list: 'allowed', domain: 'example.com' });
  dyn = env.getDyn();
  t('allow wins: domain moved off block list, allow rule p3', r.ok && r.customBlocked.length === 0 && r.customAllowed.length === 1 && dyn.length === 1 && dyn[0].action.type === 'allow' && dyn[0].priority === 3);
  // boundary check: rule pattern cannot match example.com.evil.com because '.' is not a separator in DNR
  const pat = dyn[0].condition.urlFilter;
  t('allow rule anchored with || and ^ (no unrelated suffix match)', pat.startsWith('||') && pat.endsWith('^'));

  // 4. removeCustom
  r = await send(fn, { type: 'removeCustom', list: 'allowed', domain: 'example.com' });
  t('removeCustom clears list and dynamic rules', r.ok && r.customAllowed.length === 0 && env.getDyn().length === 0);

  // 5. toggle OFF disables all rulesets and dynamic rules, storage keeps lists
  await send(fn, { type: 'addCustom', list: 'blocked', domain: 'example.org' });
  r = await send(fn, { type: 'setEnabled', enabled: false });
  t('setEnabled(false): all sets disabled, dynamic rules removed', r.ok && env.getSets().size === 0 && env.getDyn().length === 0);
  r = await send(fn, { type: 'getStatus' });
  t('OFF keeps stored custom list', r.enabled === false && r.customBlocked.length === 1);
  r = await send(fn, { type: 'setEnabled', enabled: true });
  t('re-enable restores baseline + custom dynamic rule', env.getSets().has('rules_1') && env.getDyn().length === 1);

  // 6. rule budget failure: extended sets fail at rules_3 -> baseline + rules_2 stay, no silent crash
  env = makeChrome({ failOn: 'rules_3' });
  fn = loadBackground(env.chrome);
  r = await send(fn, { type: 'setEnabled', enabled: true });
  const sets = env.getSets();
  t('budget failure: baseline+rules_2 active, rules_3/4 skipped, ok response', r.ok && sets.has('rules_1') && sets.has('rules_2') && !sets.has('rules_3') && !sets.has('rules_4'), [...sets].join(','));
  r = await send(fn, { type: 'getStatus' });
  t('status reports partial sets (2/4)', r.rulesetsActive === 2 && r.rulesetsTotal === 4);

  // 7. normalizeDomain from options.js
  const optSrc = fs.readFileSync('extension/options.js', 'utf8');
  const m = optSrc.match(/function normalizeDomain\(raw\) \{[\s\S]*?\n\}/);
  const sandbox = {};
  vm.createContext(sandbox);
  vm.runInContext(m[0] + '; this.normalizeDomain = normalizeDomain;', sandbox);
  const nd = sandbox.normalizeDomain;
  t('normalize: strips scheme+path+www', nd('HTTPS://WWW.Example.COM/page?q=1') === 'example.com');
  t('normalize: strips port', nd('example.com:8080/x') === 'example.com');
  t('normalize: rejects junk', nd('not a domain!!') === null && nd('') === null && nd('localhost') === null);
  t('normalize: keeps subdomains', nd('sub.example.co.uk') === 'sub.example.co.uk');
  t('normalize: rejects bad labels', nd('-bad-.com') === null && nd('bad..com') === null);

  let fails = 0;
  for (const [name, ok, extra] of results) {
    console.log((ok ? 'PASS' : 'FAIL') + ' | ' + name + (extra ? ' | ' + extra : ''));
    if (!ok) fails++;
  }
  process.exit(fails ? 1 : 0);
})();
